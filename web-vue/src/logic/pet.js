// 宠物与窗口边缘的预留距离，单位为像素
export const PET_MARGIN = 12;
// 指针移动达到此像素距离后进入拖动状态
export const DRAG_THRESHOLD = 6;
// 宠物初始纵向位置在可移动高度中的比例
export const DEFAULT_VERTICAL_POSITION = 1;
// 旧版默认位置仅用于迁移，手动拖动保存的其他位置继续保留
const PREVIOUS_VERTICAL_POSITION = 0.85;
// 宠物各状态使用的本地图片
export const PET_IMAGES = {
  idle: "/images/pet/idling.png",
  hover: "/images/pet/hovering.png",
  thinking: "/images/pet/thinking.png",
};

// 将宠物位置限制在可见范围，窗口小于宠物时保留边缘距离
export function clampPosition(
  left,
  top,
  width,
  height,
  viewportWidth,
  viewportHeight,
) {
  const maximumLeft = Math.max(PET_MARGIN, viewportWidth - width - PET_MARGIN);
  const maximumTop = Math.max(PET_MARGIN, viewportHeight - height - PET_MARGIN);
  return {
    left: Math.max(PET_MARGIN, Math.min(left, maximumLeft)),
    top: Math.max(PET_MARGIN, Math.min(top, maximumTop)),
  };
}

// 每个账号分别保存位置和显示开关，损坏的数据使用默认设置
export function readSettings(userId) {
  const defaults = {
    visible: true,
    horizontal: 1,
    vertical: DEFAULT_VERTICAL_POSITION,
  };
  try {
    const saved = JSON.parse(
      localStorage.getItem("4398-pet-settings-" + userId),
    );
    if (!saved || typeof saved !== "object") {
      return defaults;
    }
    if (typeof saved.visible === "boolean") {
      defaults.visible = saved.visible;
    }
    if (Number.isFinite(saved.horizontal) && Number.isFinite(saved.vertical)) {
      defaults.horizontal = Math.max(0, Math.min(saved.horizontal, 1));
      defaults.vertical = Math.max(0, Math.min(saved.vertical, 1));
      if (saved.horizontal === 1 && saved.vertical === PREVIOUS_VERTICAL_POSITION) {
        defaults.vertical = DEFAULT_VERTICAL_POSITION;
      }
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
