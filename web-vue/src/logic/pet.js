export const PET_MARGIN = 12;
export const DRAG_THRESHOLD = 6;
export const GREETING_INTERVAL = 13000;
export const GREETING_DURATION = 5000;
export const GREETINGS = [
  "今天想挑战哪个游戏？",
  "规则没看懂？我们一起研究",
  "卡关了可以来聊聊玩法",
  "别忘了保存这一局的分数",
  "玩得开心，也记得休息一下",
];
export const PET_IMAGES = {
  idle: "/images/pet/idling.png",
  hover: "/images/pet/hovering.png",
  thinking: "/images/pet/thinking.png",
};

// 限制宠物位置，防止拖动或缩小窗口后找不到它
export function clampPosition(
  left,
  top,
  width,
  height,
  viewportWidth,
  viewportHeight
) {
  const maximumLeft = Math.max(PET_MARGIN, viewportWidth - width - PET_MARGIN);
  const maximumTop = Math.max(PET_MARGIN, viewportHeight - height - PET_MARGIN);
  return {
    left: Math.max(PET_MARGIN, Math.min(left, maximumLeft)),
    top: Math.max(PET_MARGIN, Math.min(top, maximumTop)),
  };
}

// 每个账号分别保存位置和提示开关，损坏的数据使用默认设置
export function readSettings(userId) {
  const defaults = {
    visible: true,
    greetings: true,
    horizontal: 1,
    vertical: 0.85,
  };
  try {
    const saved = JSON.parse(
      localStorage.getItem("4398-pet-settings-" + userId)
    );
    if (!saved || typeof saved !== "object") {
      return defaults;
    }
    if (typeof saved.visible === "boolean") {
      defaults.visible = saved.visible;
    }
    if (typeof saved.greetings === "boolean") {
      defaults.greetings = saved.greetings;
    }
    if (Number.isFinite(saved.horizontal) && Number.isFinite(saved.vertical)) {
      defaults.horizontal = Math.max(0, Math.min(saved.horizontal, 1));
      defaults.vertical = Math.max(0, Math.min(saved.vertical, 1));
    }
  } catch (error) {
    return defaults;
  }
  return defaults;
}

// 存储错误交给界面显示，拖动和本次聊天仍能继续使用
export function saveSettings(userId, settings) {
  localStorage.setItem("4398-pet-settings-" + userId, JSON.stringify(settings));
}
