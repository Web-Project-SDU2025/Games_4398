import { createApp } from 'vue'
import Home from './components/Home.vue'
import './utils/http' // 初始化HTTP配置

const app = createApp(Home)
app.mount('#app')
