"""Optional road and non-holonomic post-processing constraints."""
import numpy as np
def apply_non_holonomic_constraint(velocity_vehicle, variance=.02):
    """Suppress lateral/vertical vehicle velocity while preserving forward speed."""
    v=np.asarray(velocity_vehicle,dtype=float).copy(); v[1:]=0.; return v
def snap_to_polyline(position_xy, polyline_xy, max_snap_distance_m=20.):
    """Return nearest road-link projection only when it is plausibly reachable."""
    p=np.asarray(position_xy,dtype=float); road=np.asarray(polyline_xy,dtype=float)
    if len(road)<2: raise ValueError('A road polyline needs two or more points')
    candidates=[]
    for a,b in zip(road[:-1],road[1:]):
        d=b-a; fraction=np.clip(np.dot(p-a,d)/max(np.dot(d,d),1e-12),0.,1.); q=a+fraction*d; candidates.append(q)
    snapped=min(candidates,key=lambda q:np.linalg.norm(p-q))
    return snapped if np.linalg.norm(p-snapped)<=max_snap_distance_m else p
