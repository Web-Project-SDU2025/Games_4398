<template>
  <section class="store-page">
    <header class="store-heading">
      <div>
        <h1>游戏商城</h1>
        <span class="store-count">{{ wallet.games.length }} 款游戏</span>
      </div>
      <div class="store-heading-actions">
        <div class="store-balance" aria-label="我的字块币">
          <span class="i-ep-coin" aria-hidden="true"></span>
          <strong>{{ formatCoins(wallet.balance) }} BC</strong>
        </div>
        <button
          class="store-refresh"
          type="button"
          aria-label="刷新商城与余额"
          title="刷新商城与余额"
          :disabled="Boolean(wallet.pending)"
          @click="load"
        >
          <span class="i-ep-refresh" aria-hidden="true"></span>
        </button>
        <RouterLink class="store-library-link" to="/GameLibrary">
          <span class="i-ep-folder" aria-hidden="true"></span>我的游戏库
        </RouterLink>
      </div>
    </header>
    <nav v-if="admin" class="store-views" aria-label="商城视图">
      <RouterLink to="/GameCatalog" :aria-current-value="productViewCurrent">商品</RouterLink>
      <RouterLink
        :to="managementRoute"
        :aria-current-value="managementViewCurrent"
      >游戏资料管理</RouterLink>
    </nav>
    <span v-toast="notice || wallet.notice"></span>
    <GameManagement v-if="managing" />
    <section v-else class="store-catalog" aria-label="商城商品" :aria-busy="Boolean(wallet.pending)">
      <div class="store-controls">
        <div class="store-filters" role="group" aria-label="商品范围">
          <button
            v-for="item in modes"
            :key="item.value"
            type="button"
            :aria-pressed="selection === item.value"
            @click="selectMode(item.value)"
          >{{ item.label }}</button>
        </div>
        <div class="store-fields">
          <label class="store-search">
            <span class="i-ep-search" aria-hidden="true"></span>
            <input v-model="search" aria-label="搜索游戏" placeholder="搜索游戏" />
          </label>
          <select v-model="category" aria-label="游戏分类">
            <option value="">全部分类</option>
            <option v-for="item in categories" :key="item" :value="item">{{ item }}</option>
          </select>
        </div>
      </div>
      <GameLauncher
        v-if="products.length"
        :catalog="products"
        :admin="admin"
        commerce
        :balance="wallet.balance"
        :purchasing="purchasing"
        @purchase="buy"
      />
      <p v-else-if="wallet.pending" class="store-empty" role="status">正在加载游戏</p>
      <p v-else-if="wallet.notice" class="store-empty" role="status">{{ wallet.notice }}</p>
      <p v-else-if="wallet.balance !== null" class="store-empty" role="status">没有找到匹配的游戏</p>
    </section>
  </section>
</template>

<script>
import GameLauncher from "../../components/GameLauncher.vue";
import GameManagement from "./GameManagement.vue";
import { useWalletStore } from "../../stores/wallet.js";
import { useAppStore } from "../../stores/app.js";
import { purchaseGame } from "../../services/storeServ.js";
import { messageConform } from "../../tools/messageBox.js";
import { GAME_MANAGEMENT_ROUTE, isGameManagementRoute } from "../../logic/navigation.js";
import {
  STORE_MODES,
  filterProducts,
  productCategories,
  coinAmount,
} from "../../logic/store.js";

export default {
  name: "GameStore",
  components: {
    GameLauncher: GameLauncher,
    GameManagement: GameManagement,
  },
  // 筛选和购买状态属于页面，余额由账户仓库维护
  data: function storeState() {
    return {
      search: "",
      category: "",
      selection: "all",
      modes: STORE_MODES,
      managementRoute: GAME_MANAGEMENT_ROUTE,
      purchasing: "",
      notice: "",
    };
  },
  computed: {
    // 商城和顶栏共用同一份余额与商品目录
    wallet: function readWallet() {
      return useWalletStore();
    },
    // 管理视图和关卡编辑器沿用当前账号的管理员权限
    admin: function readAdmin() {
      return useAppStore().userInfo.role === "ROLE_ADMIN";
    },
    // 即使有人直接打开管理链接，商城也只向管理员显示资料表
    managing: function readManaging() {
      return this.admin && isGameManagementRoute(this.$route);
    },
    // 当前显示商品时，给商品链接标记选中状态
    productViewCurrent: function readProductViewCurrent() {
      if (!this.managing) {
        return "page";
      }
      return "false";
    },
    // 当前显示资料表时，给管理链接标记选中状态
    managementViewCurrent: function readManagementViewCurrent() {
      if (this.managing) {
        return "page";
      }
      return "false";
    },
    // 同时应用搜索、分类和持有范围
    products: function readProducts() {
      return filterProducts(
        this.wallet.games,
        this.search,
        this.category,
        this.selection,
      );
    },
    // 分类选项由商品表生成
    categories: function readCategories() {
      return productCategories(this.wallet.games);
    },
  },
  // 进入商城读取余额，购买与通关后沿用同一刷新入口
  mounted: function loadStore() {
    this.load();
  },
  watch: {
    "$route.query.search": {
      immediate: true,
      // 大厅的「查看游戏」用现有搜索展示对应商品
      handler: function showLinkedGame(search, previous) {
        if (typeof search !== "string" && typeof previous !== "string") {
          return;
        }
        this.search = "";
        if (typeof search === "string") {
          this.search = search;
        }
        this.category = "";
        this.selection = "all";
      },
    },
  },
  methods: {
    // 切换筛选范围，保留控件焦点
    selectMode: function selectMode(value) {
      this.selection = value;
    },
    // 网络失败保留已有商品，具体原因由钱包仓库显示
    load: async function load() {
      await this.wallet.refresh();
    },
    // 明确确认金额后提交购买，重复点击与中途切号分别处理
    buy: async function buy(game) {
      if (this.purchasing) {
        return;
      }
      const token = useAppStore().userInfo.token;
      this.notice = "";
      this.purchasing = game.gamePath;
      try {
        const confirmed = await messageConform(
          "使用 " + game.price + " 字块币购买《" + game.name + "》？",
        );
        if (!confirmed || token !== useAppStore().userInfo.token) {
          return;
        }
        const result = await purchaseGame(game.gameId);
        if (token !== useAppStore().userInfo.token) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        this.notice = result.msg;
        await this.load();
      } catch (error) {
        this.notice = error.message || "购买失败，请稍后重试";
      } finally {
        this.purchasing = "";
      }
    },
    formatCoins: coinAmount,
  },
};
</script>
