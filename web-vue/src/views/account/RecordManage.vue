<template>
  <section class="game-page">
    <h2>游戏记录管理</h2>
    <form class="game-toolbar" @submit.prevent="query">
      <label>账号或游戏<input v-model.trim="search" /></label>
      <button type="submit" :disabled="loading">查询</button>
      <button type="button" :disabled="loading" @click="edit(null)">
        新增
      </button>
      <label class="game-checkbox">
        <input v-model="showHidden" type="checkbox" />显示隐藏记录
      </label>
    </form>
    <p v-reveal="notice" role="status">{{ notice }}</p>
    <div class="game-table-wrap motion-results" :aria-busy="loaded && loading">
      <table class="game-table">
        <thead>
          <tr>
            <th>账号</th>
            <th>游戏</th>
            <th>记录</th>
            <th>奖励（BC）</th>
            <th>分数</th>
            <th>时长（秒）</th>
            <th>游玩时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in displayedRecords" :key="record.recordId">
            <td>{{ record.username }}</td>
            <td>{{ record.name }}</td>
            <td>{{ describeRecord(record) }}</td>
            <td>{{ record.reward || 0 }}</td>
            <td>{{ record.score }}</td>
            <td>{{ record.durationSeconds }}</td>
            <td>{{ record.playedAt }}</td>
            <td>
              <div class="game-actions">
                <button
                  v-if="!record.automatic"
                  type="button"
                  @click="edit(record)"
                >
                  修改
                </button>
                <button
                  v-if="record.hidden"
                  type="button"
                  :disabled="updating !== null"
                  @click="changeVisibility(record, false)"
                >
                  恢复显示
                </button>
                <button
                  v-else
                  type="button"
                  :disabled="updating !== null"
                  @click="changeVisibility(record, true)"
                >
                  隐藏
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="displayedRecords.length === 0">
            <td colspan="8">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>
    <dialog ref="editor">
      <form class="game-form" @submit.prevent="save">
        <h3>游戏记录</h3>
        <label
          >账号<select v-model.number="form.userId" aria-label="账号" required>
            <option value="">请选择账号</option>
            <option
              v-for="user in users"
              :key="user.userId"
              :value="user.userId"
            >
              {{ user.username }} · {{ user.name }}
            </option>
          </select></label
        >
        <label
          >游戏<select v-model.number="form.gameId" aria-label="游戏" required>
            <option value="">请选择游戏</option>
            <option
              v-for="game in games"
              :key="game.gameId"
              :value="game.gameId"
            >
              {{ game.name }}
            </option>
          </select></label
        >
        <label
          >分数<input
            v-model.number="form.score"
            type="number"
            min="0"
            :max="recordNumberLimit"
            step="1"
            required
        /></label>
        <label
          >时长（秒）<input
            v-model.number="form.durationSeconds"
            type="number"
            min="0"
            :max="recordNumberLimit"
            step="1"
            required
        /></label>
        <label
          >游玩时间<input
            v-model="form.playedAt"
            type="datetime-local"
            step="1"
        /></label>
        <p v-reveal="editorNotice" role="status">{{ editorNotice }}</p>
        <div class="game-actions">
          <button type="submit" :disabled="saving">保存</button
          ><button type="button" :disabled="saving" @click="close">取消</button>
        </div>
      </form>
    </dialog>
  </section>
</template>
<script>
import {
  getAdminRecords,
  saveAdminRecord,
  hideAdminRecord,
  restoreAdminRecord,
} from "../../services/adminServ.js";
import { getAccounts } from "../../services/gameUserServ.js";
import { getGames } from "../../services/gameServ.js";
import { createRecordForm } from "../../logic/forms.js";
import { messageConform } from "../../tools/messageBox.js";
import { getErrorMessage } from "../../logic/account.js";
import { RECORD_NUMBER_LIMIT } from "../../logic/limits.js";
import { useAppStore } from "../../stores/app.js";
import {
  recordDescription,
  visibleRecords,
  recordActionError,
} from "../../logic/records.js";

export default {
  name: "RecordManage",
  // 记录的分数、时长与时间在同一表单维护
  data: function recordManagementState() {
    return {
      recordNumberLimit: RECORD_NUMBER_LIMIT,
      search: "",
      records: [],
      users: [],
      games: [],
      form: {},
      loading: false,
      loaded: false,
      saving: false,
      notice: "",
      editorNotice: "",
      showHidden: false,
      updating: null,
    };
  },
  computed: {
    // 管理员查看完整或可见记录时使用同一份原始结果
    displayedRecords: function displayedRecords() {
      return visibleRecords(this.records, this.showHidden);
    },
  },
  // 进入管理视图时读取列表
  created: function loadRecords() {
    this.query();
  },
  methods: {
    describeRecord: recordDescription,
    // 使用现有请求格式查询，等待时保留旧内容
    query: async function query() {
      if (this.loading) {
        return;
      }
      this.loading = true;
      this.notice = "";
      const token = useAppStore().userInfo.token;
      try {
        const result = await getAdminRecords(this.search);
        if (token !== useAppStore().userInfo.token) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        this.records = result.data;
        this.loaded = true;
      } catch (error) {
        if (token === useAppStore().userInfo.token) {
          this.notice = getErrorMessage(error, "读取记录失败");
        }
      } finally {
        this.loading = false;
      }
    },
    // 使用服务器实际账号和游戏作为选项，失败时不打开无效表单
    edit: async function edit(record) {
      if (this.loading) {
        return;
      }
      this.loading = true;
      try {
        const accounts = await getAccounts("");
        const games = await getGames("catalog", "");
        if (accounts.code !== 0 || games.code !== 0) {
          throw new Error("账号或游戏选项读取失败");
        }
        this.users = accounts.data;
        this.games = games.data;
        this.form = createRecordForm(record);
        this.editorNotice = "";
        this.$refs.editor.showModal();
      } catch (error) {
        this.notice = getErrorMessage(error, "表单读取失败");
      } finally {
        this.loading = false;
      }
    },
    // 保存成功才关闭，失败时保留用户输入
    save: async function save() {
      if (this.saving) {
        return;
      }
      this.saving = true;
      try {
        const result = await saveAdminRecord(this.form);
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        this.close();
        await this.query();
      } catch (error) {
        this.editorNotice = getErrorMessage(error, "保存失败");
      } finally {
        this.saving = false;
      }
    },
    // 关闭原生对话框，焦点交还给打开它的按钮
    close: function close() {
      this.$refs.editor.close();
    },
    // 隐藏与恢复按当前管理员保存，确认期间切号则结束本次操作
    changeVisibility: async function changeVisibility(record, hidden) {
      if (this.updating !== null) {
        return;
      }
      const token = useAppStore().userInfo.token;
      if (hidden) {
        const confirmed = await messageConform("确认隐藏这条游戏记录？");
        if (!confirmed) {
          return;
        }
      }
      if (token !== useAppStore().userInfo.token) {
        return;
      }
      this.updating = record.recordId;
      this.notice = "";
      try {
        let result;
        if (hidden) {
          result = await hideAdminRecord(record.recordId);
        } else {
          result = await restoreAdminRecord(record.recordId);
        }
        if (token !== useAppStore().userInfo.token) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        await this.query();
      } catch (error) {
        if (token === useAppStore().userInfo.token) {
          this.notice = recordActionError(error);
        }
      } finally {
        this.updating = null;
      }
    },
  },
};
</script>
