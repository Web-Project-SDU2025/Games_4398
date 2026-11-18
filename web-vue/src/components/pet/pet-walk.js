import { MathUtils, Vector3 } from "three";

const STRIDE_LENGTH = 0.88;
const STRIDE_SWEEP = 0.19;
const STRIDE_LIFT = 0.13;
const FRONT_ANKLE_HEIGHT = 0.125;
const BACK_ANKLE_HEIGHT = 0.115;

// 为四条腿保留各自的坐姿，再根据归位路线切换到四足步态
export function createPetWalk(pet) {
  const root = pet.root;
  const frontLeft = readLeg(root, "leftPaw", "leftElbow", "leftWrist", -1, true);
  const frontRight = readLeg(root, "rightPaw", "rightElbow", "rightWrist", 1, true);
  const backLeft = readLeg(root, "leftHip", "leftKnee", "leftHindFoot", -1, false);
  const backRight = readLeg(root, "rightHip", "rightKnee", "rightHindFoot", 1, false);
  const legs = [backLeft, frontLeft, backRight, frontRight];
  const chest = root.getObjectByName("chest");
  const collar = root.getObjectByName("collar");
  const head = pet.head;
  const chestPosition = chest.position.clone();
  const collarPosition = collar.position.clone();
  const headPosition = head.position.clone();
  const tailSkin = root.getObjectByName("tailSkin");
  let active = false;

  // 先站稳再走，停步叼碗或伸爪时其余三条腿留在地面
  function carry(pose, bowl) {
    active = true;
    const stand = pose.stand;
    const reach = pose.reach;
    let lowered = 0;
    if (bowl) {
      lowered = reach * 0.23;
    }
    chest.position.y = MathUtils.lerp(chestPosition.y, 0.66, stand);
    chest.position.z = MathUtils.lerp(chestPosition.z, -0.25, stand);
    chest.scale.set(1, 1 - 0.38 * stand, 1 + 0.31 * stand);
    head.position.y = MathUtils.lerp(headPosition.y, 1.34 - lowered, stand);
    head.position.z = MathUtils.lerp(headPosition.z, 0.61 + lowered * 0.35, stand);
    head.rotation.x = reach * 0.18;
    collar.position.y = MathUtils.lerp(collarPosition.y, 0.96 - lowered * 0.7, stand);
    collar.position.z = MathUtils.lerp(collarPosition.z, 0.24 + lowered * 0.5, stand);
    const cycle = pose.distance / STRIDE_LENGTH;
    // 行走时只切换尾身形状并轻摆，尾根仍固定在臀部中线
    pet.tail.rotation.y = Math.sin(cycle * Math.PI) * 0.14 * pose.stride;
    pet.tail.rotation.z = 0;
    tailSkin.morphTargetInfluences[1] = stand;
    for (let index = 0; index < legs.length; index = index + 1) {
      const leg = legs[index];
      let nudge = 0;
      if (!bowl && leg === frontRight) {
        nudge = reach;
      }
      moveLeg(leg, stand, cycle - index * 0.25, pose.stride, nudge);
    }
  }

  // 归位之后还原各关节，其他互动继续使用原来的坐姿起点
  function clear() {
    if (!active) {
      return;
    }
    active = false;
    chest.position.copy(chestPosition);
    chest.scale.set(1, 1, 1);
    head.position.copy(headPosition);
    head.rotation.x = 0;
    collar.position.copy(collarPosition);
    pet.tail.rotation.y = 0;
    tailSkin.morphTargetInfluences[1] = 0;
    for (const leg of legs) {
      leg.upper.position.copy(leg.upperHome);
      leg.middle.position.copy(leg.middleHome);
      leg.foot.position.copy(leg.footHome);
      leg.upper.rotation.x = 0;
      leg.middle.rotation.x = 0;
      leg.foot.rotation.x = 0;
    }
  }

  return { carry: carry, clear: clear };
}

// 记录一条腿的三个节点和坐姿位置，便于结束时原样复位
function readLeg(root, upperName, middleName, footName, side, front) {
  const upper = root.getObjectByName(upperName);
  const middle = root.getObjectByName(middleName);
  const foot = root.getObjectByName(footName);
  return {
    upper: upper, middle: middle, foot: foot, side: side, front: front,
    upperHome: upper.position.clone(), middleHome: middle.position.clone(), footHome: foot.position.clone(),
    ground: new Vector3(),
  };
}

// 后划的脚掌负责承重，向前迈时抬起，脚掌角度抵消上腿的转动
function moveLeg(leg, stand, cycle, stride, nudge) {
  const phase = cycle - Math.floor(cycle);
  let sweep = 1 - phase / 0.67 * 2;
  let lift = 0;
  if (phase > 0.67) {
    const swing = (phase - 0.67) / 0.33;
    sweep = -1 + 2 * MathUtils.smoothstep(swing, 0, 1);
    lift = Math.sin(swing * Math.PI) * STRIDE_LIFT;
  }
  let height = BACK_ANKLE_HEIGHT;
  let baseDepth = -0.65;
  let upperHeight = 0.58;
  let upperDepth = -0.62;
  let jointBend = 0.12;
  if (leg.front) {
    height = FRONT_ANKLE_HEIGHT;
    baseDepth = 0.18;
    upperHeight = 0.57;
    upperDepth = 0.16;
    jointBend = -0.115;
  }
  const forward = sweep * STRIDE_SWEEP * stride + nudge * 0.2;
  const up = lift * stride + nudge * 0.12;
  const footDepth = baseDepth + forward;
  const footHeight = height + up;
  const pivotDepth = MathUtils.lerp(leg.upperHome.z, upperDepth, stand);
  const pivotHeight = MathUtils.lerp(leg.upperHome.y, upperHeight, stand);
  const jointDepth = (upperDepth + footDepth) / 2 + jointBend;
  const jointHeight = (upperHeight + footHeight) / 2;
  leg.upper.position.set(leg.upperHome.x, pivotHeight, pivotDepth);
  aimBone(leg, stand, jointHeight, jointDepth, footHeight, footDepth);
}

// 让上段指向屈曲的关节、下段指向脚掌，四足展开时保留清晰的膝肘轮廓
function aimBone(leg, stand, jointHeight, jointDepth, footHeight, footDepth) {
  const upperY = jointHeight - leg.upper.position.y;
  const upperZ = jointDepth - leg.upper.position.z;
  const lowerY = footHeight - jointHeight;
  const lowerZ = footDepth - jointDepth;
  const turnUpper = Math.atan2(-upperZ, -upperY) * stand;
  const turnLower = Math.atan2(-lowerZ, -lowerY) * stand;
  leg.upper.rotation.x = turnUpper;
  leg.middle.rotation.x = turnLower - turnUpper;
  leg.foot.rotation.x = -turnLower;
  // 反算到父节点坐标，脚掌目标不会被腿部转角带离地面
  setBoneEnd(leg.middle.position, leg.middleHome, upperY, upperZ, turnUpper, stand, leg.ground);
  setBoneEnd(leg.foot.position, leg.footHome, lowerY, lowerZ, turnLower, stand, leg.ground);
}

// 把世界方向换回关节内的方向，再从原坐姿位置过渡过去
function setBoneEnd(position, home, height, depth, turn, stand, target) {
  const cosine = Math.cos(turn);
  const sine = Math.sin(turn);
  target.set(home.x, height * cosine + depth * sine, depth * cosine - height * sine);
  position.lerpVectors(home, target, stand);
}
