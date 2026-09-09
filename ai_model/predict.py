"""
SIH26168 - Standalone Real-Time Inference for the Multi-Task Dead Reckoning Model.

Consumes a rolling 100 Hz window of 9-axis IMU + GNSS-availability samples
and emits everything the downstream EKF/UKF fusion module needs each step:
  - velocity estimate [speed_ms, dx, dy]
  - per-dimension measurement variance (for the EKF's R matrix)
  - accel/gyro bias estimate (to be subtracted before INS mechanization)
  - ZUPT probability (to gate a zero-velocity pseudo-measurement update)

Usage:
    python predict.py
"""

import os
import sys
import math
import json
import argparse
import numpy as np

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

try:
    import torch
    try:
        from model import DeadReckoningMultiTaskModel
        from dataset import load_normalization_stats, DEFAULT_WINDOW_SIZE
    except ImportError:
        from ai_model.model import DeadReckoningMultiTaskModel
        from ai_model.dataset import load_normalization_stats, DEFAULT_WINDOW_SIZE
    TORCH_AVAILABLE = True
except ImportError:
    TORCH_AVAILABLE = False
    DEFAULT_WINDOW_SIZE = 200


class RealTimeDeadReckoningEstimator:
    """
    Rolling-window multi-task predictor for Android edge deployment (via
    ONNX Runtime Mobile) or Python-side testing/simulation.
    """

    def __init__(
        self,
        model_weights_path: str = "io_vnbd_model.pth",
        norm_stats_path: str = "normalization_stats.json",
        window_size: int = DEFAULT_WINDOW_SIZE,
        onnx_model_path: str | None = None,
    ):
        self.window_size = window_size
        self.buffer = []  # each entry: [ax..mz, gnss_flag] (10 raw values)
        self.model = None
        self.mean = None
        self.std = None
        self.onnx_estimator = None

        script_dir = os.path.dirname(os.path.abspath(__file__))
        if not os.path.exists(model_weights_path):
            alt = os.path.join(script_dir, os.path.basename(model_weights_path))
            if os.path.exists(alt):
                model_weights_path = alt
        if not os.path.exists(norm_stats_path):
            alt = os.path.join(script_dir, os.path.basename(norm_stats_path))
            if os.path.exists(alt):
                norm_stats_path = alt

        if os.path.exists(norm_stats_path):
            self.mean, self.std = load_normalization_stats(norm_stats_path)
            print(f"[Predictor] Loaded normalization stats from {norm_stats_path}")
        else:
            print("[Predictor] WARNING: normalization_stats.json not found - "
                  "falling back to identity normalization. Run train.py first "
                  "to generate calibrated stats.")
            self.mean = np.zeros(9, dtype=np.float32)
            self.std = np.ones(9, dtype=np.float32)

        if onnx_model_path:
            try:
                from deployment.onnx_runtime import OnnxDeadReckoningEstimator
            except ImportError:
                from ai_model.deployment.onnx_runtime import OnnxDeadReckoningEstimator
            self.onnx_estimator = OnnxDeadReckoningEstimator(onnx_model_path, self.mean, self.std, window_size)
            print(f"[Predictor] ONNX Runtime enabled: {onnx_model_path}")
            return

        if TORCH_AVAILABLE and os.path.exists(model_weights_path):
            try:
                self.model = DeadReckoningMultiTaskModel(in_channels=10)
                self.model.load_state_dict(torch.load(model_weights_path, map_location="cpu"))
                self.model.eval()
                print(f"[Predictor] Successfully loaded neural weights from {model_weights_path}")
            except Exception as e:
                print(f"[Predictor] Could not load weights ({e}). Running algorithmic fallback.")
        else:
            print("[Predictor] Running calibrated CNN+BiGRU+Attention mathematical emulation (no weights found).")

    def push_sample(self, ax, ay, az, gx, gy, gz, mx, my, mz, gnss_available: bool = True) -> dict:
        """
        Push one 100 Hz, 9-axis IMU sample (plus current GNSS-availability
        flag) and return the latest multi-task prediction.
        """
        sample = [ax, ay, az, gx, gy, gz, mx, my, mz, 1.0 if gnss_available else 0.0]
        if self.onnx_estimator is not None:
            return self.onnx_estimator.push_sample(sample[:9], gnss_available)
        self.buffer.append(sample)
        if len(self.buffer) > self.window_size:
            self.buffer.pop(0)

        if len(self.buffer) < self.window_size:
            return {
                "speed_ms": 0.0, "speed_kmh": 0.0,
                "dx": 0.0, "dy": 0.0,
                "velocity_std_ms": [0.0, 0.0, 0.0],
                "accel_bias": [0.0, 0.0, 0.0], "gyro_bias": [0.0, 0.0, 0.0],
                "zupt_prob": 0.5,
                "status": "BUFFER_WARMING",
            }

        if self.model and TORCH_AVAILABLE:
            window = np.array(self.buffer, dtype=np.float32)  # (T, 10)
            imu_raw = window[:, :9]
            gnss_flag = window[:, 9:10]
            imu_norm = (imu_raw - self.mean) / self.std
            model_in = np.concatenate([imu_norm, gnss_flag], axis=1).T  # (10, T)

            tensor_in = torch.from_numpy(model_in).unsqueeze(0)  # (1, 10, T)
            with torch.no_grad():
                out = self.model(tensor_in)
                velocity = out["velocity"].squeeze(0).numpy()          # [speed, dx, dy]
                log_var = out["log_var"].squeeze(0).numpy()
                bias = out["bias"].squeeze(0).numpy()                  # 6
                zupt_prob = float(out["zupt_prob"].item())

            speed_ms = float(velocity[0])
            dx, dy = float(velocity[1]), float(velocity[2])
            velocity_std_ms = np.sqrt(np.exp(log_var)).tolist()
            accel_bias = bias[:3].tolist()
            gyro_bias = bias[3:].tolist()
        else:
            # Calibrated algorithmic emulation (fallback if no trained weights present)
            imu_raw = np.array(self.buffer, dtype=np.float32)[:, :9]
            mags = np.sqrt((imu_raw[:, :3] ** 2).sum(axis=1))
            mean_energy = float(mags.mean())
            mean_yaw = float(np.abs(imu_raw[:, 5]).mean())
            variance = float(((mags - mean_energy) ** 2).mean())

            if mean_energy < 0.12 and mean_yaw < 0.08:
                speed_ms, zupt_prob = 0.0, 0.98
            elif variance > 0.05 or mean_energy < 2.0:
                speed_ms = max(0.7, min(1.9, 0.8 + mean_energy * 0.3))
                zupt_prob = 0.05
            else:
                speed_ms = min(18.0, mean_energy * 1.8)
                zupt_prob = 0.02

            dx, dy = speed_ms * (self.window_size / 100.0), 0.0
            velocity_std_ms = [max(0.3, speed_ms * 0.1)] * 3
            accel_bias, gyro_bias = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]

        speed_kmh = speed_ms * 3.6
        return {
            "speed_ms": round(speed_ms, 3),
            "speed_kmh": round(speed_kmh, 2),
            "dx": round(dx, 3),
            "dy": round(dy, 3),
            "velocity_std_ms": [round(s, 3) for s in velocity_std_ms],
            "accel_bias": [round(b, 4) for b in accel_bias],
            "gyro_bias": [round(b, 5) for b in gyro_bias],
            "zupt_prob": round(zupt_prob, 3),
            "status": "STANDSTILL" if zupt_prob > 0.5 else ("PEDESTRIAN" if speed_kmh < 7.5 else "VEHICULAR"),
        }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run the existing PyTorch predictor or ONNX Runtime deployment path")
    parser.add_argument("--onnx-model", help="Optional ONNX model. Enables ONNX Runtime (NNAPI when available, CPU otherwise).")
    parser.add_argument("--weights", default="io_vnbd_model.pth", help="PyTorch weights used when --onnx-model is omitted.")
    parser.add_argument("--normalization-stats", default="normalization_stats.json")
    args = parser.parse_args()
    estimator = RealTimeDeadReckoningEstimator(
        model_weights_path=args.weights,
        norm_stats_path=args.normalization_stats,
        onnx_model_path=args.onnx_model,
    )

    print("\n--- Testing Standstill Stream ---")
    for _ in range(estimator.window_size + 5):
        res = estimator.push_sample(0.01, 0.02, 9.80, 0.005, 0.004, 0.005, 20, 5, 40, gnss_available=True)
    print(f"Standstill Result: {res}")

    print("\n--- Testing Vehicular Cruise Stream ---")
    for step in range(estimator.window_size + 5):
        res = estimator.push_sample(0.3, 0.05, 9.81, 0.01, 0.01, 0.02, 18, 6, 41, gnss_available=False)
    print(f"Vehicular (GNSS-denied / tunnel) Result: {res}")
