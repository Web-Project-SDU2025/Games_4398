<template>
  <canvas ref="surface" class="auth-background" aria-hidden="true"></canvas>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from "vue";

const props = defineProps({
  paused: Boolean,
  speed: { type: Number, default: 1 },
});

const FAR_DEPTH = 48;
const GRID_HEIGHT = 2.12;
const PARTICLE_COUNT = 84;
const FRAME_INTERVAL = 30;
const MAX_FRAME_SECONDS = 0.08;
const MAX_PIXEL_RATIO = 1.5;
const surface = ref(null);
let context;
let observer;
let motion;
let width = 0;
let height = 0;
let focal = 0;
let horizon = 0;
let ratio = 1;
let backdrop;
let frame = 0;
let previous = 0;
let elapsed = 0;

// 同一格始终得到同一个随机值，光块不会在相邻帧之间闪动
function noise(column, row, layer) {
  const value = Math.sin(column * 127.1 + row * 311.7 + layer * 53.3) * 43758.5453;
  return value - Math.floor(value);
}

// 生成向四个斜向散开的粒子，并错开每个粒子的起点和周期
function createParticles() {
  const result = [];
  for (let index = 0; index < PARTICLE_COUNT; index = index + 1) {
    const spread = noise(index, 29, 0);
    let angle = 0.1 + spread * 0.45;
    if (index % 5 === 0) {
      angle = 0.55 + spread * 0.98;
    }
    const quadrant = index % 4;
    let directionX = Math.cos(angle);
    let directionY = Math.sin(angle);
    if (quadrant < 2) {
      directionX = -directionX;
    }
    if (quadrant % 2 === 0) {
      directionY = -directionY;
    }
    result.push({
      origin: noise(index, 19, 0) * 0.19,
      directionX: directionX,
      directionY: directionY,
      phase: noise(index, 43, 0),
      duration: 8 + noise(index, 53, 0) * 8,
      size: 0.55 + noise(index, 8, 0) * 1.2,
      glow: noise(index, 71, 0) > 0.65,
    });
  }
  return result;
}

const particles = createParticles();

// 将网格上的点投影到画布，远处靠近画面中间的消失线
function project(column, depth, side) {
  return {
    x: width / 2 + focal * column / depth,
    y: horizon + side * focal * GRID_HEIGHT / depth,
  };
}

// 沿点的顺序建立路径，调用方决定描边还是填充
function trace(points) {
  context.beginPath();
  const first = points[0];
  context.moveTo(first.x, first.y);
  for (let index = 1; index < points.length; index = index + 1) {
    const point = points[index];
    context.lineTo(point.x, point.y);
  }
}

// 用指定的颜色和宽度描绘当前路径
function stroke(color, lineWidth) {
  context.strokeStyle = color;
  context.lineWidth = lineWidth;
  context.stroke();
}

// 在部分网格中绘制光块，或用细线画出电路纹路
function drawCell(column, row, depth, side, fog) {
  const seed = noise(column, row, side);
  if (seed < 0.7) {
    return;
  }
  if (seed < 0.94) {
    const pulse = 0.7 + 0.3 * Math.sin(elapsed * 0.7 + row * 2 + seed * 30);
    trace([
      project(column, depth, side),
      project(column + 1, depth, side),
      project(column + 1, depth + 1, side),
      project(column, depth + 1, side),
    ]);
    context.closePath();
    let blue = "48,59,246";
    if (seed > 0.87) {
      blue = "35,119,255";
    }
    context.fillStyle = `rgba(${blue},${0.34 * fog * pulse})`;
    context.fill();
    return;
  }
  if (noise(column, row + 8, side) <= 0.62) {
    return;
  }
  const end = project(column + 1.7, depth + 1.6, side);
  trace([
    project(column, depth + 3, side),
    project(column, depth, side),
    project(column + 0.85, depth, side),
    project(column + 0.85, depth + 1.6, side),
    end,
  ]);
  stroke(`rgba(35,96,255,${0.33 * fog})`, 3);
  stroke(`rgba(102,200,255,${0.76 * fog})`, 0.9);
  const radiusX = Math.min(3.4, 16 / depth);
  const radiusY = Math.min(2, 8 / depth);
  context.beginPath();
  context.ellipse(end.x, end.y, radiusX, radiusY, 0, 0, Math.PI * 2);
  stroke(`rgba(151,227,255,${0.68 * fog})`, 0.8);
}

// 分别绘制消失线上下的网格，远处的线条和光块逐渐淡出
function drawPlane(side) {
  let travel = elapsed * 0.83;
  if (side < 0) {
    travel = elapsed * 1.05;
  }
  const columns = Math.ceil(width * FAR_DEPTH / (2 * focal)) + 1;
  const near = Math.max(1, focal * GRID_HEIGHT / (height * 0.53));
  for (let column = -columns; column <= columns; column = column + 1) {
    trace([project(column, near, side), project(column, FAR_DEPTH, side)]);
    stroke("rgba(70,122,255,.24)", 1);
  }
  for (let row = Math.ceil(travel + near); row < travel + FAR_DEPTH; row = row + 1) {
    const depth = row - travel;
    const fog = 1 - depth / FAR_DEPTH;
    const span = Math.ceil(width * (depth + 4) / (2 * focal)) + 1;
    const point = project(0, depth, side);
    trace([{ x: 0, y: point.y }, { x: width, y: point.y }]);
    stroke(`rgba(65,117,255,${0.29 * fog})`, 1);
    for (let column = -span; column < span; column = column + 1) {
      drawCell(column, row, depth, side, fog);
    }
  }
}

// 粒子从画面中间向外移动，离开前逐渐变暗
function drawParticles() {
  for (const particle of particles) {
    const progress = (particle.phase + elapsed / particle.duration) % 1;
    const distance = 0.58 * progress;
    const x = width * (0.5 + particle.directionX * (particle.origin + distance));
    const y = horizon + height * particle.directionY * distance;
    const alpha = Math.min(progress / 0.16, (1 - progress) / 0.28, 1);
    const radius = particle.size * (0.7 + progress * 0.55);
    let color = `rgba(160,168,255,${alpha * 0.9})`;
    if (particle.glow) {
      context.beginPath();
      context.arc(x, y, radius * 2.8, 0, Math.PI * 2);
      context.fillStyle = `rgba(128,100,255,${alpha * 0.32})`;
      context.fill();
      color = `rgba(216,191,255,${alpha})`;
    }
    context.beginPath();
    context.arc(x, y, radius, 0, Math.PI * 2);
    context.fillStyle = color;
    context.fill();
  }
}

// 在同一帧中组合底色、网格和粒子
function draw() {
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
  context.fillStyle = backdrop;
  context.fillRect(0, 0, width, height);
  drawPlane(-1);
  drawPlane(1);
  drawParticles();
}

// 每秒绘制约三十帧，速度设置只改变场景移动的距离
function tick(now) {
  let delta = now - previous;
  if (previous === 0) {
    delta = FRAME_INTERVAL;
  }
  if (delta >= FRAME_INTERVAL) {
    const seconds = Math.min(delta / 1000, MAX_FRAME_SECONDS);
    elapsed = elapsed + seconds * props.speed;
    previous = now;
    draw();
  }
  frame = requestAnimationFrame(tick);
}

// 窗口尺寸变化时重画当前画面，超宽窗口也保留合适的网格密度
function resize() {
  const canvas = surface.value;
  const size = canvas.getBoundingClientRect();
  if (size.width < 1 || size.height < 1) {
    return;
  }
  width = size.width;
  height = size.height;
  ratio = Math.min(window.devicePixelRatio, MAX_PIXEL_RATIO);
  canvas.width = Math.round(width * ratio);
  canvas.height = Math.round(height * ratio);
  focal = Math.max(height * 0.82, width * 0.28);
  horizon = height * 0.49;
  backdrop = context.createLinearGradient(0, 0, 0, height);
  backdrop.addColorStop(0, "#172a9b");
  backdrop.addColorStop(0.28, "#0c1b66");
  backdrop.addColorStop(0.5, "#03091e");
  backdrop.addColorStop(0.73, "#0a1d65");
  backdrop.addColorStop(1, "#162d98");
  draw();
  updateMotion();
}

// 暂停时保留画面，恢复后接着当前位置播放
function updateMotion() {
  cancelAnimationFrame(frame);
  frame = 0;
  previous = 0;
  if (motion && !motion.matches && !props.paused && backdrop) {
    frame = requestAnimationFrame(tick);
  }
}

// 挂载后监听画布尺寸和系统的减少动态效果设置
function mountBackground() {
  const canvas = surface.value;
  context = canvas.getContext("2d");
  if (!context) {
    return;
  }
  motion = window.matchMedia("(prefers-reduced-motion: reduce)");
  motion.addEventListener("change", updateMotion);
  observer = new ResizeObserver(resize);
  observer.observe(canvas);
  resize();
}

// 登录页退出后释放画布的动画和监听
function disposeBackground() {
  cancelAnimationFrame(frame);
  if (motion) {
    motion.removeEventListener("change", updateMotion);
  }
  if (observer) {
    observer.disconnect();
  }
}

// 读取用户的暂停设置，让 Vue 在设置变化时更新动画
function readPaused() {
  return props.paused;
}

watch(readPaused, updateMotion);
onMounted(mountBackground);
onBeforeUnmount(disposeBackground);
</script>
