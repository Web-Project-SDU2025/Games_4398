import { MathUtils } from "three";

// 从站起、走近、低头、咀嚼到抬头回座的各个时刻，单位为秒
const FEED_TIMES = { standing: 0.28, arrival: 1, lowered: 1.42, lastBite: 2.62, raised: 2.94, home: 3.66 };
// 完成一次进食并回到坐姿所需的秒数
export const FEED_DURATION = 3.94;
// 咀嚼阶段的口数，每口结束后移除一部分猫粮
const FEED_BITES = 3;
// 小碗模型中实际生成的粮食颗粒数
export const FOOD_GRAIN_COUNT = 10;
// 低头时嘴在身体前方的距离，用它对准碗心
const MOUTH_REACH = 1.05;
// 低头时猫头的前伸位置与俯角
const BITE_HEAD = { height: 0.92, forward: 0.69, pitch: 0.62, nod: 0.035 };
// 喂食时胸口的倾角以及项圈跟随头部移动到的位置
const FEED_BODY = { pitch: 0.14, collarHeight: 0.85, collarDepth: 0.31 };

// 根据当前碗的位置和体型，分别算出站起、走近、进食和退回的姿态
export function readFeedPose(elapsed, bowl, size) {
  const distance = Math.hypot(bowl.x, bowl.z);
  const path = Math.max(0, distance - MOUTH_REACH * size);
  const forward = MathUtils.smoothstep(elapsed, FEED_TIMES.standing, FEED_TIMES.arrival);
  const backward = MathUtils.smoothstep(elapsed, FEED_TIMES.raised, FEED_TIMES.home);
  const travel = path * (forward - backward);
  let horizontal = 0;
  let depth = 0;
  if (travel > 0) {
    horizontal = bowl.x / distance * travel;
    depth = bowl.z / distance * travel;
  }
  const rise = MathUtils.smoothstep(elapsed, 0, FEED_TIMES.standing);
  const sit = MathUtils.smoothstep(elapsed, FEED_TIMES.home, FEED_DURATION);
  const lower = MathUtils.smoothstep(elapsed, FEED_TIMES.arrival, FEED_TIMES.lowered);
  const lift = MathUtils.smoothstep(elapsed, FEED_TIMES.lastBite, FEED_TIMES.raised);
  const eating = MathUtils.clamp((elapsed - FEED_TIMES.lowered) / (FEED_TIMES.lastBite - FEED_TIMES.lowered), 0, 1);
  const bite = eating * FEED_BITES;
  const stride = Math.max(walkStride(forward), walkStride(backward));
  const remainingFood = Math.ceil(FOOD_GRAIN_COUNT * (1 - Math.floor(bite) / FEED_BITES));
  return {
    x: horizontal, z: depth, stand: rise - sit, distance: travel / size,
    stride: stride, reach: 0, lean: lower - lift,
    chew: Math.sin(Math.PI * bite) ** 2, foodCount: remainingFood,
  };
}

// 行走开始和结束时令步幅归零，站在碗前不会继续迈腿
function walkStride(progress) {
  if (progress <= 0 || progress >= 1) {
    return 0;
  }
  return Math.sin(Math.PI * progress);
}

// 在四足站姿上压低胸口、前伸脖子并轻点头，爪子保持原来的支撑位置
export function applyFeedLean(pet, pose) {
  const head = pet.head;
  head.position.y = MathUtils.lerp(head.position.y, BITE_HEAD.height, pose.lean);
  head.position.z = MathUtils.lerp(head.position.z, BITE_HEAD.forward, pose.lean);
  head.rotation.x = pose.lean * (BITE_HEAD.pitch + pose.chew * BITE_HEAD.nod);
  head.rotation.y = 0;
  const chest = pet.chest;
  chest.rotation.x = pose.lean * FEED_BODY.pitch;
  const collar = pet.root.getObjectByName("collar");
  collar.position.y = MathUtils.lerp(collar.position.y, FEED_BODY.collarHeight, pose.lean);
  collar.position.z = MathUtils.lerp(collar.position.z, FEED_BODY.collarDepth, pose.lean);
}
