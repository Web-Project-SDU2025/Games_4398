<template>
  <!-- 账户表单区域 -->
  <section class="game-page">
    <!-- 页面标题 -->
    <h2>用户管理</h2>
    <!-- 查询区域 -->
    <form class="game-toolbar" @submit.prevent="query">
      <label>账号或昵称<input v-model="search" /></label
      ><button type="submit">查询</button>
      <button type="button" @click="edit(null)">添加账户</button>
    </form>
    <p role="status">{{ notice }}</p>
    <!-- 账户数据表格区域 -->
    <div class="game-table-wrap">
      <table class="game-table">
        <thead>
          <tr>
            <th>账号</th>
            <th>昵称</th>
            <th>角色</th>
            <th>邮箱</th>
            <th>电话</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="account in accounts" :key="account.userId">
            <td>{{ account.username }}</td>
            <td>{{ account.name }}</td>
            <td>{{ roleName(account.role) }}</td>
            <td>{{ account.email }}</td>
            <td>{{ account.phone }}</td>
            <td class="game-actions">
              <button @click="edit(account)">编辑</button
              ><button
                v-if="account.role !== 'ROLE_ADMIN'"
                @click="remove(account)"
              >
                删除
              </button>
            </td>
          </tr>
          <tr v-if="accounts.length === 0">
            <td colspan="6">暂无账户</td>
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
            maxlength="20"
            required
        /></label>
        <label v-if="!editing"
          >初始密码<input
            v-model="form.password"
            type="password"
            maxlength="60"
            required
        /></label>
        <label>昵称<input v-model="form.name" maxlength="50" required /></label>
        <label v-if="!editing"
          >角色<select v-model="form.role">
            <option value="ROLE_GAME_USER">游戏用户</option>
            <option value="ROLE_ADMIN">管理员</option>
          </select></label
        >
        <label
          >邮箱<input v-model="form.email" type="email" maxlength="60"
        /></label>
        <label>电话<input v-model="form.phone" maxlength="20" /></label>
        <label
          >个人签名<textarea
            v-model="form.signature"
            maxlength="200"
          ></textarea>
        </label>
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
  getAccounts,
  createAccount,
  saveProfile,
  deleteAccount,
} from "../../services/gameUserServ";
export default {
  data: function () {
    return {
      search: "",
      accounts: [],
      form: {},
      editing: false,
      notice: "",
      editorNotice: "",
    };
  },
  created: function () {
    this.query();
  },
  methods: {
    roleName: function (role) {
      if (role === "ROLE_ADMIN") {
        return "管理员";
      }
      return "游戏用户";
    },
    // 查询账户。
    query: async function () {
      try {
        const result = await getAccounts(this.search);
        if (result.code === 0) {
          this.accounts = result.data;
        } else {
          this.notice = result.msg;
        }
      } catch (error) {
        this.notice = "加载失败";
      }
    },
    // 编辑账户，使用独立表单保存输入。
    edit: function (account) {
      this.editing = false;
      this.editorNotice = "";
      this.form = {
        username: "",
        password: "",
        name: "",
        email: "",
        phone: "",
        signature: "",
        role: "ROLE_GAME_USER",
      };
      if (account) {
        this.editing = true;
        const fields = [
          "userId",
          "username",
          "name",
          "email",
          "phone",
          "signature",
          "role",
        ];
        for (const field of fields) {
          this.form[field] = account[field];
        }
      }
      this.$refs.editor.showModal();
    },
    // 保存账户。
    save: async function () {
      try {
        let result;
        if (this.editing) {
          result = await saveProfile(this.form);
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
      }
    },
    close: function () {
      this.$refs.editor.close();
    },
    // 删除游戏用户。
    remove: async function (account) {
      if (!window.confirm("确认删除账号“" + account.username + "”？")) {
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
  },
};
</script>
