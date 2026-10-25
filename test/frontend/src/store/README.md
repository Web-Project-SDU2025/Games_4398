# 状态管理 (Pinia)

此目录用于存放 Pinia 状态管理相关代码。

## 什么是 Pinia

Pinia 是 Vue 3 官方推荐的状态管理库，替代 Vuex，提供更好的 TypeScript 支持。

## 目录结构

```
store/
├── index.ts              # Store 主文件
├── modules/              # Store 模块
│   ├── user.ts          # 用户 Store
│   ├── app.ts           # 应用 Store
│   └── game.ts          # 游戏 Store
└── README.md
```

## 安装依赖

```bash
npm install pinia
```

## 示例

### index.ts - Store 主文件

```typescript
/**
 * Pinia Store 配置
 */
import { createPinia } from 'pinia'
import type { App } from 'vue'

/**
 * 创建 Pinia 实例
 */
const pinia = createPinia()

/**
 * 注册 Pinia
 * @param app Vue 应用实例
 */
export function setupStore(app: App): void {
  app.use(pinia)
}

// 导出所有 Store
export * from './modules/user'
export * from './modules/app'
export * from './modules/game'

export default pinia
```

### modules/user.ts - 用户 Store

```typescript
/**
 * 用户 Store
 * 管理用户登录状态、用户信息和权限
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'

/**
 * 用户信息接口
 */
interface UserInfo {
  id: number
  username: string
  email: string
  phone: string
  avatar: string
  roleName: string
  permissions: string[]
}

/**
 * 用户 Store
 */
export const useUserStore = defineStore('user', () => {
  // 用户 Token
  const token: Ref<string | null> = ref(localStorage.getItem('token'))
  
  // 用户信息
  const userInfo: Ref<UserInfo | null> = ref(null)
  
  /**
   * 是否已登录
   */
  const isLoggedIn: ComputedRef<boolean> = computed(() => {
    return token.value !== null && token.value !== ''
  })
  
  /**
   * 用户名
   */
  const username: ComputedRef<string> = computed(() => {
    return userInfo.value?.username || '未登录'
  })
  
  /**
   * 用户权限列表
   */
  const permissions: ComputedRef<string[]> = computed(() => {
    return userInfo.value?.permissions || []
  })
  
  /**
   * 登录
   * @param newToken 新的 Token
   * @param newUserInfo 新的用户信息
   */
  function login(newToken: string, newUserInfo: UserInfo): void {
    // 保存 Token
    token.value = newToken
    localStorage.setItem('token', newToken)
    
    // 保存用户信息
    userInfo.value = newUserInfo
    localStorage.setItem('userInfo', JSON.stringify(newUserInfo))
  }
  
  /**
   * 登出
   */
  function logout(): void {
    // 清空 Token
    token.value = null
    localStorage.removeItem('token')
    
    // 清空用户信息
    userInfo.value = null
    localStorage.removeItem('userInfo')
  }
  
  /**
   * 更新用户信息
   * @param newUserInfo 新的用户信息
   */
  function updateUserInfo(newUserInfo: UserInfo): void {
    userInfo.value = newUserInfo
    localStorage.setItem('userInfo', JSON.stringify(newUserInfo))
  }
  
  /**
   * 检查是否有指定权限
   * @param permission 权限标识
   * @returns 是否有权限
   */
  function hasPermission(permission: string): boolean {
    return permissions.value.includes(permission)
  }
  
  /**
   * 检查是否有任一权限
   * @param permissionList 权限列表
   * @returns 是否有权限
   */
  function hasAnyPermission(permissionList: string[]): boolean {
    return permissionList.some(permission => hasPermission(permission))
  }
  
  /**
   * 检查是否有所有权限
   * @param permissionList 权限列表
   * @returns 是否有权限
   */
  function hasAllPermissions(permissionList: string[]): boolean {
    return permissionList.every(permission => hasPermission(permission))
  }
  
  /**
   * 从本地存储恢复用户信息
   */
  function restoreUserInfo(): void {
    const savedUserInfo = localStorage.getItem('userInfo')
    if (savedUserInfo) {
      try {
        userInfo.value = JSON.parse(savedUserInfo)
      } catch (error) {
        console.error('恢复用户信息失败', error)
      }
    }
  }
  
  // 初始化时恢复用户信息
  restoreUserInfo()
  
  return {
    token,
    userInfo,
    isLoggedIn,
    username,
    permissions,
    login,
    logout,
    updateUserInfo,
    hasPermission,
    hasAnyPermission,
    hasAllPermissions
  }
})
```

### modules/app.ts - 应用 Store

```typescript
/**
 * 应用 Store
 * 管理应用级别的状态（主题、语言、布局等）
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'

/**
 * 主题类型
 */
type Theme = 'light' | 'dark'

/**
 * 语言类型
 */
type Language = 'zh-CN' | 'en-US'

/**
 * 应用 Store
 */
export const useAppStore = defineStore('app', () => {
  // 主题
  const theme: Ref<Theme> = ref(
    (localStorage.getItem('theme') as Theme) || 'light'
  )
  
  // 语言
  const language: Ref<Language> = ref(
    (localStorage.getItem('language') as Language) || 'zh-CN'
  )
  
  // 侧边栏是否折叠
  const sidebarCollapsed: Ref<boolean> = ref(
    localStorage.getItem('sidebarCollapsed') === 'true'
  )
  
  // 加载状态
  const loading: Ref<boolean> = ref(false)
  
  /**
   * 是否暗色主题
   */
  const isDarkTheme: ComputedRef<boolean> = computed(() => {
    return theme.value === 'dark'
  })
  
  /**
   * 是否中文
   */
  const isChineseLanguage: ComputedRef<boolean> = computed(() => {
    return language.value === 'zh-CN'
  })
  
  /**
   * 切换主题
   */
  function toggleTheme(): void {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    localStorage.setItem('theme', theme.value)
    
    // 应用主题到 body
    document.body.setAttribute('data-theme', theme.value)
  }
  
  /**
   * 设置主题
   * @param newTheme 新主题
   */
  function setTheme(newTheme: Theme): void {
    theme.value = newTheme
    localStorage.setItem('theme', newTheme)
    document.body.setAttribute('data-theme', newTheme)
  }
  
  /**
   * 切换语言
   */
  function toggleLanguage(): void {
    language.value = language.value === 'zh-CN' ? 'en-US' : 'zh-CN'
    localStorage.setItem('language', language.value)
  }
  
  /**
   * 设置语言
   * @param newLanguage 新语言
   */
  function setLanguage(newLanguage: Language): void {
    language.value = newLanguage
    localStorage.setItem('language', newLanguage)
  }
  
  /**
   * 切换侧边栏折叠状态
   */
  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem('sidebarCollapsed', String(sidebarCollapsed.value))
  }
  
  /**
   * 设置侧边栏折叠状态
   * @param collapsed 是否折叠
   */
  function setSidebarCollapsed(collapsed: boolean): void {
    sidebarCollapsed.value = collapsed
    localStorage.setItem('sidebarCollapsed', String(collapsed))
  }
  
  /**
   * 显示加载
   */
  function showLoading(): void {
    loading.value = true
  }
  
  /**
   * 隐藏加载
   */
  function hideLoading(): void {
    loading.value = false
  }
  
  // 初始化主题
  document.body.setAttribute('data-theme', theme.value)
  
  return {
    theme,
    language,
    sidebarCollapsed,
    loading,
    isDarkTheme,
    isChineseLanguage,
    toggleTheme,
    setTheme,
    toggleLanguage,
    setLanguage,
    toggleSidebar,
    setSidebarCollapsed,
    showLoading,
    hideLoading
  }
})
```

### modules/game.ts - 游戏 Store

```typescript
/**
 * 游戏 Store
 * 管理游戏相关的状态
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'

/**
 * 游戏信息接口
 */
interface Game {
  id: number
  name: string
  description: string
  coverImage: string
  category: string
  tags: string[]
  status: number
}

/**
 * 游戏 Store
 */
export const useGameStore = defineStore('game', () => {
  // 游戏列表
  const gameList: Ref<Game[]> = ref([])
  
  // 当前选中的游戏
  const currentGame: Ref<Game | null> = ref(null)
  
  // 游戏分类列表
  const categoryList: Ref<string[]> = ref([
    '动作',
    '冒险',
    '角色扮演',
    '策略',
    '休闲'
  ])
  
  /**
   * 游戏总数
   */
  const totalCount: ComputedRef<number> = computed(() => {
    return gameList.value.length
  })
  
  /**
   * 在线游戏列表
   */
  const onlineGames: ComputedRef<Game[]> = computed(() => {
    return gameList.value.filter(game => game.status === 1)
  })
  
  /**
   * 下架游戏列表
   */
  const offlineGames: ComputedRef<Game[]> = computed(() => {
    return gameList.value.filter(game => game.status === 0)
  })
  
  /**
   * 设置游戏列表
   * @param games 游戏列表
   */
  function setGameList(games: Game[]): void {
    gameList.value = games
  }
  
  /**
   * 添加游戏
   * @param game 游戏信息
   */
  function addGame(game: Game): void {
    gameList.value.push(game)
  }
  
  /**
   * 更新游戏
   * @param gameId 游戏 ID
   * @param updatedGame 更新的游戏信息
   */
  function updateGame(gameId: number, updatedGame: Partial<Game>): void {
    const index = gameList.value.findIndex(game => game.id === gameId)
    if (index !== -1) {
      gameList.value[index] = {
        ...gameList.value[index],
        ...updatedGame
      }
    }
  }
  
  /**
   * 删除游戏
   * @param gameId 游戏 ID
   */
  function removeGame(gameId: number): void {
    const index = gameList.value.findIndex(game => game.id === gameId)
    if (index !== -1) {
      gameList.value.splice(index, 1)
    }
  }
  
  /**
   * 根据 ID 获取游戏
   * @param gameId 游戏 ID
   * @returns 游戏信息，不存在返回 null
   */
  function getGameById(gameId: number): Game | null {
    return gameList.value.find(game => game.id === gameId) || null
  }
  
  /**
   * 根据分类筛选游戏
   * @param category 分类
   * @returns 游戏列表
   */
  function getGamesByCategory(category: string): Game[] {
    return gameList.value.filter(game => game.category === category)
  }
  
  /**
   * 设置当前游戏
   * @param game 游戏信息
   */
  function setCurrentGame(game: Game | null): void {
    currentGame.value = game
  }
  
  /**
   * 清空游戏列表
   */
  function clearGameList(): void {
    gameList.value = []
  }
  
  return {
    gameList,
    currentGame,
    categoryList,
    totalCount,
    onlineGames,
    offlineGames,
    setGameList,
    addGame,
    updateGame,
    removeGame,
    getGameById,
    getGamesByCategory,
    setCurrentGame,
    clearGameList
  }
})
```

## 在 main.ts 中注册

```typescript
import { createApp } from 'vue'
import App from './App.vue'
import { setupStore } from '@/store'

const app = createApp(App)

// 注册 Pinia
setupStore(app)

app.mount('#app')
```

## 在组件中使用 Store

### 选项式 API

```vue
<script>
import { useUserStore } from '@/store'

export default {
  setup() {
    const userStore = useUserStore()
    return { userStore }
  },
  
  computed: {
    username() {
      return this.userStore.username
    }
  },
  
  methods: {
    handleLogout() {
      this.userStore.logout()
    }
  }
}
</script>
```

### 组合式 API

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore, useAppStore } from '@/store'

// 获取 Store 实例
const userStore = useUserStore()
const appStore = useAppStore()

// 直接访问状态
const username = computed(() => userStore.username)
const isLoggedIn = computed(() => userStore.isLoggedIn)

// 调用 actions
function handleLogout() {
  userStore.logout()
}

function toggleTheme() {
  appStore.toggleTheme()
}
</script>

<template>
  <div>
    <p>用户名：{{ username }}</p>
    <p>登录状态：{{ isLoggedIn ? '已登录' : '未登录' }}</p>
    <button @click="handleLogout">登出</button>
    <button @click="toggleTheme">切换主题</button>
  </div>
</template>
```

## Store 解构

```typescript
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/store'

const userStore = useUserStore()

// 解构 state 和 getters（保持响应式）
const { username, isLoggedIn } = storeToRefs(userStore)

// 解构 actions（不需要 storeToRefs）
const { login, logout } = userStore
```

## Store 持久化

### 方案一：手动持久化（推荐）

在 Store 中手动读写 localStorage，如上面的示例。

### 方案二：使用插件

```bash
npm install pinia-plugin-persistedstate
```

```typescript
// store/index.ts
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

export default pinia
```

```typescript
// 在 Store 中启用持久化
export const useUserStore = defineStore('user', () => {
  // ... store 定义
}, {
  persist: true  // 启用持久化
})
```

## Store 之间的依赖

```typescript
export const useUserStore = defineStore('user', () => {
  // 使用其他 Store
  const appStore = useAppStore()
  
  function doSomething() {
    // 调用其他 Store 的方法
    appStore.showLoading()
  }
  
  return { doSomething }
})
```

## 注意事项

1. **响应式解构**：解构 state 和 getters 需要使用 storeToRefs
2. **避免直接修改 state**：通过 actions 修改状态
3. **模块化**：按功能拆分 Store
4. **TypeScript 支持**：充分利用 TypeScript 类型推断
5. **持久化选择**：根据需求选择持久化方案
