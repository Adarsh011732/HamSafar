"""Route-held-out split utilities; each route belongs to exactly one partition."""
from __future__ import annotations
import random
def route_held_out_split(route_ids, train_fraction=.7, val_fraction=.15, seed=42):
    unique=list(dict.fromkeys(route_ids))
    if len(unique)<3: raise ValueError('At least three independent routes are required for train/validation/test.')
    if not 0 < train_fraction < 1 or not 0 < val_fraction < 1 or train_fraction+val_fraction >= 1: raise ValueError('Invalid split fractions.')
    rng=random.Random(seed); rng.shuffle(unique); train_end=max(1,int(len(unique)*train_fraction)); val_end=max(train_end+1,int(len(unique)*(train_fraction+val_fraction)))
    return {'train':unique[:train_end],'validation':unique[train_end:val_end],'test':unique[val_end:]}
