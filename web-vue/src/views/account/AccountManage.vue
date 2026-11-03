<template>
  <section class="account-center">
    <h1>管理中心</h1>
    <div class="admin-tabs" role="group" aria-label="管理分类">
      <button
        type="button"
        :aria-pressed="section === 'accounts'"
        @click="selectSection('accounts')"
      >
        账户管理
      </button>
      <button
        type="button"
        :aria-pressed="section === 'scores'"
        @click="selectSection('scores')"
      >
        游戏分数管理
      </button>
      <button
        type="button"
        :aria-pressed="section === 'records'"
        @click="selectSection('records')"
      >
        游戏记录管理
      </button>
    </div>
    <div class="admin-content">
      <Transition name="panel">
        <GameUserManage v-if="section === 'accounts'" key="accounts" />
        <RecordManage
          v-else
          :key="section"
          :score-only="section === 'scores'"
        />
      </Transition>
    </div>
  </section>
</template>
<script>
import GameUserManage from "./GameUserManage.vue";
import RecordManage from "./RecordManage.vue";

export default {
  components: { GameUserManage: GameUserManage, RecordManage: RecordManage },
  // 管理分类留在当前页面，角色权限由路由和后端共同检查
  data: function managementState() {
    return { section: "accounts" };
  },
  methods: {
    // 切换管理视图，不保留已关闭的编辑表单
    selectSection: function selectSection(section) {
      this.section = section;
    },
  },
};
</script>
