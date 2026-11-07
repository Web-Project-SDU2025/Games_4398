import { planAiShot } from "./ai.mjs";
import { createPockets } from "./config.mjs";

// 后台只读取球位快照，真实比赛和动画始终由页面线程维护
function calculateShot(event) {
  const snapshot = event.data;
  const match = { current: snapshot.current, groups: snapshot.groups,
    breakShot: snapshot.breakShot,
    world: { balls: snapshot.balls, pockets: createPockets() } };
  try {
    const plan = planAiShot(match, Math.random);
    self.postMessage({ request: snapshot.request, plan: plan });
  } catch (error) {
    self.postMessage({ request: snapshot.request, error: error.message });
  }
}

self.addEventListener("message", calculateShot);
