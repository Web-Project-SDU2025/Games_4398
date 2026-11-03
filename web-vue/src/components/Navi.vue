<template>
  <div class="platform-shell" :class="{ 'desktop-shell': loggedIn }">
    <!-- 主页面的头部区域 -->
    <header class="platform-header">
      <div class="platform-header-inner">
        <button
          v-if="loggedIn"
          class="workspace-menu-toggle"
          type="button"
          aria-label="展开或收起功能菜单"
          title="功能菜单"
          aria-controls="workspace-navigation"
          :aria-expanded="menuExpanded"
          @click="toggleMenu"
        >
          <span class="i-ep-menu" aria-hidden="true"></span>
        </button>
        <RouterLink class="platform-brand" :to="homePath">
          <img src="/favicon.svg" alt="" width="32" height="32" />
          <span><strong>4398</strong><small>游戏平台</small></span>
        </RouterLink>
        <TransitionGroup
          v-if="loggedIn"
          name="tab"
          tag="nav"
          class="workspace-tabs"
          aria-label="已打开的页面"
        >
          <div
            v-for="tab in tabs"
            :key="tab.path"
            class="workspace-tab"
            :class="{ 'is-current': $route.path === tab.path }"
          >
            <RouterLink :to="tab.path" :aria-current="currentPage(tab.path)">{{
              tab.title
            }}</RouterLink>
            <button
              v-if="tab.path !== '/MainPage'"
              type="button"
              :aria-label="'关闭' + tab.title"
              :title="'关闭' + tab.title"
              @click="closeTab(tab.path)"
            >
              <span class="i-ep-close" aria-hidden="true"></span>
            </button>
          </div>
        </TransitionGroup>
        <AccountSwitcher
          v-if="loggedIn"
          :user-info="userInfo"
          @logout="logout"
          @notice="showSwitchNotice"
        />
        <div class="platform-account" v-else>
          <span>简体中文</span
          ><span class="platform-online">4398 PLATFORM</span>
        </div>
      </div>
    </header>
    <!-- 当登录成功后显示主页面 -->
    <div class="platform-workspace" v-if="loggedIn">
      <!-- 中心区域的菜单区域 -->
      <aside
        id="workspace-navigation"
        class="platform-sidebar"
        :class="{ 'is-expanded': menuExpanded }"
        aria-label="功能菜单"
      >
        <div class="workspace-menu-title">功能菜单</div>
        <details
          v-for="group in systemConfig.naviList"
          :key="group.id"
          class="sidebar-group"
          :aria-label="group.title"
          :open="groupOpen(group.id)"
          @toggle="syncGroup(group.id, $event)"
        >
          <summary>{{ group.title }}</summary>
          <template v-for="item in group.sList" :key="item.id">
            <RouterLink
              v-if="item.name && item.name !== 'GameUserManage'"
              :to="'/' + item.name"
              @click="closeMenu"
              ><span
                :class="menuIcon(item.name)"
                class="sidebar-icon"
                aria-hidden="true"
              ></span
              ><span>{{ menuTitle(item) }}</span></RouterLink
            >
            <template v-else-if="!item.name"
              ><RouterLink
                v-for="child in item.sList"
                :key="child.id"
                :to="'/' + child.name"
                @click="closeMenu"
                ><span
                  :class="menuIcon(child.name)"
                  class="sidebar-icon"
                  aria-hidden="true"
                ></span
                >{{ child.title }}</RouterLink
              ></template
            >
          </template>
        </details>
        <nav class="sidebar-shortcuts" aria-label="更多功能">
          <RouterLink to="/AiSupport" @click="closeMenu"
            ><span class="i-ep-chat-dot-round" aria-hidden="true"></span>AI
            客服</RouterLink
          >
          <RouterLink v-if="isAdmin" to="/AccountManage" @click="closeMenu"
            ><span class="i-ep-setting" aria-hidden="true"></span
            >账户管理</RouterLink
          >
        </nav>
      </aside>
      <!-- router-view 根据页面地址显示对应功能 -->
      <main class="platform-main" id="main-content">
        <div
          class="workspace-page"
          :class="{ 'workspace-support': $route.path === '/AiSupport' }"
        >
          <!-- 只缓存列表，返回标签时保留内容，密码和账户表单仍会重新创建 -->
          <router-view v-slot="page">
            <Transition name="page" appear>
              <KeepAlive
                :key="userInfo.username"
                :include="cachedPages"
                :max="cacheLimit"
              >
                <component :is="page.Component" :key="page.route.path" />
              </KeepAlive>
            </Transition>
          </router-view>
        </div>
      </main>
    </div>
    <!-- 当登录前显示登录注册页面 -->
    <router-view v-else v-slot="page">
      <Transition name="page" mode="out-in" appear>
        <component :is="page.Component" :key="page.route.path" />
      </Transition>
    </router-view>
    <Octorange v-if="loggedIn" :key="userInfo.id" :user-id="userInfo.id" />
    <footer v-if="loggedIn" class="workspace-status">
      <span>4398 游戏平台 · 第02组</span
      ><span>{{ roleName }}：{{ displayName }}</span
      ><span v-reveal="switchNotice" role="status">{{ switchNotice }}</span
      ><time>{{ clock }}</time>
    </footer>
    <footer v-else class="platform-footer">
      <div>
        <strong>4398</strong><span>游戏平台</span
        ><span>游戏库 · 收藏 · 游玩记录</span>
      </div>
    </footer>
  </div>
</template>

<script>
import { useAppStore } from "../stores/app";
import * as navigation from "../logic/navigation.js";
import * as account from "../logic/account.js";
import AccountSwitcher from "./AccountSwitcher.vue";
import Octorange from "./Octorange.vue";

const CACHED_PAGES = ["GameList", "GameRecords"];
const PAGE_CACHE_LIMIT = 6;
const MENU_ICONS = {
  MainPage: "i-ep-house",
  GameCatalog: "i-ep-grid",
  GameLibrary: "i-ep-folder",
  GameFavorites: "i-ep-star",
  GameRecords: "i-ep-document",
  GameUserProfile: "i-ep-user",
  Password: "i-ep-lock",
  MenuManage: "i-ep-menu",
  DictionaryManage: "i-ep-collection",
};

/** 返回标签页、菜单展开状态和时钟数据 */
function workspaceState() {
  return {
    tabs: navigation.createHomeTabs(),
    closedGroups: [],
    clock: "",
    clockTimer: null,
    cachedPages: CACHED_PAGES,
    cacheLimit: PAGE_CACHE_LIMIT,
    switchNotice: "",
    menuExpanded: false,
  };
}

/** 返回当前账户的登录信息 */
function readUserInfo() {
  return useAppStore().userInfo;
}
/** 返回当前账户的菜单 */
function readSystemConfig() {
  return useAppStore().systemConfig;
}
/** 判断当前账户是否已经登录 */
function readLoggedIn() {
  return Boolean(this.userInfo.loggedIn && this.userInfo.token);
}
/** 返回账户昵称，没有昵称时显示账号 */
function readDisplayName() {
  return account.getDisplayName(this.userInfo);
}
/** 返回账户角色的中文名称 */
function readRoleName() {
  return account.getRoleName(this.userInfo.role);
}
/** 返回品牌链接的目标页面 */
function readHomePath() {
  if (this.loggedIn) {
    return "/MainPage";
  }
  return "/Login";
}
/** 首页保留原路由地址，菜单文字统一显示为首页 */
function menuTitle(item) {
  return navigation.getMenuTitle(item);
}
/** 根据页面名称返回菜单图标，未知页面显示通用文档图标 */
function menuIcon(name) {
  if (MENU_ICONS[name]) {
    return MENU_ICONS[name];
  }
  return "i-ep-document";
}
/** 切换小屏功能菜单，展开状态同时提供给辅助阅读工具 */
function toggleMenu() {
  this.menuExpanded = !this.menuExpanded;
}
/** 选择页面后收起小屏菜单，让内容区恢复完整高度 */
function closeMenu() {
  this.menuExpanded = false;
}
/** 页面加载后恢复菜单，失败时清除过期的登录信息 */
async function restoreNavigation() {
  this.updateClock();
  this.clockTimer = window.setInterval(
    this.updateClock,
    navigation.CLOCK_INTERVAL
  );
  if (!this.loggedIn) {
    // 未登录访问范围由路由守卫处理，保留用户选择的管理员登录入口
    return;
  }
  try {
    await useAppStore().setNavi();
    this.rememberPage(this.$route.path);
    if (
      this.$route.path === "/Login" ||
      this.$route.path === "/AdminLogin" ||
      this.$route.path === "/"
    ) {
      await this.$router.replace("/MainPage");
    }
  } catch (error) {
    await this.logout();
  }
}
/** 离开框架时停止时钟，防止重复创建定时器 */
function stopClock() {
  window.clearInterval(this.clockTimer);
}
/** 使用北京时间更新底部状态栏 */
function updateClock() {
  const now = new Date();
  this.clock = navigation.formatClock(now);
}
/** 路由变化时添加标签页，同一页面只添加一次 */
function rememberPage(path) {
  if (
    !this.loggedIn ||
    path === "/Login" ||
    path === "/AdminLogin" ||
    path === "/"
  ) {
    return;
  }
  const name = path.substring(1);
  let title = navigation.findMenuTitle(this.systemConfig.naviList, name);
  if (title === "") {
    title = this.$route.name || name;
  }
  navigation.rememberTab(this.tabs, path, title);
}
/** 关闭指定标签，关闭当前页时打开左边的页面 */
function closeTab(path) {
  const currentPath = this.$route.path;
  const nextPath = navigation.removeTab(this.tabs, path, currentPath);
  if (nextPath !== currentPath) {
    this.$router.push(nextPath);
  }
}
/** 返回当前页面的无障碍标记，其他页面不设置该标记 */
function currentPage(path) {
  if (this.$route.path === path) {
    return "page";
  }
  return undefined;
}
/** 判断左侧分组是否应该展开 */
function groupOpen(id) {
  return !this.closedGroups.includes(id);
}
/** 记录用户折叠的分组，避免页面刷新状态时自动展开 */
function syncGroup(id, event) {
  navigation.updateGroup(this.closedGroups, id, event.target.open);
}
/** 将切号结果显示在状态栏，工作区不再重复读取切号选项 */
function showSwitchNotice(message) {
  this.switchNotice = message;
}
/** 登录身份变化时清空标签，列表缓存也由账户名称区分 */
function accountChanged() {
  this.tabs = navigation.createHomeTabs();
  this.closedGroups = [];
  this.switchNotice = "";
  this.closeMenu();
}
/** 将当前身份用于界面入口显示，接口仍由后端检查 */
function readIsAdmin() {
  return this.userInfo.role === "ROLE_ADMIN";
}
/** 清除登录状态并返回登录页面 */
async function logout() {
  useAppStore().logout();
  this.tabs = navigation.createHomeTabs();
  this.closedGroups = [];
  await this.$router.replace("/Login");
}
export default {
  components: { AccountSwitcher: AccountSwitcher, Octorange: Octorange },
  data: workspaceState,
  computed: {
    userInfo: readUserInfo,
    systemConfig: readSystemConfig,
    loggedIn: readLoggedIn,
    displayName: readDisplayName,
    roleName: readRoleName,
    homePath: readHomePath,
    isAdmin: readIsAdmin,
  },
  mounted: restoreNavigation,
  beforeUnmount: stopClock,
  watch: {
    "$route.path": { handler: rememberPage, immediate: true },
    "userInfo.username": accountChanged,
  },
  methods: {
    logout: logout,
    menuTitle: menuTitle,
    menuIcon: menuIcon,
    toggleMenu: toggleMenu,
    closeMenu: closeMenu,
    updateClock: updateClock,
    closeTab: closeTab,
    currentPage: currentPage,
    groupOpen: groupOpen,
    syncGroup: syncGroup,
    rememberPage: rememberPage,
    showSwitchNotice: showSwitchNotice,
  },
};
</script>
