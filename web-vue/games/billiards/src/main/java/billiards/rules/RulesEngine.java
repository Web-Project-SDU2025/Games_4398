package billiards.rules;

import billiards.physics.PhysicsEngine;
import billiards.world.Ball;
import billiards.world.BallCatalog;
import billiards.world.BallGroup;
import billiards.world.Layout;
import billiards.world.PhysicsConfig;
import billiards.world.ShotEventListener;
import billiards.world.Table;
import billiards.world.Vec2;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 正式对局的状态机：把物理、判罚、轮次串成一局能打完的比赛
 *
 * <h2>它在整个工程里的位置</h2>
 * 这是唯一一处同时认识「球」「规则」「时间」的地方：
 * <ul>
 *   <li>它持有 {@link PhysicsEngine} 与球列（物理侧）；</li>
 *   <li>它持有 {@link ShotRecord} 并注册成物理的监听器（证据收集）；</li>
 *   <li>它按 {@link GamePhase} 推进，每杆结束时调 {@link FoulDetector} 出一份判决书</li>
 * </ul>
 * 但它<b>不</b>认识鼠标、不认识渲染、不读墙钟。输入是「朝哪打、打多重」，输出是一个
 * {@link GamePhase} 与一份 {@link ShotVerdict}——所以整个规则引擎可以无头跑，
 * S1–S18 就是这么测的
 *
 * <h2>为什么「判定」是一个真实的阶段而不是一瞬间</h2>
 * 见 {@link GamePhase#RESOLVING}。技术上判定是同步算完的，但状态机会在
 * {@code RESOLVING} 停 {@link Layout#VERDICT_BANNER_MS} 毫秒才进下一手。演示时这段时间
 * 就是「裁判在看」——没有它，玩家只会看到球停下来的同一瞬间对手就获得了自由球
 *
 * <h2>线程归属</h2>
 * 与 {@code MatchSession} 同一套约定：{@link #tickFixed()} 只在模拟线程上调用，
 * 全部可变状态关在这个线程里。M6 接 UI 时外面只该看到 {@code phase()}、
 * {@code currentPlayer()}、{@code lastVerdict()} 这类只读查询加上一份不可变快照
 */
public final class RulesEngine {

    /** 一颗球在局面里的位置。手工摆局面用（自检、调试） */
    public static final class BallPlacement {
        private final int id;
        private final double x;
        private final double y;

        /** 保存构造参数，字段只读以便安全传递状态 */
        public BallPlacement(int ballId, double x, double y) {
            this.id = ballId;
            this.x = x;
            this.y = y;
        }

        /** 返回保存的球号 */
        public int id() {
            return id;
        }

        /** 返回保存的水平坐标 */
        public double x() {
            return x;
        }

        /** 返回保存的竖直坐标 */
        public double y() {
            return y;
        }

    }

    private final Table table = new Table();
    private final List<Ball> balls = RackBuilder.buildRack();
    private final PhysicsEngine engine = new PhysicsEngine(table, balls);
    private final ShotRecord record = new ShotRecord();
    private final MatchAchievements achievements = new MatchAchievements();

    private GamePhase phase = GamePhase.MATCH_START;

    /** 当前该谁击球。<b>换手在判定那一刻就发生</b>，所以判定横幅显示的已经是对手 */
    private Player current = Player.ONE;

    /** 双方组属。{@code null} = 尚未定组（开球之后、第一次合法进球之前） */
    private BallGroup groupOne;
    private BallGroup groupTwo;

    private MatchOutcome outcome;
    private ShotVerdict lastVerdict;

    /** 本杆是不是开球那一杆。开球有一整套独立规则，所以它是个贯穿整杆的标记 */
    private boolean breakShot;

    /** 当前自由球是否被限制在开球线后（开球犯规的处罚） */
    private boolean behindHeadStringOnly;

    /** 出杆那一刻冻结的局面。判罚只认它，见 {@link ShotContext} */
    private ShotContext activeContext;

    /** 本杆已推进的<b>模拟</b>毫秒数。用模拟时间而不是墙钟，结果才不依赖机器负载 */
    private double shotSimMs;

    /** 判定横幅剩余时间（毫秒） */
    private double bannerMs;

    /** 横幅走完之后进入哪个阶段 */
    private GamePhase pendingPhase = GamePhase.AIMING;

    /** 上一杆是否是被超时打断的（而不是自然停稳）。只在出 bug 时为真 */
    private boolean lastShotTimedOut;

    /** 单杆模拟时长上限。默认取物理配置；自检用它把超时分支逼出来 */
    private double shotTimeoutSimMs = PhysicsConfig.SHOT_TIMEOUT_SIM_MS;

    /** 根据参数建立对象的初始状态，后续操作沿用这些依赖和设置 */
    public RulesEngine() {
        engine.addListener(record);
    }

    /** 再加一个物理事件的订阅者 */
    public void addListener(ShotEventListener listener) {
        engine.addListener(listener);
    }

    // 只读查询

    /** 返回构造后不再改变的球台几何 */
    public Table table() {
        return table;
    }

    /** 球列。<b>可变</b>——只给模拟线程与自检用，渲染请走快照 */
    public List<Ball> balls() {
        return balls;
    }

    /** 本杆的证据。判定完成后仍保留，供 HUD 显示细节与自检断言 */
    public ShotRecord record() {
        return record;
    }

    /** 返回本局成就记录，界面只读取文字，判决完成后由引擎统一更新 */
    public MatchAchievements achievements() {
        return achievements;
    }

    /** 返回当前对局阶段 */
    public GamePhase phase() {
        return phase;
    }

    /** 返回当前应当击球的玩家 */
    public Player currentPlayer() {
        return current;
    }

    /** 根据玩家返回对应组属，未定组时返回 null */
    public BallGroup groupOf(Player player) {
        if (player == Player.ONE) {
            return groupOne;
        }
        return groupTwo;
    }

    /** 上一杆的判决书；还没打过任何一杆时是 {@code null} */
    public ShotVerdict lastVerdict() {
        return lastVerdict;
    }

    /** 返回终局结果，比赛未结束时返回 null */
    public MatchOutcome outcome() {
        return outcome;
    }

    /** 返回当前一杆是否为开球 */
    public boolean isBreakShot() {
        return breakShot;
    }

    /** 返回上一杆是否因模拟超时被强制停止 */
    public boolean lastShotTimedOut() {
        return lastShotTimedOut;
    }

    /** 本杆已推进的模拟毫秒数。球在滚动时它一直在涨 */
    public double shotSimMs() {
        return shotSimMs;
    }

    /** 当前放置主球是否被限制在开球线后（开球布置、以及开球犯规后的自由球） */
    public boolean cuePlacementRestricted() {
        if (phase == GamePhase.BREAK_SETUP) {
            return true;
        }
        return phase == GamePhase.BALL_IN_HAND && behindHeadStringOnly;
    }

    /** 当前台上还剩的目标球号（只读快照） */
    public Set<Integer> objectBallsOnTable() {
        Set<Integer> ids = new LinkedHashSet<>();
        for (Ball ball : balls) {
            if (ball.isOnTable() && !ball.isCueBall()) {
                ids.add(ball.id());
            }
        }
        return ids;
    }

    // 开局

    /**
     * 开新的一局：重新摆球、清空组属与胜负、进入开球布置
     *
     * <p>开球方固定为玩家 1。真实比赛会争球决定，本工程固定下来是为了让自检可复现
     */
    public void startMatch() {
        List<Ball> fresh = RackBuilder.buildRack();
        for (int index = 0; index < balls.size() && index < fresh.size(); index = index + 1) {
            balls.get(index).setPosition(fresh.get(index).position());
            balls.get(index).stop();
            balls.get(index).setOnTable(true);
        }
        record.reset();
        current = Player.ONE;
        achievements.reset();
        groupOne = null;
        groupTwo = null;
        outcome = null;
        lastVerdict = null;
        activeContext = null;
        breakShot = true;
        behindHeadStringOnly = true;
        shotSimMs = 0.0;
        bannerMs = 0.0;
        pendingPhase = GamePhase.AIMING;
        lastShotTimedOut = false;
        phase = GamePhase.BREAK_SETUP;
    }

    // 主球放置

    /**
     * 把主球放到 {@code pos}。只在开球布置与自由球两个阶段有效，且落点要同时满足几何与规则
     * 约束（见 {@link PlacementValidator}）
     *
     * @return 是否放置成功；失败时局面一点没动，调用方可以放心地让玩家接着拖
     */
    public boolean placeCueBall(Vec2 pos) {
        if (!phase.allowsCuePlacement()) {
            return false;
        }
        if (!PlacementValidator.isValid(table, balls, pos, cuePlacementRestricted())) {
            return false;
        }
        Ball cue = RackBuilder.cueBall(balls);
        if (cue == null) {
            return false;
        }
        cue.setPosition(pos);
        cue.setVelocity(Vec2.ZERO);
        cue.setOnTable(true);
        phase = GamePhase.AIMING;
        return true;
    }

    // 出杆

    /** 出杆 */
    public boolean takeShot(Vec2 aimDir, double speed) {
        if (phase != GamePhase.AIMING) {
            return false;
        }
        Ball cue = RackBuilder.cueBall(balls);
        if (cue == null || !cue.isOnTable()) {
            return false;
        }
        Vec2 direction = aimDir.normalized();
        if (direction.lengthSq() < PhysicsConfig.EPS) {
            return false;
        }
        double value = Math.max(PhysicsConfig.MIN_SHOT_SPEED,
                Math.min(PhysicsConfig.MAX_SHOT_SPEED, speed));

        // 局面必须在出杆前冻结：判罚要问「当时本方还有没有球在台上」，而现在这一杆
        // 马上就会把答案改掉
        activeContext = ShotContext.of(current, groupOf(current), breakShot, balls);
        record.reset();
        achievements.clearNotice();

        cue.setVelocity(direction.times(value));
        shotSimMs = 0.0;
        lastShotTimedOut = false;
        lastVerdict = null;
        phase = GamePhase.SHOT_IN_FLIGHT;
        return true;
    }

    // 步进

    /**
     * 推进一个固定步长。只由模拟线程调用
     *
     * <p>等待输入的三个阶段（开球布置、瞄准、自由球）什么都不做——它们的变化全部由外部
     * 调用 {@link #placeCueBall} / {@link #takeShot} 触发。判定阶段只走横幅计时器
     */
    public void tickFixed() {
        achievements.tick(PhysicsConfig.FIXED_STEP_MS);
        if (phase == GamePhase.SHOT_IN_FLIGHT) {
            advanceShot();
        } else if (phase == GamePhase.RESOLVING) {
            advanceBanner();
        }
    }

    /** 推进本杆物理，停稳或达到超时限制后统一进行规则判定 */
    private void advanceShot() {
        engine.step();
        shotSimMs = shotSimMs + PhysicsConfig.FIXED_STEP_MS;

        boolean rested = engine.allAtRest();
        if (!rested && shotSimMs < shotTimeoutSimMs) {
            return;
        }
        if (!rested) {
            // 超时分支正常不该走到：走到了说明有球停不下来（比如有人把库边恢复系数调到 1.0）
            // 强制停住总比永远卡在「球在飞」不可操作要好，但要把这件事记下来让自检能看见
            lastShotTimedOut = true;
        }
        engine.stopAll();
        resolveShot();
    }

    /**
     * 本杆结束：收集证据 → 判罚 → 重置该重置的球 → 进判定阶段
     *
     * <p>顺序不能换。判罚读的是<b>这一杆留下的台面</b>（球该落袋的已经落袋），而 8 号重置
     * 与飞球重置会改变台面——先重置再判罚的话，判罚看到的局面已经被自己改过了
     */
    private void resolveShot() {
        ShotVerdict verdict = FoulDetector.judge(activeContext, record);

        // 开球把 8 号打进不是判负，而是把 8 号摆回置球点继续打
        if (breakShot && record.pocketed(BallCatalog.EIGHT_ID)) {
            respotBall(BallCatalog.EIGHT_ID);
        }
        // 异常飞出台面的球也需要重新置位，让犯规后仍能继续比赛
        for (int ballId : record.ballsOffTable()) {
            respotBall(ballId);
        }

        applyVerdict(verdict);
    }

    /**
     * 把判决书落到局面上：定组、记胜负、换手、决定横幅之后进哪个阶段
     *
     * <p>换手在这里就发生了（而不是等横幅走完）——横幅上写的是「玩家 2 获自由球」，
     * 而 {@code current} 同一时刻就已经是玩家 2，两者不可能对不上
     */
    private void applyVerdict(ShotVerdict verdict) {
        // 成就必须读取出杆前的身份与球组，换手后再判断会把奖励记给对手
        achievements.accept(activeContext, record, verdict);
        if (verdict.assignedGroupNow()) {
            // 组属是成对的：全色与花色互为补集（BallGroup.oppositeGroup），一方拿到哪一组，
            // 另一方当场拿到另一组——「一方已定组、另一方未定组」不是合法局面
            // 只写击球方那一格曾经真的发生过，后果不止 HUD 显示「未定组」：groupOf(对手)
            // 是 takeShot 构造 ShotContext 的输入，对手那一杆会被当成「台面未定组」——
            // 合法目标变成除 8 号外的所有球、打进任何球都算续杆、还会被重新定一次组，
            // 而且清组判定永远不成立（打不了 8 号）。两格必须同一次写成
            BallGroup assigned = verdict.assignedGroup();
            if (current == Player.ONE) {
                groupOne = assigned;
                groupTwo = assigned.oppositeGroup();
            } else {
                groupTwo = assigned;
                groupOne = assigned.oppositeGroup();
            }
        }

        lastVerdict = verdict;
        breakShot = false;
        current = verdict.nextPlayer();

        if (verdict.gameOver()) {
            outcome = verdict.outcome();
            pendingPhase = GamePhase.GAME_OVER;
        } else if (verdict.ballInHand()) {
            behindHeadStringOnly = verdict.ballInHandBehindHeadString();
            pendingPhase = GamePhase.BALL_IN_HAND;
            // 主球此刻不在台上（落袋了，或压根没上过台）。自由球阶段由玩家把它放回去
            Ball cue = RackBuilder.cueBall(balls);
            if (cue != null) {
                cue.setOnTable(false);
            }
        } else {
            behindHeadStringOnly = false;
            pendingPhase = GamePhase.AIMING;
        }

        phase = GamePhase.RESOLVING;
        bannerMs = Layout.VERDICT_BANNER_MS;
    }

    /** 按固定步长扣减横幅时间，到期后进入已经确定的下一阶段 */
    private void advanceBanner() {
        bannerMs = bannerMs - PhysicsConfig.FIXED_STEP_MS;
        if (bannerMs <= 0.0) {
            phase = pendingPhase;
        }
    }

    // 重置球

    /**
     * 把一颗不在台上的球摆回置球点附近
     *
     * <p>用 {@link Table#findFreeSpot} 而不是直接放在置球点上：置球点很可能正被别的球占着
     * （开球后 8 号重置时几乎必然如此），直接放会造成重叠，而重叠的球在下一个子步会被
     * 位置修正猛推一下——那看起来就像球自己炸开了
     */
    private void respotBall(int ballId) {
        Ball ball = RackBuilder.ballById(balls, ballId);
        if (ball == null || ball.isOnTable()) {
            return;
        }
        Vec2 spot = table.findFreeSpot(balls, Layout.footSpot(), ballId);
        ball.setPosition(spot);
        ball.setVelocity(Vec2.ZERO);
        ball.setOnTable(true);
    }

    // 自检与调试用的局面构造

    /** 手工摆一个局面。<b>只给自检与调试用</b>——正式开局走 {@link #startMatch()} */
    public void arrange(List<BallPlacement> placements, Player shooter,
                        BallGroup groupOne, BallGroup groupTwo, boolean breakShot) {
        for (Ball ball : balls) {
            ball.stop();
            ball.setOnTable(false);
        }
        for (BallPlacement rulesEngine : placements) {
            Ball ballValue = RackBuilder.ballById(balls, rulesEngine.id());
            if (ballValue == null) {
                throw new IllegalArgumentException("没有这个球号: " + rulesEngine.id());
            }
            ballValue.setPosition(new Vec2(rulesEngine.x(), rulesEngine.y()));
            ballValue.setVelocity(Vec2.ZERO);
            ballValue.setOnTable(true);
        }
        record.reset();
        this.current = shooter;
        achievements.reset();
        this.groupOne = groupOne;
        this.groupTwo = groupTwo;
        this.breakShot = breakShot;
        this.outcome = null;
        this.lastVerdict = null;
        this.activeContext = null;
        this.behindHeadStringOnly = false;
        this.shotSimMs = 0.0;
        this.bannerMs = 0.0;
        this.pendingPhase = GamePhase.AIMING;
        this.lastShotTimedOut = false;
        this.phase = GamePhase.AIMING;
    }

    /**
     * 单杆模拟时长上限（毫秒）。默认 {@link PhysicsConfig#SHOT_TIMEOUT_SIM_MS}
     *
     * <p>自检 S18 把它调到很小，好让「球还没停稳就被超时打断」这条分支真的被执行一次——
     * 自检由此验证模拟超时后的停止和判罚流程
     */
    public void setShotTimeoutSimMs(double ms) {
        this.shotTimeoutSimMs = ms;
    }

    /** 台上所有球的球号，含主球。诊断用 */
    public List<Integer> idsOnTable() {
        List<Integer> ids = new ArrayList<>();
        for (Ball ball : balls) {
            if (ball.isOnTable()) {
                ids.add(ball.id());
            }
        }
        return ids;
    }

    /** 便捷：某组还剩几颗在台上。{@code group} 为 {@code null} 时返回 0 */
    public int countOnTable(BallGroup group) {
        return GroupAssigner.countOnTable(group, objectBallsOnTable());
    }
}
