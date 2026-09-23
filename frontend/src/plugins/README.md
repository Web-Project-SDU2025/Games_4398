# 插件

此目录用于存放 Vue 3 插件。

## 什么是插件

插件是一个包含 `install()` 方法的对象，用于为 Vue 应用添加全局功能。

## 目录结构

```
plugins/
├── index.ts          # 插件统一导出
└── README.md
```

## 插件示例

### 全局提示插件

```typescript
/**
 * 全局提示插件
 */
import type { App } from 'vue'

/**
 * 提示配置接口
 */
interface MessageOptions {
  message: string
  type?: 'success' | 'error' | 'warning' | 'info'
  duration?: number
}

/**
 * 显示提示消息
 * @param options 提示配置
 */
function showMessage(options: MessageOptions) {
  const { message, type = 'info', duration = 3000 } = options
  
  // 创建提示元素
  const messageElement = document.createElement('div')
  messageElement.className = `message message-${type}`
  messageElement.textContent = message
  messageElement.style.cssText = `
    position: fixed;
    top: 20px;
    left: 50%;
    transform: translateX(-50%);
    padding: 12px 20px;
    background: #fff;
    border-radius: 4px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
    z-index: 9999;
    animation: slideDown 0.3s ease;
  `
  
  // 根据类型设置颜色
  const colors = {
    success: '#67C23A',
    error: '#F56C6C',
    warning: '#E6A23C',
    info: '#909399'
  }
  messageElement.style.borderLeft = `4px solid ${colors[type]}`
  
  // 添加到页面
  document.body.appendChild(messageElement)
  
  // 自动关闭
  setTimeout(() => {
    messageElement.style.animation = 'slideUp 0.3s ease'
    setTimeout(() => {
      document.body.removeChild(messageElement)
    }, 300)
  }, duration)
}

/**
 * 全局提示插件
 */
export default {
  install(app: App) {
    // 添加全局方法
    app.config.globalProperties.$message = {
      success: (message: string) => showMessage({ message, type: 'success' }),
      error: (message: string) => showMessage({ message, type: 'error' }),
      warning: (message: string) => showMessage({ message, type: 'warning' }),
      info: (message: string) => showMessage({ message, type: 'info' })
    }
    
    // 添加动画样式
    if (!document.getElementById('message-animation')) {
      const style = document.createElement('style')
      style.id = 'message-animation'
      style.innerHTML = `
        @keyframes slideDown {
          from {
            opacity: 0;
            transform: translateX(-50%) translateY(-20px);
          }
          to {
            opacity: 1;
            transform: translateX(-50%) translateY(0);
          }
        }
        @keyframes slideUp {
          from {
            opacity: 1;
            transform: translateX(-50%) translateY(0);
          }
          to {
            opacity: 0;
            transform: translateX(-50%) translateY(-20px);
          }
        }
      `
      document.head.appendChild(style)
    }
  }
}
```

### 全局确认框插件

```typescript
/**
 * 全局确认框插件
 */
import type { App } from 'vue'

/**
 * 确认框配置接口
 */
interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
}

/**
 * 显示确认框
 * @param options 确认框配置
 * @returns Promise，用户确认返回 true，取消返回 false
 */
function showConfirm(options: ConfirmOptions): Promise<boolean> {
  const {
    title = '提示',
    message,
    confirmText = '确定',
    cancelText = '取消'
  } = options
  
  return new Promise((resolve) => {
    // 创建遮罩层
    const mask = document.createElement('div')
    mask.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 9999;
    `
    
    // 创建对话框
    const dialog = document.createElement('div')
    dialog.style.cssText = `
      background: #fff;
      border-radius: 8px;
      padding: 20px;
      min-width: 300px;
      max-width: 500px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    `
    
    // 标题
    const titleElement = document.createElement('h3')
    titleElement.textContent = title
    titleElement.style.cssText = `
      margin: 0 0 10px 0;
      font-size: 18px;
      color: #333;
    `
    
    // 消息
    const messageElement = document.createElement('p')
    messageElement.textContent = message
    messageElement.style.cssText = `
      margin: 0 0 20px 0;
      color: #666;
      line-height: 1.5;
    `
    
    // 按钮容器
    const buttonContainer = document.createElement('div')
    buttonContainer.style.cssText = `
      display: flex;
      justify-content: flex-end;
      gap: 10px;
    `
    
    // 取消按钮
    const cancelButton = document.createElement('button')
    cancelButton.textContent = cancelText
    cancelButton.style.cssText = `
      padding: 8px 16px;
      border: 1px solid #ddd;
      border-radius: 4px;
      background: #fff;
      color: #666;
      cursor: pointer;
    `
    cancelButton.onclick = () => {
      document.body.removeChild(mask)
      resolve(false)
    }
    
    // 确定按钮
    const confirmButton = document.createElement('button')
    confirmButton.textContent = confirmText
    confirmButton.style.cssText = `
      padding: 8px 16px;
      border: none;
      border-radius: 4px;
      background: #409EFF;
      color: #fff;
      cursor: pointer;
    `
    confirmButton.onclick = () => {
      document.body.removeChild(mask)
      resolve(true)
    }
    
    // 组装元素
    buttonContainer.appendChild(cancelButton)
    buttonContainer.appendChild(confirmButton)
    dialog.appendChild(titleElement)
    dialog.appendChild(messageElement)
    dialog.appendChild(buttonContainer)
    mask.appendChild(dialog)
    document.body.appendChild(mask)
  })
}

/**
 * 全局确认框插件
 */
export default {
  install(app: App) {
    app.config.globalProperties.$confirm = showConfirm
  }
}
```

### index.ts - 统一导出

```typescript
/**
 * 插件统一导出
 */
import type { App } from 'vue'
import messagePlugin from './message'
import confirmPlugin from './confirm'

/**
 * 注册所有插件
 * @param app Vue 应用实例
 */
export function registerPlugins(app: App) {
  app.use(messagePlugin)
  app.use(confirmPlugin)
}
```

## 在 main.ts 中使用

```typescript
import { createApp } from 'vue'
import App from './App.vue'
import { registerPlugins } from '@/plugins'

const app = createApp(App)

// 注册所有插件
registerPlugins(app)

app.mount('#app')
```

## 在组件中使用

### 选项式 API

```vue
<script>
export default {
  methods: {
    handleSuccess() {
      // 使用提示插件
      this.$message.success('操作成功')
    },
    
    async handleDelete() {
      // 使用确认框插件
      const confirmed = await this.$confirm({
        title: '删除确认',
        message: '确定要删除这条记录吗？',
        confirmText: '删除',
        cancelText: '取消'
      })
      
      if (confirmed) {
        // 用户确认删除
        this.$message.success('删除成功')
      }
    }
  }
}
</script>
```

### 组合式 API

```vue
<script setup lang="ts">
import { getCurrentInstance } from 'vue'

const instance = getCurrentInstance()
const $message = instance?.proxy?.$message
const $confirm = instance?.proxy?.$confirm

function handleSuccess() {
  $message?.success('操作成功')
}

async function handleDelete() {
  const confirmed = await $confirm?.({
    message: '确定要删除这条记录吗？'
  })
  
  if (confirmed) {
    $message?.success('删除成功')
  }
}
</script>
```

## TypeScript 类型声明

为了获得更好的 TypeScript 支持，需要声明全局属性类型：

```typescript
// src/types/global.d.ts
import 'vue'

declare module 'vue' {
  interface ComponentCustomProperties {
    $message: {
      success: (message: string) => void
      error: (message: string) => void
      warning: (message: string) => void
      info: (message: string) => void
    }
    $confirm: (options: {
      title?: string
      message: string
      confirmText?: string
      cancelText?: string
    }) => Promise<boolean>
  }
}
```
