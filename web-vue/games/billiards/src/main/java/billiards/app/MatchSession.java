package billiards.app;

import billiards.audio.Hit;
import billiards.audio.ShotSounds;
import billiards.audio.SoundSink;
import billiards.input.AimCalculator;
import billiards.input.InputCommand;
import billiards.input.InputController;
import billiards.input.ShotPower;
import billiards.render.AimView;
import billiards.render.BallView;
import billiards.render.GameStateView;
import billiards.render.HudRenderer;
import billiards.render.MatchView;
import billiards.rules.GamePhase;
import billiards.rules.MatchOutcome;
import billiards.rules.Player;
import billiards.rules.PlacementValidator;
import billiards.rules.RackBuilder;
import billiards.rules.RulesEngine;
import billiards.rules.ShotVerdict;
import billiards.world.Ball;
import billiards.world.BallCatalog;
import billiards.world.BallGroup;
import billiards.world.Layout;
import billiards.world.Table;
import billiards.world.Vec2;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>一局正式比赛</b>：把鼠标键盘翻成 {@link RulesEngine} 的调用，再把引擎的状态打包成
 * 不可变快照
 *
 * <h2>它是「谁认识谁」的最后一处接线</h2>
 * 这个类是工程里唯一同时认识 {@code input}、{@code rules}、{@code render} 的地方，
 * 因为它就是那条依赖链的<b>汇合点</b>：{@code world ← physics ← rules ← input}，
 * 而 {@code app} 站在所有人上面。规则引擎自己不认识鼠标（{@code rules} 不许 import
 * {@code input}），渲染器自己不认识引擎——中间这段翻译的活全在这里
 *
 * <p>它的全部职责可以压成一句话：<b>每个固定步做一次「收指令 → 推进引擎 → 发布快照」</b>
 * 没有任何一处自己维护游戏规则——相位是 {@link GamePhase} 的，判定是 {@link ShotVerdict}
 * 的，能不能放球是 {@link PlacementValidator} 的。这个类只负责把它们串到时间轴上
 *
 * <h2>四种操作，按阶段分派</h2>
 * <ul>
 *   <li><b>开赛前</b>（{@link GamePhase#MATCH_START}）：点一下就开始，同时弹出开场浮层</li>
 *   <li><b>摆主球</b>（{@link GamePhase#BREAK_SETUP} / {@link GamePhase#BALL_IN_HAND}）：
 *       虚影跟着光标走，绿色＝放得下、红色＝放不下；按下再松开落位。松手才落位而不是按下
 *       就落位，是为了和击球同一个手感——<b>按下是「准备」，松开才是「就是这里」</b>，
 *       于是拖着找一个合适的位置时不会因为手抖先落一步</li>
 *   <li><b>瞄准</b>（{@link GamePhase#AIMING}）：拉弹弓，与 M4 定下来的手势逐位相同</li>
 *   <li><b>终局</b>（{@link GamePhase#GAME_OVER}）：点一下或按 R 再来一局</li>
 * </ul>
 *
 * <h2>拖放主球时为什么把主球从快照里摘掉</h2>
 * 开球布置阶段主球其实<b>已经在台上</b>（引擎把它摆在默认开球位）。若照常发布，
 * 屏幕上会同时出现「老老实实待在默认位的白球」和「跟着光标跑的虚影」——两颗主球
 * 所以<b>只要虚影真的画了出来</b>就把实体球标成不在台（{@code cueX} 随之成为 {@code NaN}），
 * 于是瞄准线、球杆、实体球三样一起消失，画面上只剩那颗跟着手走的虚影
 * <b>这不是渲染器加了特判，而是快照如实报告了「主球此刻不在台面上」</b>——
 * 玩家的手正拿着它
 *
 * <p>「虚影真的画了出来」这个限定词不能省。虚影的位置取的是光标，而<b>光标在玩家第一次
 * 移动鼠标之前是无效的</b>——那时候既没有虚影可画、实体球又被摘掉了，屏幕上会是一张
 * <b>没有主球的球台</b>，玩家只能靠猜把球拖出去。加上这个条件之后，画面上恒有且只有一个
 * 主球：还没动鼠标时是台上的实体，动了之后是跟着手走的虚影
 *
 * <h2>线程归属</h2>
 * 与 M3 起的约定完全一致：{@link #tickFixed()} 只允许在 {@link GameLoop} 的模拟线程上
 * 调用，全部可变状态关在这个线程里。对外唯一的出口是 {@link #view()}
 */
public final class MatchSession {

    /** {@link Layout#AIM_MIN_RADIUS_PX} 的平方。比较距离用平方，省一次开方 */
    private static final double AIM_MIN_RADIUS_SQ =
            Layout.AIM_MIN_RADIUS_PX * Layout.AIM_MIN_RADIUS_PX;

    /** 默认瞄准方向：朝 +x，也就是球堆方向。一进来什么都不动就拉杆，也是一记正常开球 */
    private static final Vec2 DEFAULT_AIM = new Vec2(1.0, 0.0);

    private final RulesEngine engine = new RulesEngine();
    private final MatchMessages messages = new MatchMessages(engine);
    private final InputController input = new InputController();

    /**
     * 开场浮层是否还盖着
     *
     * <p>它与 {@link GamePhase#MATCH_START} 是同一件事，只是那个阶段归引擎所有、而引擎的
     * {@code startMatch()} 一步就跳到 {@code BREAK_SETUP}，中间没有地方停。所以「还没开始」
     * 这个状态由会话自己拿一个布尔值记住，发布快照时报成 {@code MATCH_START}——
     * <b>没有新造阶段，只是让那个本来够不着的阶段真的能被走到</b>
     */
    private boolean awaitingStart = true;

    /** 调试浮层开关（D） */
    private boolean debugOverlay;

    /**
     * 声音出口。默认 {@link SoundSink#SILENT}——<b>这个默认值是一条工程约束的落点</b>：
     * 自检会在没有声卡的机器上构造 {@code MatchSession}，默认真出口就意味着自检有概率
     * 去开音频设备。默认静音则让「自检碰声卡」在构造上不可能发生，真出口只能由
     * {@code MainFrame} 显式注入
     */
    private final SoundSink sink;

    /**
     * 静音开关（M）。只由模拟线程读写：{@link #apply} 里翻它，物理回调所在的也是同一个线程
     *
     * <p>它拦在<b>入队之前</b>而不是在混音器里把音量调零——静音时连波形都不必合成
     */
    private boolean muted;

    /** 瞄准方向（单位向量） */
    private Vec2 aimDir = DEFAULT_AIM;

    /** 拉杆起点（按下时的光标位置）；{@code null} = 当前没有在拉杆 */
    private Vec2 pullAnchor;

    /** 本帧的力度（0–1）。只在 {@link #pullAnchor} 非空时有意义 */
    private double power;

    /** 上一杆的力度读数，供力度条在球飞出去之后仍显示一下打多重 */
    private double lastShotPower;

    /** 拖放主球时是否已经按下（按下＝准备落位，松开才真的落） */
    private boolean placementArmed;

    /** 交给 EDT 的那一个格子。除此之外两个线程不共享任何可变对象 */
    private volatile GameStateView published;

    /** 静音的一局。自检与无头路径用这个 */
    public MatchSession() {
        this(SoundSink.SILENT);
    }

    /**
     * 出声的一局。只有 GUI 走这条——真出口由 {@code MainFrame} 注入
     *
     * <p>挂监听器这件事发生在这里而不是某个「初始化」方法里：一局比赛从构造到结束都用
     * 同一批订阅者，中间没有可以安全增删订阅者的时刻，那就别提供一个看起来能用的入口
     */
    public MatchSession(SoundSink sink) {
        this.sink = sink;
        engine.addListener(new ShotSounds(new SessionSoundSink()));
        publish();
    }

    /** 将声音事件交给出口，并在静音时阻止播放 */
    private final class SessionSoundSink implements SoundSink {

        /** 声音回调来自模拟线程，此处只判断状态并转交事件 */
        @Override
        public void hit(Hit hit) {
            deliver(hit);
        }

        /** 返回声音出口是否已连接真实设备 */
        @Override
        public boolean isLive() {
            return sink.isLive();
        }

        /** 会话不拥有声音出口的生命周期，因此不在这里关闭设备 */
        @Override
        public void close() {
        }
    }

    /** 把一次撞击送到出口。静音时直接吞掉 */
    private void deliver(Hit hit) {
        if (!muted) {
            sink.hit(hit);
        }
    }

    // 访问（全部线程安全）

    /** 球台几何。它构造后不再变，可以安全跨线程共享 */
    public Table table() {
        return engine.table();
    }

    /** 返回输入控制器，鼠标和键盘事件由它进入队列 */
    public InputController input() {
        return input;
    }

    /** 最近一帧的不可变快照。EDT 唯一该读的东西 */
    public GameStateView view() {
        return published;
    }

    /** 规则引擎。<b>只给自检与调试用</b>——界面一切走快照 */
    public RulesEngine engine() {
        return engine;
    }

    // 模拟线程

    /**
     * 推进一个固定步。由 {@link GameLoop} 调用，且只由它调用
     *
     * <p>顺序是「先收指令 → 再推进引擎 → 最后发布快照」：指令先落地，本帧的画面就包含了
     * 玩家的输入；快照最后发布，发布的必然是完整推进后的状态
     */
    public void tickFixed() {
        advanceFixed();
        publish();
    }

    /** 只推进输入与物理，实时循环在一批补步结束后统一发布快照 */
    void advanceFixed() {
        drainInput();
        engine.tickFixed();

        if (!awaitingStart && engine.phase() == GamePhase.AIMING) {
            updateAimFromCursor();
            updatePull();
        }

    }

    /** 一帧物理补步完成后发布，避免同一画面生成多份中间快照 */
    void publishFrame() {
        publish();
    }

    /** 把 EDT 排队的指令收干净。见 {@link InputController#drain} 对「为什么全收走」的说明 */
    private void drainInput() {
        // 在模拟线程逐条取出指令，保证输入不会直接修改物理状态
        InputCommand command = input.poll();
        while (command != null) {
            apply(command);
            command = input.poll();
        }
    }

    /** 按指令类型处理输入，指令由模拟线程从队列取出 */
    private void apply(InputCommand command) {
        InputCommand.Kind kind = command.kind();
        if (kind == InputCommand.Kind.PRESS) {
            onPress(new Vec2(command.x(), command.y()));
        } else if (kind == InputCommand.Kind.RELEASE) {
            onRelease(new Vec2(command.x(), command.y()));
        } else if (kind == InputCommand.Kind.CANCEL) {
            clearPull();
        } else if (kind == InputCommand.Kind.RESET) {
            resetMatch();
        } else if (kind == InputCommand.Kind.DEBUG) {
            debugOverlay = !debugOverlay;
        } else if (kind == InputCommand.Kind.MUTE) {
            muted = !muted;
        } else {
            throw new IllegalStateException("未处理的指令: " + kind);
        }
    }

    // 按下

    /** 根据当前阶段处理按下位置，开始比赛、摆球或准备击球 */
    private void onPress(Vec2 center) {
        if (awaitingStart) {
            // 开场浮层上点一下＝开始。这里刻意<b>不</b>顺手把这一下当成落位：
            // 否则「点一下开始」会同时把主球丢在光标所在的位置，而玩家还没打算放
            awaitingStart = false;
            engine.startMatch();
            return;
        }

        // 「重新开始」按钮排在阶段分派之前：它的热区在 HUD 上（球台之外），那里既不该
        // 起拉杆、也不该落位。按钮的画与点都出自 HudRenderer 的同一个矩形（理由见那个方法）
        if (HudRenderer.hitsRestartButton(center.x, center.y)) {
            resetMatch();
            return;
        }

        GamePhase phase = engine.phase();
        if (phase == GamePhase.GAME_OVER) {
            resetMatch();
        } else if (phase == GamePhase.AIMING) {
            beginPull(center);
        } else if (phase == GamePhase.BREAK_SETUP || phase == GamePhase.BALL_IN_HAND) {
            placementArmed = true;
        }
    }

    // 松开

    /** 根据松手位置摆放主球或击球，非法位置由规则引擎拒绝 */
    private void onRelease(Vec2 center) {
        GamePhase phase = engine.phase();

        if (phase.allowsCuePlacement()) {
            // 落点不合法的处理全在引擎里：placeCueBall 返回 false 时局面一点没动，
            // 玩家可以接着拖着找位置，这里不需要另做一份校验
            if (placementArmed) {
                engine.placeCueBall(center);
            }
            placementArmed = false;
            return;
        }

        if (pullAnchor == null) {
            return;
        }
        // 方向与力度都按松手那一刻的坐标算，不用 input.cursor()：那个值可能旧一帧，
        // 而这一帧正好是「手停住、决定打多重、朝哪打」的那一帧。方向必须先算——
        // 它是实时的，松手那一刻的光标所指就是玩家看到的所指
        updateAim(center);
        double profileValue = powerFor(center);
        clearPull();
        if (profileValue > 0.0 && phase == GamePhase.AIMING) {
            fire(profileValue);
        }
    }

    // 开球与重来

    /** 重开一局。等于按了一次 R，或点了「重新开始」/终局浮层上的「再来一局」 */
    public void resetMatch() {
        clearPull();
        placementArmed = false;
        input.clearPending();
        aimDir = DEFAULT_AIM;
        lastShotPower = 0.0;
        awaitingStart = false;
        engine.startMatch();
    }

    /** 退回开场浮层（自检用得到，界面上没绑键） */
    public void returnToStart() {
        clearPull();
        placementArmed = false;
        input.clearPending();
        aimDir = DEFAULT_AIM;
        lastShotPower = 0.0;
        awaitingStart = true;
    }

    // 瞄准与拉杆（与 M4 定下来的手势逐位相同）

    /** 光标有效时更新瞄准方向，尚未移动时保持默认方向 */
    private void updateAimFromCursor() {
        if (!input.isCursorValid()) {
            return;
        }
        updateAim(input.cursor());
    }

    /** 由光标位置定出瞄准方向：<b>从光标指向主球</b> */
    private void updateAim(Vec2 cursor) {
        Ball cue = cueBall();
        if (cue == null || !cue.isOnTable()) {
            return;
        }
        Vec2 back = cue.position().minus(cursor);
        if (back.lengthSq() >= AIM_MIN_RADIUS_SQ) {
            aimDir = back.normalized();
        }
    }

    /** 按下左键：记下拉杆起点，把力度清零 */
    private void beginPull(Vec2 center) {
        Ball cue = cueBall();
        if (cue == null || !cue.isOnTable()) {
            return;
        }
        updateAim(center);
        pullAnchor = center;
        power = 0.0;
    }

    /** 根据当前拖动距离刷新力度，没有拉杆时清零 */
    private void updatePull() {
        if (pullAnchor == null) {
            power = 0.0;
        } else {
            power = powerFor(input.cursor());
        }
    }

    /** 取消拉杆并清空力度，防止下一杆沿用旧输入 */
    private void clearPull() {
        pullAnchor = null;
        power = 0.0;
    }

    /**
     * 拉距 → 力度：手离按下点多远，就是拉了多少
     *
     * <p>取位移的<b>大小</b>而不是沿某个轴的投影：投影要先定死一根轴，而唯一的候选轴就是
     * 瞄准方向——方向既然要能实时改，轴就在实时转，「转手腕」于是会漏出轴向分量被算成
     * 力度，两个量又绑回一起了（推导见 {@link Layout#PULL_MAX_PX}）
     */
    private double powerFor(Vec2 cursor) {
        if (pullAnchor == null) {
            return 0.0;
        }
        return ShotPower.powerFor(cursor.minus(pullAnchor).length());
    }

    /** 击球。用的是松手前刚更新过的 {@link #aimDir}，所以「瞄准线画在哪就打到哪」 */
    private void fire(double profileValue) {
        if (engine.takeShot(aimDir, ShotPower.speedFor(profileValue))) {
            lastShotPower = profileValue;
            // M8 在这里发过一声「出杆」，M8.2 去掉了：用户要的只有球碰球与落袋两种
            // 于是 {@link Hit} 里也不再需要 cue 这个工厂方法——出杆不是碰撞，
            // 它本来就不该走 ShotEventListener 那条路
        }
    }

    // 快照

    /**
     * 造一个新的不可变快照并发布
     *
     * <p>快照保持只读，未变化的球状态与瞄准预览可以沿用上一帧的对象，减少重复分配
     */
    private void publish() {
        // 每次发布都构造完整快照，避免界面线程读到更新一半的球列表
        GamePhase phase = reportedPhase();
        Placement placing = placement(phase);
        // 实体主球让位给虚影的条件是「虚影确实画得出来」，不是「处在可放置阶段」
        // （差别与理由见类注释）。两者差在光标还没动过的那几帧上
        boolean ghostDrawn = placing.active && !Double.isNaN(placing.x);
        List<Ball> balls = engine.balls();

        BallView[] views = new BallView[balls.size()];
        boolean sameBalls = published != null && published.ballCount() == balls.size();
        for (int index = 0; index < views.length; index = index + 1) {
            Ball ball = balls.get(index);
            Vec2 position = ball.position();
            boolean visible = ball.isOnTable() && !(ghostDrawn && ball.isCueBall());
            BallView previous = null;
            if (published != null && index < published.ballCount()) {
                previous = published.ball(index);
            }
            if (previous != null && previous.id() == ball.id()
                    && previous.x() == position.x && previous.y() == position.y
                    && previous.onTable() == visible) {
                views[index] = previous;
            } else {
                views[index] = new BallView(ball.id(), position.x, position.y, visible);
                sameBalls = false;
            }
        }

        AimView aim = reusableAim(phase, sameBalls);
        String summary = "";
        if (phase == GamePhase.GAME_OVER && engine.outcome() != null) {
            summary = engine.achievements().summary(engine.outcome().winner());
        }
        double shownPower = displayPower(phase);
        String status = messages.statusText(phase);
        MatchView match = buildMatchView(phase, placing);
        String notice = engine.achievements().notice();
        // 快照完全相同时保留引用，绘制线程据此跳过整张静止画面的重复绘制
        if (sameBalls && published.aim() == aim && published.power() == shownPower
                && published.status().equals(status) && published.match().sameDisplay(match)
                && published.achievementNotice().equals(notice) && published.achievementSummary().equals(summary)) {
            return;
        }
        published = new GameStateView(views, aim, shownPower,
                phase == GamePhase.SHOT_IN_FLIGHT, status, match,
                engine.achievements().notice(), summary);
    }

    /** 球位和方向都没变时复用只读预览，任何球位变化都会重新计算 */
    private AimView reusableAim(GamePhase phase, boolean sameBalls) {
        if (phase == GamePhase.AIMING && sameBalls) {
            AimView previous = published.aim();
            if (previous.isPresent() && previous.dir().x == aimDir.x && previous.dir().y == aimDir.y) {
                return previous;
            }
        }
        return buildAimView(phase);
    }

    /** 会话对外报的阶段：开赛前报 {@code MATCH_START}，其余照搬引擎 */
    private GamePhase reportedPhase() {
        if (awaitingStart) {
            return GamePhase.MATCH_START;
        }
        return engine.phase();
    }

    /** 从规则状态生成对局快照，参数提供当前阶段和待放主球信息 */
    private MatchView buildMatchView(GamePhase phase, Placement placing) {
        Player current = engine.currentPlayer();
        BallGroup groupOne = engine.groupOf(Player.ONE);
        BallGroup groupTwo = engine.groupOf(Player.TWO);

        List<Integer> pottedOne = new ArrayList<>();
        List<Integer> pottedTwo = new ArrayList<>();
        boolean eightOnTable = false;
        for (Ball ball : engine.balls()) {
            int ballId = ball.id();
            if (ball.isOnTable()) {
                if (ballId == BallCatalog.EIGHT_ID) {
                    eightOnTable = true;
                }
                continue;
            }
            if (ballId == BallCatalog.CUE_ID || ballId == BallCatalog.EIGHT_ID) {
                continue;   // 主球与 8 号不进托盘：前者不是「打进的球」，后者单独占中央徽章
            }
            BallGroup ballGroup = ball.group();
            if (ballGroup == groupOne) {
                pottedOne.add(ballId);
            } else if (ballGroup == groupTwo) {
                pottedTwo.add(ballId);
            }
            // 组还没定的时候落袋的球不属于任何人，两边托盘都不进
        }

        return new MatchView(phase, current, groupOne, groupTwo,
                toArray(pottedOne), toArray(pottedTwo), eightOnTable,
                messages.statusTone(phase), engine.outcome(), messages.bannerText(phase), messages.bannerTone(phase),
                placing.active, engine.cuePlacementRestricted(), placing.valid,
                placing.x, placing.y, debugLines(phase));
    }

    /** 拖放主球的当前状态 */
    private static final class Placement {
        private final boolean active;
        private final boolean valid;
        private final double x;
        private final double y;

        /** 保存构造参数，字段只读以便安全传递状态 */
        private Placement(boolean active, boolean valid, double x, double y) {
            this.active = active;
            this.valid = valid;
            this.x = x;
            this.y = y;
        }

        /** 返回保存的启用状态 */
        public boolean active() {
            return active;
        }

        /** 返回保存的合法状态 */
        public boolean valid() {
            return valid;
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

    /** 根据阶段和光标生成待放主球状态，显示位置限制在台面内 */
    private Placement placement(GamePhase phase) {
        if (!phase.allowsCuePlacement() || !input.isCursorValid()) {
            return new Placement(phase.allowsCuePlacement(), false, Double.NaN, Double.NaN);
        }
        Vec2 center = input.cursor();
        // 光标可以走到木框、背景与 HUD 上——那些地方也在窗口里，会照常收到鼠标事件
        // 虚影若照着原始光标画，拖动主球就能把它带出球台（「球出框」）
        // 收进台面之后再画；台面外一律判为放不下，因为松手走的是原始坐标、本来就不会落位，
        // 在贴边处画成绿色等于告诉玩家一个不会发生的结果
        boolean onTable = engine.table().isInsidePlayfield(center);
        Vec2 shown = center;
        if (!onTable) {
            shown = engine.table().clampToPlayfield(center);
        }
        boolean valid = onTable && PlacementValidator.isValid(
                engine.table(), engine.balls(), center, engine.cuePlacementRestricted());
        return new Placement(true, valid, shown.x, shown.y);
    }

    /** 将球号列表复制为整数数组，返回独立的快照数据 */
    private static int[] toArray(List<Integer> ids) {
        int[] out = new int[ids.size()];
        for (int index = 0; index < out.length; index = index + 1) {
            out[index] = ids.get(index);
        }
        return out;
    }

    /** 在可击球阶段计算瞄准预览，其他阶段返回空预览 */
    private AimView buildAimView(GamePhase phase) {
        if (phase != GamePhase.AIMING) {
            return AimView.NONE;
        }
        Ball cue = cueBall();
        if (cue == null || !cue.isOnTable()) {
            return AimView.NONE;
        }
        Vec2 origin = cue.position();
        AimCalculator.Preview preview = AimCalculator.preview(origin, aimDir, engine.table(), engine.balls());

        // 撞不到任何东西时（射线从袋口缺口射出）没有虚影球可画，但整条瞄准线不该凭空消失，
        // 否则玩家会以为程序卡了。画满一个「一定出画」的长度，看起来就是射出去了
        Vec2 lineEnd = preview.ghost();
        if (lineEnd == null) {
            lineEnd = origin.plus(aimDir.times(Layout.AIM_ESCAPE_LENGTH_PX));
        }

        return new AimView(aimDir, lineEnd, preview.ghost(), preview.targetId(),
                preview.targetSeparation(), preview.cueSeparation());
    }

    /** 力度条读数：球在飞就留着这一杆打出去的力度，正在拉杆看实时值，其余时候是 0 */
    private double displayPower(GamePhase phase) {
        if (phase == GamePhase.SHOT_IN_FLIGHT) {
            return lastShotPower;
        }
        if (pullAnchor != null && phase == GamePhase.AIMING) {
            return power;
        }
        return 0.0;
    }

    /** 返回最近一杆的结果文字；没有结果时返回空状态 */
    private String previousVerdictText() {
        ShotVerdict verdict = engine.lastVerdict();
        if (verdict == null) {
            return "（无）";
        }
        return verdict.toString();
    }

    /** 返回光标位置文字；光标还未移动时返回空状态 */
    private String cursorText() {
        if (!input.isCursorValid()) {
            return "（无）";
        }
        return input.cursor().toString();
    }

    /** 根据静音开关和设备状态生成调试文字 */
    private String soundText() {
        String soundState = "开";
        if (muted) {
            soundState = "关（M 开）";
        }

        String deviceState = "无";
        if (sink.isLive()) {
            deviceState = "在";
        }
        return "声音 " + soundState + " · 设备 " + deviceState;
    }

    // 文案

    // 调试浮层

    /**
     * 调试浮层的行。关着的时候返回空数组，一帧一个字符串都不拼
     *
     * <p>它是<b>预先拼好的文本</b>而不是「渲染器拿引擎去查」：渲染层不许认识引擎，
     * 而这里（app 层）本来就是唯一认识所有人的地方。同 {@code banner} 一个路子
     */
    private String[] debugLines(GamePhase phase) {
        if (!debugOverlay) {
            return new String[0];
        }
        String[] lines = new String[8];
        lines[0] = "阶段 " + phase + "（" + phase.labelZh() + "）";
        lines[1] = "当前手 " + engine.currentPlayer().labelZh() + " · 开球杆 " + engine.isBreakShot();
        lines[2] = "组属 " + Player.ONE.labelZh() + " = " + engine.groupOf(Player.ONE)
                + " · " + Player.TWO.labelZh() + " = " + engine.groupOf(Player.TWO);
        lines[3] = "在台 " + engine.idsOnTable().size()
                + " 颗 · 目标球 " + engine.objectBallsOnTable();
        lines[4] = String.format("本杆模拟 %.0f ms · 超时 %s",
                engine.shotSimMs(), engine.lastShotTimedOut());
        lines[5] = "上一杆 " + previousVerdictText();
        lines[6] = String.format("光标 %s · 力道 %.3f · 拉杆 %s",
                cursorText(), displayPower(phase), pullAnchor != null);
        lines[7] = soundText();
        return lines;
    }

    /** 在对局球列表中查找主球，找不到时返回 null */
    private Ball cueBall() {
        return RackBuilder.cueBall(engine.balls());
    }
}
