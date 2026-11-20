import * as THREE from "three";
import { attachTailRoot } from "./pet-model.js";
import { createRestClip, restEyesClosed } from "./pet-rest.js";
import { createPetWalk } from "./pet-walk.js";

// 两个关节动作相互过渡的秒数
const BLEND_SECONDS = 0.16;
// 摸摸的总时长，场景按同一时间解锁下一次互动
export const PET_DURATION = 0.9;
// 摸摸的关节关键帧和闭眼时段，时间单位为秒
const MOTIONS = {
  pet: {
    times: [0, 0.21, 0.52, PET_DURATION],
    headTilt: [0, 0.19, 0.13, 0],
    headNod: [0, -0.07, -0.055, 0],
    lean: [0, 0.044, 0.023, 0],
    tailSwing: [0, 0.24, -0.19, 0],
    pawReach: [0, 0, 0, 0],
    closedFrom: 0.1, closedUntil: 0.65,
  },
};

// 让 Three.js 插值每个关节的转角，爪子和身体可以在不同时间发力
function createClip(name, pose) {
  const tracks = [
    new THREE.NumberKeyframeTrack("head.rotation[z]", pose.times, pose.headTilt, THREE.InterpolateSmooth),
    new THREE.NumberKeyframeTrack("head.rotation[x]", pose.times, pose.headNod, THREE.InterpolateSmooth),
    new THREE.NumberKeyframeTrack("body.rotation[x]", pose.times, pose.lean, THREE.InterpolateSmooth),
    new THREE.NumberKeyframeTrack("tail.rotation[y]", pose.times, pose.tailSwing, THREE.InterpolateSmooth),
    new THREE.NumberKeyframeTrack("rightPaw.rotation[x]", pose.times, pose.pawReach, THREE.InterpolateSmooth),
  ];
  return new THREE.AnimationClip(name, -1, tracks);
}

// 动作从当前姿态过渡，减弱动态效果时只保留表情反馈
export function createPetMotion(pet) {
  const mixer = new THREE.AnimationMixer(pet.root);
  const actions = {};
  for (const name of Object.keys(MOTIONS)) {
    const clip = createClip(name, MOTIONS[name]);
    const action = mixer.clipAction(clip);
    action.setLoop(THREE.LoopOnce, 1);
    actions[name] = action;
  }
  const rest = mixer.clipAction(createRestClip());
  rest.setLoop(THREE.LoopOnce, 1);
  actions.rest = rest;
  let active = null;
  let expressionTime = 0;
  let expressionName = "";
  let reduced = false;
  const walk = createPetWalk(pet);
  const chest = pet.root.getObjectByName("chest");
  const tailSkin = pet.root.getObjectByName("tailSkin");
  const tailTip = pet.root.getObjectByName("tailTip");
  const tailTipUp = tailTip.position.clone();
  const curlTravel = tailTip.userData.curled.clone();
  curlTravel.sub(tailTipUp);
  const walkTravel = tailTip.userData.walking.clone();
  walkTravel.sub(tailTipUp);

  // 新动作淡入，上一段仍未结束时让混合器平滑收回它
  function play(name, reduceMotion) {
    const next = actions[name];
    if (!next) {
      return;
    }
    if (expressionName === name && expressionTime < next.getClip().duration) {
      return;
    }
    walk.clear();
    expressionTime = 0;
    expressionName = name;
    reduced = reduceMotion;
    if (reduced) {
      mixer.stopAllAction();
      active = null;
      return;
    }
    next.reset();
    next.setEffectiveWeight(1);
    next.play();
    if (active && active !== next && active.isRunning()) {
      active.crossFadeTo(next, BLEND_SECONDS, false);
    } else {
      next.fadeIn(BLEND_SECONDS);
    }
    active = next;
  }

  // 每帧推进关节，并在眨眼和休息时显示弧形眼睑
  function update(delta, elapsed, reduceMotion) {
    if (reduceMotion && !reduced && active) {
      mixer.stopAllAction();
      active = null;
    }
    reduced = reduceMotion;
    mixer.update(delta);
    expressionTime = expressionTime + delta;
    chest.scale.x = 1;
    if (!reduced && expressionName === "rest" && restEyesClosed(expressionTime)) {
      chest.scale.x = 1 + 0.016 * Math.sin(expressionTime * 3.2);
    }
    let closed = !reduced && (elapsed % 4.7 < 0.095 || elapsed % 11.8 > 11.65);
    const pose = MOTIONS[expressionName];
    if (pose && expressionTime > pose.closedFrom && expressionTime < pose.closedUntil) {
      closed = true;
    }
    if (expressionName === "rest" && restEyesClosed(expressionTime)) {
      closed = true;
    }
    for (const parts of pet.eyes) {
      parts.eye.visible = !closed;
      parts.eyelid.visible = closed;
    }
  }

  // 睡姿和步态更新后，把尾根贴回臀部，尾尖与尾身使用同一帧的形状
  function syncTail() {
    attachTailRoot(pet.tail, chest);
    const curl = THREE.MathUtils.clamp(tailSkin.morphTargetInfluences[0], 0, 1);
    const walking = tailSkin.morphTargetInfluences[1];
    tailSkin.morphTargetInfluences[0] = curl;
    tailTip.position.copy(tailTipUp);
    tailTip.position.addScaledVector(curlTravel, curl);
    tailTip.position.addScaledVector(walkTravel, walking);
  }

  // 模型离开页面时，停止动作并清空混合器缓存
  function dispose() {
    mixer.stopAllAction();
    mixer.uncacheRoot(pet.root);
  }
  return { play: play, update: update, carry: walk.carry, clearCarry: walk.clear, syncTail: syncTail, dispose: dispose };
}
