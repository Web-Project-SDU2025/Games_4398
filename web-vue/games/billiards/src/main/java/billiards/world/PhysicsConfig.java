package billiards.world;

/** 集中保存步进、摩擦和碰撞参数，参数是模型默认值，调整后需要重新验证 */
public final class PhysicsConfig {

    // ---- 时间步进 ----

    /** 固定模拟步长（毫秒）。200Hz —— 子步通常只需 2 步，16 个球的开销可以忽略 */
    public static final double FIXED_STEP_MS = 5.0;

    /** 一帧最多追赶多少个固定步，防止卡顿后「死亡螺旋」 */
    public static final int MAX_STEPS_PER_FRAME = 5;

    /** 渲染目标帧率，仅用于决定循环里的 sleep 时长 */
    public static final int TARGET_FPS = 60;

    // ---- 击球 ----

    /** 满力初速（px/s）。台长 880px 代表 2.54m，满力约合 8.7 m/s。需实测调 */
    public static final double MAX_SHOT_SPEED = 3000.0;

    /** 最小有效初速（px/s），避免「点一下等于没打」 */
    public static final double MIN_SHOT_SPEED = 200.0;

    /*
     * 力度本身不在这里：它由「往后拉了多远」决定，常量在 Layout（PULL_MAX_PX /
     * PULL_DEAD_ZONE_PX —— 那是屏幕像素量，不是物理量）。本类只留力度→初速这一段的端点
     */

    // ---- 摩擦 ----

    /** 台呢与球之间的动摩擦系数 */
    public static final double SLIDING_FRICTION = 0.18;

    /** 球台重力加速度，按逻辑画布比例换算成每秒像素平方 */
    public static final double TABLE_GRAVITY = 3392.0;

    /** 滚动阻力系数，决定球进入纯滚动后减速的快慢 */
    public static final double ROLLING_RESISTANCE = 0.03;

    /** 球的切向碰撞摩擦系数，控制碰撞时的旋转传递 */
    public static final double BALL_CONTACT_FRICTION = 0.08;

    /** 球与库边的切向摩擦系数，影响反弹方向与侧旋 */
    public static final double CUSHION_FRICTION = 0.14;

    /** 刚体球的转动惯量系数，实心球为质量乘半径平方再乘此值 */
    public static final double BALL_INERTIA_FACTOR = 0.4;

    /** 速度低于此值直接归零（px/s），让 {@code allAtRest()} 成为良定义条件 */
    public static final double STOP_SPEED = 0.05;

    // ---- 碰撞 ----

    /** 球球恢复系数，低于 1 表示碰撞会损失一部分动能 */
    public static final double BALL_RESTITUTION = 0.94;

    /** 库边恢复系数。明显小于球球，否则球会在库边之间弹个没完。需实测调 */
    public static final double CUSHION_RESTITUTION = 0.80;

    // ---- 防穿透 ----

    /**
     * 单个子步允许的最大位移（px）
     *
     * <p>这是整个引擎最重要的一条不变量：它必须 ≤ 球半径的一半。在此前提下不需要
     * 扫掠/连续碰撞检测——球球检测的触发距离是 22px（2R），是它的 2.75 倍；
     * 袋口捕获圆的弦长至少 29.5px，采样间隔 ≤ 8px 时至少 3 个采样点落在圆内
     */
    public static final double MAX_SUBSTEP_DISPLACEMENT = 8.0;

    /**
     * 子步数上限。注意 {@code PhysicsEngine} <b>不会</b>静默截断超限的子步数，
     * 而是先把所有速度按比例缩放到 {@code MAX_SUBSTEPS * MAX_SUBSTEP_DISPLACEMENT / dt}——
     * 这样「每子步位移 ≤ 8px」才是真正的不变量而非一厢情愿的期望
     */
    public static final int MAX_SUBSTEPS = 8;

    /**
     * 球球重叠的松弛迭代次数
     *
     * <p>一帧内 15 颗相切球会互相挤压，而「完全分离」的位置修正<b>不满足交换律</b>：
     * 把球 A 从 B 身边推开，可能正好把它推进 C 里。所以只能靠松弛反复收敛，
     * 有限轮数下必然残留一点重叠。轮数越多残余越小，代价是 O(球对 × 轮数)
     */
    public static final int CONTACT_ITERATIONS = 6;

    // ---- 静止与超时 ----

    /**
     * 一杆的模拟时长硬上限（毫秒）
     *
     * <p>注意是<b>模拟</b>毫秒而非墙钟毫秒——用墙钟会让结果依赖机器负载，破坏确定性
     * 达到上限时规则引擎会强制停止并结算，避免一杆永远不能结束
     */
    public static final double SHOT_TIMEOUT_SIM_MS = 60000.0;

    // ---- 摆放 ----

    /** 摆球时球心间距相对 2R 的额外间隙，防止 t=0 时相切球互相重叠 */
    public static final double RACK_GAP = 0.2;

    /** 自由球放置校验要求的最小额外间隙 */
    public static final double PLACEMENT_GAP = 0.5;

    /** 通用浮点容差 */
    public static final double EPS = 1e-9;

    /** 配置类只保存参数与派生计算，不需要创建对象 */
    private PhysicsConfig() {
    }

    /** 根据动摩擦系数和重力返回滑动减速度，单位为逻辑像素每秒平方 */
    public static double slidingFrictionDeceleration() {
        return SLIDING_FRICTION * TABLE_GRAVITY;
    }

    /** 根据滚动阻力系数返回滚动减速度，单位为逻辑像素每秒平方 */
    public static double rollingResistanceDeceleration() {
        return ROLLING_RESISTANCE * TABLE_GRAVITY;
    }
}
