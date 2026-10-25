package com.game.system.util;

import com.game.system.payload.response.DataResponse;
import com.game.system.service.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.*;

/**
 * CommonMethod 通用工具方法类
 * 提供常用的数据处理和响应封装方法
 */
public class CommonMethod {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 返回成功响应（带数据和消息）
     */
    public static DataResponse getReturnData(Object obj, String msg) {
        return new DataResponse(0, obj, msg);
    }

    /**
     * 返回响应消息
     */
    public static DataResponse getReturnMessage(Integer code, String msg) {
        return new DataResponse(code, null, msg);
    }

    /**
     * 返回成功响应（仅带数据）
     */
    public static DataResponse getReturnData(Object obj) {
        return getReturnData(obj, null);
    }

    /**
     * 返回成功消息
     */
    public static DataResponse getReturnMessageOK(String msg) {
        return getReturnMessage(0, msg);
    }

    /**
     * 返回成功（无消息）
     */
    public static DataResponse getReturnMessageOK() {
        return getReturnMessage(0, null);
    }

    /**
     * 返回错误消息
     */
    public static DataResponse getReturnMessageError(String msg) {
        return getReturnMessage(1, msg);
    }

    /**
     * 从Map中获取字符串数组
     */
    public static String[] getStrings(Map<String, Object> data, String key) {
        Object obj = data.get(key);
        if (obj == null)
            return new String[]{};
        if (obj instanceof String[])
            return (String[]) obj;
        return new String[]{};
    }

    /**
     * 从Map中获取字符串
     */
    public static String getString(Map<String, Object> data, String key) {
        Object obj = data.get(key);
        if (obj == null)
            return "";
        if (obj instanceof String)
            return (String) obj;
        return obj.toString();
    }

    /**
     * 从Map中获取布尔值
     */
    public static Boolean getBoolean(Map<String, Object> data, String key) {
        Object obj = data.get(key);
        if (obj == null)
            return false;
        if (obj instanceof Boolean)
            return (Boolean) obj;
        return "true".equals(obj.toString());
    }

    /**
     * 从Map中获取列表
     */
    public static List<?> getList(Map<String, Object> data, String key) {
        Object obj = data.get(key);
        if (obj == null)
            return new ArrayList<>();
        if (obj instanceof List)
            return (List<?>) obj;
        else
            return new ArrayList<>();
    }

    /**
     * 从Map中获取Map对象
     */
    public static Map<String, Object> getMap(Map<String, Object> data, String key) {
        Object obj = data.get(key);
        if (obj == null)
            return new HashMap<>();
        if (obj instanceof Map)
            return (Map<String, Object>) obj;
        else
            return new HashMap<>();
    }

    /**
     * 从Map中获取整数（可能为null）
     */
    public static Integer getInteger(Map<String, Object> data, String key) {
        Object obj = data.get(key);
        if (obj == null)
            return null;
        if (obj instanceof Integer)
            return (Integer) obj;
        String str = obj.toString();
        try {
            return (int) Double.parseDouble(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从Map中获取整数（默认值为0）
     */
    public static Integer getInteger0(Map<String, Object> data, String key) {
        Object obj = data.get(key);
        if (obj == null)
            return 0;
        if (obj instanceof Integer)
            return (Integer) obj;
        String str = obj.toString();
        try {
            return (int) Double.parseDouble(str);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 从Map中获取长整型
     */
    public static Long getLong(Map<String, Object> data, String key) {
        Object obj = data.get(key);
        if (obj == null)
            return null;
        if (obj instanceof Long)
            return (Long) obj;
        String str = obj.toString();
        try {
            return Long.parseLong(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从Map中获取双精度浮点数（可能为null）
     */
    public static Double getDouble(Map<String, Object> data, String key) {
        Object obj = data.get(key);
        if (obj == null)
            return null;
        if (obj instanceof Double)
            return (Double) obj;
        String str = obj.toString();
        try {
            return Double.parseDouble(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从Map中获取双精度浮点数（默认值为0.0）
     */
    public static Double getDouble0(Map<String, Object> data, String key) {
        Double d0 = 0d;
        Object obj = data.get(key);
        if (obj == null)
            return d0;
        if (obj instanceof Double)
            return (Double) obj;
        String str = obj.toString();
        try {
            return Double.parseDouble(str);
        } catch (Exception e) {
            return d0;
        }
    }

    /**
     * 从Map中获取日期（yyyy-MM-dd格式）
     */
    public static Date getDate(Map<String, Object> data, String key) {
        Object obj = data.get(key);
        if (obj == null)
            return null;
        if (obj instanceof Date)
            return (Date) obj;
        String str = obj.toString();
        return DateTimeTool.formatDateTime(str, "yyyy-MM-dd");
    }

    /**
     * 从Map中获取时间（yyyy-MM-dd HH:mm:ss格式）
     */
    public static Date getTime(Map<String, Object> data, String key) {
        Object obj = data.get(key);
        if (obj == null)
            return null;
        if (obj instanceof Date)
            return (Date) obj;
        String str = obj.toString();
        return DateTimeTool.formatDateTime(str, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 获取下一个2位数字编号
     * @param num 当前编号
     * @return 下一个编号
     */
    public static String getNextNum2(String num) {
        String str;
        String prefix;
        if (num.length() == 2) {
            str = num;
            prefix = "";
        } else {
            str = num.substring(num.length() - 2);
            prefix = num.substring(0, num.length() - 2);
        }
        int c;
        if (str.charAt(0) == '0') {
            c = str.charAt(1) - '0';
        } else {
            c = (str.charAt(0) - '0') * 10 + str.charAt(1) - '0';
        }
        c++;
        if (c < 10) {
            return prefix + "0" + c;
        } else {
            return prefix + c;
        }
    }

    /**
     * 获取下一个3位数字编号
     */
    public static String getNextNum3(String num) {
        String str;
        String prefix;
        if (num.length() == 3) {
            str = num;
            prefix = "";
        } else {
            str = num.substring(num.length() - 3);
            prefix = num.substring(0, num.length() - 3);
        }
        int c;
        if (str.charAt(0) == '0') {
            if (str.charAt(1) == '0') {
                c = str.charAt(2) - '0';
            } else {
                c = (str.charAt(1) - '0') * 10 + str.charAt(2) - '0';
            }
        } else {
            c = (str.charAt(0) - '0') * 100 + (str.charAt(1) - '0') * 10 + str.charAt(2) - '0';
        }
        c++;
        if (c < 10) {
            return prefix + "00" + c;
        } else if (c < 100) {
            return prefix + "0" + c;
        } else {
            return prefix + c;
        }
    }

    /**
     * 获取下一个4位数字编号
     */
    public static String getNextNum4(String num) {
        String str;
        String prefix;
        if (num.length() == 4) {
            str = num;
            prefix = "";
        } else {
            str = num.substring(num.length() - 4);
            prefix = num.substring(0, num.length() - 4);
        }
        int c;
        if (str.charAt(0) == '0') {
            if (str.charAt(1) == '0') {
                if (str.charAt(2) == '0') {
                    c = str.charAt(3) - '0';
                } else {
                    c = (str.charAt(2) - '0') * 10 + str.charAt(3) - '0';
                }
            } else {
                c = (str.charAt(1) - '0') * 100 + (str.charAt(2) - '0') * 10 + str.charAt(3) - '0';
            }
        } else {
            c = (str.charAt(0) - '0') * 1000 + (str.charAt(1) - '0') * 100
                + (str.charAt(2) - '0') * 10 + str.charAt(3) - '0';
        }
        c++;
        if (c < 10) {
            return prefix + "000" + c;
        } else if (c < 100) {
            return prefix + "00" + c;
        } else if (c < 1000) {
            return prefix + "0" + c;
        } else {
            return prefix + c;
        }
    }

    /**
     * 保留两位小数
     */
    public static Double getDouble2(Double f) {
        if (f == null)
            return 0d;
        BigDecimal bg = new BigDecimal(f);
        return bg.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    /**
     * 对象转JSON字符串
     */
    public static String objectToJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取当前登录用户的ID
     * 该方法可以在任何Controller和Service中使用
     * @return 用户ID，未登录返回null
     */
    public static Long getUserId() {
        Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(obj instanceof UserDetailsImpl userDetails))
            return null;
        return userDetails.getId();
    }

    /**
     * 获取当前登录用户的用户名
     * @return 用户名，未登录返回null
     */
    public static String getUsername() {
        Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(obj instanceof UserDetailsImpl userDetails))
            return null;
        return userDetails.getUsername();
    }

    /**
     * 获取当前登录用户的角色名称
     * @return 角色名称，未登录返回null
     */
    public static String getRoleName() {
        Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(obj instanceof UserDetailsImpl userDetails))
            return null;
        return userDetails.getAuthorities().iterator().next().toString();
    }

    /**
     * 根据分数获取等级
     * @param score 分数
     * @return 等级字符串
     */
    public static String getLevelFromScore(Double score) {
        if (score == null)
            return "";
        if (score >= 89.5)
            return "优";
        if (score >= 79.5)
            return "良";
        if (score >= 69.5)
            return "中";
        if (score >= 59.5)
            return "及格";
        return "不及格";
    }
}
