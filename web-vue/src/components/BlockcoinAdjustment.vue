<template>
  <dialog
    ref="dialog"
    class="admin-asset-dialog"
    aria-labelledby="coin-adjustment-title"
    @cancel="cancel"
  >
    <form class="game-form" @submit.prevent="submit">
      <h3 id="coin-adjustment-title">调整字块币</h3>
      <label>账户<input :value="account.username" readonly /></label>
      <fieldset class="admin-operation-modes" :disabled="saving">
        <legend>操作</legend>
        <label
          ><input
            v-model="form.direction"
            type="radio"
            value="grant"
          />发放</label
        >
        <label
          ><input
            v-model="form.direction"
            type="radio"
            value="reclaim"
          />收回</label
        >
      </fieldset>
      <label
        >数量（BC）
        <input
          v-model.number="form.quantity"
          type="number"
          min="1"
          :max="coinLimit"
          step="1"
          :disabled="saving"
          required
        />
      </label>
      <label
        >原因
        <textarea
          v-model.trim="form.reason"
          :maxlength="reasonLimit"
          :disabled="saving"
          required
        ></textarea>
      </label>
      <span v-toast="notice"></span>
      <div class="game-actions">
        <button type="submit" :disabled="saving">确定</button>
        <button type="button" :disabled="saving" @click="close">取消</button>
      </div>
    </form>
  </dialog>
</template>

<script>
import { adjustBlockcoin } from "../services/adminServ.js";
import { useAppStore } from "../stores/app.js";
import {
  ADMIN_REASON_LIMIT,
  ADMIN_COIN_LIMIT,
  assetOperation,
} from "../logic/admin.js";

export default {
  emits: ["changed"],
  // 弹窗只保存当前操作，账户列表的余额由成功后重新读取
  data: function adjustmentState() {
    return {
      account: {},
      form: { direction: "grant", quantity: 1, reason: "" },
      operation: null,
      token: "",
      saving: false,
      notice: "",
      reasonLimit: ADMIN_REASON_LIMIT,
      coinLimit: ADMIN_COIN_LIMIT,
    };
  },
  methods: {
    // 打开时复制目标账户和登录令牌，取消操作保留原账户余额
    open: function open(account) {
      if (this.saving) {
        return;
      }
      this.account = { userId: account.userId, username: account.username };
      this.form = { direction: "grant", quantity: 1, reason: "" };
      this.operation = null;
      this.token = useAppStore().userInfo.token;
      this.notice = "";
      this.$refs.dialog.showModal();
    },
    // 正在提交时保留弹窗，等待响应后再允许关闭
    cancel: function cancel(event) {
      if (this.saving) {
        event.preventDefault();
      }
    },
    // 取消时关闭当前表单，提交操作由确定按钮处理
    close: function close() {
      if (!this.saving) {
        this.$refs.dialog.close();
      }
    },
    // 校验整数、原因与账户身份，失败重试保留原请求编号
    submit: async function submit() {
      if (this.saving || this.token !== useAppStore().userInfo.token) {
        return;
      }
      if (
        !Number.isInteger(this.form.quantity) ||
        this.form.quantity < 1 ||
        this.form.quantity > ADMIN_COIN_LIMIT
      ) {
        this.notice = "请填写有效的整数字块币数量";
        return;
      }
      if (!this.form.reason || this.form.reason.length > ADMIN_REASON_LIMIT) {
        this.notice = "请填写调整原因";
        return;
      }
      const values = {
        userId: this.account.userId,
        direction: this.form.direction,
        quantity: this.form.quantity,
        reason: this.form.reason,
      };
      this.operation = assetOperation(values, this.operation);
      values.requestId = this.operation.requestId;
      this.saving = true;
      this.notice = "";
      try {
        const result = await adjustBlockcoin(values);
        if (this.token !== useAppStore().userInfo.token) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        this.$refs.dialog.close();
        this.$emit("changed", this.account.userId);
      } catch (error) {
        if (this.token === useAppStore().userInfo.token) {
          this.notice = error.message || "字块币调整失败";
        }
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>
