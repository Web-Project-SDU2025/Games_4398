<template>
  <section class="game-page account-management">
    <h2>账户管理</h2>
    <form class="game-toolbar" @submit.prevent="query">
      <label>账号或昵称<input v-model="search" /></label
      ><button type="submit">查询</button>
      <button type="button" @click="edit(null)">添加账户</button>
    </form>
    <span v-toast="notice"></span>
    <div class="game-table-wrap motion-results" :aria-busy="loaded && loading">
      <table class="game-table">
        <thead>
          <tr>
            <th>编号</th>
            <th>账号</th>
            <th>昵称</th>
            <th>角色</th>
            <th>字块币</th>
            <th>游戏</th>
            <th>收藏</th>
            <th>记录</th>
            <th>登录次数</th>
            <th>最近登录</th>
            <th>创建时间</th>
            <th>邮箱</th>
            <th>电话</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="account in accounts" :key="account.userId">
            <td>{{ account.userId }}</td>
            <td>{{ account.username }}</td>
            <td>{{ account.name }}</td>
            <td>{{ roleName(account.role) }}</td>
            <td>{{ formatCoins(account.balance) }}</td>
            <td>{{ account.gameCount }}</td>
            <td>{{ account.favoriteCount }}</td>
            <td>{{ account.recordCount }}</td>
            <td>{{ account.loginCount || 0 }}</td>
            <td>{{ account.lastLoginTime || "—" }}</td>
            <td>{{ account.createTime || "—" }}</td>
            <td>{{ account.email }}</td>
            <td>{{ account.phone }}</td>
            <td class="game-actions">
              <button type="button" @click="openGameLibrary(account)">
                查看游戏库
              </button>
              <button type="button" @click="openAdjustment(account)">
                调整字块币
              </button>
              <RouterLink
                :to="{
                  path: '/BlockcoinManage',
                  query: { userId: account.userId },
                }"
                >收支记录</RouterLink
              >
              <button @click="edit(account)">编辑</button>
              <button
                v-if="
                  account.username !== 'admin' &&
                  account.userId !== currentUserId
                "
                @click="remove(account)"
              >
                删除
              </button>
            </td>
          </tr>
          <tr v-if="accounts.length === 0">
            <td colspan="14">暂无账户</td>
          </tr>
        </tbody>
      </table>
    </div>
    <dialog ref="editor">
      <form class="game-form" @submit.prevent="save">
        <h3>账户资料</h3>
        <label
          >账号<input
            v-model="form.username"
            :readonly="editing"
            :maxlength="limits.USERNAME_LIMIT"
            required
        /></label>
        <label v-if="!editing"
          >初始密码<input
            v-model="form.password"
            type="password"
            :maxlength="limits.REGISTER_PASSWORD_LIMIT"
            required
        /></label>
        <label
          >昵称<input
            v-model="form.name"
            :maxlength="limits.NAME_LIMIT"
            required
        /></label>
        <label
          >角色<select
            v-model="form.role"
            :disabled="
              form.username === 'admin' || form.userId === currentUserId
            "
          >
            <option value="ROLE_GAME_USER">游戏用户</option>
            <option value="ROLE_ADMIN">管理员</option>
          </select></label
        >
        <label
          >邮箱<input
            v-model="form.email"
            type="email"
            :maxlength="limits.EMAIL_LIMIT"
        /></label>
        <label
          >电话<input v-model="form.phone" :maxlength="limits.PHONE_LIMIT"
        /></label>
        <label
          >个人签名<textarea
            v-model="form.signature"
            :maxlength="limits.SIGNATURE_LIMIT"
          ></textarea>
        </label>
        <div class="game-actions">
          <button type="submit" :disabled="saving">保存</button
          ><button type="button" :disabled="saving" @click="close">取消</button>
        </div>
        <span v-toast="editorNotice"></span>
      </form>
    </dialog>
    <BlockcoinAdjustment ref="adjustment" @changed="assetsChanged" />
    <AccountGameLibrary ref="library" @changed="assetsChanged" />
  </section>
</template>
<script>
import {
  getAccounts,
  createAccount,
  saveAccount,
  deleteAccount,
} from "../../services/gameUserServ";
import { createAccountForm } from "../../logic/forms.js";
import { getRoleName } from "../../logic/account.js";
import { messageConform } from "../../tools/messageBox.js";
import { useAppStore } from "../../stores/app.js";
import * as limits from "../../logic/limits.js";
import { coinAmount } from "../../logic/store.js";
import BlockcoinAdjustment from "../../components/BlockcoinAdjustment.vue";
import AccountGameLibrary from "../../components/AccountGameLibrary.vue";
import { useWalletStore } from "../../stores/wallet.js";
export default {
  name: "AccountManage",
  components: {
    BlockcoinAdjustment: BlockcoinAdjustment,
    AccountGameLibrary: AccountGameLibrary,
  },
  // 创建当前页面的独立表单，重新进入页面时从空状态开始
  data: function data() {
    return {
      limits: limits,
      search: "",
      accounts: [],
      form: {},
      editing: false,
      notice: "",
      editorNotice: "",
      loading: false,
      loaded: false,
      saving: false,
    };
  },
  // 页面打开时加载初始资料
  created: function created() {
    this.query();
  },
  computed: {
    // 返回当前账户编号，删除按钮据此保留当前登录账户
    currentUserId: function currentUserId() {
      return useAppStore().userInfo.id;
    },
  },
  methods: {
    // 查看目标账户的持有游戏，弹窗负责读取与撤回确认
    openGameLibrary: function openGameLibrary(account) {
      this.$refs.library.open(account);
    },
    // 余额调整弹窗与原资料编辑表单分别维护
    openAdjustment: function openAdjustment(account) {
      this.$refs.adjustment.open(account);
    },
    // 操作完成后更新账户表，修改当前账户时同步商城和个人列表缓存
    assetsChanged: async function assetsChanged(userId) {
      await this.query();
      const store = useAppStore();
      if (store.userInfo.id === userId) {
        store.dataChanged("games");
        store.dataChanged("records");
        await useWalletStore().refresh();
      }
    },
    // 把账户角色转成中文名称
    roleName: function roleName(role) {
      return getRoleName(role);
    },
    // 查询账户
    query: async function query() {
      if (this.loading) {
        return;
      }
      this.loading = true;
      this.notice = "";
      const token = useAppStore().userInfo.token;
      try {
        const result = await getAccounts(this.search);
        if (token !== useAppStore().userInfo.token) {
          return;
        }
        if (result.code === 0) {
          this.accounts = result.data;
          this.loaded = true;
        } else {
          this.notice = result.msg;
        }
      } catch (error) {
        if (token === useAppStore().userInfo.token) {
          this.notice = "加载失败";
        }
      } finally {
        this.loading = false;
      }
    },
    // 编辑账户，使用独立表单保存输入
    edit: function edit(account) {
      this.editing = false;
      this.editorNotice = "";
      this.form = createAccountForm(account);
      if (account) {
        this.editing = true;
      }
      this.$refs.editor.showModal();
    },
    // 保存账户
    save: async function save() {
      if (this.saving) {
        return;
      }
      this.saving = true;
      try {
        let result;
        if (this.editing) {
          result = await saveAccount(this.form);
        } else {
          result = await createAccount(this.form);
        }
        if (result.code !== 0) {
          this.editorNotice = result.msg;
          return;
        }
        this.close();
        await this.query();
      } catch (error) {
        this.editorNotice = "保存失败";
      } finally {
        this.saving = false;
      }
    },
    // 关闭当前页面的对话框
    close: function close() {
      this.$refs.editor.close();
    },
    // 删除游戏用户
    remove: async function remove(account) {
      const confirmed = await messageConform(
        "确认删除账号“" + account.username + "”？",
      );
      if (!confirmed) {
        return;
      }
      try {
        const result = await deleteAccount(account.userId);
        if (result.code !== 0) {
          this.notice = result.msg;
          return;
        }
        await this.query();
      } catch (error) {
        this.notice = "删除失败";
      }
    },
    formatCoins: coinAmount,
  },
};
</script>
