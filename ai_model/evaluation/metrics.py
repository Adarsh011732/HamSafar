import numpy as np
def trajectory_metrics(estimated_xyz,reference_xyz):
    est,ref=np.asarray(estimated_xyz),np.asarray(reference_xyz)
    if est.shape!=ref.shape or est.ndim!=2: raise ValueError('Trajectories must have matching (N,D) shape')
    error=np.linalg.norm(est-ref,axis=1); distance=float(np.linalg.norm(np.diff(ref,axis=0),axis=1).sum())
    return {'ate_rmse_m':float(np.sqrt(np.mean(error**2))),'cumulative_absolute_drift_m':float(error.sum()),'final_position_error_m':float(error[-1]),'drift_percent_of_distance':float(100*error[-1]/max(distance,1e-6))}
def relative_position_error(estimated_xyz,reference_xyz,interval):
    est,ref=np.asarray(estimated_xyz),np.asarray(reference_xyz)
    if interval<=0 or len(est)<=interval: raise ValueError('Interval must fit inside trajectory')
    return float(np.mean(np.linalg.norm((est[interval:]-est[:-interval])-(ref[interval:]-ref[:-interval]),axis=1)))
