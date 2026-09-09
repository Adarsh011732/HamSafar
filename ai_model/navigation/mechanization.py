"""Local-NED strapdown INS propagation."""
from dataclasses import dataclass, field
import numpy as np
@dataclass
class InsState:
    position_ned_m: np.ndarray = field(default_factory=lambda: np.zeros(3)); velocity_ned_ms: np.ndarray = field(default_factory=lambda: np.zeros(3)); yaw_rad: float = 0.
class StrapdownINS:
    def __init__(self, state=None): self.state = state or InsState()
    def propagate(self, delta_v_vehicle, delta_theta_vehicle, dt):
        if dt <= 0: raise ValueError('dt must be positive')
        self.state.yaw_rad += np.asarray(delta_theta_vehicle, dtype=float)[2]; c,s=np.cos(self.state.yaw_rad),np.sin(self.state.yaw_rad)
        rotation=np.array([[c,-s,0.],[s,c,0.],[0.,0.,1.]]); old=self.state.velocity_ned_ms.copy(); self.state.velocity_ned_ms += rotation @ np.asarray(delta_v_vehicle,dtype=float); self.state.position_ned_m += .5*(old+self.state.velocity_ned_ms)*dt
        return self.state
