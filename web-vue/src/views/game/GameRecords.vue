<template>
  <section class="game-page">
    <h2>游戏记录</h2>
    <div class="game-toolbar">
      <button @click="query">刷新</button
      ><button @click="open">添加记录</button>
    </div>
    <p role="status">{{ notice }}</p>
    <div class="game-table-wrap">
      <table class="game-table">
        <thead>
          <tr>
            <th>账号</th>
            <th>游戏</th>
            <th>分数</th>
            <th>时长（秒）</th>
            <th>游玩时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in records" :key="record.recordId">
            <td>{{ record.username }}</td>
            <td>{{ record.name }}</td>
            <td>{{ record.score }}</td>
            <td>{{ record.durationSeconds }}</td>
            <td>{{ record.playedAt }}</td>
            <td><button @click="remove(record)">删除</button></td>
          </tr>
          <tr v-if="records.length === 0">
            <td colspan="6">暂无游戏记录</td>
          </tr>
        </tbody>
      </table>
    </div>
    <!-- 对话框显示 -->
    <dialog ref="editor">
      <form class="game-form" @submit.prevent="save">
        <h3>添加游戏记录</h3>
        <label
          >游戏<select v-model="form.gameId" required>
            <option value="">请选择</option>
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
            step="1"
            required
        /></label>
        <label
          >时长（秒）<input
            v-model.number="form.durationSeconds"
            type="number"
            min="0"
            step="1"
            required
        /></label>
        <div class="game-actions">
          <button type="submit">保存</button
          ><button type="button" @click="close">取消</button>
        </div>
        <p role="status">{{ editorNotice }}</p>
      </form>
    </dialog>
  </section>
</template>
<script>
import {
  getRecords,
  getGames,
  saveRecord,
  deleteRecord,
} from "../../services/gameServ";
export default {
  data: function () {
    return { records: [], games: [], form: {}, notice: "", editorNotice: "" };
  },
  created: function () {
    this.query();
  },
  methods: {
    // 获取游戏记录。
    query: async function () {
      try {
        const result = await getRecords();
        if (result.code === 0) {
          this.records = result.data;
        } else {
          this.notice = result.msg;
        }
      } catch (error) {
        this.notice = "加载失败";
      }
    },
    // 添加记录时，只显示当前游戏库中的游戏。
    open: async function () {
      this.editorNotice = "";
      try {
        const result = await getGames("library", "");
        if (result.code !== 0) {
          this.notice = result.msg;
          return;
        }
        this.games = result.data;
        this.form = { gameId: "", score: 0, durationSeconds: 0 };
        this.$refs.editor.showModal();
      } catch (error) {
        this.notice = "游戏库加载失败";
      }
    },
    // 保存游戏记录。
    save: async function () {
      try {
        const result = await saveRecord(this.form);
        if (result.code !== 0) {
          this.editorNotice = result.msg;
          return;
        }
        this.close();
        await this.query();
      } catch (error) {
        this.editorNotice = "保存失败";
      }
    },
    close: function () {
      this.$refs.editor.close();
    },
    // 删除游戏记录。
    remove: async function (record) {
      if (!window.confirm("确认删除这条游戏记录？")) {
        return;
      }
      try {
        const result = await deleteRecord(record.recordId);
        if (result.code !== 0) {
          this.notice = result.msg;
          return;
        }
        await this.query();
      } catch (error) {
        this.notice = "删除失败";
      }
    },
  },
};
</script>
