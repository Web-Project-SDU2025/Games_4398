package cn.edu.sdu.java.server.util;

import java.time.ZoneId;

/**
 * 管理查询规则常量类
 *
 * 管理页面共用查询范围
 * 分页与日期边界在服务端统一校验
 */
public final class AdminQueryRules {
    /** 收支列表的默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 单次查询允许的最大条数 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 流水原因搜索的最大字符数 */
    public static final int MAX_SEARCH_LENGTH = 100;

    /** 余额调整和游戏撤回的原因最多字符数 */
    public static final int OPERATION_REASON_LIMIT = 60;

    /** 日期筛选按北京时间计算当天起止 */
    public static final ZoneId DATE_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 私有构造函数
     * 查询规则由类名读取
     */
    private AdminQueryRules() {
    }
}
