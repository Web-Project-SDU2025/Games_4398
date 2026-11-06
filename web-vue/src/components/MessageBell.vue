<template>
  <RouterLink
    class="workspace-icon-button message-bell"
    to="/Messages"
    :title="label"
    :aria-label="label"
  >
    <span class="i-ep-bell" aria-hidden="true"></span>
    <small
      v-if="messages.unread > 0"
      class="message-badge"
      aria-hidden="true"
      >{{ badge }}</small
    >
  </RouterLink>
</template>

<script>
import { useMessageStore } from "../stores/messages.js";
import {
  MESSAGE_POLL_MILLISECONDS,
  MESSAGE_BADGE_LIMIT,
} from "../logic/messages.js";

export default {
  // 计时器由本组件持有，退出或切号卸载时结束轮询
  data: function bellState() {
    return { timer: null };
  },
  computed: {
    // 顶栏与消息页面共用同一账户的未读数量
    messages: function messages() {
      return useMessageStore();
    },
    // 大数量使用固定上限，避免铃铛布局被长数字撑开
    badge: function badge() {
      if (this.messages.unread > MESSAGE_BADGE_LIMIT) {
        return MESSAGE_BADGE_LIMIT + "+";
      }
      return String(this.messages.unread);
    },
    // 可访问名称包含未读数量，服务异常时保留具体提示
    label: function label() {
      if (this.messages.notice) {
        return this.messages.notice;
      }
      return "消息中心，" + this.messages.unread + " 条未读";
    },
  },
  // 重新聚焦或页面恢复可见时读取一次，后台标签保持暂停
  mounted: function mountBell() {
    this.poll();
    this.timer = window.setInterval(this.poll, MESSAGE_POLL_MILLISECONDS);
    window.addEventListener("focus", this.poll);
    document.addEventListener("visibilitychange", this.poll);
  },
  // 卸载后清理监听和旧账户数量，下一账户由新铃铛重新读取
  beforeUnmount: function disposeBell() {
    window.clearInterval(this.timer);
    window.removeEventListener("focus", this.poll);
    document.removeEventListener("visibilitychange", this.poll);
    this.messages.reset();
  },
  methods: {
    // 当前页面可见时才发出轻量请求
    poll: function poll() {
      if (document.visibilityState === "visible") {
        this.messages.refresh();
      }
    },
  },
};
</script>
