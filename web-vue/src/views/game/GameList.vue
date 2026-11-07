<template>
  <section class="game-page catalog-page">
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
    <span v-toast="notice"></span>
    <div class="motion-results" :aria-busy="loading">
      <GameLauncher
        v-if="showLauncher && games.length > 0"
        :catalog="games"
        :admin="admin"
        :updating="updating"
        @favorite="toggleFavorite"
      />
      <div v-if="loaded && games.length === 0" class="catalog-empty">
        <img
          src="/images/game-collection.png"
          alt="电脑上的游戏库"
          width="772"
          height="528"
        />
        <h3>这里还没有游戏</h3>
        <RouterLink
          v-if="mode !== 'catalog'"
          class="platform-secondary-button"
          to="/GameCatalog"
          >浏览游戏商城</RouterLink
        >
        <button v-else-if="admin" type="button" @click="editGame(null)">
          添加第一个游戏
        </button>
      </div>
      <div v-else-if="loaded && !showLauncher" class="game-table-wrap">
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
              <td>
                <span class="catalog-game-name"
                  ><img src="/favicon.svg" alt="" width="40" height="40" />{{
                    game.name
                  }}</span
                >
              </td>
              <td>{{ game.category }}</td>
              <td>{{ game.description }}</td>
              <td class="game-actions">
                <template v-if="admin && mode === 'catalog'">
                  <button @click="editGame(game)">编辑</button
                  ><button @click="removeGame(game)">删除</button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <BlockcoinHistory v-if="mode === 'library'" :history="wallet.history" />
    <dialog ref="editor">
      <form class="game-form" @submit.prevent="confirmGame">
        <h3>游戏资料</h3>
        <label
          >游戏编号<input
            v-model="form.num"
            :maxlength="limits.GAME_NUMBER_LIMIT"
            required
        /></label>
        <label
          >游戏名称<input
            v-model="form.name"
            :maxlength="limits.NAME_LIMIT"
            required
        /></label>
        <label
          >分类<input
            v-model="form.category"
            :maxlength="limits.GAME_CATEGORY_LIMIT"
        /></label>
        <label
          >介绍<textarea
            v-model="form.description"
            :maxlength="limits.GAME_DESCRIPTION_LIMIT"
          ></textarea>
        </label>
        <label
          >入口路径<input
            v-model="form.gamePath"
            :maxlength="limits.GAME_PATH_LIMIT"
        /></label>
        <div class="game-actions">
          <button type="submit">保存</button
          ><button type="button" @click="close">取消</button>
        </div>
        <span v-toast="editorNotice"></span>
      </form>
    </dialog>
  </section>
</template>

<script>
import {
  getGames,
  saveGame,
  deleteGame,
  changeFavorite,
} from "../../services/gameServ";
import { useAppStore } from "../../stores/app";
import { createGameForm } from "../../logic/forms.js";
import { messageConform } from "../../tools/messageBox.js";
import * as limits from "../../logic/limits.js";
import GameLauncher from "../../components/GameLauncher.vue";
import BlockcoinHistory from "../../components/BlockcoinHistory.vue";
import { useWalletStore } from "../../stores/wallet.js";

export default {
  name: "GameList",
  components: {
    GameLauncher: GameLauncher,
    BlockcoinHistory: BlockcoinHistory,
  },
  emits: ["changed"],
  props: {
    mode: String,
    title: String,
    showLauncher: { type: Boolean, default: true },
  },
  // 双向绑定数据
  data: function gameListState() {
    return {
      limits: limits,
      search: "",
      games: [],
      loading: false,
      loaded: false,
      loadedRevision: -1,
      notice: "",
      editorNotice: "",
      form: {},
      updating: "",
    };
  },
  computed: {
    // 游戏库与顶栏共用当前账户的资金记录，切号时由钱包仓库更新
    wallet: function readWallet() {
      return useWalletStore();
    },
    // 管理员可以维护游戏资料
    admin: function isAdmin() {
      return useAppStore().userInfo.role === "ROLE_ADMIN";
    },
  },
  // 常驻资料表在挂载时读取，游戏库等缓存页面继续使用激活流程
  mounted: function mountGameList() {
    if (!this.showLauncher) {
      this.query();
    }
  },
  // 已加载且没有数据修改时直接显示缓存，不因切换标签再次刷新
  activated: function loadGames() {
    const revision = useAppStore().gameRevision;
    if (!this.loaded || this.loadedRevision !== revision) {
      return this.query();
    }
  },
  methods: {
    // 查询当前游戏列表
    query: async function query() {
      if (this.loading) {
        return;
      }
      this.loading = true;
      this.notice = "";
      const revision = useAppStore().gameRevision;
      try {
        const result = await getGames(this.mode, this.search);
        if (result.code === 0) {
          this.games = result.data;
          this.loaded = true;
          this.loadedRevision = revision;
        } else {
          this.notice = result.msg;
        }
      } catch (error) {
        this.notice = "加载失败，请检查网络或重新登录";
      } finally {
        this.loading = false;
      }
    },
    // 编辑时复制游戏字段，取消操作保留列表原值
    editGame: function editGame(game) {
      this.editorNotice = "";
      this.form = createGameForm(game);
      this.$refs.editor.showModal();
    },
    // 关闭对话框
    close: function close() {
      this.$refs.editor.close();
    },
    // 保存游戏，失败时保留填写的内容
    confirmGame: async function confirmGame() {
      try {
        const result = await saveGame(this.form);
        if (result.code !== 0) {
          this.editorNotice = result.msg;
          return;
        }
        this.close();
        await this.query();
        this.$emit("changed");
      } catch (error) {
        this.editorNotice = "保存失败";
      }
    },
    // 删除游戏
    removeGame: async function removeGame(game) {
      const confirmed = await messageConform(
        "删除游戏“" +
          game.name +
          "”后，将同时清理所有账户对此游戏的游戏库、收藏和游玩记录。确认删除？",
      );
      if (!confirmed) {
        return;
      }
      try {
        const result = await deleteGame(game.gameId);
        if (result.code !== 0) {
          this.notice = result.msg;
          return;
        }
        await this.query();
        this.$emit("changed");
        this.notice = "已删除";
      } catch (error) {
        this.notice = "删除失败";
      }
    },
    // 收藏状态与游戏条目一起显示，写入后同步游戏库、收藏与商城缓存
    toggleFavorite: async function toggleFavorite(game) {
      if (this.updating) {
        return;
      }
      this.updating = String(game.gameId);
      const token = useAppStore().userInfo.token;
      try {
        const result = await changeFavorite(game.gameId, !game.favorite);
        if (token !== useAppStore().userInfo.token) {
          return;
        }
        if (result.code !== 0) {
          this.notice = result.msg;
          return;
        }
        await this.query();
        await useWalletStore().refresh();
      } catch (error) {
        if (token === useAppStore().userInfo.token) {
          this.notice = "操作失败";
        }
      } finally {
        this.updating = "";
      }
    },
  },
};
</script>
