<template>
  <section class="game-page">
    <h2>{{ title }}</h2>
    <form class="game-toolbar" @submit.prevent="query">
      <label>账号或游戏<input v-model.trim="search" /></label>
      <button type="submit" :disabled="loading">查询</button>
      <button type="button" :disabled="loading" @click="edit(null)">
        新增
      </button>
    </form>
    <p v-reveal="notice" role="status">{{ notice }}</p>
    <div class="game-table-wrap motion-results" :aria-busy="loaded && loading">
      <table class="game-table">
        <thead>
          <tr>
            <th>账号</th>
            <th>游戏</th>
            <th>分数</th>
            <th v-if="!scoreOnly">时长（秒）</th>
            <th>游玩时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in records" :key="record.recordId">
            <td>{{ record.username }}</td>
            <td>{{ record.name }}</td>
            <td>{{ record.score }}</td>
            <td v-if="!scoreOnly">{{ record.durationSeconds }}</td>
            <td>{{ record.playedAt }}</td>
            <td>
              <div class="game-actions">
                <button type="button" @click="edit(record)">修改</button
                ><button type="button" @click="remove(record)">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="records.length === 0">
            <td :colspan="columnCount">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>
    <dialog ref="editor">
      <form class="game-form" @submit.prevent="save">
        <h3>{{ title }}</h3>
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
  deleteAdminRecord,
} from "../../services/adminServ.js";
import { getAccounts } from "../../services/gameUserServ.js";
import { getGames } from "../../services/gameServ.js";
import { createRecordForm } from "../../logic/forms.js";
import { messageConform } from "../../tools/messageBox.js";
import { getErrorMessage } from "../../logic/account.js";
import { RECORD_NUMBER_LIMIT } from "../../logic/limits.js";

export default {
  props: { scoreOnly: Boolean },
  // 分数和记录显示同一份数据，只改变列表展示的列
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
    };
  },
  computed: {
    // 按当前管理分类显示标题
    title: function recordTitle() {
      if (this.scoreOnly) {
        return "游戏分数管理";
      }
      return "游戏记录管理";
    },
    // 空列表单元格覆盖当前所有表格列
    columnCount: function columnCount() {
      if (this.scoreOnly) {
        return 5;
      }
      return 6;
    },
  },
  // 进入管理视图时读取列表
  created: function loadRecords() {
    this.query();
  },
  methods: {
    // 使用现有请求格式查询，等待时保留旧内容
    query: async function query() {
      if (this.loading) {
        return;
      }
      this.loading = true;
      this.notice = "";
      try {
        const result = await getAdminRecords(this.search);
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        this.records = result.data;
        this.loaded = true;
      } catch (error) {
        this.notice = getErrorMessage(error, "读取记录失败");
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
    // 删除整条游玩记录，分数与记录视图同步更新
    remove: async function remove(record) {
      const confirmed = await messageConform("删除这次游玩记录及其分数？");
      if (!confirmed) {
        return;
      }
      try {
        const result = await deleteAdminRecord(record.recordId);
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        await this.query();
      } catch (error) {
        this.notice = getErrorMessage(error, "删除失败");
      }
    },
  },
};
</script>
