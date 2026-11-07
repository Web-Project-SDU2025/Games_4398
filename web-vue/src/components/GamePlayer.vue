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
      <h3 id="player-title">{{ session.game.name }}</h3>
      <button
        v-if="session.game.capabilities.editor"
        type="button"
        :title="editorTitle"
        :disabled="!admin || session.closing"
        class="game-editor-button"
        @click="openEditor"
      >
        关卡编辑器
      </button>
      <button
        type="button"
        title="返回游戏列表"
        aria-label="返回游戏列表"
        @click="minimise"
      >
        <span class="i-ep-arrow-left" aria-hidden="true"></span>
      </button>
      <button type="button" title="全屏" aria-label="全屏" @click="fullscreen">
        <span class="i-ep-full-screen" aria-hidden="true"></span>
      </button>
      <button
        type="button"
        :title="closeLabel"
        :aria-label="closeLabel"
        :disabled="session.closing"
        @click="close"
      >
        <span class="i-ep-close" aria-hidden="true"></span>
      </button>
    </header>
    <span v-toast="notice"></span>
    <iframe
      ref="frame"
      :src="session.address"
      :title="session.game.name"
      allow="fullscreen"
      referrerpolicy="no-referrer"
      @load="frameLoaded"
    ></iframe>
  </section>
</template>

<script>
import { useGamePlayerStore } from "../stores/gamePlayer.js";
import { useAppStore } from "../stores/app.js";
import { useWalletStore } from "../stores/wallet.js";
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
    return { notice: "", closeComplete: null, closeTimer: null, editorSent: 0 };
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
      if (this.session.editorOpen) {
        return "关闭编辑器";
      }
      return "关闭游戏";
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
    "session.address": function addressChanged() {
      this.notice = "";
      this.editorSent = 0;
    },
    // 恢复画面后移回焦点，隐藏时不干扰正在使用的页面
    visible: function visibilityChanged(value) {
      if (value) {
        this.$nextTick(this.focusPlayer);
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
  },
  // 退出或切号会卸载当前账户的运行器，旧消息保持与新账户隔离
  beforeUnmount: function disposePlayer() {
    window.removeEventListener("message", this.receiveMessage);
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
    // 返回列表只改变显示状态，顶栏返回游戏按钮仍可恢复当前画面
    minimise: function minimise() {
      this.session.minimise();
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
    // 关闭按钮区分编辑器和整局游戏，返回列表不走此流程
    close: function close() {
      if (this.session.editorOpen && !this.session.editorLaunch) {
        this.sendCommand(PLAYER_MESSAGES.closeEditor);
        return;
      }
      return this.closeGame();
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
        this.session.clear();
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
  },
};
</script>
