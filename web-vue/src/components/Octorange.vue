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
      <Transition name="pet-greeting">
        <p
          v-if="greeting && settings.greetings && !expanded && !hovered"
          class="pet-greeting"
          :style="greetingStyle"
        >
          {{ greeting }}
        </p>
      </Transition>
      <button
        ref="toggle"
        type="button"
        class="pet-toggle"
        aria-label="与 Octorange 聊游戏"
        title="与 Octorange 聊游戏"
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
        @focus="hoverPet"
        @blur="leavePet"
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
        <div><strong>Octorange</strong><small>游戏助手 · 百炼</small></div>
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
        <label
          ><input
            v-model="settings.greetings"
            type="checkbox"
            @change="savePreferences"
          />游戏提示</label
        >
        <RouterLink to="/AiSupport" @click="closePanel"
          >打开 AI 客服</RouterLink
        >
      </footer>
      <p v-if="notice" class="pet-notice" role="status">{{ notice }}</p>
    </section>
  </div>
</template>

<script>
import SupportChat from "./SupportChat.vue";
import * as pet from "../logic/pet.js";
import * as support from "../logic/support.js";

// 键盘每次移动宠物的距离，单位为像素
const KEYBOARD_DISTANCE = 20;
// 提示气泡的预留宽度，单位为像素
const GREETING_WIDTH = 220;
// 宠物靠近顶栏时气泡改向下显示的距离，单位为像素
const GREETING_TOP_CLEARANCE = 80;

// 宠物只负责浮窗和位置，真实问答使用和客服页面相同的聊天组件
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
    thinking: false,
    modalOpen: false,
    greeting: "",
    greetingIndex: 0,
    greetingTimer: null,
    hideTimer: null,
    observer: null,
    layoutObserver: null,
    notice: "",
    drag: null,
    suppressClick: false,
    reducedMotion: null,
  };
}

// 把位置写在固定容器上，状态图片的淡入由独立 CSS 控制
function positionStyle() {
  return { left: this.left + "px", top: this.top + "px" };
}

// 按宠物到窗口边缘的距离选择气泡方向，贴边时向内显示
function greetingStyle() {
  const available =
    window.innerWidth - this.left - GREETING_WIDTH - pet.PET_MARGIN;
  let offset = Math.min(0, available);
  offset = Math.max(pet.PET_MARGIN - this.left, offset);
  const header = document.querySelector(".platform-header");
  if (
    header &&
    this.top < header.getBoundingClientRect().bottom + GREETING_TOP_CLEARANCE
  ) {
    return { left: offset + "px", right: "auto", top: "100%", bottom: "auto" };
  }
  return { left: offset + "px", right: "auto" };
}

// 回答期间优先显示思考状态，结束后按悬停状态选择图片
function imageState() {
  if (this.thinking) {
    return "thinking";
  }
  if (this.hovered) {
    return "hover";
  }
  return "idle";
}

// 监听尺寸和业务弹窗，编辑弹窗打开时暂时隐藏宠物
function mountPet() {
  this.reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
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
  this.greetingTimer = window.setInterval(
    this.showGreeting,
    pet.GREETING_INTERVAL,
  );
  this.showGreeting();
}

// 卸载时清理定时器和全局监听，切号后由新组件重新初始化
function disposePet() {
  window.removeEventListener("resize", this.restorePosition);
  window.clearInterval(this.greetingTimer);
  window.clearTimeout(this.hideTimer);
  if (this.observer) {
    this.observer.disconnect();
  }
  if (this.layoutObserver) {
    this.layoutObserver.disconnect();
  }
}

// 切换页面后重新避让控件，等待新页面的输入框实际显示再测量
function pageChanged() {
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
  const rectangle = this.$refs.pet.getBoundingClientRect();
  const availableWidth = window.innerWidth - rectangle.width;
  const availableHeight = window.innerHeight - rectangle.height;
  this.applyPosition(
    availableWidth * this.settings.horizontal,
    availableHeight * this.settings.vertical,
  );
}

// 所有定位入口共用可见范围检查，贴边和缩小窗口时修正坐标
function applyPosition(left, top) {
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
  const composer = document.querySelector(".support-main .support-composer");
  if (composer) {
    const composerBox = composer.getBoundingClientRect();
    const overlapsHorizontally =
      this.left < composerBox.right &&
      this.left + rectangle.width > composerBox.left;
    const overlapsVertically =
      this.top < composerBox.bottom &&
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

// 鼠标停留和键盘聚焦都给出相同状态反馈，同时暂停循环提示
function hoverPet() {
  this.hovered = true;
}

// 移出宠物时回到待机，真实请求仍优先显示思考状态
function leavePet() {
  this.hovered = false;
}

// 子组件通知等待状态，宠物据此切换思考图片
function setThinking(pending) {
  this.thinking = pending;
}

// 游戏提示只是招呼语，用户问题始终交给百炼回答
function showGreeting() {
  if (
    !this.settings.visible ||
    !this.settings.greetings ||
    this.expanded ||
    this.hovered ||
    this.modalOpen
  ) {
    return;
  }
  if (this.reducedMotion && this.reducedMotion.matches) {
    return;
  }
  this.greeting = pet.GREETINGS[this.greetingIndex];
  this.greetingIndex = (this.greetingIndex + 1) % pet.GREETINGS.length;
  window.clearTimeout(this.hideTimer);
  this.hideTimer = window.setTimeout(this.hideGreeting, pet.GREETING_DURATION);
}

// 清空当前提示，下一轮定时器再选择新的提示文字
function hideGreeting() {
  this.greeting = "";
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
  props: { userId: { type: Number, required: true } },
  data: petState,
  computed: {
    positionStyle: positionStyle,
    greetingStyle: greetingStyle,
    imageState: imageState,
  },
  mounted: mountPet,
  beforeUnmount: disposePet,
  watch: { "$route.path": pageChanged },
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
    setThinking: setThinking,
    showGreeting: showGreeting,
    hideGreeting: hideGreeting,
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
