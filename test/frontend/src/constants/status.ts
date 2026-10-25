/**
 * 状态码常量
 */

/**
 * HTTP 状态码
 */
export const HTTP_STATUS = {
  OK: 200,
  CREATED: 201,
  NO_CONTENT: 204,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  INTERNAL_SERVER_ERROR: 500,
  SERVICE_UNAVAILABLE: 503
} as const

/**
 * 业务状态码
 */
export const BUSINESS_CODE = {
  SUCCESS: 200,
  FAIL: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  SERVER_ERROR: 500
} as const

/**
 * 用户状态
 */
export const USER_STATUS = {
  DISABLED: 0,
  ENABLED: 1
} as const

/**
 * 用户状态文本映射
 */
export const USER_STATUS_TEXT = {
  [USER_STATUS.DISABLED]: '禁用',
  [USER_STATUS.ENABLED]: '启用'
} as const

/**
 * 角色状态
 */
export const ROLE_STATUS = {
  DISABLED: 0,
  ENABLED: 1
} as const

/**
 * 角色状态文本映射
 */
export const ROLE_STATUS_TEXT = {
  [ROLE_STATUS.DISABLED]: '禁用',
  [ROLE_STATUS.ENABLED]: '启用'
} as const

/**
 * 菜单类型
 */
export const MENU_TYPE = {
  MENU: 1,
  BUTTON: 2
} as const

/**
 * 菜单类型文本映射
 */
export const MENU_TYPE_TEXT = {
  [MENU_TYPE.MENU]: '菜单',
  [MENU_TYPE.BUTTON]: '按钮'
} as const

/**
 * 菜单状态
 */
export const MENU_STATUS = {
  DISABLED: 0,
  ENABLED: 1
} as const

/**
 * 菜单状态文本映射
 */
export const MENU_STATUS_TEXT = {
  [MENU_STATUS.DISABLED]: '禁用',
  [MENU_STATUS.ENABLED]: '启用'
} as const

/**
 * 游戏状态
 */
export const GAME_STATUS = {
  OFFLINE: 0,
  ONLINE: 1
} as const

/**
 * 游戏状态文本映射
 */
export const GAME_STATUS_TEXT = {
  [GAME_STATUS.OFFLINE]: '下架',
  [GAME_STATUS.ONLINE]: '上架'
} as const

/**
 * 用户游戏清单状态
 */
export const USER_GAME_STATUS = {
  FAVORITE: 1,
  PLAYING: 2,
  COMPLETED: 3
} as const

/**
 * 用户游戏清单状态文本映射
 */
export const USER_GAME_STATUS_TEXT = {
  [USER_GAME_STATUS.FAVORITE]: '收藏',
  [USER_GAME_STATUS.PLAYING]: '正在玩',
  [USER_GAME_STATUS.COMPLETED]: '已完成'
} as const

/**
 * 性别
 */
export const GENDER = {
  MALE: 'male',
  FEMALE: 'female',
  UNKNOWN: 'unknown'
} as const

/**
 * 性别文本映射
 */
export const GENDER_TEXT = {
  [GENDER.MALE]: '男',
  [GENDER.FEMALE]: '女',
  [GENDER.UNKNOWN]: '未知'
} as const
