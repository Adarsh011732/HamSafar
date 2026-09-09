from __future__ import annotations
import numpy as np
class OnnxDeadReckoningEstimator:
    """Drop-in rolling-window ONNX Runtime predictor; PyTorch path remains unchanged."""
    def __init__(self, model_path, mean, std, window_size=200, providers=None):
        try: import onnxruntime as ort
        except ImportError as exc: raise RuntimeError('Install onnxruntime or onnxruntime-mobile to use ONNX mode.') from exc
        available=ort.get_available_providers(); requested=providers or ['NNAPIExecutionProvider','CPUExecutionProvider']; selected=[p for p in requested if p in available] or ['CPUExecutionProvider']
        self.session=ort.InferenceSession(model_path,providers=selected); self.mean=np.asarray(mean,dtype=np.float32); self.std=np.asarray(std,dtype=np.float32); self.window_size=window_size; self.buffer=[]
    def push_sample(self, sample, gnss_available=True):
        sample=np.asarray(sample,dtype=np.float32)
        if sample.shape != (9,): raise ValueError('sample must contain nine IMU values')
        self.buffer.append(np.r_[sample,1. if gnss_available else 0.])
        self.buffer=self.buffer[-self.window_size:]
        if len(self.buffer)<self.window_size: return {'status':'BUFFER_WARMING'}
        window=np.asarray(self.buffer,dtype=np.float32); features=np.concatenate([(window[:,:9]-self.mean)/self.std,window[:,9:]],axis=1).T[None]
        velocity,bias,zupt,log_var,*_=self.session.run(None,{'imu_window':features})
        return {'speed_ms':float(velocity[0,0]),'dx':float(velocity[0,1]),'dy':float(velocity[0,2]),'velocity_std_ms':np.sqrt(np.exp(log_var[0])).tolist(),'accel_bias':bias[0,:3].tolist(),'gyro_bias':bias[0,3:].tolist(),'zupt_prob':float(zupt[0,0]),'status':'READY','providers':self.session.get_providers()}
