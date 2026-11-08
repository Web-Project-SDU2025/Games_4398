import { createApp } from "vue";
// 应用入口只负责装配页面、路由和状态仓库，业务处理由普通 JS 模块完成
import App from "./App.vue";
import pinia from "./stores";

// Element Plus 组件由构建插件按需引入，入口不再全量安装组件库
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
import "~/styles/toast.css";
import { toastDirective } from "./tools/toast.js";
import { revealDirective } from "./tools/motion.js";
import { markdownDirective } from "./tools/markdown.js";

// 提示与确认框通过命令式 API 调用，构建插件扫描不到，样式需要在入口手动加载
import "element-plus/es/components/message/style/css";
import "element-plus/es/components/message-box/style/css";
import router from "./router";

const app = createApp(App);
app.use(pinia);
app.use(router);
app.directive("reveal", revealDirective);
app.directive("markdown", markdownDirective);
app.directive("toast", toastDirective);
// 初始路由确定后挂载页面，刷新时直接显示目标路由
async function startApp() {
  await router.isReady();
  app.mount("#app");
}

startApp();
