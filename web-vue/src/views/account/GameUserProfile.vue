<template>
  <section class="game-page">
    <h2>个人资料</h2>
    <form class="game-form" @submit.prevent="save">
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
      <p role="status">{{ notice }}</p>
    </form>
  </section>
</template>
<script>
import { getProfile, saveProfile } from "../../services/gameUserServ";
export default {
  data: function () {
    return { form: {}, notice: "" };
  },
  created: async function () {
    try {
      const result = await getProfile();
      if (result.code === 0) {
        this.form = result.data;
      } else {
        this.notice = result.msg;
      }
    } catch (error) {
      this.notice = "加载失败";
    }
  },
  methods: {
    // 保存当前账户的个人资料。
    save: async function () {
      try {
        const result = await saveProfile(this.form);
        if (result.code === 0) {
          this.notice = "保存成功";
        } else {
          this.notice = result.msg;
        }
      } catch (error) {
        this.notice = "保存失败";
      }
    },
  },
};
</script>
