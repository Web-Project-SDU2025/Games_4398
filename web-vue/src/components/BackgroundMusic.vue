<template>
  <div class="background-music">
    <button
      class="workspace-icon-button music-toggle"
      :class="{
        'is-playing': status === 'playing',
        'is-muted': !settings.musicEnabled || settings.musicVolume === 0,
        'is-waiting': status === 'waiting',
      }"
      type="button"
      aria-label="背景音乐"
      :aria-pressed="settings.musicEnabled"
      :title="buttonTitle"
      data-music-toggle
      @click="toggle"
    >
      <span class="music-note" aria-hidden="true"></span>
    </button>
    <audio
      ref="audio"
      :src="source"
      loop
      preload="metadata"
      hidden
      @error="failed"
      @loadedmetadata="updateProgress"
      @durationchange="updateProgress"
      @timeupdate="updateProgress"
      @seeked="updateProgress"
      @emptied="updateProgress"
    ></audio>
  </div>
</template>

<script>
import { markRaw } from "vue";
import { usePreferencesStore } from "../stores/preferences.js";
import { normalizePreferences } from "../logic/preferences.js";
import { createMusicPlayer, MUSIC_SOURCE } from "../tools/music.js";
import { useMusicStore } from "../stores/music.js";

export default {
  // 播放器随共用导航保留，页面切换只更新路由内容，音轨继续使用当前位置
  data: function musicState() {
    return { source: MUSIC_SOURCE, player: null, status: "paused" };
  },
  computed: {
    // 音乐开关与音量来自共用偏好，登录前后都读取当前浏览器选择
    settings: function musicSettings() {
      return usePreferencesStore().settings;
    },
    // 根据播放结果提供操作提示，浏览器拦截时将音符按钮作为明确播放入口
    buttonTitle: function buttonTitle() {
      if (!this.settings.musicEnabled) {
        return "播放背景音乐";
      }
      if (this.status === "error") {
        return "音乐加载失败，点击重试";
      }
      if (this.status === "waiting") {
        return "点击播放背景音乐";
      }
      if (this.settings.musicVolume === 0) {
        return "背景音乐已开启，当前音量为 0";
      }
      return "关闭背景音乐";
    },
  },
  watch: {
    // 设置保存为新对象后同步到播放器，调整其他偏好时继续当前音轨
    settings: function preferencesChanged() {
      this.apply();
    },
  },
  // 首次尝试默认播放，浏览器要求交互时由具名输入监听重试
  mounted: function startMusic() {
    this.player = markRaw(createMusicPlayer(this.$refs.audio, this.report));
    useMusicStore().player = this.player;
    this.updateProgress();
    window.addEventListener("pointerdown", this.unlock, true);
    window.addEventListener("keydown", this.unlock, true);
    this.apply();
  },
  // 框架退出时停止音乐并移除全局监听，重新进入时创建新的播放器
  beforeUnmount: function stopMusic() {
    window.removeEventListener("pointerdown", this.unlock, true);
    window.removeEventListener("keydown", this.unlock, true);
    this.player.dispose();
    useMusicStore().disconnect(this.player);
  },
  methods: {
    // 音频加载、播放和跳转都同步同一份进度，设置窗口随时读取
    updateProgress: function updateProgress() {
      useMusicStore().updateProgress(this.$refs.audio);
    },
    // 播放结果只改变图标状态，用户的启用偏好继续由设置维护
    report: function report(status) {
      this.status = status;
    },
    // 将最新偏好交给音轨控制器，首次挂载前的设置变化由挂载流程应用
    apply: async function apply() {
      if (this.player) {
        await this.player.configure(this.settings);
      }
    },
    // 普通点击和按键解锁播放，音乐控件自己的事件交给对应开关处理
    unlock: function unlock(event) {
      if (event.target instanceof Element) {
        if (
          event.target.closest(
            "[data-music-toggle], #music-enabled, #music-volume, #music-progress",
          )
        ) {
          return;
        }
      }
      this.player.retry();
    },
    // 等待播放时点击音符重试，正常开关时复制完整偏好以保留其他设置
    toggle: async function toggle() {
      if (
        this.settings.musicEnabled &&
        (this.status === "waiting" || this.status === "error")
      ) {
        if (this.status === "error") {
          this.$refs.audio.load();
        }
        await this.player.retry();
        return;
      }
      const store = usePreferencesStore();
      const settings = normalizePreferences(store.settings);
      settings.musicEnabled = !settings.musicEnabled;
      store.update(settings);
    },
    // 音频文件读取失败时提供重试提示，关闭选择保留为暂停状态
    failed: function failed() {
      if (this.settings.musicEnabled) {
        this.status = "error";
      }
    },
  },
};
</script>
