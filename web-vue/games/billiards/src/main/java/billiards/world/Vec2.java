package billiards.world;

/**
 * 不可变二维向量
 *
 * <p>刻意做成不可变：物理求解器需要频繁读写球的位置与速度，若 {@code Vec2} 可变，
 * 极易出现「两个球共享同一个向量实例、改一个把另一个也改了」的别名 bug
 * 不可变后 {@code ball.position = a.plus(b)} 这种写法天然安全，代价只是每步多几次
 * 小对象分配——16 个球的规模完全无感
 *
 * <p>所有距离一律用 {@code Math.sqrt(dx*dx + dy*dy)} 而非 {@code Math.hypot}：
 * sqrt 是 IEEE-754 正确舍入的，hypot 只保证 1 ulp 且各 JDK 实现有差异，
 * 而本工程要求同一杆在实时循环与自检中跑出位一致的结果
 */
public final class Vec2 {

    public static final Vec2 ZERO = new Vec2(0.0, 0.0);

    public final double x;
    public final double y;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public Vec2(double x, double y) {
        this.x = x;
        this.y = y;
    }

    // ---- 基本运算 ----

    /** 返回与给定向量相加的新向量，不修改原对象 */
    public Vec2 plus(Vec2 other) {
        return new Vec2(x + other.x, y + other.y);
    }

    /** 返回减去给定向量后的新向量，不修改原对象 */
    public Vec2 minus(Vec2 other) {
        return new Vec2(x - other.x, y - other.y);
    }

    /** 返回按给定倍数缩放的新向量 */
    public Vec2 times(double scale) {
        return new Vec2(x * scale, y * scale);
    }

    /** 返回与给定向量的点积，用于计算方向上的速度或距离 */
    public double dot(Vec2 other) {
        return x * other.x + y * other.y;
    }

    /** 二维「叉积」的标量形式 z 分量，用于判断左右与线段求交 */
    public double cross(Vec2 other) {
        return x * other.y - y * other.x;
    }

    // ---- 长度与距离 ----

    /** 返回长度平方，比较距离时可避免额外开方 */
    public double lengthSq() {
        return x * x + y * y;
    }

    /** 返回向量或线段的长度 */
    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    /** 返回当前点到给定点的直线距离 */
    public double distanceTo(Vec2 other) {
        double deltaX = x - other.x;
        double deltaY = y - other.y;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    /** 返回到给定点的距离平方 */
    public double distanceSqTo(Vec2 other) {
        double deltaX = x - other.x;
        double deltaY = y - other.y;
        return deltaX * deltaX + deltaY * deltaY;
    }

    // ---- 派生向量 ----

    /** 单位化；零向量返回 {@link #ZERO} 而不是 NaN */
    public Vec2 normalized() {
        double length = length();
        if (length < PhysicsConfig.EPS) {
            return ZERO;
        }
        return new Vec2(x / length, y / length);
    }

    /** 缩放到指定长度；零向量返回 {@link #ZERO} */
    public Vec2 withLength(double newLength) {
        double length = length();
        if (length < PhysicsConfig.EPS) {
            return ZERO;
        }
        double scale = newLength / length;
        return new Vec2(x * scale, y * scale);
    }

    /** 逆时针旋转 {@code radians} 弧度。只用于渲染与由角度生成初速度，不进积分循环 */
    public Vec2 rotated(double radians) {
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double rotatedX = x * cosine - y * sine;
        double rotatedY = x * sine + y * cosine;
        return new Vec2(rotatedX, rotatedY);
    }

    /** 左法向（逆时针 90°） */
    public Vec2 perpendicular() {
        return new Vec2(-y, x);
    }

    /** 返回便于阅读的当前状态说明，供日志和调试使用 */
    @Override
    public String toString() {
        return String.format("(%.4f, %.4f)", x, y);
    }
}
