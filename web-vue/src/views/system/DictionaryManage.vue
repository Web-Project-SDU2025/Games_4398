<template>
  <section class="system-page">
    <div class="base_form">
      <div class="base_header">
        <div class="base_title">字典管理</div>
      </div>

      <div class="base_query_oneLine">
        <div class="query_left">
          <button class="commButton" @click="addItemFirst">添加数据项名</button>
          <button class="commButton" @click="addItem">添加数据项</button>
          <button class="commButton" @click="editItem">修改</button>
          <button class="commButton" @click="deleteItem">删除</button>
        </div>
      </div>
      <div class="motion-results" :aria-busy="loaded && loading">
        <el-tree
          :data="nodes"
          :props="defaultProps"
          accordion
          @node-click="onNodeClick"
        ></el-tree>
      </div>
    </div>
    <dialog id="dictionary-editor" ref="editor">
      <div class="base_title">{{ dialogTitle }}</div>
      <div class="dialog-div">
        <table class="dialog-content">
          <tr>
            <td colspan="1">字典名</td>
            <td colspan="1">
              {{ editedNode.parentTitle }}
            </td>
          </tr>
          <tr>
            <td colspan="1">数据值</td>
            <td colspan="1">
              <input v-model="editedNode.value" class="commInput" />
            </td>
          </tr>
          <tr>
            <td colspan="1">数据标题</td>
            <td colspan="1">
              <input v-model="editedNode.title" class="commInput" />
            </td>
          </tr>
          <tr>
            <td colspan="2">
              <button class="commButton" @click="close()">取消</button>
              <button class="commButton" @click="confirm()">确认</button>
            </td>
          </tr>
        </table>
      </div>
    </dialog>
  </section>
</template>

<script>
import {
  getDictionaryTreeNodeList,
  dictionarySave,
  dictionaryDelete,
} from "~/services/systemServ";
import { message, messageConform } from "~/tools/messageBox";
import { createNodeForm } from "../../logic/forms.js";
import { useAppStore } from "../../stores/app.js";
export default {
  // 创建当前树形页面的状态
  data: function treeState() {
    return {
      nodes: [],
      editedNode: {},
      currentNode: {},
      dialogTitle: "",
      loading: false,
      loaded: false,
      defaultProps: {
        id: "id",
        children: "children",
        label: "label",
      },
    };
  },
  // 进入页面时加载树形数据
  created: function created() {
    this.query();
  },
  methods: {
    // 按请求令牌接收字典结果，切号后丢弃旧响应，当前账号失败时显示提示
    query: async function query() {
      if (this.loading) {
        return;
      }
      this.loading = true;
      const token = useAppStore().userInfo.token;
      try {
        const nodes = await getDictionaryTreeNodeList();
        if (token !== useAppStore().userInfo.token) {
          return;
        }
        this.nodes = nodes;
        this.loaded = true;
      } catch (error) {
        if (token === useAppStore().userInfo.token) {
          message(this, "字典读取失败，请稍后重试");
        }
      } finally {
        this.loading = false;
      }
    },
    // 记录当前选中的树节点，供后续编辑使用
    onNodeClick: function onNodeClick(node) {
      this.currentNode = node;
    },
    // 准备一级节点的表单并打开对话框
    addItemFirst: function addItemFirst() {
      this.editedNode = createNodeForm(null);
      this.dialogTitle = "添加字典名对话框";
      this.$refs.editor.showModal();
    },
    // 准备所选节点下的子节点表单
    addItem: function addItem() {
      if (!this.currentNode.id) {
        message(this, "请先选择字典名");
        return;
      }
      this.editedNode = createNodeForm(null);
      this.editedNode.pid = this.currentNode.id;
      this.editedNode.parentTitle = this.currentNode.title;
      this.dialogTitle = "添加字典项对话框";
      this.$refs.editor.showModal();
    },
    // 复制选中节点作为表单，取消编辑时保留树的原值
    editItem: function editItem() {
      if (!this.currentNode.id) {
        message(this, "请先选择数据项");
        return;
      }
      this.editedNode = createNodeForm(this.currentNode);
      this.dialogTitle = "修改字典项对话框";
      this.$refs.editor.showModal();
    },
    // 关闭当前页面的对话框
    close: function close() {
      this.$refs.editor.close();
    },
    // 保存节点并重新加载树形列表
    confirm: async function confirm() {
      try {
        const result = await dictionarySave(this.editedNode);
        if (result.code !== 0) {
          message(this, result.msg);
          return;
        }
        this.close();
        await this.query();
      } catch (error) {
        message(this, "保存失败，已保留表单内容");
      }
    },
    // 确认后删除节点，取消时直接结束操作
    deleteItem: async function deleteItem() {
      if (!this.currentNode.id) {
        message(this, "请先选择数据项");
        return;
      }
      const result = await messageConform("确认删除吗?");
      if (!result) {
        return;
      }
      try {
        const result = await dictionaryDelete(this.currentNode.id);
        if (result.code !== 0) {
          message(this, result.msg);
          return;
        }
        await this.query();
      } catch (error) {
        message(this, "删除失败，请稍后重试");
      }
    },
  },
};
</script>
