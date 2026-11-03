// 创建共用状态仓库，持久化插件用于恢复浏览器保存的登录信息
import { createPinia } from "pinia";
import piniaPluginPersistedstate from "pinia-plugin-persistedstate";

const pinia = createPinia();
pinia.use(piniaPluginPersistedstate);

export default pinia;
