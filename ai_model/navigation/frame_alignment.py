"""Phone-to-vehicle frame alignment with dynamic gravity compensation."""
from __future__ import annotations
import numpy as np
GRAVITY = 9.80665
def _unit(v, fallback):
    n = np.linalg.norm(v); return v / n if n > 1e-8 else fallback.copy()
class PhoneVehicleFrameAligner:
    """Learns vehicle axes from static gravity and linear acceleration; no phone-Z assumption."""
    def __init__(self, static_accel_threshold=.18, linear_accel_threshold=.35):
        self.static_accel_threshold, self.linear_accel_threshold = static_accel_threshold, linear_accel_threshold
        self.gravity_phone = np.array([0., 0., GRAVITY]); self.forward_phone = None; self.rotation_vehicle_from_phone = np.eye(3)
    def update(self, accel_phone, gyro_phone=None, stationary=False):
        accel = np.asarray(accel_phone, dtype=float)
        alpha = .10 if stationary or abs(np.linalg.norm(accel) - GRAVITY) < self.static_accel_threshold else .005
        self.gravity_phone = (1-alpha) * self.gravity_phone + alpha * accel
        linear = accel - self.gravity_phone; up = _unit(-self.gravity_phone, np.array([0.,0.,1.])); planar = linear - linear.dot(up) * up
        if np.linalg.norm(planar) > self.linear_accel_threshold:
            candidate = _unit(planar, np.array([1.,0.,0.])); self.forward_phone = candidate if self.forward_phone is None else _unit(.9*self.forward_phone+.1*candidate, candidate)
        if self.forward_phone is not None:
            forward = _unit(self.forward_phone - self.forward_phone.dot(up)*up, np.array([1.,0.,0.])); right = _unit(np.cross(forward, up), np.array([0.,1.,0.])); forward = _unit(np.cross(up, right), forward); self.rotation_vehicle_from_phone = np.vstack([forward,right,up])
        return self.rotation_vehicle_from_phone @ linear
    def transform_gyro(self, gyro_phone): return self.rotation_vehicle_from_phone @ np.asarray(gyro_phone, dtype=float)
    def stationary_bias(self, accel_samples, gyro_samples):
        mean_accel = self.rotation_vehicle_from_phone @ np.asarray(accel_samples).mean(axis=0)
        gravity_vehicle = np.array([0.,0.,-np.linalg.norm(self.gravity_phone)])
        return np.concatenate([mean_accel-gravity_vehicle, self.transform_gyro(np.asarray(gyro_samples).mean(axis=0))]).astype(np.float32)
