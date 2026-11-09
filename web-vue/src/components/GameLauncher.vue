<template>
  <section
    class="installed-games"
    :class="{ 'store-products': commerce }"
    aria-label="游戏条目"
  >
    <span v-toast="notice"></span>
    <article
      v-for="game in visibleGames"
      :key="game.gameId"
      class="installed-game"
    >
      <div
        class="installed-game-cover"
        :class="{ 'is-placeholder': !game.installed }"
      >
        <img :src="game.cover" alt="" width="480" height="270" />
      </div>
      <div class="installed-game-description">
        <div
          v-if="game.category || (commerce && game.owned)"
          class="installed-game-details"
        >
          <span v-if="game.category">{{ game.category }}</span>
          <span v-if="commerce && game.owned" class="installed-game-owned"
            >已入库</span
          >
        </div>
        <h3>{{ game.name }}</h3>
        <p v-if="game.description">{{ game.description }}</p>
        <div v-if="commerce" class="installed-game-commerce">
          <strong v-if="!game.owned" class="installed-game-price"
            >{{ game.price }}<small>字块币</small></strong
          >
          <p v-if="game.rewardLabel" class="installed-game-reward">
            {{ game.rewardLabel }}
          </p>
        </div>
        <div class="game-actions">
          <button
            v-if="commerce && !game.owned"
            type="button"
            class="installed-game-primary"
            :disabled="
              purchasing !== '' || balance === null || balance < game.price
            "
            @click="$emit('purchase', game)"
          >
            <span class="i-ep-shopping-bag" aria-hidden="true"></span>
            <span v-if="purchasing === game.gamePath">正在购买</span>
            <span v-else-if="balance !== null && balance < game.price"
              >字块币不足</span
            >
            <span v-else>购买游戏</span>
          </button>
          <button
            v-else-if="game.owned"
            type="button"
            class="installed-game-primary"
            :disabled="!game.installed"
            :title="launchTitle(game)"
            @click="play(game, false)"
          >
            <span class="i-ep-video-play" aria-hidden="true"></span
            >{{ playLabel(game) }}
          </button>
          <RouterLink v-else class="installed-game-primary" to="/GameCatalog"
            >去商城购买</RouterLink
          >
          <button
            v-if="!commerce"
            type="button"
            :disabled="updating !== ''"
            @click="$emit('favorite', game)"
          >
            <span class="i-ep-star" aria-hidden="true"></span>
            <span v-if="game.favorite">取消收藏</span>
            <span v-else>收藏</span>
          </button>
          <button
            v-if="game.capabilities.editor"
            type="button"
            :disabled="!admin"
            :title="editorTitle"
            @click="play(game, true)"
          >
            关卡编辑器
          </button>
        </div>
      </div>
    </article>
  </section>
</template>

<script>
import { getInstalledGames } from "../services/gameRuntime.js";
import { launcherGames } from "../logic/gameRuntime.js";
import { useAppStore } from "../stores/app.js";
import { useGamePlayerStore } from "../stores/gamePlayer.js";

export default {
  props: {
    catalog: { type: Array, default: emptyCatalog },
    admin: Boolean,
    commerce: Boolean,
    balance: { type: Number, default: null },
    purchasing: { type: String, default: "" },
    updating: { type: String, default: "" },
  },
  emits: ["purchase", "favorite"],
  // 页面只读取安装资料和显示入口，运行窗口由平台公共层统一维护
  data: function launcherState() {
    return { installed: [], notice: "", loading: false };
  },
  computed: {
    // 账户列表与安装资料按入口合并，避免同一游戏出现卡片和表格两份内容
    visibleGames: function visibleGames() {
      return launcherGames(this.catalog, this.installed);
    },
    // 编辑器沿用原权限要求，普通用户可以从悬停提示了解不可用原因
    editorTitle: function editorTitle() {
      if (!this.admin) {
        return "仅管理员可用";
      }
      return "关卡编辑器";
    },
  },
  // 加载游戏清单，页面离开后运行实例继续由公共窗口保留
  mounted: function loadInstalled() {
    this.load();
  },
  methods: {
    // 清单读取失败保留上一次安装资料，重复点击共用正在进行的读取
    load: async function load() {
      if (this.loading) {
        return;
      }
      this.loading = true;
      this.notice = "";
      try {
        this.installed = await getInstalledGames();
      } catch (error) {
        this.notice = error.message;
      } finally {
        this.loading = false;
      }
    },
    // 对已运行的同一游戏提供继续入口，点击后复用原 iframe
    playLabel: function playLabel(game) {
      const player = useGamePlayerStore();
      if (player.game && player.game.gamePath === game.gamePath) {
        return "继续游戏";
      }
      return "开始游戏";
    },
    // 游戏尚未安装时保留资料，启动按钮提供明确提示
    launchTitle: function launchTitle(game) {
      if (!game.installed) {
        return "游戏尚未安装";
      }
      return this.playLabel(game);
    },
    // 校验入口条件后向共用运行仓库发起请求，页面切换本身不会结束游戏
    play: function play(
      game,
      editor,
    ) {
      if (
        !game.installed ||
        (editor && !this.admin) ||
        (!editor && !game.owned)
      ) {
        return;
      }
      const user = useAppStore().userInfo;
      this.notice = useGamePlayerStore().open(
        game,
        user,
        this.$route.path,
        editor,
      );
    },
  },
};

// 每个启动器使用独立空清单，避免多个页面共享可变默认数组
function emptyCatalog() {
  return [];
}
</script>
