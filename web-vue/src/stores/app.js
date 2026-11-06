import { defineStore } from "pinia";
import { userLoginReq, switchAccount } from "../services/userServ";
import { getMenuList } from "../services/systemServ.js";
import { getProfile } from "../services/gameUserServ.js";

// 登录和切号共用账户结构，按服务器结果填写身份与令牌
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
      profile: null,
      systemConfig: {
        naviList: [],
      },
      gameRevision: 0,
      recordRevision: 0,
    };
  },
  actions: {
    // 用后端当前身份更新缓存，切号后丢弃旧令牌对应的响应
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
      this.profile = profile;
      return true;
    },
    // 登录成功后保存服务器返回的账户和令牌
    login: async function login(
      username,
      password,
      validateCodeId,
      validateCode,
    ) {
      const result = await userLoginReq(
        username,
        password,
        validateCodeId,
        validateCode,
      );
      this.userInfo = sessionInfo(result);
      this.profile = null;
      this.gameRevision = 0;
      this.recordRevision = 0;
    },
    // 目标账户的菜单和个人资料都读取成功后再完成切换
    switchAccount: async function changeAccount(username) {
      const previousUserInfo = this.userInfo;
      const previousProfile = this.profile;
      const previousSystemConfig = this.systemConfig;
      const previousGameRevision = this.gameRevision;
      const previousRecordRevision = this.recordRevision;
      const result = await switchAccount(username, this.userInfo.token);
      this.systemConfig = { naviList: result.menus };
      this.userInfo = sessionInfo(result.session);
      this.profile = null;
      this.gameRevision = 0;
      this.recordRevision = 0;
      try {
        await this.refreshIdentity();
      } catch (error) {
        if (this.userInfo.token === result.session.token) {
          this.userInfo = previousUserInfo;
          this.profile = previousProfile;
          this.systemConfig = previousSystemConfig;
          this.gameRevision = previousGameRevision;
          this.recordRevision = previousRecordRevision;
        }
        throw error;
      }
    },
    // 退出时清空身份、菜单和数据版本，下一账户重新初始化
    logout: function logout() {
      this.userInfo = emptyUserInfo();
      this.profile = null;
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
