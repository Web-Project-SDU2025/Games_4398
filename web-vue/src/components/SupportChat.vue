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
          <h1>Octorange</h1>
        </div>
        <div
          v-for="(message, index) in conversation.messages"
          :key="index"
          class="support-message"
          :class="message.role"
        >
          <strong class="support-speaker">{{ speaker(message.role) }}</strong>
          <div
            v-if="message.images && message.images.length"
            class="support-message-images"
          >
            <figure
              v-for="(image, imageIndex) in message.images"
              :key="imageIndex"
            >
              <img
                v-if="image.preview"
                :src="image.preview"
                :alt="image.name"
                width="160"
                height="120"
              />
              <figcaption>{{ image.name }}</figcaption>
            </figure>
          </div>
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
    <form
      class="support-composer"
      @submit.prevent="send"
      @dragover.prevent
      @drop.prevent="dropImages"
    >
      <div v-if="images.length" class="support-attachments">
        <figure v-for="image in images" :key="image.id">
          <img :src="image.preview" :alt="image.name" width="80" height="60" />
          <button
            type="button"
            :aria-label="'移除图片 ' + image.name"
            :title="'移除 ' + image.name"
            @click="removeImage(image.id)"
          >
            <span class="i-ep-close" aria-hidden="true"></span>
          </button>
        </figure>
      </div>
      <label class="support-question-label" :for="inputId">游戏问题</label>
      <textarea
        :id="inputId"
        v-model="question"
        :maxlength="questionLimit"
        :disabled="pending"
        rows="2"
        placeholder="输入问题"
        @keydown="handleKey"
        @paste="pasteImages"
      ></textarea>
      <div class="support-composer-footer">
        <span>{{ question.length }} / {{ questionLimit }}</span>
        <input
          ref="imagePicker"
          class="support-image-input"
          type="file"
          accept="image/png,image/jpeg,image/webp"
          multiple
          @change="selectImages"
        />
        <button
          type="button"
          class="support-upload"
          aria-label="添加图片"
          title="添加图片"
          :disabled="pending || importing || images.length >= imageLimit"
          @click="openImagePicker"
        >
          <span class="i-ep-picture" aria-hidden="true"></span>
        </button>
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
          :disabled="
            importing || (question.trim() === '' && images.length === 0)
          "
          aria-label="发送消息"
          title="发送消息"
        >
          <span class="i-ep-top" aria-hidden="true"></span>
        </button>
      </div>
    </form>
    <div v-if="notice || failedQuestion" class="support-feedback" role="status">
      <p v-reveal="notice" class="support-notice">{{ notice }}</p>
      <button
        v-if="(failedQuestion || failedImages.length) && !pending"
        type="button"
        @click="retry"
      >
        重试
      </button>
    </div>
  </div>
</template>

<script>
import * as support from "../logic/support.js";
import { createQuestionWithSiteGuide } from "../logic/siteGuide.js";
import { askAi, getAiStatus } from "../services/aiServ.js";
import { scrollToBottom } from "../tools/motion.js";
import {
  prepareChatImage,
  imageBytes,
  CHAT_IMAGE_LIMITS,
} from "../tools/chatImages.js";

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
    images: [],
    failedImages: [],
    importing: false,
    imageLimit: CHAT_IMAGE_LIMITS.count,
    imageRevision: 0,
    activeImages: [],
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
  if (
    this.pending ||
    this.importing ||
    !support.addQuestion(this.conversation, this.question, this.images)
  ) {
    return;
  }
  const question = this.question.trim();
  const images = this.images;
  this.question = "";
  this.images = [];
  this.$emit("changed");
  await this.requestReply(question, images);
}

// 按请求序号接收回复，切换会话、关闭宠物和切号后丢弃旧响应
async function requestReply(question, images) {
  if (!images) {
    images = [];
  }
  const conversation = this.conversation;
  const request = new AbortController();
  this.revision = this.revision + 1;
  const revision = this.revision;
  this.request = request;
  this.pending = true;
  this.failedQuestion = "";
  this.failedImages = [];
  this.activeImages = images;
  this.notice = "";
  this.$emit("pending", true);
  this.$nextTick(this.scrollMessages);
  try {
    if (images.length > 0) {
      conversation.multimodal = true;
    }
    const informedQuestion = createQuestionWithSiteGuide(question);
    const reply = await askAi(
      conversation.cloudId,
      informedQuestion,
      request.signal,
      images,
      conversation.multimodal === true,
    );
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
    this.failedImages = images;
    this.notice = error.message;
    this.$emit("changed");
  } finally {
    if (revision === this.revision) {
      this.pending = false;
      this.request = null;
      this.activeImages = [];
      this.$emit("pending", false);
      this.$nextTick(this.scrollMessages);
    }
  }
}

// 重试复用原问题气泡，当前仍在等待时保持等待状态
async function retry() {
  if (!this.pending && (this.failedQuestion || this.failedImages.length)) {
    await this.requestReply(this.failedQuestion, this.failedImages);
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
  this.activeImages = [];
  this.$emit("pending", false);
}

// 用户主动停止后恢复输入，并显示停止状态
function stop() {
  this.failedImages = this.activeImages;
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
  this.failedImages = [];
  this.images = [];
  this.imageRevision = this.imageRevision + 1;
  this.$nextTick(this.scrollMessages);
}

// 离开页面或退出账号时取消监听中的请求
function disposeChat() {
  this.imageRevision = this.imageRevision + 1;
  this.cancelRequest(this.conversation);
}

// 文件选择器复用同一图片导入流程
function openImagePicker() {
  this.$refs.imagePicker.click();
}

// 导入多张图片，切换会话后丢弃旧导入结果，部分文件失败时保留其余合法图片
async function importImages(files) {
  if (this.pending || this.importing) {
    return;
  }
  this.importing = true;
  const revision = this.imageRevision;
  let totalBytes = 0;
  for (const image of this.images) {
    totalBytes = totalBytes + imageBytes(image.source);
  }
  try {
    for (const file of files) {
      if (this.images.length >= CHAT_IMAGE_LIMITS.count) {
        this.notice = "每次最多添加 " + CHAT_IMAGE_LIMITS.count + " 张图片";
        break;
      }
      const image = await prepareChatImage(file);
      if (revision !== this.imageRevision) {
        return;
      }
      totalBytes = totalBytes + imageBytes(image.source);
      if (totalBytes > CHAT_IMAGE_LIMITS.totalBytes) {
        throw new Error("图片总量过大，请减少图片");
      }
      this.images.push(image);
    }
  } catch (error) {
    if (revision === this.imageRevision) {
      this.notice = error.message;
    }
  } finally {
    this.importing = false;
  }
}

// 选择完成后清空原生控件，移除后可以再次选择同一个文件
function selectImages(event) {
  const files = [];
  for (const file of event.target.files) {
    files.push(file);
  }
  this.importImages(files);
  event.target.value = "";
}

// 粘贴文字沿用原行为，剪贴板中的图片交给共用导入流程
function pasteImages(event) {
  const files = event.clipboardData.files;
  if (files.length > 0) {
    event.preventDefault();
    this.importImages(files);
  }
}

// 拖入输入区的本地图片与文件选择器使用相同数量和体积限制
function dropImages(event) {
  this.importImages(event.dataTransfer.files);
}

// 按图片编号移除草稿，其他已选择图片保留顺序
function removeImage(id) {
  for (let index = 0; index < this.images.length; index = index + 1) {
    if (this.images[index].id === id) {
      this.images.splice(index, 1);
      return;
    }
  }
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
    this.notice = "复制失败";
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
    openImagePicker: openImagePicker,
    importImages: importImages,
    selectImages: selectImages,
    pasteImages: pasteImages,
    dropImages: dropImages,
    removeImage: removeImage,
  },
};
</script>
