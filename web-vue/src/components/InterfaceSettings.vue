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
        <legend>背景音乐</legend>
        <label class="settings-row" for="music-enabled">
          <span>播放音乐</span>
          <input
            id="music-enabled"
            v-model="form.musicEnabled"
            type="checkbox"
            @change="apply"
          />
        </label>
        <label class="settings-range" for="music-volume">
          <span
            >音量
            <output for="music-volume">{{ form.musicVolume }}%</output></span
          >
          <input
            id="music-volume"
            v-model.number="form.musicVolume"
            type="range"
            :min="volume.minimum"
            :max="volume.maximum"
            :disabled="!form.musicEnabled"
            @input="apply"
          />
        </label>
        <label class="settings-range" for="music-progress">
          <span>
            进度
            <output for="music-progress"
              >{{ formatMusicTime(music.position) }} /
              {{ formatMusicTime(music.duration) }}</output
            >
          </span>
          <input
            id="music-progress"
            type="range"
            min="0"
            :max="music.duration"
            :step="seekStep"
            :value="music.position"
            :disabled="music.duration <= 0"
            :aria-valuetext="formatMusicTime(music.position)"
            @input="seekMusic"
          />
        </label>
      </fieldset>
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
        <label class="settings-range" for="login-motion-speed">
          <span>
            动画速度
            <output for="login-motion-speed"
              >{{ form.loginMotionSpeed }}×</output
            >
          </span>
          <input
            id="login-motion-speed"
            v-model.number="form.loginMotionSpeed"
            type="range"
            :min="motionSpeed.minimum"
            :max="motionSpeed.maximum"
            :disabled="!form.loginMotion"
            @input="apply"
          />
        </label>
      </fieldset>
      <span v-toast="notice"></span>
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
import { useMusicStore } from "../stores/music.js";
import { musicTime, MUSIC_SEEK_STEP } from "../tools/music.js";

export default {
  // 表单持有独立副本，打开时读取最新偏好，控制范围来自共用参数
  data: function settingsState() {
    return {
      form: preferences.defaultPreferences(),
      density: preferences.TRAIL_DENSITY,
      size: preferences.TRAIL_SIZE,
      volume: preferences.MUSIC_VOLUME,
      motionSpeed: preferences.LOGIN_MOTION_SPEED,
      seekStep: MUSIC_SEEK_STEP,
      notice: "",
    };
  },
  computed: {
    // 位置与时长来自实际音轨，打开设置后直接显示当前播放进度
    music: function music() {
      return useMusicStore();
    },
  },
  methods: {
    // 鼠标拖动和键盘调整共用跳转入口，停止播放时也可选择下一次播放位置
    seekMusic: function seekMusic(event) {
      this.music.seek(Number(event.target.value));
    },
    formatMusicTime: musicTime,
    // 打开原生对话框，重复点击入口时保留已打开的窗口和焦点
    open: function open() {
      this.form = preferences.normalizePreferences(
        usePreferencesStore().settings,
      );
      this.notice = "";
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
      this.notice = "";
      if (!store.saved) {
        this.notice = "设置保存失败";
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
