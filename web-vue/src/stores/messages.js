import { defineStore } from "pinia";
import { useAppStore } from "./app.js";
import { getUnreadMessages } from "../services/messageServ.js";
import { messageError } from "../logic/messages.js";

export const useMessageStore = defineStore("siteMessages", {
  // 未读状态按令牌区分，具体正文由消息页面分别读取
  state: function messageState() {
    return { owner: "", unread: 0, notice: "", requestId: 0, pending: null };
  },
  actions: {
    // 切号时立即清空旧数量并使等待中的响应失效
    reset: function reset() {
      this.unread = 0;
      this.notice = "";
      this.requestId = this.requestId + 1;
      this.pending = null;
    },
    // 普通轮询共用当前请求，已读操作完成后可强制获取新的数量
    refresh: async function refresh(force) {
      const user = useAppStore().userInfo;
      if (this.owner !== user.token) {
        this.reset();
        this.owner = user.token;
      }
      if (!user.loggedIn || !user.token) {
        this.reset();
        return;
      }
      if (this.pending && force !== true) {
        return this.pending;
      }
      this.requestId = this.requestId + 1;
      const requestId = this.requestId;
      const task = this.read(user.token, requestId);
      this.pending = task;
      try {
        await task;
      } finally {
        if (requestId === this.requestId) {
          this.pending = null;
        }
      }
    },
    // 请求编号与令牌同时核对，旧轮询响应只结束自己的等待
    read: async function read(token, requestId) {
      try {
        const result = await getUnreadMessages();
        if (
          token !== useAppStore().userInfo.token ||
          requestId !== this.requestId
        ) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        this.unread = result.data;
        this.notice = "";
      } catch (error) {
        if (
          token === useAppStore().userInfo.token &&
          requestId === this.requestId
        ) {
          this.notice = messageError(error);
        }
      }
    },
  },
});
