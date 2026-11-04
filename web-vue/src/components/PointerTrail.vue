<template>
  <div class="pointer-trail" aria-hidden="true">
    <span
      v-for="particle in particles"
      :key="particle.id"
      class="pointer-trail-pixel"
      :style="particle.style"
      @animationend="removeParticle(particle.id)"
    ></span>
  </div>
</template>

<script>
import { markRaw } from "vue";
import { usePreferencesStore } from "../stores/preferences.js";
import { TRAIL_LIFETIME, TRAIL_PARTICLE_LIMIT } from "../logic/preferences.js";

export default {
  // 仅保存当前显示的像素和采样状态，数量上限控制长时间移动的占用
  data: function trailState() {
    return { particles: [], nextId: 0, lastTime: 0, media: null };
  },
  computed: {
    // 共享设置立即影响尾迹，切号时沿用当前浏览器的界面偏好
    settings: function trailSettings() {
      return usePreferencesStore().settings;
    },
  },
  watch: {
    // 开关、密度或大小改变时清空旧像素，新像素直接采用新参数
    settings: function settingsChanged() {
      this.clear();
    },
    // 页面切换时清理尾迹，页面之间保持独立的移动起点
    "$route.path": function routeChanged() {
      this.clear();
    },
  },
  // 登录页与工作区共用鼠标监听，媒体设置变化时同步清理
  mounted: function startTrail() {
    this.media = markRaw(
      window.matchMedia("(prefers-reduced-motion: reduce), (pointer: coarse)"),
    );
    this.media.addEventListener("change", this.clear);
    window.addEventListener("pointermove", this.move, { passive: true });
    window.addEventListener("blur", this.clear);
    document.addEventListener("visibilitychange", this.clear);
    document.addEventListener("pointerleave", this.clear);
  },
  // 框架卸载时成对移除监听，重新挂载时创建新的实例
  beforeUnmount: function stopTrail() {
    this.media.removeEventListener("change", this.clear);
    window.removeEventListener("pointermove", this.move);
    window.removeEventListener("blur", this.clear);
    document.removeEventListener("visibilitychange", this.clear);
    document.removeEventListener("pointerleave", this.clear);
  },
  methods: {
    // 按每秒密度采样鼠标位置，触摸、拖动和减少动态效果时保留原操作
    move: function move(event) {
      if (
        !this.settings.trailEnabled ||
        this.media.matches ||
        event.pointerType !== "mouse" ||
        event.buttons !== 0
      ) {
        return;
      }
      if (document.querySelector("dialog[open]")) {
        return;
      }
      const now = performance.now();
      const interval = 1000 / this.settings.trailDensity;
      if (now - this.lastTime < interval) {
        return;
      }
      this.lastTime = now;
      this.nextId = this.nextId + 1;
      const size = this.settings.trailSize;
      this.particles.push({
        id: this.nextId,
        style: {
          left: event.clientX - size / 2 + "px",
          top: event.clientY - size / 2 + "px",
          width: size + "px",
          height: size + "px",
          animationDuration: TRAIL_LIFETIME + "ms",
        },
      });
      if (this.particles.length > TRAIL_PARTICLE_LIMIT) {
        this.particles.shift();
      }
    },
    // 动画结束后移除对应像素，提前清空后收到的结束事件直接完成
    removeParticle: function removeParticle(id) {
      for (let index = 0; index < this.particles.length; index = index + 1) {
        if (this.particles[index].id === id) {
          this.particles.splice(index, 1);
          return;
        }
      }
    },
    // 清理残留和采样时间，切页、隐藏窗口及关闭效果共用此流程
    clear: function clear() {
      this.particles = [];
      this.lastTime = 0;
    },
  },
};
</script>
