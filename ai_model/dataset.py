"""
SIH26168 - AI/ML Based Intelligent Dead Reckoning System
IO-VNBD Multi-Task Dataset Loader & Preprocessor.

Upgrades over the original single-task loader:
  1. 9-axis IMU (accel + gyro + magnetometer) instead of 6-axis.
  2. A per-sample GNSS-availability flag channel (derived from GPS accuracy /
     satellite count) so the network learns to behave differently in
     GNSS-denied windows (tunnels, urban canyons) vs. open-sky windows.
  3. Uniform high-frequency resampling (default 100 Hz) to remove
     device-specific sampling jitter and hardware-dependent output rates.
  4. Sequence-wise Z-score standardization computed ONLY on the training
     sequences and re-used (never re-fit) on validation/test/inference to
     avoid data leakage.
  5. Multi-task targets required by the new model heads:
       - velocity:    [speed_ms, dx, dy]           (Huber regression)
       - zupt:        {0, 1} stationary flag        (BCE classification)
       - bias_target: [ax, ay, az, gx, gy, gz]      (masked regression,
                       only supervised during ZUPT==1 intervals - see notes)
       - bias_mask:   1.0 if this window may be used for bias supervision
  6. Contiguous block-wise train/val/test stratification *within* each
     trajectory (rather than handing whole files to a single split), so the
     validation set is representative of the full mix of driving behaviour
     (standstill / cruise / turning / braking) instead of whichever file
     happens to be held out.

Notes on target honesty
------------------------
The IO-VNBD reference "vehicle" logs give us reliable ground-truth speed
and heading, but NOT ground-truth IMU bias or ground-truth 2D position.
So:
  * dx, dy displacement targets are derived by integrating the reference
    speed over the window and projecting it along the reference heading.
    This is a real (if imperfect) supervisory signal, not a placeholder.
  * IMU bias has no available ground truth in general. We only supervise
    the bias head during intervals where the vehicle is truly stationary
    (ZUPT == 1): in that regime the true specific force/angular rate is
    (approximately) zero, so the raw sensor reading itself IS the bias +
    noise. Everywhere else, `bias_mask == 0` and the bias loss for that
    window is skipped in train.py. This is a standard technique in
    zero-velocity-aided INS calibration and is far more honest than
    inventing a synthetic bias label for every window.
"""

import os
import math
import json
import numpy as np
import pandas as pd

try:
    from navigation.frame_alignment import PhoneVehicleFrameAligner
except ImportError:
    from ai_model.navigation.frame_alignment import PhoneVehicleFrameAligner

try:
    import torch
    from torch.utils.data import Dataset
    TORCH_AVAILABLE = True
except ImportError:
    TORCH_AVAILABLE = False

    class Dataset:  # minimal mock so the module still imports without torch
        pass


# =====================================================================
# Configuration constants
# =====================================================================

TARGET_HZ = 100.0                 # Uniform resampling rate requested by SIH spec
ZUPT_SPEED_THRESHOLD_MS = 0.30    # Below this reference speed -> "stationary"
GNSS_SAT_THRESHOLD = 4            # Fewer visible satellites -> GNSS considered unavailable
GNSS_ACCURACY_THRESHOLD_M = 15.0  # Reported horizontal accuracy worse than this -> unavailable

IMU_CHANNELS = 9   # ax, ay, az, gx, gy, gz, mx, my, mz
INPUT_CHANNELS = IMU_CHANNELS + 1  # + GNSS-availability flag channel

DEFAULT_WINDOW_SIZE = 200   # 200 samples @ 100 Hz = 2.0 s window
DEFAULT_STEP = 20           # 90% overlap between consecutive windows


# =====================================================================
# Low level signal processing helpers
# =====================================================================

def resample_to_uniform_grid(timestamps: np.ndarray, signals: np.ndarray, target_hz: float = TARGET_HZ):
    """
    Linearly resample one or more (possibly jittery, variable-rate) signals
    onto a uniform time grid at `target_hz`.

    Args:
        timestamps: (N,) monotonically increasing time in seconds.
        signals:    (N, C) raw signal columns aligned with `timestamps`.
                    Non-interpolatable columns (e.g. boolean flags) should
                    be resampled with nearest-neighbour separately - see
                    `resample_flag_to_uniform_grid`.
        target_hz:  output sample rate in Hz.

    Returns:
        grid_time: (M,) uniform timestamps.
        grid_signals: (M, C) resampled signals.
    """
    if len(timestamps) < 2:
        raise ValueError("Need at least 2 samples to resample a sequence.")

    dt = 1.0 / target_hz
    t0, t1 = timestamps[0], timestamps[-1]
    grid_time = np.arange(t0, t1, dt)

    grid_signals = np.empty((len(grid_time), signals.shape[1]), dtype=np.float32)
    for c in range(signals.shape[1]):
        grid_signals[:, c] = np.interp(grid_time, timestamps, signals[:, c])

    return grid_time, grid_signals


def resample_flag_to_uniform_grid(timestamps: np.ndarray, flag: np.ndarray, grid_time: np.ndarray):
    """
    Nearest-neighbour resample for discrete/boolean signals (e.g. GNSS
    availability), so we don't invent fractional "half available" values.
    """
    idx = np.searchsorted(timestamps, grid_time)
    idx = np.clip(idx, 0, len(timestamps) - 1)
    # searchsorted gives the insertion point; snap to the closer neighbour
    idx_prev = np.clip(idx - 1, 0, len(timestamps) - 1)
    use_prev = np.abs(timestamps[idx_prev] - grid_time) <= np.abs(timestamps[idx] - grid_time)
    idx_final = np.where(use_prev, idx_prev, idx)
    return flag[idx_final].astype(np.float32)


def compute_normalization_stats(imu_features: np.ndarray):
    """
    Sequence-wise Z-score statistics for the 9 IMU channels ONLY.
    The GNSS-availability flag is left un-normalized (it is already in {0,1}).

    Returns:
        mean: (9,) float32
        std:  (9,) float32 (floor applied to avoid divide-by-zero on flat axes)
    """
    mean = imu_features.mean(axis=0).astype(np.float32)
    std = imu_features.std(axis=0).astype(np.float32)
    std = np.maximum(std, 1e-6)
    return mean, std


def apply_normalization(imu_features: np.ndarray, mean: np.ndarray, std: np.ndarray):
    return (imu_features - mean) / std


def save_normalization_stats(path: str, mean: np.ndarray, std: np.ndarray):
    with open(path, "w") as f:
        json.dump({"mean": mean.tolist(), "std": std.tolist()}, f, indent=2)


def load_normalization_stats(path: str):
    with open(path, "r") as f:
        d = json.load(f)
    return np.array(d["mean"], dtype=np.float32), np.array(d["std"], dtype=np.float32)


# =====================================================================
# CSV loading (IO-VNBD smartphone + reference-vehicle pairs)
# =====================================================================

def load_iovnbd_csv(smartphone_path: str, vehicle_path: str):
    """
    Load one synchronized IO-VNBD smartphone/vehicle CSV pair and produce
    a uniformly-resampled, fully-labeled sequence.

    Returns a dict:
        {
          "time":        (M,)   seconds, uniform grid @ TARGET_HZ
          "imu":         (M, 9) ax, ay, az, gx, gy, gz, mx, my, mz  (raw units)
          "gnss_flag":   (M,)   1.0 = GNSS available, 0.0 = denied
          "speed_ms":    (M,)   reference vehicle speed
          "heading_rad": (M,)   reference vehicle heading (radians)
          "zupt":        (M,)   1.0 if stationary (speed < threshold)
        }
    """
    print(f"[IO-VNBD] Loading smartphone: {smartphone_path}")
    print(f"[IO-VNBD] Loading vehicle:    {vehicle_path}")

    smartphone = pd.read_csv(smartphone_path, encoding="cp1252")
    vehicle = pd.read_csv(vehicle_path, encoding="cp1252")

    smartphone.columns = smartphone.columns.str.strip()
    vehicle.columns = vehicle.columns.str.strip()

    print(f"[IO-VNBD] Smartphone rows: {len(smartphone)}")
    print(f"[IO-VNBD] Vehicle rows:    {len(vehicle)}")

    # -----------------------------------------------------------------
    # Column mapping
    # -----------------------------------------------------------------
    phone_time_col = "TIME SINCE START (ms)"

    # NOTE: The IO-VNBD CSV export is UTF-8 encoded but this loader reads it
    # with encoding="cp1252" (matching the original script, since other
    # columns only round-trip correctly that way). That mismatch mangles
    # the degree/micro symbols unpredictably across exports/locales (e.g.
    # "μT" -> "Î¼T" or similar mojibake). Rather than hard-coding one
    # specific mis-decoding, we look up the accelerometer/gyroscope columns
    # by their stable ASCII prefixes and the magnetometer columns by
    # prefix-match on "MAGNETIC FIELD <axis>", which is robust regardless
    # of how the unit suffix garbled.
    def _find_column(df, prefix):
        for col in df.columns:
            if col.strip().upper().startswith(prefix.upper()):
                return col
        return None

    accel_cols = [_find_column(smartphone, f"ACCELEROMETER {axis}") for axis in ("X", "Y", "Z")]
    gyro_cols = [_find_column(smartphone, f"GYROSCOPE {axis}") for axis in ("Yaw", "Pitch", "Roll")]
    mag_cols = [_find_column(smartphone, f"MAGNETIC FIELD {axis}") for axis in ("X", "Y", "Z")]
    phone_imu_cols = accel_cols + gyro_cols + mag_cols

    if any(c is None for c in phone_imu_cols):
        raise ValueError(
            "Could not locate all 9 IMU columns (accel/gyro/mag) in "
            f"smartphone CSV. Found: {phone_imu_cols}\nAvailable columns: {list(smartphone.columns)}"
        )

    # GNSS quality columns used to derive the availability flag.
    # Column names vary slightly release to release, so we look them up
    # defensively instead of hard-failing.
    phone_gnss_sat_col = "GPS SATELLITES IN RANGE"
    phone_gnss_acc_col = "GPS ACCURACY (m)"

    vehicle_time_col = "Time Since Start of Day (seconds)"
    vehicle_speed_col = "Indicated Vehicle Speed (km/hr)"
    vehicle_heading_col = "Heading (degrees)"

    if phone_time_col not in smartphone.columns:
        raise ValueError(
            f"\nMissing smartphone column:\n{phone_time_col}\n\nAvailable columns:\n{list(smartphone.columns)}"
        )

    for col in [vehicle_time_col, vehicle_speed_col]:
        if col not in vehicle.columns:
            raise ValueError(
                f"\nMissing vehicle column:\n{col}\n\nAvailable columns:\n{list(vehicle.columns)}"
            )

    has_heading = vehicle_heading_col in vehicle.columns
    if not has_heading:
        print(f"[IO-VNBD] Warning: '{vehicle_heading_col}' not found - dx/dy targets will fall back to forward-only displacement.")

    has_sat_col = phone_gnss_sat_col in smartphone.columns
    has_acc_col = phone_gnss_acc_col in smartphone.columns

    # -----------------------------------------------------------------
    # Smartphone: time + 9-axis IMU
    # -----------------------------------------------------------------
    phone_time = pd.to_numeric(smartphone[phone_time_col], errors="coerce").to_numpy() / 1000.0
    imu = smartphone[phone_imu_cols].apply(pd.to_numeric, errors="coerce").to_numpy()

    if has_sat_col:
        sats = pd.to_numeric(smartphone[phone_gnss_sat_col], errors="coerce").to_numpy()
    else:
        sats = np.full(len(smartphone), np.nan)

    if has_acc_col:
        acc_m = pd.to_numeric(smartphone[phone_gnss_acc_col], errors="coerce").to_numpy()
    else:
        acc_m = np.full(len(smartphone), np.nan)

    # Derive a boolean GNSS-availability signal. If neither quality column
    # exists, default to "always available" (flag == 1) since we have no
    # basis to mark it unavailable - this keeps the feature well-defined
    # even for datasets that don't log GNSS quality per-sample.
    if has_sat_col or has_acc_col:
        sat_ok = np.where(np.isnan(sats), True, sats >= GNSS_SAT_THRESHOLD)
        acc_ok = np.where(np.isnan(acc_m), True, acc_m <= GNSS_ACCURACY_THRESHOLD_M)
        gnss_flag_raw = (sat_ok & acc_ok).astype(np.float32)
    else:
        gnss_flag_raw = np.ones(len(smartphone), dtype=np.float32)

    valid = np.isfinite(phone_time) & np.all(np.isfinite(imu), axis=1)
    phone_time, imu, gnss_flag_raw = phone_time[valid], imu[valid], gnss_flag_raw[valid]

    order = np.argsort(phone_time)
    phone_time, imu, gnss_flag_raw = phone_time[order], imu[order], gnss_flag_raw[order]

    unique_mask = np.concatenate(([True], np.diff(phone_time) > 0))
    phone_time, imu, gnss_flag_raw = phone_time[unique_mask], imu[unique_mask], gnss_flag_raw[unique_mask]

    # -----------------------------------------------------------------
    # Vehicle: time + speed (+ heading)
    # -----------------------------------------------------------------
    vehicle_time = pd.to_numeric(vehicle[vehicle_time_col], errors="coerce").to_numpy()
    vehicle_speed_kmh = pd.to_numeric(vehicle[vehicle_speed_col], errors="coerce").to_numpy()
    if has_heading:
        vehicle_heading_deg = pd.to_numeric(vehicle[vehicle_heading_col], errors="coerce").to_numpy()
    else:
        vehicle_heading_deg = np.zeros(len(vehicle), dtype=np.float32)

    v_valid = np.isfinite(vehicle_time) & np.isfinite(vehicle_speed_kmh)
    vehicle_time = vehicle_time[v_valid]
    vehicle_speed_kmh = vehicle_speed_kmh[v_valid]
    vehicle_heading_deg = vehicle_heading_deg[v_valid]

    v_order = np.argsort(vehicle_time)
    vehicle_time = vehicle_time[v_order]
    vehicle_speed_kmh = vehicle_speed_kmh[v_order]
    vehicle_heading_deg = vehicle_heading_deg[v_order]

    v_unique = np.concatenate(([True], np.diff(vehicle_time) > 0))
    vehicle_time = vehicle_time[v_unique]
    vehicle_speed_kmh = vehicle_speed_kmh[v_unique]
    vehicle_heading_deg = vehicle_heading_deg[v_unique]

    # -----------------------------------------------------------------
    # Synchronize timelines (relative time, overlap only - no extrapolation)
    # -----------------------------------------------------------------
    phone_rel = phone_time - phone_time[0]
    vehicle_rel = vehicle_time - vehicle_time[0]

    overlap_start = max(phone_rel[0], vehicle_rel[0])
    overlap_end = min(phone_rel[-1], vehicle_rel[-1])
    if overlap_end <= overlap_start:
        raise ValueError("Smartphone and vehicle recordings have no overlapping time interval.")

    print(f"[IO-VNBD] Overlap duration: {overlap_end - overlap_start:.3f} s")

    phone_mask = (phone_rel >= overlap_start) & (phone_rel <= overlap_end)
    phone_rel = phone_rel[phone_mask]
    imu = imu[phone_mask]
    gnss_flag_raw = gnss_flag_raw[phone_mask]

    # -----------------------------------------------------------------
    # Resample everything onto a uniform TARGET_HZ grid
    # -----------------------------------------------------------------
    grid_time, imu_grid = resample_to_uniform_grid(phone_rel, imu, target_hz=TARGET_HZ)
    gnss_flag_grid = resample_flag_to_uniform_grid(phone_rel, gnss_flag_raw, grid_time)

    # Interpolate reference speed/heading onto the same grid (still inside overlap)
    speed_kmh_grid = np.interp(grid_time, vehicle_rel, vehicle_speed_kmh)
    speed_ms_grid = (speed_kmh_grid / 3.6).astype(np.float32)

    heading_rad_grid = np.interp(
        grid_time, vehicle_rel, np.unwrap(np.deg2rad(vehicle_heading_deg))
    ).astype(np.float32)

    zupt_grid = (speed_ms_grid < ZUPT_SPEED_THRESHOLD_MS).astype(np.float32)

    print(f"[IO-VNBD] Resampled to {TARGET_HZ:.0f} Hz -> {len(grid_time)} samples")
    print(f"[IO-VNBD] Target speed range: {speed_ms_grid.min():.2f} - {speed_ms_grid.max():.2f} m/s")
    print(f"[IO-VNBD] Stationary (ZUPT) fraction: {zupt_grid.mean() * 100:.1f}%")
    print(f"[IO-VNBD] GNSS available fraction: {gnss_flag_grid.mean() * 100:.1f}%")

    return {
        "time": grid_time.astype(np.float32),
        "imu": imu_grid.astype(np.float32),
        "gnss_flag": gnss_flag_grid.astype(np.float32),
        "speed_ms": speed_ms_grid,
        "heading_rad": heading_rad_grid,
        "zupt": zupt_grid,
    }


def load_iovnbd_sequences(sequence_pairs):
    """Load multiple synchronized IO-VNBD sequences, kept separate for
    trajectory-aware splitting."""
    sequences = []
    for smartphone_path, vehicle_path in sequence_pairs:
        seq = load_iovnbd_csv(smartphone_path, vehicle_path)
        sequences.append(seq)
        print(f"[IO-VNBD] Sequence loaded: {os.path.basename(smartphone_path)} ({len(seq['time'])} samples)")

    print("\n========== MULTI-SEQUENCE SUMMARY ==========")
    print(f"Number of sequences: {len(sequences)}")
    for i, seq in enumerate(sequences):
        print(f"Sequence {i + 1}: {len(seq['time'])} samples @ {TARGET_HZ:.0f} Hz")
    print("=============================================")

    return sequences


def generate_synthetic_iovnbd_trajectory(duration_sec: float = 120.0, freq_hz: float = TARGET_HZ):
    """
    Synthetic 9-axis fallback trajectory generator (used only when no real
    CSV pairs are available, e.g. for a quick architecture smoke test).
    Produces the same dict schema as `load_iovnbd_csv`.
    """
    total_steps = int(duration_sec * freq_hz)
    dt = 1.0 / freq_hz
    t = np.linspace(0, duration_sec, total_steps)

    speed_ms = np.zeros(total_steps)
    yaw_rate = np.zeros(total_steps)
    heading = np.zeros(total_steps)

    for i, ti in enumerate(t):
        if ti < 15.0:
            speed_ms[i] = 0.0
        elif ti < 35.0:
            speed_ms[i] = (ti - 15.0) * 0.6
        elif ti < 60.0:
            speed_ms[i] = 12.0 + 0.5 * math.sin(ti * 0.5)
        elif ti < 75.0:
            speed_ms[i] = 10.0
            yaw_rate[i] = 0.25 * math.sin((ti - 60.0) / 15.0 * math.pi)
        elif ti < 95.0:
            speed_ms[i] = max(0.0, 10.0 - (ti - 75.0) * 0.5)
        else:
            speed_ms[i] = 0.0
        if i > 0:
            heading[i] = heading[i - 1] + yaw_rate[i] * dt

    accel_fwd = np.gradient(speed_ms, dt)
    accel_lat = speed_ms * yaw_rate

    rng = np.random.default_rng(0)
    ax = accel_fwd + rng.normal(0, 0.04, total_steps) + 0.02   # + fixed bias
    ay = accel_lat + rng.normal(0, 0.04, total_steps) - 0.01
    az = rng.normal(0, 0.03, total_steps) + 9.81
    gx = rng.normal(0, 0.01, total_steps) + 0.005
    gy = rng.normal(0, 0.01, total_steps) - 0.003
    gz = yaw_rate + rng.normal(0, 0.015, total_steps)
    mx = 20.0 * np.cos(heading) + rng.normal(0, 1.0, total_steps)
    my = 20.0 * np.sin(heading) + rng.normal(0, 1.0, total_steps)
    mz = rng.normal(0, 1.0, total_steps) + 40.0

    imu = np.column_stack([ax, ay, az, gx, gy, gz, mx, my, mz]).astype(np.float32)
    gnss_flag = np.ones(total_steps, dtype=np.float32)
    gnss_flag[int(0.5 * total_steps):int(0.65 * total_steps)] = 0.0  # simulate a "tunnel"

    zupt = (speed_ms < ZUPT_SPEED_THRESHOLD_MS).astype(np.float32)

    return {
        "time": t.astype(np.float32),
        "imu": imu,
        "gnss_flag": gnss_flag,
        "speed_ms": speed_ms.astype(np.float32),
        "heading_rad": heading.astype(np.float32),
        "zupt": zupt,
    }


# =====================================================================
# Windowing / multi-task target construction
# =====================================================================

def _windows_from_sequence(seq: dict, window_size: int, step: int, mean: np.ndarray, std: np.ndarray):
    """
    Slice one uniformly-sampled sequence into overlapping windows and build
    every multi-task target array.

    Returns a dict of stacked numpy arrays, one row per window:
        model_input:  (W, 10, window_size)   normalized IMU (9) + gnss flag (1)
        velocity:     (W, 3)                 [speed_ms, dx, dy] at window end
        zupt:         (W, 1)
        bias_target:  (W, 6)                 [ax, ay, az, gx, gy, gz] raw units
        bias_mask:    (W, 1)                 1.0 if window is usable for bias supervision
    """
    imu_raw = seq["imu"]                      # (N, 9) raw units
    imu_norm = apply_normalization(imu_raw, mean, std)
    gnss_flag = seq["gnss_flag"]              # (N,)
    speed_ms = seq["speed_ms"]                # (N,)
    heading_rad = seq["heading_rad"]          # (N,)
    zupt = seq["zupt"]                        # (N,)
    dt = 1.0 / TARGET_HZ

    n = len(speed_ms)
    model_inputs, velocities, zupts, bias_targets, bias_masks = [], [], [], [], []

    for start in range(0, n - window_size + 1, step):
        end = start + window_size

        win_imu_norm = imu_norm[start:end]            # (T, 9)
        win_gnss = gnss_flag[start:end]                # (T,)
        win_speed = speed_ms[start:end]
        win_heading = heading_rad[start:end]
        win_zupt = zupt[start:end]
        win_imu_raw = imu_raw[start:end]

        # ---- model input: (channels, time) ----
        stacked = np.concatenate([win_imu_norm, win_gnss[:, None]], axis=1)  # (T, 10)
        model_inputs.append(stacked.T)  # (10, T)

        # ---- velocity head target: speed + forward-projected displacement ----
        end_speed = win_speed[-1]
        # distance traveled during the window, integrated from reference speed
        delta_s = float(np.trapezoid(win_speed, dx=dt)) if hasattr(np, "trapezoid") else float(np.trapz(win_speed, dx=dt))
        mean_heading = win_heading[-1]  # use end-of-window heading for the projection
        dx = delta_s * math.sin(mean_heading)  # East component
        dy = delta_s * math.cos(mean_heading)  # North component
        velocities.append([end_speed, dx, dy])

        # ---- ZUPT classification target: stationary at window end ----
        zupts.append([win_zupt[-1]])

        # ---- bias supervision: only valid if the ENTIRE window is stationary ----
        is_fully_stationary = float(np.all(win_zupt > 0.5))
        if is_fully_stationary:
            # Estimate bias in the dynamically learned vehicle frame. This
            # avoids the invalid old assumption that the handset Z axis is up.
            aligner = PhoneVehicleFrameAligner()
            for sample in win_imu_raw[:, :3]:
                aligner.update(sample, stationary=True)
            bias_vec = aligner.stationary_bias(win_imu_raw[:, :3], win_imu_raw[:, 3:6]).tolist()
        else:
            bias_vec = [0.0] * 6
        bias_targets.append(bias_vec)
        bias_masks.append([is_fully_stationary])

    return {
        "model_input": np.array(model_inputs, dtype=np.float32),
        "velocity": np.array(velocities, dtype=np.float32),
        "zupt": np.array(zupts, dtype=np.float32),
        "bias_target": np.array(bias_targets, dtype=np.float32),
        "bias_mask": np.array(bias_masks, dtype=np.float32),
    }


class IOVNBDMultiTaskDataset(Dataset):
    """
    Multi-task windowed dataset consumed by the training loop.

    __getitem__ returns:
        x: (10, window_size) tensor - normalized IMU (9) + GNSS flag (1)
        targets: dict of tensors:
            "velocity":    (3,)
            "zupt":        (1,)
            "bias_target": (6,)
            "bias_mask":   (1,)
    """

    def __init__(self, windows: dict):
        self.model_input = windows["model_input"]
        self.velocity = windows["velocity"]
        self.zupt = windows["zupt"]
        self.bias_target = windows["bias_target"]
        self.bias_mask = windows["bias_mask"]

    def __len__(self):
        return len(self.model_input)

    def __getitem__(self, idx):
        x = self.model_input[idx]
        targets = {
            "velocity": self.velocity[idx],
            "zupt": self.zupt[idx],
            "bias_target": self.bias_target[idx],
            "bias_mask": self.bias_mask[idx],
        }
        if TORCH_AVAILABLE:
            x_t = torch.from_numpy(x)
            targets_t = {k: torch.from_numpy(v) for k, v in targets.items()}
            return x_t, targets_t
        return x, targets

    @staticmethod
    def concat(datasets):
        """Concatenate several IOVNBDMultiTaskDataset instances into one."""
        merged = {
            "model_input": np.concatenate([d.model_input for d in datasets], axis=0),
            "velocity": np.concatenate([d.velocity for d in datasets], axis=0),
            "zupt": np.concatenate([d.zupt for d in datasets], axis=0),
            "bias_target": np.concatenate([d.bias_target for d in datasets], axis=0),
            "bias_mask": np.concatenate([d.bias_mask for d in datasets], axis=0),
        }
        return IOVNBDMultiTaskDataset(merged)


def build_windows_for_sequences(sequences, indices, mean, std, window_size=DEFAULT_WINDOW_SIZE, step=DEFAULT_STEP):
    """Build one IOVNBDMultiTaskDataset from selected whole sequences."""
    per_seq_datasets = []
    for idx in indices:
        windows = _windows_from_sequence(sequences[idx], window_size, step, mean, std)
        per_seq_datasets.append(IOVNBDMultiTaskDataset(windows))
        print(f"[IO-VNBD] Sequence {idx + 1}: {len(per_seq_datasets[-1])} windows")
    if not per_seq_datasets:
        raise ValueError("No sequences selected.")
    return IOVNBDMultiTaskDataset.concat(per_seq_datasets)


def compute_train_only_normalization(sequences, train_frac: float = 0.70):
    """
    Fit Z-score statistics using ONLY the first `train_frac` (chronological)
    portion of every sequence - i.e. exactly the data that will end up in
    the training block of `contiguous_block_split`. This must be called
    BEFORE windowing/splitting so that validation/test statistics never
    leak into the normalization.
    """
    train_chunks = []
    for seq in sequences:
        n = len(seq["imu"])
        train_end = int(n * train_frac)
        train_chunks.append(seq["imu"][:train_end])
    all_train_imu = np.concatenate(train_chunks, axis=0)
    return compute_normalization_stats(all_train_imu)


def contiguous_block_split(sequences, mean, std, train_frac=0.70, val_frac=0.15,
                            window_size=DEFAULT_WINDOW_SIZE, step=DEFAULT_STEP):
    """
    Validation fix requested by the SIH review: instead of holding out whole
    trajectory FILES for validation/test (which can accidentally give you a
    validation set that is only "turning" or only "highway cruising"),
    carve each sequence into three CONTIGUOUS time blocks
    [0, train_frac), [train_frac, train_frac+val_frac), [train_frac+val_frac, 1)
    and pool the corresponding blocks across all sequences.

    This keeps windows strictly non-overlapping across the split boundary
    (no leakage) while ensuring train/val/test each see a realistic mix of
    standstill, cruising, turning, and braking behaviour from every route.
    """
    train_sets, val_sets, test_sets = [], [], []

    for i, seq in enumerate(sequences):
        n = len(seq["time"])
        train_end = int(n * train_frac)
        val_end = int(n * (train_frac + val_frac))

        def _slice(seq, a, b):
            return {k: v[a:b] for k, v in seq.items()}

        seq_train = _slice(seq, 0, train_end)
        seq_val = _slice(seq, train_end, val_end)
        seq_test = _slice(seq, val_end, n)

        if len(seq_train["time"]) >= window_size:
            train_sets.append(IOVNBDMultiTaskDataset(
                _windows_from_sequence(seq_train, window_size, step, mean, std)))
        if len(seq_val["time"]) >= window_size:
            val_sets.append(IOVNBDMultiTaskDataset(
                _windows_from_sequence(seq_val, window_size, step, mean, std)))
        if len(seq_test["time"]) >= window_size:
            test_sets.append(IOVNBDMultiTaskDataset(
                _windows_from_sequence(seq_test, window_size, step, mean, std)))

        print(f"[Block Split] Sequence {i + 1}: train={len(seq_train['time'])} "
              f"val={len(seq_val['time'])} test={len(seq_test['time'])} samples")

    train_dataset = IOVNBDMultiTaskDataset.concat(train_sets)
    val_dataset = IOVNBDMultiTaskDataset.concat(val_sets)
    test_dataset = IOVNBDMultiTaskDataset.concat(test_sets)

    print(f"[Block Split] TOTAL windows -> train={len(train_dataset)} "
          f"val={len(val_dataset)} test={len(test_dataset)}")

    return train_dataset, val_dataset, test_dataset


def default_sequence_pairs(data_dir: str):
    names = ["S1", "S2", "S3a", "S3b", "S4"]
    return [(os.path.join(data_dir, f"S-{n}.csv"), os.path.join(data_dir, f"V-{n}.csv")) for n in names]


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--data_dir", type=str, default=os.path.join(os.path.dirname(__file__), "..", "data"))
    parser.add_argument("--synthetic", action="store_true", help="Use synthetic data instead of CSV files")
    args = parser.parse_args()

    if args.synthetic:
        sequences = [generate_synthetic_iovnbd_trajectory()]
    else:
        sequences = load_iovnbd_sequences(default_sequence_pairs(args.data_dir))

    # Fit normalization stats on the TRAIN portion only (first sequence's
    # first 70% here, just for this smoke test - train.py does this properly
    # across all training sequences).
    train_imu = sequences[0]["imu"][: int(0.7 * len(sequences[0]["imu"]))]
    mean, std = compute_normalization_stats(train_imu)
    print("Normalization mean:", mean)
    print("Normalization std: ", std)

    train_ds, val_ds, test_ds = contiguous_block_split(sequences, mean, std)
    print("\n========== FINAL DATASET SPLIT ==========")
    print(f"Train windows:      {len(train_ds)}")
    print(f"Validation windows: {len(val_ds)}")
    print(f"Test windows:       {len(test_ds)}")

    x, y = train_ds[0]
    print("\nSample model input shape:", x.shape if hasattr(x, "shape") else np.array(x).shape)
    print("Sample targets:", {k: (v.shape if hasattr(v, "shape") else v) for k, v in y.items()})
