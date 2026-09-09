"""
SIH26168 - AI/ML Based Intelligent Dead Reckoning System
Multi-Task Neural Architecture for AI-Assisted Inertial Odometry.

Backbone:  1D-CNN (vibration/noise filtering)
        -> Bi-GRU (temporal / vehicular-momentum modeling)
        -> Temporal Self-Attention (dynamic frame weighting + attention pooling)

Heads (all fed by the shared attended context vector):
  1. Velocity Head       -> [speed_ms, dx, dy]                (regression)
  2. Bias/Drift Head     -> [ax_bias, ay_bias, az_bias,
                              gx_bias, gy_bias, gz_bias]        (regression)
  3. ZUPT Head           -> P(stationary)                       (Sigmoid classification)
  4. Uncertainty Head    -> log-variance for each velocity dim   (feeds EKF/UKF R matrix)

Input:
    x: (Batch, 10, SequenceLength)
       channels 0-8 = normalized [ax, ay, az, gx, gy, gz, mx, my, mz]
       channel  9   = GNSS-availability flag in {0, 1}, broadcast per sample
"""

import torch
import torch.nn as nn
import torch.nn.functional as F


class TemporalSelfAttention(nn.Module):
    """
    Lightweight temporal self-attention block:
      1. A standard multi-head self-attention refines the Bi-GRU sequence,
         letting every time step attend to every other time step (dynamic
         frame weighting - e.g. down-weighting a pothole-induced spike).
      2. An additive attention pooling layer then collapses the refined
         sequence into a single context vector, instead of naively using
         only the last time step (which discards useful mid-window
         information such as a braking event that resolves before the
         window ends).

    Returns:
        context: (B, D)      pooled representation for the heads
        attn_weights: (B, T) pooling weights, useful for diagnostics/plots
    """

    def __init__(self, dim: int, num_heads: int = 4, dropout: float = 0.1):
        super().__init__()
        self.self_attn = nn.MultiheadAttention(
            embed_dim=dim, num_heads=num_heads, dropout=dropout, batch_first=True
        )
        self.norm = nn.LayerNorm(dim)

        # Additive ("Bahdanau-style") pooling attention
        self.pool_proj = nn.Sequential(
            nn.Linear(dim, dim // 2),
            nn.Tanh(),
            nn.Linear(dim // 2, 1),
        )

    def forward(self, seq: torch.Tensor):
        # seq: (B, T, D)
        attended, _ = self.self_attn(seq, seq, seq, need_weights=False)
        seq = self.norm(seq + attended)  # residual + LayerNorm

        pool_scores = self.pool_proj(seq).squeeze(-1)        # (B, T)
        attn_weights = F.softmax(pool_scores, dim=-1)          # (B, T)
        context = torch.bmm(attn_weights.unsqueeze(1), seq).squeeze(1)  # (B, D)
        return context, attn_weights


class DeadReckoningMultiTaskModel(nn.Module):
    """
    1D-CNN + Bi-GRU + Temporal Self-Attention multi-task inertial odometry model.

    Args:
        in_channels: number of input channels (default 10 = 9-axis IMU + GNSS flag)
        conv_filters: base number of CNN filters
        gru_hidden: hidden size per GRU direction
        num_gru_layers: number of stacked Bi-GRU layers
        attn_heads: number of self-attention heads (must divide gru_hidden*2)

    forward(x) returns a dict:
        "velocity":    (B, 3)  [speed_ms (>=0), dx, dy]
        "bias":        (B, 6)  [ax, ay, az, gx, gy, gz] bias estimate, raw sensor units
        "zupt_prob":   (B, 1)  P(vehicle stationary), in [0, 1]
        "log_var":     (B, 3)  predicted log-variance per velocity-head dimension,
                                 i.e. Var = exp(log_var). Feed directly into the
                                 EKF/UKF measurement-noise covariance R.
        "attn_weights":(B, T)  temporal attention weights (diagnostics only)
    """

    def __init__(
        self,
        in_channels: int = 10,
        conv_filters: int = 64,
        gru_hidden: int = 96,
        num_gru_layers: int = 2,
        attn_heads: int = 4,
    ):
        super().__init__()
        self.in_channels = in_channels

        # 1. 1D-CNN multi-scale feature extraction / vibration filtering
        self.conv_block = nn.Sequential(
            nn.Conv1d(in_channels, conv_filters, kernel_size=7, padding=3),
            nn.BatchNorm1d(conv_filters),
            nn.LeakyReLU(0.1),
            nn.Dropout(0.15),

            nn.Conv1d(conv_filters, conv_filters, kernel_size=5, padding=2),
            nn.BatchNorm1d(conv_filters),
            nn.LeakyReLU(0.1),
            nn.MaxPool1d(kernel_size=2),  # halves the time axis

            nn.Conv1d(conv_filters, conv_filters * 2, kernel_size=3, padding=1),
            nn.BatchNorm1d(conv_filters * 2),
            nn.LeakyReLU(0.1),
            nn.MaxPool1d(kernel_size=2),  # halves the time axis again
        )

        # 2. Bi-GRU temporal sequence modeling (vehicular momentum & inertia)
        self.gru = nn.GRU(
            input_size=conv_filters * 2,
            hidden_size=gru_hidden,
            num_layers=num_gru_layers,
            batch_first=True,
            bidirectional=True,
            dropout=0.2 if num_gru_layers > 1 else 0.0,
        )

        gru_out_dim = gru_hidden * 2  # bidirectional doubles hidden size
        assert gru_out_dim % attn_heads == 0, (
            f"gru_hidden*2 ({gru_out_dim}) must be divisible by attn_heads ({attn_heads})"
        )

        # 3. Temporal self-attention (dynamic frame weighting + pooling)
        self.temporal_attention = TemporalSelfAttention(dim=gru_out_dim, num_heads=attn_heads)

        # 4. Multi-task heads, all fed from the shared attended context vector
        shared_dim = gru_out_dim

        self.velocity_trunk = nn.Sequential(
            nn.Linear(shared_dim, 64),
            nn.LeakyReLU(0.1),
            nn.Dropout(0.1),
            nn.Linear(64, 32),
            nn.LeakyReLU(0.1),
        )
        # speed_ms must be >= 0 (softplus); dx, dy are signed and unconstrained
        self.speed_out = nn.Linear(32, 1)
        self.displacement_out = nn.Linear(32, 2)

        self.bias_head = nn.Sequential(
            nn.Linear(shared_dim, 32),
            nn.LeakyReLU(0.1),
            nn.Linear(32, 6),  # ax, ay, az, gx, gy, gz bias, raw physical units
        )

        self.zupt_head = nn.Sequential(
            nn.Linear(shared_dim, 16),
            nn.LeakyReLU(0.1),
            nn.Linear(16, 1),
            nn.Sigmoid(),
        )

        self.uncertainty_head = nn.Sequential(
            nn.Linear(shared_dim, 32),
            nn.LeakyReLU(0.1),
            nn.Linear(32, 3),  # log-variance for [speed, dx, dy]
        )

    def forward(self, x: torch.Tensor):
        # x: (B, in_channels, T)
        conv_feats = self.conv_block(x)              # (B, C', T')
        gru_in = conv_feats.permute(0, 2, 1)          # (B, T', C')
        gru_out, _ = self.gru(gru_in)                 # (B, T', 2*hidden)

        context, attn_weights = self.temporal_attention(gru_out)  # (B, 2*hidden), (B, T')

        vel_feat = self.velocity_trunk(context)
        speed = F.softplus(self.speed_out(vel_feat))       # forward speed >= 0
        displacement = self.displacement_out(vel_feat)      # dx, dy (signed)
        velocity = torch.cat([speed, displacement], dim=-1)  # (B, 3)

        bias = self.bias_head(context)                # (B, 6)
        zupt_prob = self.zupt_head(context)            # (B, 1)
        log_var = self.uncertainty_head(context)
        log_var = torch.clamp(log_var, min=-5.0, max=5.0)

        return {
            "velocity": velocity,
            "bias": bias,
            "zupt_prob": zupt_prob,
            "log_var": log_var,
            "attn_weights": attn_weights,
        }


def count_parameters(model: nn.Module) -> int:
    return sum(p.numel() for p in model.parameters() if p.requires_grad)


if __name__ == "__main__":
    model = DeadReckoningMultiTaskModel()
    dummy_input = torch.randn(4, 10, 200)  # (B, 10 channels, 200 samples @ 100Hz = 2s)
    out = model(dummy_input)

    print(f"[Model Verified] Total Trainable Parameters: {count_parameters(model):,}")
    for k, v in out.items():
        print(f"  {k:14s} -> {tuple(v.shape)}")
