package cn.edu.sdu.java.server.util;

/**
 * 消息规则配置
 *
 * 统一管理消息内容限制和发布频率约束
 * 分页参数复用管理查询规则
 */
public final class MessageRules {
    /** 消息业务编号的最大长度 */
    public static final int ID_LIMIT = 160;

    /** 公告标题最多字符数 */
    public static final int TITLE_LIMIT = 80;

    /** 公告正文最多字符数 */
    public static final int CONTENT_LIMIT = 2000;

    /** 反馈状态：管理员确认会修复 */
    public static final String FEEDBACK_WILL_FIX = "will_fix";

    /** 反馈状态：管理员确认不会修复 */
    public static final String FEEDBACK_WILL_NOT_FIX = "will_not_fix";

    /** 反馈状态：问题已解决 */
    public static final String FEEDBACK_RESOLVED = "resolved";

    /** 反馈状态：尚未处理 */
    public static final String FEEDBACK_PENDING = "pending";

    /** 同一管理员两次公告的最短间隔（秒），防止刷屏 */
    public static final int PUBLISH_INTERVAL_SECONDS = 10;

    /** 全部标记已读时每批处理的消息条数 */
    public static final int RECEIPT_BATCH_SIZE = 100;

    /** 消息列表允许的最大页码 */
    public static final int MAX_PAGE = 100000;

    /** 工具类禁止实例化 */
    private MessageRules() {
    }
}
