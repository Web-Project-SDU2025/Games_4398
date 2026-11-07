package billiards.world;

/**
 * 一段库边（球台内沿的一条直线边）
 *
 * <p>关键设计：库边<b>不是四条整墙</b>，而是按袋口缺口切成若干段。这样袋口在物理上
 * 是真实存在的开口，而不是需要特判的例外——角袋处两侧库边都被削掉，球自然滚进去；
 * 中袋处库边断开，球从缺口穿过
 *
 * <p>法向量在构造时预计算，使库边碰撞判定退化成一次点积
 * （{@link #signedDistanceTo}），而不是一串「是上边还是下边」的分支
 */
public final class RailSegment {

    private final Vec2 a;
    private final Vec2 b;

    /** 指向台面内部。台内的点 {@code signedDistanceTo > 0} */
    private final Vec2 inwardNormal;

    /** 沿段方向的单位切向量，构造时预计算——库边判定在每子步的热路径上 */
    private final Vec2 tangent;

    private final double length;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public RailSegment(Vec2 segmentStart, Vec2 segmentEnd, Vec2 inwardNormal) {
        this.a = segmentStart;
        this.b = segmentEnd;
        this.inwardNormal = inwardNormal.normalized();
        this.length = segmentStart.distanceTo(segmentEnd);
        if (length > 0.0) {
            this.tangent = segmentEnd.minus(segmentStart).times(1.0 / length);
        } else {
            this.tangent = Vec2.ZERO;
        }
    }

    /** 返回库边段起点 */
    public Vec2 a() {
        return a;
    }

    /** 返回库边段终点 */
    public Vec2 b() {
        return b;
    }

    /** 返回指向台面内部的单位法向量 */
    public Vec2 inwardNormal() {
        return inwardNormal;
    }

    /** 返回向量或线段的长度 */
    public double length() {
        return length;
    }

    /** 沿段方向的单位切向量 */
    public Vec2 tangent() {
        return tangent;
    }

    /**
     * 点到库边所在直线的有符号距离：正=在台内，负=已越过库边线
     * 库边碰撞只需比较它与 {@code BALL_RADIUS}
     */
    public double signedDistanceTo(Vec2 position) {
        return position.minus(a).dot(inwardNormal);
    }

    /**
     * 点在段方向上的投影长度。用于判断接触点是否落在本段范围内
     * （即「确实撞在这段库边上」还是「从旁边的袋口缺口穿过去了」）
     */
    public double projectionOnto(Vec2 position) {
        return position.minus(a).dot(tangent);
    }

    /** 点的投影是否落在 {@code [0, length]} 内 */
    public boolean covers(Vec2 position, double tolerance) {
        double timeFraction = projectionOnto(position);
        return timeFraction >= -tolerance && timeFraction <= length + tolerance;
    }

    /** 点到库边段的最近点（超出段范围时收敛到端点） */
    public Vec2 closestPoint(Vec2 position) {
        double timeFraction = projectionOnto(position);
        if (timeFraction <= 0.0) {
            return a;
        }
        if (timeFraction >= length) {
            return b;
        }
        return a.plus(tangent.times(timeFraction));
    }

    /** 返回便于阅读的当前状态说明，供日志和调试使用 */
    @Override
    public String toString() {
        return String.format("Rail[%s -> %s, n=%s]", a, b, inwardNormal);
    }
}
