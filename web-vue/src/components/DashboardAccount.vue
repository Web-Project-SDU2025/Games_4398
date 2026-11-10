<template>
  <section
    class="dashboard-section dashboard-account"
    aria-labelledby="account-title"
  >
    <header class="dashboard-section-heading">
      <h2 id="account-title">
        <span class="i-ep-user" aria-hidden="true"></span>我的账户
      </h2>
    </header>
    <div class="dashboard-account-overview">
      <div class="dashboard-profile">
        <span class="dashboard-profile-avatar" aria-hidden="true">{{
          avatarLetter
        }}</span>
        <div>
          <strong>{{ displayName }}</strong>
          <span>{{ userInfo.username }} · {{ roleName }}</span>
        </div>
      </div>
      <RouterLink class="dashboard-wallet" to="/GameCatalog">
        <span><span class="i-ep-coin" aria-hidden="true"></span>字块币</span>
        <strong>{{ formatCoins(wallet.balance) }}<small>BC</small></strong>
      </RouterLink>
    </div>
    <span v-toast:error="wallet.notice"></span>
    <div class="dashboard-signature">
      <span>个性签名</span>
      <p>{{ signature }}</p>
    </div>
    <dl class="dashboard-account-details">
      <div v-for="field in details" :key="field.label">
        <dt>{{ field.label }}</dt>
        <dd>{{ field.value }}</dd>
      </div>
    </dl>
    <nav class="dashboard-account-links" aria-label="账户功能">
      <button
        type="button"
        aria-haspopup="dialog"
        @click="$refs.accountForm.open('profile')"
      >
        <span class="i-ep-postcard" aria-hidden="true"></span>个人资料
        <span class="i-ep-arrow-right" aria-hidden="true"></span>
      </button>
      <button
        type="button"
        aria-haspopup="dialog"
        @click="$refs.accountForm.open('password')"
      >
        <span class="i-ep-lock" aria-hidden="true"></span>修改密码
        <span class="i-ep-arrow-right" aria-hidden="true"></span>
      </button>
      <RouterLink to="/AiSupport"
        ><span class="i-ep-chat-dot-round" aria-hidden="true"></span>AI
        客服<span class="i-ep-arrow-right" aria-hidden="true"></span
      ></RouterLink>
    </nav>
    <AccountFormDialog :key="userInfo.token" ref="accountForm" />
  </section>
</template>

<script>
import { useAppStore } from "../stores/app.js";
import { useWalletStore } from "../stores/wallet.js";
import {
  getDisplayName,
  getRoleName,
  profileDetails,
} from "../logic/account.js";
import { coinAmount } from "../logic/store.js";
import AccountFormDialog from "./AccountFormDialog.vue";

export default {
  components: { AccountFormDialog },
  computed: {
    // 登录身份和后端资料按当前账号同步，切号时重新读取
    userInfo: function userInfo() {
      return useAppStore().userInfo;
    },
    // 资料共用身份请求结果，首次响应前返回空资料供字段显示空值
    profile: function profile() {
      return useAppStore().profile;
    },
    // 昵称和顶栏保持一致，空昵称使用账号
    displayName: function displayName() {
      return getDisplayName(this.userInfo);
    },
    // 原始角色转成与其他页面一致的中文名称
    roleName: function roleName() {
      return getRoleName(this.userInfo.role);
    },
    // 使用昵称首字符作为头像，昵称全文保留在相邻位置
    avatarLetter: function avatarLetter() {
      return this.displayName.substring(0, 1);
    },
    // 签名按完整文字换行，尚未填写时保留统一空值符号
    signature: function signature() {
      if (this.profile && this.profile.signature) {
        return this.profile.signature;
      }
      return "—";
    },
    // 基础资料字段与空值规则在账户逻辑中统一维护
    details: function details() {
      return profileDetails(this.profile);
    },
    // 余额与顶栏、商城和游戏库读取同一钱包
    wallet: function wallet() {
      return useWalletStore();
    },
  },
  methods: { formatCoins: coinAmount },
};
</script>
