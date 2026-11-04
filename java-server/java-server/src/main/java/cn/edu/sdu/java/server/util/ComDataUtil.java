package cn.edu.sdu.java.server.util;

import cn.edu.sdu.java.server.payload.response.OptionItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 保存启动时的字典缓存与旧 HTML 接口内容，缓存由当前进程持有 */
public class ComDataUtil {
    private static final ComDataUtil INSTANCE = new ComDataUtil();
    private Map<String, List<OptionItem>> dictListMap = new HashMap<>();
    private Map<String, Map<String, String>> dictMapMap = new HashMap<>();
    private Map<String, String> systemMap = new HashMap<>();
    private final Map<Integer, String> htmlMap = new HashMap<>();
    private int htmlCount;

    /** 返回当前进程共用的缓存对象 */
    public static ComDataUtil getInstance() {
        return INSTANCE;
    }

    /** 返回字典选项缓存，启动服务负责填充空缓存 */
    public Map<String, List<OptionItem>> getDictListMap() {
        return dictListMap;
    }

    /** 替换完整字典选项缓存，调用方提供独立映射 */
    public void setDictListMap(Map<String, List<OptionItem>> values) {
        dictListMap = values;
    }

    /** 返回字典值到标题的映射，启动服务按类别填充 */
    public Map<String, Map<String, String>> getDictMapMap() {
        return dictMapMap;
    }

    /** 替换字典标题缓存，空表表示当前没有可用字典 */
    public void setDictMapMap(Map<String, Map<String, String>> values) {
        dictMapMap = values;
    }

    /** 保存启动时读取的系统配置，空表保留为空配置 */
    public void setSystemMap(Map<String, String> values) {
        systemMap = values;
    }

    /** 生成 HTML 编号并保存内容，同步访问使编号与内容对应 */
    public synchronized int addHtmlString(String html) {
        htmlCount = htmlCount + 1;
        htmlMap.put(htmlCount, html);
        return htmlCount;
    }

    /** 按编号读取 HTML，内容缺失时返回空值 */
    public synchronized String getHtmlString(int id) {
        return htmlMap.get(id);
    }
}
