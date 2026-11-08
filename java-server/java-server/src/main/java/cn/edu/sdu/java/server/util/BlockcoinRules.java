package cn.edu.sdu.java.server.util;

/**
 * 字块币经济系统的核心规则配置
 *
 * 这里集中管理整个游戏平台的虚拟货币参数，金额全部使用整数避免浮点精度问题
 * 价格和奖励金额由后端统一控制，前端只负责展示，这样可以防止客户端篡改金额
 */
public final class BlockcoinRules {
    /** 新用户首次打开商城时的见面礼，让大家能直接体验购买流程 */
    public static final long INITIAL_BALANCE = 100;

    /** 游戏商城中每款游戏的统一售价，目前所有游戏同价 */
    public static final long GAME_PRICE = 100;

    /** 账户余额的天花板，设置上限是为了防止奖励累积溢出，计算奖励前会先检查剩余空间 */
    public static final long MAX_BALANCE = 2000000000;

    /** 单次游戏会话的最长有效时长（6小时），超时后会话自动失效，避免挂机刷奖励 */
    public static final long SESSION_SECONDS = 21600;

    /**
     * 关卡最短时长校验的时钟倍率容差
     * 考虑到有些玩家开加速器或者系统时钟偶尔会快一点，允许8倍的宽松度
     * 比如关卡要求最少30秒，实际只玩了25秒也能通过（因为允许快进）
     */
    public static final int MAX_SPEED = 8;

    /** 正常结算要求的最短游玩时间，至少玩2秒才算有效，防止秒退刷奖励 */
    public static final int MIN_PLAY_SECONDS = 2;

    /** 商城页面展示的最近流水记录数，只显示最近20条，避免一次加载太多历史数据 */
    public static final int HISTORY_SIZE = 20;

    /** 单个账户一小时内允许开局的次数上限，防止恶意刷会话 */
    public static final int RUNS_PER_HOUR = 100;

    /** 频率统计的时间窗口（1小时），用于计算RUNS_PER_HOUR */
    public static final long HOUR_SECONDS = 3600;

    /** 首个接入平台的游戏入口路径，用于初始化商城时确保至少有一个商品 */
    public static final String PVZ_PATH = "/games/pvz/game.json";

    /** 工具类，全是静态常量，禁止实例化 */
    private BlockcoinRules() {
    }
}
