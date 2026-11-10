<template>
  <section class="account-grid-page">
    <canvas ref="surface" class="account-grid-canvas" aria-hidden="true"></canvas>
    <div class="account-grid-content">
      <h2 class="account-grid-title">
        <span class="account-grid-icon" aria-hidden="true">
          <span :class="icon"></span>
        </span>
        {{ title }}
      </h2>
      <slot />
    </div>
  </section>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from "vue";

defineProps({ title: String, icon: String });

const surface = ref(null);
const far = 48;
let canvas, context, observer, motion;
let width, height, focal, horizon, ratio, backdrop;
let frame = 0;
let previous = 0;
let elapsed = 0;

function noise(a, b, c) {
  const value = Math.sin(a * 127.1 + b * 311.7 + c * 53.3) * 43758.5453;
  return value - Math.floor(value);
}

const particles = Array.from({ length: 84 }, (_, index) => {
  const spread = noise(index, 29, 0);
  // 大多数光点斜向两侧，少量沿更陡的角度散开。
  const angle = index % 5 === 0 ? 0.55 + spread * 0.98 : 0.1 + spread * 0.45;
  const quadrant = index % 4;
  return {
    origin: noise(index, 19, 0) * 0.19,
    directionX: Math.cos(angle) * (quadrant < 2 ? -1 : 1),
    directionY: Math.sin(angle) * (quadrant % 2 === 0 ? -1 : 1),
    phase: noise(index, 43, 0),
    duration: 8 + noise(index, 53, 0) * 8,
    size: 0.55 + noise(index, 8, 0) * 1.2,
    glow: noise(index, 71, 0) > 0.65,
  };
});

// 上下两层共用消失线，越远的网格越密、越暗。
function point(x, depth, side) {
  return [width / 2 + focal * x / depth, horizon + side * focal * 2.12 / depth];
}

function trace(points) {
  context.beginPath();
  context.moveTo(...points[0]);
  for (const point of points.slice(1)) context.lineTo(...point);
}

function stroke(color, lineWidth = 1) {
  context.strokeStyle = color;
  context.lineWidth = lineWidth;
  context.stroke();
}

function drawPlane(side) {
  const travel = elapsed * (side < 0 ? 1.05 : .83);
  const columns = Math.ceil(width * far / (2 * focal)) + 1;
  const near = Math.max(1, focal * 2.12 / (height * .53));
  for (let column = -columns; column <= columns; column++) {
    trace([point(column, near, side), point(column, far, side)]);
    stroke("rgba(70,122,255,.24)");
  }
  for (let row = Math.ceil(travel + near); row < travel + far; row++) {
    const depth = row - travel;
    const fog = 1 - depth / far;
    const span = Math.ceil(width * (depth + 4) / (2 * focal)) + 1;
    const y = point(0, depth, side)[1];
    trace([[0, y], [width, y]]);
    stroke(`rgba(65,117,255,${.29 * fog})`);
    for (let column = -span; column < span; column++) {
      const seed = noise(column, row, side);
      if (seed < .7) continue;
      if (seed < .94) {
        const pulse = .7 + .3 * Math.sin(elapsed * .7 + row * 2 + seed * 30);
        trace([point(column, depth, side), point(column + 1, depth, side),
          point(column + 1, depth + 1, side), point(column, depth + 1, side)]);
        context.closePath();
        const blue = seed > .87 ? "35,119,255" : "48,59,246";
        context.fillStyle = `rgba(${blue},${.34 * fog * pulse})`;
        context.fill();
      } else if (noise(column, row + 8, side) > .62) {
        const end = point(column + 1.7, depth + 1.6, side);
        trace([point(column, depth + 3, side), point(column, depth, side),
          point(column + .85, depth, side), point(column + .85, depth + 1.6, side), end]);
        stroke(`rgba(35,96,255,${.33 * fog})`, 3);
        stroke(`rgba(102,200,255,${.76 * fog})`, .9);
        context.beginPath();
        context.ellipse(...end, Math.min(3.4, 16 / depth), Math.min(2, 8 / depth), 0, 0, Math.PI * 2);
        stroke(`rgba(151,227,255,${.68 * fog})`, .8);
      }
    }
  }
}

// 光点从黑带浮现，沿固定的斜线向外移动。
function drawParticles() {
  for (const particle of particles) {
    const progress = (particle.phase + elapsed / particle.duration) % 1;
    const distance = 0.58 * progress;
    const x = width * (0.5 + particle.directionX * (particle.origin + distance));
    const y = horizon + height * particle.directionY * distance;
    const alpha = Math.min(progress / 0.16, (1 - progress) / 0.28, 1);
    const radius = particle.size * (0.7 + progress * 0.55);

    if (particle.glow) {
      context.beginPath();
      context.arc(x, y, radius * 2.8, 0, Math.PI * 2);
      context.fillStyle = `rgba(128,100,255,${alpha * 0.32})`;
      context.fill();
    }
    context.beginPath();
    context.arc(x, y, radius, 0, Math.PI * 2);
    context.fillStyle = particle.glow
      ? `rgba(216,191,255,${alpha})`
      : `rgba(160,168,255,${alpha * 0.9})`;
    context.fill();
  }
}

function draw() {
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
  context.fillStyle = backdrop;
  context.fillRect(0, 0, width, height);
  drawPlane(-1);
  drawPlane(1);
  drawParticles();
}

function tick(now) {
  const delta = previous ? now - previous : 34;
  if (delta >= 30) {
    elapsed += Math.min(delta / 1000, .08);
    previous = now;
    draw();
  }
  frame = requestAnimationFrame(tick);
}

function resize() {
  const size = canvas.getBoundingClientRect();
  if (size.width < 1 || size.height < 1) return;
  width = size.width;
  height = size.height;
  ratio = Math.min(devicePixelRatio || 1, 1.5);
  canvas.width = Math.round(width * ratio);
  canvas.height = Math.round(height * ratio);
  focal = height * .82;
  horizon = height * .49;
  backdrop = context.createLinearGradient(0, 0, 0, height);
  backdrop.addColorStop(0, "#172a9b");
  backdrop.addColorStop(.28, "#0c1b66");
  backdrop.addColorStop(.5, "#03091e");
  backdrop.addColorStop(.73, "#0a1d65");
  backdrop.addColorStop(1, "#162d98");
  draw();
  if (!frame && !motion.matches) frame = requestAnimationFrame(tick);
}

function updateMotion() {
  cancelAnimationFrame(frame);
  frame = 0;
  previous = 0;
  resize();
}

onMounted(() => {
  canvas = surface.value;
  context = canvas.getContext("2d");
  if (!context) return;
  motion = matchMedia("(prefers-reduced-motion: reduce)");
  motion.addEventListener("change", updateMotion);
  observer = new ResizeObserver(resize);
  observer.observe(canvas);
  resize();
});

onBeforeUnmount(() => {
  cancelAnimationFrame(frame);
  motion?.removeEventListener("change", updateMotion);
  observer?.disconnect();
});
</script>

<style>
.platform-main section.account-grid-page,
.account-form-dialog section.account-grid-page {
  position: relative;
  isolation: isolate;
  overflow: clip;
  padding: clamp(16px, 3vw, 40px);
  background: #07153d;
  color: #e0f8ff;
}

.account-grid-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.account-grid-content {
  position: relative;
  width: min(100%, 480px);
  box-sizing: border-box;
  padding: clamp(16px, 3vw, 28px);
  background: linear-gradient(135deg, #071536f5, #080f2ee8);
  border: 1px solid #439ae2;
  box-shadow: inset 0 0 28px #2164dc35, 0 0 16px #267af45c;
}

.account-grid-page .account-grid-content .account-grid-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 0 20px;
  color: #9feaff;
  font: 700 clamp(20px, 2.4vw, 27px)/1.3 system-ui, "Microsoft YaHei", sans-serif;
  letter-spacing: .06em;
  text-shadow: 0 0 12px #36bbff88;
}

.account-grid-icon {
  display: grid;
  place-items: center;
  flex: 0 0 40px;
  height: 38px;
  clip-path: polygon(20% 0, 100% 0, 100% 80%, 80% 100%, 0 100%, 0 20%);
  background: linear-gradient(155deg, #123e80, #0a2450);
  box-shadow: inset 0 0 0 1px #57cfff;
  color: #6de6ff;
  font-size: 21px;
}

.account-grid-page .account-grid-form {
  display: grid;
  gap: 15px;
  font-family: system-ui, "Microsoft YaHei", sans-serif;
}

.account-grid-page .account-grid-form label {
  display: grid;
  gap: 6px;
  min-width: 0;
  color: #aee8ff;
  font-weight: 600;
  letter-spacing: .04em;
}

.account-grid-page .account-grid-form :is(input, textarea) {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  min-height: 40px;
  padding: 8px 11px;
  border: 1px solid #35679d;
  border-left: 3px solid #3c9ad5;
  border-radius: 0;
  background: #060f2b;
  color: #e7f8ff;
  font: inherit;
  font-weight: 400;
  letter-spacing: normal;
}

.account-grid-page .account-grid-form textarea {
  min-height: 68px;
  resize: vertical;
}

.account-grid-page .account-grid-form input[readonly] {
  background: #101e3b;
  color: #b9cde7;
}

.account-grid-page .account-grid-form :is(input, textarea):focus-visible {
  outline: 2px solid #6de4ff;
  outline-offset: 2px;
}

.account-grid-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.account-grid-page .account-grid-actions button {
  --cut: polygon(12px 0, calc(100% - 12px) 0, 100% 50%, calc(100% - 12px) 100%, 12px 100%, 0 50%);
  position: relative;
  isolation: isolate;
  min-width: 98px;
  min-height: 40px;
  padding: 8px 20px;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: #b8edff;
  font: inherit;
  font-weight: 700;
  letter-spacing: .07em;
  text-shadow: 0 0 8px #25a2ff80;
}

.account-grid-actions button::before,
.account-grid-actions button::after {
  position: absolute;
  z-index: -1;
  inset: 0;
  clip-path: var(--cut);
  content: "";
}

.account-grid-actions button::before {
  background: linear-gradient(#159fe6, #a8edff);
}

.account-grid-actions button::after {
  inset: 2px;
  background: linear-gradient(155deg, #15417d, #081735 72%);
  box-shadow: inset 0 0 0 1px #6acaf052;
}

.account-grid-actions button[type="button"]::before {
  background: linear-gradient(#325d90, #739ec6);
}

.account-grid-page .account-grid-actions button:not(:disabled):hover::after {
  background: linear-gradient(155deg, #1d61a8, #0c2352 72%);
}

.account-grid-page .account-grid-actions button:focus-visible {
  outline: 2px solid #98f4ff;
  outline-offset: 3px;
}

.account-grid-page .account-grid-actions button:disabled {
  opacity: .56;
}
</style>
