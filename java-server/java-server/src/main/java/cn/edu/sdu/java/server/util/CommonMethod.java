package cn.edu.sdu.java.server.util;

import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.UserDetailsImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Map;

/** 整理接口响应、当前登录身份和菜单参数 */
public class CommonMethod {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 返回业务数据及说明，成功码统一为零 */
    public static DataResponse getReturnData(Object data, String message) {
        return new DataResponse(0, data, message);
    }

    /** 返回指定状态码和提示文字 */
    public static DataResponse getReturnMessage(Integer code, String message) {
        return new DataResponse(code, null, message);
    }

    /** 返回不带提示文字的业务数据 */
    public static DataResponse getReturnData(Object data) {
        return getReturnData(data, null);
    }

    /** 返回带说明的成功结果 */
    public static DataResponse getReturnMessageOK(String message) {
        return getReturnMessage(0, message);
    }

    /** 返回不带说明的成功结果 */
    public static DataResponse getReturnMessageOK() {
        return getReturnMessageOK(null);
    }

    /** 返回业务失败说明，失败码统一为一 */
    public static DataResponse getReturnMessageError(String message) {
        return getReturnMessage(1, message);
    }

    /** 从认证上下文读取本站账户，未登录时返回空值 */
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

    /** 返回当前用户编号，未登录时返回空值 */
    public static Integer getUserId() {
        UserDetailsImpl user = currentUser();
        if (user == null) {
            return null;
        }
        return user.getId();
    }

    /** 返回当前账户的角色名称，未登录或没有角色时返回空值 */
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

    /** 将对象转换为日志中的 JSON，无法转换时返回空字符串 */
    public static String ObjectToJSon(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            return "";
        }
    }
}
