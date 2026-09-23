# 路由配置

此目录用于存放 Vue Router 路由配置。

## 目录结构

```
router/
├── index.ts              # 路由主文件
├── routes.ts            # 路由定义
├── guards.ts            # 路由守卫
└── README.md
```

## 安装依赖

```bash
npm install vue-router@4
```

## 示例

### index.ts - 路由主文件

```typescript
/**
 * 路由配置
 */
import { createRouter, createWebHistory } from 'vue-router'
import type { Router } from 'vue-router'
import { routes } from './routes'
import { setupRouterGuards } from './guards'

/**
 * 创建路由实例
 */
const router: Router = createRouter({
  // 使用 HTML5 History 模式
  history: createWebHistory(import.meta.env.BASE_URL),
  
  // 路由配置
  routes,
  
  // 滚动行为
  scrollBehavior(to, from, savedPosition) {
    // 如果有保存的位置，恢复到该位置
    if (savedPosition) {
      return savedPosition
    }
    
    // 如果有锚点，滚动到锚点
    if (to.hash) {
      return {
        el: to.hash,
        behavior: 'smooth'
      }
    }
    
    // 默认滚动到顶部
    return { top: 0 }
  }
})

// 设置路由守卫
setupRouterGuards(router)

export default router
```

### routes.ts - 路由定义

```typescript
/**
 * 路由定义
 */
import type { RouteRecordRaw } from 'vue-router'

/**
 * 路由配置数组
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: {
      title: '首页',
      requiresAuth: true
    }
  },
  
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: {
      title: '登录',
      requiresAuth: false
    }
  },
  
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: {
      title: '注册',
      requiresAuth: false
    }
  },
  
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/Dashboard.vue'),
    meta: {
      title: '仪表板',
      requiresAuth: true
    }
  },
  
  {
    path: '/system',
    name: 'System',
    redirect: '/system/user',
    meta: {
      title: '系统管理',
      requiresAuth: true
    },
    children: [
      {
        path: 'user',
        name: 'UserManagement',
        component: () => import('@/views/system/UserManagement.vue'),
        meta: {
          title: '用户管理',
          requiresAuth: true,
          permissions: ['user:view']
        }
      },
      {
        path: 'role',
        name: 'RoleManagement',
        component: () => import('@/views/system/RoleManagement.vue'),
        meta: {
          title: '角色管理',
          requiresAuth: true,
          permissions: ['role:view']
        }
      },
      {
        path: 'menu',
        name: 'MenuManagement',
        component: () => import('@/views/system/MenuManagement.vue'),
        meta: {
          title: '菜单管理',
          requiresAuth: true,
          permissions: ['menu:view']
        }
      },
      {
        path: 'dict',
        name: 'DictManagement',
        component: () => import('@/views/system/DictManagement.vue'),
        meta: {
          title: '字典管理',
          requiresAuth: true,
          permissions: ['dict:view']
        }
      }
    ]
  },
  
  {
    path: '/game',
    name: 'Game',
    redirect: '/game/list',
    meta: {
      title: '游戏管理',
      requiresAuth: true
    },
    children: [
      {
        path: 'list',
        name: 'GameList',
        component: () => import('@/views/game/GameList.vue'),
        meta: {
          title: '游戏列表',
          requiresAuth: true,
          permissions: ['game:view']
        }
      },
      {
        path: 'detail/:id',
        name: 'GameDetail',
        component: () => import('@/views/game/GameDetail.vue'),
        meta: {
          title: '游戏详情',
          requiresAuth: true,
          permissions: ['game:view']
        }
      }
    ]
  },
  
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: {
      title: '页面不存在',
      requiresAuth: false
    }
  },
  
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
]
```

### guards.ts - 路由守卫

```typescript
/**
 * 路由守卫
 */
import type { Router } from 'vue-router'
import { useUserStore } from '@/store'

/**
 * 设置路由守卫
 * @param router 路由实例
 */
export function setupRouterGuards(router: Router): void {
  // 全局前置守卫
  router.beforeEach((to, from, next) => {
    // 获取用户 store
    const userStore = useUserStore()
    
    // 设置页面标题
    if (to.meta.title) {
      document.title = `${to.meta.title} - 4398游戏管理系统`
    } else {
      document.title = '4398游戏管理系统'
    }
    
    // 检查是否需要登录
    if (to.meta.requiresAuth) {
      // 检查是否已登录
      if (!userStore.isLoggedIn) {
        // 未登录，跳转到登录页
        next({
          path: '/login',
          query: {
            // 保存原本要访问的路径
            redirect: to.fullPath
          }
        })
        return
      }
      
      // 检查权限
      if (to.meta.permissions && Array.isArray(to.meta.permissions)) {
        const hasPermission = to.meta.permissions.some((permission: string) => {
          return userStore.hasPermission(permission)
        })
        
        if (!hasPermission) {
          // 没有权限，跳转到首页
          alert('您没有访问该页面的权限')
          next('/')
          return
        }
      }
    }
    
    // 如果已登录，不允许访问登录页
    if (to.path === '/login' && userStore.isLoggedIn) {
      next('/')
      return
    }
    
    // 继续导航
    next()
  })
  
  // 全局后置守卫
  router.afterEach((to, from) => {
    // 记录页面访问日志
    console.log(`从 ${from.path} 导航到 ${to.path}`)
  })
  
  // 全局解析守卫
  router.beforeResolve((to, from, next) => {
    // 在导航被确认之前，所有组件内守卫和异步路由组件被解析之后调用
    next()
  })
}
```

## 路由元信息类型定义

```typescript
// src/types/router.d.ts

import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /**
     * 页面标题
     */
    title?: string
    
    /**
     * 是否需要登录
     */
    requiresAuth?: boolean
    
    /**
     * 需要的权限
     */
    permissions?: string[]
    
    /**
     * 是否缓存组件
     */
    keepAlive?: boolean
    
    /**
     * 图标
     */
    icon?: string
    
    /**
     * 是否在菜单中显示
     */
    hidden?: boolean
  }
}
```

## 在 main.ts 中使用

```typescript
import { createApp } from 'vue'
import App from './App.vue'
import router from '@/router'

const app = createApp(App)

// 使用路由
app.use(router)

app.mount('#app')
```

## 在组件中使用路由

### 选项式 API

```vue
<script>
export default {
  methods: {
    goToUserList() {
      // 编程式导航
      this.$router.push('/system/user')
    },
    
    goBack() {
      // 后退
      this.$router.back()
    }
  },
  
  computed: {
    currentRoute() {
      // 获取当前路由
      return this.$route
    }
  }
}
</script>
```

### 组合式 API

```vue
<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'

// 获取路由实例
const router = useRouter()
const route = useRoute()

/**
 * 跳转到用户列表
 */
function goToUserList() {
  router.push('/system/user')
}

/**
 * 带参数跳转
 */
function goToUserDetail(userId: number) {
  router.push({
    name: 'UserDetail',
    params: { id: userId }
  })
}

/**
 * 带查询参数跳转
 */
function goToSearch(keyword: string) {
  router.push({
    path: '/search',
    query: { keyword }
  })
}

/**
 * 获取路由参数
 */
const userId = route.params.id

/**
 * 获取查询参数
 */
const keyword = route.query.keyword
</script>
```

## 路由导航方法

```typescript
// 字符串路径
router.push('/users')

// 对象路径
router.push({ path: '/users' })

// 命名路由
router.push({ name: 'User', params: { id: 123 } })

// 带查询参数
router.push({ path: '/users', query: { page: 1 } })

// 替换当前路由（不会留下历史记录）
router.replace('/users')

// 后退
router.back()

// 前进
router.forward()

// 跳转到历史中的某个位置
router.go(-2) // 后退两步
router.go(1)  // 前进一步
```

## 路由懒加载

```typescript
// 使用动态导入实现懒加载
const UserManagement = () => import('@/views/system/UserManagement.vue')

// 在路由配置中使用
{
  path: '/system/user',
  component: UserManagement
}

// 或者直接使用箭头函数
{
  path: '/system/user',
  component: () => import('@/views/system/UserManagement.vue')
}
```

## 命名视图

```typescript
// 路由配置
{
  path: '/layout',
  components: {
    default: MainContent,
    sidebar: Sidebar,
    footer: Footer
  }
}
```

```vue
<!-- 在组件中使用 -->
<template>
  <div>
    <router-view name="sidebar"></router-view>
    <router-view></router-view>
    <router-view name="footer"></router-view>
  </div>
</template>
```

## 动态路由

```typescript
// 添加路由
router.addRoute({
  path: '/new-route',
  component: NewComponent
})

// 删除路由
router.removeRoute('RouteName')

// 获取所有路由
const routes = router.getRoutes()
```

## 注意事项

1. **懒加载**：使用动态导入优化首屏加载
2. **权限控制**：在路由守卫中检查权限
3. **参数验证**：验证路由参数的有效性
4. **404 处理**：设置通配符路由处理 404
5. **滚动恢复**：配置 scrollBehavior 优化用户体验
