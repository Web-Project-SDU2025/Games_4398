<template>
  <AccountGridPanel class="base_form" title="修改密码" icon="i-ep-lock">
    <form
      class="account-grid-form motion-results"
      :aria-busy="saving"
      @submit.prevent="submitPassword"
    >
      <div v-for="field in passwordFields" :key="field.id" class="password-field">
        <label :for="field.id">{{ field.label }}</label>
        <div class="password-input">
          <input
            :id="field.id"
            v-model="passwords[field.key]"
            :type="passwordInputType(field.key)"
            :autocomplete="field.autocomplete"
            :maxlength="field.maxlength"
            :spellcheck="false"
            autocapitalize="off"
            required
          />
          <button
            class="password-visibility"
            type="button"
            :aria-controls="field.id"
            :aria-label="visibilityLabel(field)"
            @click="toggleVisibility(field.key)"
          >
            <span
              v-if="visible[field.key]"
              class="i-ep-view"
              aria-hidden="true"
            ></span>
            <span v-else class="i-ep-hide" aria-hidden="true"></span>
          </button>
        </div>
      </div>
      <div class="account-grid-actions">
        <button type="submit" :disabled="saving"><span>保存密码</span></button>
        <button type="button" :disabled="saving" @click="reset">
          <span>重置</span>
        </button>
      </div>
      <span v-toast="notice"></span>
    </form>
  </AccountGridPanel>
</template>
<script>
import { updatePassword } from "../../services/systemServ.js";
import * as account from "../../logic/account.js";
import AccountGridPanel from "../../components/AccountGridPanel.vue";

// 密码表单的字段顺序及对应的浏览器自动填充类型
const PASSWORD_FIELDS = [
  {
    id: "old-password",
    key: "oldPassword",
    label: "旧密码",
    autocomplete: "current-password",
  },
  {
    id: "new-password",
    key: "newPassword",
    label: "新密码",
    autocomplete: "new-password",
    maxlength: account.CHANGE_PASSWORD_LIMIT,
  },
  {
    id: "repeat-password",
    key: "checkPassword",
    label: "再次输入新密码",
    autocomplete: "new-password",
    maxlength: account.CHANGE_PASSWORD_LIMIT,
  },
];

/** 返回修改密码表单的数据 */
function passwordState() {
  return {
    passwords: { oldPassword: "", newPassword: "", checkPassword: "" },
    visible: {},
    passwordFields: PASSWORD_FIELDS,
    saving: false,
    notice: "",
  };
}
/** 清空表单中填写的密码 */
function reset() {
  this.passwords = { oldPassword: "", newPassword: "", checkPassword: "" };
  this.visible = {};
  this.notice = "";
}
// 提交密码，保留原有的非空、确认和最大长度检查
async function submitPassword() {
  if (this.saving) {
    return;
  }
  const oldPassword = this.passwords.oldPassword;
  const newPassword = this.passwords.newPassword;
  const checkPassword = this.passwords.checkPassword;
  const error = account.checkPasswordChange(
    oldPassword,
    newPassword,
    checkPassword,
  );
  if (error !== "") {
    this.notice = error;
    return;
  }
  this.saving = true;
  try {
    const result = await updatePassword({ oldPassword: oldPassword, newPassword: newPassword });
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

// 每个密码输入框使用自己的显示状态
function passwordInputType(key) {
  if (this.visible[key]) {
    return "text";
  }
  return "password";
}

// 为图标按钮说明点击后是显示还是隐藏密码
function visibilityLabel(field) {
  if (this.visible[field.key]) {
    return "隐藏" + field.label;
  }
  return "显示" + field.label;
}

// 只切换当前输入框，不影响另外两个密码输入框
function toggleVisibility(key) {
  this.visible[key] = !this.visible[key];
}

export default {
  components: { AccountGridPanel: AccountGridPanel },
  data: passwordState,
  methods: {
    submitPassword: submitPassword,
    reset: reset,
    passwordInputType: passwordInputType,
    visibilityLabel: visibilityLabel,
    toggleVisibility: toggleVisibility,
  },
};
</script>

<style scoped>
.password-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.password-input {
  position: relative;
}

.account-grid-form .password-input input {
  padding-right: 48px;
}

.password-input input::-ms-reveal {
  display: none;
}

.password-visibility {
  position: absolute;
  inset: 1px 1px 1px auto;
  display: grid;
  place-items: center;
  width: 44px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-muted);
  font-size: 19px;
}

.password-visibility:hover {
  color: var(--color-ink);
  background: var(--color-paper-3);
}

.password-visibility:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: -3px;
}
</style>
