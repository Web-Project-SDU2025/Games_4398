// 游戏与平台共用状态消息，时长和奖励变化分别刷新对应缓存
export const GAMEPLAY_MESSAGES = {
  recordsChanged: "4398-records-changed",
  walletChanged: "4398-wallet-changed",
  notice: "4398-gameplay-notice",
  accessRevoked: "4398-game-access-revoked",
};
// 事件读取间隔、普通游戏计时间隔和失败重试次数
export const GAMEPLAY_LIMITS = {
  pollMilliseconds: 1000,
  heartbeatMilliseconds: 5000,
  maximumAttempts: 8,
  timeoutMilliseconds: 10000,
};
// 通用游戏事件接口前缀，所有游戏共用相同请求结构
const GAMEPLAY_PREFIX = "/api/gameplay/";

// 发送单个事件，页面退出时通过 keepalive 尽量完成最后一次保存
export async function gameplayRequest(
  action,
  values,
  identity,
  fetchResource,
  keepalive,
) {
  const controller = new AbortController();
  // 超时后结束本次请求，后续重试仍使用同一会话编号
  function expire() {
    controller.abort();
  }
  const timer = setTimeout(expire, GAMEPLAY_LIMITS.timeoutMilliseconds);
  try {
    const response = await fetchResource(GAMEPLAY_PREFIX + action, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer " + identity.token,
      },
      body: JSON.stringify({ data: values }),
      keepalive: keepalive === true,
      signal: controller.signal,
    });
    if (!response.ok) {
      throw new Error("游戏记录暂时无法保存");
    }
    return await response.json();
  } finally {
    clearTimeout(timer);
  }
}

// 适配器上报关卡事件，普通入口按可见运行时间生成通用游玩记录
export function createGameplayTracker(
  adapter,
  configuration,
  identity,
  fetchResource,
  host,
  billiardsResult,
) {
  const queue = [];
  const sessions = new Map();
  const failedBilliardsRounds = new Set();
  let busy = null;
  let stopped = false;
  let genericRun = "";
  let genericMilliseconds = 0;
  let lastSample = host.performance.now();
  let lastHeartbeat = 0;
  let paused = false;

  // 同源平台只收到状态变化消息，具体记录由其重新向后端读取
  function notify(type, message) {
    if (host.parent !== host) {
      host.parent.postMessage(
        { type: type, message: message },
        host.location.origin,
      );
    }
  }

  // 验证事件种类和编号，单次计时事件只携带必要的业务值
  function enqueue(event) {
    if (
      !event ||
      !["begin", "heartbeat", "finish", "billiards"].includes(event.type) ||
      typeof event.runId !== "string"
    ) {
      throw new Error("游戏事件格式不正确");
    }
    if (event.type === "begin") {
      if (!sessions.has(event.runId)) {
        sessions.set(event.runId, {
          challenge: "",
          activeMilliseconds: 0,
          finished: false,
        });
      }
    }
    const session = sessions.get(event.runId);
    if (!session) {
      return;
    }
    if (Number.isFinite(event.activeMilliseconds)) {
      session.activeMilliseconds = event.activeMilliseconds;
    }
    queue.push({ event: event, attempts: 0 });
  }

  // 普通 main 入口按可见时间计时，编辑器打开时暂停该入口的统计
  function sampleGeneric() {
    if (adapter) {
      return;
    }
    const now = host.performance.now();
    const elapsed = Math.max(0, now - lastSample);
    lastSample = now;
    if (!paused && host.document.visibilityState === "visible") {
      genericMilliseconds = genericMilliseconds + elapsed;
    }
    if (now - lastHeartbeat >= GAMEPLAY_LIMITS.heartbeatMilliseconds) {
      lastHeartbeat = now;
      enqueue({
        type: "heartbeat",
        runId: genericRun,
        activeMilliseconds: Math.floor(genericMilliseconds),
      });
    }
  }

  // 每次仅处理队首，开局响应丢失时重试取得原凭证，结算响应丢失时复用原结果
  async function send(item) {
    const event = item.event;
    // 某杆被拒后停止本盘后续报告，避免跳过该杆而连续触发序号错误
    if (event.type === "billiards" && failedBilliardsRounds.has(event.roundId)) {
      return;
    }
    const session = sessions.get(event.runId);
    if (!session || session.finished) {
      return;
    }
    const values = {
      runId: event.runId,
      gamePath: "/games/" + configuration.id + "/game.json",
      activeMilliseconds: event.activeMilliseconds || 0,
    };
    if (event.type === "begin") {
      values.level = event.level;
      values.hash = event.hash;
      values.mode = event.mode || "official";
    } else {
      if (!session.challenge) {
        return;
      }
      values.challenge = session.challenge;
      values.outcome = event.outcome;
      if (event.type === "billiards") {
        values.roundId = event.roundId;
        values.sequence = event.sequence;
        values.shot = event.shot;
        values.matchMode = event.matchMode;
      }
    }
    const result = await gameplayRequest(
      event.type,
      values,
      identity,
      fetchResource,
      false,
    );
    if (event.type === "billiards" && result.code === 0 && result.data.retry === true) {
      throw new Error("本杆记录等待计时同步");
    }
    if (event.type === "billiards" && billiardsResult) {
      billiardsResult(event, result);
    }
    if (result.code !== 0) {
      if (event.type === "billiards") {
        failedBilliardsRounds.add(event.roundId);
      } else {
        session.finished = true;
      }
      notify(GAMEPLAY_MESSAGES.notice, result.msg);
      return;
    }
    if (event.type === "begin") {
      session.challenge = result.data.challenge;
    }
    if (result.data.outcome === "revoked") {
      session.finished = true;
      notify(GAMEPLAY_MESSAGES.accessRevoked, "游戏已由管理员撤回");
      return;
    }
    notify(GAMEPLAY_MESSAGES.recordsChanged, "");
    if (event.type === "billiards" && result.data.reward > 0 && !result.data.repeated) {
      notify(GAMEPLAY_MESSAGES.walletChanged,
        "本局结算 +" + result.data.reward + " 字块币 · " + result.data.titles);
    }
    if (event.type === "finish") {
      session.finished = true;
      if (result.data.reward > 0 && configuration.id !== "billiards") {
        notify(
          GAMEPLAY_MESSAGES.walletChanged,
          "通关奖励 +" + result.data.reward + " 字块币",
        );
      }
    }
  }

  // 批量读取 Java 事件，异步请求期间的新轮询复用当前处理任务
  async function process() {
    if (adapter) {
      const encoded = await adapter.gameEvents();
      const events = JSON.parse(encoded);
      if (!Array.isArray(events)) {
        throw new Error("游戏事件应为列表");
      }
      for (const event of events) {
        enqueue(event);
      }
    } else {
      sampleGeneric();
    }
    while (queue.length > 0) {
      const item = queue[0];
      try {
        await send(item);
        queue.shift();
      } catch (error) {
        item.attempts = item.attempts + 1;
        if (item.attempts >= GAMEPLAY_LIMITS.maximumAttempts) {
          queue.shift();
          if (item.event.type === "billiards") {
            failedBilliardsRounds.add(item.event.roundId);
            if (billiardsResult) {
              billiardsResult(item.event, { code: 1, msg: "台球奖励同步失败，请重新开始本盘" });
            }
          }
          notify(GAMEPLAY_MESSAGES.notice, "游戏记录保存失败");
        }
        break;
      }
    }
  }

  // 同一时间只启动一个事件读取任务，避免消息顺序因慢网络改变
  async function flush() {
    while (busy) {
      await busy;
    }
    const task = process();
    busy = task;
    try {
      await task;
    } catch (error) {
      notify(GAMEPLAY_MESSAGES.notice, "游戏记录状态读取失败");
    } finally {
      if (busy === task) {
        busy = null;
      }
    }
  }

  // 定时回调仅安排一次事件同步，退出后保留已保存的会话记录
  function poll() {
    if (!stopped && !busy) {
      flush();
    }
  }

  // 正常关闭时先保存 Java 最后事件，再为尚未结束的会话写入离开结果
  async function stop() {
    if (stopped) {
      return;
    }
    stopped = true;
    host.clearInterval(timer);
    await flush();
    for (const entry of sessions) {
      const runId = entry[0];
      const session = entry[1];
      if (!session.finished) {
        enqueue({
          type: "finish",
          runId: runId,
          outcome: "closed",
          activeMilliseconds: session.activeMilliseconds,
        });
      }
    }
    await flush();
    host.removeEventListener("pagehide", pageHidden);
  }

  // 浏览器直接关闭时使用最后已知时长补交结果，已有结束事件保留原通过或失败状态
  function pageHidden() {
    host.clearInterval(timer);
    for (const entry of sessions) {
      const runId = entry[0];
      const session = entry[1];
      if (!session.finished && session.challenge) {
        let outcome = "closed";
        for (const item of queue) {
          if (item.event.runId === runId && item.event.type === "finish") {
            outcome = item.event.outcome;
          }
        }
        const values = {
          runId: runId,
          challenge: session.challenge,
          activeMilliseconds: session.activeMilliseconds,
          outcome: outcome,
        };
        gameplayRequest("finish", values, identity, fetchResource, true).catch(
          reportCloseFailure,
        );
      }
    }
  }

  // 页面退出后的失败保留服务器最近一次心跳，下一次读取按超时状态显示
  function reportCloseFailure() {
    return;
  }

  // 普通游戏打开编辑器时暂停计时，适配器游戏自行上报各自的正式与试玩事件
  function pause(value) {
    sampleGeneric();
    paused = value;
  }

  // 原生台球提供单杆证据，排在开局与心跳之后发送，重试继续使用相同局号和序号
  function reportBilliards(report) {
    if (stopped || adapter || configuration.id !== "billiards") {
      return;
    }
    sampleGeneric();
    enqueue({ type: "billiards", runId: genericRun, roundId: report.roundId,
      sequence: report.sequence, shot: report.shot, matchMode: report.matchMode,
      activeMilliseconds: Math.floor(genericMilliseconds) });
    poll();
  }

  if (!adapter) {
    genericRun = host.crypto.randomUUID();
    enqueue({
      type: "begin",
      runId: genericRun,
      mode: "official",
      activeMilliseconds: 0,
    });
  }
  const timer = host.setInterval(poll, GAMEPLAY_LIMITS.pollMilliseconds);
  host.addEventListener("pagehide", pageHidden, { once: true });
  poll();
  return { flush: flush, stop: stop, pause: pause, reportBilliards: reportBilliards };
}
