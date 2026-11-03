<template>
  <section class="support-page" aria-label="AI 客服">
    <aside class="support-history">
      <button type="button" class="support-new" @click="newConversation">
        <span class="i-ep-edit" aria-hidden="true"></span>新建对话
      </button>
      <h2>历史对话</h2>
      <div
        v-for="item in conversations"
        :key="item.id"
        class="support-history-row"
        :class="{ selected: current && current.id === item.id }"
      >
        <button
          type="button"
          class="support-history-title"
          :title="item.title"
          @click="selectConversation(item.id)"
        >
          {{ item.title }}
        </button>
        <button
          type="button"
          aria-label="重命名对话"
          title="重命名对话"
          @click="renameConversation(item)"
        >
          <span class="i-ep-edit-pen" aria-hidden="true"></span>
        </button>
        <button
          type="button"
          aria-label="删除对话"
          title="删除对话"
          @click="removeConversation(item.id)"
        >
          <span class="i-ep-delete" aria-hidden="true"></span>
        </button>
      </div>
    </aside>
    <div class="support-main">
      <header class="support-heading">
        <strong>AI 客服</strong><span>Octorange · 百炼</span>
      </header>
      <Transition name="page" mode="out-in">
        <SupportChat
          v-if="current"
          :key="owner + '-' + current.id"
          :conversation="current"
          @changed="persist"
        />
      </Transition>
      <p v-if="notice" v-reveal="notice" class="support-notice" role="status">
        {{ notice }}
      </p>
    </div>
    <dialog ref="nameEditor">
      <form class="game-form" @submit.prevent="saveName">
        <h3>重命名对话</h3>
        <label
          >对话名称<input v-model.trim="newName" maxlength="40" required
        /></label>
        <div class="game-actions">
          <button type="submit">保存</button
          ><button type="button" @click="closeName">取消</button>
        </div>
      </form>
    </dialog>
  </section>
</template>
<script>
import * as support from "../logic/support.js";
import { useAppStore } from "../stores/app.js";
import { messageConform } from "../tools/messageBox.js";
import SupportChat from "../components/SupportChat.vue";

export default {
  components: { SupportChat: SupportChat },
  // 页面只维护输入和选中的会话，内容整理放在普通 JS 中
  data: function supportState() {
    return {
      conversations: [],
      currentId: "",
      notice: "",
      owner: useAppStore().userInfo.id,
      nameId: "",
      newName: "",
    };
  },
  computed: {
    // 跟随状态仓库中的账号变化，切号时重新读取对应历史
    accountId: function accountId() {
      return useAppStore().userInfo.id;
    },
    // 根据编号查找当前会话，避免历史切换时复制错消息
    current: function currentConversation() {
      for (const conversation of this.conversations) {
        if (conversation.id === this.currentId) {
          return conversation;
        }
      }
      return null;
    },
  },
  // 按登录账户读取本机会话，切号后不共享聊天历史
  created: function loadConversations() {
    this.loadAccount();
  },
  watch: {
    // 切换身份时清理当前页面的账号状态
    accountId: function accountChanged() {
      this.loadAccount();
    },
  },
  methods: {
    // 切号时重新读取历史，组件用账号编号重建以取消旧请求
    loadAccount: function loadAccount() {
      this.owner = useAppStore().userInfo.id;
      this.conversations = support.readConversations(this.owner);
      if (this.conversations.length === 0) {
        this.newConversation();
      } else {
        this.currentId = this.conversations[0].id;
      }
    },
    // 存储失败不丢弃当前内容，只给出可见提示
    persist: function persist() {
      try {
        support.saveConversations(this.owner, this.conversations);
      } catch (error) {
        this.notice = "浏览器空间不足，本次对话尚未保存";
      }
    },
    // 创建空会话并立即显示输入区
    newConversation: function newConversation() {
      const conversation = support.createConversation();
      this.conversations.unshift(conversation);
      this.currentId = conversation.id;
      this.persist();
    },
    // 历史会话切换只改变当前编号，不发送网络请求
    selectConversation: function selectConversation(id) {
      this.currentId = id;
      this.notice = "";
    },
    // 删除会话前确认，取消不改变历史列表
    removeConversation: async function removeConversation(id) {
      const confirmed = await messageConform("删除这段本地对话？");
      if (!confirmed) {
        return;
      }
      for (
        let index = 0;
        index < this.conversations.length;
        index = index + 1
      ) {
        if (this.conversations[index].id === id) {
          this.conversations.splice(index, 1);
          break;
        }
      }
      if (this.conversations.length === 0) {
        this.newConversation();
      } else if (this.currentId === id) {
        this.currentId = this.conversations[0].id;
      }
      this.persist();
    },
    // 重命名使用原生对话框，和其他弹窗保持相同动画
    renameConversation: function renameConversation(item) {
      this.nameId = item.id;
      this.newName = item.title;
      this.$refs.nameEditor.showModal();
    },
    // 只修改对应会话名称，不修改消息内容
    saveName: function saveName() {
      for (const item of this.conversations) {
        if (item.id === this.nameId) {
          item.title = this.newName;
        }
      }
      this.persist();
      this.closeName();
    },
    // 取消重命名时不写入会话
    closeName: function closeName() {
      this.$refs.nameEditor.close();
    },
  },
};
</script>
