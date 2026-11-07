package cn.edu.sdu.java.server.util;

import java.util.List;

/** 台球奖励只由服务器维护，玩家一代表登录账户，玩家二为本地对手 */
public final class BilliardsRewards {
    /** 唯一允许申请台球成就奖励的商品入口 */
    public static final String GAME_PATH = "/games/billiards/game.json";
    /** 单杆与上一杆至少间隔的真实秒数，排除连续瞬时提交 */
    public static final int MIN_SHOT_SECONDS = 2;
    /** 每局最多接收的击球次数，防止错误客户端无限增长状态 */
    public static final int MAX_SHOTS = 1000;
    /** 稳定的奖励编号，金额与中文说明按同一位置读取 */
    public static final List<String> IDS = List.of("opening", "double", "bank", "run", "triple", "kick", "win");
    /** 奖励显示名称，与账本收入原因一致 */
    public static final List<String> TITLES = List.of("开门红", "一杆双收", "翻袋好球", "连下三城", "一杆三收", "勾球进袋", "获胜");
    /** 各项奖励金额，单位为字块币 */
    public static final List<Long> AMOUNTS = List.of(1L, 4L, 6L, 8L, 11L, 11L, 20L);

    /** 规则类通过静态成员提供参数 */
    private BilliardsRewards() {
    }
}
