package billiards.world;

import java.awt.Color;

/**
 * 球的静态数据表：号码 → 组属 / 颜色，以及标准开球摆法
 *
 * <p>这是「表驱动」而不是「分支驱动」：想知道 11 号是什么颜色、属于哪一组，
 * 查表即可，没有 switch，也没有把颜色写死在渲染器里
 */
public final class BallCatalog {

    /** 主球号码 */
    public static final int CUE_ID = 0;

    /** 8 号球号码 */
    public static final int EIGHT_ID = 8;

    /** 球的总数：主球 + 15 颗目标球 */
    public static final int BALL_COUNT = 16;

    /**
     * 标准 15 球摆法，下标即球架槽位：
     * <pre>
     *              0            ← 顶球（置于置球点）
     *            1   2
     *          3   4   5        ← 槽位 4 是 8 号
     *        6   7   8   9
     *     10  11  12  13  14    ← 底排两角（10 与 14）必须一全色一花色
     * </pre>
     * 本排列满足全部约束：8 号居中、底排两角为 6（全色）与 15（花色）、
     * 1–7 与 9–15 各出现一次。合法性由 {@link #isLegalRackCorner} 与自检 P10 双重把关
     */
    private static final int[] STANDARD_RACK = {
            1,
            9, 2,
            3, 8, 10,
            11, 4, 12, 5,
            6, 13, 7, 14, 15
    };

    /** 下标 = 球号；0 号为白色主球。9–15 与 1–7 同色，靠条纹区分 */
    private static final Color[] COLORS = {
            new Color(0xFA, 0xFA, 0xF2), // 0 主球，暖白色用于区分背景反光
            new Color(0xFD, 0xD0, 0x17), //  1 黄
            new Color(0x1E, 0x5A, 0xA8), //  2 蓝
            new Color(0xD6, 0x28, 0x28), //  3 红
            new Color(0x6A, 0x2C, 0x91), //  4 紫
            new Color(0xF5, 0x7C, 0x00), //  5 橙
            new Color(0x1B, 0x7F, 0x3B), //  6 绿
            new Color(0x8B, 0x2E, 0x2E), //  7 栗
            new Color(0x1A, 0x1A, 0x1A), //  8 黑
            new Color(0xFD, 0xD0, 0x17), //  9 黄花
            new Color(0x1E, 0x5A, 0xA8), // 10 蓝花
            new Color(0xD6, 0x28, 0x28), // 11 红花
            new Color(0x6A, 0x2C, 0x91), // 12 紫花
            new Color(0xF5, 0x7C, 0x00), // 13 橙花
            new Color(0x1B, 0x7F, 0x3B), // 14 绿花
            new Color(0x8B, 0x2E, 0x2E), // 15 栗花
    };

    /** 摆球总排数 */
    public static final int RACK_ROWS = 5;

    /** 底排两角下标 */
    public static final int RACK_BACK_LEFT_INDEX = 10;
    public static final int RACK_BACK_RIGHT_INDEX = 14;

    /** 工具类只提供静态方法，不需要创建对象 */
    private BallCatalog() {
    }

    /** 根据玩家返回对应组属，未定组时返回 null */
    public static BallGroup groupOf(int ballId) {
        if (ballId == CUE_ID) {
            return BallGroup.CUE;
        }
        if (ballId == EIGHT_ID) {
            return BallGroup.EIGHT;
        }
        if (ballId < EIGHT_ID) {
            return BallGroup.SOLID;
        }
        return BallGroup.STRIPE;
    }

    /** 按球号返回球体底色，非法球号会报告参数错误 */
    public static Color colorOf(int ballId) {
        if (ballId < 0 || ballId >= COLORS.length) {
            throw new IllegalArgumentException("非法球号: " + ballId);
        }
        return COLORS[ballId];
    }

    /** 花色球（9–15）画成「白底 + 彩色横带」 */
    public static boolean isStriped(int ballId) {
        return groupOf(ballId) == BallGroup.STRIPE;
    }

    /** 标准摆法的槽位数组副本（长度 15） */
    public static int[] standardRack() {
        return STANDARD_RACK.clone();
    }

    /** 第 {@code row} 排（0-based）有 {@code row + 1} 个槽位 */
    public static int rowSize(int row) {
        return row + 1;
    }

    /** 第 {@code row} 排第 {@code col} 个槽位在数组中的下标 */
    public static int rackIndex(int row, int col) {
        return row * (row + 1) / 2 + col;
    }

    /**
     * 底排两角是否一颗全色一颗花色（WPA 摆球硬性要求）
     * 自检 P10 用它检查 {@link #STANDARD_RACK}，改摆法时不会静默违规
     */
    public static boolean isLegalRackCorner(int leftId, int rightId) {
        BallGroup ballGroup = groupOf(leftId);
        BallGroup ballGroupValue = groupOf(rightId);
        if (!ballGroup.isObjectBall() || !ballGroupValue.isObjectBall() || ballGroup.isEight() || ballGroupValue.isEight()) {
            return false;
        }
        return ballGroup != ballGroupValue;
    }
}
