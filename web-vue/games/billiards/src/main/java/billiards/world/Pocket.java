package billiards.world;

/**
 * 一个袋口
 *
 * <p>袋心刻意放在台面<b>之外</b>（{@link Layout#POCKET_CENTER_OFFSET}），这正是
 * 「擦库的球不会被中袋吸进去」的机制：沿库边平行滚动时球心距库边线恒为
 * {@code BALL_RADIUS}，到中袋袋心的最近距离是 {@code 11 + 10 = 21}，恒大于中袋捕获
 * 半径 19，因此永远不满足捕获条件；只有真正朝袋口去的轨迹才会逼近到 19 以内
 *
 * <p>角袋不受这条保护，也<b>不应</b>受保护：角袋缺口处两侧库边都被削掉了，
 * 球会一直滚到角落把距离压到捕获半径以下——那正是「贴库滚进角袋」的正确行为
 *
 * <p>两类袋口的捕获半径不同（角袋 {@code 36}、中袋 {@code 19}），且这个差值是被
 * 两条互斥要求同时钉住的，不是手感调参。完整推导见 {@link Layout} 里
 * 「袋口几何的推导」那段注释
 */
public final class Pocket {

    /** 区分角袋与中袋，并集中保存各自的捕获半径和显示半径 */
    public enum Kind {
        /** 角袋（4 个） */
        CORNER("角袋", Layout.POCKET_CATCH_RADIUS_CORNER, Layout.POCKET_MOUTH_RADIUS_CORNER),
        /** 中袋/边袋（2 个，位于长边中点） */
        SIDE("中袋", Layout.POCKET_CATCH_RADIUS_SIDE, Layout.POCKET_MOUTH_RADIUS_SIDE);

        private final String labelZh;
        private final double catchRadius;
        private final double mouthRadius;

        /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
        Kind(String labelZh, double catchRadius, double mouthRadius) {
            this.labelZh = labelZh;
            this.catchRadius = catchRadius;
            this.mouthRadius = mouthRadius;
        }

        /** 返回该枚举值对应的中文名称 */
        public String labelZh() {
            return labelZh;
        }

        /** 捕获判定半径（物理） */
        public double catchRadius() {
            return catchRadius;
        }

        /** 画出来的洞口半径（视觉）。角袋画得大一些，真实球台也是这样 */
        public double mouthRadius() {
            return mouthRadius;
        }
    }

    private final int index;
    private final Vec2 center;
    private final Kind kind;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public Pocket(int index, Vec2 center, Kind kind) {
        this.index = index;
        this.center = center;
        this.kind = kind;
    }

    /** 返回袋口的固定编号 */
    public int index() {
        return index;
    }

    /** 返回袋口圆心的逻辑坐标 */
    public Vec2 center() {
        return center;
    }

    /** 返回当前袋口类型 */
    public Kind kind() {
        return kind;
    }

    /** 返回物理落袋判定使用的捕获半径 */
    public double catchRadius() {
        return kind.catchRadius();
    }

    /** 返回画面使用的袋口半径 */
    public double mouthRadius() {
        return kind.mouthRadius();
    }

    /** 球心是否落入捕获圆。比较平方距离，省一次 sqrt */
    public boolean captures(Vec2 ballCenter) {
        double radius = kind.catchRadius();
        return ballCenter.distanceSqTo(center) < radius * radius;
    }

    /** 球心到袋心的距离，供自检的边界断言（1.15× / 0.85× 捕获半径）使用 */
    public double distanceTo(Vec2 ballCenter) {
        return ballCenter.distanceTo(center);
    }
}
