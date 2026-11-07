<template>
  <section
    class="workspace-home"
    aria-labelledby="home-title"
    :aria-busy="loading"
  >
    <header class="workspace-home-heading">
      <div>
        <h1 id="home-title">游戏概览</h1>
      </div>
      <div class="dashboard-heading-actions">
        <span v-if="updatedAt" class="dashboard-updated"
          >更新于 {{ updatedAt }}</span
        >
        <button
          class="dashboard-icon-button"
          type="button"
          title="刷新概览"
          aria-label="刷新概览"
          :disabled="loading"
          @click="loadDashboard"
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
      <section
        class="dashboard-section dashboard-library"
        aria-labelledby="library-title"
      >
        <header class="dashboard-section-heading">
          <h2 id="library-title">
            <span class="i-ep-folder" aria-hidden="true"></span>我的游戏库
          </h2>
          <RouterLink to="/GameLibrary"
            >查看游戏库<span class="i-ep-arrow-right" aria-hidden="true"></span
          ></RouterLink>
        </header>
        <ul v-if="libraryGames.length" class="dashboard-library-list">
          <li v-for="game in libraryGames" :key="game.gameId">
            <RouterLink to="/GameLibrary">
              <img
                v-if="game.cover"
                :src="game.cover"
                :alt="game.name"
                width="160"
                height="120"
              />
              <span v-else class="dashboard-cover-empty" aria-hidden="true">
                <span class="i-ep-monitor"></span>
              </span>
              <strong>{{ game.name }}</strong>
            </RouterLink>
          </li>
        </ul>
        <div v-else class="dashboard-inline-empty">
          <p>{{ emptyLibraryText }}</p>
        </div>
      </section>
    </div>
  </section>
</template>

<script>
import { useAppStore } from "../stores/app";
import * as account from "../logic/account.js";
import * as dashboard from "../logic/dashboard.js";
import { getGames, getRecords } from "../services/gameServ.js";
import DashboardActivity from "../components/DashboardActivity.vue";
import DashboardAccount from "../components/DashboardAccount.vue";
import { getInstalledGames } from "../services/gameRuntime.js";
import { findInstalledGame } from "../logic/gameRuntime.js";
import {
  outcomeName,
  recordDescription,
  visibleRecords,
} from "../logic/records.js";

// 返回首页的数据状态，请求编号用于阻止旧请求覆盖新账户
function homeState() {
  return {
    summary: null,
    installedGames: [],
    loading: false,
    error: "",
    updatedAt: "",
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

// 通过游戏入口匹配安装封面，外部游戏保留名称和图标入口
function readLibraryGames() {
  const result = [];
  if (!this.summary) {
    return result;
  }
  for (const game of this.summary.libraryPreview) {
    const installed = findInstalledGame(this.installedGames, game.gamePath);
    let cover = "";
    if (installed) {
      cover = installed.cover;
    }
    result.push({ gameId: game.gameId, name: game.name, cover: cover });
  }
  return result;
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

// 返回游戏库空状态的文字，成功读取后才判断为空
function readEmptyLibraryText() {
  if (!this.summary) {
    return this.pendingText;
  }
  return "尚未加入游戏";
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

// 同时读取游戏和记录，成功后一次更新概览，旧账户的响应直接丢弃
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
    const results = await Promise.all([
      getGames("catalog", ""),
      getRecords(),
      readGameCovers(),
    ]);
    if (requestId !== this.requestId || token !== store.userInfo.token) {
      return;
    }
    for (const result of results.slice(0, 2)) {
      if (result.code !== 0 || !Array.isArray(result.data)) {
        throw new Error(result.msg || "游戏概览读取失败");
      }
    }
    const now = new Date();
    this.installedGames = results[2];
    this.summary = dashboard.summarizeDashboard(
      results[0].data,
      results[1].data,
      this.userInfo.id,
      now,
    );
    this.loadedGameRevision = gameRevision;
    this.loadedRecordRevision = recordRevision;
    this.updatedAt = now.toLocaleTimeString("zh-CN", {
      timeZone: "Asia/Shanghai",
      hour12: false,
    });
  } catch (error) {
    if (requestId === this.requestId && token === store.userInfo.token) {
      this.error = account.getErrorMessage(
        error,
        "游戏概览读取失败，请刷新重试",
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
    libraryGames: readLibraryGames,
    pendingText: readPendingText,
    emptyLibraryText: readEmptyLibraryText,
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
  },
};
</script>
