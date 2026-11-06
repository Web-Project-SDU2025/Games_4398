<template>
  <section class="base_form password-page">
    <div class="base_header"><h2 class="base_title">修改密码</h2></div>
    <form
      class="motion-results"
      :aria-busy="saving"
      @submit.prevent="submitPassword"
    >
      <label for="old-password"
        >旧密码<input
          id="old-password"
          v-model="oldPassword"
          type="password"
          autocomplete="current-password"
          required
      /></label>
      <label for="new-password"
        >新密码<input
          id="new-password"
          v-model="newPassword"
          type="password"
          autocomplete="new-password"
          :maxlength="passwordLimit"
          required
      /></label>
      <label for="repeat-password"
        >再次输入新密码<input
          id="repeat-password"
          v-model="checkPassword"
          type="password"
          autocomplete="new-password"
          :maxlength="passwordLimit"
          required
      /></label>
      <div class="password-actions">
        <button class="commButton" type="submit" :disabled="saving">
          保存密码</button
        ><button
          class="commButton"
          type="button"
          :disabled="saving"
          @click="reset"
        >
          重置
        </button>
      </div>
      <p v-reveal="notice" role="status">{{ notice }}</p>
    </form>
  </section>
</template>
<script>
import { updatePassword } from "../../services/systemServ.js";
import * as account from "../../logic/account.js";

/** 返回修改密码表单的数据 */
function passwordState() {
  return {
    oldPassword: "",
    newPassword: "",
    checkPassword: "",
    saving: false,
    notice: "",
    passwordLimit: account.CHANGE_PASSWORD_LIMIT,
  };
}
/** 清空表单中填写的密码 */
function reset() {
  this.oldPassword = "";
  this.newPassword = "";
  this.checkPassword = "";
  this.notice = "";
}
// 提交密码，保留原有的非空、确认和最大长度检查
async function submitPassword() {
  if (this.saving) {
    return;
  }
  const error = account.checkPasswordChange(
    this.oldPassword,
    this.newPassword,
    this.checkPassword,
  );
  if (error !== "") {
    this.notice = error;
    return;
  }
  this.saving = true;
  try {
    const result = await updatePassword({
      oldPassword: this.oldPassword,
      newPassword: this.newPassword,
    });
    if (result.code === 0) {
      this.reset();
      this.notice = "密码已修改";
    } else {
      this.notice = result.msg;
    }
  } catch (error) {
    this.notice = "修改失败，请检查网络后重试";
  } finally {
    this.saving = false;
  }
}
export default {
  data: passwordState,
  methods: { submitPassword: submitPassword, reset: reset },
};
</script>
