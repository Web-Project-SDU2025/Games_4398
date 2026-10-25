<template>
  <section class="game-page">
    <h2>{{ title }}</h2>
    <form class="game-toolbar" @submit.prevent="query">
      <label>游戏编号或名称 <input v-model="search" /></label>
      <button type="submit" :disabled="loading">查询</button>
      <button
        v-if="admin && mode === 'catalog'"
        type="button"
        @click="editGame(null)"
      >
        添加游戏
      </button>
    </form>
    <p role="status">{{ notice }}</p>
    <div class="game-table-wrap" :aria-busy="loading">
      <table class="game-table">
        <thead>
          <tr>
            <th>游戏编号</th>
            <th>游戏名称</th>
            <th>分类</th>
            <th>介绍</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="game in games" :key="game.gameId">
            <td>{{ game.num }}</td>
            <td>{{ game.name }}</td>
            <td>{{ game.category }}</td>
            <td>{{ game.description }}</td>
            <td class="game-actions">
              <button
                v-if="!game.owned"
                @click="setCollection(game, 'library', true)"
              >
                加入游戏库
              </button>
              <button v-else @click="setCollection(game, 'library', false)">
                移出游戏库
              </button>
              <button
                v-if="!game.favorite"
                @click="setCollection(game, 'favorites', true)"
              >
                收藏
              </button>
              <button v-else @click="setCollection(game, 'favorites', false)">
                取消收藏
              </button>
              <template v-if="admin && mode === 'catalog'">
                <button @click="editGame(game)">编辑</button
                ><button @click="removeGame(game)">删除</button>
              </template>
            </td>
          </tr>
          <tr v-if="!loading && games.length === 0">
            <td colspan="5">暂无游戏</td>
          </tr>
        </tbody>
      </table>
    </div>
    <!-- 对话框显示 -->
    <dialog ref="editor">
      <form class="game-form" @submit.prevent="confirmGame">
        <h3>游戏资料</h3>
        <label
          >游戏编号<input v-model="form.num" maxlength="20" required
        /></label>
        <label
          >游戏名称<input v-model="form.name" maxlength="50" required
        /></label>
        <label>分类<input v-model="form.category" maxlength="50" /></label>
        <label
          >介绍<textarea v-model="form.description" maxlength="1000"></textarea>
        </label>
        <label>入口路径<input v-model="form.gamePath" maxlength="255" /></label>
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
  getGames,
  saveGame,
  deleteGame,
  changeCollection,
} from "../../services/gameServ";
import { useAppStore } from "../../stores/app";

export default {
  props: { mode: String, title: String },
  // 双向绑定数据。
  data: function () {
    return {
      search: "",
      games: [],
      loading: false,
      notice: "",
      editorNotice: "",
      form: {},
    };
  },
  computed: {
    admin: function () {
      return useAppStore().userInfo.role === "ROLE_ADMIN";
    },
  },
  watch: { mode: "query" },
  // 初始加载游戏列表。
  created: function () {
    this.query();
  },
  methods: {
    // 查询当前游戏列表。
    query: async function () {
      this.loading = true;
      this.notice = "";
      try {
        const result = await getGames(this.mode, this.search);
        if (result.code === 0) {
          this.games = result.data;
        } else {
          this.notice = result.msg;
        }
      } catch (error) {
        this.notice = "加载失败，请检查网络或重新登录";
      } finally {
        this.loading = false;
      }
    },
    // 编辑游戏时复制表单，取消后不会改变列表内容。
    editGame: function (game) {
      this.editorNotice = "";
      this.form = {
        num: "",
        name: "",
        category: "",
        description: "",
        gamePath: "",
      };
      if (game) {
        this.form.gameId = game.gameId;
        this.form.num = game.num;
        this.form.name = game.name;
        this.form.category = game.category;
        this.form.description = game.description;
        this.form.gamePath = game.gamePath;
      }
      this.$refs.editor.showModal();
    },
    // 关闭对话框。
    close: function () {
      this.$refs.editor.close();
    },
    // 保存游戏，失败时保留填写的内容。
    confirmGame: async function () {
      try {
        const result = await saveGame(this.form);
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
    // 删除游戏。
    removeGame: async function (game) {
      if (!window.confirm("确认删除游戏“" + game.name + "”？")) {
        return;
      }
      try {
        const result = await deleteGame(game.gameId);
        if (result.code !== 0) {
          this.notice = result.msg;
          return;
        }
        await this.query();
      } catch (error) {
        this.notice = "删除失败";
      }
    },
    setCollection: async function (game, mode, added) {
      try {
        const result = await changeCollection(game.gameId, mode, added);
        if (result.code !== 0) {
          this.notice = result.msg;
          return;
        }
        await this.query();
      } catch (error) {
        this.notice = "操作失败";
      }
    },
  },
};
</script>
