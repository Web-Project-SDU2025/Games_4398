# 静态资源

此目录用于存放经过构建的静态资源文件。

## 目录结构

```
assets/
├── images/          # 图片资源
│   ├── logo.png
│   ├── avatar.png
│   └── icons/
├── styles/          # 样式文件
│   ├── global.css   # 全局样式
│   ├── reset.css    # 样式重置
│   └── variables.css # CSS 变量
└── fonts/           # 字体文件
    └── custom-font.ttf
```

## 与 public 的区别

- **assets/**：会被 Vite 处理（压缩、哈希命名）
- **public/**：直接复制到输出目录，不经过处理

## 使用方式

### 在组件中引用

```vue
<template>
  <div>
    <!-- 引用图片 -->
    <img :src="logoUrl" alt="Logo" />
  </div>
</template>

<script setup lang="ts">
import logoUrl from '@/assets/images/logo.png'
</script>
```

### 在样式中引用

```css
.header {
  background-image: url('@/assets/images/background.jpg');
}

.icon {
  background-image: url('@/assets/images/icons/user.svg');
}
```

## 全局样式示例

### reset.css - 样式重置

```css
/**
 * 样式重置
 * 统一不同浏览器的默认样式
 */

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body {
  width: 100%;
  height: 100%;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 
    'Helvetica Neue', Arial, sans-serif;
  font-size: 14px;
  line-height: 1.5;
  color: #333;
  background-color: #f5f5f5;
}

a {
  color: inherit;
  text-decoration: none;
}

ul,
ol {
  list-style: none;
}

button {
  border: none;
  background: none;
  cursor: pointer;
  font-family: inherit;
}

input,
textarea,
select {
  font-family: inherit;
  font-size: inherit;
}

img {
  max-width: 100%;
  height: auto;
  vertical-align: middle;
}
```

### variables.css - CSS 变量

```css
/**
 * CSS 变量定义
 * 统一管理颜色、间距等设计规范
 */

:root {
  /* 主题色 */
  --color-primary: #409EFF;
  --color-success: #67C23A;
  --color-warning: #E6A23C;
  --color-danger: #F56C6C;
  --color-info: #909399;

  /* 文字颜色 */
  --text-primary: #303133;
  --text-regular: #606266;
  --text-secondary: #909399;
  --text-placeholder: #C0C4CC;

  /* 边框颜色 */
  --border-base: #DCDFE6;
  --border-light: #E4E7ED;
  --border-lighter: #EBEEF5;
  --border-extra-light: #F2F6FC;

  /* 背景色 */
  --background-base: #F5F7FA;
  --background-light: #FAFAFA;
  --background-white: #FFFFFF;

  /* 间距 */
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 32px;

  /* 圆角 */
  --border-radius-sm: 2px;
  --border-radius-md: 4px;
  --border-radius-lg: 8px;

  /* 阴影 */
  --box-shadow-light: 0 2px 4px rgba(0, 0, 0, 0.12);
  --box-shadow-base: 0 2px 8px rgba(0, 0, 0, 0.15);
  --box-shadow-dark: 0 4px 12px rgba(0, 0, 0, 0.18);

  /* 字体大小 */
  --font-size-xs: 12px;
  --font-size-sm: 13px;
  --font-size-base: 14px;
  --font-size-lg: 16px;
  --font-size-xl: 18px;

  /* 行高 */
  --line-height-base: 1.5;
  --line-height-tight: 1.25;
  --line-height-loose: 1.75;

  /* 过渡时间 */
  --transition-fast: 0.15s;
  --transition-base: 0.3s;
  --transition-slow: 0.5s;
}
```

### global.css - 全局样式

```css
/**
 * 全局样式
 * 定义通用的样式规则
 */

/* 容器 */
.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--spacing-md);
}

/* 按钮 */
.btn {
  display: inline-block;
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-base);
  text-align: center;
  cursor: pointer;
  transition: all var(--transition-base);
}

.btn-primary {
  background: var(--color-primary);
  color: #fff;
}

.btn-primary:hover {
  opacity: 0.8;
}

.btn-success {
  background: var(--color-success);
  color: #fff;
}

.btn-danger {
  background: var(--color-danger);
  color: #fff;
}

/* 卡片 */
.card {
  background: var(--background-white);
  border-radius: var(--border-radius-lg);
  box-shadow: var(--box-shadow-light);
  padding: var(--spacing-lg);
}

/* 表单 */
.form-item {
  margin-bottom: var(--spacing-md);
}

.form-label {
  display: block;
  margin-bottom: var(--spacing-xs);
  font-size: var(--font-size-sm);
  color: var(--text-regular);
}

.form-input {
  width: 100%;
  padding: var(--spacing-sm);
  border: 1px solid var(--border-base);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-base);
  transition: border-color var(--transition-base);
}

.form-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

/* 文本对齐 */
.text-left {
  text-align: left;
}

.text-center {
  text-align: center;
}

.text-right {
  text-align: right;
}

/* Flex 布局 */
.flex {
  display: flex;
}

.flex-center {
  display: flex;
  align-items: center;
  justify-content: center;
}

.flex-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 间距工具类 */
.mt-xs { margin-top: var(--spacing-xs); }
.mt-sm { margin-top: var(--spacing-sm); }
.mt-md { margin-top: var(--spacing-md); }
.mt-lg { margin-top: var(--spacing-lg); }

.mb-xs { margin-bottom: var(--spacing-xs); }
.mb-sm { margin-bottom: var(--spacing-sm); }
.mb-md { margin-bottom: var(--spacing-md); }
.mb-lg { margin-bottom: var(--spacing-lg); }

.pt-xs { padding-top: var(--spacing-xs); }
.pt-sm { padding-top: var(--spacing-sm); }
.pt-md { padding-top: var(--spacing-md); }
.pt-lg { padding-top: var(--spacing-lg); }

.pb-xs { padding-bottom: var(--spacing-xs); }
.pb-sm { padding-bottom: var(--spacing-sm); }
.pb-md { padding-bottom: var(--spacing-md); }
.pb-lg { padding-bottom: var(--spacing-lg); }
```

## 在 main.ts 中引入全局样式

```typescript
import { createApp } from 'vue'
import App from './App.vue'

// 引入全局样式
import '@/assets/styles/reset.css'
import '@/assets/styles/variables.css'
import '@/assets/styles/global.css'

const app = createApp(App)
app.mount('#app')
```

## 图片优化建议

1. **压缩图片**：使用工具压缩后再使用
2. **选择合适格式**：
   - JPEG：照片、复杂图像
   - PNG：图标、透明背景
   - SVG：矢量图标、Logo
   - WebP：现代浏览器优先使用
3. **提供多种尺寸**：响应式图片
4. **使用懒加载**：首屏外的图片延迟加载

## 字体优化建议

1. **仅加载需要的字体**：减小文件大小
2. **字体子集化**：只包含使用的字符
3. **使用 Web 字体**：优先使用系统字体
4. **字体预加载**：关键字体提前加载
