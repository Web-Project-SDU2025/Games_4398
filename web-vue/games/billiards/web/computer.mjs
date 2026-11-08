import { AI, planAiPlacement } from "./ai.mjs";
import { placeCue } from "./rules.mjs";
import { beginStroke } from "./stroke.mjs";

// 为一局比赛创建电脑控制器，比赛和动画状态由主入口传入，返回推进与重置方法
export function createComputer(match, ui, reducedMotion, cancelPull) {
  let wait = 0;
  let phase = "";
  let worker = null;
  let request = 0;
  let planning = false;
  let failed = false;

  // 重开、换模式和退出时终止旧计算，迟到结果不能进入下一盘
  function reset() {
    request = request + 1;
    planning = false;
    failed = false;
    wait = 0;
    phase = "";
    if (worker) {
      worker.terminate();
      worker = null;
    }
  }

  // 计算失败后等待玩家重开，不在画面线程反复执行昂贵预演
  function planningFailed() {
    planning = false;
    failed = true;
    match.message = "电脑选杆失败，请重新开始本盘";
    match.revision = match.revision + 1;
    ui.dirty = true;
  }

  // 只接受当前请求的最终方案，显示球杆和真实发射共用同一方向
  function receivePlan(event) {
    if (event.data.request !== request || !planning || ui.stopped) {
      return;
    }
    if (match.mode !== "single" || match.current !== 1 || match.phase !== "aiming") {
      return;
    }
    if (event.data.error) {
      planningFailed();
      return;
    }
    const plan = event.data.plan;
    planning = false;
    const origin = match.world.balls[0].body.position;
    beginStroke(ui.stroke, plan.direction, plan.power, origin, true, reducedMotion.matches);
    ui.direction = ui.stroke.direction;
    ui.dirty = true;
  }

  // 后台只接收球位副本，避免传递 Matter 内部循环引用或提前改变正式局面
  function requestShot() {
    planning = true;
    request = request + 1;
    const balls = [];
    for (const ball of match.world.balls) {
      const position = ball.body.position;
      balls.push({
        id: ball.id,
        active: ball.active,
        body: { position: { x: position.x, y: position.y } },
      });
    }
    try {
      if (!worker) {
        worker = new Worker(new URL("./ai-worker.mjs", import.meta.url), { type: "module" });
        worker.addEventListener("message", receivePlan);
        worker.addEventListener("error", planningFailed);
      }
      worker.postMessage({
        request: request,
        balls: balls,
        groups: match.groups.slice(),
        current: match.current,
        breakShot: match.breakShot,
      });
    } catch (error) {
      planningFailed();
    }
  }

  // 摆球和出杆分别等待，连续进球后重新判断局面，seconds 是本帧经过的秒数
  function update(seconds) {
    if (ui.stroke.active || planning || failed) {
      return;
    }
    if (match.mode !== "single" || match.current !== 1
        || !["placement", "aiming"].includes(match.phase)) {
      wait = 0;
      phase = "";
      return;
    }
    if (phase !== match.phase) {
      phase = match.phase;
      wait = 0;
      ui.cursor = null;
      ui.placeValid = false;
      cancelPull();
      match.message = "电脑思考中…";
      match.revision = match.revision + 1;
    }
    wait = wait + seconds;
    if (wait < AI.thinkSeconds) {
      return;
    }
    wait = 0;
    phase = "";
    if (match.phase === "placement") {
      const position = planAiPlacement(match);
      if (position) {
        placeCue(match, position.x, position.y);
      }
      ui.cursor = null;
    } else {
      requestShot();
    }
    ui.dirty = true;
  }

  return { update: update, reset: reset };
}
