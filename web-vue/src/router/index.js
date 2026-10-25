import { createRouter, createWebHistory } from "vue-router";

// 路由表，游戏列表、游戏库和收藏复用同一个页面。
const routes = [
  { path: "/", redirect: "/Login" },
  {
    path: "/Login",
    component: function () {
      return import("../Login.vue");
    },
  },
  {
    path: "/MainPage",
    component: function () {
      return import("../views/MainPage.vue");
    },
  },
  {
    path: "/Password",
    component: function () {
      return import("../views/info/Password.vue");
    },
  },
  {
    path: "/MenuManage",
    component: function () {
      return import("../views/system/MenuManage.vue");
    },
  },
  {
    path: "/DictionaryManage",
    component: function () {
      return import("../views/system/DictionaryManage.vue");
    },
  },
  {
    path: "/GameCatalog",
    component: function () {
      return import("../views/game/GameList.vue");
    },
    props: { mode: "catalog", title: "游戏列表" },
  },
  {
    path: "/GameLibrary",
    component: function () {
      return import("../views/game/GameList.vue");
    },
    props: { mode: "library", title: "我的游戏库" },
  },
  {
    path: "/GameFavorites",
    component: function () {
      return import("../views/game/GameList.vue");
    },
    props: { mode: "favorites", title: "我的收藏" },
  },
  {
    path: "/GameRecords",
    component: function () {
      return import("../views/game/GameRecords.vue");
    },
  },
  {
    path: "/GameUserProfile",
    component: function () {
      return import("../views/account/GameUserProfile.vue");
    },
  },
  {
    path: "/GameUserManage",
    component: function () {
      return import("../views/account/GameUserManage.vue");
    },
  },
];

// 路由创建，保留原项目的地址模式。
const router = createRouter({ history: createWebHistory(), routes: routes });
// 路由导出。
export default router;
