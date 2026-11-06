<template>
  <section class="game-page blockcoin-management">
    <h2>字块币收支管理</h2>
    <form class="game-toolbar" @submit.prevent="searchEntries">
      <label
        >账户
        <select v-model="filters.userId">
          <option value="">全部账户</option>
          <option
            v-for="account in accounts"
            :key="account.userId"
            :value="account.userId"
          >
            {{ account.username }} · {{ account.name }}
          </option>
        </select>
      </label>
      <label
        >方向
        <select v-model="filters.direction">
          <option value="all">全部收支</option>
          <option value="income">收入</option>
          <option value="expense">支出</option>
        </select>
      </label>
      <label
        >原因<input v-model.trim="filters.search" :maxlength="searchLimit"
      /></label>
      <label>开始日期<input v-model="filters.startDate" type="date" /></label>
      <label>结束日期<input v-model="filters.endDate" type="date" /></label>
      <button type="submit" :disabled="loading">查询</button>
      <button type="button" :disabled="loading" @click="reset">重置</button>
    </form>
    <p v-if="notice" role="status">{{ notice }}</p>
    <div class="game-table-wrap motion-results" :aria-busy="loading">
      <table class="game-table">
        <thead>
          <tr>
            <th>流水编号</th>
            <th>账户编号</th>
            <th>账号</th>
            <th>昵称</th>
            <th>方向</th>
            <th>字块币</th>
            <th>交易后余额</th>
            <th>原因</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in entries" :key="entry.entryId">
            <td>{{ entry.entryId }}</td>
            <td>{{ entry.userId }}</td>
            <td>{{ entry.username || "已删除账户" }}</td>
            <td>{{ entry.name }}</td>
            <td>
              <span v-if="entry.amount > 0">收入</span><span v-else>支出</span>
            </td>
            <td
              :class="{
                'admin-income': entry.amount > 0,
                'admin-expense': entry.amount < 0,
              }"
            >
              <span v-if="entry.amount > 0">+</span
              >{{ formatCoins(entry.amount) }}
            </td>
            <td>{{ formatCoins(entry.balanceAfter) }}</td>
            <td>{{ entry.reason }}</td>
            <td>{{ formatTime(entry.createdAt) }}</td>
          </tr>
          <tr v-if="loaded && entries.length === 0">
            <td colspan="9">暂无收支记录</td>
          </tr>
        </tbody>
      </table>
    </div>
    <nav class="admin-pagination" aria-label="收支分页">
      <span>共 {{ total }} 笔</span>
      <label
        >每页
        <select
          v-model.number="filters.pageSize"
          :disabled="loading"
          @change="searchEntries"
        >
          <option v-for="size in pageSizes" :key="size" :value="size">
            {{ size }}
          </option>
        </select>
      </label>
      <button
        type="button"
        :disabled="loading || filters.page <= 1"
        @click="previousPage"
      >
        上一页
      </button>
      <span>{{ filters.page }} / {{ pageCount }}</span>
      <button
        type="button"
        :disabled="loading || filters.page >= pageCount"
        @click="nextPage"
      >
        下一页
      </button>
    </nav>
  </section>
</template>

<script>
import { getAdminBlockcoin } from "../../services/adminServ.js";
import { getAccounts } from "../../services/gameUserServ.js";
import { useAppStore } from "../../stores/app.js";
import { coinAmount, transactionTime } from "../../logic/store.js";
import {
  ADMIN_PAGE_SIZES,
  ADMIN_SEARCH_LIMIT,
  blockcoinFilters,
} from "../../logic/admin.js";

export default {
  name: "BlockcoinManage",
  // 筛选与查询结果分别保存，请求期间继续显示上一次成功读取的内容
  data: function blockcoinState() {
    return {
      filters: blockcoinFilters(),
      accounts: [],
      entries: [],
      total: 0,
      loading: false,
      loaded: false,
      notice: "",
      pageSizes: ADMIN_PAGE_SIZES,
      searchLimit: ADMIN_SEARCH_LIMIT,
      requestId: 0,
    };
  },
  computed: {
    // 空结果仍保留第一页，末页之后的操作由按钮状态限制
    pageCount: function pageCount() {
      return Math.max(1, Math.ceil(this.total / this.filters.pageSize));
    },
  },
  // 从账户表跳转时带入指定账户，直接打开时读取全部账户
  created: function initialiseBlockcoin() {
    this.selectRouteAccount();
    this.loadAccounts();
    this.query();
  },
  watch: {
    // 同一页面切换到账户表中的另一账户时同步查询
    "$route.query.userId": function routeAccountChanged() {
      this.selectRouteAccount();
      this.searchEntries();
    },
  },
  methods: {
    // 账户筛选只接受正整数，异常链接按全部账户处理
    selectRouteAccount: function selectRouteAccount() {
      const id = Number(this.$route.query.userId);
      this.filters.userId = "";
      if (Number.isInteger(id) && id > 0) {
        this.filters.userId = id;
      }
    },
    // 账户选项与账户管理共用接口，切号后的旧结果直接丢弃
    loadAccounts: async function loadAccounts() {
      const token = useAppStore().userInfo.token;
      try {
        const result = await getAccounts("");
        if (token !== useAppStore().userInfo.token) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        this.accounts = result.data;
      } catch (error) {
        if (token === useAppStore().userInfo.token) {
          this.notice = error.message || "账户读取失败";
        }
      }
    },
    // 查询一页流水，服务器返回的页码与每页条数作为实际分页依据
    query: async function query() {
      this.requestId = this.requestId + 1;
      const requestId = this.requestId;
      this.loading = true;
      this.notice = "";
      const token = useAppStore().userInfo.token;
      const filters = Object.assign({}, this.filters);
      try {
        const result = await getAdminBlockcoin(filters);
        if (
          token !== useAppStore().userInfo.token ||
          requestId !== this.requestId
        ) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        this.entries = result.data.entries;
        this.total = result.data.total;
        this.filters.page = result.data.page;
        this.filters.pageSize = result.data.pageSize;
        this.loaded = true;
      } catch (error) {
        if (
          token === useAppStore().userInfo.token &&
          requestId === this.requestId
        ) {
          this.notice = error.message || "收支记录读取失败";
        }
      } finally {
        if (requestId === this.requestId) {
          this.loading = false;
        }
      }
    },
    // 改变筛选后回到第一页，原有末页编号随本次条件重置
    searchEntries: function searchEntries() {
      this.filters.page = 1;
      return this.query();
    },
    // 清空筛选并恢复共用分页默认值
    reset: function reset() {
      this.filters = blockcoinFilters();
      return this.query();
    },
    // 上一页按钮与方法都检查边界，重复点击期间保留当前页码
    previousPage: function previousPage() {
      if (!this.loading && this.filters.page > 1) {
        this.filters.page = this.filters.page - 1;
        return this.query();
      }
    },
    // 下一页沿用当前筛选，最后一页保持原页码
    nextPage: function nextPage() {
      if (!this.loading && this.filters.page < this.pageCount) {
        this.filters.page = this.filters.page + 1;
        return this.query();
      }
    },
    formatCoins: coinAmount,
    formatTime: transactionTime,
  },
};
</script>
