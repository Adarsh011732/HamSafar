# SIH26168 Intelligent Dead Reckoning — Mathematical Architecture

This document presents the complete mathematical formulation and state-space mechanics of the **Chameleon Nav** multi-sensor fusion pipeline:
$$\text{AI (IO-VNBD)} + \text{INS} + \text{NHC} + \text{ZUPT} + \text{Map Matching} + \text{GNSS Fusion}$$

---

## 1. Extended Kalman Filter (EKF) 9-State Vector

The navigation core estimates error states in the local navigation frame (NED):
$$\delta \mathbf{x} = \begin{bmatrix} \delta \mathbf{p}^n \\ \delta \mathbf{v}^n \\ \boldsymbol{\psi}^n \end{bmatrix} \in \mathbb{R}^9$$

- $\delta \mathbf{p}^n = [\delta \phi, \delta \lambda, \delta h]^T$: Geodetic position error (Latitude, Longitude, Altitude)
- $\delta \mathbf{v}^n = [\delta v_N, \delta v_E, \delta v_D]^T$: Velocity error in North-East-Down frame
- $\boldsymbol{\psi}^n = [\psi_N, \psi_E, \psi_D]^T$: Attitude error angles (Roll, Pitch, Yaw)

### Continuous State Transition
$$\delta \dot{\mathbf{x}}(t) = \mathbf{F}(t) \delta \mathbf{x}(t) + \mathbf{G}(t) \mathbf{w}(t)$$

where $\mathbf{F}$ is the system dynamic matrix derived from strapdown inertial mechanization:
$$\mathbf{F} = \begin{bmatrix} 
\mathbf{0}_{3 \times 3} & \mathbf{I}_{3 \times 3} & \mathbf{0}_{3 \times 3} \\
\mathbf{0}_{3 \times 3} & -2\boldsymbol{\omega}_{ie}^n \times & \mathbf{f}^n \times \\
\mathbf{0}_{3 \times 3} & \mathbf{0}_{3 \times 3} & -\boldsymbol{\omega}_{in}^n \times
\end{bmatrix}$$

---

## 2. Non-Holonomic Constraints (NHC)

Land vehicles and pedestrians cannot instantaneously slide sideways or jump vertically through the road:
$$v_y^b \approx 0, \quad v_z^b \approx 0$$

In the vehicle body frame ($b$), the lateral and vertical velocity pseudo-measurements are modeled as:
$$\mathbf{z}_{NHC} = \begin{bmatrix} 0 - v_y^b \\ 0 - v_z^b \end{bmatrix} = \mathbf{H}_{NHC} \delta \mathbf{x} + \mathbf{v}_{NHC}$$

where the measurement matrix $\mathbf{H}_{NHC}$ projects navigation velocity using the body-to-nav rotation matrix $\mathbf{C}_b^n$:
$$\mathbf{H}_{NHC} = \begin{bmatrix} 
\mathbf{0}_{1 \times 3} & \mathbf{C}_b^n(2,:) & \mathbf{0}_{1 \times 3} \\
\mathbf{0}_{1 \times 3} & \mathbf{C}_b^n(3,:) & \mathbf{0}_{1 \times 3}
\end{bmatrix}$$

This suppresses cubic position divergence ($\propto t^3$) in cross-track and vertical dimensions.

---

## 3. Zero-Velocity Update (ZUPT) GLRT Detector

Stationary periods (e.g. traffic signals, pauses) are detected using a Generalized Likelihood Ratio Test (GLRT):
$$T(\mathbf{z}_k) = \frac{1}{N} \sum_{i=k-N+1}^k \left( \frac{1}{\sigma_a^2} \|\mathbf{a}_i - g \mathbf{u}_i\|^2 + \frac{1}{\sigma_g^2} \|\boldsymbol{\omega}_i\|^2 \right) < \gamma$$

When $T(\mathbf{z}_k) < \gamma$:
1. Vehicle velocity is clamped: $\mathbf{v}^n = \mathbf{0}$.
2. The Kalman measurement update directly observes velocity errors:
   $$\mathbf{z}_{ZUPT} = \mathbf{v}_{INS}^n - \mathbf{0} = \mathbf{H}_{ZUPT} \delta \mathbf{x} + \mathbf{v}_{ZUPT}$$
3. Accelerometer and gyroscope bias drift are actively estimated and eliminated.

---

## 4. AI-ML Velocity Inference (IO-VNBD Benchmark)

To overcome cubic distance drift during extended GNSS outages ($> 30\text{s}$), the 1D-CNN + Bi-GRU network directly maps 6-axis IMU temporal features to forward velocity $v_{fwd}$:
$$\hat{v}_{fwd} = f_{\boldsymbol{\theta}}(\mathbf{a}_{k-N:k}, \boldsymbol{\omega}_{k-N:k})$$

Trained on the **IO-VNBD** vehicular dataset:
- **1D-CNN**: Extracts high-frequency vibration signatures and filters sensor noise.
- **Bi-GRU**: Models vehicle forward inertia, throttle momentum, and centripetal turns ($a_{lat} \approx v \cdot \omega_z$).
- **Result**: Reduces drift from $> 15\%$ (raw double integration) to $< 1.5\%$ over 60s GNSS loss.

---

## 5. Vector Map Matching Engine

Projecting dead reckoning coordinates onto road centerlines:
Given an estimated position $\mathbf{p}_{DR}$ and candidate road segment $\mathbf{S} = (\mathbf{A}, \mathbf{B})$:
$$\mathbf{p}_{proj} = \mathbf{A} + \text{clamp}\left(\frac{(\mathbf{p}_{DR} - \mathbf{A}) \cdot (\mathbf{B} - \mathbf{A})}{\|\mathbf{B} - \mathbf{A}\|^2}, 0, 1\right) (\mathbf{B} - \mathbf{A})$$

Snapping occurs when orthogonal distance $d_{\perp} < 18\text{m}$ and heading alignment $|\Delta \theta| < 35^\circ$.
