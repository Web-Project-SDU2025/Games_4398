<template>
  <section
    class="workspace-home"
    aria-labelledby="home-title"
    :aria-busy="loading"
  >
    <header class="workspace-home-heading">
      <div>
        <h1 id="home-title">4398 游戏厅</h1>
        <p>今天想玩点什么？</p>
      </div>
      <div class="dashboard-heading-actions">
        <button
          class="dashboard-icon-button"
          type="button"
          title="刷新游戏厅"
          aria-label="刷新游戏厅"
          :disabled="loading"
          @click="loadDashboard"
        >
          <span class="i-ep-refresh" aria-hidden="true"></span>
        </button>
        <RouterLink class="lobby-library-link" to="/GameLibrary"
          ><span class="i-ep-folder" aria-hidden="true"></span
          >我的游戏库</RouterLink
        >
      </div>
    </header>
    <span v-toast:error="error"></span>
    <span v-toast="playNotice"></span>
    <button
      v-if="activeGame && !activeGameListed"
      type="button"
      class="lobby-action lobby-action-primary lobby-resume"
      :disabled="player.closing"
      @click="resumeGame"
    >
      <span class="i-ep-video-play" aria-hidden="true"></span>
      {{ activeAction }} · {{ activeGame.name }}
    </button>
    <section
      v-if="showcase.length"
      class="lobby-showcase"
      :class="{ 'lobby-single': showcase.length === 1 }"
      aria-label="游戏推荐"
    >
      <article
        v-for="(game, index) in showcase"
        :key="game.gameId"
        class="lobby-feature"
        :class="{ 'lobby-feature-main': index === 0, 'lobby-feature-pool': game.id === 'billiards' }"
      >
        <img v-if="game.installed" :src="game.cover" alt="" class="lobby-art" />
        <div class="lobby-feature-body">
          <div class="lobby-feature-labels">
            <span v-if="game.category">{{ game.category }}</span>
            <span v-if="isActive(game)" class="lobby-live">正在游玩</span>
            <span v-else-if="game.owned">已入库</span>
          </div>
          <h2>{{ game.name }}</h2>
          <p :title="game.description">{{ game.description }}</p>
          <button
            v-if="isActive(game)"
            class="lobby-action lobby-action-primary"
            type="button"
            :disabled="player.closing"
            @click="resumeGame"
          >
            <span class="i-ep-video-play" aria-hidden="true"></span>
            {{ activeAction }}
          </button>
          <button
            v-else-if="game.owned && game.installed"
            class="lobby-action lobby-action-primary"
            type="button"
            :disabled="Boolean(activeGame)"
            @click="launchGame(game)"
          >
            <span class="i-ep-video-play" aria-hidden="true"></span>
            <template v-if="activeGame">请先结束当前游戏</template>
            <template v-else>{{ playedGame(game) ? "再玩一局" : "开始游戏" }}</template>
          </button>
          <RouterLink
            v-else
            class="lobby-action lobby-action-primary"
            :to="game.owned ? '/GameLibrary' : { path: '/GameCatalog', query: { search: game.name } }"
          >
            {{ game.owned ? "查看游戏库" : "查看游戏" }}
            <span class="i-ep-arrow-right" aria-hidden="true"></span>
          </RouterLink>
        </div>
      </article>
    </section>
    <div v-else class="lobby-showcase lobby-single">
      <div class="lobby-placeholder" role="status">
        <span class="i-ep-monitor" aria-hidden="true"></span>
        <p>{{ lobbyPlaceholder }}</p>
        <button v-if="error" type="button" class="lobby-action" @click="loadDashboard">
          重试
        </button>
      </div>
    </div>
    <header class="lobby-section-heading">
      <h2>我的游玩</h2>
      <RouterLink to="/GameCatalog"
        >浏览游戏商城<span class="i-ep-arrow-right" aria-hidden="true"></span
      ></RouterLink>
    </header>
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
    <DashboardAccount />
    <div class="dashboard-main-grid">
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
    </div>
  </section>
</template>

<script>
import { useAppStore } from "../stores/app";
import { useGamePlayerStore } from "../stores/gamePlayer.js";
import * as account from "../logic/account.js";
import * as dashboard from "../logic/dashboard.js";
import { getGames, getRecords } from "../services/gameServ.js";
import DashboardActivity from "../components/DashboardActivity.vue";
import DashboardAccount from "../components/DashboardAccount.vue";
import { getInstalledGames } from "../services/gameRuntime.js";
import { launcherGames } from "../logic/gameRuntime.js";
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

// 封面、持有状态和启动能力沿用商城与游戏库的合并结果
function readShowcase() {
  return dashboard.lobbyGames(
    launcherGames(this.catalog || [], this.installedGames),
    this.personalRecords,
    this.activeGame && this.activeGame.gamePath,
  );
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
    return "游戏暂时没有加载出来。";
  }
  if (this.catalog) {
    return "游戏厅暂时没有上架的游戏。";
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
  const store = useAppStore();
  if (
    !this.summary ||
    this.loadedGameRevision !== store.gameRevision ||
    this.loadedRecordRevision !== store.recordRevision
  ) {
    return this.loadDashboard();
  }
}

// 离开首页后让尚未完成的响应失效
function invalidateRequest() {
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
    statistics: readStatistics,
    recentRecords: readRecentRecords,
    // 大厅和其他页面共享当前运行实例
    player: function readPlayer() {
      return useGamePlayerStore();
    },
    activeGame: readActiveGame,
    // 目录中没有当前游戏时，也保留返回入口
    activeGameListed: function readActiveGameListed() {
      for (const game of this.showcase) {
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
    showcase: readShowcase,
    lobbyPlaceholder: readLobbyPlaceholder,
    pendingText: readPendingText,
    emptyRecordText: readEmptyRecordText,
  },
  activated: activateDashboard,
  beforeUnmount: invalidateRequest,
  methods: {
    outcomeName: outcomeName,
    describeRecord: recordDescription,
    metric: metric,
    recordMinutes: recordMinutes,
    recordTime: dashboard.formatRecordTime,
    loadDashboard: loadDashboard,
    isActive: isActive,
    playedGame: playedGame,
    launchGame: launchGame,
    resumeGame: resumeGame,
  },
};
</script>
