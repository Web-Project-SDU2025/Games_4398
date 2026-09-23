# 常量定义

此目录用于存放项目中使用的常量。

## 目录结构

```
constants/
├── index.ts          # 通用常量
├── api.ts            # API 相关常量
├── status.ts         # 状态码常量
├── regex.ts          # 正则表达式常量
└── README.md
```

## 示例

### index.ts - 通用常量

```typescript
/**
 * 通用常量定义
 */

/**
 * 本地存储键名
 */
export const STORAGE_KEYS = {
  TOKEN: 'token',
  USER_INFO: 'userInfo',
  THEME: 'theme',
  LANGUAGE: 'language'
} as const

/**
 * 页面大小选项
 */
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const

/**
 * 默认页面大小
 */
export const DEFAULT_PAGE_SIZE = 10

/**
 * 日期格式
 */
export const DATE_FORMATS = {
  DATE: 'YYYY-MM-DD',
  TIME: 'HH:mm:ss',
  DATETIME: 'YYYY-MM-DD HH:mm:ss',
  MONTH: 'YYYY-MM'
} as const

/**
 * 文件上传限制
 */
export const UPLOAD_LIMITS = {
  MAX_SIZE: 10 * 1024 * 1024,  // 10MB
  ALLOWED_TYPES: ['image/jpeg', 'image/png', 'image/gif'],
  ALLOWED_EXTENSIONS: ['.jpg', '.jpeg', '.png', '.gif']
} as const

/**
 * 表格操作列宽度
 */
export const TABLE_ACTION_WIDTH = 200

/**
 * 密码强度要求
 */
export const PASSWORD_RULES = {
  MIN_LENGTH: 6,
  MAX_LENGTH: 20,
  REQUIRE_NUMBER: true,
  REQUIRE_LETTER: true,
  REQUIRE_SPECIAL: false
} as const
