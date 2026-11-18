import * as THREE from "three";
import { createPetModel, matte, oval, placeMesh, cord } from "./pet-model.js";
import { createPetMotion } from "./pet-motion.js";
import { REST_DURATION } from "./pet-rest.js";
import { BOWL_START, createPetFloor, TOY_RADIUS, TOY_START } from "./pet-floor.js";
import { createPetPicker } from "./pet-picker.js";
import { PLAY_CONTACT, PLAY_DURATION, readPlayPose } from "./pet-play.js";
import { dragPropPosition, makePropReturn, readPropReturn } from "./pet-props.js";

const ROOM_COLOR = 0xeee4cf;
const CAMERA_DISTANCE = 7.6;
const SCENE_HALF_WIDTH = 3.38;
const TAP_DISTANCE = 8;
const MAX_PIXEL_RATIO = 1.6;
const LOOK_DAMPING = 11;
const PRINT_DURATION = 1.8;
const LAMP_INTENSITY = 8;
const CLOCK_STEP = Math.PI / 30;
const PROP_REACTION_TIME = 1.3;
const HOLD_MS = 280;
const TAP_FEEDBACK_MS = 280;
const ACTION_DURATION = { pet: 0.9, play: PLAY_DURATION, feed: 1.14, rest: REST_DURATION };

// 用指定尺寸的盒体搭建窗框和凳腿
function box(parent, material, x, y, z, width, height, depth) {
  const geometry = new THREE.BoxGeometry(width, height, depth);
  return placeMesh(parent, geometry, material, x, y, z);
}

// 放置装有猫粮的矮碗，食物会在喂食动作中短暂出现
function addBowl(scene) {
  const bowl = new THREE.Group();
  bowl.position.set(BOWL_START.x, 0, BOWL_START.z);
  scene.add(bowl);
  const bowlMaterial = matte(0x427f79);
  const rim = placeMesh(bowl, new THREE.TorusGeometry(0.36, 0.044, 12, 44), bowlMaterial, 0, 0.18, 0);
  rim.rotation.x = Math.PI / 2;
  const shape = [new THREE.Vector2(0.01, 0.045), new THREE.Vector2(0.26, 0.045), new THREE.Vector2(0.36, 0.18), new THREE.Vector2(0.31, 0.18), new THREE.Vector2(0.23, 0.091), new THREE.Vector2(0.01, 0.091)];
  placeMesh(bowl, new THREE.LatheGeometry(shape, 44), bowlMaterial, 0, 0, 0);
  const food = new THREE.Group();
  bowl.add(food);
  const grain = new THREE.SphereGeometry(1, 10, 8);
  const brown = matte(0xb77541);
  for (let index = 0; index < 10; index = index + 1) {
    const angle = index * 2.4;
    const distance = 0.07 + index * 0.017;
    oval(food, grain, brown, Math.cos(angle) * distance, 0.12, Math.sin(angle) * distance, 0.061, 0.035, 0.05);
  }
  return { group: bowl, food: food };
}

// 用同一组坐标搭好矮凳和花盆，仙人掌以盆中土面为转轴
function addCactus(scene, wood, peach) {
  const stand = new THREE.Group();
  stand.position.set(2.02, 0, -1.18);
  scene.add(stand);
  placeMesh(stand, new THREE.CylinderGeometry(0.46, 0.48, 0.18, 32), wood, 0, 0.47, 0);
  for (const foot of [[-0.26, 0.19], [0.26, 0.19], [0, -0.29]]) {
    box(stand, wood, foot[0], 0.2, foot[1], 0.1, 0.42, 0.11);
  }

  // 土面略低于盆口，枝干从盆中央探出来
  placeMesh(stand, new THREE.CylinderGeometry(0.27, 0.205, 0.34, 28), peach, 0, 0.73, 0);
  const rim = placeMesh(stand, new THREE.TorusGeometry(0.255, 0.025, 10, 32), peach, 0, 0.9, 0);
  rim.rotation.x = Math.PI / 2;
  const soil = matte(0x745242);
  const soilTop = placeMesh(stand, new THREE.CircleGeometry(0.248, 32), soil, 0, 0.901, 0);
  soilTop.rotation.x = -Math.PI / 2;
  const cactus = new THREE.Group();
  cactus.position.y = 0.89;
  stand.add(cactus);
  const green = matte(0x689b69);
  const trunk = new THREE.CapsuleGeometry(0.13, 0.58, 6, 16);
  placeMesh(cactus, trunk, green, 0, 0.35, 0);
  const sphere = new THREE.SphereGeometry(1, 16, 12);
  const branches = [
    [[-0.07, 0.25, 0], [-0.21, 0.28, 0], [-0.29, 0.38, 0], [-0.29, 0.54, 0]],
    [[0.07, 0.37, 0], [0.21, 0.41, 0], [0.27, 0.51, 0], [0.27, 0.67, 0]],
  ];
  for (const branch of branches) {
    const radius = 0.08;
    cord(cactus, branch, radius, green);
    const tip = branch[branch.length - 1];
    oval(cactus, sphere, green, tip[0], tip[1], tip[2], radius, radius, radius);
  }
  return { stand: stand, cactus: cactus };
}

// 落地灯的灯罩与照明分开，点击时两者一起切换
function addLamp(scene, wood) {
  const stand = new THREE.Group();
  stand.position.set(1.12, 0, -1.96);
  scene.add(stand);
  const metal = matte(0x977047);
  const shade = new THREE.MeshStandardMaterial({ color: 0xcfa46f, roughness: 0.82, emissive: 0xffb74a });
  placeMesh(stand, new THREE.CylinderGeometry(0.24, 0.27, 0.09, 32), wood, 0, 0.01, 0);
  placeMesh(stand, new THREE.CylinderGeometry(0.033, 0.039, 1.82, 16), metal, 0, 0.96, 0);
  const cover = placeMesh(stand, new THREE.CylinderGeometry(0.2, 0.36, 0.45, 40), shade, 0, 1.98, 0);
  cover.castShadow = false;
  const glow = new THREE.PointLight(0xffa94f, LAMP_INTENSITY, 6.5);
  glow.position.set(0, 1.7, 0.14);
  stand.add(glow);
  return { stand: stand, shade: shade, glow: glow };
}

// 挂钟的分针绕表盘中心转动，不带动边框和刻度
function addWallClock(scene, wood, linen) {
  const frame = new THREE.Group();
  frame.position.set(-0.82, 2.48, -2.11);
  scene.add(frame);
  const caseShape = new THREE.CylinderGeometry(0.31, 0.31, 0.09, 40);
  const clockCase = placeMesh(frame, caseShape, wood, 0, 0, 0);
  clockCase.rotation.x = Math.PI / 2;
  placeMesh(frame, new THREE.CircleGeometry(0.27, 40), linen, 0, 0, 0.051);
  const ink = matte(0x6f4d3a);
  const mark = new THREE.SphereGeometry(0.016, 10, 8);
  for (let position = 0; position < 12; position = position + 1) {
    const angle = position * CLOCK_STEP * 5;
    placeMesh(frame, mark, ink, Math.sin(angle) * 0.225, Math.cos(angle) * 0.225, 0.059);
  }
  const hourHand = box(frame, ink, -0.052, 0.038, 0.072, 0.12, 0.024, 0.02);
  hourHand.rotation.z = -0.62;
  const minuteHand = new THREE.Group();
  minuteHand.rotation.z = -0.4;
  frame.add(minuteHand);
  box(minuteHand, ink, 0, 0.08, 0.08, 0.019, 0.19, 0.018);
  placeMesh(frame, mark, ink, 0, 0, 0.096);
  return { frame: frame, minuteHand: minuteHand };
}

// 窗框、地毯和矮凳留在宠物背后，为旋转模型提供清晰的远近参照
function furnishRoom(scene) {
  const floor = matte(0xeddbc0);
  const mint = matte(0xa4d3c5);
  const deepMint = matte(0x75a99e);
  const linen = matte(0xf4efdc);
  const peach = matte(0xf4b09a);
  const wood = matte(0xe2b480);
  const sphere = new THREE.SphereGeometry(1, 24, 16);
  const plane = placeMesh(scene, new THREE.PlaneGeometry(200, 200), floor, 0, -0.07, 0);
  plane.rotation.x = -Math.PI / 2;
  const wall = placeMesh(scene, new THREE.PlaneGeometry(40, 10), matte(ROOM_COLOR), 0, 4.93, -2.27);
  wall.castShadow = false;
  box(scene, linen, 0, 0.015, -2.22, 40, 0.17, 0.09);
  const rug = placeMesh(scene, new THREE.CylinderGeometry(2.48, 2.53, 0.075, 80), mint, 0, -0.025, 0);
  const rugEdge = placeMesh(scene, new THREE.TorusGeometry(2.22, 0.018, 8, 80), linen, 0, 0.02, 0);
  rugEdge.rotation.x = Math.PI / 2;

  const windowFrame = new THREE.Group();
  windowFrame.position.set(-2.35, 1.42, -2.1);
  scene.add(windowFrame);
  box(windowFrame, deepMint, 0, 0, 0, 1.75, 2.05, 0.19);
  box(windowFrame, matte(0xf9e8b7), 0, 0, 0.105, 1.5, 1.8, 0.035);
  box(windowFrame, deepMint, 0, 0, 0.14, 0.065, 1.8, 0.04);
  box(windowFrame, deepMint, 0, -0.12, 0.14, 1.51, 0.065, 0.04);
  box(windowFrame, linen, 0, -1.05, 0.23, 1.97, 0.08, 0.44);

  const planter = addCactus(scene, wood, peach);
  const lamp = addLamp(scene, wood);
  const wallClock = addWallClock(scene, wood, linen);

  const toy = new THREE.Group();
  toy.position.set(TOY_START.x, TOY_RADIUS, TOY_START.z);
  scene.add(toy);
  oval(toy, sphere, peach, 0, 0, 0, TOY_RADIUS, TOY_RADIUS, TOY_RADIUS);
  cord(toy, [[-0.17, 0.08, 0.08], [-0.08, 0.06, 0.18], [0.06, 0, 0.19], [0.15, -0.09, 0.1]], 0.016, linen);
  cord(toy, [[-0.12, 0.14, 0.08], [-0.04, 0.117, 0.15], [0.1, 0.079, 0.153], [0.177, 0, 0.09]], 0.013, linen);
  const bowl = addBowl(scene);
  const sunshine = new THREE.PointLight(0xffbd61, 0, 5);
  sunshine.position.set(-2, 1.8, -1.35);
  scene.add(sunshine);
  const star = makeWindowStar(windowFrame);
  const tracks = makePawTracks(scene);
  const spots = [
    { name: "window", object: windowFrame },
    { name: "plant", object: planter.stand },
    { name: "lamp", object: lamp.stand },
    { name: "clock", object: wallClock.frame },
    { name: "bowl", object: bowl.group },
    { name: "toy", object: toy },
    { name: "rug", object: rug },
    { name: "rug", object: rugEdge },
    { name: "floor", object: plane },
  ];
  return {
    toy: toy, bowl: bowl, plant: planter.cactus, lamp: lamp, wallClock: wallClock,
    spots: spots, sunshine: sunshine, star: star, tracks: tracks,
  };
}

// 在地面放几枚很浅的爪印，探索时才短暂出现，也不参与点击判定
function makePawTracks(scene) {
  const group = new THREE.Group();
  const shape = new THREE.SphereGeometry(1, 12, 8);
  const material = new THREE.MeshBasicMaterial({ color: 0x6e9f8f });
  scene.add(group);
  group.visible = false;
  const centers = [[0, 0], [0.19, -0.25], [0.02, -0.49]];
  for (const center of centers) {
    const horizontal = center[0];
    const depth = center[1];
    const pad = oval(group, shape, material, horizontal, 0, depth, 0.078, 0.006, 0.061);
    pad.castShadow = false;
    for (const offset of [-0.061, 0, 0.061]) {
      const toe = oval(group, shape, material, horizontal + offset, 0, depth - 0.095, 0.026, 0.006, 0.029);
      toe.castShadow = false;
    }
  }
  return group;
}

// 第三次敲窗才会显出玻璃上的小星星，平时将它藏在窗框里
function makeWindowStar(windowFrame) {
  const outline = new THREE.Shape();
  for (let point = 0; point < 10; point = point + 1) {
    const angle = Math.PI / 2 + point * Math.PI / 5;
    let radius = 0.19;
    if (point % 2 === 1) {
      radius = 0.083;
    }
    const horizontal = Math.cos(angle) * radius;
    const vertical = Math.sin(angle) * radius;
    if (point === 0) {
      outline.moveTo(horizontal, vertical);
    } else {
      outline.lineTo(horizontal, vertical);
    }
  }
  outline.closePath();
  const material = new THREE.MeshBasicMaterial({ color: 0xf19d28, side: THREE.DoubleSide });
  const star = placeMesh(windowFrame, new THREE.ShapeGeometry(outline), material, 0.38, 0.46, 0.178);
  star.castShadow = false;
  star.visible = false;
  return star;
}

// 暖色主光照亮桌宠，保留少量柔和的补光看清背面
function lightRoom(scene) {
  scene.add(new THREE.HemisphereLight(0xffe3c1, 0xc5aa93, 1.74));
  const sun = new THREE.DirectionalLight(0xffd294, 2.05);
  sun.position.set(-3.2, 6, 3);
  sun.castShadow = true;
  sun.shadow.mapSize.set(1024, 1024);
  sun.shadow.camera.left = -5;
  sun.shadow.camera.right = 5;
  sun.shadow.camera.top = 5;
  sun.shadow.camera.bottom = -5;
  sun.shadow.normalBias = 0.027;
  scene.add(sun);
}

// 窄屏也保留摆件所需的宽度，窗户和仙人掌不会被画布裁开
function fitCamera(camera, width, height) {
  const aspect = width / height;
  const halfHeight = Math.max(1.76, SCENE_HALF_WIDTH / aspect);
  const halfWidth = halfHeight * aspect;
  camera.left = -halfWidth;
  camera.right = halfWidth;
  camera.top = halfHeight;
  camera.bottom = -halfHeight;
  camera.updateProjectionMatrix();
}

// 用集合归并复用的材质和几何体，离开页面时只释放每份资源一次
function disposeScene(scene) {
  const materials = new Set();
  const geometries = new Set();
  // 灯光没有网格，阴影贴图由灯光自己释放
  function collect(object) {
    if (object.isMesh) {
      if (Array.isArray(object.material)) {
        for (const material of object.material) {
          materials.add(material);
        }
      } else {
        materials.add(object.material);
      }
      geometries.add(object.geometry);
    } else if (object.isLight && object.shadow) {
      object.shadow.dispose();
    }
  }
  scene.traverse(collect);
  for (const material of materials) {
    if (material.map) {
      material.map.dispose();
    }
    material.dispose();
  }
  for (const geometry of geometries) {
    geometry.dispose();
  }
}

// 建立小屋场景，区分桌宠和陈设，拖动不提交任何照顾动作
export function createPetScene(host, onPet, onExplore, onActivity) {
  const renderer = new THREE.WebGLRenderer({ antialias: true });
  const canvas = renderer.domElement;
  canvas.setAttribute("aria-hidden", "true");
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, MAX_PIXEL_RATIO));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(ROOM_COLOR);
  scene.fog = new THREE.Fog(ROOM_COLOR, 10, 24);
  const camera = new THREE.OrthographicCamera(-2, 2, 2, -2, 0.1, 40);
  camera.position.set(0, 3.2, CAMERA_DISTANCE);
  camera.lookAt(0, 1.13, 0);
  lightRoom(scene);
  const props = furnishRoom(scene);
  const pet = createPetModel();
  const motion = createPetMotion(pet);
  const floor = createPetFloor();
  scene.add(pet.root);
  const reduced = window.matchMedia("(prefers-reduced-motion: reduce)");
  const picker = createPetPicker(camera, pet.hitParts, props.spots);
  const rollAxis = new THREE.Vector3();
  const direction = new THREE.Vector3();
  const toyRotation = new THREE.Quaternion();
  let angle = -0.2;
  let level = 1;
  let reaction = "";
  let reactionStart = 0;
  let lastFrame = 0;
  let shownAngle = angle;
  let targetLook = 0;
  let shownLook = 0;
  let drag = null;
  let visits = 0;
  let lampOn = true;
  let playTarget = null;
  let propFocus = null;
  let headTurn = 0;
  let propFocusUntil = 0;
  let plantStarted = -Infinity;
  let windowStarted = -Infinity;
  let toyTapped = -Infinity;
  let tracksStarted = -Infinity;
  let returning = null;
  let pendingAction = "";
  let ready = true;
  const movedProps = new Set();
  props.lamp.shade.emissiveIntensity = 0.4;
  host.appendChild(canvas);

  // 从画布实际尺寸更新取景，不依赖浏览器窗口的宽高
  function resize() {
    const width = Math.max(1, host.clientWidth);
    const height = Math.max(1, host.clientHeight);
    fitCamera(camera, width, height);
    renderer.setSize(width, height, false);
    render(performance.now());
  }

  // 抬爪时才推动球，球的位置和滚动角度来自地面物理计算
  function updateToy(delta, sinceAction, reducedMotion) {
    if (playTarget && !playTarget.struck && sinceAction >= PLAY_CONTACT) {
      playTarget.struck = true;
      if (!reducedMotion) {
        direction.set(playTarget.x, 0, playTarget.z);
        direction.normalize();
        floor.push(direction.x, direction.z);
      }
    }
    if (playTarget && sinceAction >= PLAY_DURATION) {
      playTarget = null;
    }
    if (reducedMotion) {
      floor.stop();
      return;
    }
    const position = floor.advance(delta * 1000);
    showToy(position);
    if (Math.hypot(position.x - TOY_START.x, position.z - TOY_START.z) > 0.09) {
      movedProps.add("toy");
    }
  }

  // 用实际滚过的距离更新球纹的转角，拖动和归位时也保持一致
  function showToy(position) {
    const changeX = position.x - props.toy.position.x;
    const changeZ = position.z - props.toy.position.z;
    const distance = Math.hypot(changeX, changeZ);
    if (distance > 0) {
      rollAxis.set(changeZ / distance, 0, -changeX / distance);
      toyRotation.setFromAxisAngle(rollAxis, distance / TOY_RADIUS);
      props.toy.quaternion.premultiply(toyRotation);
      props.toy.position.set(position.x, TOY_RADIUS, position.z);
    }
  }

  // 小猫靠近正在使用的物件，物件留在原位，动作结束后回到垫子中央
  function placePet(sinceAction, now) {
    pet.root.position.set(0, 0, 0);
    pet.root.rotation.y = shownAngle;
    if (reduced.matches) {
      return shownLook;
    }
    const target = props.bowl.group.position;
    let weight = 0;
    const reach = 0.9;
    if (reaction === "play") {
      return 0;
    } else if (reaction === "feed" && sinceAction < 1.14) {
      weight = approachWeight(sinceAction, 0.3, 1.14, 0.31);
    } else if (reaction === "rest") {
      pet.root.rotation.y = shownAngle;
      return 0;
    } else if (propFocus && now < propFocusUntil) {
      const look = targetHeading(propFocus, pet.root.position, shownAngle);
      return THREE.MathUtils.clamp(look, -0.57, 0.57);
    }
    if (weight > 0) {
      const distance = Math.hypot(target.x, target.z);
      if (distance > reach) {
        const travel = (distance - reach) * weight;
        pet.root.position.set(target.x / distance * travel, 0, target.z / distance * travel);
      }
      const turn = targetHeading(target, pet.root.position, shownAngle);
      pet.root.rotation.y = shownAngle + turn * weight;
    }
    return shownLook * (1 - weight);
  }

  // 逗玩沿用四足步态，直到爪子碰球才由物理引擎推动玩具
  function posePlay(elapsed, size) {
    if (!playTarget || reaction !== "play" || reduced.matches) {
      return false;
    }
    const pose = readPlayPose(elapsed, playTarget, size);
    pet.root.position.set(pose.x, 0, pose.z);
    const turn = targetHeading(playTarget, pet.root.position, shownAngle);
    pet.root.rotation.y = shownAngle + turn * pose.stand;
    pet.body.scale.y = 1;
    motion.carry(pose, false);
    return true;
  }

  // 松手后依次归位，先等照顾动作或睡觉结束，再走向被挪动的物件
  function updateReturn(now, delta) {
    if (reaction) {
      return false;
    }
    if (!returning && !drag) {
      for (const name of movedProps) {
        if (name === "toy" && floor.toyIsRolling()) {
          continue;
        }
        movedProps.delete(name);
        const object = propObject(name);
        returning = makePropReturn(name, object.position, now);
        if (returning) {
          returning.heading = shownAngle;
          break;
        }
      }
    }
    if (!returning) {
      return false;
    }
    if (reduced.matches) {
      placeProp(returning.name, returning.end);
      returning = null;
      return false;
    }
    const pose = readPropReturn(returning, now);
    if (!pose) {
      return false;
    }
    if (!returning.announced) {
      returning.announced = true;
      onExplore("tidy-" + returning.name);
    }
    const name = returning.name;
    placeProp(name, pose.item);
    pet.root.position.copy(pose.cat);
    const current = returning.heading;
    const angleChange = targetHeading(pose.face, pose.cat, current);
    returning.heading = THREE.MathUtils.damp(current, current + angleChange, LOOK_DAMPING, delta);
    pet.root.rotation.y = returning.heading;
    pet.head.rotation.y = 0;
    pet.body.scale.y = 1;
    motion.carry(pose, name === "bowl");
    if (pose.done) {
      motion.clearCarry();
      const difference = returning.heading - angle;
      shownAngle = angle + Math.atan2(Math.sin(difference), Math.cos(difference));
      returning = null;
    }
    return true;
  }

  // 移动物件后更新对应的碰撞体，拖动的球不会继续被惯性推走
  function placeProp(name, position) {
    if (name === "toy") {
      floor.placeToy(position);
      showToy(position);
    } else {
      props.bowl.group.position.set(position.x, position.y, position.z);
      floor.placeBowl(position);
    }
  }

  // 按可移动物件名称找到场景节点
  function propObject(name) {
    if (name === "toy") {
      return props.toy;
    }
    return props.bowl.group;
  }

  // 保留独立的视角、注视和关节动作，用户可以随时打断前一个动作
  function render(now) {
    const age = now / 1000;
    let frameTime = 0;
    if (lastFrame > 0) {
      frameTime = Math.max(0, (now - lastFrame) / 1000);
    }
    const delta = Math.min(0.05, frameTime);
    lastFrame = now;
    const sinceAction = Math.max(0, (now - reactionStart) / 1000);
    if (reaction && sinceAction >= ACTION_DURATION[reaction]) {
      const finished = reaction;
      reaction = "";
      if (finished === "rest") {
        onExplore("wake");
      }
    }
    let idle = 0;
    if (!reduced.matches) {
      idle = Math.sin(age * 2.1);
      shownAngle = THREE.MathUtils.damp(shownAngle, angle, LOOK_DAMPING, delta);
      shownLook = THREE.MathUtils.damp(shownLook, targetLook, LOOK_DAMPING, delta);
    } else {
      shownAngle = angle;
      shownLook = 0;
    }
    const size = 0.93 + (level - 1) * 0.036;
    pet.root.scale.setScalar(size);
    const focus = placePet(sinceAction, now);
    headTurn = THREE.MathUtils.damp(headTurn, focus, LOOK_DAMPING, delta);
    pet.head.rotation.y = headTurn;
    pet.body.scale.y = 1 + 0.009 * idle;
    pet.tail.rotation.z = 0.075 * idle;
    if (reaction === "rest") {
      pet.body.scale.y = 1;
      pet.tail.rotation.z = 0;
    }
    props.bowl.food.visible = reaction === "feed" && sinceAction < 0.84;
    motion.update(frameTime, age, reduced.matches);
    const tidying = updateReturn(now, delta);
    const playing = posePlay(sinceAction, size);
    if (!tidying && !playing) {
      motion.clearCarry();
    }
    motion.syncTail();
    floor.placeCat(pet.root.position.x, pet.root.position.z);
    if (!tidying && !(drag && drag.holding)) {
      updateToy(delta, sinceAction, reduced.matches);
    }
    const plantAge = (now - plantStarted) / 1000;
    props.plant.rotation.z = 0;
    if (plantAge < 0.85 && !reduced.matches) {
      props.plant.rotation.z = Math.sin(plantAge * 16) * (1 - plantAge / 0.85) * 0.09;
    }
    const windowAge = (now - windowStarted) / 1000;
    props.sunshine.intensity = 0;
    if (windowAge < 1.5 && !reduced.matches) {
      props.sunshine.intensity = 7 * (1 - windowAge / 1.5);
    }
    props.tracks.visible = now - tracksStarted < PRINT_DURATION * 1000;
    renderer.render(scene, camera);
    if (pendingAction && canReact()) {
      const next = pendingAction;
      pendingAction = "";
      react(next);
    }
    const available = canReact();
    if (ready !== available) {
      ready = available;
      onActivity(ready);
    }
  }

  // 按下可移动物件时启动长按计时，点一下仍保留原本的探索动作
  function startDrag(event) {
    if (!event.isPrimary || event.button !== 0 || drag) {
      return;
    }
    const hit = picker.read(event, canvas);
    const movable = hit.name === "toy" || hit.name === "bowl";
    drag = {
      id: event.pointerId, x: event.clientX, y: event.clientY, angle: angle, hit: hit,
      moved: false, holding: false, timer: 0, offsetX: 0, offsetZ: 0,
    };
    if (movable && event.pointerType === "mouse" && !returning && !playTarget && reaction !== "feed") {
      drag.timer = window.setTimeout(grabProp, HOLD_MS);
    }
    canvas.setPointerCapture(event.pointerId);
  }

  // 保留抓取位置的偏移，物件不会在长按成功的一刻跳到鼠标中心
  function grabProp() {
    if (!drag || drag.moved) {
      return;
    }
    const object = propObject(drag.hit.name);
    const start = { clientX: drag.x, clientY: drag.y };
    const point = picker.readDragPoint(start, canvas, object.position.y);
    if (!point) {
      return;
    }
    drag.holding = true;
    drag.offsetX = object.position.x - point.x;
    drag.offsetZ = object.position.z - point.z;
    floor.stop();
    canvas.classList.add("pet-scene-holding");
  }

  // 沿地面平移抓住的物件，越过边界或另一个物件时留在上个可放位置
  function moveProp(event) {
    const name = drag.hit.name;
    const object = propObject(name);
    const point = picker.readDragPoint(event, canvas, object.position.y);
    if (!point) {
      return;
    }
    let other = props.bowl.group.position;
    if (name === "bowl") {
      other = props.toy.position;
    }
    point.x = point.x + drag.offsetX;
    point.z = point.z + drag.offsetZ;
    const position = dragPropPosition(name, point, other, pet.root.position);
    if (position) {
      position.y = object.position.y;
      placeProp(name, position);
      drag.moved = true;
    }
  }

  // 无需拖动也能把物件挪到旁边，键盘和触屏共用相同的归位动作
  function shiftProp(name) {
    if (!canReact()) {
      return;
    }
    let other = props.bowl.group.position;
    let point = { x: 2.07, z: 1.44 };
    if (name === "bowl") {
      other = props.toy.position;
      point = { x: -2.08, z: 1.46 };
    }
    const position = dragPropPosition(name, point, other, pet.root.position);
    if (position) {
      position.y = 0;
      placeProp(name, position);
      movedProps.add(name);
    }
  }

  // 拖动时立即更新视角，单击仍留给桌宠互动
  function moveDrag(event) {
    if (!drag || event.pointerId !== drag.id) {
      if (event.pointerType === "mouse") {
        const area = canvas.getBoundingClientRect();
        const centered = (event.clientX - area.left) / area.width - 0.5;
        targetLook = THREE.MathUtils.clamp(centered * 0.46, -0.2, 0.2);
        const hit = picker.read(event, canvas);
        canvas.classList.toggle("pet-scene-target", hit.name !== "floor");
        canvas.classList.toggle("pet-scene-grab", hit.name === "toy" || hit.name === "bowl");
      }
      return;
    }
    if (drag.holding) {
      moveProp(event);
      return;
    }
    const changeX = event.clientX - drag.x;
    const changeY = event.clientY - drag.y;
    if (Math.hypot(changeX, changeY) >= TAP_DISTANCE) {
      drag.moved = true;
      window.clearTimeout(drag.timer);
    }
    if (drag.moved) {
      angle = drag.angle + changeX * 0.012;
    }
  }

  // 使用按下时选中的物件，避免移动中的球或小猫在松手前抢走这次点按
  function finishDrag(event) {
    if (!drag || event.pointerId !== drag.id) {
      return;
    }
    const pressed = drag;
    cancelDrag();
    if (pressed.holding) {
      return;
    }
    const distance = Math.hypot(event.clientX - pressed.x, event.clientY - pressed.y);
    if (pressed.moved || distance >= TAP_DISTANCE) {
      return;
    }
    if (pressed.hit.name === "pet") {
      onPet();
      return;
    }
    explore(pressed.hit.name, pressed.hit.point);
  }

  // 陈设只播放场景反馈，点击背景不会改变账户资产或成长值
  function explore(name, point) {
    const now = performance.now();
    for (const spot of props.spots) {
      if (spot.name === name && name !== "floor" && name !== "rug") {
        propFocus = spot.object.position;
        propFocusUntil = now + PROP_REACTION_TIME * 1000;
        break;
      }
    }
    if (name === "toy" && !reduced.matches && canReact() && now - toyTapped >= TAP_FEEDBACK_MS) {
      toyTapped = now;
      direction.set(props.toy.position.x, 0, props.toy.position.z);
      direction.normalize();
      floor.push(-direction.x * 0.58, -direction.z * 0.58);
    }
    if (name === "plant" && now - plantStarted >= 850) {
      plantStarted = now;
    }
    if (name === "lamp") {
      lampOn = !lampOn;
      props.lamp.glow.visible = lampOn;
      props.lamp.shade.emissiveIntensity = 0;
      if (lampOn) {
        props.lamp.shade.emissiveIntensity = 0.4;
      }
      if (reaction !== "rest") {
        if (lampOn) {
          onExplore("lamp-lit");
        } else {
          onExplore("lamp-dark");
        }
      }
      return;
    }
    if (name === "clock") {
      const hand = props.wallClock.minuteHand;
      hand.rotation.z = hand.rotation.z - CLOCK_STEP;
    }
    if (name === "floor") {
      tracksStarted = now;
      if (point) {
        props.tracks.position.set(point.x, point.y + 0.012, point.z);
      } else {
        props.tracks.position.set(0.3, 0.02, 1.55);
      }
    }
    if (name === "window") {
      if (now - windowStarted >= 1500) {
        windowStarted = now;
      }
      visits = visits + 1;
      if (visits === 3) {
        props.star.visible = true;
        if (reaction !== "rest") {
          onExplore("star");
        }
        return;
      }
    }
    if (reaction !== "rest") {
      onExplore(name);
    }
  }

  // 浏览器取消手势后清除拖动状态
  function cancelDrag() {
    if (!drag) {
      return;
    }
    window.clearTimeout(drag.timer);
    if (drag.holding && drag.moved) {
      movedProps.add(drag.hit.name);
    }
    canvas.classList.remove("pet-scene-holding");
    drag = null;
  }

  // 光标离开小屋后，让小橘慢慢转回正前方
  function clearLook() {
    targetLook = 0;
    canvas.classList.remove("pet-scene-target");
    canvas.classList.remove("pet-scene-grab");
  }

  // 当前动作结束前拒绝重播，也不打断拖动、搬运或睡眠
  function canReact() {
    return !reaction && !returning && !(drag && drag.holding);
  }

  // 让照顾动作完整播放一次，连续点击不会让小猫跳回第一帧
  function react(action) {
    if (!canReact()) {
      if (action !== "pet" && reaction !== "rest") {
        pendingAction = action;
      }
      return false;
    }
    reaction = action;
    reactionStart = performance.now();
    playTarget = null;
    if (action === "play") {
      floor.stop();
      playTarget = { x: props.toy.position.x, z: props.toy.position.z, struck: false };
    }
    motion.play(action, reduced.matches);
    if (action === "rest") {
      onExplore("sleep");
    }
    return true;
  }

  // 成长等级改变模型体型，所有页面状态仍由后端提供
  function setLevel(value) {
    level = value;
  }

  // 视角按钮提供不依赖拖动的等价操作
  function rotate(amount) {
    angle = angle + amount;
  }

  // 回到进入小屋时的视角
  function resetView() {
    angle = -0.2;
    clearLook();
  }

  const observer = new ResizeObserver(resize);
  observer.observe(host);
  canvas.addEventListener("pointerdown", startDrag);
  canvas.addEventListener("pointermove", moveDrag);
  canvas.addEventListener("pointerup", finishDrag);
  canvas.addEventListener("pointercancel", cancelDrag);
  canvas.addEventListener("lostpointercapture", cancelDrag);
  canvas.addEventListener("pointerleave", clearLook);
  resize();
  renderer.setAnimationLoop(render);

  // 退出小屋时停止绘制，移除画布并释放占用的图形资源
  function dispose() {
    renderer.setAnimationLoop(null);
    cancelDrag();
    observer.disconnect();
    motion.dispose();
    canvas.remove();
    disposeScene(scene);
    renderer.dispose();
  }

  return { canReact: canReact, react: react, explore: explore, shiftProp: shiftProp, setLevel: setLevel, rotate: rotate, resetView: resetView, dispose: dispose };
}

// 在起步和收回时放缓速度，让身体经过目标姿态而不是瞬移
function approachWeight(elapsed, approach, duration, retreat) {
  const progress = Math.min(elapsed / approach, (duration - elapsed) / retreat);
  return THREE.MathUtils.smoothstep(progress, 0, 1);
}

// 计算当前朝向到物件的最短转角，绕过半圈时也不会突然折返
function targetHeading(target, origin, current) {
  const bearing = Math.atan2(target.x - origin.x, target.z - origin.z);
  const difference = bearing - current;
  return Math.atan2(Math.sin(difference), Math.cos(difference));
}
