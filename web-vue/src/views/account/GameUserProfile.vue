<template>
  <section class="game-page">
    <h2>个人资料</h2>
    <form
      class="game-form motion-results"
      @submit.prevent="save"
      :aria-busy="loaded && loading"
    >
      <label>账号<input :value="form.username" readonly /></label>
      <label>昵称<input v-model="form.name" maxlength="50" required /></label>
      <label
        >邮箱<input v-model="form.email" type="email" maxlength="60"
      /></label>
      <label>电话<input v-model="form.phone" maxlength="20" /></label>
      <label
        >个人签名<textarea v-model="form.signature" maxlength="200"></textarea>
      </label>
      <button type="submit">保存</button>
      <p v-reveal="notice" role="status">{{ notice }}</p>
    </form>
  </section>
</template>
<script>
import { getProfile, saveProfile } from "../../services/gameUserServ";
export default {
  // 创建当前页面独立的数据，避免多个页面共用表单
  data: function data() {
    return { form: {}, notice: "", loading: false, loaded: false };
  },
  // 页面打开时加载初始资料
  created: async function created() {
    this.loading = true;
    try {
      const result = await getProfile();
      if (result.code === 0) {
        this.form = result.data;
        this.loaded = true;
      } else {
        this.notice = result.msg;
      }
    } catch (error) {
      this.notice = "加载失败";
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
      try {
        const result = await saveProfile(this.form);
        if (result.code === 0) {
          this.notice = "保存成功";
        } else {
          this.notice = result.msg;
        }
      } catch (error) {
        this.notice = "保存失败";
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>
