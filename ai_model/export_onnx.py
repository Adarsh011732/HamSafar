"""Compatibility entry point for Android-oriented ONNX export."""
from export import export_to_onnx
if __name__ == '__main__':
    import argparse
    p=argparse.ArgumentParser(description='Export the existing multitask model for ONNX Runtime / Android NNAPI')
    p.add_argument('--weights',default='io_vnbd_model.pth'); p.add_argument('--output',default='io_vnbd_model.onnx'); p.add_argument('--window_size',type=int,default=200)
    a=p.parse_args(); export_to_onnx(a.weights,a.output,a.window_size)
