<template>
  <section class="game-page message-page">
    <header class="message-heading">
      <div>
        <h2>消息中心</h2>
        <span>{{ counter.unread }} 条未读</span>
      </div>
      <div class="game-actions">
        <button type="button" @click="composeFeedback">提交问题反馈</button>
        <button
          v-if="admin"
          type="button"
          @click="composeAnnouncement"
        >
          发布公告
        </button>
        <button
          type="button"
          :disabled="busy || replying || counter.unread === 0"
          @click="readAll"
        >
          全部已读
        </button>
        <button
          type="button"
          :disabled="loading || replying"
          title="刷新消息"
          aria-label="刷新消息"
          @click="query"
        >
          <span class="i-ep-refresh" aria-hidden="true"></span>
        </button>
      </div>
    </header>
    <form class="game-toolbar" @submit.prevent="search">
      <label
        >类型<select
          v-model="kind"
          :disabled="loading || replying"
          @change="search"
        >
          <option v-for="item in kinds" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select></label
      >
      <label class="game-checkbox"
        ><input
          v-model="unreadOnly"
          type="checkbox"
          :disabled="loading || replying"
          @change="search"
        />仅未读</label
      >
    </form>
    <span v-toast="notice"></span>
    <div class="message-workspace" :aria-busy="loading">
      <div class="message-list">
        <ol>
          <li v-for="message in messages" :key="message.messageId">
            <button
              type="button"
              :class="{
                'is-selected':
                  selected && selected.messageId === message.messageId,
                'is-unread': !message.read,
              }"
              :disabled="busy || replying"
              @click="openMessage(message)"
            >
              <span class="message-row-meta"
                ><span>{{ kindName(message.kind) }}</span
                ><span v-if="!message.read">未读</span></span
              >
              <strong>{{ message.title }}</strong>
              <span class="message-row-meta"
                ><span>{{ message.senderName }}</span
                ><time>{{ formatTime(message.createdAt) }}</time></span
              >
            </button>
          </li>
        </ol>
        <p v-if="loaded && messages.length === 0" class="message-empty">
          暂无消息
        </p>
        <nav class="admin-pagination" aria-label="消息分页">
          <span>共 {{ total }} 条</span>
          <button
            type="button"
            :disabled="loading || busy || replying || page <= 1"
            @click="previous"
          >
            上一页
          </button>
          <span>{{ page }} / {{ pageCount }}</span>
          <button
            type="button"
            :disabled="loading || busy || replying || page >= pageCount"
            @click="next"
          >
            下一页
          </button>
        </nav>
      </div>
      <article
        v-if="selected"
        class="message-detail"
        aria-labelledby="message-title"
      >
        <div class="message-detail-meta">
          <span>{{ kindName(selected.kind) }}</span
          ><span>{{ selected.senderName }}</span
          ><time>{{ formatTime(selected.createdAt) }}</time>
        </div>
        <div class="message-title-row">
          <h3 id="message-title">{{ selected.title }}</h3>
          <button
            v-if="canEditSelected"
            class="message-edit-button"
            type="button"
            :disabled="busy || replying"
            @click="editSelected"
          >
            编辑
          </button>
        </div>
        <div
          class="message-content support-markdown"
          v-markdown="selected.content"
        ></div>
        <section
          v-if="selected.kind === 'feedback'"
          class="message-feedback-status"
        >
          <div class="message-feedback-meta">
            <strong>处理结果</strong>
            <span>{{ feedbackDecisionName(selected.feedbackDecision) }}</span>
          </div>
          <div class="message-feedback-meta">
            <strong>管理员回复</strong>
            <time v-if="selected.replyUpdatedAt">
              {{ formatTime(selected.replyUpdatedAt) }}
            </time>
          </div>
          <div
            v-if="selected.adminReply"
            class="message-content support-markdown"
            v-markdown="selected.adminReply"
          ></div>
          <p v-else class="message-no-reply">暂无回复</p>
        </section>
        <form
          v-if="admin && selected.kind === 'feedback'"
          class="message-feedback-response game-form"
          @submit.prevent="respondToFeedback"
        >
          <label
            >当前修复状态<select
              v-model="replyDecision"
              :disabled="replying"
              required
            >
              <option
                v-for="decision in feedbackDecisions"
                :key="decision.value"
                :value="decision.value"
              >
                {{ decision.label }}
              </option>
            </select></label
          >
          <label
            >管理员回复<textarea
              v-model.trim="replyContent"
              :maxlength="contentLimit"
              :disabled="replying"
              required
            ></textarea>
          </label>
          <p class="message-markdown-hint">回复支持 Markdown</p>
          <span v-toast="replyNotice"></span>
          <button type="submit" :disabled="replying">
            {{ replySubmitLabel }}
          </button>
        </form>
        <button
          v-if="!selected.read"
          type="button"
          :disabled="busy || replying"
          @click="openMessage(selected)"
        >
          标记已读
        </button>
      </article>
      <div v-else class="message-empty message-detail">选择消息</div>
    </div>
    <MessageComposer
      ref="composer"
      dialog-id="message-center-composer-title"
      @saved="messageSaved"
    />
  </section>
</template>

<script>
import MessageComposer from "../components/MessageComposer.vue";
import {
  getMessages,
  readMessage,
  readAllMessages,
  saveFeedbackResponse,
} from "../services/messageServ.js";
import { useAppStore } from "../stores/app.js";
import { useMessageStore } from "../stores/messages.js";
import {
  MESSAGE_PAGE_SIZE,
  MESSAGE_MAX_PAGE,
  MESSAGE_CONTENT_LIMIT,
  MESSAGE_KINDS,
  messageKind,
  feedbackDecisionName,
  FEEDBACK_DECISIONS,
  messageError,
} from "../logic/messages.js";
import { transactionTime } from "../logic/store.js";

export default {
  name: "Messages",
  components: { MessageComposer: MessageComposer },
  // 列表和已选正文分别维护，读取新页时继续保留当前正文
  data: function inboxState() {
    return {
      kinds: MESSAGE_KINDS,
      kind: "all",
      unreadOnly: false,
      page: 1,
      total: 0,
      messages: [],
      selected: null,
      loading: false,
      loaded: false,
      busy: false,
      notice: "",
      requestId: 0,
      contentLimit: MESSAGE_CONTENT_LIMIT,
      feedbackDecisions: FEEDBACK_DECISIONS,
      replyDecision: "",
      replyContent: "",
      replyNotice: "",
      replying: false,
    };
  },
  computed: {
    // 管理员发布入口与后端角色使用相同标记
    admin: function admin() {
      return useAppStore().userInfo.role === "ROLE_ADMIN";
    },
    // 顶栏与页面共用未读计数
    counter: function counter() {
      return useMessageStore();
    },
    // 空列表仍显示第一页，页数根据服务端总条数计算
    pageCount: function pageCount() {
      return Math.max(1, Math.ceil(this.total / MESSAGE_PAGE_SIZE));
    },
    // 保存期间显示等待状态
    replySubmitLabel: function replySubmitLabel() {
      if (this.replying) {
        return "正在保存";
      }
      return "保存处理结果";
    },
    // 公告由管理员编辑，反馈仅由提交账户编辑
    canEditSelected: function canEditSelected() {
      if (!this.selected) {
        return false;
      }
      const user = useAppStore().userInfo;
      if (this.selected.kind === "announcement" && this.admin) {
        return true;
      }
      if (
        this.selected.kind === "feedback" &&
        this.selected.senderId === user.id
      ) {
        return true;
      }
      return false;
    },
  },
  // 首次进入读取消息，页面不加入长期列表缓存
  mounted: function mountInbox() {
    this.query();
  },
  // 离开后使未完成查询失效
  beforeUnmount: function disposeInbox() {
    this.requestId = this.requestId + 1;
  },
  methods: {
    kindName: messageKind,
    formatTime: transactionTime,
    feedbackDecisionName: feedbackDecisionName,
    // 过滤条件变化后从第一页开始，正文选择同时清空
    search: function search() {
      this.page = 1;
      this.selected = null;
      return this.query();
    },
    // 查询按编号与令牌丢弃旧响应，失败保留上一批已成功读取的内容
    query: async function query() {
      this.requestId = this.requestId + 1;
      const requestId = this.requestId;
      const token = useAppStore().userInfo.token;
      this.loading = true;
      this.notice = "";
      try {
        const filters = {
          kind: this.kind,
          unreadOnly: this.unreadOnly,
          page: this.page,
          pageSize: MESSAGE_PAGE_SIZE,
        };
        const result = await getMessages(filters);
        if (
          requestId !== this.requestId ||
          token !== useAppStore().userInfo.token
        ) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        this.messages = result.data.messages;
        this.total = result.data.total;
        this.loaded = true;
        if (this.page > this.pageCount && this.page <= MESSAGE_MAX_PAGE) {
          this.page = this.pageCount;
          return this.query();
        }
        await this.counter.refresh(true);
      } catch (error) {
        if (
          requestId === this.requestId &&
          token === useAppStore().userInfo.token
        ) {
          this.notice = messageError(error);
        }
      } finally {
        if (requestId === this.requestId) {
          this.loading = false;
        }
      }
    },
    // 点击展示原文并保存个人已读状态，失败时保留正文供阅读和重试
    openMessage: async function openMessage(message) {
      if (this.busy || this.replying) {
        return;
      }
      this.selected = message;
      this.replyDecision = "";
      for (const decision of this.feedbackDecisions) {
        if (message.feedbackDecision === decision.value) {
          this.replyDecision = decision.value;
          break;
        }
      }
      this.replyContent = message.adminReply || "";
      this.replyNotice = "";
      if (message.read) {
        return;
      }
      const token = useAppStore().userInfo.token;
      this.busy = true;
      this.notice = "";
      try {
        const result = await readMessage(message.messageId);
        if (token !== useAppStore().userInfo.token) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        message.read = true;
        await this.query();
      } catch (error) {
        if (token === useAppStore().userInfo.token) {
          this.notice = messageError(error);
        }
      } finally {
        this.busy = false;
      }
    },
    // 全部已读由服务端确定时间边界，完成后重新读取当前列表
    readAll: async function readAll() {
      if (this.busy || this.replying) {
        return;
      }
      const token = useAppStore().userInfo.token;
      this.busy = true;
      this.notice = "";
      try {
        const result = await readAllMessages();
        if (token !== useAppStore().userInfo.token) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        this.selected = null;
        await this.query();
      } catch (error) {
        if (token === useAppStore().userInfo.token) {
          this.notice = messageError(error);
        }
      } finally {
        this.busy = false;
      }
    },
    // 管理员回复反馈后通知提交者，并更新当前消息详情
    respondToFeedback: async function respondToFeedback() {
      if (
        this.replying ||
        !this.admin ||
        !this.selected ||
        this.selected.kind !== "feedback"
      ) {
        return;
      }
      if (
        !this.replyDecision ||
        !this.replyContent ||
        this.replyContent.length > this.contentLimit
      ) {
        this.replyNotice = "请选择处理结果并填写回复";
        return;
      }
      const token = useAppStore().userInfo.token;
      const values = {
        messageId: this.selected.messageId,
        decision: this.replyDecision,
        reply: this.replyContent,
      };
      this.replying = true;
      this.replyNotice = "";
      try {
        const result = await saveFeedbackResponse(values);
        if (token !== useAppStore().userInfo.token) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        this.selected.feedbackDecision = result.data.decision;
        this.selected.adminReply = result.data.reply;
        this.selected.replyUpdatedAt = result.data.updatedAt;
        this.replyNotice = "处理结果已保存";
        await this.counter.refresh(true);
      } catch (error) {
        if (token === useAppStore().userInfo.token) {
          this.replyNotice = messageError(error);
        }
      } finally {
        this.replying = false;
      }
    },
    // 上一页继续使用当前类型与未读筛选
    previous: function previous() {
      if (this.page > 1) {
        this.page = this.page - 1;
        this.query();
      }
    },
    // 下一页在服务端总页数内读取
    next: function next() {
      if (this.page < this.pageCount) {
        this.page = this.page + 1;
        this.query();
      }
    },
    // 管理员发布入口由表单和服务端分别核对权限
    composeAnnouncement: function composeAnnouncement() {
      this.$refs.composer.open("announcement");
    },
    // 所有已登录账户都能提交问题反馈
    composeFeedback: function composeFeedback() {
      this.$refs.composer.open("feedback");
    },
    // 编辑原消息保留当前筛选，新增消息切到对应类型第一页
    editSelected: function editSelected() {
      if (this.canEditSelected) {
        this.$refs.composer.open(this.selected.kind, this.selected);
      }
    },
    // 保存后重新读取列表，并在读取完成后显示结果提示
    messageSaved: async function messageSaved(savedMessage) {
      if (savedMessage.editing) {
        if (
          this.selected &&
          this.selected.messageId === savedMessage.messageId
        ) {
          this.selected.title = savedMessage.title;
          this.selected.content = savedMessage.content;
          this.selected.read = true;
        }
        await this.query();
        if (this.notice === "") {
          this.notice = "消息已修改";
        }
        return;
      }
      this.kind = savedMessage.kind;
      this.unreadOnly = false;
      this.selected = null;
      this.page = 1;
      await this.query();
      if (this.notice !== "") {
        return;
      }
      if (savedMessage.kind === "feedback") {
        this.notice = "问题反馈已发送";
      } else {
        this.notice = "公告已发布";
      }
    },
  },
};
</script>
