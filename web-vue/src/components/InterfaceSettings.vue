<template>
  <dialog
    ref="dialog"
    class="interface-settings"
    aria-labelledby="settings-title"
  >
    <header class="settings-heading">
      <h2 id="settings-title">界面设置</h2>
      <button
        class="workspace-icon-button"
        type="button"
        aria-label="关闭设置"
        title="关闭设置"
        @click="close"
      >
        <span class="i-ep-close" aria-hidden="true"></span>
      </button>
    </header>
    <form @submit.prevent="close">
      <fieldset class="settings-section">
        <legend>鼠标尾迹</legend>
        <label class="settings-row" for="trail-enabled">
          <span>启用尾迹</span>
          <input
            id="trail-enabled"
            v-model="form.trailEnabled"
            type="checkbox"
            @change="apply"
          />
        </label>
        <label class="settings-range" for="trail-density">
          <span
            >密度
            <output for="trail-density"
              >{{ form.trailDensity }} 个/秒</output
            ></span
          >
          <input
            id="trail-density"
            v-model.number="form.trailDensity"
            type="range"
            :min="density.minimum"
            :max="density.maximum"
            :disabled="!form.trailEnabled"
            @input="apply"
          />
        </label>
        <label class="settings-range" for="trail-size">
          <span
            >大小
            <output for="trail-size">{{ form.trailSize }} 像素</output></span
          >
          <input
            id="trail-size"
            v-model.number="form.trailSize"
            type="range"
            :min="size.minimum"
            :max="size.maximum"
            :disabled="!form.trailEnabled"
            @input="apply"
          />
        </label>
      </fieldset>
      <fieldset class="settings-section">
        <legend>登录背景</legend>
        <label class="settings-row" for="login-motion">
          <span>播放背景动画</span>
          <input
            id="login-motion"
            v-model="form.loginMotion"
            type="checkbox"
            @change="apply"
          />
        </label>
      </fieldset>
      <p class="settings-notice" role="status">{{ notice }}</p>
      <div class="settings-actions">
        <button class="platform-secondary-button" type="button" @click="reset">
          恢复默认
        </button>
        <button class="platform-secondary-button" type="submit">完成</button>
      </div>
    </form>
  </dialog>
</template>

<script>
import { usePreferencesStore } from "../stores/preferences.js";
import * as preferences from "../logic/preferences.js";

export default {
  // 表单持有独立副本，打开时读取最新偏好，控制范围来自共用参数
  data: function settingsState() {
    return {
      form: preferences.defaultPreferences(),
      density: preferences.TRAIL_DENSITY,
      size: preferences.TRAIL_SIZE,
      notice: "设置自动保存在当前浏览器",
    };
  },
  methods: {
    // 打开原生对话框，重复点击入口时保留已打开的窗口和焦点
    open: function open() {
      this.form = preferences.normalizePreferences(
        usePreferencesStore().settings,
      );
      this.notice = "设置自动保存在当前浏览器";
      if (!this.$refs.dialog.open) {
        this.$refs.dialog.showModal();
      }
    },
    // 关闭后由原生对话框将焦点交还打开设置的控件
    close: function close() {
      this.$refs.dialog.close();
    },
    // 应用表单并反馈保存状态，存储受限时当前页面继续采用本次选择
    apply: function apply() {
      const store = usePreferencesStore();
      store.update(this.form);
      this.notice = "已保存";
      if (!store.saved) {
        this.notice = "本次设置已生效，浏览器存储空间暂不可用";
      }
    },
    // 恢复所有界面偏好，表单和实际效果使用同一份默认值
    reset: function reset() {
      this.form = preferences.defaultPreferences();
      this.apply();
    },
  },
};
</script>
