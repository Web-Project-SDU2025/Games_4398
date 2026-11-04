package cn.edu.sdu.java.server.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/** 处理原统计和账户时间格式，每次调用分别创建格式器 */
public class DateTimeTool {
    // 账户日志和旧接口共用的默认日期时间格式
    private static final String DEFAULT_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /** 按指定格式读取日期，空输入或解析失败时返回空值 */
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

    /** 按默认日志格式输出日期，空日期返回空值 */
    public static String parseDateTime(Date date) {
        return parseDateTime(date, DEFAULT_FORMAT);
    }

    /** 按指定格式输出日期，空日期返回空值 */
    public static String parseDateTime(Date date, String pattern) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        return format.format(date);
    }

    /** 返回上个月的对应日期，跨月天数由 Calendar 按原规则调整 */
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
