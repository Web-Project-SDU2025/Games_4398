import * as THREE from "three";
import { placeMesh } from "./pet-model.js";

// 金光走过星轨后再消散，减弱动效时只在星位上短暂淡出
const CELEBRATION = { travelMs: 460, fadeMs: 360, reducedMs: 120, rise: 0.2 };
// 四角星从细光芯向上下左右伸出，和可点的圆形星位区分开
const SPARK_SHAPE = [
  [0, 0.095], [0.016, 0.018], [0.063, 0], [0.016, -0.018],
  [0, -0.095], [-0.016, -0.018], [-0.063, 0], [-0.016, 0.018],
];
// 主光点和分开的星屑使用同一套形状与颜色
const SPARK_STYLE = { color: 0xffe0a2, order: 4, scale: 0.48 };

// 返回这一帧的星轨进度和余光透明度，超时后不再绘制
export function readStarCelebration(elapsed, reduced) {
  if (elapsed < 0) {
    return null;
  }
  if (reduced) {
    if (elapsed >= CELEBRATION.reducedMs) {
      return null;
    }
    const opacity = 1 - elapsed / CELEBRATION.reducedMs;
    return { path: 1, drift: 0, opacity: opacity };
  }
  const end = CELEBRATION.travelMs + CELEBRATION.fadeMs;
  if (elapsed >= end) {
    return null;
  }
  const path = THREE.MathUtils.smoothstep(elapsed, 0, CELEBRATION.travelMs);
  const drift = THREE.MathUtils.smoothstep(elapsed, CELEBRATION.travelMs, end);
  return { path: path, drift: drift, opacity: 1 - drift };
}

// 创建星轨上的光点，材质不写入深度也不会参与物品点选
export function createStarCelebration(group, points, depth) {
  const shape = new THREE.Shape();
  shape.moveTo(SPARK_SHAPE[0][0], SPARK_SHAPE[0][1]);
  for (let index = 1; index < SPARK_SHAPE.length; index = index + 1) {
    shape.lineTo(SPARK_SHAPE[index][0], SPARK_SHAPE[index][1]);
  }
  shape.closePath();
  const geometry = new THREE.ShapeGeometry(shape);
  const material = new THREE.MeshBasicMaterial({
    color: SPARK_STYLE.color, transparent: true, depthWrite: false, toneMapped: false,
  });
  const tracer = addSpark(group, geometry, material, depth);
  const accents = [];
  for (const point of points) {
    const spark = addSpark(group, geometry, material, depth);
    spark.scale.setScalar(SPARK_STYLE.scale);
    accents.push({ object: spark, point: point });
  }
  let startedAt = -Infinity;

  // 第三颗星接通后记录起点，使用小屋现有的渲染时钟播放
  function play(now) {
    startedAt = now;
  }

  // 切走夜景、合上窗帘或换账户时立即停止余光
  function stop() {
    startedAt = -Infinity;
    tracer.visible = false;
    for (const accent of accents) {
      accent.object.visible = false;
    }
  }

  // 金光先沿连线前进，再让三颗星的余光轻轻散开
  function update(now, reduced) {
    const pose = readStarCelebration(now - startedAt, reduced);
    if (!pose) {
      stop();
      return;
    }
    material.opacity = pose.opacity;
    tracer.visible = pose.path < 1;
    if (tracer.visible) {
      moveTracer(tracer, points, pose.path);
    }
    for (const accent of accents) {
      const spark = accent.object;
      spark.visible = pose.path === 1;
      spark.position.x = accent.point[0];
      spark.position.y = accent.point[1] + pose.drift * CELEBRATION.rise;
    }
  }

  stop();
  return { play: play, stop: stop, update: update };
}

// 把光点放在玻璃上，仍由窗棂和窗帘按正常深度遮挡
function addSpark(group, geometry, material, depth) {
  const spark = placeMesh(group, geometry, material, 0, 0, depth);
  spark.castShadow = false;
  spark.renderOrder = SPARK_STYLE.order;
  return spark;
}

// 把总进度分配给相邻的两颗星，按这段连线插入光点坐标
function moveTracer(tracer, points, progress) {
  const step = progress * (points.length - 1);
  const lastSegment = points.length - 2;
  const index = Math.min(Math.floor(step), lastSegment);
  const amount = step - index;
  const start = points[index];
  const end = points[index + 1];
  tracer.position.x = THREE.MathUtils.lerp(start[0], end[0], amount);
  tracer.position.y = THREE.MathUtils.lerp(start[1], end[1], amount);
}
