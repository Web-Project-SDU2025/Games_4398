<template>
  <section class="game-page">
    <h2>{{ title }}</h2>
    <form class="game-toolbar" @submit.prevent="query">
      <label>游戏编号或名称 <input v-model="search" /></label>
      <button type="submit" :disabled="loading">查询</button>
    </form>
    <span v-toast="notice"></span>
    <div class="motion-results" :aria-busy="loading">
      <GameLauncher
        v-if="games.length > 0"
        :catalog="games"
        :admin="admin"
        :updating="updating"
        @favorite="toggleFavorite"
      />
      <div v-else-if="loaded" class="catalog-empty">
        <img
          src="/images/game-collection.png"
          alt="电脑上的游戏库"
          width="772"
          height="528"
        />
        <h3>这里还没有游戏</h3>
        <RouterLink
          class="platform-secondary-button"
          to="/GameCatalog"
          >浏览游戏商城</RouterLink
        >
      </div>
    </div>
    <BlockcoinHistory v-if="mode === 'library'" :history="wallet.history" />
  </section>
</template>

<script>
import { getGames, changeFavorite } from "../../services/gameServ.js";
import { useAppStore } from "../../stores/app";
import GameLauncher from "../../components/GameLauncher.vue";
import BlockcoinHistory from "../../components/BlockcoinHistory.vue";
import { useWalletStore } from "../../stores/wallet.js";

export default {
  name: "GameList",
  components: {
    GameLauncher: GameLauncher,
    BlockcoinHistory: BlockcoinHistory,
  },
  props: {
    mode: String,
    title: String,
  },
  // 游戏库和收藏各自保存查询结果，返回页面时按数据版本判断是否刷新
  data: function gameListState() {
    return {
      search: "",
      games: [],
      loading: false,
      loaded: false,
      loadedRevision: -1,
      notice: "",
      updating: "",
    };
  },
  computed: {
    // 游戏库与顶栏共用当前账户的资金记录，切号时由钱包仓库更新
    wallet: function readWallet() {
      return useWalletStore();
    },
    // 启动器只向管理员开放关卡编辑器入口
    admin: function isAdmin() {
      return useAppStore().userInfo.role === "ROLE_ADMIN";
    },
  },
  // 已加载且没有数据修改时直接显示缓存，不因切换标签再次刷新
  activated: function loadGames() {
    const revision = useAppStore().gameRevision;
    if (!this.loaded || this.loadedRevision !== revision) {
      return this.query();
    }
  },
  methods: {
    // 按当前页面和关键词查询游戏，失败时保留上次成功加载的列表
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
        await this.wallet.refresh();
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
