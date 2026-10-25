import { defineStore } from "pinia";
import { userLoginReq } from "../services/userServ";
import { getMenuList } from "../services/mainServ";

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
  state: function () {
    return {
      userInfo: emptyUserInfo(),
      systemConfig: {
        naviList: [],
        showLeftMeun: false,
        leftList: [],
        id: null,
      },
    };
  },
  actions: {
    login: async function (username, password, validateCodeId, validateCode) {
      const result = await userLoginReq(
        username,
        password,
        validateCodeId,
        validateCode
      );
      this.userInfo = {
        loggedIn: true,
        username: result.username,
        perName: result.perName,
        token: result.token,
        id: result.id,
        role: result.role,
      };
    },
    logout: function () {
      this.userInfo = emptyUserInfo();
      this.systemConfig = {
        naviList: [],
        showLeftMeun: false,
        leftList: [],
        id: null,
      };
    },
    setNavi: async function () {
      const menus = await getMenuList();
      if (menus.length === 0) {
        throw new Error("当前账号没有可用菜单");
      }
      this.systemConfig.naviList = menus;
      this.systemConfig.leftList = menus[0].sList;
      this.systemConfig.showLeftMeun = true;
      this.systemConfig.id = menus[0].id;
    },
    // 选中模块菜单，设置左侧菜单数据。
    setLeftList: function (id) {
      for (const menu of this.systemConfig.naviList) {
        if (menu.id === id) {
          this.systemConfig.leftList = menu.sList;
          this.systemConfig.id = id;
          break;
        }
      }
    },
  },
  persist: {
    storage: localStorage,
    paths: ["userInfo", "systemConfig"],
  },
});
