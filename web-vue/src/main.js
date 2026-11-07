import { createApp } from "vue";
// 应用入口只负责装配页面、路由和状态仓库，业务处理由普通 JS 模块完成
import App from "./App.vue";
import pinia from "./stores";

import ElementPlus from "element-plus";
// 应用入口加载本地组件样式
import "element-plus/dist/index.css";

import "uno.css";
import "~/styles/platform.css";
import "~/styles/game.css";
import "~/styles/game-runtime.css";
import "~/styles/store.css";
import "~/styles/blockcoin-history.css";
import "~/styles/auth.css";
// 提前加载工作区和首页样式，首次切换页面时不用临时改变布局
import "~/styles/workspace.css";
import "~/styles/home.css";
import "~/styles/support.css";
import "~/styles/pet.css";
import "~/styles/settings.css";
import "~/styles/music.css";
import "~/styles/messages.css";
import "~/styles/motion.css";
import "~/styles/cursor.css";
import { revealDirective } from "./tools/motion.js";
import { markdownDirective } from "./tools/markdown.js";

// 管理页面会显示消息提示，因此在入口加载提示组件样式
import "element-plus/es/components/message/style/css";
import router from "./router";

const app = createApp(App);
app.use(pinia);
app.use(ElementPlus);
app.use(router);
app.directive("reveal", revealDirective);
app.directive("markdown", markdownDirective);
// 初始路由确定后挂载页面，刷新时直接显示目标路由
async function startApp() {
  await router.isReady();
  app.mount("#app");
}

startApp();
