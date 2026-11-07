package billiards.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 球台几何：台面边界、6 个袋口、按袋口缺口切开的库边段
 *
 * <p>几何只在构造时算一次，之后全是只读查询——物理每子步会问很多次
 * 「这个点在哪条库边内侧」，所以这里不承担任何每帧重组几何的成本
 *
 * <p>库边分段是袋口能真实工作的原因：中袋处上库边断成两截，球可以从缺口穿过去；
 * 角袋处两侧库边各自退缩，球滚到角落时两边都不碰它，于是落进捕获圆
 * 换句话说，<b>物理引擎里没有任何一行「如果这是袋口则……」的特殊代码</b>
 */
public final class Table {

    /** 角袋袋心的对角偏移分量。10px 的偏移在 45° 方向上的两个分量各为 10/√2 */
    private static final double CORNER_OFFSET_COMPONENT =
            Layout.POCKET_CENTER_OFFSET / Math.sqrt(2.0);

    private final List<Pocket> pockets;
    private final List<RailSegment> rails;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public Table() {
        this.pockets = buildPockets();
        this.rails = buildRails();
    }

    // 构造

    /** 根据布局参数创建六个袋口，编号按顺时针固定 */
    private static List<Pocket> buildPockets() {
        final double left = Layout.PLAYFIELD_LEFT;
        final double right = Layout.PLAYFIELD_RIGHT;
        final double top = Layout.PLAYFIELD_TOP;
        final double bottom = Layout.PLAYFIELD_BOTTOM;
        final double midX = left + Layout.playfieldWidth() * 0.5;

        final double constantTerm = CORNER_OFFSET_COMPONENT; // 角袋：沿 45° 对角线向台外
        final double strength = Layout.POCKET_CENTER_OFFSET; // 中袋：沿库边法向向台外

        List<Pocket> list = new ArrayList<>(Layout.POCKET_COUNT);
        // 顺时针：左上 → 上中 → 右上 → 右下 → 下中 → 左下
        list.add(new Pocket(0, new Vec2(left - constantTerm, top - constantTerm), Pocket.Kind.CORNER));
        list.add(new Pocket(1, new Vec2(midX, top - strength), Pocket.Kind.SIDE));
        list.add(new Pocket(2, new Vec2(right + constantTerm, top - constantTerm), Pocket.Kind.CORNER));
        list.add(new Pocket(3, new Vec2(right + constantTerm, bottom + constantTerm), Pocket.Kind.CORNER));
        list.add(new Pocket(4, new Vec2(midX, bottom + strength), Pocket.Kind.SIDE));
        list.add(new Pocket(5, new Vec2(left - constantTerm, bottom + constantTerm), Pocket.Kind.CORNER));
        return Collections.unmodifiableList(list);
    }

    /**
     * 顺时针切出 6 段库边。每段用「起点 a + 终点 b + 指向台内的法向」描述
     */
    private static List<RailSegment> buildRails() {
        final double left = Layout.PLAYFIELD_LEFT;
        final double right = Layout.PLAYFIELD_RIGHT;
        final double top = Layout.PLAYFIELD_TOP;
        final double bottom = Layout.PLAYFIELD_BOTTOM;
        final double midX = left + Layout.playfieldWidth() * 0.5;

        final double gc = Layout.CUSHION_GAP_HALF_CORNER;
        final double gs = Layout.CUSHION_GAP_HALF_SIDE;

        final Vec2 center = new Vec2(0.0, 1.0);    // 上库边的内侧方向是 +y
        final Vec2 down = new Vec2(0.0, -1.0);
        final Vec2 rightIn = new Vec2(-1.0, 0.0);
        final Vec2 leftIn = new Vec2(1.0, 0.0);

        List<RailSegment> list = new ArrayList<>(6);
        list.add(new RailSegment(new Vec2(left + gc, top), new Vec2(midX - gs, top), center));
        list.add(new RailSegment(new Vec2(midX + gs, top), new Vec2(right - gc, top), center));
        list.add(new RailSegment(new Vec2(right, top + gc), new Vec2(right, bottom - gc), rightIn));
        list.add(new RailSegment(new Vec2(right - gc, bottom), new Vec2(midX + gs, bottom), down));
        list.add(new RailSegment(new Vec2(midX - gs, bottom), new Vec2(left + gc, bottom), down));
        list.add(new RailSegment(new Vec2(left, bottom - gc), new Vec2(left, top + gc), leftIn));
        return Collections.unmodifiableList(list);
    }

    // 只读查询

    /** 返回只读袋口列表，供物理和绘制共用几何 */
    public List<Pocket> pockets() {
        return pockets;
    }

    /** 返回只读库边列表，袋口位置保留真实缺口 */
    public List<RailSegment> rails() {
        return rails;
    }

    /** 按固定编号返回袋口 */
    public Pocket pocket(int index) {
        return pockets.get(index);
    }

    /**
     * 球心是否在台呢内（已按半径内缩）。注意：球可以合法地越过库边线进入袋口区域，
     * 所以这个判定用于「摆放校验」与自检不变量，而不是用来把球关在台面里
     */
    public boolean isInsidePlayfield(Vec2 ballCenter) {
        double radius = Ball.RADIUS;
        return ballCenter.x >= Layout.PLAYFIELD_LEFT + radius
                && ballCenter.x <= Layout.PLAYFIELD_RIGHT - radius
                && ballCenter.y >= Layout.PLAYFIELD_TOP + radius
                && ballCenter.y <= Layout.PLAYFIELD_BOTTOM - radius;
    }

    /**
     * 把一个点收进台面（球心可达范围）：超出边界的分量贴到最近的边界上，另一分量不动
     *
     * <p>它与 {@link #isInsidePlayfield} 是同一组边界的两面——那边问「在不在里面」，
     * 这边负责「不在里面就拉回来」。两个方法必须共用同样的内缩半径，否则会出现
     * 「收进来了却仍被判为不在台面内」的夹缝
     *
     * <p>用途只有一个：拖放主球时把虚影留在视野里的台面上。光标能走到木框、背景与
     * HUD 上，虚影若照原始光标画，玩家会看到主球被拖出球台
     */
    public Vec2 clampToPlayfield(Vec2 position) {
        double radius = Ball.RADIUS;
        double x = Math.max(Layout.PLAYFIELD_LEFT + radius,
                Math.min(Layout.PLAYFIELD_RIGHT - radius, position.x));
        double y = Math.max(Layout.PLAYFIELD_TOP + radius,
                Math.min(Layout.PLAYFIELD_BOTTOM - radius, position.y));
        return new Vec2(x, y);
    }

    /** 点是否落在外框（含木质边框）范围内。用于渲染裁剪与鼠标命中 */
    public boolean isInsideOuterFrame(double x, double y) {
        return x >= Layout.outerLeft() && x <= Layout.outerRight()
                && y >= Layout.outerTop() && y <= Layout.outerBottom();
    }

    /** 球心是否在开球区内（开球线之后，且留出半径） */
    public boolean isInBreakZone(Vec2 ballCenter) {
        return isInsidePlayfield(ballCenter)
                && ballCenter.x <= Layout.HEAD_STRING_X - Ball.RADIUS;
    }

    /**
     * 几何上能否把主球放在 {@code pos}：在台面内、不与任何在台球重叠、不陷在袋口里
     *
     * <p>只做几何判断，不含「是否限制在开球线后」这类规则约束——那部分在
     * {@code rules.PlacementValidator} 里叠加。这样几何只有一份实现
     */
    public boolean canPlaceBall(List<Ball> balls, Vec2 pos, int ignoreBallId) {
        if (!isInsidePlayfield(pos)) {
            return false;
        }
        double minGap = 2.0 * Ball.RADIUS + PhysicsConfig.PLACEMENT_GAP;
        double minGapSq = minGap * minGap;
        for (Ball ball : balls) {
            if (!ball.isOnTable() || ball.id() == ignoreBallId) {
                continue;
            }
            if (ball.position().distanceSqTo(pos) < minGapSq) {
                return false;
            }
        }
        // 不同袋口的捕获半径不同（角袋大于中袋），所以逐个取，不能用统一常量
        for (Pocket pocket : pockets) {
            double minPocket = pocket.catchRadius() + Ball.RADIUS;
            if (pocket.center().distanceSqTo(pos) < minPocket * minPocket) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从 {@code preferred} 出发找最近的合法落点——8 号开球落袋重置、飞球重新置位都靠它
     *
     * <p>先试原位，不行就以固定步长一圈圈向外扫。扫描是确定性的（同样的输入必然给出
     * 同样的落点），所以自检可复现；它不在物理热路径上，用 cos/sin 无所谓
     */
    public Vec2 findFreeSpot(List<Ball> balls, Vec2 preferred, int ignoreBallId) {
        if (canPlaceBall(balls, preferred, ignoreBallId)) {
            return preferred;
        }
        final double step = Ball.RADIUS;
        final int maxRings = 160;
        for (int ring = 1; ring <= maxRings; ring = ring + 1) {
            double radius = ring * step;
            int samples = Math.max(8, (int) Math.round(2.0 * Math.PI * radius / step));
            for (int sampleIndex = 0; sampleIndex < samples; sampleIndex = sampleIndex + 1) {
                double ang = 2.0 * Math.PI * sampleIndex / samples;
                Vec2 position = new Vec2(preferred.x + radius * Math.cos(ang), preferred.y + radius * Math.sin(ang));
                if (canPlaceBall(balls, position, ignoreBallId)) {
                    return position;
                }
            }
        }
        // 理论上不可达：台面能容纳的球远多于 16 颗。返回原位以免抛异常打断比赛
        return preferred;
    }
}
