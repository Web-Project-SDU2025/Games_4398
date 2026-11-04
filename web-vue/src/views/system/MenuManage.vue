<template>
  <section class="system-page">
    <div class="base_form">
      <div class="base_header">
        <div class="base_title">菜单管理</div>
      </div>

      <div class="base_query_oneLine">
        <div class="query_left">
          <button class="commButton" @click="addItemFirst">添加一级菜单</button>
        </div>
      </div>
      <div
        class="custom-tree-container motion-results"
        :aria-busy="loaded && loading"
      >
        <el-tree
          :data="nodes"
          show-checkbox
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
        >
          <template #default="slot">
            <span class="custom-tree-node">
              <span>{{ slot.node.label }}</span>
              <span>
                <button
                  type="button"
                  class="commButton"
                  @click="addItem(slot.data)"
                >
                  添加
                </button>
                <button
                  type="button"
                  class="commButton"
                  @click="editItem(slot.data)"
                >
                  修改
                </button>
                <button
                  type="button"
                  class="commButton"
                  @click="deleteItem(slot.data)"
                >
                  删除
                </button>
              </span>
            </span>
          </template>
        </el-tree>
      </div>
    </div>
    <dialog id="menu-editor" ref="editor">
      <div class="base_title">{{ dialogTitle }}</div>
      <div class="dialog-div">
        <table class="dialog-content">
          <tr>
            <td colspan="1">父节点</td>
            <td colspan="1">
              {{ editedNode.parentTitle }}
            </td>
          </tr>
          <tr>
            <td colspan="1">主键ID</td>
            <td colspan="1">
              <input
                v-model="editedNode.id"
                class="commInput"
                :disabled="disabled"
              />
            </td>
          </tr>
          <tr>
            <td colspan="1">菜单名称</td>
            <td colspan="1">
              <input v-model="editedNode.value" class="commInput" />
            </td>
          </tr>
          <tr>
            <td colspan="1">菜单标题</td>
            <td colspan="1">
              <input v-model="editedNode.title" class="commInput" />
            </td>
          </tr>
          <tr>
            <td colspan="1">角色</td>
            <td colspan="1">
              <input type="checkbox" v-model="roleAdmin" />管理员
              <input type="checkbox" v-model="roleGameUser" />游戏用户
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
  getMenuTreeNodeList,
  menuSave,
  menuDelete,
} from "~/services/systemServ";
import { message, messageConform } from "~/tools/messageBox";
import { createNodeForm, createRoleIds } from "../../logic/forms.js";
import { useAppStore } from "../../stores/app.js";
export default {
  // 创建当前树形页面的状态
  data: function treeState() {
    return {
      nodes: [],
      editedNode: {},
      dialogTitle: "",
      disabled: false,
      editType: 0,
      roleAdmin: false,
      roleGameUser: false,
      loading: false,
      loaded: false,
    };
  },
  // 进入页面时加载树形数据
  created: function created() {
    this.query();
  },
  methods: {
    // 等待时保留当前树，按请求令牌接收结果，切号后丢弃旧账户响应
    query: async function query() {
      if (this.loading) {
        return;
      }
      this.loading = true;
      const token = useAppStore().userInfo.token;
      try {
        const nodes = await getMenuTreeNodeList();
        if (token !== useAppStore().userInfo.token) {
          return;
        }
        this.nodes = nodes;
        this.loaded = true;
      } catch (error) {
        if (token === useAppStore().userInfo.token) {
          message(this, "菜单读取失败，请稍后重试");
        }
      } finally {
        this.loading = false;
      }
    },
    // 准备一级节点的表单并打开对话框
    addItemFirst: function addItemFirst() {
      this.editedNode = createNodeForm(null);
      this.roleAdmin = false;
      this.roleGameUser = false;
      this.dialogTitle = "添加一级菜单对话框";
      this.disabled = false;
      this.editType = 0;
      this.$refs.editor.showModal();
    },
    // 准备所选节点下的子节点表单
    addItem: function addItem(node) {
      this.editedNode = createNodeForm(null);
      this.editedNode.pid = node.id;
      this.editedNode.parentTitle = node.title;
      this.roleAdmin = false;
      this.roleGameUser = false;
      this.dialogTitle = "添加菜单对话框";
      this.disabled = false;
      this.editType = 1;
      this.$refs.editor.showModal();
    },
    // 复制选中节点作为表单，取消编辑时保留树的原值
    editItem: function editItem(node) {
      this.editedNode = createNodeForm(node);
      let roles = [];
      if (node.userTypeIds) {
        roles = node.userTypeIds.split(",");
      }
      this.roleAdmin = roles.includes("1");
      this.roleGameUser = roles.includes("2");
      this.dialogTitle = "修改菜单对话框";
      this.disabled = true;
      this.editType = 2;
      this.$refs.editor.showModal();
    },
    // 关闭当前页面的对话框
    close: function close() {
      this.$refs.editor.close();
    },
    // 保存节点并重新加载树形列表
    confirm: async function confirm() {
      this.editedNode.userTypeIds = createRoleIds(
        this.roleAdmin,
        this.roleGameUser,
      );
      try {
        const result = await menuSave(this.editType, this.editedNode);
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
    deleteItem: async function deleteItem(node) {
      const result = await messageConform("确认删除吗?");
      if (!result) {
        return;
      }
      try {
        const result = await menuDelete(node.id);
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
