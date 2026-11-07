package cn.edu.sdu.java.server.util;

/** 集中维护字块币规则，金额使用整数，价格与奖励由后端决定 */
public final class BlockcoinRules {
    /** 每个账户首次进入商城时发放的字块币 */
    public static final long INITIAL_BALANCE = 100;
    /** 当前商城的单款游戏售价 */
    public static final long GAME_PRICE = 100;
    /** 余额上限，计算奖励前检查剩余空间 */
    public static final long MAX_BALANCE = 2000000000;
    /** 一次正式关卡尝试的最长有效秒数 */
    public static final long SESSION_SECONDS = 21600;
    /** 最短时间核验采用宽松的时钟倍率上限 */
    public static final int MAX_SPEED = 8;
    /** 正常结算至少经过的秒数 */
    public static final int MIN_PLAY_SECONDS = 2;
    /** 商城一次展示的最近流水条数 */
    public static final int HISTORY_SIZE = 20;
    /** 单账户一小时内可创建的正式尝试数量 */
    public static final int RUNS_PER_HOUR = 100;
    /** 尝试频率统计的时间范围，单位为秒 */
    public static final long HOUR_SECONDS = 3600;
    /** 首个已接入游戏的站内商品入口 */
    public static final String PVZ_PATH = "/games/pvz/game.json";

    /** 此类只提供共用规则 */
    private BlockcoinRules() {
    }
}
