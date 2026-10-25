/**
 * 日期时间工具类
 * 提供日期格式化和计算功能
 */

/**
 * 格式化日期为字符串
 * @param date 日期对象
 * @param format 格式字符串，如 'yyyy-MM-dd HH:mm:ss'
 * @returns 格式化后的字符串
 */
export function formatDate(date: Date | string | number, format: string = 'yyyy-MM-dd HH:mm:ss'): string {
    const d = new Date(date);

    if (isNaN(d.getTime())) {
        return '';
    }

    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    const hours = String(d.getHours()).padStart(2, '0');
    const minutes = String(d.getMinutes()).padStart(2, '0');
    const seconds = String(d.getSeconds()).padStart(2, '0');

    return format
        .replace('yyyy', String(year))
        .replace('MM', month)
        .replace('dd', day)
        .replace('HH', hours)
        .replace('mm', minutes)
        .replace('ss', seconds);
}

/**
 * 解析字符串为日期对象
 * @param dateString 日期字符串
 * @returns Date对象，解析失败返回null
 */
export function parseDate(dateString: string): Date | null {
    if (!dateString) return null;

    const date = new Date(dateString);
    return isNaN(date.getTime()) ? null : date;
}

/**
 * 获取后n天的日期
 * @param date 基准日期
 * @param days 天数
 * @returns 新日期对象
 */
export function addDays(date: Date, days: number): Date {
    const result = new Date(date);
    result.setDate(result.getDate() + days);
    return result;
}

/**
 * 获取前n天的日期
 * @param date 基准日期
 * @param days 天数
 * @returns 新日期对象
 */
export function subtractDays(date: Date, days: number): Date {
    return addDays(date, -days);
}

/**
 * 获取后n个月的日期
 * @param date 基准日期
 * @param months 月数
 * @returns 新日期对象
 */
export function addMonths(date: Date, months: number): Date {
    const result = new Date(date);
    result.setMonth(result.getMonth() + months);
    return result;
}

/**
 * 获取前n个月的日期
 * @param date 基准日期
 * @param months 月数
 * @returns 新日期对象
 */
export function subtractMonths(date: Date, months: number): Date {
    return addMonths(date, -months);
}

/**
 * 获取后n年的日期
 * @param date 基准日期
 * @param years 年数
 * @returns 新日期对象
 */
export function addYears(date: Date, years: number): Date {
    const result = new Date(date);
    result.setFullYear(result.getFullYear() + years);
    return result;
}

/**
 * 获取前n年的日期
 * @param date 基准日期
 * @param years 年数
 * @returns 新日期对象
 */
export function subtractYears(date: Date, years: number): Date {
    return addYears(date, -years);
}

/**
 * 获取当前日期的开始时间（00:00:00）
 * @param date 基准日期
 * @returns 新日期对象
 */
export function getStartOfDay(date: Date): Date {
    const result = new Date(date);
    result.setHours(0, 0, 0, 0);
    return result;
}

/**
 * 获取当前日期的结束时间（23:59:59）
 * @param date 基准日期
 * @returns 新日期对象
 */
export function getEndOfDay(date: Date): Date {
    const result = new Date(date);
    result.setHours(23, 59, 59, 999);
    return result;
}

/**
 * 获取两个日期之间的天数差
 * @param date1 日期1
 * @param date2 日期2
 * @returns 天数差
 */
export function getDaysDiff(date1: Date, date2: Date): number {
    const msPerDay = 24 * 60 * 60 * 1000;
    const d1 = getStartOfDay(date1);
    const d2 = getStartOfDay(date2);
    return Math.round((d2.getTime() - d1.getTime()) / msPerDay);
}

/**
 * 获取当前星期几
 * @param date 日期对象
 * @returns 0-6 (0为周日)
 */
export function getDayOfWeek(date: Date): number {
    return date.getDay();
}

/**
 * 获取星期几的中文名称
 * @param date 日期对象
 * @returns 中文名称
 */
export function getDayOfWeekName(date: Date): string {
    const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
    return days[date.getDay()];
}

/**
 * 判断是否为今天
 * @param date 日期对象
 * @returns 是否为今天
 */
export function isToday(date: Date): boolean {
    const today = new Date();
    return date.getFullYear() === today.getFullYear() &&
           date.getMonth() === today.getMonth() &&
           date.getDate() === today.getDate();
}

/**
 * 判断是否为有效日期
 * @param date 日期对象或字符串
 * @returns 是否有效
 */
export function isValidDate(date: Date | string): boolean {
    const d = new Date(date);
    return !isNaN(d.getTime());
}

/**
 * 获取月份的天数
 * @param year 年份
 * @param month 月份 (1-12)
 * @returns 天数
 */
export function getDaysInMonth(year: number, month: number): number {
    return new Date(year, month, 0).getDate();
}
