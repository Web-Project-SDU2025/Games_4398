<template>
  <dialog
    ref="dialog"
    class="admin-asset-dialog admin-library-dialog"
    aria-labelledby="account-library-title"
    @cancel="cancel"
  >
    <header class="admin-library-heading">
      <h3 id="account-library-title">{{ account.username }} 的游戏库</h3>
      <button
        type="button"
        title="关闭游戏库"
        aria-label="关闭游戏库"
        :disabled="saving"
        @click="close"
      >
        <span class="i-ep-close" aria-hidden="true"></span>
      </button>
    </header>
    <form class="game-toolbar" @submit.prevent="query">
      <label>游戏名称<input v-model.trim="search" :disabled="saving" /></label>
      <button type="submit" :disabled="saving || loading">查询</button>
    </form>
    <span v-toast="notice"></span>
    <div class="game-table-wrap" :aria-busy="loading">
      <table class="game-table">
        <thead>
          <tr>
            <th>游戏编号</th>
            <th>游戏名称</th>
            <th>分类</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="game in visibleGames" :key="game.gameId">
            <td>{{ game.num }}</td>
            <td>{{ game.name }}</td>
            <td>{{ game.category }}</td>
            <td>
              <button
                type="button"
                :disabled="saving"
                @click="selectGame(game)"
              >
                撤回
              </button>
            </td>
          </tr>
          <tr v-if="!loading && visibleGames.length === 0">
            <td colspan="4">暂无游戏</td>
          </tr>
        </tbody>
      </table>
    </div>
    <form
      v-if="selected"
      class="game-form admin-revocation-form"
      @submit.prevent="revoke"
    >
      <h3>撤回 {{ selected.name }}</h3>
      <label
        >原因<textarea
          v-model.trim="reason"
          :maxlength="reasonLimit"
          :disabled="saving"
          required
        ></textarea>
      </label>
      <label class="admin-revocation-confirm">
        <input
          v-model="confirmed"
          type="checkbox"
          :disabled="saving"
          required
        />确认撤回，不退还字块币
      </label>
      <div class="game-actions">
        <button type="submit" :disabled="saving || !confirmed">确定</button>
        <button type="button" :disabled="saving" @click="clearSelection">
          取消
        </button>
      </div>
    </form>
  </dialog>
</template>

<script>
import { getAccountLibrary, revokeAccountGame } from "../services/adminServ.js";
import { useAppStore } from "../stores/app.js";
import { ADMIN_REASON_LIMIT, assetOperation } from "../logic/admin.js";

export default {
  emits: ["changed"],
  // 目标账户、列表和撤回确认各自保存，查看过程只读取资料
  data: function libraryState() {
    return {
      account: {},
      token: "",
      games: [],
      selected: null,
      search: "",
      reason: "",
      confirmed: false,
      operation: null,
      notice: "",
      loading: false,
      saving: false,
      requestId: 0,
      reasonLimit: ADMIN_REASON_LIMIT,
    };
  },
  computed: {
    // 搜索只改变当前列表的可见条目，撤回仍使用真实游戏编号
    visibleGames: function visibleGames() {
      const result = [];
      const search = this.search.toLowerCase();
      for (const game of this.games) {
        const name = game.name.toLowerCase();
        if (name.includes(search)) {
          result.push(game);
        }
      }
      return result;
    },
  },
  methods: {
    // 打开另一个账户时清空旧数据，并丢弃旧列表请求的迟到响应
    open: function open(account) {
      if (this.saving) {
        return;
      }
      this.account = { userId: account.userId, username: account.username };
      this.token = useAppStore().userInfo.token;
      this.games = [];
      this.search = "";
      this.clearSelection();
      this.$refs.dialog.showModal();
      return this.query();
    },
    // 列表请求按序号和登录令牌校验，关闭后返回的旧数据直接丢弃
    query: async function query() {
      this.requestId = this.requestId + 1;
      const requestId = this.requestId;
      this.loading = true;
      this.notice = "";
      try {
        const result = await getAccountLibrary(this.account.userId);
        if (
          this.token !== useAppStore().userInfo.token ||
          requestId !== this.requestId
        ) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        this.games = result.data;
      } catch (error) {
        if (
          this.token === useAppStore().userInfo.token &&
          requestId === this.requestId
        ) {
          this.notice = error.message || "游戏库读取失败";
        }
      } finally {
        if (requestId === this.requestId) {
          this.loading = false;
        }
      }
    },
    // 选中游戏后显示原因与退款规则，明确确认后再执行撤回
    selectGame: function selectGame(game) {
      this.clearSelection();
      this.selected = { gameId: game.gameId, name: game.name };
    },
    // 取消本次选择，原有游戏库和购买流水保持原值
    clearSelection: function clearSelection() {
      this.selected = null;
      this.reason = "";
      this.confirmed = false;
      this.operation = null;
      this.notice = "";
    },
    // 撤回完成后刷新当前账户游戏库，网络重试保持同一操作编号
    revoke: async function revoke() {
      if (
        this.saving ||
        !this.selected ||
        !this.confirmed ||
        this.token !== useAppStore().userInfo.token
      ) {
        return;
      }
      if (!this.reason || this.reason.length > ADMIN_REASON_LIMIT) {
        this.notice = "请填写撤回原因";
        return;
      }
      const values = {
        userId: this.account.userId,
        gameId: this.selected.gameId,
        reason: this.reason,
      };
      this.operation = assetOperation(values, this.operation);
      values.requestId = this.operation.requestId;
      this.saving = true;
      try {
        const result = await revokeAccountGame(values);
        if (this.token !== useAppStore().userInfo.token) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        this.clearSelection();
        this.$emit("changed", this.account.userId);
        await this.query();
      } catch (error) {
        if (this.token === useAppStore().userInfo.token) {
          this.notice = error.message || "撤回失败";
        }
      } finally {
        this.saving = false;
      }
    },
    // 提交期间保留弹窗，其他取消动作同步结束旧列表请求
    cancel: function cancel(event) {
      if (this.saving) {
        event.preventDefault();
      } else {
        this.requestId = this.requestId + 1;
      }
    },
    // 关闭查看窗口，并使等待中的列表请求失效
    close: function close() {
      if (!this.saving) {
        this.requestId = this.requestId + 1;
        this.$refs.dialog.close();
      }
    },
  },
};
</script>
