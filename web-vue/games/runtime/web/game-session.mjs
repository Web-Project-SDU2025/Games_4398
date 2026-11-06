// 平台与游戏页共用的消息、运行入口和账户目录规则
export const PLAYER_MESSAGES = {
  openEditor: "4398-open-editor",
  closeEditor: "4398-close-editor",
  editorState: "4398-editor-state",
  stopGame: "4398-stop-game",
  gameStopped: "4398-game-stopped",
};
export const MAIN_ADAPTER = "platform.runtime.MainGame";
export const EDITOR_LEVEL_PROPERTY = "platform.editor.level";
export const EDITOR_ACCESS_PROPERTY = "platform.editor.allowed";
export const PLAYER_PAGE = "/games/java-player.html";
const OWNER_PATTERN = /^[a-zA-Z0-9_-]{1,64}$/;
const DEFAULT_OWNER = "standalone";
// 将账户编号转换成目录名，缺少编号或格式不符合规则时使用独立运行目录
export function gameOwner(value) {
  if (typeof value !== "string" && typeof value !== "number") {
    return DEFAULT_OWNER;
  }
  const owner = String(value);
  if (!OWNER_PATTERN.test(owner)) {
    return DEFAULT_OWNER;
  }
  return owner;
}

// 校验消息来源与内容，外部页面、未知指令和缺少开关值的消息返回空结果
export function playerMessage(event, source, origin) {
  if (!source || event.source !== source || event.origin !== origin) {
    return null;
  }
  const message = event.data;
  if (!message || typeof message !== "object" || Array.isArray(message)) {
    return null;
  }
  if (message.type === PLAYER_MESSAGES.editorState) {
    if (typeof message.open !== "boolean") {
      return null;
    }
    return message;
  }
  if (
    message.type === PLAYER_MESSAGES.openEditor ||
    message.type === PLAYER_MESSAGES.closeEditor ||
    message.type === PLAYER_MESSAGES.stopGame ||
    message.type === PLAYER_MESSAGES.gameStopped
  ) {
    return message;
  }
  return null;
}

// 编辑器编号按游戏配置检查，空值、非整数与越界值使用配置中的最小编号
export function editorLevel(value, limits) {
  if (value === null || value === "") {
    return limits.minimum;
  }
  const level = Number(value);
  if (
    !Number.isInteger(level) ||
    level < limits.minimum ||
    level > limits.maximum
  ) {
    return limits.minimum;
  }
  return level;
}
