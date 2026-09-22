package com.game.system.util;

import java.util.*;

/**
 * ComDataUtil 数据缓存工具类
 * 单例模式，用于缓存字典数据、HTML字符串、系统配置等
 */
public class ComDataUtil {

    // 字典数据缓存（字典编码 -> 字典项列表）
    // 示例：{"user_status": [{"label": "启用", "value": "1"}, {"label": "禁用", "value": "0"}]}
    private Map<String, List<Map<String, Object>>> dictListMap = new HashMap<>();

    // 字典数据缓存（字典编码 -> (值 -> 标签)映射）
    // 示例：{"gender": {"male": "男", "female": "女", "unknown": "未知"}}
    private Map<String, Map<String, String>> dictMapMap = new HashMap<>();

    // HTML字符串缓存（键 -> HTML内容）
    private Map<String, String> htmlMap = new HashMap<>();

    // 系统配置缓存（配置键 -> 配置值）
    private Map<String, String> systemMap = new HashMap<>();

    // 单例实例
    private static final ComDataUtil instance = new ComDataUtil();

    /**
     * 获取单例实例
     */
    public static ComDataUtil getInstance() {
        return instance;
    }

    // ==================== 字典数据相关 ====================

    /**
     * 设置字典列表缓存
     */
    public void setDictListMap(Map<String, List<Map<String, Object>>> dictListMap) {
        this.dictListMap = dictListMap;
    }

    /**
     * 设置字典映射缓存
     */
    public void setDictMapMap(Map<String, Map<String, String>> dictMapMap) {
        this.dictMapMap = dictMapMap;
    }

    /**
     * 获取字典列表缓存
     */
    public Map<String, List<Map<String, Object>>> getDictListMap() {
        return dictListMap;
    }

    /**
     * 获取字典映射缓存
     */
    public Map<String, Map<String, String>> getDictMapMap() {
        return dictMapMap;
    }

    /**
     * 根据字典编码获取字典列表
     * @param dictCode 字典编码（如 "user_status"）
     * @return 字典项列表
     */
    public List<Map<String, Object>> getDictionaryList(String dictCode) {
        return dictListMap.getOrDefault(dictCode, new ArrayList<>());
    }

    /**
     * 根据字典编码和值获取标签
     * @param dictCode 字典编码（如 "gender"）
     * @param value 字典值（如 "male"）
     * @return 字典标签（如 "男"），未找到返回空字符串
     */
    public String getDictionaryLabelByValue(String dictCode, String value) {
        Map<String, String> map = dictMapMap.get(dictCode);
        if (map == null) {
            return "";
        }
        return map.getOrDefault(value, "");
    }

    /**
     * 添加或更新单个字典类型的缓存
     * @param dictCode 字典编码
     * @param dictList 字典项列表
     */
    public void putDictionaryList(String dictCode, List<Map<String, Object>> dictList) {
        dictListMap.put(dictCode, dictList);

        // 同时更新dictMapMap
        Map<String, String> map = new HashMap<>();
        for (Map<String, Object> item : dictList) {
            String value = String.valueOf(item.get("value"));
            String label = String.valueOf(item.get("label"));
            map.put(value, label);
        }
        dictMapMap.put(dictCode, map);
    }

    /**
     * 清空字典缓存
     */
    public void clearDictionary() {
        dictListMap.clear();
        dictMapMap.clear();
    }

    // ==================== HTML缓存相关 ====================

    /**
     * 设置HTML缓存
     */
    public void setHtmlMap(Map<String, String> htmlMap) {
        this.htmlMap = htmlMap;
    }

    /**
     * 获取HTML缓存
     */
    public Map<String, String> getHtmlMap() {
        return htmlMap;
    }

    /**
     * 获取HTML内容
     * @param key 键
     * @return HTML字符串
     */
    public String getHtml(String key) {
        return htmlMap.getOrDefault(key, "");
    }

    /**
     * 添加或更新HTML缓存
     * @param key 键
     * @param html HTML内容
     */
    public void putHtml(String key, String html) {
        htmlMap.put(key, html);
    }

    /**
     * 清空HTML缓存
     */
    public void clearHtml() {
        htmlMap.clear();
    }

    // ==================== 系统配置相关 ====================

    /**
     * 设置系统配置缓存
     */
    public void setSystemMap(Map<String, String> systemMap) {
        this.systemMap = systemMap;
    }

    /**
     * 获取系统配置缓存
     */
    public Map<String, String> getSystemMap() {
        return systemMap;
    }

    /**
     * 获取系统配置
     * @param key 配置键
     * @return 配置值
     */
    public String getSystemConfig(String key) {
        return systemMap.getOrDefault(key, "");
    }

    /**
     * 获取系统配置（带默认值）
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public String getSystemConfig(String key, String defaultValue) {
        return systemMap.getOrDefault(key, defaultValue);
    }

    /**
     * 设置系统配置
     * @param key 配置键
     * @param value 配置值
     */
    public void setSystemConfig(String key, String value) {
        systemMap.put(key, value);
    }

    /**
     * 清空系统配置缓存
     */
    public void clearSystemConfig() {
        systemMap.clear();
    }

    /**
     * 清空所有缓存
     */
    public void clearAll() {
        clearDictionary();
        clearHtml();
        clearSystemConfig();
    }

    /**
     * 获取缓存状态信息
     * @return 缓存统计信息
     */
    public Map<String, Object> getCacheStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("dictionaryCount", dictListMap.size());
        status.put("htmlCount", htmlMap.size());
        status.put("systemConfigCount", systemMap.size());
        return status;
    }
}
