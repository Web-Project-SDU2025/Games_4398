// 浏览器保存界面偏好的键名，同一浏览器中的账户共用界面设置
export const PREFERENCES_KEY = "4398-interface-preferences";
// 尾迹密度表示每秒最多产生的像素数量
export const TRAIL_DENSITY = { minimum: 5, maximum: 80, defaultValue: 40 };
// 尾迹大小以像素为单位，限制范围便于保持页面清晰
export const TRAIL_SIZE = { minimum: 3, maximum: 24, defaultValue: 10 };
// 单个像素的保留时间与页面中的像素数量上限
export const TRAIL_LIFETIME = 450;
export const TRAIL_PARTICLE_LIMIT = 48;

// 返回一份独立的默认设置，恢复默认时同时重置各个选项
export function defaultPreferences() {
  return {
    trailEnabled: true,
    trailDensity: TRAIL_DENSITY.defaultValue,
    trailSize: TRAIL_SIZE.defaultValue,
    loginMotion: true,
  };
}

// 将数字调整到整数范围，旧数据中的空值和非数字使用默认值
function normalizeNumber(value, range) {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return range.defaultValue;
  }
  const rounded = Math.round(value);
  return Math.max(range.minimum, Math.min(range.maximum, rounded));
}

// 按已知字段整理偏好，损坏或旧版本数据使用对应的默认选项
export function normalizePreferences(saved) {
  const settings = defaultPreferences();
  if (!saved || typeof saved !== "object") {
    return settings;
  }
  if (typeof saved.trailEnabled === "boolean") {
    settings.trailEnabled = saved.trailEnabled;
  }
  if (typeof saved.loginMotion === "boolean") {
    settings.loginMotion = saved.loginMotion;
  }
  settings.trailDensity = normalizeNumber(saved.trailDensity, TRAIL_DENSITY);
  settings.trailSize = normalizeNumber(saved.trailSize, TRAIL_SIZE);
  return settings;
}

// 从指定存储读取偏好，浏览器限制存储或内容损坏时返回默认设置
export function readPreferences(storage) {
  try {
    const saved = JSON.parse(storage.getItem(PREFERENCES_KEY));
    return normalizePreferences(saved);
  } catch (error) {
    return defaultPreferences();
  }
}

// 保存规范化后的偏好，返回保存结果供界面显示本次设置的保存状态
export function savePreferences(storage, settings) {
  try {
    const normalized = normalizePreferences(settings);
    storage.setItem(PREFERENCES_KEY, JSON.stringify(normalized));
    return true;
  } catch (error) {
    return false;
  }
}
