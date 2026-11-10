<template>
  <div
    class="platform-shell"
    :class="{ 'desktop-shell': workspaceVisible, 'auth-shell': !workspaceVisible, 'is-playing': gameVisible }"
  >
    <header class="platform-header">
      <div class="platform-header-inner">
        <button
          v-if="workspaceVisible"
          v-show="!gameVisible"
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
          v-if="workspaceVisible"
          v-show="!gameVisible"
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
        <div
          :class="{
            'workspace-header-actions': workspaceVisible,
            'auth-header-actions': !workspaceVisible,
          }"
        >
          <button
            v-if="
              !gameVisible &&
              playerSession.game &&
              playerSession.ownerToken === userInfo.token
            "
            class="workspace-game-return"
            type="button"
            :title="playerSession.game.name"
            :disabled="playerSession.closing"
            @click="returnToGame"
          >
            <span class="i-ep-video-play" aria-hidden="true"></span>{{ gameReturnLabel }}
          </button>
          <RouterLink
            v-if="workspaceVisible && wallet.balance !== null"
            class="workspace-wallet"
            to="/GameCatalog"
            title="字块币账户"
            @click.left.exact="playerSession.minimise()"
          >
            <span class="i-ep-coin" aria-hidden="true"></span
            >{{ wallet.balance }} BC
          </RouterLink>
          <MessageBell v-if="workspaceVisible" :key="userInfo.token" />
          <BackgroundMusic />
          <button
            v-if="workspaceVisible"
            class="workspace-icon-button workspace-auxiliary"
            type="button"
            aria-label="快速反馈"
            title="快速反馈"
            aria-haspopup="dialog"
            @click="openQuickFeedback"
          >
            <span class="i-ep-chat-dot-round" aria-hidden="true"></span>
          </button>
          <button
            v-if="workspaceVisible"
            class="workspace-icon-button workspace-auxiliary"
            type="button"
            aria-label="界面设置"
            title="界面设置"
            @click="openSettings"
          >
            <span class="i-ep-setting" aria-hidden="true"></span>
          </button>
          <AccountSwitcher
            v-if="workspaceVisible"
            :user-info="userInfo"
            @logout="logout"
            @notice="showSwitchNotice"
          />
        </div>
        <img
          v-if="!workspaceVisible"
          class="platform-university"
          src="/images/shandong-university.png"
          alt="山东大学"
          width="378"
          height="107"
        />
      </div>
    </header>
    <div v-if="workspaceVisible" class="platform-workspace">
      <aside
        v-show="!gameVisible"
        id="workspace-navigation"
        class="platform-sidebar"
        :class="{ 'is-expanded': menuExpanded }"
        aria-label="功能菜单"
      >
        <div class="sidebar-group">
          <RouterLink to="/MainPage" @click="closeMenu">
            <span class="i-ep-house sidebar-icon" aria-hidden="true"></span>
            <span>首页</span>
          </RouterLink>
        </div>
        <details
          v-for="group in sidebarGroups"
          :key="group.id"
          class="sidebar-group"
          :class="'sidebar-group-' + group.id"
          :aria-label="group.title"
          :open="groupOpen(group.id)"
          @toggle="syncGroup(group.id, $event)"
        >
          <summary>{{ group.title }}</summary>
          <template v-for="item in group.sList" :key="item.id">
            <RouterLink
              v-if="item.name"
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
        <nav class="sidebar-shortcuts" aria-label="帮助与设置">
          <RouterLink class="workspace-narrow-only" to="/Messages" @click="closeMenu">
            <span class="i-ep-bell" aria-hidden="true"></span>消息中心
          </RouterLink>
          <RouterLink to="/AiSupport" @click="closeMenu"
            ><span class="i-ep-chat-dot-round" aria-hidden="true"></span>AI
            客服</RouterLink
          >
          <button
            class="workspace-wide-only"
            type="button"
            aria-haspopup="dialog"
            @click="openQuickFeedback"
          >
            <span class="i-ep-edit-pen" aria-hidden="true"></span>问题反馈
          </button>
          <button type="button" @click="openSettings">
            <span class="i-ep-setting" aria-hidden="true"></span>界面设置
          </button>
        </nav>
        <div class="workspace-university">
          <img
            src="/images/shandong-university.png"
            alt="山东大学"
            width="378"
            height="107"
          />
        </div>
      </aside>
      <!-- router-view 根据页面地址显示对应功能 -->
      <main class="platform-main" id="main-content">
        <div
          class="workspace-page"
          :class="{
            'workspace-support': $route.path === '/AiSupport',
            'workspace-playing': gameVisible,
          }"
          :inert="gameVisible"
          :aria-hidden="gameVisible"
        >
          <!-- 缓存概览和列表，账户切换时按账号重建，密码表单每次单独创建 -->
          <router-view v-slot="page">
            <Transition name="page">
              <KeepAlive
                :key="userInfo.username"
                :include="cachedPages"
                :max="cacheLimit"
              >
                <component
                  v-if="canViewCurrentPage"
                  :is="page.Component"
                  :key="page.route.path"
                />
              </KeepAlive>
            </Transition>
          </router-view>
        </div>
        <GamePlayer
          :key="userInfo.token"
          :owner-token="userInfo.token"
          :admin="isAdmin"
        />
      </main>
    </div>
    <router-view v-else-if="authRoute" v-slot="page">
      <Transition name="page" mode="out-in" appear>
        <component :is="page.Component" :key="page.route.path" />
      </Transition>
    </router-view>
    <Octorange
      v-if="workspaceVisible"
      v-show="!gameVisible"
      :key="userInfo.id"
      :user-id="userInfo.id"
    />
    <PointerTrail />
    <InterfaceSettings v-if="workspaceVisible" ref="settings" />
    <MessageComposer
      v-if="workspaceVisible"
      ref="quickFeedback"
      dialog-id="quick-feedback-title"
      @saved="feedbackSent"
    />
    <footer v-if="workspaceVisible" v-show="!gameVisible" class="workspace-status">
      <span class="workspace-team">
        <span>4398 游戏平台 · 第02组</span>
        <span v-for="member in teamMembers" :key="member">{{
          member
        }}</span> </span
      ><span>{{ roleName }}：{{ displayName }}</span
      ><span v-toast="switchNotice"></span>
      <div class="workspace-status-details">
        <span aria-label="当前版本">{{ version }}</span>
        <time>{{ clock }}</time>
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
import PointerTrail from "./PointerTrail.vue";
import InterfaceSettings from "./InterfaceSettings.vue";
import BackgroundMusic from "./BackgroundMusic.vue";
import project from "../../package.json";
import { useWalletStore } from "../stores/wallet.js";
import { useGamePlayerStore } from "../stores/gamePlayer.js";
import GamePlayer from "./GamePlayer.vue";
import MessageBell from "./MessageBell.vue";
import MessageComposer from "./MessageComposer.vue";

// 返回页面时保留列表数据的组件名称
const CACHED_PAGES = ["MainPage", "GameList", "GameRecords"];
// 小组成员按照页面展示顺序维护
const TEAM_MEMBERS = ["张昱晨", "吴易", "张牧", "赵振武"];
// 同时保留的列表页面数量上限
const PAGE_CACHE_LIMIT = 8;
// 菜单页面与图标类名的对应表，未匹配页面使用文档图标
const MENU_ICONS = {
  MainPage: "i-ep-house",
  GameCatalog: "i-ep-grid",
  GameLibrary: "i-ep-folder",
  GameFavorites: "i-ep-star",
  GameRecords: "i-ep-document",
  GameUserProfile: "i-ep-user",
  Password: "i-ep-lock",
  AccountManage: "i-ep-user",
  BlockcoinManage: "i-ep-coin",
  RecordManage: "i-ep-document",
  GameManage: "i-ep-box",
};

/** 返回标签页、菜单展开状态、时钟和项目配置中的版本号 */
function workspaceState() {
  return {
    tabs: navigation.createHomeTabs(),
    teamMembers: TEAM_MEMBERS,
    closedGroups: [],
    version: "v" + project.version,
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
/** 顶栏余额与商城使用同一账户仓库，切号后重新读取后端余额 */
function readWallet() {
  return useWalletStore();
}
/** 运行信息由公共层维护，缓存列表的创建与销毁均不会结束当前游戏 */
function readPlayerSession() {
  return useGamePlayerStore();
}
/** 当前页面显示游戏时隐藏其后方列表，其他页面仍按原布局显示 */
function readGameVisible() {
  const player = this.playerSession;
  return Boolean(
    player.game &&
      player.ownerToken === this.userInfo.token &&
      player.expanded &&
      player.routePath === this.$route.path,
  );
}
/** 返回入口区分进行中的游戏与关卡编辑器 */
function readGameReturnLabel() {
  if (this.playerSession.editorOpen) {
    return "返回关卡编辑器";
  }
  return "返回当前对局";
}
/** 顶栏入口返回当前游戏的启动页面，并恢复原实例画面 */
function returnToGame() {
  const path = this.playerSession.resume();
  this.closeMenu();
  if (path && path !== this.$route.path) {
    this.$router.push(path);
  }
}
/** 返回当前账户的菜单 */
function readSystemConfig() {
  return useAppStore().systemConfig;
}
/** 按当前角色整理侧栏菜单，原始后端菜单继续由状态仓库保存 */
function readSidebarGroups() {
  return navigation.createSidebarGroups(
    this.systemConfig.naviList,
    this.isAdmin,
  );
}
/** 判断当前账户是否已经登录 */
function readLoggedIn() {
  return Boolean(this.userInfo.loggedIn && this.userInfo.token);
}
/** 通过路由记录识别登录页 */
function readAuthRoute() {
  return this.$route.name === "Login";
}
/** 等待首页路由完成后再与内容一起显示工作区 */
function readWorkspaceVisible() {
  return this.loggedIn && !this.authRoute;
}
/** 按当前角色决定页面是否显示，切号期间的管理员地址保持空位直到导航完成 */
function readCanViewCurrentPage() {
  if (this.$route.meta.adminOnly) {
    return this.isAdmin;
  }
  return this.loggedIn;
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
  if (this.workspaceVisible) {
    return "/MainPage";
  }
  return "/Login";
}
/** 游戏厅等玩家页面统一使用同一套菜单名称 */
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
/** 从顶栏或侧栏打开共用设置，小屏先收起菜单以显示完整窗口 */
function openSettings() {
  this.closeMenu();
  this.$refs.settings.open();
}
/** 从导航打开共用反馈弹窗，小屏状态下先收起菜单 */
function openQuickFeedback() {
  this.closeMenu();
  this.$refs.quickFeedback.open("feedback");
}
/** 反馈提交成功后在底部状态栏显示结果 */
function feedbackSent(message) {
  if (message.kind === "feedback" && !message.editing) {
    this.switchNotice = "问题反馈已发送";
  }
}
/** 页面加载后恢复菜单，失败时清除过期的登录信息 */
async function restoreNavigation() {
  this.updateClock();
  this.clockTimer = window.setInterval(
    this.updateClock,
    navigation.CLOCK_INTERVAL,
  );
  if (!this.loggedIn) {
    return;
  }
  try {
    await useAppStore().setNavi();
    useWalletStore().refresh();
    this.rememberPage(this.$route.path);
    if (this.authRoute) {
      await this.$router.replace("/MainPage");
    }
  } catch (error) {
    await this.logout();
  }
}
/** 页面卸载时停止时钟刷新，重新进入时由挂载过程启动 */
function stopClock() {
  window.clearInterval(this.clockTimer);
}
/** 将北京时间显示在右下角，版本号在相邻位置独立显示 */
function updateClock() {
  this.clock = navigation.formatClock(new Date());
}
/** 路由变化时添加标签页，同一页面只添加一次 */
function rememberPage(path) {
  if (!this.workspaceVisible) {
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
/** 记录分组的折叠选择，页面状态更新时继续使用该选择 */
function syncGroup(
  id,
  event,
) {
  navigation.updateGroup(this.closedGroups, id, event.target.open);
}
/** 将切号结果显示在状态栏，切号控件负责读取账号选项 */
function showSwitchNotice(message) {
  this.switchNotice = message;
}
/** 登录身份变化时清空标签，列表缓存也由账户名称区分 */
function accountChanged() {
  useWalletStore().refresh();
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
  components: {
    AccountSwitcher: AccountSwitcher,
    Octorange: Octorange,
    PointerTrail: PointerTrail,
    InterfaceSettings: InterfaceSettings,
    BackgroundMusic: BackgroundMusic,
    GamePlayer: GamePlayer,
    MessageBell: MessageBell,
    MessageComposer: MessageComposer,
  },
  data: workspaceState,
  computed: {
    wallet: readWallet,
    playerSession: readPlayerSession,
    gameVisible: readGameVisible,
    gameReturnLabel: readGameReturnLabel,
    userInfo: readUserInfo,
    systemConfig: readSystemConfig,
    sidebarGroups: readSidebarGroups,
    loggedIn: readLoggedIn,
    authRoute: readAuthRoute,
    workspaceVisible: readWorkspaceVisible,
    canViewCurrentPage: readCanViewCurrentPage,
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
    updateClock: updateClock,
    logout: logout,
    menuTitle: menuTitle,
    menuIcon: menuIcon,
    toggleMenu: toggleMenu,
    closeMenu: closeMenu,
    openSettings: openSettings,
    openQuickFeedback: openQuickFeedback,
    feedbackSent: feedbackSent,
    closeTab: closeTab,
    currentPage: currentPage,
    groupOpen: groupOpen,
    syncGroup: syncGroup,
    rememberPage: rememberPage,
    showSwitchNotice: showSwitchNotice,
    returnToGame: returnToGame,
  },
};
</script>
