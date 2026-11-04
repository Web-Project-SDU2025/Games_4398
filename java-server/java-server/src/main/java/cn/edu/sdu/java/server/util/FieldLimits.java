package cn.edu.sdu.java.server.util;

/** 维护数据库字段对应的校验上限，实体与业务校验共用相同参数 */
public final class FieldLimits {
    // 登录账号最多字符数
    public static final int USERNAME = 20;
    // 密码哈希与注册密码最多字符数
    public static final int PASSWORD = 60;
    // 昵称最多字符数
    public static final int NAME = 50;
    // 性别字段最多字符数
    public static final int GENDER = 2;
    // 年月日字段最多字符数
    public static final int DATE = 10;
    // 邮箱最多字符数
    public static final int EMAIL = 60;
    // 电话和地址字段各自最多字符数
    public static final int CONTACT = 20;
    // 个人介绍最多字符数
    public static final int INTRODUCTION = 1000;
    // 签名最多字符数
    public static final int SIGNATURE = 200;
    // 时间文字最多字符数
    public static final int DATE_TIME = 20;
    // 菜单、字典及配置短字段最多字符数
    public static final int SHORT_TEXT = 40;
    // 日志分类字段最多字符数
    public static final int LOG_TYPE = 4;
    // 日志表名最多字符数
    public static final int TABLE_NAME = 20;
    // 日志内容最多字符数
    public static final int LOG_CONTENT = 2000;
    // 请求地址和系统配置说明最多字符数
    public static final int DESCRIPTION = 100;

    /** 常量由类名直接读取，构造器保持私有 */
    private FieldLimits() {
    }
}
