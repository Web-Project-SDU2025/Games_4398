package cn.edu.sdu.java.server.util;

import java.util.List;

/**
 * 台球成就奖励配置
 *
 * 所有奖励金额和规则由服务器统一维护
 * 玩家一代表当前登录账户，玩家二是本地对手（不联网）
 */
public final class BilliardsRewards {
    /** 只有这个游戏路径可以申请台球成就奖励 */
    public static final String GAME_PATH = "/games/billiards/game.json";

    /** 每两杆之间至少间隔2秒，防止连续瞬间提交作弊 */
    public static final int MIN_SHOT_SECONDS = 2;

    /** 每局最多接收1000杆，防止异常客户端无限增长状态 */
    public static final int MAX_SHOTS = 1000;

    /** 成就ID列表，金额和名称按相同位置对应 */
    public static final List<String> IDS = List.of("opening", "double", "bank", "run", "triple", "kick", "win");

    /** 成就显示名称，会显示在账本收支记录里 */
    public static final List<String> TITLES = List.of("开门红", "一杆双收", "翻袋好球", "连下三城", "一杆三收", "勾球进袋", "获胜");

    /** 各项成就奖励金额，单位是字块币 */
    public static final List<Long> AMOUNTS = List.of(1L, 4L, 6L, 8L, 11L, 11L, 20L);

    /** 工具类禁止实例化 */
    private BilliardsRewards() {
    }
}
}
