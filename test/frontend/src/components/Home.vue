<template>
  <div class="home-container">
    <!-- 顶部导航栏 -->
    <header class="header">
      <div class="header-content">
        <h1 class="logo">4398游戏管理系统</h1>
        <div class="user-info">
          <span class="username">{{ userInfo?.username || '未知用户' }}</span>
          <button @click="handleLogout" class="btn-logout">退出登录</button>
        </div>
      </div>
    </header>

    <!-- 主内容区域 -->
    <main class="main-content">
      <div class="welcome-section">
        <h2>欢迎回来，{{ userInfo?.username }}！</h2>
        <p class="subtitle">当前时间：{{ currentTime }}</p>
      </div>

      <!-- 菜单列表 -->
      <div v-if="menus.length > 0" class="menu-grid">
        <div
          v-for="menu in menus"
          :key="menu.id"
          class="menu-card"
          @click="handleMenuClick(menu)"
        >
          <div class="menu-icon">{{ menu.icon || '📋' }}</div>
          <div class="menu-info">
            <h3>{{ menu.title }}</h3>
            <p>{{ menu.name }}</p>
          </div>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-else-if="loading" class="loading">
        <div class="spinner"></div>
        <p>加载菜单中...</p>
      </div>

      <!-- 暂无菜单 -->
      <div v-else class="empty">
        <p>暂无可用菜单</p>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { userApi, menuApi } from '@/api'
import { storage } from '@/utils/common'
import { formatDate } from '@/utils/datetime'
import type { User, Menu } from '@/types'

// 状态
const userInfo = ref<User | null>(null)
const menus = ref<Menu[]>([])
const loading = ref(true)
const currentTime = ref('')

// 更新时间
const updateTime = () => {
  currentTime.value = formatDate(new Date(), 'yyyy-MM-dd HH:mm:ss')
}

// 加载用户信息
const loadUserInfo = () => {
  const storedInfo = storage.get<User>('userInfo')
  if (storedInfo) {
    userInfo.value = storedInfo
  } else {
    // 没有用户信息，跳转到登录页
    window.location.href = '/index.html'
  }
}

// 加载菜单
const loadMenus = async () => {
  try {
    loading.value = true
    const response = await menuApi.getUserMenus()
    if (response.code === 0 && response.data) {
      menus.value = response.data
    }
  } catch (error) {
    console.error('加载菜单失败:', error)
  } finally {
    loading.value = false
  }
}

// 菜单点击
const handleMenuClick = (menu: Menu) => {
  console.log('点击菜单:', menu)
  if (menu.path) {
    // 这里可以根据实际需求进行路由跳转
    alert(`即将跳转到：${menu.path}`)
  }
}

// 退出登录
const handleLogout = async () => {
  if (!confirm('确定要退出登录吗？')) {
    return
  }

  try {
    await userApi.logout()
  } catch (error) {
    console.error('退出登录失败:', error)
  } finally {
    // 清除本地存储
    storage.remove('token')
    storage.remove('userInfo')
    // 跳转到登录页
    window.location.href = '/index.html'
  }
}

// 组件挂载
onMounted(() => {
  loadUserInfo()
  loadMenus()
  updateTime()
  // 每秒更新时间
  setInterval(updateTime, 1000)
})
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  background: #0a0d12;
}

/* 顶部导航栏 */
.header {
  background: #0f131c;
  border-bottom: 1px solid #1e2636;
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 1rem 2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo {
  color: #38bdf8;
  font-size: clamp(1.25rem, 3vw, 1.5rem);
  font-weight: 600;
  letter-spacing: -0.02em;
  margin: 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.username {
  color: #e2e8f0;
  font-size: 0.875rem;
  font-weight: 500;
}

.btn-logout {
  padding: 0.5rem 1.25rem;
  background: transparent;
  color: #f87171;
  border: 1px solid #1e2636;
  border-radius: 999px;
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-logout:hover {
  border-color: #f87171;
  background: rgba(248, 113, 113, 0.1);
}

/* 主内容区域 */
.main-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 2rem;
}

.welcome-section {
  margin-bottom: 3rem;
}

.welcome-section h2 {
  color: #e2e8f0;
  font-size: clamp(1.5rem, 4vw, 2rem);
  font-weight: 600;
  margin-bottom: 0.5rem;
  letter-spacing: -0.02em;
}

.subtitle {
  color: #94a3b8;
  font-size: 0.875rem;
}

/* 菜单网格 */
.menu-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1.5rem;
}

.menu-card {
  background: #0f131c;
  border: 1px solid #1e2636;
  border-radius: 1rem;
  padding: 1.5rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  cursor: pointer;
  transition: all 0.2s;
}

.menu-card:hover {
  transform: translateY(-2px);
  border-color: #38bdf8;
  box-shadow: 0 8px 24px rgba(56, 189, 248, 0.15);
}

.menu-icon {
  width: 3rem;
  height: 3rem;
  background: #161d2b;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  flex-shrink: 0;
}

.menu-info h3 {
  color: #e2e8f0;
  font-size: 1rem;
  font-weight: 600;
  margin: 0 0 0.25rem 0;
}

.menu-info p {
  color: #64748b;
  font-size: 0.875rem;
  margin: 0;
}

/* 加载中 */
.loading {
  text-align: center;
  padding: 4rem 2rem;
  color: #94a3b8;
}

.spinner {
  width: 3rem;
  height: 3rem;
  border: 3px solid #1e2636;
  border-top-color: #38bdf8;
  border-radius: 50%;
  margin: 0 auto 1rem;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 空状态 */
.empty {
  text-align: center;
  padding: 4rem 2rem;
  color: #64748b;
  font-size: 0.875rem;
}
</style>
