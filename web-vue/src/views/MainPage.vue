<template>
  <section
    class="workspace-home"
    aria-labelledby="home-title"
    :aria-busy="loading"
  >
    <header class="workspace-home-heading">
      <h1 id="home-title">游戏概览</h1>
      <div class="dashboard-heading-actions">
        <span v-if="updatedAt" class="dashboard-updated">更新于 {{ updatedAt }}</span>
        <button
          class="dashboard-icon-button"
          type="button"
          title="刷新游戏厅"
          aria-label="刷新游戏厅"
          :disabled="loading"
          @click="refreshDashboard"
        >
          <span class="i-ep-refresh" aria-hidden="true"></span>
        </button>
        <RouterLink class="dashboard-primary-link" to="/GameCatalog"
          ><span class="i-ep-grid" aria-hidden="true"></span
          >浏览游戏商城</RouterLink
        >
      </div>
    </header>
    <span v-toast:error="error"></span>
    <span v-toast="playNotice"></span>
    <button
      v-if="activeGame && !activeGameListed"
      type="button"
      class="dashboard-game-action dashboard-resume"
      :disabled="player.closing"
      @click="resumeGame"
    >
      <span class="i-ep-video-play" aria-hidden="true"></span>
      {{ activeAction }} · {{ activeGame.name }}
    </button>
    <div class="dashboard-stat-grid" :aria-busy="loading">
      <RouterLink
        v-for="stat in statistics"
        :key="stat.title"
        :to="stat.path"
        class="dashboard-stat"
        :class="stat.tone"
      >
        <div>
          <span class="dashboard-stat-label">{{ stat.title }}</span
          ><strong
            >{{ stat.value }}<small>{{ stat.unit }}</small></strong
          >
        </div>
        <span
          :class="stat.icon"
          class="dashboard-stat-icon"
          aria-hidden="true"
        ></span>
      </RouterLink>
    </div>
    <section
      v-if="activeBattleReport"
      class="dashboard-battle-report"
      aria-labelledby="battle-report-title"
    >
      <header class="dashboard-battle-report-heading">
        <h2 id="battle-report-title">
          {{ activeBattleReport === "billiards" ? "美式八球战报" : "小丑牌战报" }}
        </h2>
        <div
          v-if="jokerReport && billiardsReport"
          class="dashboard-battle-report-picker"
          role="group"
          aria-label="切换游戏战报"
        >
          <button
            type="button"
            :aria-pressed="activeBattleReport === 'joker'"
            @click="selectedBattleReport = 'joker'"
          >
            小丑牌
          </button>
          <button
            type="button"
            :aria-pressed="activeBattleReport === 'billiards'"
            @click="selectedBattleReport = 'billiards'"
          >
            美式八球
          </button>
        </div>
      </header>
      <div
        v-if="activeBattleReport === 'joker'"
        class="dashboard-battle-report-body dashboard-joker-report"
      >
        <div class="dashboard-battle-report-summary">
          <p>
            上局 · 第 {{ jokerReport.latest.ante }} 注 ·
            <span :class="{ 'battle-report-won': jokerReport.latest.outcome === 'won' }">
              {{ jokerReport.latest.outcome === "won" ? "通关" : "落败" }}
            </span>
          </p>
          <p v-if="jokerReport.latest.newPersonalBest" class="joker-report-new-best">
            刷新个人纪录
          </p>
        </div>
        <dl class="dashboard-battle-report-metrics">
          <div>
            <dt>最高单手</dt>
            <dd>{{ formatJokerScore(jokerReport.latest.score) }}</dd>
          </div>
          <div>
            <dt>个人最高单手</dt>
            <dd>{{ formatJokerScore(jokerReport.bestScore) }}</dd>
          </div>
        </dl>
      </div>
      <div v-else class="dashboard-battle-report-body dashboard-billiards-report">
        <p class="dashboard-battle-report-summary">
          上局 · 人机对局 ·
          <span :class="{ 'battle-report-won': billiardsReport.won }">
            {{ billiardsReport.won ? "胜利" : "落败" }}
          </span>
        </p>
        <dl class="dashboard-battle-report-metrics dashboard-billiards-metrics">
          <div>
            <dt>成就</dt>
            <dd class="dashboard-billiards-achievements">
              <ul v-if="billiardsReport.achievements.length">
                <li v-for="achievement in billiardsReport.achievements" :key="achievement">
                  {{ achievement }}
                </li>
              </ul>
              <span v-else>本局未解锁成就</span>
            </dd>
          </div>
          <div>
            <dt>实际到账</dt>
            <dd class="dashboard-billiards-reward">
              {{ formatCoins(billiardsReport.reward) }} <small>BC</small>
            </dd>
          </div>
        </dl>
      </div>
    </section>
    <div class="dashboard-main-grid">
      <DashboardAccount />
      <section
        class="dashboard-section dashboard-records"
        aria-labelledby="records-title"
      >
        <header class="dashboard-section-heading">
          <h2 id="records-title">
            <span class="i-ep-clock" aria-hidden="true"></span>最近游玩
          </h2>
          <RouterLink to="/GameRecords"
            >全部记录<span class="i-ep-arrow-right" aria-hidden="true"></span
          ></RouterLink>
        </header>
        <div
          v-if="summary && recentRecords.length"
          class="dashboard-table-wrap"
        >
          <table>
            <thead>
              <tr>
                <th>游戏</th>
                <th>结果</th>
                <th>时长</th>
                <th>游玩时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in recentRecords" :key="record.recordId">
                <td>{{ record.name }}</td>
                <td :title="describeRecord(record)">
                  {{ outcomeName(record) }} {{ record.levelName }}
                </td>
                <td>{{ recordMinutes(record) }} 分钟</td>
                <td>{{ recordTime(record.playedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="dashboard-record-empty">
          <img
            src="/images/game-collection.png"
            alt=""
            width="130"
            height="96"
          />
          <div>
            <p>{{ emptyRecordText }}</p>
            <RouterLink to="/GameRecords"
              >查看游戏记录<span
                class="i-ep-arrow-right"
                aria-hidden="true"
              ></span
            ></RouterLink>
          </div>
        </div>
      </section>
      <section class="dashboard-section" aria-labelledby="activity-title">
        <header class="dashboard-section-heading">
          <h2 id="activity-title">
            <span class="i-ep-data-line" aria-hidden="true"></span>游玩活动
          </h2>
          <span>最近 7 天</span>
        </header>
        <DashboardActivity
          v-if="summary"
          :records="summary.records"
          :generated-at="summary.generatedAt"
          :total-hours="summary.hours"
        />
        <div v-else class="dashboard-chart-placeholder">{{ pendingText }}</div>
      </section>
      <section class="dashboard-section dashboard-library" aria-labelledby="library-title">
        <header class="dashboard-section-heading">
          <h2 id="library-title">
            <span class="i-ep-folder" aria-hidden="true"></span>游戏库
          </h2>
          <RouterLink to="/GameLibrary">
            我的游戏库<span class="i-ep-arrow-right" aria-hidden="true"></span>
          </RouterLink>
        </header>
        <ul v-if="dashboardGames.length" class="dashboard-library-list">
          <li v-for="game in dashboardGames" :key="game.gameId">
            <img v-if="game.installed" :src="game.cover" alt="" width="192" height="144" />
            <span v-else class="dashboard-cover-empty" aria-hidden="true">
              <span class="i-ep-monitor"></span>
            </span>
            <strong>{{ game.name }}</strong>
            <button
              v-if="isActive(game)"
              class="dashboard-game-action"
              type="button"
              :disabled="player.closing"
              @click="resumeGame"
            >{{ activeAction }}</button>
            <button
              v-else-if="game.owned && game.installed"
              class="dashboard-game-action"
              type="button"
              :disabled="Boolean(activeGame)"
              @click="launchGame(game)"
            >
              <template v-if="activeGame">请先结束当前游戏</template>
              <template v-else-if="playedGame(game)">再玩一局</template>
              <template v-else>开始游戏</template>
            </button>
            <RouterLink v-else-if="game.owned" class="dashboard-game-action" to="/GameLibrary">
              查看游戏库
            </RouterLink>
            <RouterLink
              v-else
              class="dashboard-game-action"
              :to="{ path: '/GameCatalog', query: { search: game.name } }"
            >查看游戏</RouterLink>
          </li>
        </ul>
        <div v-else class="dashboard-inline-empty" role="status">
          <p>{{ lobbyPlaceholder }}</p>
          <button v-if="error" type="button" class="dashboard-game-action" @click="loadDashboard">
            重试
          </button>
        </div>
      </section>
    </div>
  </section>
</template>

<script>
import { useAppStore } from "../stores/app";
import { useGamePlayerStore } from "../stores/gamePlayer.js";
import * as account from "../logic/account.js";
import * as dashboard from "../logic/dashboard.js";
import { getGames, getRecords } from "../services/gameServ.js";
import { getBilliardsReport } from "../services/billiardsReport.js";
import DashboardActivity from "../components/DashboardActivity.vue";
import DashboardAccount from "../components/DashboardAccount.vue";
import { getInstalledGames } from "../services/gameRuntime.js";
import { launcherGames } from "../logic/gameRuntime.js";
import { coinAmount } from "../logic/store.js";
import {
  jokerBattleReportPrefix,
  readJokerBattleReport,
} from "../../games/joker-table/web/battle-report.mjs";
import {
  outcomeName,
  recordDescription,
  visibleRecords,
} from "../logic/records.js";

// 返回首页的数据状态，请求编号用于阻止旧请求覆盖新账户
function homeState() {
  return {
    summary: null,
    catalog: null,
    installedGames: [],
    jokerReport: null,
    billiardsReport: null,
    selectedBattleReport: "",
    billiardsReportRequestId: 0,
    pendingBilliardsReportRevision: -1,
    loadedBilliardsReportRevision: -1,
    loading: false,
    error: "",
    playNotice: "",
    requestId: 0,
    loadedGameRevision: -1,
    loadedRecordRevision: -1,
  };
}

// 读取当前账户，作为首页汇总和显示身份的依据
function readUserInfo() {
  return useAppStore().userInfo;
}

// 只读取当前已登录账户的战报
function readJokerReportOwner() {
  if (this.userInfo.loggedIn && this.userInfo.token) {
    return this.userInfo.id;
  }
  return 0;
}

// 换号时同步替换上个账户的战报
function refreshJokerReport() {
  this.jokerReport = readJokerBattleReport(this.jokerReportOwner);
}

// 游戏在内嵌页面完成结算，同一大厅无需切页就能收到存储变化
function onJokerReportStorage(event) {
  const prefix = jokerBattleReportPrefix(this.jokerReportOwner);
  if (prefix && (event.key === null || (
    typeof event.key === "string" && event.key.startsWith(prefix)
  ))) {
    this.refreshJokerReport();
  }
}

// 缓存中的大厅也会收到牌局结算更新
function listenForJokerReport() {
  window.addEventListener("storage", this.onJokerReportStorage);
}

// 为较大的单手得分保留千位分隔
function formatJokerScore(score) {
  return score.toLocaleString("zh-CN");
}

// 读取当前已登录账户的凭证，退出时清除战报
function readBilliardsReportToken() {
  if (this.userInfo.loggedIn) {
    return this.userInfo.token;
  }
  return "";
}

// 八球实际结算时更新战报版本，普通计时请求不刷新战报
function readBilliardsReportRevision() {
  return useAppStore().billiardsReportRevision;
}

// 切换账户时立即隐藏上一账户的战报并作废其请求
function changeBilliardsReportOwner() {
  this.billiardsReport = null;
  this.selectedBattleReport = "";
  this.billiardsReportRequestId = this.billiardsReportRequestId + 1;
  this.pendingBilliardsReportRevision = -1;
  this.loadedBilliardsReportRevision = -1;
  this.refreshBilliardsReport();
}

// 只应用当前账号当前版本的结算，重试期间保留已有战报
async function refreshBilliardsReport() {
  const token = this.billiardsReportToken;
  const revision = this.billiardsReportRevision;
  if (
    !token ||
    revision === this.pendingBilliardsReportRevision ||
    revision === this.loadedBilliardsReportRevision
  ) {
    return;
  }
  const requestId = this.billiardsReportRequestId + 1;
  this.billiardsReportRequestId = requestId;
  this.pendingBilliardsReportRevision = revision;
  try {
    const response = await getBilliardsReport();
    if (
      requestId === this.billiardsReportRequestId &&
      token === this.billiardsReportToken &&
      revision === this.billiardsReportRevision &&
      response.code === 0
    ) {
      this.billiardsReport = response.data || null;
      this.loadedBilliardsReportRevision = revision;
    }
  } catch (error) {
    // 下次进入大厅或手动刷新时可重试
  } finally {
    if (requestId === this.billiardsReportRequestId) {
      this.pendingBilliardsReportRevision = -1;
    }
  }
}

// 保留用户当前查看的战报，必要时显示另一款已有战报的游戏
function readActiveBattleReport() {
  if (this.selectedBattleReport === "billiards" && this.billiardsReport) {
    return "billiards";
  }
  if (this.jokerReport) {
    return "joker";
  }
  if (this.billiardsReport) {
    return "billiards";
  }
  return "";
}

// 只有一款游戏有战报时也记住当前显示项
function rememberBattleReport(selection) {
  this.selectedBattleReport = selection;
}

// 读取指定统计值，未成功读取时返回占位文字
function metric(name) {
  if (!this.summary) {
    return "--";
  }
  return this.summary[name];
}

// 整理四项统计卡片，每项保留对应功能的路由入口
function readStatistics() {
  return [
    {
      title: "已收录游戏",
      value: this.metric("catalogCount"),
      unit: "款",
      icon: "i-ep-grid",
      path: "/GameCatalog",
      tone: "dashboard-stat-blue",
    },
    {
      title: "我的游戏库",
      value: this.metric("libraryCount"),
      unit: "款",
      icon: "i-ep-folder",
      path: "/GameLibrary",
      tone: "dashboard-stat-green",
    },
    {
      title: "我的收藏",
      value: this.metric("favoriteCount"),
      unit: "款",
      icon: "i-ep-star",
      path: "/GameFavorites",
      tone: "dashboard-stat-amber",
    },
    {
      title: "游玩记录",
      value: this.metric("recordCount"),
      unit: "次",
      icon: "i-ep-document",
      path: "/GameRecords",
      tone: "dashboard-stat-neutral",
    },
  ];
}

// 播放器可能仍保留其他页面打开的对局，只显示当前账户的实例
function readActiveGame() {
  if (this.player.ownerToken === this.userInfo.token) {
    return this.player.game;
  }
  return null;
}

// 概览显示全部游戏，优先展示当前对局与最近可玩的游戏
function readDashboardGames() {
  if (!this.catalog) {
    return [];
  }
  const games = launcherGames(this.catalog, this.installedGames);
  let activePath = "";
  if (this.activeGame) {
    activePath = this.activeGame.gamePath;
  }
  return dashboard.lobbyGames(games, this.personalRecords, activePath);
}

// 概览请求成功后显示本次数据的更新时间
function readUpdatedAt() {
  if (!this.summary) {
    return "";
  }
  const updated = new Date(this.summary.generatedAt);
  return updated.toLocaleTimeString("zh-CN", {
    timeZone: "Asia/Shanghai",
    hour12: false,
  });
}

// 区分正在游玩的游戏与只是存在游玩记录的游戏
function isActive(game) {
  return Boolean(this.activeGame && this.activeGame.gamePath === game.gamePath);
}

// 已隐藏的记录不参与首页推荐
function playedGame(game) {
  for (const record of this.personalRecords) {
    if (record.hidden !== true && String(record.gameId) === String(game.gameId)) {
      return true;
    }
  }
  return false;
}

// 从大厅直接启动已拥有的游戏，地址仍由共享播放器生成
function launchGame(game) {
  this.playNotice = this.player.open(game, this.userInfo, this.$route.path, false);
}

// 恢复时回到原启动页，保留运行中的 iframe
function resumeGame() {
  const path = this.player.resume();
  if (path && path !== this.$route.path) {
    this.$router.push(path);
  }
}

// 初次加载、失败和空商城分别提供明确状态
function readLobbyPlaceholder() {
  if (this.error) {
    return "游戏加载失败";
  }
  if (this.catalog) {
    return "游戏厅暂无上架游戏";
  }
  return "正在加载游戏…";
}

// 安装配置读取失败时保留游戏名称，其他概览数据继续显示
async function readGameCovers() {
  try {
    return await getInstalledGames();
  } catch (error) {
    return [];
  }
}

// 返回尚无可用数据时的提示，区别请求中和读取失败
function readPendingText() {
  if (this.error) {
    return "概览读取失败，请刷新重试";
  }
  return "";
}

// 成功读取后判断记录是否为空，请求失败时显示重试提示
function readEmptyRecordText() {
  if (!this.summary) {
    return this.pendingText;
  }
  return "暂无游玩记录";
}

// 最近游玩共用隐藏选择，统计继续读取概览中的完整记录
function readRecentRecords() {
  if (!this.summary) {
    return [];
  }
  const records = visibleRecords(this.summary.records);
  return records.slice(0, dashboard.RECENT_RECORD_LIMIT);
}

// 将记录时长换算为分钟，record 是接口返回的一条记录
function recordMinutes(record) {
  const seconds = Math.max(0, Number(record.durationSeconds) || 0);
  return (seconds / 60).toFixed(dashboard.DURATION_DECIMALS);
}

// 并行读取游戏和记录，游戏入口先显示，旧账户的响应直接丢弃
async function loadDashboard() {
  if (this.loading) {
    return;
  }
  const store = useAppStore();
  const token = store.userInfo.token;
  const gameRevision = store.gameRevision;
  const recordRevision = store.recordRevision;
  this.requestId = this.requestId + 1;
  const requestId = this.requestId;
  this.loading = true;
  this.error = "";
  try {
    const recordRequest = Promise.allSettled([getRecords()]);
    const results = await Promise.allSettled([
      getGames("catalog", ""),
      readGameCovers(),
    ]);
    if (requestId !== this.requestId || token !== store.userInfo.token) {
      return;
    }
    const games = results[0];
    const covers = results[1];
    if (games.status === "rejected") {
      throw games.reason;
    }
    if (games.value.code !== 0 || !Array.isArray(games.value.data)) {
      throw new Error(games.value.msg || "游戏列表读取失败");
    }
    this.catalog = games.value.data;
    this.installedGames = covers.value || [];
    const recordResults = await recordRequest;
    if (requestId !== this.requestId || token !== store.userInfo.token) {
      return;
    }
    const records = recordResults[0];
    if (records.status === "rejected") {
      throw records.reason;
    }
    if (records.value.code !== 0 || !Array.isArray(records.value.data)) {
      throw new Error(records.value.msg || "游玩记录读取失败");
    }
    this.summary = dashboard.summarizeDashboard(
      this.catalog,
      records.value.data,
      this.userInfo.id,
      new Date(),
    );
    this.loadedGameRevision = gameRevision;
    this.loadedRecordRevision = recordRevision;
  } catch (error) {
    if (requestId === this.requestId && token === store.userInfo.token) {
      this.error = account.getErrorMessage(
        error,
        "游戏厅读取失败，请刷新重试",
      );
    }
  } finally {
    if (requestId === this.requestId) {
      this.loading = false;
    }
  }
}

// 返回首页时按数据版本检查，已加载且版本相同的概览直接显示
function activateDashboard() {
  this.refreshJokerReport();
  this.refreshBilliardsReport();
  const store = useAppStore();
  if (
    !this.summary ||
    this.loadedGameRevision !== store.gameRevision ||
    this.loadedRecordRevision !== store.recordRevision
  ) {
    return this.loadDashboard();
  }
}

// 手动刷新时也重新读取八球战报
function refreshDashboard() {
  this.loadedBilliardsReportRevision = -1;
  this.refreshBilliardsReport();
  return this.loadDashboard();
}

// 销毁首页时停止监听战报，并使尚未完成的响应失效
function invalidateRequest() {
  window.removeEventListener("storage", this.onJokerReportStorage);
  this.billiardsReportRequestId = this.billiardsReportRequestId + 1;
  this.requestId = this.requestId + 1;
}

export default {
  name: "MainPage",
  components: {
    DashboardActivity: DashboardActivity,
    DashboardAccount: DashboardAccount,
  },
  data: homeState,
  computed: {
    userInfo: readUserInfo,
    jokerReportOwner: readJokerReportOwner,
    billiardsReportToken: readBilliardsReportToken,
    billiardsReportRevision: readBilliardsReportRevision,
    activeBattleReport: readActiveBattleReport,
    statistics: readStatistics,
    recentRecords: readRecentRecords,
    // 大厅和其他页面共享当前运行实例
    player: function readPlayer() {
      return useGamePlayerStore();
    },
    activeGame: readActiveGame,
    // 目录中没有当前游戏时，也保留返回入口
    activeGameListed: function readActiveGameListed() {
      for (const game of this.dashboardGames) {
        if (this.isActive(game)) {
          return true;
        }
      }
      return false;
    },
    // 编辑器和游戏使用不同的返回文字
    activeAction: function readActiveAction() {
      if (this.player.editorOpen) {
        return "返回关卡编辑器";
      }
      return "返回当前对局";
    },
    // 概览未读取完成时没有可用于推荐的个人记录
    personalRecords: function readPersonalRecords() {
      if (this.summary) {
        return this.summary.records;
      }
      return [];
    },
    dashboardGames: readDashboardGames,
    updatedAt: readUpdatedAt,
    lobbyPlaceholder: readLobbyPlaceholder,
    pendingText: readPendingText,
    emptyRecordText: readEmptyRecordText,
  },
  watch: {
    jokerReportOwner: { handler: refreshJokerReport, immediate: true },
    billiardsReportToken: { handler: changeBilliardsReportOwner, immediate: true },
    billiardsReportRevision: refreshBilliardsReport,
    activeBattleReport: { handler: rememberBattleReport, immediate: true },
  },
  mounted: listenForJokerReport,
  activated: activateDashboard,
  beforeUnmount: invalidateRequest,
  methods: {
    outcomeName: outcomeName,
    describeRecord: recordDescription,
    metric: metric,
    recordMinutes: recordMinutes,
    recordTime: dashboard.formatRecordTime,
    refreshJokerReport: refreshJokerReport,
    onJokerReportStorage: onJokerReportStorage,
    formatJokerScore: formatJokerScore,
    formatCoins: coinAmount,
    refreshBilliardsReport: refreshBilliardsReport,
    refreshDashboard: refreshDashboard,
    loadDashboard: loadDashboard,
    isActive: isActive,
    playedGame: playedGame,
    launchGame: launchGame,
    resumeGame: resumeGame,
  },
};
</script>
