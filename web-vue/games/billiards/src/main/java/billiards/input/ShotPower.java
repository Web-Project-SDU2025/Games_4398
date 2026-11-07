package billiards.input;

import billiards.world.Layout;
import billiards.world.PhysicsConfig;

/**
 * 力度换算：把「手往后拖了多远」折成 0–1 的力度，再把力度折成初速
 *
 * <h2>为什么不是按住时长</h2>
 * 按住时长做力度，读数一直在动：玩家看到力度条到了想要的位置再松手，条已经又涨了一截，
 * 误差正比于反应时间且松手之后无法修正。改成拉距后力度是一个<b>位置</b>——拉到哪就是多少，
 * 松手之前可以来回微调。常量与完整理由见 {@link Layout#PULL_MAX_PX}
 *
 * <p>传进来的 {@code pullPx} 是手离<b>按下点</b>的距离（不是沿某个轴的投影，也不是离球心
 * 的距离），理由见 {@link Layout#PULL_MAX_PX} 那段推导——这一层只负责换算，不关心它是
 * 怎么量出来的
 *
 * <h2>为什么整类都是静态纯函数</h2>
 * 「拉距 → 力度 → 初速」是本工程唯一的「输入 → 数值」映射，它错了会让所有击球系统性
 * 偏重或偏轻，而演示时根本判不出来。做成无状态的纯函数之后，「拉了 80px 该是 50% 力度」
 * 变成一行断言；反解 {@link #pullFor(double)} 与正解互为逆函数，本身又是一条可断言的性质
 *
 * <p>谁在拉、拉到哪，那是 {@code MatchSession} 的状态，不是这里的——这里只管换算
 */
public final class ShotPower {

    /** 死区到满力之间的有效行程（px） */
    private static final double SPAN = Layout.PULL_MAX_PX - Layout.PULL_DEAD_ZONE_PX;

    /** 工具类只提供静态方法，不需要创建对象 */
    private ShotPower() {
    }

    /**
     * 拉距 → 力度
     *
     * <p>死区之内一律 0（松手即取消），之后线性涨到 1 并在 {@link Layout#PULL_MAX_PX}
     * 处封顶。负数与 NaN 都落到 0：距离本身不该为负，但这一层是「输入 → 数值」的最后一道
     * 关口，光标没动过、坐标还没初始化之类的边界情况都该读成「没拉」，而不是让 NaN 顺着
     * 力度条一路渗进初速
     */
    public static double powerFor(double pullPx) {
        if (!(pullPx > Layout.PULL_DEAD_ZONE_PX)) {
            return 0.0;
        }
        return clamp01((pullPx - Layout.PULL_DEAD_ZONE_PX) / SPAN);
    }

    /** {@link #powerFor(double)} 的反解：给定力度，要拉多远。自检拿它做往返断言 */
    public static double pullFor(double power) {
        return Layout.PULL_DEAD_ZONE_PX + clamp01(power) * SPAN;
    }

    /**
     * 力度 → 初速（px/s）
     *
     * <p>{@code power = 0} 映射到 {@link PhysicsConfig#MIN_SHOT_SPEED} 而不是 0：只要能
     * 拉出死区就该是一记真实的轻推，而不是「什么都没发生、状态却已切到球在飞」
     */
    public static double speedFor(double power) {
        return PhysicsConfig.MIN_SHOT_SPEED
                + clamp01(power) * (PhysicsConfig.MAX_SHOT_SPEED - PhysicsConfig.MIN_SHOT_SPEED);
    }

    /** 将传入数值限制在零到一之间，供力度或音量换算使用 */
    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        return Math.min(value, 1.0);
    }
}
