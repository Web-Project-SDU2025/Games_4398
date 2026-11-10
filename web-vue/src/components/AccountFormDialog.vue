<template>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="account-form-dialog"
      :aria-label="form === 'password' ? '修改密码' : '个人资料'"
      @close="clearForm"
    >
      <button
        class="account-form-close"
        type="button"
        aria-label="关闭弹窗"
        autofocus
        @click="dialog.close()"
      >
        <span class="i-ep-close" aria-hidden="true"></span>
      </button>
      <GameUserProfile v-if="form === 'profile'" />
      <Password v-else-if="form === 'password'" />
    </dialog>
  </Teleport>
</template>

<script setup>
import { nextTick, onDeactivated, ref } from "vue";
import GameUserProfile from "../views/account/GameUserProfile.vue";
import Password from "../views/info/Password.vue";

const dialog = ref(null);
const form = ref(null);

async function open(type) {
  if (dialog.value.open) return;
  form.value = type;
  await nextTick();
  if (dialog.value && !dialog.value.open && form.value === type) {
    dialog.value.showModal();
  }
}

// 关闭即卸载表单，停止背景动画，也清空未提交的密码。
function clearForm() {
  if (!dialog.value?.open) form.value = null;
}

onDeactivated(() => {
  if (dialog.value?.open) dialog.value.close();
  clearForm();
});

defineExpose({ open });
</script>

<style scoped>
.account-form-dialog {
  --dialog-gutter: clamp(12px, 2vw, 28px);
  position: fixed;
  inset: 0;
  width: calc((100% - var(--dialog-gutter) * 2) * 0.75);
  height: calc((100dvh - var(--dialog-gutter) * 2) * 0.75);
  box-sizing: border-box;
  margin: auto;
  padding: 0;
  overflow: auto;
  border: 1px solid #4bb9ed;
  background: #07153d;
  color: #e0f8ff;
  box-shadow: 0 0 26px #2966ee70;
  scrollbar-color: #4387b9 #081634;
}

.account-form-dialog::backdrop {
  background: rgb(2 7 22 / 65%);
}

.account-form-dialog :deep(section.account-grid-page) {
  display: grid;
  align-items: center;
  box-sizing: border-box;
  min-height: 100%;
  padding-top: 54px;
}

.account-form-dialog :deep(.account-grid-content) {
  margin-inline: auto;
}

.account-form-close {
  position: absolute;
  z-index: 1;
  top: 10px;
  right: 12px;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid #4bb9ed;
  background: #0b244a;
  color: #a3ecff;
  font-size: 18px;
}

.account-form-close:focus-visible {
  outline: 2px solid #a3ecff;
  outline-offset: 2px;
}
</style>
