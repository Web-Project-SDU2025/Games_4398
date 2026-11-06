import { defineStore } from "pinia";
import { musicProgress } from "../tools/music.js";

export const useMusicStore = defineStore("backgroundMusic", {
  // 进度属于当前音轨，播放器由导航创建，页面切换时继续保留
  state: function musicState() {
    return { player: null, position: 0, duration: 0 };
  },
  actions: {
    // 根据音频事件同步位置和时长，未知时长时进度条保持不可用
    updateProgress: function updateProgress(audio) {
      const progress = musicProgress(audio);
      this.position = progress.position;
      this.duration = progress.duration;
    },
    // 设置只发出跳转命令，音轨控制器负责检查范围和加载状态
    seek: function seek(seconds) {
      if (!this.player) {
        return;
      }
      const position = this.player.seek(seconds);
      if (position !== null) {
        this.position = position;
      }
    },
    // 当前播放器卸载时清空状态，旧实例的清理保留新实例
    disconnect: function disconnect(player) {
      if (this.player === player) {
        this.player = null;
        this.position = 0;
        this.duration = 0;
      }
    },
  },
});
