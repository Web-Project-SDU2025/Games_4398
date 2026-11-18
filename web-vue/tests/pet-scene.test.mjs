import test from "node:test";
import assert from "node:assert/strict";
import * as THREE from "three";
import { BOWL_START, createPetFloor, TOY_START } from "../src/components/pet/pet-floor.js";
import { createPetPicker } from "../src/components/pet/pet-picker.js";
import { createPetMotion } from "../src/components/pet/pet-motion.js";
import { PLAY_CONTACT, PLAY_DURATION, readPlayPose } from "../src/components/pet/pet-play.js";
import { REST_DURATION } from "../src/components/pet/pet-rest.js";
import { createPetModel } from "../src/components/pet/pet-model.js";
import { dragPropPosition, makePropReturn, readPropReturn } from "../src/components/pet/pet-props.js";

// 小猫先过去，再叼着碗移动，最后把碗准确落回原位
test("归位过程依次靠近、抬起和放下食碗", function testBowlReturn() {
  const start = { x: -2.05, z: 1.33 };
  const route = makePropReturn("bowl", start, 1000);
  assert.equal(readPropReturn(route, 1000), null);
  const standing = readPropReturn(route, route.startedAt + 150);
  assert.equal(standing.cat.length(), 0);
  assert.ok(standing.stand > 0 && standing.stand < 1);
  const approachingAt = route.startedAt + route.arrival * 500;
  const approaching = readPropReturn(route, approachingAt);
  assert.equal(approaching.item.x, start.x);
  assert.ok(approaching.cat.length() > 0);
  assert.equal(approaching.stand, 1);
  assert.ok(approaching.distance > 0 && approaching.stride > 0);
  const carryingAt = route.startedAt + (route.delivery + route.pickup) * 500;
  const carrying = readPropReturn(route, carryingAt);
  assert.ok(carrying.item.y > 0);
  assert.ok(carrying.item.x > start.x && carrying.item.x < BOWL_START.x);
  const finished = readPropReturn(route, route.startedAt + (route.finished + 0.01) * 1000);
  assert.equal(finished.item.x, BOWL_START.x);
  assert.equal(finished.item.z, BOWL_START.z);
  assert.equal(finished.item.y, 0);
  assert.equal(finished.cat.length(), 0);
  assert.equal(finished.stand, 0);
  assert.equal(finished.done, true);
});

// 拖动时不能占住猫或另一件物品，原位的小偏差不触发归位
test("拖动保持边界和物件间距", function testPropSpacing() {
  const cat = { x: 0, z: 0 };
  assert.equal(dragPropPosition("toy", cat, BOWL_START, cat), null);
  assert.equal(dragPropPosition("toy", BOWL_START, BOWL_START, cat), null);
  const bounded = dragPropPosition("toy", { x: 12, z: 8 }, BOWL_START, cat);
  assert.ok(bounded.x < 2.53 && bounded.z < 1.94);
  assert.equal(makePropReturn("toy", TOY_START, 0), null);
});

// 只替换画笔接口，关节、几何体和动画继续使用真实模型
function makeTestPet() {
  // 测试不绘制额纹，画布只给模型生成贴图所需的接口
  function ignorePaint() {
  }
  // 返回不依赖浏览器的画笔，几何体和动画继续使用真实实现
  function mockPainter() {
    return { beginPath: ignorePaint, moveTo: ignorePaint, lineTo: ignorePaint, stroke: ignorePaint };
  }
  // 模型只创建一块画布，测试结束后恢复环境
  function mockCanvas() {
    return { getContext: mockPainter };
  }
  const originalDocument = globalThis.document;
  globalThis.document = { createElement: mockCanvas };
  try {
    return createPetModel();
  } finally {
    globalThis.document = originalDocument;
  }
}

// 用真实关节验证伏低时爪掌会弯、尾巴会变形，起身后恢复坐姿
test("小橙侧躺约八秒且连点不会重播", function testRestMotion() {
  const pet = makeTestPet();
  const left = pet.root.getObjectByName("leftElbow");
  const right = pet.root.getObjectByName("rightElbow");
  const knee = pet.root.getObjectByName("rightKnee");
  const tail = pet.root.getObjectByName("tailSkin");
  const tip = pet.root.getObjectByName("tailTip");
  const sittingTip = tip.position.clone();
  const motion = createPetMotion(pet);
  motion.play("rest", false);
  motion.update(0.34, 0.34, false);
  assert.ok(left.rotation.x > right.rotation.x);
  motion.update(0.96, 1.3, false);
  motion.syncTail();
  assert.equal(pet.body.rotation.z, 0);
  assert.ok(pet.head.position.y < 0.7);
  assert.ok(knee.rotation.x > 0.8);
  assert.ok(tail.morphTargetInfluences[0] > 0.99);
  assert.ok(tip.position.y < sittingTip.y);
  assert.ok(pet.tail.position.y + tip.position.y >= 0.105);
  assert.equal(pet.eyes[0].eye.visible, false);
  motion.play("rest", false);
  motion.update(5.6, 6.9, false);
  assert.ok(pet.head.position.y < 0.7);
  motion.update(0.8, 7.7, false);
  assert.ok(pet.head.position.y > 1.15);
  assert.ok(left.rotation.x > 0.15);
  motion.update(REST_DURATION - 7.7 + 0.02, 8.27, false);
  motion.syncTail();
  assert.equal(pet.head.position.y, 1.65);
  assert.equal(left.rotation.x, 0);
  assert.equal(knee.rotation.x, 0);
  assert.deepEqual(tip.position, sittingTip);
  assert.equal(pet.eyes[0].eye.visible, true);
  motion.dispose();
});

// 逐帧测量真实头部网格的最低点，包括脸颊、耳朵和胡须
test("睡眠过渡时整个头部都留在地垫上方", function testHeadClearance() {
  const rugTop = 0.038;
  const pet = makeTestPet();
  const motion = createPetMotion(pet);
  const vertex = new THREE.Vector3();
  let lowest = Infinity;
  // 只取实际网格顶点，包围盒角点不代表模型的表面
  function checkMesh(node) {
    if (!node.isMesh) {
      return;
    }
    const vertices = node.geometry.getAttribute("position");
    for (let index = 0; index < vertices.count; index = index + 1) {
      vertex.fromBufferAttribute(vertices, index);
      vertex.applyMatrix4(node.matrixWorld);
      lowest = Math.min(lowest, vertex.y);
    }
  }
  pet.root.scale.setScalar(0.93);
  motion.play("rest", false);
  for (let frame = 0; frame <= 500; frame = frame + 1) {
    motion.update(1 / 60, frame / 60, false);
    pet.root.updateMatrixWorld(true);
    pet.head.traverse(checkMesh);
  }
  assert.ok(lowest > rugTop, "头部最低点：" + lowest);
  motion.dispose();
});

// 还没站稳时身体不平移，站稳后才迈步，拨球时腿不再迈动
test("逗玩先站稳，再走近停步拨球", function testPlayApproach() {
  const rising = readPlayPose(0.13, TOY_START, 0.93);
  assert.equal(rising.x, 0);
  assert.ok(rising.stand > 0 && rising.stand < 1);
  const stepping = readPlayPose(0.58, TOY_START, 0.93);
  assert.equal(stepping.stand, 1);
  assert.ok(stepping.distance > 0 && stepping.stride > 0);
  const contact = readPlayPose(PLAY_CONTACT, TOY_START, 0.93);
  assert.equal(contact.reach, 1);
  assert.equal(contact.stride, 0);
  const sitting = readPlayPose(PLAY_DURATION, TOY_START, 0.93);
  assert.equal(sitting.stand, 0);
  assert.equal(sitting.distance, 0);
});

// 每次迈步都有脚掌贴着垫子，前后腿交替抬起且不会拖地
test("四足步态先撑起胸腹，再交替迈前后腿", function testWalkMotion() {
  const pet = makeTestPet();
  const motion = createPetMotion(pet);
  const chest = pet.root.getObjectByName("chest");
  const feet = ["leftWrist", "rightWrist", "leftHindFoot", "rightHindFoot"];
  const lifted = new Set();
  const position = new THREE.Vector3();
  for (let step = 0; step < 32; step = step + 1) {
    motion.carry({ stand: 1, distance: step * 0.0275, stride: 1, reach: 0 }, false);
    let planted = 0;
    for (const name of feet) {
      const foot = pet.root.getObjectByName(name);
      foot.getWorldPosition(position);
      assert.ok(position.y >= 0.11);
      if (position.y < 0.14) {
        planted = planted + 1;
      } else if (position.y > 0.2) {
        lifted.add(name);
      }
    }
    assert.ok(planted >= 2);
  }
  assert.equal(lifted.size, 4);
  assert.ok(chest.position.y > 0.6);
  motion.syncTail();
  const tailSkin = pet.root.getObjectByName("tailSkin");
  const tailTip = pet.root.getObjectByName("tailTip");
  assert.equal(tailSkin.morphTargetInfluences[1], 1);
  assert.ok(pet.tail.position.z < -0.7);
  assert.ok(tailTip.position.distanceTo(tailTip.userData.walking) < 0.0001);
  motion.clearCarry();
  assert.equal(tailSkin.morphTargetInfluences[1], 0);
  assert.equal(chest.position.y, 0.56);
  assert.equal(pet.head.position.y, 1.65);
  motion.dispose();
});

// 用当前躯干的网格坐标检查连接点，尾根要在后侧表面以内而不是浮在外面
function assertTailAttached(pet) {
  assert.equal(pet.tail.position.x, 0);
  const root = pet.tail.getWorldPosition(new THREE.Vector3());
  const trunk = pet.hitParts[1];
  trunk.worldToLocal(root);
  assert.ok(root.z < -0.8);
  assert.ok(root.length() < 1);
}

// 坐姿、四足过渡和照顾动作都共用臀部中线上的连接点
test("尾根全程连在臀部正后方", function testTailRoot() {
  const pet = makeTestPet();
  const motion = createPetMotion(pet);
  assertTailAttached(pet);
  for (const stand of [0, 0.25, 0.5, 0.75, 1, 0.5, 0]) {
    motion.carry({ stand: stand, distance: stand, stride: stand, reach: 0 }, false);
    motion.syncTail();
    assertTailAttached(pet);
  }
  motion.clearCarry();
  for (const action of ["pet", "feed", "rest"]) {
    motion.play(action, false);
    for (let frame = 0; frame < 102; frame = frame + 1) {
      motion.update(0.085, frame * 0.085, false);
      motion.syncTail();
      assertTailAttached(pet);
    }
  }
  motion.dispose();
});

// 球撞到猫和粮碗时要反弹，松手后由阻力减速停下
test("玩具球碰到猫和食碗后反弹并停下", function testToyBounce() {
  const catFloor = createPetFloor();
  catFloor.placeCat(0.5, TOY_START.z);
  catFloor.push(-1, 0);
  const approach = catFloor.advance(200);
  assert.ok(approach.x > 1.08);
  const rebound = catFloor.advance(300);
  assert.ok(rebound.x > approach.x);

  const bowlFloor = createPetFloor();
  bowlFloor.push(-3.4, 0);
  let furthest = TOY_START.x;
  let point = bowlFloor.advance(0);
  for (let step = 0; step < 120; step = step + 1) {
    point = bowlFloor.advance(1000 / 120);
    furthest = Math.min(furthest, point.x);
  }
  assert.ok(furthest < -0.9 && furthest > -1.1);
  assert.ok(point.x > furthest);
  for (let step = 0; step < 180; step = step + 1) {
    bowlFloor.advance(1000 / 60);
  }
  assert.deepEqual(bowlFloor.advance(100), bowlFloor.advance(100));
});

// 相同的输入在常见的两种刷新率下得到同一结果
test("玩具球的滚动与刷新率无关", function testToyFrameRate() {
  const regular = createPetFloor();
  const fast = createPetFloor();
  regular.push(1, 0.6);
  fast.push(1, 0.6);
  for (let step = 0; step < 60; step = step + 1) {
    regular.advance(1000 / 60);
    fast.advance(1000 / 120);
    fast.advance(1000 / 120);
  }
  assert.deepEqual(regular.advance(0), fast.advance(0));
});

// 用相同的屏幕像素验证前景物件、紧邻猫的物件和隐藏物件
test("射线优先选中可见物件，猫不会吞掉相邻点击", function testScenePicking() {
  const camera = new THREE.OrthographicCamera(-2, 2, 2, -2, 0.1, 12);
  camera.position.z = 5;
  camera.updateMatrixWorld();
  const shape = new THREE.BoxGeometry(0.9, 0.9, 0.1);
  const cat = new THREE.Mesh(shape);
  cat.updateMatrixWorld();
  const furniture = new THREE.Group();
  const ball = new THREE.Mesh(shape);
  furniture.add(ball);
  const spots = [{ name: "toy", object: furniture }];
  const picker = createPetPicker(camera, [cat], spots);
  const click = { clientX: 150, clientY: 130 };

  // 模拟带偏移量的画布，确保窗口坐标不会被当成画布坐标
  function readBounds() {
    return { width: 200, height: 200, left: 50, top: 30 };
  }
  const canvas = { getBoundingClientRect: readBounds };
  furniture.position.z = 1;
  furniture.updateMatrixWorld(true);
  assert.equal(picker.read(click, canvas).name, "toy");
  furniture.position.z = -0.12;
  furniture.updateMatrixWorld(true);
  assert.equal(picker.read(click, canvas).name, "toy");
  furniture.position.z = -0.8;
  furniture.updateMatrixWorld(true);
  assert.equal(picker.read(click, canvas).name, "pet");
  furniture.position.z = 1;
  furniture.visible = false;
  furniture.updateMatrixWorld(true);
  assert.equal(picker.read(click, canvas).name, "pet");
});
