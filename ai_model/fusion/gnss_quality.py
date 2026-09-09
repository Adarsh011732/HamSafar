from dataclasses import dataclass
@dataclass(frozen=True)
class GnssQuality: hdop: float; satellites: int; position_variance_m2: float
class GnssTransitionManager:
    """Hysteretic quality gate that prevents GNSS/DR mode flapping."""
    def __init__(self,min_satellites=4,max_hdop=3.5,max_variance_m2=100.,hold_count=3): self.min_satellites,self.max_hdop,self.max_variance_m2,self.hold_count=min_satellites,max_hdop,max_variance_m2,hold_count; self.mode='GNSS'; self._good=self._bad=0
    def update(self,q):
        good=q.satellites>=self.min_satellites and q.hdop<=self.max_hdop and q.position_variance_m2<=self.max_variance_m2; self._good,self._bad=(self._good+1,0) if good else (0,self._bad+1)
        if self.mode=='GNSS' and self._bad>=self.hold_count: self.mode='DR'
        elif self.mode=='DR' and self._good>=self.hold_count: self.mode='GNSS'
        return self.mode
