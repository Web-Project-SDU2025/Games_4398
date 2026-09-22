package com.game.system.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 日期时间工具类
 * 提供日期格式化、计算等功能
 */
public class DateTimeTool {

    /**
     * 格式化日期时间为字符串
     * @param date 日期对象
     * @param format 格式字符串
     * @return 格式化后的字符串
     */
    public static String formatDateTime(Date date, String format) {
        if (date == null) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(date);
    }

    /**
     * 解析字符串为日期对象
     * @param dateStr 日期字符串
     * @param format 格式字符串
     * @return Date对象，解析失败返回null
     */
    public static Date formatDateTime(String dateStr, String format) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        try {
            return sdf.parse(dateStr);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * 获取当前日期时间字符串（yyyy-MM-dd HH:mm:ss）
     */
    public static String getNow() {
        return formatDateTime(new Date(), "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 获取当前日期字符串（yyyy-MM-dd）
     */
    public static String getToday() {
        return formatDateTime(new Date(), "yyyy-MM-dd");
    }

    /**
     * 获取后n天的日期
     * @param date 基准日期
     * @param days 天数
     * @return 新日期对象
     */
    public static Date addDays(Date date, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    /**
     * 获取前n天的日期
     * @param date 基准日期
     * @param days 天数
     * @return 新日期对象
     */
    public static Date subtractDays(Date date, int days) {
        return addDays(date, -days);
    }

    /**
     * 获取后n个月的日期
     * @param date 基准日期
     * @param months 月数
     * @return 新日期对象
     */
    public static Date addMonths(Date date, int months) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MONTH, months);
        return calendar.getTime();
    }

    /**
     * 获取前n个月的日期
     * @param date 基准日期
     * @param months 月数
     * @return 新日期对象
     */
    public static Date subtractMonths(Date date, int months) {
        return addMonths(date, -months);
    }

    /**
     * 获取后n年的日期
     * @param date 基准日期
     * @param years 年数
     * @return 新日期对象
     */
    public static Date addYears(Date date, int years) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.YEAR, years);
        return calendar.getTime();
    }

    /**
     * 获取前n年的日期
     * @param date 基准日期
     * @param years 年数
     * @return 新日期对象
     */
    public static Date subtractYears(Date date, int years) {
        return addYears(date, -years);
    }

    /**
     * 获取当前日期的开始时间（00:00:00）
     * @param date 基准日期
     * @return 新日期对象
     */
    public static Date getStartOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * 获取当前日期的结束时间（23:59:59）
     * @param date 基准日期
     * @return 新日期对象
     */
    public static Date getEndOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    /**
     * 获取两个日期之间的天数差
     * @param date1 日期1
     * @param date2 日期2
     * @return 天数差
     */
    public static long getDaysDiff(Date date1, Date date2) {
        long diff = date2.getTime() - date1.getTime();
        return diff / (24 * 60 * 60 * 1000);
    }

    /**
     * 获取当前星期几
     * @param date 日期对象
     * @return 1-7 (1为周一，7为周日)
     */
    public static int getDayOfWeek(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        // 将周日从1转换为7
        return day == 1 ? 7 : day - 1;
    }

    /**
     * 获取星期几的中文名称
     * @param date 日期对象
     * @return 中文名称
     */
    public static String getDayOfWeekName(Date date) {
        String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return days[getDayOfWeek(date) - 1];
    }

    /**
     * 判断是否为今天
     * @param date 日期对象
     * @return 是否为今天
     */
    public static boolean isToday(Date date) {
        String today = formatDateTime(new Date(), "yyyy-MM-dd");
        String dateStr = formatDateTime(date, "yyyy-MM-dd");
        return today.equals(dateStr);
    }

    /**
     * 获取月份的天数
     * @param year 年份
     * @param month 月份 (1-12)
     * @return 天数
     */
    public static int getDaysInMonth(int year, int month) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    /**
     * 获取年份
     * @param date 日期对象
     * @return 年份
     */
    public static int getYear(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.YEAR);
    }

    /**
     * 获取月份
     * @param date 日期对象
     * @return 月份 (1-12)
     */
    public static int getMonth(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.MONTH) + 1;
    }

    /**
     * 获取日
     * @param date 日期对象
     * @return 日 (1-31)
     */
    public static int getDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.DAY_OF_MONTH);
    }
}
