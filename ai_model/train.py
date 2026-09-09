"""
SIH26168 - Training & Evaluation Pipeline
1D-CNN + Bi-GRU + Temporal Self-Attention Multi-Task Dead Reckoning Model.

Multi-task loss:
    L_total = alpha * L_velocity(Huber)
            + beta  * L_zupt(BCE)
            + gamma * L_uncertainty(Gaussian NLL)
            + delta * L_bias(masked Huber, ZUPT-interval supervision only)

Validation strategy:
    Contiguous block-wise stratification: every trajectory is cut into
    chronological [train | val | test] blocks and the corresponding blocks
    are pooled across all trajectories (see dataset.contiguous_block_split).
    This keeps val/test representative of the full mix of driving
    behaviour instead of depending on whichever whole file was held out.

Usage:
    python train.py --epochs 25 --batch_size 64 --lr 1e-3
"""

import os
import sys
import argparse
import time
import numpy as np

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

try:
    import torch
    import torch.nn as nn
    from torch.utils.data import DataLoader
    try:
        from model import DeadReckoningMultiTaskModel
        from dataset import (
            load_iovnbd_sequences, default_sequence_pairs,
            compute_train_only_normalization, contiguous_block_split,
            save_normalization_stats, DEFAULT_WINDOW_SIZE, DEFAULT_STEP,
        )
    except ImportError:
        from ai_model.model import DeadReckoningMultiTaskModel
        from ai_model.dataset import (
            load_iovnbd_sequences, default_sequence_pairs,
            compute_train_only_normalization, contiguous_block_split,
            save_normalization_stats, DEFAULT_WINDOW_SIZE, DEFAULT_STEP,
        )
    TORCH_AVAILABLE = True
except ImportError:
    TORCH_AVAILABLE = False


def gaussian_nll_loss(
    pred: "torch.Tensor",
    target: "torch.Tensor",
    log_var: "torch.Tensor"
    ):
    """
    Numerically stable heteroscedastic Gaussian NLL.

    log_var is constrained to a safe range so exp(-log_var)
    cannot overflow during training.
    """
    log_var = torch.clamp(log_var, min=-5.0, max=5.0)

    precision = torch.exp(-log_var)

    nll = (
        0.5 * precision * (pred - target) ** 2
        + 0.5 * log_var
    )

    return nll.mean()

def masked_bias_loss(pred_bias: "torch.Tensor", target_bias: "torch.Tensor", mask: "torch.Tensor", huber: "nn.HuberLoss"):
    """
    Only penalize the bias head on windows where `mask == 1`, i.e. windows
    that are fully stationary (ZUPT interval) and therefore have a valid
    physical bias target. Non-stationary windows contribute zero bias loss
    so the head is never pushed toward a fabricated target while the
    vehicle is moving.
    """
    mask = mask.expand_as(target_bias)  # (B,1) -> (B,6)
    if mask.sum() < 1.0:
        return torch.tensor(0.0, device=pred_bias.device)
    per_elem = huber(pred_bias * mask, target_bias * mask)
    # HuberLoss with reduction='mean' would dilute by the zeroed-out
    # elements too, so renormalize by the actual number of supervised entries.
    denom = mask.sum().clamp(min=1.0)
    return per_elem * mask.numel() / denom


def evaluate(model, loader, device, huber, alpha, beta, gamma, delta):
    model.eval()
    total_loss = 0.0
    n = 0
    speed_abs_errors = []
    zupt_correct = 0
    zupt_total = 0

    with torch.no_grad():
        for x, targets in loader:
            x = x.to(device)
            velocity_t = targets["velocity"].to(device)
            zupt_t = targets["zupt"].to(device)
            bias_t = targets["bias_target"].to(device)
            bias_mask = targets["bias_mask"].to(device)

            out = model(x)
            for name, value in out.items():
                if torch.is_tensor(value):
                    if not torch.isfinite(value).all():
                        raise RuntimeError(
                            f"[ERROR] Non-finite model output detected: {name}"
                        )

            l_vel = huber(out["velocity"], velocity_t)
            zupt_prob = torch.clamp(
                out["zupt_prob"],
                min=1e-7,
                max=1.0 - 1e-7
            )

            l_zupt = nn.functional.binary_cross_entropy(
                zupt_prob,
                zupt_t.float()
            )
            
            l_nll = gaussian_nll_loss(out["velocity"], velocity_t, out["log_var"])
            l_bias = masked_bias_loss(out["bias"], bias_t, bias_mask, huber)

            loss = alpha * l_vel + beta * l_zupt + gamma * l_nll + delta * l_bias

            bs = x.size(0)
            total_loss += loss.item() * bs
            n += bs

            speed_abs_errors.extend(torch.abs(out["velocity"][:, 0] - velocity_t[:, 0]).cpu().numpy())
            zupt_pred_label = (out["zupt_prob"] > 0.5).float()
            zupt_correct += (zupt_pred_label == zupt_t).sum().item()
            zupt_total += zupt_t.numel()

    avg_loss = total_loss / max(n, 1)
    speed_mae_ms = float(np.mean(speed_abs_errors)) if speed_abs_errors else float("nan")
    zupt_acc = zupt_correct / max(zupt_total, 1)
    return avg_loss, speed_mae_ms, zupt_acc


def train_model(
    epochs: int = 25,
    batch_size: int = 64,
    lr: float = 1e-4,
    window_size: int = DEFAULT_WINDOW_SIZE,
    step: int = DEFAULT_STEP,
    alpha: float = 1.0,   # velocity (Huber) weight
    beta: float = 0.5,    # ZUPT (BCE) weight
    gamma: float = 0.3,   # uncertainty (Gaussian NLL) weight
    delta: float = 0.2,   # bias (masked Huber) weight
    save_path: str = "io_vnbd_model.pth",
    norm_stats_path: str = "normalization_stats.json",
):
    if not TORCH_AVAILABLE:
        print("[Error] PyTorch is required to run train.py. Install via: pip install torch numpy")
        return

    script_dir = os.path.dirname(os.path.abspath(__file__))
    if not os.path.isabs(save_path):
        save_path = os.path.join(script_dir, save_path)
    if not os.path.isabs(norm_stats_path):
        norm_stats_path = os.path.join(script_dir, norm_stats_path)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"[Training] Running on compute device: {device}")

    # -----------------------------------------------------------------
    # 1. Load sequences (kept separate so we can block-split each one)
    # -----------------------------------------------------------------
    data_dir = os.path.join(script_dir, "..", "data")
    sequences = load_iovnbd_sequences(default_sequence_pairs(data_dir))

    # -----------------------------------------------------------------
    # 2. Fit normalization on the TRAIN block only (no leakage), then
    #    persist it so predict.py/export.py/the Android app use the exact
    #    same normalization at inference time.
    # -----------------------------------------------------------------
    mean, std = compute_train_only_normalization(sequences, train_frac=0.70)
    save_normalization_stats(norm_stats_path, mean, std)
    print(f"[Training] Saved normalization stats -> {norm_stats_path}")

    # -----------------------------------------------------------------
    # 3. Contiguous block-wise stratified split (see dataset.py docstring)
    # -----------------------------------------------------------------
    train_dataset, val_dataset, test_dataset = contiguous_block_split(
        sequences, mean, std, train_frac=0.70, val_frac=0.15,
        window_size=window_size, step=step,
    )

    train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True, drop_last=True)
    val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False)
    test_loader = DataLoader(test_dataset, batch_size=batch_size, shuffle=False)

    print(
        f"[Dataset Split] Train: {len(train_dataset)} | "
        f"Validation: {len(val_dataset)} | Test: {len(test_dataset)}"
    )

    # -----------------------------------------------------------------
    # 4. Model / optimizer / scheduler
    # -----------------------------------------------------------------
    model = DeadReckoningMultiTaskModel(in_channels=10).to(device)
    huber = nn.HuberLoss(delta=1.0)
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)

    best_val_mae = float("inf")
    start_time = time.time()

    print("\n" + "=" * 78)
    print("   SIH26168 MULTI-TASK DEAD RECKONING TRAINING (CNN+BiGRU+Attention)")
    print("=" * 78)
    print(f"Loss weights -> alpha(vel)={alpha} beta(zupt)={beta} gamma(nll)={gamma} delta(bias)={delta}")

    for epoch in range(1, epochs + 1):
        model.train()
        train_loss = 0.0
        n_train = 0

        for x, targets in train_loader:
            x = x.to(device)
            velocity_t = targets["velocity"].to(device)
            zupt_t = targets["zupt"].to(device)
            bias_t = targets["bias_target"].to(device)
            bias_mask = targets["bias_mask"].to(device)

            optimizer.zero_grad()
            out = model(x)

            l_vel = huber(out["velocity"], velocity_t)
            zupt_prob = torch.clamp(
                out["zupt_prob"],
                min=1e-7,
                max=1.0 - 1e-7
            )

            l_zupt = nn.functional.binary_cross_entropy(
                zupt_prob,
                zupt_t.float()
            )
            l_nll = gaussian_nll_loss(out["velocity"], velocity_t, out["log_var"])
            l_bias = masked_bias_loss(out["bias"], bias_t, bias_mask, huber)

            loss = alpha * l_vel + beta * l_zupt + gamma * l_nll + delta * l_bias

            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()

            bs = x.size(0)
            train_loss += loss.item() * bs
            n_train += bs

        train_loss /= max(n_train, 1)
        scheduler.step()

        val_loss, val_speed_mae_ms, val_zupt_acc = evaluate(
            model, val_loader, device, huber, alpha, beta, gamma, delta
        )
        val_speed_mae_kmh = val_speed_mae_ms * 3.6

        is_best = val_speed_mae_ms < best_val_mae
        if is_best:
            best_val_mae = val_speed_mae_ms
            torch.save(model.state_dict(), save_path)

        star = " [BEST]" if is_best else ""
        print(
            f"Epoch [{epoch:02d}/{epochs:02d}] | Train Loss: {train_loss:.4f} | "
            f"Val Loss: {val_loss:.4f} | Val Speed MAE: {val_speed_mae_kmh:.2f} km/h "
            f"({val_speed_mae_ms:.3f} m/s) | Val ZUPT Acc: {val_zupt_acc * 100:.1f}%{star}"
        )

    elapsed = time.time() - start_time
    print("=" * 78)
    print(f"[Training Complete] Elapsed Time: {elapsed:.1f}s")
    print(f"[Best Performance] Validation Speed MAE: {best_val_mae * 3.6:.2f} km/h | Model saved to: {save_path}")

    # -----------------------------------------------------------------
    # 5. Final held-out test evaluation (contiguous block, never touched
    #    during training or model selection)
    # -----------------------------------------------------------------
    model.load_state_dict(torch.load(save_path, map_location=device))
    test_loss, test_speed_mae_ms, test_zupt_acc = evaluate(
        model, test_loader, device, huber, alpha, beta, gamma, delta
    )
    print(
        f"[Test Set] Loss: {test_loss:.4f} | Speed MAE: {test_speed_mae_ms * 3.6:.2f} km/h "
        f"({test_speed_mae_ms:.3f} m/s) | ZUPT Acc: {test_zupt_acc * 100:.1f}%"
    )
    print("=" * 78)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train SIH26168 Multi-Task AI Dead Reckoning Model")
    parser.add_argument("--epochs", type=int, default=25)
    parser.add_argument("--batch_size", type=int, default=64)
    parser.add_argument("--lr", type=float, default=1e-4)
    parser.add_argument("--window_size", type=int, default=DEFAULT_WINDOW_SIZE)
    parser.add_argument("--step", type=int, default=DEFAULT_STEP)
    parser.add_argument("--alpha", type=float, default=1.0, help="Velocity (Huber) loss weight")
    parser.add_argument("--beta", type=float, default=0.5, help="ZUPT (BCE) loss weight")
    parser.add_argument("--gamma", type=float, default=0.3, help="Uncertainty (Gaussian NLL) loss weight")
    parser.add_argument("--delta", type=float, default=0.2, help="Bias (masked Huber) loss weight")
    parser.add_argument("--save", type=str, default="io_vnbd_model.pth")
    parser.add_argument("--norm_stats", type=str, default="normalization_stats.json")
    args = parser.parse_args()

    train_model(
        epochs=args.epochs, batch_size=args.batch_size, lr=args.lr,
        window_size=args.window_size, step=args.step,
        alpha=args.alpha, beta=args.beta, gamma=args.gamma, delta=args.delta,
        save_path=args.save, norm_stats_path=args.norm_stats,
    )
