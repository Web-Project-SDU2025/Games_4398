<template>
  <section class="game-page store-management">
    <h2>游戏资料管理</h2>
    <form class="game-toolbar" @submit.prevent="queryGames">
      <label>游戏编号或名称 <input v-model="search" /></label>
      <button type="submit" :disabled="loading">查询</button>
      <button class="game-manage-add" type="button" @click="editGame(null)">
        添加游戏
      </button>
    </form>
    <span v-toast="notice"></span>
    <div class="motion-results" :aria-busy="loading">
      <p v-if="loaded && games.length === 0" class="game-manage-empty">
        <template v-if="search">没有找到匹配的游戏</template>
        <template v-else>还没有游戏资料</template>
      </p>
      <div v-else-if="loaded" class="game-table-wrap">
        <table class="game-table game-manage-table">
          <thead>
            <tr>
              <th>游戏编号</th>
              <th>游戏名称</th>
              <th>分类</th>
              <th>介绍</th>
              <th>每次通关奖励</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="game in games" :key="game.gameId">
              <td>{{ game.num }}</td>
              <td>
                <span class="catalog-game-name">
                  <img src="/favicon.svg" alt="" width="40" height="40" />{{ game.name }}
                </span>
              </td>
              <td>{{ game.category }}</td>
              <td>{{ game.description }}</td>
              <td>{{ game.levelReward }} BC</td>
              <td>
                <div class="game-actions">
                  <button type="button" @click="editGame(game)">编辑</button>
                  <button type="button" @click="removeGame(game)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <dialog ref="editor" aria-labelledby="game-edit-title">
      <form class="game-form" @submit.prevent="confirmGame">
        <h3 id="game-edit-title">游戏资料</h3>
        <label>
          游戏编号<input v-model="form.num" :maxlength="limits.GAME_NUMBER_LIMIT" required />
        </label>
        <label>
          游戏名称<input v-model="form.name" :maxlength="limits.NAME_LIMIT" required />
        </label>
        <label>
          分类<input v-model="form.category" :maxlength="limits.GAME_CATEGORY_LIMIT" />
        </label>
        <label>
          介绍<textarea v-model="form.description" :maxlength="limits.GAME_DESCRIPTION_LIMIT"></textarea>
        </label>
        <label>
          入口路径<input v-model="form.gamePath" :maxlength="limits.GAME_PATH_LIMIT" />
        </label>
        <label>
          每次通关奖励（字块币）<input
            v-model.number="form.levelReward"
            type="number"
            min="0"
            :max="limits.GAME_REWARD_LIMIT"
            step="1"
            required
          />
        </label>
        <div class="game-actions">
          <button type="submit">保存</button>
          <button type="button" @click="closeEditor">取消</button>
        </div>
        <span v-toast="editorNotice"></span>
      </form>
    </dialog>
  </section>
</template>

<script>
import { getGames, saveGame, deleteGame } from "../../services/gameServ.js";
import { useAppStore } from "../../stores/app.js";
import { useWalletStore } from "../../stores/wallet.js";
import { createGameForm } from "../../logic/forms.js";
import * as limits from "../../logic/limits.js";
import { messageConform } from "../../tools/messageBox.js";

// 为商城管理视图创建独立的列表和编辑草稿
function managementState() {
  return {
    limits: limits,
    search: "",
    games: [],
    loaded: false,
    loading: false,
    notice: "",
    editorNotice: "",
    form: createGameForm(null),
  };
}

// 按关键词读取商城目录，查询失败时保留上次的结果
async function queryGames() {
  if (this.loading) {
    return;
  }
  this.loading = true;
  this.notice = "";
  const store = useAppStore();
  const token = store.userInfo.token;
  try {
    const result = await getGames("catalog", this.search);
    if (token !== store.userInfo.token) {
      return;
    }
    if (result.code === 0) {
      this.games = result.data;
      this.loaded = true;
    } else {
      this.notice = result.msg;
    }
  } catch (error) {
    if (token === store.userInfo.token) {
      this.notice = "加载失败，请检查网络或重新登录";
    }
  } finally {
    this.loading = false;
  }
}

// 复制选中的游戏资料作为草稿，新增时使用空表单
function editGame(game) {
  this.editorNotice = "";
  this.form = createGameForm(game);
  this.$refs.editor.showModal();
}

// 切换商城视图或关闭页面时，先收起仍打开的编辑框
function closeEditor() {
  const editor = this.$refs.editor;
  if (editor && editor.open) {
    editor.close();
  }
}

// 写入成功后同步管理表和商城商品；切号后不显示旧操作的结果
async function refreshManagement(token, message) {
  const wallet = useWalletStore();
  const refresh = this.queryGames();
  await Promise.all([refresh, wallet.refresh()]);
  if (token === useAppStore().userInfo.token && !this.notice) {
    this.notice = message;
  }
}

// 保存编辑草稿，失败时保持对话框打开，方便管理员继续修改
async function confirmGame() {
  const store = useAppStore();
  const token = store.userInfo.token;
  try {
    const result = await saveGame(this.form);
    if (token !== store.userInfo.token) {
      return;
    }
    if (result.code !== 0) {
      this.editorNotice = result.msg;
      return;
    }
    this.closeEditor();
    await this.refreshManagement(token, "游戏资料已更新");
  } catch (error) {
    if (token === store.userInfo.token) {
      this.editorNotice = "保存失败";
    }
  }
}

// 管理员确认影响范围后才提交删除，取消或切换账户时不发送请求
async function removeGame(game) {
  const store = useAppStore();
  const token = store.userInfo.token;
  const warning = "确认删除“" + game.name +
    "”并清理所有账户中与它关联的游戏库条目、收藏和游玩记录？";
  const confirmed = await messageConform(warning);
  if (!confirmed || token !== store.userInfo.token) {
    return;
  }
  try {
    const result = await deleteGame(game.gameId);
    if (token !== store.userInfo.token) {
      return;
    }
    if (result.code !== 0) {
      this.notice = result.msg;
      return;
    }
    await this.refreshManagement(token, "已删除");
  } catch (error) {
    if (token === store.userInfo.token) {
      this.notice = "删除失败";
    }
  }
}

export default {
  name: "GameManagement",
  data: managementState,
  mounted: queryGames,
  beforeUnmount: closeEditor,
  methods: {
    queryGames: queryGames,
    editGame: editGame,
    closeEditor: closeEditor,
    confirmGame: confirmGame,
    removeGame: removeGame,
    refreshManagement: refreshManagement,
  },
};
</script>
