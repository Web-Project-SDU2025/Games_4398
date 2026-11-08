package cn.edu.sdu.java.server.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 日期时间工具类
 *
 * 处理统计和账户时间格式转换
 * 每次调用单独创建格式化器
 */
public class DateTimeTool {
    /** 账户日志和旧接口共用的默认日期时间格式 */
    private static final String DEFAULT_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 按指定格式解析日期
     * 空输入或解析失败时返回null
     */
    public static Date formatDateTime(String text, String pattern) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        format.setLenient(true);
        try {
            return format.parse(text);
        } catch (ParseException error) {
            return null;
        }
    }

    /**
     * 按默认格式输出日期
     * 空日期返回null
     */
    public static String parseDateTime(Date date) {
        return parseDateTime(date, DEFAULT_FORMAT);
    }

    /**
     * 按指定格式输出日期
     * 空日期返回null
     */
    public static String parseDateTime(Date date, String pattern) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        return format.format(date);
    }

    /**
     * 获取上个月日期
     * 返回上个月的对应日期，跨月天数由Calendar按规则调整
     */
    public static Date prevMonth(Date date) {
        if (date == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MONTH, -1);
        return calendar.getTime();
    }
}
