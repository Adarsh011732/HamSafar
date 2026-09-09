import math
import numpy as np
def gaussian_nll(mean,std,target):
    std=np.maximum(np.asarray(std),1e-6); error=np.asarray(target)-np.asarray(mean); return float(np.mean(.5*(error/std)**2+np.log(std)+.5*np.log(2*np.pi)))
def expected_calibration_error(mean,std,target,bins=10):
    z=np.abs((np.asarray(target)-np.asarray(mean))/np.maximum(np.asarray(std),1e-6)); thresholds=np.linspace(.1,2.,bins); return float(sum(abs(np.mean(z<=t)-math.erf(t/np.sqrt(2)))/bins for t in thresholds))
