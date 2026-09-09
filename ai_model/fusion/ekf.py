"""Error-state EKF coupling INS, neural speed uncertainty and ZUPT."""
import numpy as np
from ai_model.navigation.mechanization import StrapdownINS, InsState
class DeadReckoningEKF:
    def __init__(self, process_noise=.08): self.ins=StrapdownINS(); self.x=np.zeros(9); self.P=np.eye(9)*.5; self.process_noise=process_noise
    @property
    def position_ned_m(self): return self.x[:3].copy()
    @property
    def velocity_ned_ms(self): return self.x[3:6].copy()
    def predict(self, delta_v_vehicle, delta_theta_vehicle, dt):
        state=self.ins.propagate(np.asarray(delta_v_vehicle)-self.x[6:9]*dt,delta_theta_vehicle,dt); self.x[:3],self.x[3:6]=state.position_ned_m,state.velocity_ned_ms
        f=np.eye(9); f[:3,3:6]=np.eye(3)*dt; f[3:6,6:9]=-np.eye(3)*dt; q=np.diag([self.process_noise*dt**3]*3+[self.process_noise*dt]*3+[1e-5*dt]*3); self.P=f@self.P@f.T+q; return self.x.copy()
    def update_neural_speed(self, speed_ms, speed_std_ms):
        h=np.zeros((1,9)); heading=self.ins.state.yaw_rad; h[0,3:5]=[np.cos(heading),np.sin(heading)]; self._update(np.array([speed_ms]),h,np.array([[max(float(speed_std_ms)**2,1e-4)]]))
    def update_zupt(self, probability):
        if probability >= .5:
            h=np.zeros((3,9)); h[:,3:6]=np.eye(3); self._update(np.zeros(3),h,np.eye(3)*max(.0001,(1-probability)*.01))
    def update_gnss(self, position_ned_m, variance_m2):
        h=np.zeros((3,9)); h[:,:3]=np.eye(3); self._update(np.asarray(position_ned_m),h,np.eye(3)*max(float(variance_m2),.25))
    def _update(self,z,h,r):
        k=self.P@h.T@np.linalg.pinv(h@self.P@h.T+r); self.x += k@(z-h@self.x); self.P=(np.eye(9)-k@h)@self.P; self.ins.state=InsState(self.x[:3].copy(),self.x[3:6].copy(),self.ins.state.yaw_rad)
