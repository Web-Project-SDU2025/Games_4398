<template>
  <section class="workspace-home" aria-labelledby="home-title">
    <header class="workspace-home-heading">
      <div>
        <h1 id="home-title">游戏概览</h1>
        <p>欢迎回来，{{ displayName }}</p>
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
          >浏览游戏</RouterLink
        >
      </div>
    </header>
    <p v-if="error" class="dashboard-error" role="alert">{{ error }}</p>
    <p v-if="loading && !summary" role="status" class="dashboard-loading">
      正在读取游戏概览
    </p>
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
          ><span class="dashboard-stat-detail">{{ stat.detail }}</span>
        </div>
        <span
          :class="stat.icon"
          class="dashboard-stat-icon"
          aria-hidden="true"
        ></span>
      </RouterLink>
    </div>
    <div class="dashboard-main-grid">
      <section class="dashboard-section" aria-labelledby="library-title">
        <header class="dashboard-section-heading">
          <h2 id="library-title">
            <span class="i-ep-folder" aria-hidden="true"></span>我的游戏库
          </h2>
          <RouterLink to="/GameLibrary"
            >查看游戏库<span class="i-ep-arrow-right" aria-hidden="true"></span
          ></RouterLink>
        </header>
        <div class="dashboard-library-summary">
          <div>
            <span>已加入游戏</span
            ><strong>{{ metric("libraryCount") }}<small>款</small></strong>
          </div>
          <span class="dashboard-account-role">{{ roleName }}</span>
        </div>
        <div class="dashboard-progress-row">
          <span>游戏库 / 全部游戏</span
          ><strong
            >{{ metric("libraryCount") }} / {{ metric("catalogCount") }}</strong
          >
        </div>
        <progress
          :value="libraryPercent"
          max="100"
          aria-label="游戏库占已收录游戏的比例"
        ></progress>
        <div class="dashboard-progress-row">
          <span>收藏 / 全部游戏</span
          ><strong
            >{{ metric("favoriteCount") }} /
            {{ metric("catalogCount") }}</strong
          >
        </div>
        <progress
          class="dashboard-favorite-progress"
          :value="favoritePercent"
          max="100"
          aria-label="收藏占已收录游戏的比例"
        ></progress>
        <ul
          v-if="summary && summary.libraryPreview.length"
          class="dashboard-library-list"
        >
          <li v-for="game in summary.libraryPreview" :key="game.gameId">
            <span class="i-ep-monitor" aria-hidden="true"></span
            ><strong>{{ game.name }}</strong
            ><span>{{ game.category || "未分类" }}</span>
          </li>
        </ul>
        <div v-else class="dashboard-inline-empty">
          <span class="i-ep-folder-opened" aria-hidden="true"></span>
          <p>{{ emptyLibraryText }}</p>
          <RouterLink to="/GameCatalog"
            >浏览游戏<span class="i-ep-arrow-right" aria-hidden="true"></span
          ></RouterLink>
        </div>
      </section>
      <section class="dashboard-section" aria-labelledby="activity-title">
        <header class="dashboard-section-heading">
          <h2 id="activity-title">
            <span class="i-ep-data-line" aria-hidden="true"></span>游玩活动
          </h2>
          <span>最近 7 天</span>
        </header>
        <div class="dashboard-chart-caption">
          <strong>{{ metric("hours") }}<small>小时</small></strong
          ><span>累计游玩时长</span>
        </div>
        <DashboardActivity v-if="summary" :activity="summary.activity" />
        <div v-else class="dashboard-chart-placeholder">{{ pendingText }}</div>
      </section>
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
          v-if="summary && summary.recentRecords.length"
          class="dashboard-table-wrap"
        >
          <table>
            <thead>
              <tr>
                <th>游戏</th>
                <th>分数</th>
                <th>时长</th>
                <th>游玩时间</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="record in summary.recentRecords"
                :key="record.recordId"
              >
                <td>{{ record.name }}</td>
                <td>{{ record.score }}</td>
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
      <section class="dashboard-section" aria-labelledby="account-title">
        <header class="dashboard-section-heading">
          <h2 id="account-title">
            <span class="i-ep-user" aria-hidden="true"></span>我的账户
          </h2>
        </header>
        <div class="dashboard-profile">
          <span class="dashboard-profile-avatar" aria-hidden="true">{{
            avatarLetter
          }}</span>
          <div>
            <strong>{{ displayName }}</strong
            ><span>{{ userInfo.username }} · {{ roleName }}</span>
          </div>
        </div>
        <nav class="dashboard-account-links" aria-label="账户功能">
          <RouterLink to="/GameUserProfile"
            ><span class="i-ep-postcard" aria-hidden="true"></span>个人资料<span
              class="i-ep-arrow-right"
              aria-hidden="true"
            ></span></RouterLink
          ><RouterLink to="/Password"
            ><span class="i-ep-lock" aria-hidden="true"></span>修改密码<span
              class="i-ep-arrow-right"
              aria-hidden="true"
            ></span></RouterLink
          ><RouterLink to="/AiSupport"
            ><span class="i-ep-chat-dot-round" aria-hidden="true"></span>AI
            客服<span class="i-ep-arrow-right" aria-hidden="true"></span
          ></RouterLink>
        </nav>
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

// 返回首页的数据状态，请求编号用于阻止旧请求覆盖新账户
function homeState() {
  return {
    summary: null,
    loading: false,
    error: "",
    updatedAt: "",
    requestId: 0,
  };
}

// 读取当前账户，作为首页汇总和显示身份的依据
function readUserInfo() {
  return useAppStore().userInfo;
}

// 返回账户昵称，没有昵称时使用账号
function readDisplayName() {
  return account.getDisplayName(this.userInfo);
}

// 返回账户角色的中文名称
function readRoleName() {
  return account.getRoleName(this.userInfo.role);
}

// 返回头像中的第一个字符，长昵称不会挤占账户区域
function readAvatarLetter() {
  return this.displayName.substring(0, 1);
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
      detail: "全部游戏",
      icon: "i-ep-grid",
      path: "/GameCatalog",
      tone: "dashboard-stat-blue",
    },
    {
      title: "我的游戏库",
      value: this.metric("libraryCount"),
      unit: "款",
      detail: "已加入游戏库",
      icon: "i-ep-folder",
      path: "/GameLibrary",
      tone: "dashboard-stat-green",
    },
    {
      title: "我的收藏",
      value: this.metric("favoriteCount"),
      unit: "款",
      detail: "已收藏游戏",
      icon: "i-ep-star",
      path: "/GameFavorites",
      tone: "dashboard-stat-amber",
    },
    {
      title: "游玩记录",
      value: this.metric("recordCount"),
      unit: "次",
      detail: "当前账户的记录",
      icon: "i-ep-document",
      path: "/GameRecords",
      tone: "dashboard-stat-neutral",
    },
  ];
}

// 返回游戏库在全部收录游戏中的比例
function readLibraryPercent() {
  if (!this.summary) {
    return 0;
  }
  return dashboard.collectionPercent(
    this.summary.libraryCount,
    this.summary.catalogCount
  );
}

// 返回收藏在全部收录游戏中的比例
function readFavoritePercent() {
  if (!this.summary) {
    return 0;
  }
  return dashboard.collectionPercent(
    this.summary.favoriteCount,
    this.summary.catalogCount
  );
}

// 返回尚无可用数据时的提示，区别请求中和读取失败
function readPendingText() {
  if (this.error) {
    return "概览读取失败，请刷新重试";
  }
  return "正在读取";
}

// 返回游戏库空状态的文字，成功读取后才判断为空
function readEmptyLibraryText() {
  if (!this.summary) {
    return this.pendingText;
  }
  return "尚未加入游戏";
}

// 返回记录空状态的文字，避免把请求失败当成没有记录
function readEmptyRecordText() {
  if (!this.summary) {
    return this.pendingText;
  }
  return "还没有游玩记录";
}

// 将记录时长换算为分钟，record 是接口返回的一条记录
function recordMinutes(record) {
  const seconds = Math.max(0, Number(record.durationSeconds) || 0);
  return (seconds / 60).toFixed(1);
}

// 同时读取游戏和记录，成功后一次更新概览，旧账户的响应直接丢弃
async function loadDashboard() {
  const store = useAppStore();
  const token = store.userInfo.token;
  this.requestId = this.requestId + 1;
  const requestId = this.requestId;
  this.loading = true;
  this.error = "";
  try {
    const results = await Promise.all([getGames("catalog", ""), getRecords()]);
    if (requestId !== this.requestId || token !== store.userInfo.token) {
      return;
    }
    for (const result of results) {
      if (result.code !== 0 || !Array.isArray(result.data)) {
        throw new Error(result.msg || "游戏概览读取失败");
      }
    }
    const now = new Date();
    this.summary = dashboard.summarizeDashboard(
      results[0].data,
      results[1].data,
      this.userInfo.id,
      now
    );
    this.updatedAt = now.toLocaleTimeString("zh-CN", {
      timeZone: "Asia/Shanghai",
      hour12: false,
    });
  } catch (error) {
    if (requestId === this.requestId && token === store.userInfo.token) {
      this.error = account.getErrorMessage(
        error,
        "游戏概览读取失败，请刷新重试"
      );
    }
  } finally {
    if (requestId === this.requestId) {
      this.loading = false;
    }
  }
}

// 切号后清空旧统计，再读取新账户的数据
function accountChanged() {
  this.summary = null;
  this.updatedAt = "";
  this.loadDashboard();
}

// 离开首页后让尚未完成的响应失效
function invalidateRequest() {
  this.requestId = this.requestId + 1;
}

export default {
  components: { DashboardActivity: DashboardActivity },
  data: homeState,
  computed: {
    userInfo: readUserInfo,
    displayName: readDisplayName,
    roleName: readRoleName,
    avatarLetter: readAvatarLetter,
    statistics: readStatistics,
    libraryPercent: readLibraryPercent,
    favoritePercent: readFavoritePercent,
    pendingText: readPendingText,
    emptyLibraryText: readEmptyLibraryText,
    emptyRecordText: readEmptyRecordText,
  },
  mounted: loadDashboard,
  beforeUnmount: invalidateRequest,
  watch: { "userInfo.username": accountChanged },
  methods: {
    metric: metric,
    recordMinutes: recordMinutes,
    recordTime: dashboard.formatRecordTime,
    loadDashboard: loadDashboard,
  },
};
</script>
