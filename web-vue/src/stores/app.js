import { defineStore } from "pinia";
import { userLoginReq, switchAccount } from "../services/userServ";
import { getMenuList } from "../services/mainServ";
import { getProfile } from "../services/gameUserServ.js";

// 登录和切号使用同一种账户结构，避免遗漏身份或令牌字段
function sessionInfo(result) {
  return {
    loggedIn: true,
    username: result.username,
    perName: result.perName,
    token: result.token,
    id: result.id,
    role: result.role,
  };
}

// 创建未登录状态，退出时使用新对象清空账户资料
function emptyUserInfo() {
  return {
    loggedIn: false,
    username: "",
    perName: "",
    token: "",
    id: 0,
    role: "",
  };
}

export const useAppStore = defineStore("gameApp", {
  // 只保存跨页面共用的账户和菜单，表单数据留在各自页面
  state: function appState() {
    return {
      userInfo: emptyUserInfo(),
      systemConfig: {
        naviList: [],
      },
      gameRevision: 0,
      recordRevision: 0,
    };
  },
  actions: {
    // 读取后端当前身份，修正旧浏览器缓存；角色不能从菜单标题或下拉文字猜测
    refreshIdentity: async function refreshIdentity() {
      const token = this.userInfo.token;
      const result = await getProfile();
      if (token !== this.userInfo.token) {
        return false;
      }
      if (result.code !== 0 || !result.data) {
        throw new Error("无法读取当前账户身份");
      }
      const profile = result.data;
      if (
        profile.username !== this.userInfo.username ||
        (profile.role !== "ROLE_ADMIN" && profile.role !== "ROLE_GAME_USER")
      ) {
        throw new Error("当前账户身份不完整，请重新登录");
      }
      this.userInfo.role = profile.role;
      this.userInfo.id = profile.userId;
      this.userInfo.perName = profile.name;
      return true;
    },
    // 登录成功后保存服务器返回的账户和令牌
    login: async function login(
      username,
      password,
      validateCodeId,
      validateCode,
      adminOnly
    ) {
      const result = await userLoginReq(
        username,
        password,
        validateCodeId,
        validateCode,
        adminOnly
      );
      this.userInfo = sessionInfo(result);
      this.gameRevision = 0;
      this.recordRevision = 0;
    },
    // 目标凭证和菜单都读取成功后再替换身份，网络失败时仍保留当前账号
    switchAccount: async function changeAccount(username) {
      const result = await switchAccount(username, this.userInfo.token);
      this.systemConfig = { naviList: result.menus };
      this.userInfo = sessionInfo(result.session);
      this.gameRevision = 0;
      this.recordRevision = 0;
    },
    // 退出时同时清空菜单，避免不同账户共用权限信息
    logout: function logout() {
      this.userInfo = emptyUserInfo();
      this.systemConfig = {
        naviList: [],
      };
      this.gameRevision = 0;
      this.recordRevision = 0;
    },
    // 写入成功后递增版本，缓存列表只有落后于这个版本时才重新请求
    dataChanged: function dataChanged(kind) {
      if (kind === "games") {
        this.gameRevision = this.gameRevision + 1;
      } else if (kind === "records") {
        this.recordRevision = this.recordRevision + 1;
      }
    },
    // 加载当前角色的菜单，前端不自行授予权限
    setNavi: async function setNavi() {
      const refreshed = await this.refreshIdentity();
      if (!refreshed) {
        return;
      }
      const token = this.userInfo.token;
      const menus = await getMenuList();
      if (token !== this.userInfo.token) {
        return;
      }
      if (menus.length === 0) {
        throw new Error("当前账号没有可用菜单");
      }
      this.systemConfig.naviList = menus;
    },
  },
  persist: {
    storage: localStorage,
    paths: ["userInfo", "systemConfig"],
  },
});
