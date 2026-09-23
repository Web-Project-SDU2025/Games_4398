# 自定义指令

此目录用于存放 Vue 3 自定义指令。

## 什么是自定义指令

自定义指令用于封装 DOM 操作，可以直接在模板中使用。

## 目录结构

```
directives/
├── index.ts          # 指令统一导出
├── permission.ts     # 权限控制指令
├── loading.ts        # 加载指令
└── README.md
```

## 示例

### permission.ts - 权限控制指令

```typescript
/**
 * 权限控制指令
 * 用于根据用户权限显示或隐藏元素
 */
import type { Directive } from 'vue'
import { useUserStore } from '@/store'

/**
 * 检查是否有权限
 * @param permissions 需要的权限列表
 * @returns 是否有权限
 */
function hasPermission(permissions: string[]): boolean {
  const userStore = useUserStore()
  const userPermissions = userStore.userInfo?.permissions || []
  
  // 检查是否包含任一权限
  return permissions.some(permission => {
    return userPermissions.includes(permission)
  })
}

/**
 * 权限指令
 * 使用方式：v-permission="['user:add', 'user:edit']"
 */
export const permission: Directive = {
  mounted(el, binding) {
    const { value } = binding
    
    // 如果没有传入权限列表，直接返回
    if (!value || !Array.isArray(value) || value.length === 0) {
      return
    }
    
    // 检查权限
    if (!hasPermission(value)) {
      // 没有权限，移除元素
      el.parentNode?.removeChild(el)
    }
  }
}
```

### loading.ts - 加载指令

```typescript
/**
 * 加载指令
 * 用于在元素上显示加载状态
 */
import type { Directive } from 'vue'

/**
 * 创建加载遮罩层
 * @returns 遮罩层元素
 */
function createLoadingMask(): HTMLDivElement {
  const mask = document.createElement('div')
  mask.className = 'loading-mask'
  mask.style.cssText = `
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(255, 255, 255, 0.9);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
  `
  
  // 创建加载图标
  const spinner = document.createElement('div')
  spinner.className = 'loading-spinner'
  spinner.style.cssText = `
    width: 40px;
    height: 40px;
    border: 4px solid #f3f3f3;
    border-top: 4px solid #3498db;
    border-radius: 50%;
    animation: spin 1s linear infinite;
  `
  
  mask.appendChild(spinner)
  
  // 添加动画样式
  if (!document.getElementById('loading-animation')) {
    const style = document.createElement('style')
    style.id = 'loading-animation'
    style.innerHTML = `
      @keyframes spin {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
      }
    `
    document.head.appendChild(style)
  }
  
  return mask
}

/**
 * 加载指令
 * 使用方式：v-loading="isLoading"
 */
export const loading: Directive = {
  mounted(el, binding) {
    // 设置元素为相对定位，方便遮罩层定位
    if (getComputedStyle(el).position === 'static') {
      el.style.position = 'relative'
    }
    
    // 根据绑定值决定是否显示加载
    if (binding.value) {
      const mask = createLoadingMask()
      el.appendChild(mask)
      el._loadingMask = mask
    }
  },
  
  updated(el, binding) {
    // 值改变时更新加载状态
    if (binding.value && !el._loadingMask) {
      // 需要显示但还没有遮罩层，创建一个
      const mask = createLoadingMask()
      el.appendChild(mask)
      el._loadingMask = mask
    } else if (!binding.value && el._loadingMask) {
      // 不需要显示但有遮罩层，移除它
      el.removeChild(el._loadingMask)
      el._loadingMask = null
    }
  },
  
  unmounted(el) {
    // 组件卸载时清理
    if (el._loadingMask) {
      el.removeChild(el._loadingMask)
      el._loadingMask = null
    }
  }
}
```

### index.ts - 统一导出

```typescript
/**
 * 自定义指令统一导出
 */
import type { App } from 'vue'
import { permission } from './permission'
import { loading } from './loading'

/**
 * 注册所有自定义指令
 * @param app Vue 应用实例
 */
export function registerDirectives(app: App) {
  app.directive('permission', permission)
  app.directive('loading', loading)
}

export { permission, loading }
```

## 在 main.ts 中注册

```typescript
import { createApp } from 'vue'
import App from './App.vue'
import { registerDirectives } from '@/directives'

const app = createApp(App)

// 注册所有自定义指令
registerDirectives(app)

app.mount('#app')
```

## 在组件中使用

```vue
<template>
  <div>
    <!-- 权限指令：只有拥有指定权限的用户才能看到 -->
    <button v-permission="['user:add']">添加用户</button>
    <button v-permission="['user:edit']">编辑用户</button>
    <button v-permission="['user:delete']">删除用户</button>
    
    <!-- 加载指令：显示加载状态 -->
    <div v-loading="isLoading" style="height: 200px;">
      <p>这里是内容</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const isLoading = ref(false)

// 模拟加载
function startLoading() {
  isLoading.value = true
  setTimeout(() => {
    isLoading.value = false
  }, 2000)
}
</script>
```

## 其他常用指令示例

### 防抖指令

```typescript
export const debounce: Directive = {
  mounted(el, binding) {
    let timer: number
    el.addEventListener('click', () => {
      if (timer) {
        clearTimeout(timer)
      }
      timer = setTimeout(() => {
        binding.value()
      }, 500)
    })
  }
}
```

### 复制指令

```typescript
export const copy: Directive = {
  mounted(el, binding) {
    el.addEventListener('click', () => {
      const text = binding.value
      navigator.clipboard.writeText(text).then(() => {
        alert('复制成功')
      })
    })
  }
}
```

### 图片懒加载指令

```typescript
export const lazyload: Directive = {
  mounted(el, binding) {
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        el.src = binding.value
        observer.disconnect()
      }
    })
    observer.observe(el)
  }
}
```
