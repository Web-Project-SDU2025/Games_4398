<template>
  <AccountGridPanel class="game-page" title="个人资料" icon="i-ep-postcard">
    <form
      class="account-grid-form motion-results"
      @submit.prevent="save"
      :aria-busy="loaded && loading"
    >
      <label>账号<input :value="form.username" readonly /></label>
      <label
        >昵称<input v-model="form.name" :maxlength="limits.NAME_LIMIT" required
      /></label>
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
      <div class="account-grid-actions">
        <button type="submit"><span>保存</span></button>
      </div>
      <span v-toast="notice"></span>
    </form>
  </AccountGridPanel>
</template>
<script>
import { getProfile, saveProfile } from "../../services/gameUserServ";
import * as limits from "../../logic/limits.js";
import { useAppStore } from "../../stores/app.js";
import AccountGridPanel from "../../components/AccountGridPanel.vue";
export default {
  components: { AccountGridPanel },
  // 创建独立资料表单，加载结果只填入当前页面
  data: function data() {
    return {
      form: {},
      notice: "",
      loading: false,
      loaded: false,
      limits: limits,
    };
  },
  // 页面打开时加载初始资料
  created: async function created() {
    const token = useAppStore().userInfo.token;
    this.loading = true;
    try {
      const result = await getProfile();
      if (token !== useAppStore().userInfo.token) {
        return;
      }
      if (result.code === 0) {
        this.form = result.data;
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
  methods: {
    // 保存当前账户的个人资料
    save: async function save() {
      if (this.loading) {
        return;
      }
      this.loading = true;
      const store = useAppStore();
      const token = store.userInfo.token;
      const profile = Object.assign({}, this.form);
      try {
        const result = await saveProfile(profile);
        if (token !== store.userInfo.token) {
          return;
        }
        if (result.code === 0) {
          // 保存接口返回操作结果，已提交字段同步到当前资料，登录信息继续保留
          store.profile = profile;
          store.userInfo.perName = profile.name;
          this.notice = "保存成功";
        } else {
          this.notice = result.msg;
        }
      } catch (error) {
        if (token === store.userInfo.token) {
          this.notice = "保存失败";
        }
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>
