package com.game.system.payload.request;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据请求封装类
 * 用于接收前端传来的数据
 */
public class DataRequest {

    private Map<String, Object> data;

    public DataRequest() {
        this.data = new HashMap<>();
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    /**
     * 添加数据
     */
    public void add(String key, Object value) {
        this.data.put(key, value);
    }

    /**
     * 获取数据
     */
    public Object get(String key) {
        return this.data.get(key);
    }

    /**
     * 获取字符串
     */
    public String getString(String key) {
        Object obj = this.data.get(key);
        return obj == null ? "" : obj.toString();
    }

    /**
     * 获取整数
     */
    public Integer getInteger(String key) {
        Object obj = this.data.get(key);
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取布尔值
     */
    public Boolean getBoolean(String key) {
        Object obj = this.data.get(key);
        if (obj == null) return false;
        if (obj instanceof Boolean) return (Boolean) obj;
        return "true".equalsIgnoreCase(obj.toString());
    }

    @Override
    public String toString() {
        return "DataRequest{" +
                "data=" + data +
                '}';
    }
}
