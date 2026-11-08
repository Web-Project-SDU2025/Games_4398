package cn.edu.sdu.java.server.util;

import java.time.ZoneId;

/**
 * 游戏会话的公共配置参数
 *
 * 定义心跳间隔、超时时间、并发上限等规则
 * 所有游戏共用这套参数，保证体验一致
 */
public final class GameplayRules {
    /** 客户端上报时长的间隔，每5秒同步一次 */
    public static final int HEARTBEAT_MILLISECONDS = 5000;

    /** 超过45秒未同步就判定为中途离开 */
    public static final int IDLE_SECONDS = 45;

    /** 同一个人最多同时玩5个游戏，防止刷奖励 */
    public static final int MAX_ACTIVE_GAMES = 5;

    /** 允许2秒的网络延迟和时钟误差 */
    public static final long CLOCK_GRACE_MILLISECONDS = 2000;

    /** 单个游戏配置的通关奖励上限，管理员不能设超过这个数 */
    public static final long MAX_LEVEL_REWARD = 1000;

    /** 关卡编号最大值，超过100万的关卡号视为无效 */
    public static final int MAX_LEVEL_NUMBER = 1000000;

    /** 一局游戏的最长时长，超过就自动判定结束 */
    public static final long MAX_PLAY_MILLISECONDS = BlockcoinRules.SESSION_SECONDS * 1000;

    /** 会话ID使用标准UUID格式，防止伪造 */
    public static final String RUN_PATTERN = "[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}";

    /** 游戏配置文件的路径格式，只接受符合规范的路径 */
    public static final String GAME_PATH_PATTERN = "/games/[a-z][a-z0-9-]{0,39}/game\\.json";

    /** 所有游戏记录统一使用北京时间 */
    public static final ZoneId RECORD_ZONE = ZoneId.of("Asia/Shanghai");

    /** 工具类禁止实例化 */
    private GameplayRules() {
    }
}

