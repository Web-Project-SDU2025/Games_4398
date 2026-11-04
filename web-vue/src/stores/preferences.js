import { defineStore } from "pinia";
import * as preferences from "../logic/preferences.js";

// 获取浏览器存储，隐私模式限制访问时交给读取和保存流程返回结果
function browserStorage() {
  try {
    return window.localStorage;
  } catch (error) {
    return null;
  }
}

export const usePreferencesStore = defineStore("interfacePreferences", {
  // 初次读取浏览器偏好，存储受限时仍可在当前页面调整设置
  state: function preferenceState() {
    return {
      settings: preferences.readPreferences(browserStorage()),
      saved: true,
    };
  },
  actions: {
    // 先应用当前设置再尝试保存，滑块和开关即时生效
    update: function update(settings) {
      this.settings = preferences.normalizePreferences(settings);
      this.saved = preferences.savePreferences(browserStorage(), this.settings);
    },
  },
});
