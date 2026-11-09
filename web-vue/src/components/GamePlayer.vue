<template>
  <section
    v-if="session.address && session.ownerToken === ownerToken"
    v-show="visible"
    ref="player"
    class="game-player-panel"
    aria-labelledby="player-title"
    tabindex="-1"
  >
    <header class="game-player-heading">
      <button
        type="button"
        class="player-back"
        title="返回大厅，保留当前游戏"
        :disabled="session.closing || confirmingClose"
        @click="minimise"
      >
        <span class="i-ep-arrow-left" aria-hidden="true"></span>
        返回大厅
      </button>
      <h3 id="player-title">{{ session.game.name }}</h3>
      <div class="player-actions">
        <button
          v-if="session.game.capabilities.editor"
          type="button"
          :title="editorTitle"
          :disabled="!admin || session.closing || confirmingClose"
          @click="openEditor"
        >
          关卡编辑器
        </button>
        <button
          type="button"
          :title="fullscreenLabel"
          :aria-label="fullscreenLabel"
          @click="fullscreen"
        >
          <span class="i-ep-full-screen" aria-hidden="true"></span>
        </button>
        <button
          type="button"
          class="player-end"
          :disabled="session.closing || confirmingClose"
          @click="close"
        >
          {{ closeLabel }}
        </button>
      </div>
    </header>
    <span v-toast="notice"></span>
    <div class="player-stage">
      <iframe
        ref="frame"
        :src="session.address"
        :title="session.game.name"
        :aria-busy="frameLoading || session.closing"
        :inert="session.closing"
        allow="fullscreen"
        referrerpolicy="no-referrer"
        @load="frameLoaded"
      ></iframe>
      <p v-if="session.closing" class="player-stage-status" role="status">
        正在结束游戏并保存记录…
      </p>
      <p v-else-if="frameLoading" class="player-stage-status" role="status">
        正在打开{{ session.game.name }}…
      </p>
    </div>
  </section>
</template>

<script>
import { useGamePlayerStore } from "../stores/gamePlayer.js";
import { useAppStore } from "../stores/app.js";
import { useWalletStore } from "../stores/wallet.js";
import { messageConform } from "../tools/messageBox.js";
import {
  PLAYER_MESSAGES,
  playerMessage,
} from "../../games/runtime/web/game-session.mjs";
import { GAMEPLAY_MESSAGES } from "../../games/runtime/web/game-play.mjs";

// 明确关闭时等待游戏保存，超时后由最后一次心跳保留已经记录的时长
const CLOSE_WAIT_MILLISECONDS = 2500;

export default {
  props: {
    ownerToken: { type: String, required: true },
    admin: Boolean,
  },
  // 运行窗口位于导航公共层，所有页面共用同一份 iframe 与消息监听
  data: function playerState() {
    return {
      notice: "",
      closeComplete: null,
      closeTimer: null,
      editorSent: 0,
      frameLoading: false,
      confirmingClose: false,
      fullscreenActive: false,
    };
  },
  computed: {
    // 游戏入口与显示状态由启动器和顶栏共用仓库维护
    session: function session() {
      return useGamePlayerStore();
    },
    // 页面切换仅改变可见性，游戏加载期间也保留同一个 iframe
    visible: function visible() {
      return Boolean(
        this.session.game &&
          this.session.ownerToken === this.ownerToken &&
          this.session.expanded &&
          this.session.routePath === this.$route.path,
      );
    },
    // 非管理员保留编辑器按钮和权限提示
    editorTitle: function editorTitle() {
      if (!this.admin) {
        return "仅管理员可用";
      }
      return "关卡编辑器";
    },
    // 从游戏打开编辑器时先关闭编辑器，单独启动的编辑器关闭后释放运行环境
    closeLabel: function closeLabel() {
      if (this.session.closing) {
        return "正在结束…";
      }
      if (this.session.editorOpen) {
        return "关闭编辑器";
      }
      return "结束游戏";
    },
    // 浏览器退出全屏后同步按钮名称
    fullscreenLabel: function fullscreenLabel() {
      if (this.fullscreenActive) {
        return "退出全屏";
      }
      return "全屏";
    },
  },
  watch: {
    // 返回游戏所在页面时自动显示原画面，页面缓存是否仍存在均不影响运行环境
    "$route.path": function routeChanged(path) {
      if (path === this.session.routePath) {
        this.session.resume();
      }
    },
    // 同一游戏的编辑器请求使用消息打开，游戏包和素材继续复用
    "session.editorRequest": function editorRequested() {
      this.deliverEditor();
    },
    // 关闭后重新开始时清理旧提示和消息序号
    "session.address": function addressChanged(address) {
      this.notice = "";
      this.editorSent = 0;
      this.frameLoading = Boolean(address);
    },
    // 恢复画面后移回焦点，隐藏时退出播放器开启的平台全屏
    visible: function visibilityChanged(value) {
      if (value) {
        this.$nextTick(this.focusPlayer);
      } else {
        return this.exitPlayerFullscreen();
      }
    },
    // 角色被撤销时关闭已打开的编辑器，正式游戏继续保留
    admin: function adminChanged(value) {
      if (!value && this.session.editorOpen) {
        this.sendCommand(PLAYER_MESSAGES.closeEditor);
      }
    },
  },
  // 首次装配时监听消息，页面路由切换不会卸载本组件
  mounted: function mountPlayer() {
    if (this.session.ownerToken !== this.ownerToken) {
      this.session.clear();
    }
    window.addEventListener("message", this.receiveMessage);
    document.addEventListener("fullscreenchange", this.syncFullscreen);
    this.syncFullscreen();
  },
  // 退出或切号会卸载当前账户的运行器，旧消息保持与新账户隔离
  beforeUnmount: function disposePlayer() {
    window.removeEventListener("message", this.receiveMessage);
    document.removeEventListener("fullscreenchange", this.syncFullscreen);
    this.exitPlayerFullscreen();
    this.sendCommand(PLAYER_MESSAGES.stopGame);
    if (this.closeComplete) {
      this.closeComplete();
    }
    if (this.session.ownerToken === this.ownerToken) {
      this.session.clear();
    }
  },
  methods: {
    // 指令只发送给当前同源 iframe，尚未挂载时保持请求状态供加载完成后处理
    sendCommand: function sendCommand(type) {
      const frame = this.$refs.frame;
      if (!frame || !frame.contentWindow) {
        return false;
      }
      frame.contentWindow.postMessage({ type: type }, location.origin);
      return true;
    },
    // 编辑器请求在 iframe 初次加载期间也可保留，完成加载后再次发送
    deliverEditor: function deliverEditor() {
      if (!this.admin || this.session.editorRequest <= this.editorSent) {
        return;
      }
      if (this.sendCommand(PLAYER_MESSAGES.openEditor)) {
        this.editorSent = this.session.editorRequest;
      }
    },
    // 文件页面加载完成时同步未送达的编辑器请求，游戏实例本身保持不变
    frameLoaded: function frameLoaded() {
      this.editorSent = 0;
      this.frameLoading = false;
      this.deliverEditor();
      if (this.visible) {
        this.focusPlayer();
      }
    },
    // 接收记录、奖励和编辑器消息前同时核对账户、源窗口与来源地址
    receiveMessage: function receiveMessage(event) {
      const frame = this.$refs.frame;
      const user = useAppStore().userInfo;
      if (
        !frame ||
        user.token !== this.ownerToken ||
        event.origin !== location.origin ||
        event.source !== frame.contentWindow ||
        !event.data ||
        typeof event.data !== "object"
      ) {
        return;
      }
      const type = event.data.type;
      if (type === PLAYER_MESSAGES.gameStopped) {
        if (this.closeComplete) {
          this.closeComplete();
        }
        return;
      }
      if (type === GAMEPLAY_MESSAGES.recordsChanged) {
        useAppStore().dataChanged("records");
        return;
      }
      if (type === GAMEPLAY_MESSAGES.walletChanged) {
        this.notice = event.data.message;
        useWalletStore().refresh();
        return;
      }
      if (type === GAMEPLAY_MESSAGES.notice) {
        this.notice = event.data.message;
        return;
      }
      if (type === GAMEPLAY_MESSAGES.accessRevoked) {
        this.notice = event.data.message;
        useAppStore().dataChanged("games");
        useAppStore().dataChanged("records");
        useWalletStore().refresh();
        this.closeGame();
        return;
      }
      const message = playerMessage(
        event,
        frame.contentWindow,
        location.origin,
      );
      if (message && message.type === PLAYER_MESSAGES.editorState) {
        this.session.editorOpen = message.open;
        if (
          !message.open &&
          this.session.editorLaunch &&
          !this.session.closing
        ) {
          this.closeGame();
        }
      }
    },
    // 开启编辑器复用当前运行实例，已打开时由 Java 将原窗口移至前台
    openEditor: function openEditor() {
      if (this.admin && !this.session.closing) {
        this.session.editorRequest = this.session.editorRequest + 1;
      }
    },
    // 回到大厅时保留运行窗口，顶栏仍可恢复原画面
    minimise: async function minimise() {
      this.session.minimise();
      await this.$router.push("/MainPage");
      this.$nextTick(this.focusWorkspace);
    },
    // 退出专注视图后把键盘焦点移回页面
    focusWorkspace: function focusWorkspace() {
      const heading = document.querySelector(
        ".workspace-page h2, .workspace-page h1",
      );
      if (heading) {
        heading.setAttribute("tabindex", "-1");
        heading.focus();
      }
    },
    // 画面恢复后聚焦运行区，保持顶栏和侧栏可继续访问
    focusPlayer: function focusPlayer() {
      if (this.$refs.player) {
        this.$refs.player.focus();
      }
    },
    // 关闭编辑器与结束游戏分别处理，结束整局前说明进度和奖励影响
    close: async function close() {
      if (this.session.editorOpen) {
        if (!this.session.editorLaunch) {
          this.sendCommand(PLAYER_MESSAGES.closeEditor);
          return;
        }
        return this.closeGame();
      }
      if (this.confirmingClose || this.session.closing) {
        return;
      }
      const address = this.session.address;
      let message = "结束后需要重新启动游戏。";
      if (this.session.game.id === "billiards") {
        message = "结束游戏？如果本局还未分出胜负，本局成就不会结算。";
      }
      this.confirmingClose = true;
      const shell = this.$refs.player.closest(".platform-shell");
      const confirmed = await messageConform(message, shell);
      this.confirmingClose = false;
      if (confirmed && this.session.address === address &&
          this.session.ownerToken === this.ownerToken) {
        return this.closeGame();
      }
    },
    // 保存结束事件后才删除 iframe，等待期间阻止重复关闭与启动
    closeGame: async function closeGame() {
      if (!this.session.address || this.session.closing) {
        return;
      }
      const address = this.session.address;
      this.session.closing = true;
      await this.waitForRecord();
      if (
        this.session.address === address &&
        this.session.ownerToken === this.ownerToken
      ) {
        const wasVisible = this.visible;
        this.session.clear();
        if (wasVisible) {
          this.$nextTick(this.focusWorkspace);
        }
      }
      this.notice = "";
    },
    // 游戏确认保存与超时共用完成入口，卸载时也会清理定时器并结束等待
    waitForRecord: function waitForRecord() {
      const player = this;
      // 等待消息返回，超时后仍保留服务器最近一次已保存的计时
      function wait(resolve) {
        // 完成一次关闭等待，随后释放回调与定时器
        function complete() {
          clearTimeout(player.closeTimer);
          player.closeTimer = null;
          player.closeComplete = null;
          resolve();
        }
        player.closeComplete = complete;
        player.closeTimer = setTimeout(complete, CLOSE_WAIT_MILLISECONDS);
        player.sendCommand(PLAYER_MESSAGES.stopGame);
      }
      return new Promise(wait);
    },
    // 平台整体切换全屏，游戏继续沿用导航和设置入口
    fullscreen: async function fullscreen() {
      try {
        if (document.fullscreenElement) {
          await document.exitFullscreen();
        } else {
          const shell = this.$refs.player.closest(".platform-shell");
          await shell.requestFullscreen();
        }
      } catch (error) {
        this.notice = "全屏操作失败：" + error.message;
      }
    },
    // 系统手势和工具栏都会触发同一个全屏状态变化
    syncFullscreen: function syncFullscreen() {
      this.fullscreenActive = Boolean(document.fullscreenElement);
    },
    // 全屏请求被浏览器拒绝时仍可正常离开游戏界面
    exitPlayerFullscreen: async function exitPlayerFullscreen() {
      const player = this.$refs.player;
      if (player && document.fullscreenElement &&
          document.fullscreenElement === player.closest(".platform-shell")) {
        try {
          await document.exitFullscreen();
        } catch {
          // 浏览器仍可通过系统全屏快捷键退出
        }
      }
    },
  },
};
</script>

<style scoped>
.game-player-panel {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--color-paper);
  color: var(--color-ink);
}
.game-player-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm) var(--space-md);
  padding: var(--space-sm) clamp(12px, 2vw, 32px);
  border-bottom: 1px solid var(--color-rule);
  background: var(--color-header);
}
.game-player-heading h3 {
  min-width: 0;
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.player-actions {
  display: flex;
  gap: var(--space-sm);
  margin-left: auto;
}
.game-player-heading button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-control);
  color: var(--color-muted);
  background: transparent;
  font-size: 13px;
}
.game-player-heading button:hover:not(:disabled) {
  border-color: var(--color-rule);
  color: var(--color-ink);
  background: var(--color-paper-2);
}
.game-player-heading button span {
  display: inline-block;
  width: 16px;
  height: 16px;
}
.game-player-heading .player-back {
  padding-left: 0;
  color: var(--color-play-action, var(--color-accent));
}
.game-player-heading .player-end {
  border-color: var(--color-rule);
}
.player-stage {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
}
.player-stage iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  background: var(--color-paper);
}
.player-stage-status {
  position: absolute;
  top: var(--space-md);
  left: 50%;
  width: max-content;
  max-width: 80%;
  transform: translateX(-50%);
  margin: 0;
  padding: 10px 14px;
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-control);
  background: var(--color-paper-2);
  color: var(--color-ink);
}
</style>
