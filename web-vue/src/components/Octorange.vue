<template>
  <div class="pet-root" :class="{ 'pet-obscured': modalOpen }">
    <button
      v-if="!settings.visible"
      type="button"
      class="pet-restore"
      aria-label="显示 Octorange"
      title="显示 Octorange"
      @click="showPet"
    >
      <span class="i-ep-chat-dot-round" aria-hidden="true"></span>
    </button>
    <div v-else ref="pet" class="pet-character" :style="positionStyle">
      <button
        ref="toggle"
        type="button"
        class="pet-toggle"
        aria-label="与 Octorange 聊游戏"
        :aria-describedby="thoughtDescription"
        :aria-expanded="expanded"
        aria-controls="octorange-panel"
        @click="togglePanel"
        @pointerdown="startDrag"
        @pointermove="moveDrag"
        @pointerup="finishDrag"
        @pointercancel="cancelDrag"
        @lostpointercapture="cancelDrag"
        @pointerenter="hoverPet"
        @pointerleave="leavePet"
        @focus="focusPet"
        @blur="blurPet"
        @keydown="moveWithKeyboard"
      >
        <img
          v-for="(image, name) in images"
          :key="name"
          :src="image"
          :class="{ active: name === imageState }"
          alt=""
          width="160"
          height="120"
          draggable="false"
        />
      </button>
      <p
        v-if="showThought"
        id="octorange-thought"
        class="pet-thought"
        :class="{ 'pet-thought-below': top < thoughtClearance }"
      >{{ thought }}</p>
      <button
        type="button"
        class="pet-hide"
        aria-label="隐藏 Octorange"
        title="隐藏 Octorange"
        @click="hidePet"
      >
        <span class="i-ep-close" aria-hidden="true"></span>
      </button>
    </div>
    <section
      id="octorange-panel"
      ref="panel"
      popover="auto"
      class="pet-panel"
      role="dialog"
      aria-label="Octorange 游戏助手"
      @toggle="panelToggled"
    >
      <header class="pet-panel-heading">
        <div class="pet-avatar" aria-hidden="true">
          <img
            v-for="(image, name) in images"
            :key="name"
            :src="image"
            :class="{ active: name === imageState }"
            alt=""
            width="40"
            height="34"
          />
        </div>
        <div><strong>Octorange</strong></div>
        <button
          type="button"
          aria-label="新建宠物对话"
          title="新建对话"
          @click="clearChat"
        >
          <span class="i-ep-edit" aria-hidden="true"></span>
        </button>
        <button
          type="button"
          aria-label="重置宠物位置"
          title="重置位置"
          @click="resetPosition"
        >
          <span class="i-ep-aim" aria-hidden="true"></span>
        </button>
        <button
          type="button"
          aria-label="关闭宠物对话"
          title="关闭对话"
          @click="closePanel"
        >
          <span class="i-ep-close" aria-hidden="true"></span>
        </button>
      </header>
      <SupportChat
        ref="chat"
        :key="conversation.id"
        :conversation="conversation"
        input-id="pet-question"
        compact
        @changed="persistChat"
        @pending="setThinking"
      />
      <footer class="pet-panel-footer">
        <RouterLink to="/AiSupport" @click="closePanel"
          >打开 AI 客服</RouterLink
        >
      </footer>
      <span v-toast="notice"></span>
    </section>
  </div>
</template>

<script>
import SupportChat from "./SupportChat.vue";
import * as pet from "../logic/pet.js";
import * as support from "../logic/support.js";
import { idlePetThought, THOUGHT_ROTATION_MS } from "../logic/petSpeech.js";
import { generalRequest } from "../services/genServ.js";
import { useAppStore } from "../stores/app.js";

// 键盘每次移动宠物的距离，单位为像素
const KEYBOARD_DISTANCE = 20;
const THOUGHT_CLEARANCE = 136;
const THOUGHT_VISIBLE_MS = 3600;

// 记录浮窗和气泡状态，聊天记录仍交给客服组件管理
function petState() {
  const conversations = support.readConversations(this.userId + "-pet");
  let conversation = support.createConversation();
  if (conversations.length > 0) {
    conversation = conversations[0];
  }
  return {
    settings: pet.readSettings(this.userId),
    conversation: conversation,
    images: pet.PET_IMAGES,
    left: 0,
    top: 0,
    expanded: false,
    hovered: false,
    focused: false,
    mood: null,
    moodPending: false,
    moodRequest: 0,
    thoughtIndex: 0,
    idleThought: false,
    thoughtRotation: 0,
    thoughtTimeout: 0,
    thoughtClearance: THOUGHT_CLEARANCE,
    thinking: false,
    modalOpen: false,
    observer: null,
    layoutObserver: null,
    notice: "",
    drag: null,
    suppressClick: false,
  };
}

// 把位置写在固定容器上，状态图片的淡入由独立 CSS 控制
function positionStyle() {
  const left = this.left + "px";
  return { left: left, top: this.top + "px", "--pet-left": left };
}

// 回答期间优先显示思考状态，结束后按悬停状态选择图片
function imageState() {
  if (this.thinking) {
    return "thinking";
  }
  if (this.hovered || this.focused) {
    return "hover";
  }
  return "idle";
}

// 打开聊天、拖动或遇到业务弹窗时，让气泡避开正在操作的内容
function showThought() {
  const app = useAppStore();
  return this.available && this.settings.visible && app.userInfo.loggedIn
    && (this.hovered || this.focused || this.idleThought) && !this.expanded && !this.drag && !this.modalOpen;
}

// 气泡显示时才让桌宠按钮引用它的文字
function thoughtDescription() {
  if (this.showThought && this.focused) {
    return "octorange-thought";
  }
  return null;
}

// 已有存档时说出当前状态，没有存档时沿用小屋的问候
function thought() {
  return idlePetThought(this.mood, this.thoughtIndex);
}

// 自动气泡到时收起，鼠标仍停在桌宠上时继续显示
function hideIdleThought() {
  this.idleThought = false;
}

// 空闲时轮换一句短话，隐藏桌宠、游玩或编辑时不打扰用户
function rotateThought() {
  if (document.hidden || !this.available || !this.settings.visible || this.modalOpen || this.expanded || this.drag) {
    return;
  }
  this.thoughtIndex = this.thoughtIndex + 1;
  this.idleThought = true;
  window.clearTimeout(this.thoughtTimeout);
  this.thoughtTimeout = window.setTimeout(this.hideIdleThought, THOUGHT_VISIBLE_MS);
}

// 令牌变化时清除上一账户的气泡和仍在等待的请求
function resetMood() {
  this.moodRequest = this.moodRequest + 1;
  this.moodPending = false;
  this.mood = null;
  this.thoughtIndex = 0;
  window.clearTimeout(this.thoughtTimeout);
  this.hideIdleThought();
}

// 读取当前身份，账号切换或令牌更新时不沿用旧气泡
function moodToken() {
  const app = useAppStore();
  return app.userInfo.token;
}

// 每次查看气泡时读取最新存档，同时合并悬停与聚焦发起的请求
async function refreshMood() {
  if (!this.showThought || !this.moodToken || this.moodPending) {
    return;
  }
  const token = this.moodToken;
  const request = this.moodRequest;
  this.moodPending = true;
  try {
    const result = await generalRequest("/api/pet/mood", {});
    if (request === this.moodRequest && token === this.moodToken && result.code === 0) {
      this.mood = result.data;
    }
  } catch (error) {
    if (request === this.moodRequest && token === this.moodToken) {
      this.mood = null;
    }
  } finally {
    if (request === this.moodRequest) {
      this.moodPending = false;
    }
  }
}

// 监听尺寸和业务弹窗，编辑弹窗打开时暂时隐藏宠物
function mountPet() {
  this.thoughtRotation = window.setInterval(this.rotateThought, THOUGHT_ROTATION_MS);
  this.$nextTick(this.restorePosition);
  window.addEventListener("resize", this.restorePosition);
  this.layoutObserver = new ResizeObserver(this.restorePosition);
  const fixedAreas = document.querySelectorAll(
    ".platform-header, .workspace-status, .platform-main",
  );
  for (const area of fixedAreas) {
    this.layoutObserver.observe(area);
  }
  this.observer = new MutationObserver(this.checkModal);
  this.observer.observe(document.body, {
    attributes: true,
    attributeFilter: ["open"],
    subtree: true,
  });
  this.checkModal();
}

// 卸载时清理全局监听，切号后由新组件重新初始化
function disposePet() {
  window.clearInterval(this.thoughtRotation);
  this.resetMood();
  window.removeEventListener("resize", this.restorePosition);
  if (this.observer) {
    this.observer.disconnect();
  }
  if (this.layoutObserver) {
    this.layoutObserver.disconnect();
  }
}

// 切换页面后重新避让控件，等待新页面的输入框实际显示再测量
function pageChanged() {
  this.hovered = false;
  this.focused = false;
  this.resetMood();
  this.$nextTick(this.restorePosition);
}

// 编辑器打开时临时隐藏浮层，编辑结束后恢复原来的显示设置
function checkModal() {
  this.modalOpen = Boolean(document.querySelector("dialog[open]"));
  if (this.modalOpen) {
    this.closePanel();
  }
}

// 根据保存的相对位置恢复坐标，移动端也保持在可见区域内
function restorePosition() {
  if (!this.$refs.pet) {
    return;
  }
  // 图片草稿会改变输入区高度，尺寸变化时同步避让快捷问题和输入框
  const composer = document.querySelector(".support-main .support-composer");
  if (composer && this.layoutObserver) {
    this.layoutObserver.observe(composer);
  }
  const rectangle = this.$refs.pet.getBoundingClientRect();
  const availableWidth = window.innerWidth - rectangle.width;
  const availableHeight = window.innerHeight - rectangle.height;
  this.applyPosition(
    availableWidth * this.settings.horizontal,
    availableHeight * this.settings.vertical,
  );
}

// 所有定位入口共用可见范围检查，贴边和缩小窗口时修正坐标
function applyPosition(
  left,
  top,
) {
  if (!this.$refs.pet) {
    return;
  }
  const rectangle = this.$refs.pet.getBoundingClientRect();
  let minimumTop = pet.PET_MARGIN;
  let viewportBottom = window.innerHeight;
  const header = document.querySelector(".platform-header");
  const status = document.querySelector(".workspace-status");
  if (header) {
    minimumTop = header.getBoundingClientRect().bottom + pet.PET_MARGIN;
  }
  if (status) {
    viewportBottom = Math.min(
      viewportBottom,
      status.getBoundingClientRect().top,
    );
  }
  // 顶部菜单和底部状态栏属于固定控件，拖动范围要扣除它们占用的空间
  const position = pet.clampPosition(
    left,
    top,
    rectangle.width,
    rectangle.height,
    window.innerWidth,
    viewportBottom,
  );
  this.left = position.left;
  const maximumTop = Math.max(
    pet.PET_MARGIN,
    viewportBottom - rectangle.height - pet.PET_MARGIN,
  );
  this.top = Math.min(maximumTop, Math.max(minimumTop, position.top));
  let composer = document.querySelector(".support-main .support-suggestions");
  if (!composer) {
    composer = document.querySelector(".support-main .support-composer");
  }
  if (composer) {
    const composerBox = composer.getBoundingClientRect();
    const input = document.querySelector(".support-main .support-composer");
    let bottom = composerBox.bottom;
    if (input) {
      bottom = Math.max(bottom, input.getBoundingClientRect().bottom);
    }
    const overlapsHorizontally =
      this.left < composerBox.right &&
      this.left + rectangle.width > composerBox.left;
    const overlapsVertically =
      this.top < bottom &&
      this.top + rectangle.height > composerBox.top;
    if (overlapsHorizontally && overlapsVertically) {
      this.top = Math.max(
        minimumTop,
        composerBox.top - rectangle.height - pet.PET_MARGIN,
      );
    }
  }
}

// 位置按窗口比例存储，改变窗口大小后仍能找回宠物
function savePosition() {
  const rectangle = this.$refs.pet.getBoundingClientRect();
  const availableWidth = Math.max(1, window.innerWidth - rectangle.width);
  const availableHeight = Math.max(1, window.innerHeight - rectangle.height);
  this.settings.horizontal = this.left / availableWidth;
  this.settings.vertical = this.top / availableHeight;
  this.savePreferences();
}

// 鼠标或触摸按下时记录起点，只有主要指针可以开始拖动
function startDrag(event) {
  if (!event.isPrimary || event.button !== 0 || this.drag) {
    return;
  }
  this.drag = {
    id: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    left: this.left,
    top: this.top,
    moved: false,
  };
  this.suppressClick = false;
  event.currentTarget.setPointerCapture(event.pointerId);
}

// 指针移动达到阈值后进入拖动状态，短距离轻点仍视为点击
function moveDrag(event) {
  if (!this.drag || event.pointerId !== this.drag.id) {
    return;
  }
  const distanceX = event.clientX - this.drag.startX;
  const distanceY = event.clientY - this.drag.startY;
  if (Math.abs(distanceX) + Math.abs(distanceY) > pet.DRAG_THRESHOLD) {
    this.drag.moved = true;
  }
  if (this.drag.moved) {
    this.suppressClick = true;
    this.closePanel();
    this.applyPosition(this.drag.left + distanceX, this.drag.top + distanceY);
  }
}

// 拖动完成后保存位置，并阻止随后的点击误打开聊天
function finishDrag(event) {
  if (!this.drag || event.pointerId !== this.drag.id) {
    return;
  }
  if (this.drag.moved) {
    this.savePosition();
  }
  this.drag = null;
  if (event.currentTarget.hasPointerCapture(event.pointerId)) {
    event.currentTarget.releasePointerCapture(event.pointerId);
  }
}

// 系统取消手势时结束拖动，保留当前可见位置
function cancelDrag() {
  this.drag = null;
}

// 方向键提供拖动以外的移动方式，回车和空格仍由按钮负责打开聊天
function moveWithKeyboard(event) {
  let left = this.left;
  let top = this.top;
  if (event.key === "ArrowLeft") {
    left = left - KEYBOARD_DISTANCE;
  } else if (event.key === "ArrowRight") {
    left = left + KEYBOARD_DISTANCE;
  } else if (event.key === "ArrowUp") {
    top = top - KEYBOARD_DISTANCE;
  } else if (event.key === "ArrowDown") {
    top = top + KEYBOARD_DISTANCE;
  } else {
    return;
  }
  event.preventDefault();
  this.applyPosition(left, top);
  this.savePosition();
}

// 只有普通点击打开浮窗，拖动结束产生的点击会被忽略
function togglePanel(event) {
  if (this.suppressClick && event.detail !== 0) {
    this.suppressClick = false;
    return;
  }
  this.$refs.panel.togglePopover();
}

// 同步原生浮窗状态，点击空白或按 Escape 也能取消正在等待的请求
function panelToggled(event) {
  this.expanded = event.newState === "open";
  if (this.expanded) {
    this.$nextTick(this.$refs.chat.scrollMessages);
  } else {
    if (this.$refs.chat.pending) {
      this.$refs.chat.stop();
    }
    this.thinking = false;
  }
}

// 原生浮窗负责键盘焦点与 Escape，业务代码只发出关闭命令
function closePanel() {
  if (this.$refs.panel) {
    this.$refs.panel.hidePopover();
  }
}

// 只有鼠标和笔停留时显示气泡，触摸直接交给原来的拖动逻辑
function hoverPet(event) {
  if (event.pointerType !== "touch") {
    this.hovered = true;
  }
}

// 指针离开时清掉悬停状态，键盘焦点单独保留
function leavePet() {
  this.hovered = false;
}

// 键盘聚焦时也能看到气泡，普通点击保持原来的聊天操作
function focusPet(event) {
  this.focused = event.currentTarget.matches(":focus-visible");
}

// 键盘移到其他控件后收起气泡，悬停状态由指针事件维护
function blurPet() {
  this.focused = false;
}

// 子组件通知等待状态，宠物据此切换思考图片
function setThinking(pending) {
  this.thinking = pending;
}

// 保存宠物的开关和位置，失败时保留本次界面状态
function savePreferences() {
  try {
    pet.saveSettings(this.userId, this.settings);
  } catch (error) {
    this.notice = "浏览器无法保存宠物设置";
  }
}

// 隐藏后留下一个小恢复按钮，用户随时可以重新显示
function hidePet() {
  this.closePanel();
  this.hovered = false;
  this.focused = false;
  this.settings.visible = false;
  this.savePreferences();
}

// 重新显示后恢复位置，不改动已有聊天记录
function showPet() {
  this.settings.visible = true;
  this.savePreferences();
  this.$nextTick(this.restorePosition);
}

// 恢复默认位置，提供容易找到的非拖动入口
function resetPosition() {
  this.settings.horizontal = 1;
  this.settings.vertical = pet.DEFAULT_VERTICAL_POSITION;
  this.restorePosition();
  this.savePreferences();
}

// 只保存当前账号的宠物聊天，不与全屏客服正在处理的会话混用
function persistChat() {
  try {
    support.saveConversations(this.userId + "-pet", [this.conversation]);
  } catch (error) {
    this.notice = "浏览器无法保存这段对话";
  }
}

// 新建宠物对话后旧聊天组件卸载，其请求会自动取消
function clearChat() {
  this.conversation = support.createConversation();
  this.persistChat();
}

export default {
  components: { SupportChat: SupportChat },
  props: { userId: { type: Number, required: true }, available: { type: Boolean, required: true } },
  data: petState,
  computed: {
    positionStyle: positionStyle,
    imageState: imageState,
    showThought: showThought,
    thoughtDescription: thoughtDescription,
    thought: thought,
    moodToken: moodToken,
  },
  mounted: mountPet,
  beforeUnmount: disposePet,
  watch: { "$route.path": pageChanged, showThought: refreshMood, moodToken: resetMood, available: resetMood },
  methods: {
    restorePosition: restorePosition,
    applyPosition: applyPosition,
    savePosition: savePosition,
    startDrag: startDrag,
    moveDrag: moveDrag,
    finishDrag: finishDrag,
    cancelDrag: cancelDrag,
    moveWithKeyboard: moveWithKeyboard,
    togglePanel: togglePanel,
    panelToggled: panelToggled,
    closePanel: closePanel,
    hoverPet: hoverPet,
    leavePet: leavePet,
    focusPet: focusPet,
    blurPet: blurPet,
    resetMood: resetMood,
    rotateThought: rotateThought,
    hideIdleThought: hideIdleThought,
    setThinking: setThinking,
    savePreferences: savePreferences,
    hidePet: hidePet,
    showPet: showPet,
    resetPosition: resetPosition,
    persistChat: persistChat,
    clearChat: clearChat,
    checkModal: checkModal,
  },
};
</script>
