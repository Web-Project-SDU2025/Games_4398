package billiards.physics;

import billiards.world.Vec2;

/**
 * 纯几何工具：线段最近点、射线与圆/线段的首次相交
 *
 * <p>这些是「没有状态、没有副作用、给定输入必有确定输出」的函数，因此可以独立断言
 * 瞄准预览（画虚影球、算首个接触球、画分离线）全部建立在它们之上，物理本身不依赖
 */
public final class CollisionMath {

    /** 工具类只提供静态方法，不需要创建对象 */
    private CollisionMath() {
    }

    /** 点 {@code p} 到线段 {@code ab} 的最近点。超出段范围时收敛到端点 */
    public static Vec2 closestPointOnSegment(Vec2 position, Vec2 segmentStart, Vec2 segmentEnd) {
        Vec2 segmentDirection = segmentEnd.minus(segmentStart);
        double lengthSquared = segmentDirection.lengthSq();
        if (lengthSquared < 1e-18) {
            return segmentStart;
        }
        double timeFraction = position.minus(segmentStart).dot(segmentDirection) / lengthSquared;
        if (timeFraction <= 0.0) {
            return segmentStart;
        }
        if (timeFraction >= 1.0) {
            return segmentEnd;
        }
        return segmentStart.plus(segmentDirection.times(timeFraction));
    }

    /**
     * 射线 {@code origin + t * dir}（{@code t >= 0}）与圆的<b>首次</b>相交参数 t
     *
     * <p>返回 {@code NaN} 表示不相交或只在反方向相交。若射线起点已在圆内，返回 0
     * {@code dir} 不必是单位向量，返回的 t 以 {@code dir} 的长度为尺度
     */
    public static double rayCircleFirstHitTime(Vec2 origin, Vec2 direction, Vec2 center, double radius) {
        Vec2 centerToOrigin = origin.minus(center);
        double quadraticTerm = direction.lengthSq();
        if (quadraticTerm < 1e-18) {
            return Double.NaN;
        }
        double linearTerm = 2.0 * centerToOrigin.dot(direction);
        double constantTerm = centerToOrigin.lengthSq() - radius * radius;

        if (constantTerm <= 0.0) {
            return 0.0; // 起点已在圆内
        }
        double discriminant = linearTerm * linearTerm - 4.0 * quadraticTerm * constantTerm;
        if (discriminant < 0.0) {
            return Double.NaN;
        }
        double root = Math.sqrt(discriminant);
        // 两个根；取最小的非负根
        double t1 = (-linearTerm - root) / (2.0 * quadraticTerm);
        double t2 = (-linearTerm + root) / (2.0 * quadraticTerm);
        if (t1 >= 0.0) {
            return t1;
        }
        if (t2 >= 0.0) {
            return t2;
        }
        return Double.NaN;
    }

    /**
     * 射线 {@code origin + t * dir}（{@code t >= 0}）与线段 {@code ab} 的交点参数 t
     * 不相交返回 {@code NaN}。{@code dir} 为单位向量时，t 即距离
     */
    public static double raySegmentHitTime(Vec2 origin, Vec2 direction, Vec2 segmentStart, Vec2 segmentEnd) {
        Vec2 segmentDirection = segmentEnd.minus(segmentStart);
        double denom = direction.cross(segmentDirection);
        if (Math.abs(denom) < 1e-12) {
            return Double.NaN; // 平行
        }
        Vec2 originToStart = segmentStart.minus(origin);
        double timeFraction = originToStart.cross(segmentDirection) / denom;      // 射线参数
        double segmentFraction = originToStart.cross(direction) / denom;     // 线段参数
        if (timeFraction < 0.0 || segmentFraction < 0.0 || segmentFraction > 1.0) {
            return Double.NaN;
        }
        return timeFraction;
    }

    /**
     * 射线与一条库边段的首次相交参数 t。库边只在「面向球的那一侧」起作用，
     * 因此还要求交点在法向的反侧（即球不能从台外撞回来）
     */
    public static double rayRailHitTime(Vec2 origin, Vec2 direction, Vec2 segmentStart, Vec2 segmentEnd, Vec2 inwardNormal) {
        double timeFraction = raySegmentHitTime(origin, direction, segmentStart, segmentEnd);
        if (Double.isNaN(timeFraction)) {
            return Double.NaN;
        }
        if (direction.dot(inwardNormal) >= 0.0) {
            return Double.NaN; // 沿法向朝外走，不会撞上这段库边的内侧
        }
        return timeFraction;
    }
}
