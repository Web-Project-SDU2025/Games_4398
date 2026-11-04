<template>
  <div class="support-chat" :class="{ 'support-compact': compact }">
    <div
      ref="messages"
      class="support-scroll"
      role="log"
      aria-label="游戏对话"
      aria-live="polite"
    >
      <div class="support-thread">
        <div v-if="conversation.messages.length === 0" class="support-welcome">
          <h1>今天玩点什么？</h1>
          <p>我是 Octorange，一起聊聊游戏规则和玩法。</p>
          <div class="support-suggestions">
            <button
              type="button"
              :disabled="pending"
              @click="ask('五子棋的获胜规则是什么？')"
            >
              五子棋规则
            </button>
            <button
              type="button"
              :disabled="pending"
              @click="ask('新手玩星露谷物语第一天可以做什么？')"
            >
              新手攻略
            </button>
            <button
              type="button"
              :disabled="pending"
              @click="ask('4398 平台怎样收藏游戏？')"
            >
              收藏游戏
            </button>
          </div>
        </div>
        <div
          v-for="(message, index) in conversation.messages"
          :key="index"
          class="support-message"
          :class="message.role"
        >
          <strong class="support-speaker">{{ speaker(message.role) }}</strong>
          <div
            v-if="message.role === 'assistant'"
            v-markdown="message.content"
            class="support-markdown"
          ></div>
          <p v-else class="support-user-text">{{ message.content }}</p>
          <button
            v-if="message.role === 'assistant'"
            type="button"
            class="support-copy"
            aria-label="复制回复"
            title="复制回复"
            @click="copyReply(message.content)"
          >
            <span class="i-ep-document-copy" aria-hidden="true"></span>
          </button>
        </div>
        <p v-if="pending" class="support-waiting" role="status">
          Octorange 正在思考…
        </p>
      </div>
    </div>
    <form class="support-composer" @submit.prevent="send">
      <label class="support-question-label" :for="inputId">游戏问题</label>
      <textarea
        :id="inputId"
        v-model="question"
        :maxlength="questionLimit"
        :disabled="pending"
        rows="2"
        placeholder="问问游戏规则、玩法或攻略"
        @keydown="handleKey"
      ></textarea>
      <div class="support-composer-footer">
        <span>{{ question.length }} / {{ questionLimit }}</span>
        <button
          v-if="pending"
          type="button"
          aria-label="停止等待"
          title="停止等待"
          @click="stop"
        >
          <span class="i-ep-video-pause" aria-hidden="true"></span>
        </button>
        <button
          v-else
          type="submit"
          :disabled="question.trim() === ''"
          aria-label="发送消息"
          title="发送消息"
        >
          <span class="i-ep-top" aria-hidden="true"></span>
        </button>
      </div>
    </form>
    <div v-if="notice || failedQuestion" class="support-feedback" role="status">
      <p v-reveal="notice" class="support-notice">{{ notice }}</p>
      <button v-if="failedQuestion && !pending" type="button" @click="retry">
        重试
      </button>
    </div>
  </div>
</template>

<script>
import * as support from "../logic/support.js";
import { askAi, getAiStatus } from "../services/aiServ.js";
import { scrollToBottom } from "../tools/motion.js";

// 返回单个聊天窗口的输入和请求状态，页面与宠物各自持有会话
function chatState() {
  return {
    question: "",
    notice: "",
    pending: false,
    failedQuestion: "",
    request: null,
    revision: 0,
    questionLimit: support.QUESTION_LIMIT,
  };
}

// 初次显示时读取配置状态，读取失败时显示重试提示
async function checkStatus() {
  const revision = this.revision;
  try {
    const status = await getAiStatus();
    if (!status.configured && revision === this.revision) {
      this.notice = "尚未配置百炼密钥，请联系管理员";
    }
  } catch (error) {
    if (revision === this.revision) {
      this.notice = "暂时无法读取百炼状态，发送时可以重试";
    }
  }
  this.scrollMessages();
}

// 输入法选词与 Shift 加回车保留编辑行为，普通回车发送问题
function handleKey(event) {
  if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
    event.preventDefault();
    this.send();
  }
}

// 将建议问题填入输入区并通过共用发送流程提交
function ask(question) {
  this.question = question;
  this.send();
}

// 记录问题后请求模型，等待期间直接保留当前请求
async function send() {
  if (this.pending || !support.addQuestion(this.conversation, this.question)) {
    return;
  }
  const question = this.question.trim();
  this.question = "";
  this.$emit("changed");
  await this.requestReply(question);
}

// 按请求序号接收回复，切换会话、关闭宠物和切号后丢弃旧响应
async function requestReply(question) {
  const conversation = this.conversation;
  const request = new AbortController();
  this.revision = this.revision + 1;
  const revision = this.revision;
  this.request = request;
  this.pending = true;
  this.failedQuestion = "";
  this.notice = "";
  this.$emit("pending", true);
  this.$nextTick(this.scrollMessages);
  try {
    const reply = await askAi(conversation.cloudId, question, request.signal);
    if (revision !== this.revision) {
      return;
    }
    support.addReply(conversation, reply);
    this.$emit("changed");
  } catch (error) {
    if (revision !== this.revision) {
      return;
    }
    // 超时后重新分配云端编号，重试从新上下文开始
    conversation.cloudId = crypto.randomUUID();
    this.failedQuestion = question;
    this.notice = error.message;
    this.$emit("changed");
  } finally {
    if (revision === this.revision) {
      this.pending = false;
      this.request = null;
      this.$emit("pending", false);
      this.$nextTick(this.scrollMessages);
    }
  }
}

// 重试复用原问题气泡，当前仍在等待时保持等待状态
async function retry() {
  if (!this.pending && this.failedQuestion) {
    await this.requestReply(this.failedQuestion);
  }
}

// 停止浏览器等待，云端请求最多在后端超时前继续完成
function cancelRequest(conversation) {
  this.revision = this.revision + 1;
  if (this.request) {
    this.request.abort();
    conversation.cloudId = crypto.randomUUID();
    this.$emit("changed");
  }
  this.request = null;
  this.pending = false;
  this.$emit("pending", false);
}

// 用户主动停止后恢复输入，并显示停止状态
function stop() {
  const messages = this.conversation.messages;
  if (messages.length > 0) {
    this.failedQuestion = messages[messages.length - 1].content;
  }
  this.cancelRequest(this.conversation);
  this.notice = "已停止等待";
}

// 切换历史时取消旧请求，同时清理上一次输入和错误状态
function conversationChanged(current, previous) {
  this.cancelRequest(previous);
  this.question = "";
  this.notice = "";
  this.failedQuestion = "";
  this.$nextTick(this.scrollMessages);
}

// 离开页面或退出账号时取消监听中的请求
function disposeChat() {
  this.cancelRequest(this.conversation);
}

// 使用共用滚动工具，遵从系统减少动态效果的设置
function scrollMessages() {
  scrollToBottom(this.$refs.messages);
}

// 明确区分提问者和模型回复
function speaker(role) {
  if (role === "user") {
    return "你";
  }
  return "Octorange";
}

// 通过剪贴板写入回复文字，权限失败时提示手动选择复制
async function copyReply(content) {
  try {
    await navigator.clipboard.writeText(content);
    this.notice = "已复制回复";
  } catch (error) {
    this.notice = "复制失败，可以手动选中文字";
  }
}

export default {
  props: {
    conversation: { type: Object, required: true },
    compact: { type: Boolean, default: false },
    inputId: { type: String, default: "support-question" },
  },
  emits: ["changed", "pending"],
  data: chatState,
  mounted: checkStatus,
  beforeUnmount: disposeChat,
  watch: { conversation: conversationChanged },
  methods: {
    ask: ask,
    send: send,
    retry: retry,
    stop: stop,
    requestReply: requestReply,
    cancelRequest: cancelRequest,
    scrollMessages: scrollMessages,
    handleKey: handleKey,
    speaker: speaker,
    copyReply: copyReply,
  },
};
</script>
