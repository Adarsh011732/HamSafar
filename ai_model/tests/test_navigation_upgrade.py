import unittest
import numpy as np
from ai_model.navigation.frame_alignment import PhoneVehicleFrameAligner, GRAVITY
from ai_model.navigation.mechanization import StrapdownINS
from ai_model.fusion.ekf import DeadReckoningEKF
from ai_model.fusion.gnss_quality import GnssQuality, GnssTransitionManager
from ai_model.evaluation.metrics import trajectory_metrics

class NavigationUpgradeTest(unittest.TestCase):
    def test_tilted_phone_static_bias_removes_gravity_in_learned_frame(self):
        aligner=PhoneVehicleFrameAligner(); tilted=np.array([GRAVITY/np.sqrt(2),0,GRAVITY/np.sqrt(2)])
        for _ in range(100): aligner.update(tilted,stationary=True)
        bias=aligner.stationary_bias(np.repeat(tilted[None],8,axis=0),np.zeros((8,3)))
        self.assertLess(np.linalg.norm(bias[:3]),.1)
    def test_ins_integrates_forward_delta_v(self):
        ins=StrapdownINS()
        for _ in range(10): ins.propagate([.1,0,0],[0,0,0],.1)
        self.assertGreater(ins.state.position_ned_m[0],.4)
    def test_ekf_uses_zupt_and_neural_speed(self):
        ekf=DeadReckoningEKF(); ekf.predict([1,0,0],[0,0,0],1); ekf.update_neural_speed(2,.1)
        self.assertGreater(ekf.velocity_ned_ms[0],1.5); ekf.update_zupt(.99); self.assertLess(np.linalg.norm(ekf.velocity_ned_ms),.2)
    def test_gnss_transition_is_hysteretic(self):
        manager=GnssTransitionManager(hold_count=2); bad=GnssQuality(10,1,999); good=GnssQuality(1,8,4)
        self.assertEqual(manager.update(bad),'GNSS'); self.assertEqual(manager.update(bad),'DR'); self.assertEqual(manager.update(good),'DR'); self.assertEqual(manager.update(good),'GNSS')
    def test_trajectory_metrics(self):
        result=trajectory_metrics([[0,0],[1,0],[2,0]],[[0,0],[1,0],[2,0]])
        self.assertEqual(result['ate_rmse_m'],0.)
if __name__ == '__main__': unittest.main()
