<template>
  <section class="store-page">
    <header class="store-heading">
      <div>
        <h1>
          游戏商城<span class="store-count"
            >{{ wallet.games.length }} 款游戏</span
          >
        </h1>
      </div>
      <RouterLink class="store-library-link" to="/GameLibrary"
        >我的游戏库<span class="i-ep-right" aria-hidden="true"></span
      ></RouterLink>
    </header>
    <div class="store-account">
      <div class="store-balance">
        <span class="blockcoin-mark" aria-hidden="true">B</span>
        <div>
          <span>我的字块币 <small>Blockcoin</small></span>
          <strong>{{ formatCoins(wallet.balance) }}<small>BC</small></strong>
        </div>
      </div>
      <button
        class="store-refresh"
        type="button"
        aria-label="刷新商城与余额"
        title="刷新商城与余额"
        @click="load"
      >
        <span class="i-ep-refresh" aria-hidden="true"></span>
      </button>
    </div>
    <div class="store-controls">
      <nav class="store-filters" aria-label="商品范围">
        <button
          v-for="item in modes"
          :key="item.value"
          type="button"
          :aria-pressed="selection === item.value"
          @click="selectMode(item.value)"
        >
          {{ item.label }}
        </button>
      </nav>
      <label class="store-search"
        ><span class="i-ep-search" aria-hidden="true"></span>
        <input v-model="search" aria-label="搜索游戏" placeholder="搜索游戏" />
      </label>
      <select v-model="category" aria-label="游戏分类">
        <option value="">全部分类</option>
        <option v-for="item in categories" :key="item" :value="item">
          {{ item }}
        </option>
      </select>
    </div>
    <span v-toast="notice || wallet.notice"></span>
    <GameLauncher
      :catalog="products"
      :admin="admin"
      commerce
      :balance="wallet.balance"
      :purchasing="purchasing"
      @purchase="buy"
    />
    <p
      v-if="wallet.balance !== null && products.length === 0"
      class="store-empty"
    >
      没有找到匹配的游戏
    </p>
  </section>
</template>

<script>
import GameLauncher from "../../components/GameLauncher.vue";
import { useWalletStore } from "../../stores/wallet.js";
import { useAppStore } from "../../stores/app.js";
import { purchaseGame } from "../../services/storeServ.js";
import { messageConform } from "../../tools/messageBox.js";
import {
  STORE_MODES,
  filterProducts,
  productCategories,
  coinAmount,
} from "../../logic/store.js";

export default {
  name: "GameStore",
  components: { GameLauncher: GameLauncher },
  // 筛选和购买状态属于页面，余额由账户仓库维护
  data: function storeState() {
    return {
      search: "",
      category: "",
      selection: "all",
      modes: STORE_MODES,
      purchasing: "",
      notice: "",
    };
  },
  computed: {
    // 返回当前账户的钱包
    wallet: function readWallet() {
      return useWalletStore();
    },
    // 已入库的游戏仍为管理员提供关卡编辑器入口
    admin: function readAdmin() {
      return useAppStore().userInfo.role === "ROLE_ADMIN";
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
