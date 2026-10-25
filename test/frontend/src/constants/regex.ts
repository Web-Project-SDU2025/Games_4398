/**
 * 正则表达式常量
 */

/**
 * 用户名正则（4-20位字母、数字、下划线）
 */
export const USERNAME_REGEX = /^[a-zA-Z0-9_]{4,20}$/

/**
 * 密码正则（6-20位，至少包含字母和数字）
 */
export const PASSWORD_REGEX = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@$!%*#?&]{6,20}$/

/**
 * 邮箱正则
 */
export const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/

/**
 * 手机号正则（中国大陆）
 */
export const PHONE_REGEX = /^1[3-9]\d{9}$/

/**
 * 身份证号正则（中国大陆）
 */
export const ID_CARD_REGEX = /^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]$/

/**
 * URL 正则
 */
export const URL_REGEX = /^(https?:\/\/)?([\da-z.-]+)\.([a-z.]{2,6})([/\w .-]*)*\/?$/

/**
 * IP 地址正则
 */
export const IP_REGEX = /^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$/

/**
 * 中文正则
 */
export const CHINESE_REGEX = /^[一-龥]+$/

/**
 * 整数正则
 */
export const INTEGER_REGEX = /^-?\d+$/

/**
 * 正整数正则
 */
export const POSITIVE_INTEGER_REGEX = /^\d+$/

/**
 * 小数正则（保留两位小数）
 */
export const DECIMAL_REGEX = /^-?\d+(\.\d{1,2})?$/

/**
 * 邮政编码正则（中国大陆）
 */
export const POSTAL_CODE_REGEX = /^\d{6}$/

/**
 * 银行卡号正则
 */
export const BANK_CARD_REGEX = /^\d{16,19}$/

/**
 * 验证用户名
 * @param username 用户名
 * @returns 是否有效
 */
export function validateUsername(username: string): boolean {
  return USERNAME_REGEX.test(username)
}

/**
 * 验证密码
 * @param password 密码
 * @returns 是否有效
 */
export function validatePassword(password: string): boolean {
  return PASSWORD_REGEX.test(password)
}

/**
 * 验证邮箱
 * @param email 邮箱
 * @returns 是否有效
 */
export function validateEmail(email: string): boolean {
  return EMAIL_REGEX.test(email)
}

/**
 * 验证手机号
 * @param phone 手机号
 * @returns 是否有效
 */
export function validatePhone(phone: string): boolean {
  return PHONE_REGEX.test(phone)
}

/**
 * 验证 URL
 * @param url URL
 * @returns 是否有效
 */
export function validateUrl(url: string): boolean {
  return URL_REGEX.test(url)
}

/**
 * 验证 IP 地址
 * @param ip IP 地址
 * @returns 是否有效
 */
export function validateIp(ip: string): boolean {
  return IP_REGEX.test(ip)
}
