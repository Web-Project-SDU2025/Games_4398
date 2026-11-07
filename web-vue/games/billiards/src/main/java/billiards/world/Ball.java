package billiards.world;

/**
 * 一颗球
 *
 * <p>{@code id} 与 {@code group} 是 {@code final}：球号一旦定下就不会变，组属由
 * {@link BallCatalog} 派生。会变的是位置、速度和「是否还在台上」
 *
 * <p>位置与速度是<b>可变字段</b>而非不可变对象，因为物理每子步都要改它们；
 * 但 {@link Vec2} 本身不可变，所以赋值永远是整体替换，不会出现两个球共享一个
 * 向量实例的别名 bug
 */
public final class Ball {

    /** 球半径，来自布局真源 */
    public static final double RADIUS = Layout.BALL_RADIUS;

    private final int id;
    private final BallGroup group;

    private Vec2 position;
    private Vec2 velocity;
    private boolean onTable;
    private double spinX;
    private double spinY;
    private double spinZ;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public Ball(int ballId, Vec2 position) {
        this.id = ballId;
        this.group = BallCatalog.groupOf(ballId);
        this.position = position;
        this.velocity = Vec2.ZERO;
        this.onTable = true;
        this.spinX = 0.0;
        this.spinY = 0.0;
        this.spinZ = 0.0;
    }

    // ---- 身份 ----

    /** 返回球号 */
    public int id() {
        return id;
    }

    /** 返回当前对象所属的球组或检查分组 */
    public BallGroup group() {
        return group;
    }

    /** 返回当前球是否为主球 */
    public boolean isCueBall() {
        return id == BallCatalog.CUE_ID;
    }

    // ---- 状态 ----

    /** 返回当前球心位置，位置向量本身不可变 */
    public Vec2 position() {
        return position;
    }

    /** 设置球心位置，参数使用游戏逻辑坐标 */
    public void setPosition(Vec2 position) {
        this.position = position;
    }

    /** 返回当前平动速度，单位为逻辑像素每秒 */
    public Vec2 velocity() {
        return velocity;
    }

    /** 设置平动速度，不自动改变角速度以保留碰撞后的旋转 */
    public void setVelocity(Vec2 velocity) {
        this.velocity = velocity;
    }

    /** 当前绕 x 轴的角速度，单位是弧度每秒 */
    public double spinX() {
        return spinX;
    }

    /** 当前绕 y 轴的角速度，单位是弧度每秒 */
    public double spinY() {
        return spinY;
    }

    /** 当前绕竖直轴的角速度，单位是弧度每秒 */
    public double spinZ() {
        return spinZ;
    }

    /** 设置三个方向的角速度，供碰撞与台呢摩擦更新旋转 */
    public void setSpin(double spinX, double spinY, double spinZ) {
        this.spinX = spinX;
        this.spinY = spinY;
        this.spinZ = spinZ;
    }

    /** 返回球是否仍参与台面模拟 */
    public boolean isOnTable() {
        return onTable;
    }

    /** 设置球的在台状态，离场时同时清空平动和旋转 */
    public void setOnTable(boolean onTable) {
        this.onTable = onTable;
        if (!onTable) {
            this.velocity = Vec2.ZERO;
            this.spinX = 0.0;
            this.spinY = 0.0;
            this.spinZ = 0.0;
        }
    }

    // ---- 便捷查询 ----

    /** 返回平动速度的大小 */
    public double speed() {
        return velocity.length();
    }

    /** 返回球是否仍有平动速度 */
    public boolean isMoving() {
        return speed() > 0.0;
    }

    /** 球心速度与转速共同决定球底相对台呢的滑动速度 */
    public Vec2 surfaceSlipVelocity() {
        double slipX = velocity.x - RADIUS * spinY;
        double slipY = velocity.y + RADIUS * spinX;
        return new Vec2(slipX, slipY);
    }

    /** 把球的平动速度与角速度调整为无滑动滚动 */
    public void matchRollingSpin() {
        spinX = -velocity.y / RADIUS;
        spinY = velocity.x / RADIUS;
    }

    /** 清零速度与旋转，模拟超时或重新摆球时调用 */
    public void stop() {
        this.velocity = Vec2.ZERO;
        this.spinX = 0.0;
        this.spinY = 0.0;
        this.spinZ = 0.0;
    }

    /** 位置加一个位移，返回新位置（不修改本球） */
    public Vec2 positionAfter(Vec2 displacement) {
        return position.plus(displacement);
    }

    /** 返回便于阅读的当前状态说明，供日志和调试使用 */
    @Override
    public String toString() {
        String tableState = "";
        if (!onTable) {
            tableState = " [已落袋]";
        }
        return String.format("Ball#%d(%s) @%s v=%s%s",
                id, group.labelZh(), position, velocity, tableState);
    }
}
