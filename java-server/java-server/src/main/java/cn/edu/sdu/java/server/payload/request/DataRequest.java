package cn.edu.sdu.java.server.payload.request;


import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.DateTimeTool;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/*
 * DataRequest 请求参数数据类
 * Map data 保存前端请求参数的Map集合
 */
@Getter
@Setter
public class DataRequest {
    private Map<String,Object> data;

    /** 创建独立参数表，每个请求分别保存字段 */
    public DataRequest() {
        data = new HashMap<>();
    }

    /** 写入指定字段，重复键由最新值覆盖 */
    public void add(String key, Object obj){
        if (data == null) {
            data = new HashMap<>();
        }
        data.put(key,obj);
    }
    /** 读取原始字段，字段缺失时返回空值 */
    public Object get(String key){
        if (data == null) {
            return null;
        }
        return data.get(key);
    }

    /** 将字段转换为文字，字段缺失时返回空值 */
    public String getString(String key){
        Object obj = get(key);
        if(obj == null)
            return null;
        if(obj instanceof String)
            return (String)obj;
        return obj.toString();
    }
    /** 读取布尔值，字段缺失时使用 false */
    public Boolean getBoolean(String key){
        Object obj = get(key);
        if(obj == null)
            return false;
        if(obj instanceof Boolean)
            return (Boolean)obj;
        return "true".equals(obj.toString());
    }

    /** 读取列表，缺失或类型不符时返回空列表 */
    public List<?> getList(String key){
        Object obj = get(key);
        if(obj == null)
            return new ArrayList<>();
        if(obj instanceof List)
            return (List<?>)obj;
        else
            return new ArrayList<>();
    }
    /** 读取嵌套参数表，缺失或类型不符时返回空表 */
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

    /** 读取整数，转换失败时返回空值，数字转换沿用现有接口规则 */
    public Integer getInteger(String key) {
        if(data == null)
            return null;
        return CommonMethod.getInteger(data, key);
    }
    /** 读取长整数，字段缺失或转换失败时返回空值 */
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

    /** 读取小数，字段缺失或转换失败时返回空值 */
    public Double getDouble(String key) {
        if(data == null)
            return null;
        Object obj = data.get(key);
        if(obj == null)
            return null;
        if(obj instanceof Double)
            return (Double)obj;
        String str = obj.toString();
        try {
            return Double.parseDouble(str);
        }catch(Exception e) {
            return null;
        }
    }
    /** 按年月日读取日期，解析结果由共用日期工具提供 */
    public Date getDate( String key) {
        Object obj = get(key);
        if(obj == null)
            return null;
        if(obj instanceof Date)
            return (Date)obj;
        String str = obj.toString();
        return DateTimeTool.formatDateTime(str,"yyyy-MM-dd");
    }
    /** 按年月日和时分秒读取时间，解析失败时返回空值 */
    public Date getTime(String key) {
        Object obj = get(key);
        if(obj == null)
            return null;
        if(obj instanceof Date)
            return (Date)obj;
        String str = obj.toString();
        return DateTimeTool.formatDateTime(str,"yyyy-MM-dd HH:mm:ss");
    }
    /** 将从一开始的页面编号转换为从零开始，缺失或非正数时返回零 */
    public Integer getCurrentPage(){
        Integer cPage = this.getInteger("currentPage");
        if(cPage != null && cPage >0 )
            cPage = cPage -1;
        else
            cPage = 0;
        return cPage;

    }

}
