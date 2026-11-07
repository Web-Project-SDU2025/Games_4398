import { readRegisteredGame } from "./game-registry.mjs";
import { gameIdentity, economyRequest } from "./game-economy.mjs";
import { createGameplayTracker } from "./game-play.mjs";
import { PLAYER_MESSAGES, playerMessage } from "./game-session.mjs";

let tracker = null;
let stopped = false;
let configuration = null;
let identity = null;
const frame = document.getElementById("native-game");
const status = document.getElementById("native-status");

// 只将本局对应的服务器结果交回子游戏，开局先手和到账反馈不能由子游戏自行确认
function billiardsResult(event, result) {
  frame.contentWindow.postMessage({ type: "4398-billiards-result", roundId: event.roundId,
    sequence: event.sequence, result: result }, location.origin);
}

// 平台明确关闭时先停止网页循环，再保存同一账户的游玩时长
async function stopGame() {
  if (stopped) {
    return;
  }
  stopped = true;
  frame.contentWindow.postMessage({ type: PLAYER_MESSAGES.stopGame }, location.origin);
  if (tracker) {
    await tracker.stop();
  }
  window.parent.postMessage({ type: PLAYER_MESSAGES.gameStopped }, location.origin);
}

// 只接受父平台的关闭指令和当前子游戏的就绪消息
async function receiveMessage(event) {
  if (event.origin !== location.origin) {
    return;
  }
  if (event.source === frame.contentWindow && event.data
      && event.data.type === "4398-billiards-shot" && tracker && !stopped
      && configuration.id === "billiards") {
    tracker.reportBilliards(event.data);
    return;
  }
  if (event.source === frame.contentWindow && event.data && event.data.type === "4398-native-ready") {
    if (stopped || tracker) {
      return;
    }
    tracker = createGameplayTracker(null, configuration, identity, window.fetch.bind(window), window, billiardsResult);
    status.hidden = true;
    document.body.dataset.ready = "true";
    return;
  }
  if (event.source === frame.contentWindow && event.data && event.data.type === "4398-native-error") {
    status.hidden = false;
    status.textContent = String(event.data.message);
    return;
  }
  const message = playerMessage(event, window.parent, location.origin);
  if (message && message.type === PLAYER_MESSAGES.stopGame) {
    await stopGame();
  }
}

// 先核对商城持有关系，再打开站内网页入口，复用平台原有记录协议
async function initialize() {
  try {
    const parameters = new URLSearchParams(location.search);
    configuration = await readRegisteredGame(parameters.get("game"), window.fetch.bind(window));
    if (configuration.runtime !== "html5") {
      throw new Error("此入口只运行原生网页游戏");
    }
    identity = gameIdentity(localStorage);
    const authorization = await economyRequest("authorize", {
      gamePath: "/games/" + configuration.id + "/game.json", editor: false,
    }, identity, window.fetch.bind(window));
    if (authorization.code !== 0) {
      throw new Error(authorization.msg);
    }
    if (stopped) {
      return;
    }
    document.title = configuration.name;
    frame.title = configuration.name;
    frame.src = "/games/" + configuration.id + "/" + configuration.entry;
    frame.hidden = false;
  } catch (error) {
    status.hidden = false;
    status.textContent = "启动失败：" + error.message;
  }
}

window.addEventListener("message", receiveMessage);
initialize();
