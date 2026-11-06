package cn.edu.sdu.java.server.util;

import java.time.ZoneId;

/** 游戏记录和结算共用参数，金额由服务器发布配置决定 */
public final class GameplayRules {
    /** 客户端同步游玩时长的间隔，单位为毫秒 */
    public static final int HEARTBEAT_MILLISECONDS = 5000;
    /** 超过此秒数未同步的游戏按中途离开显示 */
    public static final int IDLE_SECONDS = 45;
    /** 同一账户同时运行的游戏上限 */
    public static final int MAX_ACTIVE_GAMES = 5;
    /** 网络抖动允许的计时误差，单位为毫秒 */
    public static final long CLOCK_GRACE_MILLISECONDS = 2000;
    /** 单个游戏配置允许的最高通关奖励 */
    public static final long MAX_LEVEL_REWARD = 1000;
    /** 通用协议允许的最大关卡编号 */
    public static final int MAX_LEVEL_NUMBER = 1000000;
    /** 一局时长上限，单位为毫秒 */
    public static final long MAX_PLAY_MILLISECONDS = BlockcoinRules.SESSION_SECONDS * 1000;
    /** 会话编号采用标准 UUID 文字形式 */
    public static final String RUN_PATTERN = "[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}";
    /** 已登记的站内游戏配置地址 */
    public static final String GAME_PATH_PATTERN = "/games/[a-z][a-z0-9-]{0,39}/game\\.json";
    /** 记录时间统一使用北京时间 */
    public static final ZoneId RECORD_ZONE = ZoneId.of("Asia/Shanghai");

    /** 此类通过类名提供共用参数 */
    private GameplayRules() {
    }
}
