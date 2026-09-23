/**
 * API 相关常量
 */

/**
 * API 基础路径
 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

/**
 * API 端点
 */
export const API_ENDPOINTS = {
  // 认证相关
  AUTH: {
    LOGIN: '/auth/login',
    LOGOUT: '/auth/logout',
    REGISTER: '/auth/register',
    REFRESH_TOKEN: '/auth/refresh'
  },

  // 用户相关
  USER: {
    LIST: '/user/list',
    DETAIL: '/user/:id',
    CREATE: '/user/create',
    UPDATE: '/user/update/:id',
    DELETE: '/user/delete/:id',
    CHANGE_PASSWORD: '/user/change-password',
    RESET_PASSWORD: '/user/reset-password/:id'
  },

  // 角色相关
  ROLE: {
    LIST: '/role/list',
    DETAIL: '/role/:id',
    CREATE: '/role/create',
    UPDATE: '/role/update/:id',
    DELETE: '/role/delete/:id'
  },

  // 菜单相关
  MENU: {
    LIST: '/menu/list',
    TREE: '/menu/tree',
    DETAIL: '/menu/:id',
    CREATE: '/menu/create',
    UPDATE: '/menu/update/:id',
    DELETE: '/menu/delete/:id'
  },

  // 游戏相关
  GAME: {
    LIST: '/game/list',
    DETAIL: '/game/:id',
    CREATE: '/game/create',
    UPDATE: '/game/update/:id',
    DELETE: '/game/delete/:id'
  }
} as const

/**
 * 请求超时时间（毫秒）
 */
export const REQUEST_TIMEOUT = 30000

/**
 * 请求重试次数
 */
export const REQUEST_RETRY_TIMES = 3

/**
 * 请求头键名
 */
export const HEADER_KEYS = {
  AUTHORIZATION: 'Authorization',
  CONTENT_TYPE: 'Content-Type',
  ACCEPT: 'Accept'
} as const

/**
 * Content-Type 类型
 */
export const CONTENT_TYPES = {
  JSON: 'application/json',
  FORM: 'application/x-www-form-urlencoded',
  MULTIPART: 'multipart/form-data'
} as const
