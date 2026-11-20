import * as THREE from "three";
import { matte, placeMesh } from "./pet-model.js";
import { drawLandscape, printNightLandscape } from "./pet-landscape.js";
import { createStarCelebration } from "./pet-star-celebration.js";

// 各种窗景开始的整点，清晨之前仍是夜色
export const WINDOW_VIEWS = [
  { key: "morning", label: "晨光", topic: "window-morning", startHour: 6 },
  { key: "evening", label: "晚霞", topic: "window-evening", startHour: 16 },
  { key: "night", label: "夜色", topic: "window-night", startHour: 19 },
];
// 只有夜色会露出连星的轨迹
export const WINDOW_NIGHT = 2;
// 窗景各层在窗棂后面，分别叠在不透明的晨景底图上
const PANE = { width: 1.5, height: 1.8, depth: 0.126, layerGap: 0.001 };
// 色彩过渡以毫秒计，减弱动效只保留更短的淡入淡出
const FADE_MS = { normal: 260, reduced: 100 };
// 星星和连线贴着玻璃，深度必须小于窗棂正面的 0.16
const STAR_LAYER = { depth: 0.141, radius: 0.035, starScale: 0.18, lineThickness: 0.012 };
// 彩蛋光点略高于连线，仍在窗棂前表面之后
const CELEBRATION_DEPTH = 0.151;
// 三颗可点的星由左到右，最后一颗放在月亮左上方留出空隙
const STAR_POINTS = [[-0.51, 0.57], [-0.25, 0.37], [0.17, 0.71]];
// 窗帘收起后保留的宽度，以布料原宽为 1
const CURTAIN_OPEN = 0.17;
// 两边窗帘拉拢时在窗框中间相接
const CURTAIN_CLOSED = 0.84;
// 窗帘收放的速度，越大越快贴近目标
const CURTAIN_SPEED = 15;
// 一次拨动后风铃继续摇摆的秒数
const CHIME_SECONDS = 1.4;
// 摆动相位每秒增加的弧度，决定摇摆频率
const CHIME_SPEED = 19;
// 风铃刚拨动时的最大转角，随后逐渐变小
const CHIME_ANGLE = 0.2;

// 根据拨钟读到的 24 小时制时间，返回对应的窗景编号
export function readWindowView(hour) {
  let view = WINDOW_NIGHT;
  for (let index = 0; index < WINDOW_VIEWS.length; index = index + 1) {
    if (hour < WINDOW_VIEWS[index].startHour) {
      break;
    }
    view = index;
  }
  return view;
}

// 记录当前三种窗景各占多少，改目标时接着当前画面淡入，不跳回整张底图
export function createWindowFade() {
  const weights = [1, 0, 0];
  const start = [1, 0, 0];
  let target = 0;
  let startedAt = 0;
  let active = false;

  // 按已经过的时间更新窗景比例，低帧率也不会拉长切换时间
  function update(now, reduced) {
    if (!active) {
      return weights;
    }
    let duration = FADE_MS.normal;
    if (reduced) {
      duration = FADE_MS.reduced;
    }
    const elapsed = now - startedAt;
    const progress = THREE.MathUtils.smoothstep(elapsed, 0, duration);
    for (let index = 0; index < weights.length; index = index + 1) {
      let end = 0;
      if (index === target) {
        end = 1;
      }
      weights[index] = THREE.MathUtils.lerp(start[index], end, progress);
    }
    active = progress < 1;
    return weights;
  }

  // 同一个目标不重启过渡，改变目标时保留用户刚看到的混合画面
  function select(mode, now, reduced) {
    if (mode === target) {
      return;
    }
    update(now, reduced);
    for (let index = 0; index < weights.length; index = index + 1) {
      start[index] = weights[index];
    }
    target = mode;
    startedAt = now;
    active = true;
  }

  return { select: select, update: update };
}

// 玻璃只绘制三张固定贴图，换景时不重画位图也不改变窗框的深度
function addPanes(frame) {
  const panes = [];
  const shape = new THREE.PlaneGeometry(PANE.width, PANE.height);
  for (let index = 0; index < WINDOW_VIEWS.length; index = index + 1) {
    const texture = drawLandscape(WINDOW_VIEWS[index].key);
    const material = new THREE.MeshBasicMaterial({ map: texture });
    const depth = PANE.depth + index * PANE.layerGap;
    const pane = placeMesh(frame, shape, material, 0, 0, depth);
    pane.castShadow = false;
    if (index > 0) {
      material.transparent = true;
      material.depthWrite = false;
      pane.renderOrder = index;
      pane.visible = false;
    }
    panes.push(pane);
  }
  return panes;
}

// 上层会盖住下层的一部分，先换算中间层透明度，让三张图的配比保持正确
function paintPanes(panes, weights) {
  const nightWeight = weights[2];
  const spaceBelowNight = 1 - nightWeight;
  let eveningOpacity = 0;
  if (spaceBelowNight > 0) {
    eveningOpacity = weights[1] / spaceBelowNight;
  }
  panes[1].material.opacity = eveningOpacity;
  panes[1].visible = eveningOpacity > 0;
  panes[2].material.opacity = nightWeight;
  panes[2].visible = nightWeight > 0;
}

// 给窗帘和风铃放置有厚度的网格，点选和绘制共用它
function block(parent, material, x, y, z, width, height, depth) {
  const shape = new THREE.BoxGeometry(width, height, depth);
  return placeMesh(parent, shape, material, x, y, z);
}

// 左右窗帘共用褶皱尺寸，返回可分别收拢的两片布
function addCurtains(frame) {
  const curtains = new THREE.Group();
  frame.add(curtains);
  const fabric = matte(0xbf8971);
  const fold = matte(0xe5b49b);
  const panels = [];
  for (const side of [-1, 1]) {
    const panel = new THREE.Group();
    panel.position.set(side * 0.77, 0.015, 0.219);
    panel.scale.x = side;
    curtains.add(panel);
    panels.push(panel);
    block(panel, fabric, -0.46, 0, 0, 0.92, 1.86, 0.05);
    for (let pleat = 0; pleat < 5; pleat = pleat + 1) {
      block(panel, fold, -0.09 - pleat * 0.18, 0, 0.026, 0.025, 1.83, 0.026);
    }
  }
  return { group: curtains, panels: panels };
}

// 在玻璃上放置可点的星和连线，返回点选节点以及逐段显示的线
function addConstellation(frame) {
  const stars = new THREE.Group();
  frame.add(stars);
  const buttons = [];
  const links = [];
  for (let index = 0; index < STAR_POINTS.length; index = index + 1) {
    const point = STAR_POINTS[index];
    const material = new THREE.MeshBasicMaterial({ color: 0xe2d8af });
    const shape = new THREE.SphereGeometry(STAR_LAYER.radius, 12, 8);
    const star = placeMesh(stars, shape, material, point[0], point[1], STAR_LAYER.depth);
    star.scale.z = STAR_LAYER.starScale;
    star.castShadow = false;
    buttons.push(star);
    if (index > 0) {
      const prior = STAR_POINTS[index - 1];
      const changeX = point[0] - prior[0];
      const changeY = point[1] - prior[1];
      // 让窗棂挡住玻璃上的连线，经过两扇窗之间时不会浮在木框上
      const centerX = (point[0] + prior[0]) / 2;
      const centerY = (point[1] + prior[1]) / 2;
      const length = Math.hypot(changeX, changeY);
      const lineMaterial = new THREE.MeshBasicMaterial({ color: 0xffd788 });
      const line = block(stars, lineMaterial, centerX, centerY, STAR_LAYER.depth, length, 0.012, STAR_LAYER.lineThickness);
      line.rotation.z = Math.atan2(changeY, changeX);
      line.castShadow = false;
      links.push(line);
    }
  }
  return { group: stars, buttons: buttons, links: links };
}

// 挂钩和铃管共用窗沿上的支点，拨动时绕这个支点摇摆
function addChime(frame) {
  const chime = new THREE.Group();
  chime.position.set(0.59, 0.96, 0.32);
  frame.add(chime);
  const brass = matte(0xd4a65d);
  block(chime, brass, 0, -0.07, 0, 0.26, 0.04, 0.1);
  for (const offset of [-1, 0, 1]) {
    block(chime, brass, offset * 0.076, -0.23, 0, 0.024, 0.28 - Math.abs(offset) * 0.075, 0.028);
  }
  block(chime, matte(0x78988b), 0, -0.443, 0, 0.09, 0.08, 0.027);
  return chime;
}

// 组合窗景、窗帘、星位和风铃，并返回场景使用的操作
export function createWindowDetails(frame) {
  const panes = addPanes(frame);
  const fade = createWindowFade();
  const curtains = addCurtains(frame);
  const constellation = addConstellation(frame);
  const celebration = createStarCelebration(constellation.group, STAR_POINTS, CELEBRATION_DEPTH);
  const chime = addChime(frame);

  let sceneMode = 0;
  let curtainClosed = false;
  let drawnWidth = CURTAIN_OPEN;
  let chimeAt = -Infinity;

  // 拨钟确定目标时段和星位状态，画面渐变由渲染循环推进
  function setView(mode, progress, now, reduced) {
    sceneMode = mode;
    fade.select(mode, now, reduced);
    setStars(progress);
  }

  // 将已连接的星星和轨迹变成暖金色
  function setStars(progress) {
    constellation.group.visible = sceneMode === WINDOW_NIGHT && !curtainClosed;
    if (!constellation.group.visible || progress === 0) {
      celebration.stop();
    }
    const buttons = constellation.buttons;
    for (let index = 0; index < buttons.length; index = index + 1) {
      const star = buttons[index];
      const color = star.material.color;
      color.setHex(0xe2d8af);
      if (index < progress) {
        color.setHex(0xffbd57);
      }
    }
    const links = constellation.links;
    for (let index = 0; index < links.length; index = index + 1) {
      links[index].visible = index + 1 < progress;
    }
  }

  // 第一次连完本次小屋的星轨时播放动效，并返回给明信片使用的插画
  function celebrate(now) {
    celebration.play(now);
    const texture = panes[WINDOW_NIGHT].material.map;
    return printNightLandscape(texture.image, STAR_POINTS, PANE);
  }

  // 拉拢窗帘后隐藏星位，开启时继续保留已连的星
  function toggleCurtain(progress) {
    curtainClosed = !curtainClosed;
    setStars(progress);
    return curtainClosed;
  }

  // 记录拨动风铃的时刻，后续帧用它计算摆动
  function ring(now) {
    chimeAt = now;
  }

  // 更新玻璃透明度、窗帘和风铃，并把当前景色比例交给房间补光
  function update(now, delta, reduced) {
    const weights = fade.update(now, reduced);
    paintPanes(panes, weights);
    celebration.update(now, reduced);
    let targetWidth = CURTAIN_OPEN;
    if (curtainClosed) {
      targetWidth = CURTAIN_CLOSED;
    }
    if (reduced) {
      drawnWidth = targetWidth;
    } else {
      drawnWidth = THREE.MathUtils.damp(drawnWidth, targetWidth, CURTAIN_SPEED, delta);
    }
    curtains.panels[0].scale.x = -drawnWidth;
    curtains.panels[1].scale.x = drawnWidth;
    const age = (now - chimeAt) / 1000;
    chime.rotation.z = 0;
    if (!reduced && age < CHIME_SECONDS) {
      const swing = Math.sin(age * CHIME_SPEED);
      const remaining = 1 - age / CHIME_SECONDS;
      chime.rotation.z = swing * CHIME_ANGLE * remaining;
    }
    return weights;
  }

  setStars(0);
  update(0, 0, true);
  return {
    curtains: curtains.group,
    stars: constellation.buttons,
    chime: chime,
    setView: setView,
    setStars: setStars,
    celebrate: celebrate,
    toggleCurtain: toggleCurtain,
    ring: ring,
    update: update,
  };
}
