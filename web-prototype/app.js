// Chameleon Nav — SIH26168 Interactive Prototype Engine

// 1. Demo Route Waypoints (New Delhi India Gate to Rashtrapati Bhavan)
const WAYPOINTS = [
  { id: "start", name: "Start: India Gate", lat: 28.6129, lon: 77.2295 },
  { id: "wp_a", name: "War Memorial", lat: 28.6145, lon: 77.2240 },
  { id: "wp_b", name: "Kartavya Path", lat: 28.6138, lon: 77.2150 },
  { id: "wp_c", name: "Vijay Chowk", lat: 28.6143, lon: 77.2085 },
  { id: "wp_d", name: "North Block", lat: 28.6170, lon: 77.2045 },
  { id: "dest", name: "Rashtrapati Bhavan", lat: 28.6144, lon: 77.1995 }
];

// Calculate route lengths
function computeDistance(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

let totalRouteDist = 0;
for (let i = 0; i < WAYPOINTS.length - 1; i++) {
  totalRouteDist += computeDistance(WAYPOINTS[i].lat, WAYPOINTS[i].lon, WAYPOINTS[i+1].lat, WAYPOINTS[i+1].lon);
}

function getRouteSample(dist) {
  let rem = dist % totalRouteDist;
  if (rem < 0) rem += totalRouteDist;

  for (let i = 0; i < WAYPOINTS.length - 1; i++) {
    const p1 = WAYPOINTS[i];
    const p2 = WAYPOINTS[i+1];
    const segDist = computeDistance(p1.lat, p1.lon, p2.lat, p2.lon);

    if (rem <= segDist || i === WAYPOINTS.length - 2) {
      const ratio = Math.min(Math.max(rem / segDist, 0), 1);
      const lat = p1.lat + ratio * (p2.lat - p1.lat);
      const lon = p1.lon + ratio * (p2.lon - p1.lon);
      
      const y = Math.sin((p2.lon - p1.lon) * Math.PI / 180) * Math.cos(p2.lat * Math.PI / 180);
      const x = Math.cos(p1.lat * Math.PI / 180) * Math.sin(p2.lat * Math.PI / 180) -
                Math.sin(p1.lat * Math.PI / 180) * Math.cos(p2.lat * Math.PI / 180) * Math.cos((p2.lon - p1.lon) * Math.PI / 180);
      const heading = ((Math.atan2(y, x) * 180 / Math.PI) + 360) % 360;

      return { lat, lon, heading, segIndex: i };
    }
    rem -= segDist;
  }
  return { lat: WAYPOINTS[WAYPOINTS.length - 1].lat, lon: WAYPOINTS[WAYPOINTS.length - 1].lon, heading: 270, segIndex: WAYPOINTS.length - 1 };
}

// State Variables
let isNavigating = false;
let isOutageActive = false;
let isRecovering = false;
let distanceTraveled = 0;
let simSpeedMultiplier = 1.0;
let showRefPath = true;
let outageTimeSec = 0;
let recoveryProgress = 0;
let driftAtRecovery = 0;
let currentDriftMeters = 0;
let tickCount = 0;
let realHardwareGpsActive = false;
let realPhoneMotionActive = false;

// Physical Device Sensor Listeners (Real World)
if (typeof navigator !== "undefined" && "geolocation" in navigator) {
  navigator.geolocation.watchPosition(
    (pos) => {
      realHardwareGpsActive = true;
      if (!isOutageActive) {
        currentRefPos = { lat: pos.coords.latitude, lon: pos.coords.longitude };
        if (pos.coords.speed !== null && pos.coords.speed > 0) {
          metricSpeed.textContent = (pos.coords.speed * 3.6).toFixed(1);
        }
        if (pos.coords.heading !== null && !isNaN(pos.coords.heading)) {
          metricHeading.textContent = `${Math.round(pos.coords.heading)}°`;
        }
        if (pos.coords.accuracy) {
          metricAccuracy.textContent = pos.coords.accuracy.toFixed(1);
        }
      }
    },
    (err) => {
      console.warn("Real Geolocation unavailable:", err.message);
    },
    { enableHighAccuracy: true, maximumAge: 1000, timeout: 5000 }
  );
}

if (typeof window !== "undefined" && window.DeviceMotionEvent) {
  window.addEventListener("devicemotion", (e) => {
    if (e.acceleration) {
      realPhoneMotionActive = true;
      const ax = e.acceleration.x || 0;
      const ay = e.acceleration.y || 0;
      const az = e.acceleration.z || 0;
      document.getElementById("val-ax").textContent = `${ax >= 0 ? "+" : ""}${ax.toFixed(2)}`;
      document.getElementById("val-ay").textContent = `${ay >= 0 ? "+" : ""}${ay.toFixed(2)}`;
      document.getElementById("val-az").textContent = `${az >= 0 ? "+" : ""}${az.toFixed(2)}`;
    }
  });
}

let currentRefPos = { lat: WAYPOINTS[0].lat, lon: WAYPOINTS[0].lon };
let currentEstPos = { lat: WAYPOINTS[0].lat, lon: WAYPOINTS[0].lon };
let refPathHistory = [currentRefPos];
let estPathHistory = [currentEstPos];

let autoDemoRunning = false;
let autoDemoTimer = null;

// DOM Elements
const gnssStatusDot = document.getElementById("gnss-status-dot");
const gnssStatusText = document.getElementById("gnss-status-text");
const navModeBadge = document.getElementById("nav-mode-badge");
const navModeText = document.getElementById("nav-mode-text");
const statusDescText = document.getElementById("status-desc-text");

const eventBanner = document.getElementById("event-banner");
const bannerIcon = document.getElementById("banner-icon");
const bannerText = document.getElementById("banner-text");

const mapDriftOverlay = document.getElementById("map-drift-overlay");
const driftOverlayText = document.getElementById("drift-overlay-text");

const metricSpeed = document.getElementById("metric-speed");
const metricHeading = document.getElementById("metric-heading");
const metricCardinal = document.getElementById("metric-cardinal");
const metricAccuracy = document.getElementById("metric-accuracy");
const metricPosition = document.getElementById("metric-position");
const metricConfPct = document.getElementById("metric-conf-pct");
const confProgressBar = document.getElementById("conf-progress-bar");

const chipAiSpeed = document.getElementById("chip-ai-speed");
const chipImuStatus = document.getElementById("chip-imu-status");
const chipFilterDot = document.getElementById("chip-filter-dot");
const chipFilterStatus = document.getElementById("chip-filter-status");

const badgeAi = document.getElementById("badge-ai");
const badgeIns = document.getElementById("badge-ins");
const badgeNhc = document.getElementById("badge-nhc");
const badgeZupt = document.getElementById("badge-zupt");
const badgeMap = document.getElementById("badge-map");
const badgeGnss = document.getElementById("badge-gnss");

const btnStart = document.getElementById("btn-start");
const startLabel = document.getElementById("start-label");
const btnOutage = document.getElementById("btn-outage");
const btnRestore = document.getElementById("btn-restore");
const btnReset = document.getElementById("btn-reset");
const btnAutoDemo = document.getElementById("btn-auto-demo");
const autoDemoLabel = document.getElementById("auto-demo-label");

const btnModeHardware = document.getElementById("btn-mode-hardware");
const btnModeDemo = document.getElementById("btn-mode-demo");
let isDemoMode = true;

if (btnModeHardware && btnModeDemo) {
  btnModeHardware.addEventListener("click", () => {
    isDemoMode = false;
    btnModeHardware.className = "mode-pill-btn active hardware";
    btnModeDemo.className = "mode-pill-btn";
    document.querySelector(".demo-badge span:last-child").textContent = "LIVE HARDWARE";
    document.querySelector(".header-tagline").textContent = "“When GPS Disappears, We Adapt.” • SIH26168 Real Hardware Mode";
    resetNavigation();
  });

  btnModeDemo.addEventListener("click", () => {
    isDemoMode = true;
    btnModeDemo.className = "mode-pill-btn active demo";
    btnModeHardware.className = "mode-pill-btn";
    document.querySelector(".demo-badge span:last-child").textContent = "DEMO MODE";
    document.querySelector(".header-tagline").textContent = "“When GPS Disappears, We Adapt.” • SIH26168 Demo Simulation";
    resetNavigation();
  });
}

// Canvas
const canvas = document.getElementById("nav-canvas");
const ctx = canvas.getContext("2d");

function resizeCanvas() {
  const rect = canvas.parentElement.getBoundingClientRect();
  canvas.width = rect.width * window.devicePixelRatio;
  canvas.height = rect.height * window.devicePixelRatio;
}
window.addEventListener("resize", resizeCanvas);
resizeCanvas();

// 3. Tab Switching
document.querySelectorAll(".nav-tab").forEach(tab => {
  tab.addEventListener("click", () => {
    document.querySelectorAll(".nav-tab").forEach(t => t.classList.remove("active"));
    document.querySelectorAll(".screen-view").forEach(s => s.classList.remove("active"));
    tab.classList.add("active");
    const targetId = tab.getAttribute("data-target");
    document.getElementById(targetId).classList.add("active");
    if (targetId === "screen-nav") resizeCanvas();
  });
});

// 4. Speed Multipliers
document.querySelectorAll(".btn-speed").forEach(btn => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".btn-speed").forEach(b => b.classList.remove("active"));
    btn.classList.add("active");
    simSpeedMultiplier = parseFloat(btn.getAttribute("data-speed"));
  });
});

document.getElementById("toggle-ref-path").addEventListener("change", (e) => {
  showRefPath = e.target.checked;
});

document.getElementById("btn-settings-reset").addEventListener("click", resetNavigation);

// 5. Button Actions
btnStart.addEventListener("click", () => {
  if (isNavigating) {
    pauseNavigation();
  } else {
    startNavigation();
  }
});

btnOutage.addEventListener("click", simulateOutage);
btnRestore.addEventListener("click", restoreGnss);
btnReset.addEventListener("click", resetNavigation);
btnAutoDemo.addEventListener("click", toggleAutoDemo);

function startNavigation() {
  isNavigating = true;
  startLabel.textContent = "PAUSE NAVIGATION";
  btnStart.style.backgroundColor = "var(--card-border)";
  btnStart.style.color = "var(--text-primary)";
  btnOutage.disabled = false;
  btnRestore.disabled = !isOutageActive;
  if (badgeZupt) {
    badgeZupt.className = "fusion-badge";
    badgeZupt.innerHTML = '<span class="badge-dot dot-muted"></span><span class="badge-text">ZUPT (STANDBY)</span>';
  }
  updateStatusDesc("Navigating with continuous satellite fix and INS strapdown alignment.");
  updateSihTimeline(2);
}

function pauseNavigation() {
  isNavigating = false;
  startLabel.textContent = "START NAVIGATION";
  btnStart.style.backgroundColor = "var(--accent-emerald)";
  btnStart.style.color = "var(--bg-dark)";
  metricSpeed.textContent = "0.0";
  if (chipAiSpeed) chipAiSpeed.textContent = "0.0 km/h";
  if (badgeZupt) {
    badgeZupt.className = "fusion-badge badge-active";
    badgeZupt.innerHTML = '<span class="badge-dot dot-emerald"></span><span class="badge-text">ZUPT ACTIVE</span>';
  }
  updateStatusDesc("Navigation paused. Zero-velocity constraint (ZUPT) engaged.");
}

function simulateOutage() {
  if (!isNavigating) return;
  isOutageActive = true;
  isRecovering = false;
  outageTimeSec = 0;

  btnOutage.disabled = true;
  btnRestore.disabled = false;

  // Update Status UI
  gnssStatusDot.className = "status-indicator lost";
  gnssStatusText.className = "status-val gnss-lost";
  gnssStatusText.textContent = "LOST";

  navModeBadge.className = "nav-mode-badge mode-dead-reckoning";
  navModeText.textContent = "DEAD RECKONING";

  if (badgeGnss) {
    badgeGnss.className = "fusion-badge badge-outage";
    badgeGnss.innerHTML = '<span class="badge-dot dot-muted"></span><span class="badge-text">GNSS LOST</span>';
  }

  if (chipFilterDot) chipFilterDot.className = "chip-dot dot-purple";
  if (chipFilterStatus) {
    chipFilterStatus.className = "chip-val chip-purple";
    chipFilterStatus.textContent = "DR ACTIVE";
  }

  showBanner("⚠ GNSS SIGNAL LOST: Switching to AI-Assisted Dead Reckoning...", "warning");
  updateStatusDesc("GNSS Lost. Autonomous AI Dead Reckoning maintaining track.");
  mapDriftOverlay.classList.remove("hidden");
  updateSihTimeline(4);
}

function restoreGnss() {
  if (!isNavigating || (!isOutageActive && !isRecovering)) return;
  isOutageActive = false;
  isRecovering = true;
  recoveryProgress = 0;
  driftAtRecovery = currentDriftMeters;

  btnOutage.disabled = true;
  btnRestore.disabled = true;

  gnssStatusDot.className = "status-indicator restored";
  gnssStatusText.className = "status-val gnss-restored";
  gnssStatusText.textContent = "RESTORED";

  navModeBadge.className = "nav-mode-badge mode-recovery";
  navModeText.textContent = "RECOVERY";

  if (badgeGnss) {
    badgeGnss.className = "fusion-badge badge-active";
    badgeGnss.innerHTML = '<span class="badge-dot dot-blue"></span><span class="badge-text">GNSS FIX</span>';
  }

  showBanner("↻ GNSS RESTORED: Synchronizing position & correcting drift...", "info");
  updateStatusDesc("Synchronizing position... Drift correction active.");
  updateSihTimeline(6);
}

function resetNavigation() {
  cancelAutoDemo();
  isNavigating = false;
  isOutageActive = false;
  isRecovering = false;
  distanceTraveled = 0;
  outageTimeSec = 0;
  recoveryProgress = 0;
  currentDriftMeters = 0;

  startLabel.textContent = "START NAVIGATION";
  btnStart.style.backgroundColor = "var(--accent-emerald)";
  btnStart.style.color = "var(--bg-dark)";
  btnOutage.disabled = true;
  btnRestore.disabled = true;

  gnssStatusDot.className = "status-indicator available";
  gnssStatusText.className = "status-val gnss-available";
  gnssStatusText.textContent = "AVAILABLE";

  navModeBadge.className = "nav-mode-badge mode-gnss-ins";
  navModeText.textContent = "GNSS + INS";

  if (badgeGnss) {
    badgeGnss.className = "fusion-badge badge-active";
    badgeGnss.innerHTML = '<span class="badge-dot dot-blue"></span><span class="badge-text">GNSS FIX</span>';
  }
  if (badgeZupt) {
    badgeZupt.className = "fusion-badge";
    badgeZupt.innerHTML = '<span class="badge-dot dot-muted"></span><span class="badge-text">ZUPT (STANDSTILL)</span>';
  }

  if (chipFilterDot) chipFilterDot.className = "chip-dot dot-cyan";
  if (chipFilterStatus) {
    chipFilterStatus.className = "chip-val chip-cyan";
    chipFilterStatus.textContent = "EKF / INS";
  }

  metricSpeed.textContent = "0.0";
  metricHeading.textContent = "128°";
  metricAccuracy.textContent = "4.2";
  metricConfPct.textContent = "98%";
  confProgressBar.style.width = "98%";
  confProgressBar.style.backgroundColor = "var(--accent-emerald)";

  currentRefPos = { lat: WAYPOINTS[0].lat, lon: WAYPOINTS[0].lon };
  currentEstPos = { lat: WAYPOINTS[0].lat, lon: WAYPOINTS[0].lon };
  refPathHistory = [currentRefPos];
  estPathHistory = [currentEstPos];

  hideBanner();
  mapDriftOverlay.classList.add("hidden");
  updateStatusDesc("Ready. Press START NAVIGATION.");
  updateSihTimeline(1);
}

function showBanner(text, type) {
  eventBanner.className = `event-banner ${type}`;
  bannerText.textContent = text;
  bannerIcon.textContent = type === "warning" ? "⚠" : (type === "success" ? "✓" : "↻");
}

function hideBanner() {
  eventBanner.classList.add("hidden");
}

function updateStatusDesc(text) {
  statusDescText.textContent = text;
}

function updateSihTimeline(stepIndex) {
  for (let i = 1; i <= 7; i++) {
    const el = document.getElementById(`step-${i}`);
    if (!el) continue;
    const tag = el.querySelector(".step-tag");
    if (i === stepIndex) {
      el.className = "step-item active";
      if (tag) tag.textContent = "ACTIVE";
    } else if (i < stepIndex) {
      el.className = "step-item";
      if (tag) tag.textContent = "DONE";
    } else {
      el.className = "step-item";
      if (tag) tag.textContent = "WAITING";
    }
  }
}

// 6. Automated SIH Demo Script (30 Seconds)
function toggleAutoDemo() {
  if (autoDemoRunning) {
    cancelAutoDemo();
  } else {
    runAutoDemo();
  }
}

function runAutoDemo() {
  autoDemoRunning = true;
  autoDemoLabel.textContent = "STOP AUTOMATED DEMO";
  btnAutoDemo.style.backgroundColor = "var(--accent-rose)";

  // Switch to NAV tab to watch live demonstration!
  document.querySelector('.nav-tab[data-target="screen-nav"]').click();

  resetNavigation();
  setTimeout(() => {
    if (!autoDemoRunning) return;
    startNavigation();

    autoDemoTimer = setTimeout(() => {
      if (!autoDemoRunning) return;
      simulateOutage();

      autoDemoTimer = setTimeout(() => {
        if (!autoDemoRunning) return;
        restoreGnss();

        autoDemoTimer = setTimeout(() => {
          if (!autoDemoRunning) return;
          autoDemoRunning = false;
          autoDemoLabel.textContent = "RUN AUTOMATED SIH JUDGE DEMO (30s)";
          btnAutoDemo.style.backgroundColor = "var(--accent-emerald)";
        }, 5000);
      }, 9000);
    }, 6000);
  }, 500);
}

function cancelAutoDemo() {
  autoDemoRunning = false;
  clearTimeout(autoDemoTimer);
  autoDemoLabel.textContent = "RUN AUTOMATED SIH JUDGE DEMO (30s)";
  btnAutoDemo.style.backgroundColor = "var(--accent-emerald)";
}

// 7. Physics & Telemetry Loop (20Hz = 50ms)
setInterval(() => {
  if (!isNavigating) return;
  tickCount++;
  const dt = 0.05 * simSpeedMultiplier;

  // Speed dynamics with realistic variation (42.5 km/h base)
  const speedVar = Math.sin(tickCount * 0.06) * 1.5 + Math.cos(tickCount * 0.14) * 0.8;
  const speedKmh = Math.max(34, Math.min(52, 42.5 + speedVar));
  const speedMs = (speedKmh * 1000) / 3600;

  distanceTraveled += speedMs * dt;
  const sample = getRouteSample(distanceTraveled);
  currentRefPos = { lat: sample.lat, lon: sample.lon };

  // Dead Reckoning & Drift Dynamics
  let conf = 98;
  let accuracy = 4.2;
  let cov = 0.0420;

  if (isOutageActive) {
    outageTimeSec += dt;
    const maxDrift = 14.0;
    const driftFactor = 1.0 - Math.exp(-outageTimeSec / 12.0);
    currentDriftMeters = driftFactor * maxDrift;

    conf = Math.max(78, Math.round(98 - outageTimeSec * 0.7));
    accuracy = Math.min(19.5, 4.2 + outageTimeSec * 0.55);
    cov = Math.min(0.35, 0.0420 + outageTimeSec * 0.015);

    const driftHeading = (sample.heading + 90) * Math.PI / 180;
    const dLat = (currentDriftMeters * Math.cos(driftHeading)) / 111139;
    const dLon = (currentDriftMeters * Math.sin(driftHeading)) / (111139 * Math.cos(sample.lat * Math.PI / 180));
    currentEstPos = { lat: sample.lat + dLat, lon: sample.lon + dLon };

    driftOverlayText.textContent = `AI DR DRIFT: ${currentDriftMeters.toFixed(1)} m`;

  } else if (isRecovering) {
    const recoveryDuration = 2.0;
    recoveryProgress += dt / recoveryDuration;

    if (recoveryProgress >= 1.0) {
      isRecovering = false;
      currentDriftMeters = 0;
      currentEstPos = currentRefPos;
      conf = 98;
      accuracy = 4.2;
      cov = 0.0420;

      gnssStatusDot.className = "status-indicator available";
      gnssStatusText.className = "status-val gnss-available";
      gnssStatusText.textContent = "AVAILABLE";

      navModeBadge.className = "nav-mode-badge mode-gnss-ins";
      navModeText.textContent = "GNSS + INS";

      if (chipFilterDot) chipFilterDot.className = "chip-dot dot-cyan";
      if (chipFilterStatus) {
        chipFilterStatus.className = "chip-val chip-cyan";
        chipFilterStatus.textContent = "EKF / INS";
      }

      btnOutage.disabled = false;
      btnRestore.disabled = true;

      showBanner("✓ NAVIGATION SYNCHRONIZED", "success");
      updateStatusDesc("All systems synchronized. GNSS + INS active.");
      mapDriftOverlay.classList.add("hidden");
      updateSihTimeline(7);
    } else {
      const t = Math.min(1, Math.max(0, recoveryProgress));
      const smooth = 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t);
      currentDriftMeters = driftAtRecovery * (1.0 - smooth);

      const driftHeading = (sample.heading + 90) * Math.PI / 180;
      const dLat = (currentDriftMeters * Math.cos(driftHeading)) / 111139;
      const dLon = (currentDriftMeters * Math.sin(driftHeading)) / (111139 * Math.cos(sample.lat * Math.PI / 180));
      currentEstPos = { lat: sample.lat + dLat, lon: sample.lon + dLon };

      conf = Math.min(98, Math.round(80 + 18 * smooth));
      accuracy = 4.2 + (1.0 - smooth) * 8.0;
      cov = 0.0420 + (1.0 - smooth) * 0.15;
      driftOverlayText.textContent = `SYNCING DRIFT: ${currentDriftMeters.toFixed(1)} m`;
    }
  } else {
    currentDriftMeters = 0;
    currentEstPos = currentRefPos;
  }

  // Trajectory history sampling
  if (tickCount % 5 === 0) {
    refPathHistory.push(currentRefPos);
    estPathHistory.push(currentEstPos);
    if (refPathHistory.length > 120) refPathHistory.shift();
    if (estPathHistory.length > 120) estPathHistory.shift();
  }

  // Live Metrics DOM updates
  metricSpeed.textContent = speedKmh.toFixed(1);
  metricHeading.textContent = `${Math.round(sample.heading)}°`;
  metricCardinal.textContent = getCardinal(sample.heading);
  metricAccuracy.textContent = accuracy.toFixed(1);

  const latStr = `${Math.abs(currentEstPos.lat).toFixed(4)}° N`;
  const lonStr = `${Math.abs(currentEstPos.lon).toFixed(4)}° E`;
  metricPosition.textContent = `${latStr}, ${lonStr}`;

  metricConfPct.textContent = `${conf}%`;
  confProgressBar.style.width = `${conf}%`;
  confProgressBar.style.backgroundColor = conf >= 90 ? "var(--accent-emerald)" : (conf >= 80 ? "var(--accent-amber)" : "var(--accent-rose)");

  const aiSpeed = speedKmh + Math.sin(tickCount * 0.12) * 0.35;
  if (chipAiSpeed) chipAiSpeed.textContent = `${aiSpeed.toFixed(1)} km/h`;

  // System Monitor Screen DOM updates
  const ax = (speedVar * 0.15 + Math.sin(tickCount * 0.3) * 0.03);
  const ay = (Math.cos(tickCount * 0.4) * 0.06);
  const az = (9.81 + Math.sin(tickCount * 0.8) * 0.12);

  const gx = (Math.sin(tickCount * 0.25) * 0.008);
  const gy = (Math.cos(tickCount * 0.2) * 0.012);
  const gz = (Math.sin(tickCount * 0.6) * 0.005);

  document.getElementById("mon-ai-speed").textContent = `${aiSpeed.toFixed(1)} km/h`;
  document.getElementById("mon-error").textContent = `±${Math.abs(speedKmh - aiSpeed).toFixed(2)} km/h`;
  document.getElementById("mon-filter-mode").textContent = navModeText.textContent;
  document.getElementById("mon-cov").textContent = cov.toFixed(4);
  document.getElementById("mon-drift").textContent = `${currentDriftMeters.toFixed(1)} m`;
  document.getElementById("mon-conf").textContent = `${conf}%`;

  document.getElementById("val-ax").textContent = `${ax >= 0 ? "+" : ""}${ax.toFixed(2)}`;
  document.getElementById("val-ay").textContent = `${ay >= 0 ? "+" : ""}${ay.toFixed(2)}`;
  document.getElementById("val-az").textContent = `${az >= 0 ? "+" : ""}${az.toFixed(2)}`;
  document.getElementById("bar-ax").style.width = `${Math.min(100, Math.abs(ax) / 15 * 100)}%`;
  document.getElementById("bar-ay").style.width = `${Math.min(100, Math.abs(ay) / 15 * 100)}%`;
  document.getElementById("bar-az").style.width = `${Math.min(100, Math.abs(az) / 15 * 100)}%`;

  document.getElementById("val-gx").textContent = `${gx >= 0 ? "+" : ""}${gx.toFixed(2)}`;
  document.getElementById("val-gy").textContent = `${gy >= 0 ? "+" : ""}${gy.toFixed(2)}`;
  document.getElementById("val-gz").textContent = `${gz >= 0 ? "+" : ""}${gz.toFixed(2)}`;
  document.getElementById("bar-gx").style.width = `${Math.min(100, Math.abs(gx) / 2 * 100)}%`;
  document.getElementById("bar-gy").style.width = `${Math.min(100, Math.abs(gy) / 2 * 100)}%`;
  document.getElementById("bar-gz").style.width = `${Math.min(100, Math.abs(gz) / 2 * 100)}%`;

}, 50);

function getCardinal(deg) {
  const norm = (deg % 360 + 360) % 360;
  if (norm >= 337.5 || norm < 22.5) return "N";
  if (norm >= 22.5 && norm < 67.5) return "NE";
  if (norm >= 67.5 && norm < 112.5) return "E";
  if (norm >= 112.5 && norm < 157.5) return "SE";
  if (norm >= 157.5 && norm < 202.5) return "S";
  if (norm >= 202.5 && norm < 247.5) return "SW";
  if (norm >= 247.5 && norm < 292.5) return "W";
  return "NW";
}

// 8. Vector Map Canvas Render Loop (60 FPS)
let pulseRadius = 12;
let pulseAlpha = 0.8;

function renderCanvas() {
  requestAnimationFrame(renderCanvas);
  if (!canvas.width || !canvas.height) return;

  const w = canvas.width;
  const h = canvas.height;
  ctx.clearRect(0, 0, w, h);

  const minLat = Math.min(...WAYPOINTS.map(w => w.lat)) - 0.0015;
  const maxLat = Math.max(...WAYPOINTS.map(w => w.lat)) + 0.0015;
  const minLon = Math.min(...WAYPOINTS.map(w => w.lon)) - 0.0015;
  const maxLon = Math.max(...WAYPOINTS.map(w => w.lon)) + 0.0015;

  function toCanvas(geo) {
    const pad = 40 * window.devicePixelRatio;
    const nx = (geo.lon - minLon) / (maxLon - minLon);
    const ny = (maxLat - geo.lat) / (maxLat - minLat);
    return {
      x: pad + nx * (w - pad * 2),
      y: pad + ny * (h - pad * 2)
    };
  }

  // Draw Grid
  ctx.strokeStyle = "rgba(30, 45, 72, 0.45)";
  ctx.lineWidth = 1 * window.devicePixelRatio;
  const spacing = 40 * window.devicePixelRatio;
  for (let x = 0; x < w; x += spacing) {
    ctx.beginPath();
    ctx.moveTo(x, 0);
    ctx.lineTo(x, h);
    ctx.stroke();
  }
  for (let y = 0; y < h; y += spacing) {
    ctx.beginPath();
    ctx.moveTo(0, y);
    ctx.lineTo(w, y);
    ctx.stroke();
  }

  // Draw Road Corridor
  const routeOffsets = WAYPOINTS.map(wp => toCanvas(wp));
  if (routeOffsets.length > 1) {
    // Outer casing
    ctx.beginPath();
    ctx.moveTo(routeOffsets[0].x, routeOffsets[0].y);
    for (let i = 1; i < routeOffsets.length; i++) ctx.lineTo(routeOffsets[i].x, routeOffsets[i].y);
    ctx.strokeStyle = "#151F33";
    ctx.lineWidth = 18 * window.devicePixelRatio;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.stroke();

    ctx.strokeStyle = "#1E2D48";
    ctx.lineWidth = 20 * window.devicePixelRatio;
    ctx.stroke();

    // Inner route centerline
    ctx.beginPath();
    ctx.moveTo(routeOffsets[0].x, routeOffsets[0].y);
    for (let i = 1; i < routeOffsets.length; i++) ctx.lineTo(routeOffsets[i].x, routeOffsets[i].y);
    ctx.strokeStyle = "#1B2A44";
    ctx.lineWidth = 7 * window.devicePixelRatio;
    ctx.stroke();
  }

  // Draw Reference Trajectory Trail
  if (showRefPath && refPathHistory.length > 1) {
    ctx.beginPath();
    const p0 = toCanvas(refPathHistory[0]);
    ctx.moveTo(p0.x, p0.y);
    for (let i = 1; i < refPathHistory.length; i++) {
      const p = toCanvas(refPathHistory[i]);
      ctx.lineTo(p.x, p.y);
    }
    ctx.strokeStyle = "rgba(0, 229, 255, 0.7)";
    ctx.lineWidth = 4 * window.devicePixelRatio;
    ctx.stroke();
  }

  // Draw Estimated / Dead Reckoning Trajectory Trail
  if (estPathHistory.length > 1) {
    ctx.beginPath();
    const p0 = toCanvas(estPathHistory[0]);
    ctx.moveTo(p0.x, p0.y);
    for (let i = 1; i < estPathHistory.length; i++) {
      const p = toCanvas(estPathHistory[i]);
      ctx.lineTo(p.x, p.y);
    }
    ctx.strokeStyle = (isOutageActive || isRecovering) ? "rgba(255, 179, 0, 0.85)" : "rgba(0, 230, 118, 0.85)";
    ctx.lineWidth = 5 * window.devicePixelRatio;
    ctx.stroke();
  }

  // Draw Waypoint Nodes
  routeOffsets.forEach((pt, idx) => {
    ctx.beginPath();
    ctx.arc(pt.x, pt.y, (idx === 0 || idx === routeOffsets.length - 1 ? 6 : 4) * window.devicePixelRatio, 0, Math.PI * 2);
    ctx.fillStyle = idx === 0 ? "#00E676" : (idx === routeOffsets.length - 1 ? "#FF3D71" : "#00E5FF");
    ctx.fill();
    ctx.strokeStyle = "#000000";
    ctx.lineWidth = 2 * window.devicePixelRatio;
    ctx.stroke();
  });

  // Draw Drift Offset Line during outage
  const refCanvas = toCanvas(currentRefPos);
  const estCanvas = toCanvas(currentEstPos);

  if (isOutageActive || isRecovering) {
    ctx.beginPath();
    ctx.setLineDash([6 * window.devicePixelRatio, 4 * window.devicePixelRatio]);
    ctx.moveTo(refCanvas.x, refCanvas.y);
    ctx.lineTo(estCanvas.x, estCanvas.y);
    ctx.strokeStyle = "rgba(255, 61, 113, 0.8)";
    ctx.lineWidth = 2 * window.devicePixelRatio;
    ctx.stroke();
    ctx.setLineDash([]);

    // Ghost circle on lost reference position
    ctx.beginPath();
    ctx.arc(refCanvas.x, refCanvas.y, 8 * window.devicePixelRatio, 0, Math.PI * 2);
    ctx.strokeStyle = "rgba(255, 61, 113, 0.5)";
    ctx.lineWidth = 2 * window.devicePixelRatio;
    ctx.stroke();
  }

  // Draw Animated Radar Pulse around Estimated Position
  pulseRadius += 0.35 * window.devicePixelRatio;
  pulseAlpha -= 0.012;
  if (pulseRadius > 35 * window.devicePixelRatio) {
    pulseRadius = 10 * window.devicePixelRatio;
    pulseAlpha = 0.8;
  }

  const markerColor = isOutageActive ? "#FFB300" : (isRecovering ? "#00B0FF" : "#00E5FF");

  ctx.beginPath();
  ctx.arc(estCanvas.x, estCanvas.y, pulseRadius, 0, Math.PI * 2);
  ctx.fillStyle = `rgba(${isOutageActive ? "255,179,0" : "0,229,255"}, ${Math.max(0, pulseAlpha)})`;
  ctx.fill();

  // Draw Directional Vehicle Navigation Chevron
  const sample = getRouteSample(distanceTraveled);
  const rad = (sample.heading * Math.PI / 180);

  ctx.save();
  ctx.translate(estCanvas.x, estCanvas.y);
  ctx.rotate(rad);

  ctx.beginPath();
  ctx.moveTo(0, -14 * window.devicePixelRatio);
  ctx.lineTo(9 * window.devicePixelRatio, 11 * window.devicePixelRatio);
  ctx.lineTo(0, 6 * window.devicePixelRatio);
  ctx.lineTo(-9 * window.devicePixelRatio, 11 * window.devicePixelRatio);
  ctx.closePath();

  ctx.fillStyle = markerColor;
  ctx.fill();
  ctx.strokeStyle = "#FFFFFF";
  ctx.lineWidth = 1.5 * window.devicePixelRatio;
  ctx.stroke();

  ctx.beginPath();
  ctx.arc(0, 2 * window.devicePixelRatio, 3 * window.devicePixelRatio, 0, Math.PI * 2);
  ctx.fillStyle = "#FFFFFF";
  ctx.fill();

  ctx.restore();
}

requestAnimationFrame(renderCanvas);
