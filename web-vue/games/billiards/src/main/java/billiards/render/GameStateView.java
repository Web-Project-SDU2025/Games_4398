package billiards.render;

import billiards.world.BallCatalog;

/**
 * 一帧画面所需的全部状态的<b>不可变快照</b>
 *
 * <h2>它是两个线程之间唯一的通道</h2>
 * 布局是「模拟线程写、EDT 只读」。模拟线程每帧造一个新的 {@code GameStateView} 并写进一个
 * {@code volatile} 字段；EDT 在 {@code paintComponent} 里读那个字段。除此之外两边不共享
 * 任何可变对象——连球的位置都不共享
 *
 * <p>M6 之后快照里还挂着一份 {@link MatchView}（谁在打、双方托盘、判定横幅）。它<b>不是</b>
 * 例外：{@code MatchView} 同样是构造后不可变的，数组字段也同样是私有的。判断标准是
 * 「拿到引用之后还能不能改到里面的东西」，不是「这个字段有几个」
 *
 * <p>这条通道成立依赖两件事，缺一不可：
 * <ol>
 *   <li><b>volatile 建立的 happens-before</b>：写 volatile 之前的全部写操作，对随后读到
 *       该 volatile 值的线程可见。所以 EDT 一定看得到「16 颗球都已就位」的那个状态，
 *       而不是构造到一半的中间态</li>
 *   <li><b>深不可变</b>：光有 volatile 不够。若快照里挂着一个可变的 {@code Ball} 数组，
 *       EDT 拿到引用之后模拟线程照样能改它——volatile 只保证「发布那一刻之前的写」可见，
 *       不保证「之后的写」不发生。所以 {@link BallView} 必须使用只读字段、数组必须私有且
 *       永不外泄</li>
 * </ol>
 * 两条合起来，等于把「共享可变状态」这件事从并发里彻底删掉了，而不是靠加锁去管理它
 *
 * <h2>为什么不把数组交出去</h2>
 * 对外只给 {@link #ballCount()} 与 {@link #ball(int)}，不返回数组或 {@code List}
 * 返回数组意味着任何拿到它的代码都能往里写——而数组又是本对象的一个字段，于是
 * 「不可变」当场失效。<b>不可变性必须是结构上做不到被破坏，而不是文档里写着请勿修改</b>
 */
public final class GameStateView {

    private final BallView[] balls;
    private final int ballsOnTable;
    private final AimView aim;
    private final double cueX;
    private final double cueY;
    private final double power;
    private final boolean shotInFlight;
    private final String status;
    private final MatchView match;
    private final String achievementNotice;
    private final String achievementSummary;

    /**
     * 不带对局信息的一帧（自由练习式的布局、以及只关心球位的无头夹具）
     *
     * <p>保留这个重载是有意的：{@code CheckImages} 与 {@code UIChecks} 里有一批夹具只造
     * 球位、不关心谁在打哪一组，让它们统统去构造一个 {@link MatchView} 只会把「这份夹具
     * 到底在测什么」搅浑。对局信息缺省为 {@link MatchView#NONE}，渲染器读到它就是不画
     * 托盘与徽章——那是「合法但没有对局」的表示，不是零值凑合
     */
    public GameStateView(BallView[] balls, AimView aim, double power,
                         boolean shotInFlight, String status) {
        this(balls, aim, power, shotInFlight, status, MatchView.NONE);
    }

    /**
     * @param balls  本帧全部 16 颗球的状态（含已落袋的，靠 {@code onTable} 区分）
     *               构造时会拷贝一份，调用方之后改自己的数组不影响本对象
     * @param aim    瞄准线；不画时传 {@link AimView#NONE}
     * @param power  当前力度 0–1，供力度条显示
     * @param status 状态行文案
     * @param match  对局信息（谁在打、双方托盘、横幅）；没有时传 {@link MatchView#NONE}
     */
    public GameStateView(BallView[] balls, AimView aim, double power,
                         boolean shotInFlight, String status, MatchView match) {
        this(balls, aim, power, shotInFlight, status, match, "", "");
    }

    /** 保存本帧状态和已生成的成就文字，字符串只读，可安全交给界面线程 */
    public GameStateView(BallView[] balls, AimView aim, double power,
                         boolean shotInFlight, String status, MatchView match,
                         String achievementNotice, String achievementSummary) {
        this.balls = balls.clone();
        if (aim == null) {
            this.aim = AimView.NONE;
        } else {
            this.aim = aim;
        }
        this.power = power;
        this.shotInFlight = shotInFlight;
        if (status == null) {
            this.status = "";
        } else {
            this.status = status;
        }
        if (match == null) {
            this.match = MatchView.NONE;
        } else {
            this.match = match;
        }

        int onTable = 0;
        double centerX = Double.NaN;
        double centerY = Double.NaN;
        for (BallView ballView : this.balls) {
            if (ballView.onTable()) {
                onTable = onTable + 1;
                if (ballView.id() == BallCatalog.CUE_ID) {
                    centerX = ballView.x();
                    centerY = ballView.y();
                }
            }
        }
        this.ballsOnTable = onTable;
        this.cueX = centerX;
        this.cueY = centerY;
        this.achievementNotice = achievementNotice;
        this.achievementSummary = achievementSummary;
    }

    /** 返回本帧新解锁的成就提示，空串表示不绘制 */
    public String achievementNotice() {
        return achievementNotice;
    }

    /** 返回胜者的本局成就标题，空串表示没有成就 */
    public String achievementSummary() {
        return achievementSummary;
    }

    /** 什么都不画的空态。构造期与无头检查用它充当「合法但没有内容」的初始值 */
    public static GameStateView empty() {
        return new GameStateView(new BallView[0], AimView.NONE, 0.0, false, "");
    }

    /** 返回快照中的球数量，包含已落袋的球 */
    public int ballCount() {
        return balls.length;
    }

    /** 按下标返回只读球快照，下标由调用方校验 */
    public BallView ball(int index) {
        return balls[index];
    }

    /** 在台球数量（落袋的不算） */
    public int ballsOnTable() {
        return ballsOnTable;
    }

    /** 主球球心 x；主球不在台上时为 {@code NaN}。渲染器据此决定要不要画瞄准线 */
    public double cueX() {
        return cueX;
    }

    /** 返回主球球心的竖直坐标，主球离场时为 NaN */
    public double cueY() {
        return cueY;
    }

    /** 主球是否在台上 */
    public boolean cueBallOnTable() {
        return !Double.isNaN(cueX);
    }

    /** 返回本帧的只读瞄准预览 */
    public AimView aim() {
        return aim;
    }

    /** 返回本帧力度读数，正常范围为零到一 */
    public double power() {
        return power;
    }

    /** 返回当前是否仍在模拟一杆球 */
    public boolean shotInFlight() {
        return shotInFlight;
    }

    /** 返回本帧状态栏文字 */
    public String status() {
        return status;
    }

    /** 对局信息。没有对局时是 {@link MatchView#NONE}（非 {@code null}），渲染器不必判空 */
    public MatchView match() {
        return match;
    }
}
