import { defineStore } from "pinia";
import { gameAddress } from "../logic/gameRuntime.js";

export const useGamePlayerStore = defineStore("gamePlayer", {
  // 只保留当前浏览器中的运行信息，刷新网页后重新加载，账户之间分别运行
  state: function gamePlayerState() {
    return {
      game: null,
      address: "",
      ownerToken: "",
      routePath: "",
      expanded: false,
      editorLaunch: false,
      editorOpen: false,
      editorRequest: 0,
      closing: false,
    };
  },
  actions: {
    // 同一游戏重复打开时返回原实例，其他游戏等当前实例关闭后再启动
    open: function open(game, user, routePath, editor) {
      if (!user.loggedIn || !user.token) {
        return "请先登录";
      }
      if (editor && user.role !== "ROLE_ADMIN") {
        return "仅管理员可用";
      }
      if (this.ownerToken !== user.token) {
        this.clear();
      }
      if (this.closing) {
        return "正在关闭游戏";
      }
      if (this.game) {
        if (this.game.gamePath !== game.gamePath) {
          return "请先关闭当前游戏";
        }
        if (!editor && this.editorLaunch) {
          return "请先关闭关卡编辑器";
        }
        if (editor && !this.editorLaunch) {
          this.editorRequest = this.editorRequest + 1;
        }
      } else {
        const address = gameAddress(game, user.id, editor);
        this.game = {
          id: game.id,
          name: game.name,
          gamePath: game.gamePath,
          capabilities: game.capabilities,
        };
        this.address = address;
        this.ownerToken = user.token;
        this.editorLaunch = editor;
        this.editorOpen = editor;
      }
      this.routePath = routePath;
      this.expanded = true;
      return "";
    },
    // 只隐藏运行画面，iframe 仍留在平台公共层，不随路由移除
    minimise: function minimise() {
      this.expanded = false;
    },
    // 返回原启动页面时恢复画面，地址与运行实例保持原值
    resume: function resume() {
      if (this.game && !this.closing) {
        this.expanded = true;
      }
      return this.routePath;
    },
    // 明确关闭或切换账户后释放状态，新的游戏使用新的运行实例
    clear: function clear() {
      this.game = null;
      this.address = "";
      this.ownerToken = "";
      this.routePath = "";
      this.expanded = false;
      this.editorLaunch = false;
      this.editorOpen = false;
      this.editorRequest = 0;
      this.closing = false;
    },
  },
});
