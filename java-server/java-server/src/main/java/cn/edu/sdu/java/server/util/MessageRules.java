package cn.edu.sdu.java.server.util;

/** 消息内容与发布频率由服务端统一约束，分页复用管理查询规则 */
public final class MessageRules {
    /** 消息业务编号的最大长度 */
    public static final int ID_LIMIT = 160;
    /** 公告标题最多字符数 */
    public static final int TITLE_LIMIT = 80;
    /** 公告正文最多字符数 */
    public static final int CONTENT_LIMIT = 2000;
    /** 管理员确认会修复问题 */
    public static final String FEEDBACK_WILL_FIX = "will_fix";
    /** 管理员确认不会修复问题 */
    public static final String FEEDBACK_WILL_NOT_FIX = "will_not_fix";
    /** 管理员确认问题已经解决 */
    public static final String FEEDBACK_RESOLVED = "resolved";
    /** 问题尚未处理 */
    public static final String FEEDBACK_PENDING = "pending";
    /** 同一管理员两次公告的最短间隔，单位为秒 */
    public static final int PUBLISH_INTERVAL_SECONDS = 10;
    /** 全部已读每批处理条数 */
    public static final int RECEIPT_BATCH_SIZE = 100;
    /** 消息列表允许的最大页码 */
    public static final int MAX_PAGE = 100000;

    /** 参数通过类名直接读取 */
    private MessageRules() {
    }
}
