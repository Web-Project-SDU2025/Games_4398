<template>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="account-form-dialog"
      :aria-label="dialogTitle"
      @close="clearForm"
    >
      <button
        class="account-form-close"
        type="button"
        aria-label="关闭弹窗"
        autofocus
        @click="closeDialog"
      >
        <span class="i-ep-close" aria-hidden="true"></span>
      </button>
      <GameUserProfile v-if="form === 'profile'" />
      <Password v-else-if="form === 'password'" />
    </dialog>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onDeactivated, ref } from "vue";
import GameUserProfile from "../views/account/GameUserProfile.vue";
import Password from "../views/info/Password.vue";

const dialog = ref(null);
const form = ref(null);

// 按当前表单设置弹窗的无障碍名称
function readDialogTitle() {
  if (form.value === "password") {
    return "修改密码";
  }
  return "个人资料";
}

const dialogTitle = computed(readDialogTitle);

// 等待表单挂载后再打开原生弹窗，确保焦点可以进入弹窗
async function open(type) {
  const panel = dialog.value;
  if (!panel || panel.open) {
    return;
  }
  form.value = type;
  await nextTick();
  if (dialog.value && !dialog.value.open && form.value === type) {
    dialog.value.showModal();
  }
}

// 关闭即卸载表单，清空未提交的密码
function clearForm() {
  const panel = dialog.value;
  if (!panel || !panel.open) {
    form.value = null;
  }
}

// 点击关闭或离开游戏概览时，收起弹窗并释放表单
function closeDialog() {
  const panel = dialog.value;
  if (panel && panel.open) {
    panel.close();
  }
  clearForm();
}

onDeactivated(closeDialog);
defineExpose({ open: open });
</script>

<style scoped>
.account-form-dialog {
  position: fixed;
  inset: 0;
  width: min(560px, calc(100% - var(--space-lg)));
  max-height: 85dvh;
  box-sizing: border-box;
  margin: auto;
  padding: 0;
  overflow: auto;
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-card);
  background: var(--color-paper-2);
  color: var(--color-ink);
}

.account-form-dialog::backdrop {
  background: var(--color-overlay);
}

.account-form-dialog :deep(section.account-grid-page) {
  box-sizing: border-box;
  padding: var(--space-lg);
}

.account-form-close {
  position: absolute;
  z-index: 1;
  top: 10px;
  right: 12px;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 1px solid var(--color-rule);
  border-radius: var(--radius-control);
  background: var(--color-paper-3);
  color: var(--color-ink);
  font-size: 18px;
}

</style>
