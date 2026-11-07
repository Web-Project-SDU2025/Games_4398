<template>
  <dialog
    ref="dialog"
    class="message-composer-dialog"
    :aria-labelledby="dialogId"
    @cancel="cancel"
  >
    <form class="game-form" @submit.prevent="save">
      <h3 :id="dialogId">{{ dialogTitle }}</h3>
      <label
        >标题<input
          v-model.trim="title"
          :maxlength="titleLimit"
          :disabled="saving"
          required
      /></label>
      <label
        >正文<textarea
          v-model.trim="content"
          :maxlength="contentLimit"
          :disabled="saving"
          required
        ></textarea>
      </label>
      <p class="message-markdown-hint">正文支持 Markdown</p>
      <span v-toast="notice"></span>
      <div class="game-actions">
        <button type="submit" :disabled="saving">{{ submitLabel }}</button>
        <button type="button" :disabled="saving" @click="close">取消</button>
      </div>
    </form>
  </dialog>
</template>

<script>
import {
  publishAnnouncement,
  submitFeedback,
  editMessage,
} from "../services/messageServ.js";
import { useAppStore } from "../stores/app.js";
import { assetOperation } from "../logic/admin.js";
import {
  MESSAGE_TITLE_LIMIT,
  MESSAGE_CONTENT_LIMIT,
  messageError,
} from "../logic/messages.js";

export default {
  props: {
    dialogId: { type: String, required: true },
  },
  emits: ["saved"],
  // 公告和反馈共用标题、正文、重试与编辑状态
  data: function messageComposerState() {
    return {
      kind: "announcement",
      editing: false,
      messageId: "",
      title: "",
      content: "",
      token: "",
      operation: null,
      notice: "",
      saving: false,
      titleLimit: MESSAGE_TITLE_LIMIT,
      contentLimit: MESSAGE_CONTENT_LIMIT,
    };
  },
  computed: {
    // 根据消息类型和编辑状态显示弹窗标题
    dialogTitle: function dialogTitle() {
      if (this.editing) {
        if (this.kind === "announcement") {
          return "编辑公告";
        }
        return "编辑问题反馈";
      }
      if (this.kind === "announcement") {
        return "发布公告";
      }
      return "提交问题反馈";
    },
    // 根据消息类型和编辑状态显示提交按钮文字
    submitLabel: function submitLabel() {
      if (this.editing) {
        return "保存修改";
      }
      if (this.kind === "announcement") {
        return "发布公告";
      }
      return "提交反馈";
    },
  },
  methods: {
    // 打开公告或反馈表单，编辑时载入原文并核对当前账户权限
    open: function open(
      kind,
      message,
    ) {
      const user = useAppStore().userInfo;
      if (this.saving || !user.token) {
        return;
      }
      if (kind !== "announcement" && kind !== "feedback") {
        return;
      }
      if (kind === "announcement" && user.role !== "ROLE_ADMIN") {
        return;
      }
      this.kind = kind;
      this.editing = false;
      this.messageId = "";
      this.title = "";
      this.content = "";
      this.operation = null;
      if (message) {
        if (message.kind !== kind) {
          return;
        }
        if (kind === "feedback" && message.senderId !== user.id) {
          return;
        }
        this.editing = true;
        this.messageId = message.messageId;
        this.title = message.title;
        this.content = message.content;
      }
      this.token = user.token;
      this.notice = "";
      this.$refs.dialog.showModal();
    },
    // 提交期间阻止通过弹窗取消事件关闭表单
    cancel: function cancel(event) {
      if (this.saving) {
        event.preventDefault();
      }
    },
    // 取消只关闭表单，草稿留在组件中供下次打开
    close: function close() {
      if (!this.saving) {
        this.$refs.dialog.close();
      }
    },
    // 新增或修改消息，失败时保留内容供用户重试
    save: async function save() {
      if (this.saving || this.token !== useAppStore().userInfo.token) {
        return;
      }
      if (
        !this.title ||
        this.title.length > MESSAGE_TITLE_LIMIT ||
        !this.content ||
        this.content.length > MESSAGE_CONTENT_LIMIT
      ) {
        this.notice = "请填写有效的消息标题与内容";
        return;
      }
      const values = {
        title: this.title,
        content: this.content,
      };
      if (this.editing) {
        values.messageId = this.messageId;
      } else {
        this.operation = assetOperation(values, this.operation);
        values.requestId = this.operation.requestId;
      }
      this.saving = true;
      this.notice = "";
      try {
        let result;
        if (this.editing) {
          result = await editMessage(values);
        } else if (this.kind === "announcement") {
          result = await publishAnnouncement(values);
        } else {
          result = await submitFeedback(values);
        }
        if (this.token !== useAppStore().userInfo.token) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        let savedMessageId = this.messageId;
        if (typeof result.data === "string") {
          savedMessageId = result.data;
        }
        const savedMessage = {
          kind: this.kind,
          editing: this.editing,
          messageId: savedMessageId,
          title: this.title,
          content: this.content,
        };
        this.title = "";
        this.content = "";
        this.messageId = "";
        this.operation = null;
        this.$refs.dialog.close();
        this.$emit("saved", savedMessage);
      } catch (error) {
        if (this.token === useAppStore().userInfo.token) {
          this.notice = messageError(error);
        }
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>
