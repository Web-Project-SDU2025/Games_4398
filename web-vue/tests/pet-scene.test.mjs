import test from "node:test";
import assert from "node:assert/strict";
import * as THREE from "three";
import { BOWL_RADIUS, BOWL_RIM, BOWL_START, createPetFloor, TOY_START } from "../src/components/pet/pet-floor.js";
import { createPetPicker } from "../src/components/pet/pet-picker.js";
import { createPetMotion } from "../src/components/pet/pet-motion.js";
import { PLAY_CONTACT, PLAY_DURATION, readPlayPose } from "../src/components/pet/pet-play.js";
import { REST_DURATION } from "../src/components/pet/pet-rest.js";
import { applyFeedLean, FEED_DURATION, readFeedPose } from "../src/components/pet/pet-feed.js";
import { CLOCK_TURN, createClockPreview, readClockAngles, readClockHour } from "../src/components/pet/pet-clock.js";
import { createWindowFade, readWindowView, WINDOW_NIGHT } from "../src/components/pet/pet-window.js";
import { createStarCelebration, readStarCelebration } from "../src/components/pet/pet-star-celebration.js";
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
  const lowestByFrame = [];
  for (let frame = 0; frame <= 500; frame = frame + 1) {
    lowest = Infinity;
    motion.update(1 / 60, frame / 60, false);
    pet.root.updateMatrixWorld(true);
    pet.head.traverse(checkMesh);
    lowestByFrame.push(lowest);
  }
  assert.ok(lowest > rugTop, "头部最低点：" + lowest);
  const lowestHeadHeight = Math.min.apply(null, lowestByFrame);
  assert.ok(lowestHeadHeight > rugTop, "睡眠过渡的头部最低点：" + lowestHeadHeight);
  motion.dispose();
});

// 真实饱腹值不会影响站起和靠碗路线，三口后抬头离开
test("进食路线俯身到食碗并在三口后回座", function testFeedPose() {
  const bowl = { x: -1.55, z: 0.85 };
  const standing = readFeedPose(0.15, bowl, 0.93);
  assert.ok(Math.abs(standing.x) < 0.0001);
  assert.ok(standing.stand > 0);
  const arrived = readFeedPose(1.05, bowl, 0.93);
  assert.equal(arrived.stand, 1);
  assert.ok(arrived.x < 0 || arrived.z > 0);
  const firstBite = readFeedPose(1.85, bowl, 0.93);
  assert.ok(firstBite.lean > 0.9);
  assert.equal(firstBite.foodCount, 7);
  const lastBite = readFeedPose(2.75, bowl, 0.93);
  assert.equal(lastBite.foodCount, 0);
  assert.equal(readFeedPose(FEED_DURATION + 0.1, bowl, 0.93).foodCount, 0);
  const raised = readFeedPose(3, bowl, 0.93);
  assert.ok(raised.lean < firstBite.lean);
  const seated = readFeedPose(FEED_DURATION, bowl, 0.93);
  assert.ok(Math.abs(seated.stand) < 0.0001);
});

// 对准实物模型，逐帧检查宠物网格没有进入碗沿以下的空间
test("进食时脸颊和四肢不穿进食碗", function testFeedClearance() {
  const pet = makeTestPet();
  const motion = createPetMotion(pet);
  const bowlRadius = BOWL_RADIUS + BOWL_RIM.thickness;
  const bowlTop = BOWL_RIM.height + BOWL_RIM.thickness;
  const vertex = new THREE.Vector3();
  let nearest = Infinity;

  // 用世界坐标比较实际网格顶点和食碗范围
  function checkMesh(node) {
    if (!node.isMesh || !node.visible) {
      return;
    }
    const vertices = node.geometry.getAttribute("position");
    for (let index = 0; index < vertices.count; index = index + 1) {
      node.getVertexPosition(index, vertex);
      vertex.applyMatrix4(node.matrixWorld);
      const radius = Math.hypot(vertex.x - BOWL_START.x, vertex.z - BOWL_START.z);
      if (radius < bowlRadius) {
        nearest = Math.min(nearest, vertex.y);
      }
    }
  }

  for (const size of [0.93, 1.074]) {
    pet.root.scale.setScalar(size);
    nearest = Infinity;
    for (let frame = 0; frame <= Math.ceil(FEED_DURATION * 60); frame = frame + 1) {
      const pose = readFeedPose(frame / 60, BOWL_START, size);
      pet.root.position.set(pose.x, 0, pose.z);
      pet.root.rotation.y = Math.atan2(BOWL_START.x - pose.x, BOWL_START.z - pose.z) * pose.stand;
      motion.carry(pose, true);
      applyFeedLean(pet, pose);
      motion.syncTail();
      pet.root.updateMatrixWorld(true);
      pet.root.traverseVisible(checkMesh);
    }
    assert.ok(nearest > bowlTop, "模型最低点：" + nearest + "，碗沿：" + bowlTop);
    assert.ok(nearest < bowlTop + 0.1, "低头时嘴部应靠近碗沿：" + nearest);
  }
  motion.dispose();
});

// 时针、分针和秒针跟随当前本地时间，表盘拨动后自动恢复
test("挂钟按本地时间计时，交互后回到实时刻度", function testPetClock() {
  const date = new Date(2026, 10, 20, 15, 30, 45, 0);
  const angles = readClockAngles(date, 0);
  assert.ok(Math.abs(angles.hour - (-3.5125 / 12 * CLOCK_TURN)) < 0.0001);
  assert.ok(Math.abs(angles.minute - (-30.75 / 60 * CLOCK_TURN)) < 0.0001);
  assert.ok(Math.abs(angles.second + 0.75 * CLOCK_TURN) < 0.0001);
  const clock = createClockPreview();
  assert.equal(clock.point(0, 0, date, 500), false);
  assert.equal(clock.point(1, 0, date, 500), true);
  const held = clock.readOffset(500, false, date);
  assert.ok(held > -16 && held < -15);
  clock.release(1000, date);
  assert.equal(clock.readOffset(1500, false, date), held);
  const returning = clock.readOffset(2330, false, date);
  assert.ok(returning > held && returning < 0);
  assert.equal(clock.readOffset(2330, true, date), 0);
  assert.equal(clock.readOffset(2460, false, date), 0);
});

// 表盘拖过十二点时取完整的 24 小时，三个时段按约定整点切换
test("拨钟时段跨过中午和午夜后仍按本地时间选窗景", function testClockWindowTime() {
  const morning = new Date(2026, 10, 20, 6, 0);
  const beforeSunset = new Date(2026, 10, 20, 15, 59);
  const beforeNight = new Date(2026, 10, 20, 18, 59);
  const beforeMidnight = new Date(2026, 10, 20, 23, 59);
  assert.equal(readClockHour(morning, -1), 5);
  assert.equal(readClockHour(morning, 720), 18);
  assert.equal(readClockHour(beforeMidnight, 1), 0);
  assert.equal(readWindowView(readClockHour(morning, -1)), WINDOW_NIGHT);
  assert.equal(readWindowView(readClockHour(morning, 0)), 0);
  assert.equal(readWindowView(readClockHour(beforeSunset, 0)), 0);
  assert.equal(readWindowView(readClockHour(beforeSunset, 1)), 1);
  assert.equal(readWindowView(readClockHour(beforeNight, 1)), WINDOW_NIGHT);
  assert.equal(readWindowView(readClockHour(beforeMidnight, 1)), WINDOW_NIGHT);
});

// 在晨光刚淡入晚霞时转向夜色，下一帧必须接着当前两张图的比例
test("连续拨钟会从当前混合画面接着过渡", function testWindowFadeRetarget() {
  const fade = createWindowFade();
  fade.select(1, 100, false);
  assert.deepEqual(fade.update(100, false), [1, 0, 0]);
  const current = fade.update(230, false).slice();
  assert.deepEqual(current, [0.5, 0.5, 0]);
  fade.select(WINDOW_NIGHT, 230, false);
  assert.deepEqual(fade.update(230, false), current);
  fade.select(WINDOW_NIGHT, 360, false);
  assert.deepEqual(fade.update(360, false), [0.25, 0.25, 0.5]);
  assert.deepEqual(fade.update(490, false), [0, 0, 1]);
});

// 减弱动效保留短暂的颜色混合，不引入额外的位移或等待
test("减弱动效时窗景更快完成淡入", function testReducedWindowFade() {
  const fade = createWindowFade();
  fade.select(WINDOW_NIGHT, 100, true);
  assert.deepEqual(fade.update(150, true), [0.5, 0, 0.5]);
  assert.deepEqual(fade.update(200, true), [0, 0, 1]);
});

// 金光先穿过连线再消散，减弱动效时余光不移动
test("连星奖励在完整和减弱动效下都能结束", function testStarCelebration() {
  const moving = readStarCelebration(230, false);
  assert.equal(moving.path, 0.5);
  assert.equal(moving.opacity, 1);
  const fading = readStarCelebration(640, false);
  assert.equal(fading.path, 1);
  assert.equal(fading.drift, 0.5);
  assert.equal(fading.opacity, 0.5);
  assert.equal(readStarCelebration(820, false), null);
  const reduced = readStarCelebration(60, true);
  assert.deepEqual(reduced, { path: 1, drift: 0, opacity: 0.5 });
  assert.equal(readStarCelebration(120, true), null);
});

// 取消播放时立即隐藏所有光点，之后重新触发仍从星轨起点走
test("星轨动效停止后可以从头播放", function testStarCelebrationReset() {
  const group = new THREE.Group();
  const points = [[-1, 0], [0, 1], [1, 0]];
  const effect = createStarCelebration(group, points, 0.15);
  const tracer = group.children[0];
  effect.play(100);
  effect.update(330, false);
  assert.equal(tracer.visible, true);
  assert.equal(tracer.position.x, 0);
  assert.equal(tracer.position.y, 1);
  effect.stop();
  for (const spark of group.children) {
    assert.equal(spark.visible, false);
  }
  effect.play(1000);
  effect.update(1000, false);
  assert.equal(tracer.visible, true);
  assert.equal(tracer.position.x, -1);
  assert.equal(tracer.material.depthWrite, false);
  tracer.geometry.dispose();
  tracer.material.dispose();
});

// 顺拨两圈再逆拨一圈，时针必须记住之前走过的整小时
test("拖动分针跨过半圈和十二点时，时针按比例连续转动", function testClockDragTurns() {
  const clock = createClockPreview();
  const date = new Date(2026, 10, 20, 3, 0, 0);
  const later = new Date(2026, 10, 20, 3, 0, 30);
  let now = 0;
  let previousHour = readClockAngles(date, 0).hour;

  // 每次拖过五分钟，检查时针只转动对应角度
  function dragTo(minute, expectedChange) {
    const angle = minute / 60 * CLOCK_TURN;
    now = now + 100;
    assert.equal(clock.point(Math.sin(angle), Math.cos(angle), date, now), true);
    const offset = clock.readOffset(now, false, date);
    const hands = readClockAngles(date, offset);
    const change = (previousHour - hands.hour) / CLOCK_TURN * 720;
    assert.ok(Math.abs(change - expectedChange) < 0.0001);
    previousHour = hands.hour;
  }

  dragTo(0, 0);
  for (let minute = 5; minute <= 120; minute = minute + 5) {
    dragTo(minute, 5);
  }
  assert.equal(clock.readOffset(now, false, date), 120);
  assert.equal(clock.readOffset(now, false, later), 119.5);
  for (let minute = 115; minute >= 60; minute = minute - 5) {
    dragTo(minute, -5);
  }
  assert.equal(clock.readOffset(now, false, date), 60);
  clock.release(now, date);
  assert.equal(clock.readOffset(now + 1000, false, date), 60);
  assert.equal(clock.readOffset(now + 1500, false, date), 0);
});

// 先拨钟再拖动家具，确保两个投影平面各自保持正确方向
test("表盘投影和地面投影互不干扰", function testClockPicking() {
  const camera = new THREE.OrthographicCamera(-2, 2, 2, -2, 0.1, 20);
  camera.position.set(0, 3, 5);
  camera.lookAt(0, 1, 0);
  camera.updateMatrixWorld(true);
  const face = new THREE.Object3D();
  face.position.set(1, 2, -2);
  const picker = createPetPicker(camera, null, []);

  // 模拟画布正常获取的位置范围
  function readBounds() {
    return { top: 0, left: 0, width: 200, height: 200 };
  }

  const canvas = { getBoundingClientRect: readBounds };
  const position = new THREE.Vector3(1.4, 2.1, -2);
  position.project(camera);
  const pointer = { clientX: (position.x + 1) * 100, clientY: (1 - position.y) * 100 };
  const dial = picker.readClockPoint(pointer, canvas, face);
  assert.ok(Math.abs(dial.x - 0.4) < 0.0001);
  assert.ok(Math.abs(dial.y - 0.1) < 0.0001);
  const ground = picker.readDragPoint({ clientX: 100, clientY: 150 }, canvas, 0.2);
  assert.ok(Math.abs(ground.y - 0.2) < 0.0001);
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
  const trunk = pet.root.getObjectByName("chestMesh");
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
  const picker = createPetPicker(camera, cat, spots);
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
  assert.equal(picker.read(click, canvas).name, "pet");
  furniture.position.z = -0.8;
  furniture.updateMatrixWorld(true);
  assert.equal(picker.read(click, canvas).name, "pet");
  furniture.position.z = 1;
  furniture.visible = false;
  furniture.updateMatrixWorld(true);
  assert.equal(picker.read(click, canvas).name, "pet");
});

// 耳朵与身体之间的空白不能摸到猫，透明图案和藏起来的网格也不能抢点击
test("只拾取宠物实际可见的网格", function testPetSilhouette() {
  const camera = new THREE.OrthographicCamera(-2, 2, 2, -2, 0.1, 12);
  camera.position.z = 5;
  camera.updateMatrixWorld();
  const pet = new THREE.Group();
  const shape = new THREE.SphereGeometry(0.3, 16, 12);
  const body = new THREE.Mesh(shape);
  const ear = new THREE.Mesh(shape);
  ear.position.x = 1;
  const sticker = new THREE.Mesh(shape, new THREE.MeshBasicMaterial({ transparent: true, depthWrite: false }));
  sticker.position.x = 0.5;
  pet.add(body, ear, sticker);
  pet.updateMatrixWorld(true);
  const picker = createPetPicker(camera, pet, []);

  // 画布对应四个场景单位，像素坐标可以直接对应模型的位置
  function readBounds() {
    return { width: 200, height: 200, left: 0, top: 0 };
  }
  const canvas = { getBoundingClientRect: readBounds };
  const earPoint = { clientX: 150, clientY: 100 };
  const gap = { clientX: 125, clientY: 100 };
  assert.equal(picker.read(earPoint, canvas).name, "pet");
  assert.equal(picker.read(gap, canvas).name, "");
  ear.visible = false;
  assert.equal(picker.read(earPoint, canvas).name, "");
});

// 容器和子网格交叠时，应该拾取具体星位而不是整个窗框
test("夜空星位与窗框分别拾取", function testWindowPicking() {
  const camera = new THREE.OrthographicCamera(-2, 2, 2, -2, 0.1, 12);
  camera.position.z = 5;
  camera.updateMatrixWorld();
  const frame = new THREE.Group();
  const glass = new THREE.Mesh(new THREE.BoxGeometry(2, 2, 0.1));
  const star = new THREE.Mesh(new THREE.SphereGeometry(0.1, 12, 8));
  const stars = new THREE.Group();
  star.position.z = 0.2;
  stars.add(star);
  frame.add(glass, stars);
  frame.updateMatrixWorld(true);
  const spots = [{ name: "window", object: frame }, { name: "star-ridge", object: star }];
  const picker = createPetPicker(camera, null, spots);

  // 射线正对星星，也会穿过它后面的玻璃
  function readBounds() {
    return { width: 200, height: 200, left: 0, top: 0 };
  }
  const canvas = { getBoundingClientRect: readBounds };
  const click = { clientX: 100, clientY: 100 };
  assert.equal(picker.read(click, canvas).name, "star-ridge");
  stars.visible = false;
  assert.equal(picker.read(click, canvas).name, "window");
});
