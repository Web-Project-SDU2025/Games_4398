package cn.edu.sdu.java.server.util;

import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.UserDetailsImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Map;

/**
 * 通用工具方法
 *
 * 提供接口响应包装、当前登录用户信息获取等常用功能
 * 避免每个地方都重复写相同的代码
 */
public class CommonMethod {
    /**
     * 包装业务数据和提示消息
     * 成功的接口统一返回code=0
     */
    public static DataResponse getReturnData(Object data, String message) {
        return new DataResponse(0, data, message);
    }

    /**
     * 返回指定状态码和提示
     * 用于需要自定义错误码的场景
     */
    public static DataResponse getReturnMessage(Integer code, String message) {
        return new DataResponse(code, null, message);
    }

    /**
     * 只返回数据，不带提示消息
     * 常用于列表查询等场景
     */
    public static DataResponse getReturnData(Object data) {
        return getReturnData(data, null);
    }

    /**
     * 返回成功提示
     * 用于操作成功但没有返回数据的场景
     */
    public static DataResponse getReturnMessageOK(String message) {
        return getReturnMessage(0, message);
    }

    /**
     * 返回成功状态，不带提示
     * 最简单的成功响应
     */
    public static DataResponse getReturnMessageOK() {
        return getReturnMessageOK(null);
    }

    /**
     * 返回错误提示
     * 失败接口统一返回code=1
     */
    public static DataResponse getReturnMessageError(String message) {
        return getReturnMessage(1, message);
    }

    /**
     * 从Spring Security上下文获取当前用户
     * 未登录时返回null
     */
    private static UserDetailsImpl currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof UserDetailsImpl)) {
            return null;
        }
        return (UserDetailsImpl) principal;
    }

    /**
     * 获取当前登录用户的ID
     * 常用于需要知道是谁在操作的场景
     */
    public static Integer getUserId() {
        UserDetailsImpl user = currentUser();
        if (user == null) {
            return null;
        }
        return user.getId();
    }

    /**
     * 获取当前用户的角色
     * 用于判断是管理员还是普通用户
     */
    public static String getRoleName() {
        UserDetailsImpl user = currentUser();
        if (user == null) {
            return null;
        }
        for (GrantedAuthority authority : user.getAuthorities()) {
            return authority.getAuthority();
        }
        return null;
    }

    /** 从参数表读取文字，字段不存在时返回空字符串 */
    public static String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    /** 从参数表读取整数，沿用原有数字转换方式，无法转换时返回空值 */
    public static Integer getInteger(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        try {
            double number = Double.parseDouble(value.toString());
            return (int) number;
        } catch (NumberFormatException error) {
            return null;
        }
    }

}
