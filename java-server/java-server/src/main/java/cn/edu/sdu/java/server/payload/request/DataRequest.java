package cn.edu.sdu.java.server.payload.request;


import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.DateTimeTool;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * 通用请求参数封装
 *
 * 前端传来的参数统一用Map存储，提供各种类型转换方法
 * 这样设计的好处是接口参数灵活，新增字段时不需要修改类定义
 */
@Getter
@Setter
public class DataRequest {
    private Map<String,Object> data;

    /** 创建空的参数表，每个请求独立存储参数 */
    public DataRequest() {
        data = new HashMap<>();
    }

    /** 写入参数，同名的键会被新值覆盖 */
    public void add(String key, Object obj){
        if (data == null) {
            data = new HashMap<>();
        }
        data.put(key,obj);
    }

    /** 读取原始参数值，不存在时返回null */
    public Object get(String key){
        if (data == null) {
            return null;
        }
        return data.get(key);
    }

    /** 转成字符串，不存在时返回null */
    public String getString(String key){
        Object obj = get(key);
        if(obj == null)
            return null;
        if(obj instanceof String)
            return (String)obj;
        return obj.toString();
    }

    /** 读取布尔值，不存在时默认false */
    public Boolean getBoolean(String key){
        Object obj = get(key);
        if(obj == null)
            return false;
        if(obj instanceof Boolean)
            return (Boolean)obj;
        return "true".equals(obj.toString());
    }

    /** 读取列表，不存在或类型不对时返回空列表 */
    public List<?> getList(String key){
        Object obj = get(key);
        if(obj == null)
            return new ArrayList<>();
        if(obj instanceof List)
            return (List<?>)obj;
        else
            return new ArrayList<>();
    }

    /** 读取嵌套Map，不存在或类型不对时返回空Map */
    public Map<String,Object> getMap(String key){
        if(data == null)
            return new HashMap<>();
        Object obj = data.get(key);
        if(obj == null)
            return new HashMap<>();
        if(obj instanceof Map)
            return (Map<String,Object>)obj;
        else
            return new HashMap<>();
    }

    /** 读取整数，转换失败时返回null，兼容老接口的数字转换规则 */
    public Integer getInteger(String key) {
        if(data == null)
            return null;
        return CommonMethod.getInteger(data, key);
    }

    /** 读取长整数，不存在或转换失败时返回null */
    public Long getLong(String key) {
        if(data == null)
            return null;
        Object obj = data.get(key);
        if(obj == null)
            return null;
        if(obj instanceof Long)
            return (Long)obj;
        String str = obj.toString();
        try {
            return Long.parseLong(str);
        }catch(Exception e) {
            return null;
        }
    }

    /** 按"yyyy-MM-dd HH:mm:ss"格式解析时间，失败时返回null */
    public Date getTime(String key) {
        Object obj = get(key);
        if(obj == null)
            return null;
        if(obj instanceof Date)
            return (Date)obj;
        String str = obj.toString();
        return DateTimeTool.formatDateTime(str,"yyyy-MM-dd HH:mm:ss");
    }
}
