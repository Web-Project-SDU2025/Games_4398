import { createRouter, createWebHistory } from "vue-router";
import { useAppStore } from "../stores/app";
import { ElMessage } from "element-plus";

// 游戏列表、游戏库和收藏共用一个页面，打开时才读取组件
function loadGameList() {
  return import("../views/game/GameList.vue");
}

// 路由表，游戏列表、游戏库和收藏复用同一个页面
const routes = [
  { path: "/", redirect: "/Login" },
  {
    path: "/Login",
    // 打开对应页面时再加载组件，减少启动时的等待
    component: function loadLogin() {
      return import("../Login.vue");
    },
  },
  {
    path: "/MainPage",
    // 打开对应页面时再加载组件，减少启动时的等待
    component: function loadHome() {
      return import("../views/MainPage.vue");
    },
  },
  {
    path: "/Password",
    // 打开对应页面时再加载组件，减少启动时的等待
    component: function loadPassword() {
      return import("../views/info/Password.vue");
    },
  },
  {
    path: "/MenuManage",
    meta: { adminOnly: true },
    // 打开对应页面时再加载组件，减少启动时的等待
    component: function loadMenu() {
      return import("../views/system/MenuManage.vue");
    },
  },
  {
    path: "/DictionaryManage",
    meta: { adminOnly: true },
    // 打开对应页面时再加载组件，减少启动时的等待
    component: function loadDictionary() {
      return import("../views/system/DictionaryManage.vue");
    },
  },
  {
    path: "/GameCatalog",
    // 打开对应页面时再加载组件，减少启动时的等待
    component: loadGameList,
    props: { mode: "catalog", title: "游戏列表" },
  },
  {
    path: "/GameLibrary",
    // 打开对应页面时再加载组件，减少启动时的等待
    component: loadGameList,
    props: { mode: "library", title: "我的游戏库" },
  },
  {
    path: "/GameFavorites",
    // 打开对应页面时再加载组件，减少启动时的等待
    component: loadGameList,
    props: { mode: "favorites", title: "我的收藏" },
  },
  {
    path: "/GameRecords",
    // 打开对应页面时再加载组件，减少启动时的等待
    component: function loadRecords() {
      return import("../views/game/GameRecords.vue");
    },
  },
  {
    path: "/GameUserProfile",
    // 打开对应页面时再加载组件，减少启动时的等待
    component: function loadProfile() {
      return import("../views/account/GameUserProfile.vue");
    },
  },
  {
    path: "/GameUserManage",
    redirect: "/AccountManage",
  },
  {
    path: "/AccountManage",
    meta: { adminOnly: true },
    // 打开对应页面时再加载组件，减少启动时的等待
    component: function loadAccounts() {
      return import("../views/account/AccountManage.vue");
    },
  },
  {
    path: "/AiSupport",
    // 客服页面单独加载，不影响普通业务页面
    component: function loadSupport() {
      return import("../views/AiSupport.vue");
    },
  },
];

// 路由创建，保留原项目的地址模式
const router = createRouter({ history: createWebHistory(), routes: routes });
// 前端拦截直接输入地址的访问，接口仍由后端独立检查权限
async function checkAccess(to) {
  if (to.path === "/Login") {
    return true;
  }
  const store = useAppStore();
  const user = store.userInfo;
  if (!user.loggedIn || !user.token) {
    return "/Login";
  }
  if (to.meta.adminOnly) {
    // 管理员角色可能在登录后被修改，进入管理页面前必须重新向后端确认
    try {
      const refreshed = await store.refreshIdentity();
      if (!refreshed) {
        return false;
      }
    } catch (error) {
      ElMessage({ message: "无法确认当前账户身份，请稍后重试", type: "error" });
      return false;
    }
    if (user.role !== "ROLE_ADMIN") {
      return "/MainPage";
    }
  }
  return true;
}
router.beforeEach(checkAccess);
// 路由导出
export default router;
