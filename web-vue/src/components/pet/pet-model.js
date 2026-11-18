import * as THREE from "three";
import { DecalGeometry } from "three/addons/geometries/DecalGeometry.js";

const COLORS = {
  fur: 0xffd881,
  ginger: 0xe97939,
  cream: 0xffe6a5,
  pink: 0xeb9b73,
  ink: 0x50282a,
  iris: 0xb34d29,
  white: 0xfff0dc,
  silver: 0xc4bcb4,
  pendant: 0xc5947a,
};
const CORD_SEGMENTS = 24;
const CORD_SIDES = 8;
const CHEST_DEPTH = 0.48;
const TAIL_ROOT_DROP = 0.06;
const TAIL_ROOT_INSET = 0.05;
const TAIL_ROOT_CURVE = [[0, 0, 0], [0, 0.01, -0.14]];
const TAIL_UP = [[0.28, 0.04, -0.25], [0.7, 0.2, -0.2], [1.04, 0.62, -0.06], [1.23, 0.85, 0.12], [1.43, 0.78, 0.24]];
const TAIL_CURLED = [[0.32, -0.14, -0.26], [0.69, -0.22, -0.21], [0.91, -0.25, 0.06], [0.91, -0.25, 0.39], [0.78, -0.25, 0.63]];
const TAIL_WALK = [[0.03, 0.055, -0.35], [0.07, 0.13, -0.55], [0.13, 0.23, -0.75], [0.19, 0.36, -0.92], [0.23, 0.47, -1.02]];

// 创建可被灯光照亮的哑光材质，颜色来自桌宠原画
export function matte(color) {
  return new THREE.MeshStandardMaterial({ color: color, roughness: 0.78 });
}

// 在指定位置放置网格，既接受阴影也向地面投影
export function placeMesh(parent, geometry, material, x, y, z) {
  const mesh = new THREE.Mesh(geometry, material);
  mesh.position.set(x, y, z);
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  parent.add(mesh);
  return mesh;
}

// 将球体拉伸成给定尺寸，用同一个轮廓塑造脸颊和爪子
export function oval(parent, sphere, material, x, y, z, width, height, depth) {
  const mesh = placeMesh(parent, sphere, material, x, y, z);
  mesh.scale.set(width, height, depth);
  return mesh;
}

// 把坐标连成有厚度的曲线，截面和分段数由花纹、胡须和尾巴共用
function cordGeometry(points, radius) {
  const vertices = [];
  for (const point of points) {
    vertices.push(new THREE.Vector3(point[0], point[1], point[2]));
  }
  const curve = new THREE.CatmullRomCurve3(vertices);
  return new THREE.TubeGeometry(curve, CORD_SEGMENTS, radius, CORD_SIDES, false);
}

// 把一段曲线放入模型，供调用方调整父节点的位置和角度
export function cord(parent, points, radius, material) {
  const geometry = cordGeometry(points, radius);
  return placeMesh(parent, geometry, material, 0, 0, 0);
}

// 三条曲线使用相同的分段，让尾巴在坐姿、行走和贴地睡姿之间变形
function addTail(tail, sphere, materials) {
  const geometry = cordGeometry(TAIL_ROOT_CURVE.concat(TAIL_UP), 0.105);
  const curled = cordGeometry(TAIL_ROOT_CURVE.concat(TAIL_CURLED), 0.105);
  const walking = cordGeometry(TAIL_ROOT_CURVE.concat(TAIL_WALK), 0.105);
  geometry.morphAttributes.position = [curled.getAttribute("position"), walking.getAttribute("position")];
  geometry.morphAttributes.normal = [curled.getAttribute("normal"), walking.getAttribute("normal")];
  curled.dispose();
  walking.dispose();
  const indicesPerSegment = CORD_SIDES * 6;
  const tipStart = 17 * indicesPerSegment;
  geometry.addGroup(0, tipStart, 0);
  geometry.addGroup(tipStart, geometry.index.count - tipStart, 1);
  const colors = [materials.fur, materials.ginger];
  const skin = placeMesh(tail, geometry, colors, 0, 0, 0);
  skin.name = "tailSkin";
  const up = TAIL_UP[TAIL_UP.length - 1];
  const tip = oval(tail, sphere, materials.ginger, up[0], up[1], up[2], 0.105, 0.105, 0.105);
  tip.name = "tailTip";
  tip.userData.curled = curveTip(TAIL_CURLED);
  tip.userData.walking = curveTip(TAIL_WALK);
}

// 尾根固定在臀部中线，身体伸缩时只跟随臀部表面，不沿左右方向滑动
export function attachTailRoot(tail, chest) {
  const height = chest.position.y - TAIL_ROOT_DROP * chest.scale.y;
  const rear = chest.position.z - CHEST_DEPTH * chest.scale.z;
  tail.position.set(0, height, rear + TAIL_ROOT_INSET);
}

// 提取曲线末端，圆润的尾尖要和尾身使用相同的变形进度
function curveTip(points) {
  const tip = points[points.length - 1];
  return new THREE.Vector3(tip[0], tip[1], tip[2]);
}

// 前腿从肩、肘到腕分成三段，支撑和收爪时各段可以独立弯曲
function addFrontLeg(body, sphere, materials, side, name) {
  const shoulder = new THREE.Group();
  shoulder.name = name + "Paw";
  shoulder.position.set(side * 0.238, 0.45, 0.25);
  body.add(shoulder);
  oval(shoulder, sphere, materials.fur, 0, -0.045, 0.005, 0.155, 0.21, 0.155);
  const elbow = new THREE.Group();
  elbow.name = name + "Elbow";
  elbow.position.set(0, -0.17, 0.025);
  shoulder.add(elbow);
  oval(elbow, sphere, materials.fur, 0, -0.035, 0.012, 0.119, 0.139, 0.112);
  const wrist = new THREE.Group();
  wrist.name = name + "Wrist";
  wrist.position.set(0, -0.165, 0.082);
  elbow.add(wrist);
  oval(wrist, sphere, materials.ginger, 0, 0, 0, 0.17, 0.109, 0.207);
}

// 髋部包住折叠的后腿，脚掌仍能随膝盖伸展或收进肚子下
function addBackLeg(body, sphere, materials, side, name) {
  const hip = new THREE.Group();
  hip.name = name + "Hip";
  hip.position.set(side * 0.365, 0.3, -0.16);
  body.add(hip);
  oval(hip, sphere, materials.fur, 0, 0, 0, 0.253, 0.279, 0.32);
  const knee = new THREE.Group();
  knee.name = name + "Knee";
  knee.position.set(side * 0.045, -0.11, 0.095);
  hip.add(knee);
  oval(knee, sphere, materials.fur, 0, -0.015, 0.021, 0.145, 0.13, 0.156);
  const foot = oval(knee, sphere, materials.ginger, side * 0.025, -0.085, 0.126, 0.203, 0.103, 0.231);
  foot.name = name + "HindFoot";
}

// 胸腹和项圈各有自己的支点，伏低时头不用跟着整个坐姿翻倒
function addChest(body, sphere, materials) {
  const chest = new THREE.Group();
  chest.name = "chest";
  chest.position.set(0, 0.56, -0.15);
  body.add(chest);
  const hitBody = oval(chest, sphere, materials.fur, 0, 0, 0, 0.55, 0.585, CHEST_DEPTH);
  oval(chest, sphere, materials.cream, 0, 0.03, 0.379, 0.35, 0.406, 0.145);
  oval(chest, sphere, materials.ginger, 0, 0.29, 0.494, 0.224, 0.21, 0.084);
  const collar = new THREE.Group();
  collar.name = "collar";
  collar.position.set(0, 1.091, 0.075);
  body.add(collar);
  const band = placeMesh(collar, new THREE.TorusGeometry(0.34, 0.056, 20, 52), materials.silver, 0, 0, 0);
  band.rotation.x = Math.PI / 2;
  oval(collar, sphere, materials.silver, 0, -0.113, 0.336, 0.125, 0.13, 0.058);
  oval(collar, sphere, materials.pendant, 0, -0.113, 0.382, 0.092, 0.099, 0.027);
  cord(collar, [[-0.054, -0.166, 0.399], [-0.055, -0.066, 0.401], [0.052, -0.065, 0.403], [0.05, -0.164, 0.401], [0, -0.156, 0.41], [0, -0.106, 0.412]], 0.011, materials.cream);
  return hitBody;
}

// 用带倒角的三角轮廓塑造有厚度的耳朵
function earShape(width, height, depth) {
  const outline = new THREE.Shape();
  outline.moveTo(-width, 0);
  outline.quadraticCurveTo(-width, height * 0.45, -0.02, height);
  outline.quadraticCurveTo(width * 0.32, height * 1.14, width * 0.45, height * 0.8);
  outline.quadraticCurveTo(width, height * 0.27, width, 0);
  outline.closePath();
  return new THREE.ExtrudeGeometry(outline, {
    depth: depth, bevelEnabled: true, bevelThickness: 0.045,
    bevelSize: 0.04, bevelSegments: 3, curveSegments: 12,
  });
}

// 耳朵以根部为转轴，内耳和橘色耳尖与外耳一起移动
function addEar(head, side, materials) {
  const ear = new THREE.Group();
  ear.position.set(side * 0.48, 0.33, -0.12);
  ear.rotation.z = -side * 0.3;
  ear.scale.x = side;
  head.add(ear);
  placeMesh(ear, earShape(0.26, 0.55, 0.22), materials.ginger, 0, 0, 0);
  placeMesh(ear, earShape(0.17, 0.36, 0.025), materials.pink, 0, 0.08, 0.245);
  return ear;
}

// 按原画描出中间方形回纹和两侧斜纹，透明贴图只覆盖额头
function addMarkings(head, shell) {
  const canvas = document.createElement("canvas");
  canvas.width = 480;
  canvas.height = 230;
  const painter = canvas.getContext("2d");
  painter.lineWidth = 15;
  painter.lineJoin = "round";
  painter.lineCap = "round";
  painter.strokeStyle = "#ec762f";
  const lines = [
    [[128, 42], [80, 55], [61, 169], [123, 149], [141, 58], [110, 66], [94, 132], [112, 126], [119, 94]],
    [[201, 52], [276, 50], [286, 161], [188, 162], [185, 53], [250, 53], [252, 128], [220, 129], [219, 93], [235, 93], [235, 101]],
    [[346, 48], [399, 65], [422, 175], [352, 156], [335, 63], [373, 77], [391, 143], [369, 136], [361, 104]],
  ];
  for (const line of lines) {
    painter.beginPath();
    painter.moveTo(line[0][0], line[0][1]);
    for (let index = 1; index < line.length; index = index + 1) {
      painter.lineTo(line[index][0], line[index][1]);
    }
    painter.stroke();
  }
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  const material = new THREE.MeshStandardMaterial({
    map: texture, transparent: true, depthWrite: false, polygonOffset: true,
    polygonOffsetFactor: -4, roughness: 1,
  });
  const surface = new THREE.Mesh(shell);
  surface.updateMatrixWorld(true);
  const geometry = new DecalGeometry(surface, new THREE.Vector3(0, 0.358, 0.49),
    new THREE.Euler(0, 0, 0), new THREE.Vector3(1.06, 0.3, 0.65));
  const decal = new THREE.Mesh(geometry, material);
  decal.renderOrder = 1;
  head.add(decal);
}

// 恢复上版有立体高光的眼睛，闭眼时用单独的弧形眼睑替换它
function addEye(head, side, sphere, materials) {
  const eye = new THREE.Group();
  eye.position.set(side * 0.3, 0.035, 0.515);
  eye.rotation.y = side * 0.19;
  head.add(eye);
  oval(eye, sphere, materials.ink, 0, 0, 0, 0.166, 0.199, 0.052);
  oval(eye, sphere, materials.iris, 0.004, -0.044, 0.042, 0.128, 0.126, 0.031);
  oval(eye, sphere, materials.ink, 0, 0.025, 0.058, 0.098, 0.122, 0.023);
  oval(eye, sphere, materials.white, -0.041, 0.071, 0.082, 0.039, 0.044, 0.013);
  oval(eye, sphere, materials.cream, 0.047, -0.066, 0.076, 0.024, 0.024, 0.012);
  const eyelid = cord(head, [[side * 0.3 - 0.128, 0.045, 0.542], [side * 0.3, 0.1, 0.56], [side * 0.3 + 0.123, 0.038, 0.527]], 0.019, materials.ink);
  eyelid.visible = false;
  return { eye: eye, eyelid: eyelid };
}

// 用上版脸部比例组合眼睛、圆脸颊和嘴型，耳朵仍能独立活动
function makeHead(root, sphere, materials) {
  const head = new THREE.Group();
  head.name = "head";
  head.position.set(0, 1.65, 0.15);
  root.add(head);
  const shell = sphere.clone();
  shell.scale(0.73, 0.565, 0.59);
  const hitFace = placeMesh(head, shell, materials.fur, 0, 0, 0);
  addMarkings(head, shell);
  const ears = [];
  const eyes = [];
  for (const side of [-1, 1]) {
    ears.push(addEar(head, side, materials));
    eyes.push(addEye(head, side, sphere, materials));
    oval(head, sphere, materials.cream, side * 0.23, -0.23, 0.495, 0.285, 0.191, 0.135);
    oval(head, sphere, materials.pink, side * 0.47, -0.14, 0.45, 0.111, 0.051, 0.045);
    cord(head, [[side * 0.54, -0.17, 0.45], [side * 0.73, -0.13, 0.47], [side * 0.88, -0.14, 0.45]], 0.015, materials.ginger);
    cord(head, [[side * 0.55, -0.22, 0.45], [side * 0.71, -0.24, 0.47], [side * 0.83, -0.29, 0.46]], 0.014, materials.ginger);
  }
  oval(head, sphere, materials.ink, 0, -0.176, 0.628, 0.066, 0.043, 0.043);
  cord(head, [[-0.12, -0.28, 0.603], [-0.06, -0.31, 0.632], [0, -0.27, 0.642], [0.06, -0.31, 0.632], [0.12, -0.28, 0.603]], 0.016, materials.ink);
  return { head: head, ears: ears, eyes: eyes, hitFace: hitFace };
}

// 组合坐姿、银灰项圈和可摆动的翘尾，返回动画会用到的节点
export function createPetModel() {
  const materials = {};
  for (const name of Object.keys(COLORS)) {
    materials[name] = matte(COLORS[name]);
  }
  const sphere = new THREE.SphereGeometry(1, 48, 32);
  const root = new THREE.Group();
  const body = new THREE.Group();
  body.name = "body";
  root.add(body);
  const hitBody = addChest(body, sphere, materials);
  addBackLeg(body, sphere, materials, -1, "left");
  addBackLeg(body, sphere, materials, 1, "right");
  addFrontLeg(body, sphere, materials, -1, "left");
  addFrontLeg(body, sphere, materials, 1, "right");
  const tail = new THREE.Group();
  tail.name = "tail";
  attachTailRoot(tail, hitBody.parent);
  body.add(tail);
  addTail(tail, sphere, materials);
  const face = makeHead(body, sphere, materials);
  return { root: root, body: body, head: face.head, ears: face.ears, eyes: face.eyes, tail: tail, hitParts: [face.hitFace, hitBody] };
}
