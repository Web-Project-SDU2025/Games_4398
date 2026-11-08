import test from "node:test";
import assert from "node:assert/strict";
import { createComputer } from "../web/computer.mjs";
import { AI } from "../web/ai.mjs";
import { createMatch, resetMatch, openRound } from "../web/rules.mjs";
import { createStroke } from "../web/stroke.mjs";

// 用可控后台线程验证迟到结果和重开，不运行实际预演或依赖浏览器
function computerFixture() {
  const previousWorker = globalThis.Worker;
  const workers = [];

  // 保存线程消息与监听器，让用例按实际网络或计算顺序交回结果
  class ShotWorker {
    // 新控制器只在需要选杆时创建线程
    constructor() {
      this.listeners = {};
      this.requests = [];
      this.terminated = false;
      workers.push(this);
    }

    // 保存真实控制器注册的具名回调
    addEventListener(name, listener) {
      this.listeners[name] = listener;
    }

    // 记录发往后台的快照，不复制或修改正式比赛
    postMessage(message) {
      this.requests.push(message);
    }

    // 记录重开是否释放了旧线程
    terminate() {
      this.terminated = true;
    }
  }

  globalThis.Worker = ShotWorker;
  const match = createMatch();
  resetMatch(match, "single");
  openRound(match, 1);
  match.phase = "aiming";
  const ui = {
    stroke: createStroke(),
    stopped: false,
    cursor: { x: 500, y: 280 },
    placeValid: true,
    dirty: false,
    direction: { x: 1, y: 0 },
  };

  // 控制器进入思考阶段时清理玩家拉杆
  function cancelPull() {
    ui.power = 0;
  }

  const controller = createComputer(match, ui, { matches: false }, cancelPull);

  // 结束用例时释放线程并恢复全局对象，避免影响其他测试
  function dispose() {
    controller.reset();
    if (previousWorker === undefined) {
      delete globalThis.Worker;
    } else {
      globalThis.Worker = previousWorker;
    }
  }

  return { controller: controller, match: match, ui: ui, workers: workers, dispose: dispose };
}

// 重开期间迟到的方案不能出杆，新方案的动画和真实发射共用最终方向
test("电脑控制器隔离旧盘并显示当前方案", function testComputerReset() {
  const fixture = computerFixture();
  try {
    fixture.controller.update(AI.thinkSeconds);
    const previous = fixture.workers[0];
    const oldRequest = previous.requests[0];
    fixture.controller.reset();
    assert.equal(previous.terminated, true);
    previous.listeners.message({
      data: { request: oldRequest.request, plan: { direction: { x: 1, y: 0 }, power: 0.6 } },
    });
    assert.equal(fixture.ui.stroke.active, false);
    fixture.controller.update(AI.thinkSeconds);
    const current = fixture.workers[1];
    const request = current.requests[0];
    current.listeners.message({
      data: { request: request.request, plan: { direction: { x: 0, y: -1 }, power: 0.7 } },
    });
    assert.equal(fixture.ui.stroke.active, true);
    assert.equal(fixture.ui.stroke.stage, "aim");
    assert.equal(fixture.ui.direction.y, -1);
    assert.equal(fixture.ui.stroke.power, 0.7);
    assert.notEqual(request.balls[0].body.position, fixture.match.world.balls[0].body.position);
  } finally {
    fixture.dispose();
  }
});

// 失败后停留在可重开的状态，不让每帧重试变成长任务，重开后可以重新计算
test("电脑后台失败不重复提交且重开可恢复", function testComputerFailure() {
  const fixture = computerFixture();
  try {
    fixture.controller.update(AI.thinkSeconds);
    const worker = fixture.workers[0];
    worker.listeners.error();
    fixture.controller.update(AI.thinkSeconds);
    assert.equal(worker.requests.length, 1);
    assert.match(fixture.match.message, /选杆失败/);
    fixture.controller.reset();
    fixture.controller.update(AI.thinkSeconds);
    assert.equal(fixture.workers.length, 2);
    assert.equal(fixture.workers[1].requests.length, 1);
  } finally {
    fixture.dispose();
  }
});
