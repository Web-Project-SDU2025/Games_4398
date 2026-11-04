<template>
  <div class="platform-account">
    <label class="workspace-user-menu">
      <span :title="displayName">{{ displayName }}</span>
      <select
        aria-label="切换检查账号"
        :title="selectedLabel"
        :value="userInfo.username"
        :disabled="!enabled || switching"
        @change="switchUser"
      >
        <option v-if="accounts.length === 0" :value="userInfo.username">
          {{ userInfo.username }}
        </option>
        <option
          v-for="item in accounts"
          :key="item.username"
          :value="item.username"
        >
          {{ optionLabel(item) }}
        </option>
      </select>
    </label>
    <button
      class="workspace-logout"
      type="button"
      aria-label="退出登录"
      title="退出登录"
      @click="$emit('logout')"
    >
      <span class="i-ep-switch-button" aria-hidden="true"></span>
    </button>
  </div>
</template>

<script>
import { useAppStore } from "../stores/app.js";
import { getSwitchAccounts } from "../services/userServ.js";
import {
  getDisplayName,
  getRoleName,
  getErrorMessage,
} from "../logic/account.js";

export default {
  props: { userInfo: Object },
  emits: ["logout", "notice"],
  // 账号选择的等待状态与选项只属于这个控件，不放进整个工作区
  data: function switcherState() {
    return { accounts: [], enabled: false, switching: false };
  },
  computed: {
    // 昵称过长时由样式省略，完整内容保留在提示中
    displayName: function displayName() {
      return getDisplayName(this.userInfo);
    },
    // 同时给选择框提供完整文字，极窄屏下仍可查看当前身份
    selectedLabel: function selectedLabel() {
      return this.optionLabel(this.userInfo);
    },
  },
  // 第一次显示控件时读取一次选项，普通页面切换不重复请求
  created: function loadInitialAccounts() {
    this.loadAccounts();
  },
  watch: {
    "userInfo.username": "loadAccounts",
  },
  methods: {
    // 根据目标账户生成单行标签，完整标签同时供提示读取
    optionLabel: function optionLabel(item) {
      return item.username + " · " + getRoleName(item.role);
    },
    // 按当前令牌读取切号选项，失败时清空选项并保留当前登录
    loadAccounts: async function loadAccounts() {
      const token = this.userInfo.token;
      this.enabled = false;
      this.accounts = [];
      try {
        const result = await getSwitchAccounts(token);
        if (token !== this.userInfo.token) {
          return;
        }
        this.enabled = result.enabled;
        this.accounts = result.accounts;
      } catch (error) {
        if (token === this.userInfo.token) {
          this.$emit("notice", "检查账号列表读取失败，当前账号仍可正常使用");
        }
      }
    },
    // 后端凭证和菜单都成功后再切换身份，失败时保留原账号并恢复选择框
    switchUser: async function switchUser(event) {
      const username = event.target.value;
      event.target.value = this.userInfo.username;
      if (
        !this.enabled ||
        this.switching ||
        username === this.userInfo.username
      ) {
        return;
      }
      this.switching = true;
      this.$emit("notice", "正在切换账号");
      try {
        await useAppStore().switchAccount(username);
        await this.$router.replace("/MainPage");
        this.$emit("notice", "");
      } catch (error) {
        this.$emit("notice", getErrorMessage(error, "切号失败，请重新登录"));
      } finally {
        this.switching = false;
      }
    },
  },
};
</script>
