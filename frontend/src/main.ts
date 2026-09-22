import { createApp } from 'vue'
import Login from './components/Login.vue'
import './utils/http' // 初始化HTTP配置

const app = createApp(Login)
app.mount('#app')
