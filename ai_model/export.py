"""
SIH26168 - ONNX Exporter for the Multi-Task Dead Reckoning Model.
Converts the trained PyTorch model to ONNX for ONNX Runtime Mobile / Android.

Usage:
    python export.py --weights io_vnbd_model.pth --output io_vnbd_model.onnx
"""

import os
import sys
import argparse

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

try:
    import torch
    try:
        from model import DeadReckoningMultiTaskModel
        from dataset import DEFAULT_WINDOW_SIZE
    except ImportError:
        from ai_model.model import DeadReckoningMultiTaskModel
        from ai_model.dataset import DEFAULT_WINDOW_SIZE
    TORCH_AVAILABLE = True
except ImportError:
    TORCH_AVAILABLE = False
    DEFAULT_WINDOW_SIZE = 200


def export_to_onnx(weights_path: str = "io_vnbd_model.pth", output_path: str = "io_vnbd_model.onnx",
                    window_size: int = DEFAULT_WINDOW_SIZE):
    if not TORCH_AVAILABLE:
        print("[Error] PyTorch is required to run export.py. Install via: pip install torch onnx onnxscript")
        return

    script_dir = os.path.dirname(os.path.abspath(__file__))
    if not os.path.exists(weights_path):
        alt_weights = os.path.join(script_dir, os.path.basename(weights_path))
        if os.path.exists(alt_weights):
            weights_path = alt_weights

    print("[ONNX Export] Instantiating multi-task Dead Reckoning model...")
    model = DeadReckoningMultiTaskModel(in_channels=10)

    if os.path.exists(weights_path):
        model.load_state_dict(torch.load(weights_path, map_location="cpu"))
        print(f"[ONNX Export] Loaded weights from {weights_path}")
    else:
        print(f"[ONNX Export] Warning: Weights file {weights_path} not found. Exporting randomly initialized model structure.")

    model.eval()

    # (batch=1, channels=10 [9-axis IMU + GNSS flag], time=window_size)
    dummy_input = torch.randn(1, 10, window_size, requires_grad=False)

    print(f"[ONNX Export] Exporting graph to: {output_path}...")
    torch.onnx.export(
        model,
        dummy_input,
        output_path,
        export_params=True,
        opset_version=17,
        do_constant_folding=True,
        input_names=["imu_window"],
        output_names=["velocity", "bias", "zupt_prob", "log_var", "attn_weights"],
        dynamic_axes={
            "imu_window": {0: "batch_size"},
            "velocity": {0: "batch_size"},
            "bias": {0: "batch_size"},
            "zupt_prob": {0: "batch_size"},
            "log_var": {0: "batch_size"},
            "attn_weights": {0: "batch_size"},
        },
        dynamo=False,
    )

    print(f"[ONNX Export] Export successful! Output file: {output_path}")
    print("[ONNX Export] Outputs: velocity=[speed_ms,dx,dy], bias=[ax,ay,az,gx,gy,gz], "
          "zupt_prob, log_var=[speed,dx,dy] (Var = exp(log_var)).")
    print("[ONNX Export] Remember to also ship normalization_stats.json to the Android app - "
          "raw IMU samples MUST be normalized with those exact stats before inference.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Export SIH26168 multi-task PyTorch model to ONNX")
    parser.add_argument("--weights", type=str, default="io_vnbd_model.pth")
    parser.add_argument("--output", type=str, default="io_vnbd_model.onnx")
    parser.add_argument("--window_size", type=int, default=DEFAULT_WINDOW_SIZE)
    args = parser.parse_args()

    export_to_onnx(args.weights, args.output, args.window_size)
