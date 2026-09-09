# IO-VNBD AI Inertial Odometry Pipeline (SIH26168)

This directory contains the Python machine learning training, evaluation, and export pipeline for the **IO-VNBD (Inertial Odometry for Vehicles Navigation Benchmark Dataset)** neural velocity estimator used in Chameleon Nav.

---

## Architecture Overview

```
IMU 6-Axis Window (40 samples @ 50 Hz = 0.8s context)
[ax, ay, az, gx, gy, gz]
           │
           ▼
┌───────────────────────────────────────┐
│ 1D-CNN Feature Extractor              │
│ • Conv1D(k=5, f=64) + BatchNorm + Act │
│ • Conv1D(k=3, f=128) + MaxPool1D      │
└───────────────────────────────────────┘
           │
           ▼
┌───────────────────────────────────────┐
│ 2-Layer Bidirectional GRU             │
│ • Hidden Size: 64 (Bidirectional=128) │
│ • Captures vehicular inertia & turns  │
└───────────────────────────────────────┘
           │
           ▼
┌───────────────────────────────────────┐
│ Dense Regression & Confidence Head    │
│ • Predicts forward velocity: v_fwd    │
│ • Predicts certainty: confidence      │
└───────────────────────────────────────┘
```

---

## File Structure

| File | Purpose |
| :--- | :--- |
| `model.py` | PyTorch implementation of the 1D-CNN + Bi-GRU neural network. |
| `dataset.py` | IO-VNBD CSV loader and realistic synthetic trajectory generator. |
| `train.py` | End-to-end training loop with Huber loss, AdamW, and MAE/RMSE validation. |
| `predict.py` | Standalone real-time inference script for testing raw IMU sequences. |
| `export.py` | Converts trained PyTorch checkpoints into universal ONNX format (`.onnx`). |
| `requirements.txt` | Python package dependencies. |

---

## Quickstart Guide

### 1. Install Dependencies
```bash
pip install -r requirements.txt
```

### 2. Train the Model
To train on the benchmark generator (or specify `--data path/to/iovnbd.csv`):
```bash
python train.py --epochs 25 --batch_size 32 --lr 0.001 --save io_vnbd_model.pth
```

### 3. Test Real-Time Inference
```bash
python predict.py
```

### 4. Export to ONNX for Mobile Deployment
```bash
python export.py --weights io_vnbd_model.pth --output io_vnbd_model.onnx
```
