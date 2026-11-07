import { createRouter, createWebHistory } from "vue-router";
import { useAppStore } from "../stores/app";
import { showToast } from "../tools/toast.js";

// 游戏库和收藏共用列表页面，商城按商品布局单独维护
function loadGameList() {
  return import("../views/game/GameList.vue");
}

// 页面地址保留原项目约定，商城沿用原 GameCatalog 地址
const routes = [
  { path: "/", redirect: "/Login" },
  {
    path: "/Messages",
    // 消息中心按需加载，所有登录角色共用同一阅读页面
    component: function loadMessages() {
      return import("../views/Messages.vue");
    },
  },
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
    redirect: "/AccountManage",
  },
  {
    path: "/DictionaryManage",
    redirect: "/BlockcoinManage",
  },
  {
    path: "/GameCatalog",
    // 打开对应页面时再加载组件，减少启动时的等待
    component: function loadGameStore() {
      return import("../views/game/GameStore.vue");
    },
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
    path: "/BlockcoinManage",
    meta: { adminOnly: true },
    // 收支页面独立加载，账户选择由查询参数传入
    component: function loadBlockcoin() {
      return import("../views/account/BlockcoinManage.vue");
    },
  },
  {
    path: "/RecordManage",
    meta: { adminOnly: true },
    // 管理员记录页面复用原有增删改查
    component: function loadRecordManagement() {
      return import("../views/account/RecordManage.vue");
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
      showToast("无法确认当前账户身份，请稍后重试", "error");
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
