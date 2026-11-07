package billiards.selfcheck;

import billiards.rules.FoulDetector;
import billiards.rules.FoulType;
import billiards.rules.GamePhase;
import billiards.rules.GroupAssigner;
import billiards.rules.Player;
import billiards.rules.RackBuilder;
import billiards.rules.RulesEngine;
import billiards.rules.ShotContext;
import billiards.rules.ShotRecord;
import billiards.rules.ShotVerdict;
import billiards.world.Ball;
import billiards.world.BallCatalog;
import billiards.world.BallGroup;
import billiards.world.Layout;
import billiards.world.PhysicsConfig;
import billiards.world.Table;
import billiards.world.Vec2;

import java.util.List;
import java.util.Set;

/**
 * 规则引擎检查（S 组）
 *
 * <h2>为什么这一组能无头跑</h2>
 * {@code RulesEngine} 不认识鼠标、不认识渲染、不读墙钟——它的输入是「朝哪打、打多重」，
 * 输出是一个阶段与一份判决书。于是「先碰错球会怎样」这类问题不需要开窗口就能问，
 * 而且答案是可复现的一串字符
 *
 * <h2>摆局面为什么不算作弊</h2>
 * 规则里绝大多数分支要在真实台面上<b>碰巧</b>复现，得凑齐特定球位（8 号既要在袋口附近、
 * 又不能被别的球挡住、还要保证主球先碰到指定的那颗）。所以这些检查用
 * {@link RulesEngine#arrange} 摆初始条件，然后<b>照样驱动真实的物理引擎</b>——
 * 摆的是局面，不是伪造的事件。手造假事件只能测出「判罚函数按我想的写」，
 * 测不出「物理报出来的东西判罚函数读得懂」
 *
 * <h2>摆局的统一手法：正面全撞进上中袋</h2>
 * 所有要进球的分支都摆成「球心共线、球心连线垂直指向中袋」。中袋的捕获圆沿法向是
 * 一条没有任何库边遮挡的走廊，而正面全撞的碰撞法向与入射方向严格平行——两者合起来
 * 让落袋对「子步何时检测到接触」这件事<b>完全不敏感</b>。斜切则不然：接触被检测到时
 * 球已经沿入射方向多走了一个子步（最多 8px），法向随之偏最多约 8°，球飞几百像素后
 * 偏离可达几十像素。这一组检查要钉的是规则，不该被那点几何噪声带崩
 *
 * <p>唯一例外是 S16，理由写在那个方法里
 */
public final class RulesChecks {

    private static final Vec2 UP = new Vec2(0, -1);
    private static final Vec2 RIGHT = new Vec2(1, 0);
    private static final Vec2 ORIGIN = new Vec2(0, 0);

    /**
     * 手搓事件序列时填的撞击强度，单位 px/s
     *
     * <p>数值本身<b>无关紧要</b>：八条犯规问的都是「碰没碰过」而不是「碰得多重」，
     * 规则层从头到尾不读这个参数（它只为声音而存在）。写成具名常量而不是散落的
     * 字面量，是为了让下一个人一眼看出「这里不是随手填的，是真的不需要精确」
     */
    private static final double FIXTURE_IMPACT = 500.0;

    /** 一杆推进的步数上限：物理自己会在硬超时处收手，这个上限只是防死循环 */
    private static final int MAX_TICKS = 20000;

    /** 判定横幅走完的步数上限（正常是 1400/5 = 280 步） */
    private static final int MAX_BANNER_TICKS = 1000;

    // 摆局用的初速。三个档位，各自对应一类局面
    /** 足以让目标球滚到袋口并掉进去，又不至于把满台球都带起来 */
    private static final double SPEED_POT = 1040.0;
    /** 碰到球但球滚不到任何库边 */
    private static final double SPEED_SOFT = 480.0;
    /** 碰到球且球一定滚到库边 */
    private static final double SPEED_RAIL = 1460.0;

    // 摆局坐标：上中袋正下方的走廊，x = 500 与袋心同列
    private static final double LANE_X = 500.0;

    /** 工具类只提供静态方法，不需要创建对象 */
    private RulesChecks() {
    }

    /** 执行本模块的主流程，检查模块将结果记录到传入的报告中 */
    public static void run(CheckHarness checks) {
        s01_breakSetup(checks);

        ShotVerdict vLegalBreak = s02_legalBreak(checks);
        ShotVerdict vIllegalBreak = s03_illegalBreak(checks);
        s04_eightOnBreak(checks);
        ShotVerdict vAssign = s05_openTableAssignment(checks);
        s06_groupAssignerOrder(checks);
        s07_wrongBallFirst(checks);
        s08_noRailAfterContact(checks);
        ShotVerdict vPass = s09_legalNoPot(checks);
        s10_opponentBallPotted(checks);
        s11_cueBallScratch(checks);
        s12_foulShotAssignsNothing(checks);
        ShotVerdict vWin = s13_winWhenCleared(checks);
        s14_loseWhenEightEarly(checks);
        s15_loseWhenEightWithFoul(checks);
        s16_sameShotClear(checks);
        s17_placementGeometry(checks);
        s18_bannerDuration(checks);
        s19_timeoutFallback(checks);
        s20_messageZh(checks, vIllegalBreak, vAssign, vPass, vWin);
        s21_assignmentIsPaired(checks);
        AchievementChecks.run(checks);

        // vLegalBreak 没有单独的文案断言，但它的判决已经在 s02 里查过；
        // 这里只保证它确实产出了判决（返回 null 会在 s02 就报出来）
        if (vLegalBreak == null) {
            checks.check("S2 满力开球产出判决书", false, "判决书为 null");
        }
    }

    // S1 开局与主球放置

    /** 检查S1 构造后停在「比赛未开始」，将验证结果记入传入的检查报告 */
    private static void s01_breakSetup(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        checks.check("S1 构造后停在「比赛未开始」", rulesEngine.phase() == GamePhase.MATCH_START);

        rulesEngine.startMatch();
        checks.check("S1 开局进入开球布置", rulesEngine.phase() == GamePhase.BREAK_SETUP);
        checks.check("S1 开球布置时主球受开球线限制", rulesEngine.cuePlacementRestricted());
        checks.check("S1 开球布置时不能出杆", !rulesEngine.takeShot(RIGHT, SPEED_POT));
        checks.check("S1 被拒的出杆不改阶段", rulesEngine.phase() == GamePhase.BREAK_SETUP);

        double centerY = Layout.playfieldCenterY();
        checks.check("S1 开球线之前放不下主球",
                !rulesEngine.placeCueBall(new Vec2(Layout.HEAD_STRING_X + 20, centerY)));
        checks.check("S1 连开球线本身都不能压线",
                !rulesEngine.placeCueBall(new Vec2(Layout.HEAD_STRING_X, centerY)));
        checks.check("S1 台面之外放不下主球",
                !rulesEngine.placeCueBall(new Vec2(Layout.PLAYFIELD_LEFT - 1, centerY)));
        checks.check("S1 压在别的球上放不下",
                !rulesEngine.placeCueBall(new Vec2(Layout.FOOT_SPOT_X, Layout.FOOT_SPOT_Y)));
        checks.check("S1 被拒的放置不改阶段", rulesEngine.phase() == GamePhase.BREAK_SETUP);

        checks.check("S1 开球线之后放得下主球", rulesEngine.placeCueBall(new Vec2(150, centerY)));
        checks.check("S1 放置成功即进入瞄准", rulesEngine.phase() == GamePhase.AIMING);
        checks.check("S1 进入瞄准后不再受开球线限制", !rulesEngine.cuePlacementRestricted());

        checks.check("S1 瞄准中不能改放主球", !rulesEngine.placeCueBall(new Vec2(140, 200)));
        checks.check("S1 瞄准中改放被拒后仍是瞄准", rulesEngine.phase() == GamePhase.AIMING);
        checks.check("S1 瞄准中可以出杆", rulesEngine.takeShot(RIGHT, SPEED_POT));
        checks.check("S1 出杆后进入球在滚动", rulesEngine.phase() == GamePhase.SHOT_IN_FLIGHT);
        checks.check("S1 球在滚动时不能再出杆", !rulesEngine.takeShot(RIGHT, SPEED_POT));

        rulesEngine.startMatch();
        checks.check("S1 重新开局回到开球布置", rulesEngine.phase() == GamePhase.BREAK_SETUP);
        checks.check("S1 重新开局清空组属", rulesEngine.groupOf(Player.ONE) == null && rulesEngine.groupOf(Player.TWO) == null);
        checks.check("S1 重新开局清空判决", rulesEngine.lastVerdict() == null && rulesEngine.outcome() == null);
        checks.check("S1 重新开局又是开球杆", rulesEngine.isBreakShot());
        checks.check("S1 重新开局清空本杆证据", !rulesEngine.record().firstContactOccurred()
                && rulesEngine.record().pocketedInOrder().isEmpty());
    }

    // S2 合法开球

    /** 检查S2 摆好开球位置，将验证结果记入传入的检查报告 */
    private static ShotVerdict s02_legalBreak(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.startMatch();
        checks.check("S2 摆好开球位置",
                rulesEngine.placeCueBall(new Vec2(RackBuilder.DEFAULT_BREAK_CUE_X, Layout.playfieldCenterY())));
        checks.check("S2 满力开球被接受", rulesEngine.takeShot(RIGHT, PhysicsConfig.MAX_SHOT_SPEED));

        ShotVerdict verdict = play(rulesEngine);
        checks.checkNotNull("S2 开球产出判决书", verdict);
        if (verdict == null) {
            return null;
        }
        checks.check("S2 满力开球不算开球未达标",
                !verdict.fouls().contains(FoulType.ILLEGAL_BREAK), "犯规=" + verdict.fouls());
        checks.check("S2 满力开球自然停稳（未走超时兜底）", !rulesEngine.lastShotTimedOut());
        checks.check("S2 一杆之后不再是开球杆", !rulesEngine.isBreakShot());
        checks.check("S2 开球不定组", rulesEngine.groupOf(Player.ONE) == null && rulesEngine.groupOf(Player.TWO) == null);
        checks.check("S2 开球后球仍在台上", rulesEngine.idsOnTable().size() > 1, "在台 " + rulesEngine.idsOnTable());
        checks.check("S2 开球后进入判定阶段", rulesEngine.phase() == GamePhase.RESOLVING);
        return verdict;
    }

    // S3 开球犯规 → 开球线后的自由球

    /** 检查S3 摆局后可以出杆，将验证结果记入传入的检查报告 */
    private static ShotVerdict s03_illegalBreak(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, 650, 280),
                        new RulesEngine.BallPlacement(1, 720, 280)),
                Player.ONE, null, null, true);
        checks.check("S3 摆局后可以出杆", rulesEngine.takeShot(RIGHT, SPEED_SOFT));

        ShotVerdict verdict = play(rulesEngine);
        checks.checkNotNull("S3 轻推开球产出判决书", verdict);
        if (verdict == null) {
            return null;
        }
        checks.check("S3 轻推开球判开球未达标", verdict.fouls().contains(FoulType.ILLEGAL_BREAK),
                "犯规=" + verdict.fouls());
        checks.check("S3 开球未达标不另报「碰球后无球碰库」（避免一次犯规报两行）",
                !verdict.fouls().contains(FoulType.NO_RAIL_AFTER_CONTACT), "犯规=" + verdict.fouls());
        checks.check("S3 开球未达标的处罚是自由球", verdict.ballInHand());
        checks.check("S3 该自由球被限制在开球线后", verdict.ballInHandBehindHeadString());
        checks.check("S3 开球未达标换手", !verdict.continues() && verdict.nextPlayer() == Player.TWO);
        checks.check("S3 开球未达标不终局", !verdict.gameOver());

        settle(rulesEngine);
        checks.check("S3 横幅走完进入自由球阶段", rulesEngine.phase() == GamePhase.BALL_IN_HAND);
        checks.check("S3 自由球阶段仍受开球线限制", rulesEngine.cuePlacementRestricted());
        checks.check("S3 开球线之前仍放不下", !rulesEngine.placeCueBall(new Vec2(400, 200)));
        checks.check("S3 开球线之后放得下", rulesEngine.placeCueBall(new Vec2(150, 200)));
        checks.check("S3 放下主球后进入瞄准", rulesEngine.phase() == GamePhase.AIMING);
        checks.check("S3 放下主球后限制解除", !rulesEngine.cuePlacementRestricted());
        return verdict;
    }

    // S4 开球打进 8 号：重置，不判负，开球方续杆

    /** 检查S4 开球进 8 号产出判决书，将验证结果记入传入的检查报告 */
    private static void s04_eightOnBreak(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, LANE_X, 190),
                        new RulesEngine.BallPlacement(8, LANE_X, 120)),
                Player.ONE, null, null, true);
        rulesEngine.takeShot(UP, 1880);

        ShotVerdict verdict = play(rulesEngine);
        checks.checkNotNull("S4 开球进 8 号产出判决书", verdict);
        if (verdict == null) {
            return;
        }
        checks.check("S4 证据里 8 号确实落袋", rulesEngine.record().pocketed(8));
        checks.check("S4 开球进 8 号不判提前落袋",
                !verdict.fouls().contains(FoulType.EIGHT_POCKETED_EARLY), "犯规=" + verdict.fouls());
        checks.check("S4 开球进 8 号不判胜负", !verdict.gameOver() && rulesEngine.outcome() == null);
        checks.check("S4 8 号被摆回台面", rulesEngine.idsOnTable().contains(8), "在台 " + rulesEngine.idsOnTable());
        checks.check("S4 开球方续杆", verdict.continues() && verdict.nextPlayer() == Player.ONE);
        checks.check("S4 开球进 8 号仍不定组",
                rulesEngine.groupOf(Player.ONE) == null && rulesEngine.groupOf(Player.TWO) == null);

        settle(rulesEngine);
        checks.check("S4 横幅走完回到瞄准", rulesEngine.phase() == GamePhase.AIMING);
    }

    // S5 开放台面定组

    /** 检查S5 开放台面进球产出判决书，将验证结果记入传入的检查报告 */
    private static ShotVerdict s05_openTableAssignment(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, LANE_X, 190),
                        new RulesEngine.BallPlacement(9, LANE_X, 150)),
                Player.ONE, null, null, false);
        rulesEngine.takeShot(UP, SPEED_POT);

        ShotVerdict verdict = play(rulesEngine);
        checks.checkNotNull("S5 开放台面进球产出判决书", verdict);
        if (verdict == null) {
            return null;
        }
        checks.check("S5 开放台面上合法进球即定组", verdict.assignedGroupNow());
        checks.check("S5 先落袋的是 9 号 → 定花色", verdict.assignedGroup() == BallGroup.STRIPE);
        checks.check("S5 组属落到击球方身上", rulesEngine.groupOf(Player.ONE) == BallGroup.STRIPE);
        checks.check("S5 定组是成对的：对手同时拿到另一组",
                rulesEngine.groupOf(Player.TWO) == BallGroup.SOLID,
                "实际 " + rulesEngine.groupOf(Player.TWO));
        checks.check("S5 定组那一杆续杆", verdict.continues() && verdict.nextPlayer() == Player.ONE);
        return verdict;
    }

    // S6 定组判据（纯函数）

    /** 检查S6 先落袋的花色定花色，将验证结果记入传入的检查报告 */
    private static void s06_groupAssignerOrder(CheckHarness checks) {
        checks.check("S6 先落袋的花色定花色", GroupAssigner.assignFrom(List.of(9, 3)) == BallGroup.STRIPE);
        checks.check("S6 顺序颠倒则定全色", GroupAssigner.assignFrom(List.of(3, 9)) == BallGroup.SOLID);
        checks.check("S6 跳过主球", GroupAssigner.assignFrom(List.of(0, 9)) == BallGroup.STRIPE);
        checks.check("S6 跳过 8 号", GroupAssigner.assignFrom(List.of(8, 3)) == BallGroup.SOLID);
        checks.check("S6 只有主球与 8 号时定不了组", GroupAssigner.assignFrom(List.of(0, 8)) == null);
        checks.check("S6 空落袋清单定不了组", GroupAssigner.assignFrom(List.of()) == null);
        checks.check("S6 数在台球只数目标球",
                GroupAssigner.countOnTable(BallGroup.SOLID, List.of(0, 1, 2, 9, 10)) == 2);
        checks.check("S6 组属为 null 时不算清空", !GroupAssigner.isCleared(null, List.of(1)));
        checks.check("S6 组内球全不在台上即为清空",
                GroupAssigner.isCleared(BallGroup.SOLID, List.of(0, 8, 9)));
        checks.check("S6 组内还剩一颗就不算清空",
                !GroupAssigner.isCleared(BallGroup.SOLID, List.of(1, 9)));
    }

    // S7 先碰错球

    /** 检查S7 先碰错球产出判决书，将验证结果记入传入的检查报告 */
    private static void s07_wrongBallFirst(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, 400, 280),
                        new RulesEngine.BallPlacement(9, 470, 280),
                        new RulesEngine.BallPlacement(3, 400, 150)),
                Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);
        rulesEngine.takeShot(RIGHT, SPEED_RAIL);

        ShotVerdict verdict = play(rulesEngine);
        checks.checkNotNull("S7 先碰错球产出判决书", verdict);
        if (verdict == null) {
            return;
        }
        checks.check("S7 证据里首次触球记的是那颗错球", rulesEngine.record().firstContactBallId() == 9,
                "首触=" + rulesEngine.record().firstContactBallId());
        checks.check("S7 只报「先碰错球」这一条",
                verdict.fouls().equals(List.of(FoulType.WRONG_BALL_FIRST)), "犯规=" + verdict.fouls());
        checks.check("S7 该杆确实碰到过库（不叠加「无球碰库」）", rulesEngine.record().anyRailAfterContact());
        checks.check("S7 犯规一杆换手", !verdict.continues() && verdict.nextPlayer() == Player.TWO);
        checks.check("S7 犯规一杆给自由球", verdict.ballInHand());
        checks.check("S7 普通自由球不受开球线限制", !verdict.ballInHandBehindHeadString());
    }

    // S8 碰球后无球碰库

    /** 检查S8 轻推产出判决书，将验证结果记入传入的检查报告 */
    private static void s08_noRailAfterContact(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, 400, 280),
                        new RulesEngine.BallPlacement(3, 470, 280)),
                Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);
        rulesEngine.takeShot(RIGHT, 240.0);

        ShotVerdict verdict = play(rulesEngine);
        checks.checkNotNull("S8 轻推产出判决书", verdict);
        if (verdict == null) {
            return;
        }
        checks.check("S8 碰了球但没球碰库、也没进球 → 犯规",
                verdict.fouls().equals(List.of(FoulType.NO_RAIL_AFTER_CONTACT)), "犯规=" + verdict.fouls());
        checks.check("S8 证据里确实没有任何球碰库", !rulesEngine.record().anyRailAfterContact());
        checks.check("S8 证据里确实没有球落袋", !rulesEngine.record().anyBallPocketed());
        checks.check("S8 该杆换手并给自由球", verdict.ballInHand() && verdict.nextPlayer() == Player.TWO);
    }

    // S9 合法但没进球 → 换手

    /** 检查S9 合法无进球产出判决书，将验证结果记入传入的检查报告 */
    private static ShotVerdict s09_legalNoPot(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, 400, 280),
                        new RulesEngine.BallPlacement(3, 470, 280)),
                Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);
        rulesEngine.takeShot(RIGHT, SPEED_RAIL);

        ShotVerdict verdict = play(rulesEngine);
        checks.checkNotNull("S9 合法无进球产出判决书", verdict);
        if (verdict == null) {
            return null;
        }
        checks.check("S9 合法无进球零犯规", verdict.fouls().isEmpty(), "犯规=" + verdict.fouls());
        checks.check("S9 没进球就换手", !verdict.continues() && verdict.nextPlayer() == Player.TWO);
        checks.check("S9 没吃到自由球", !verdict.ballInHand());
        checks.check("S9 换手不改组属", rulesEngine.groupOf(Player.ONE) == BallGroup.SOLID
                && rulesEngine.groupOf(Player.TWO) == BallGroup.STRIPE);
        return verdict;
    }

    // S10 打进对手的球：不犯规，但也不续杆

    /** 检查S10 送球产出判决书，将验证结果记入传入的检查报告 */
    private static void s10_opponentBallPotted(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, LANE_X, 190),
                        new RulesEngine.BallPlacement(3, LANE_X, 150),
                        new RulesEngine.BallPlacement(9, LANE_X, 110)),
                Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);
        rulesEngine.takeShot(UP, SPEED_POT);

        ShotVerdict verdict = play(rulesEngine);
        checks.checkNotNull("S10 送球产出判决书", verdict);
        if (verdict == null) {
            return;
        }
        checks.check("S10 首次触球先碰到自己的 3 号", rulesEngine.record().firstContactBallId() == 3,
                "首触=" + rulesEngine.record().firstContactBallId());
        checks.check("S10 落袋的是对手的 9 号",
                rulesEngine.record().objectBallsPocketedInOrder().equals(List.of(9)),
                "落袋=" + rulesEngine.record().objectBallsPocketedInOrder());
        checks.check("S10 打进对手的球不算犯规", verdict.fouls().isEmpty(), "犯规=" + verdict.fouls());
        checks.check("S10 打进对手的球不续杆", !verdict.continues() && verdict.nextPlayer() == Player.TWO);
        checks.check("S10 送球不给自由球", !verdict.ballInHand());
    }

    // S11 主球落袋 → 普通自由球

    /** 检查S11 主球落袋产出判决书，将验证结果记入传入的检查报告 */
    private static void s11_cueBallScratch(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, LANE_X, 200),
                        new RulesEngine.BallPlacement(1, 200, 300)),
                Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);
        rulesEngine.takeShot(UP, SPEED_SOFT);

        ShotVerdict verdict = play(rulesEngine);
        checks.checkNotNull("S11 主球落袋产出判决书", verdict);
        if (verdict == null) {
            return;
        }
        checks.check("S11 证据里主球落袋", rulesEngine.record().cueBallPocketed());
        checks.check("S11 判主球落袋犯规", verdict.fouls().contains(FoulType.CUE_BALL_POCKETED),
                "犯规=" + verdict.fouls());
        checks.check("S11 空杆同时报「未击中任何球」", verdict.fouls().contains(FoulType.MISS_NO_CONTACT),
                "犯规=" + verdict.fouls());
        checks.check("S11 给自由球且不受开球线限制",
                verdict.ballInHand() && !verdict.ballInHandBehindHeadString());
        checks.check("S11 主球被拿下台面", !rulesEngine.idsOnTable().contains(0), "在台 " + rulesEngine.idsOnTable());

        settle(rulesEngine);
        checks.check("S11 横幅走完进入自由球阶段", rulesEngine.phase() == GamePhase.BALL_IN_HAND);
        checks.check("S11 自由球不限制在开球线后", !rulesEngine.cuePlacementRestricted());
        checks.check("S11 自由球放到台面任意合法位置", rulesEngine.placeCueBall(new Vec2(300, 300)));
        checks.check("S11 放回后主球回到台面", rulesEngine.idsOnTable().contains(0));
        checks.check("S11 放回后进入瞄准", rulesEngine.phase() == GamePhase.AIMING);
    }

    // S12 犯规的一杆即使进球也不定组

    /** 检查S12 开放台面犯规产出判决书，将验证结果记入传入的检查报告 */
    private static void s12_foulShotAssignsNothing(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, LANE_X, 190),
                        new RulesEngine.BallPlacement(8, LANE_X, 150),
                        new RulesEngine.BallPlacement(9, LANE_X, 110)),
                Player.ONE, null, null, false);
        rulesEngine.takeShot(UP, SPEED_POT);

        ShotVerdict verdict = play(rulesEngine);
        checks.checkNotNull("S12 开放台面犯规产出判决书", verdict);
        if (verdict == null) {
            return;
        }
        checks.check("S12 开放台面上先碰 8 号是犯规",
                verdict.fouls().contains(FoulType.WRONG_BALL_FIRST), "犯规=" + verdict.fouls());
        checks.check("S12 这一杆确实进了球", rulesEngine.record().anyBallPocketed());
        checks.check("S12 犯规的一杆不定组", !verdict.assignedGroupNow());
        checks.check("S12 台面组属仍为空",
                rulesEngine.groupOf(Player.ONE) == null && rulesEngine.groupOf(Player.TWO) == null);
        checks.check("S12 犯规的一杆换手", verdict.nextPlayer() == Player.TWO);
    }

    // S13 本组已清 → 打进 8 号判胜

    /** 检查S13 清组后打 8 号产出判决书，将验证结果记入传入的检查报告 */
    private static ShotVerdict s13_winWhenCleared(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, LANE_X, 190),
                        new RulesEngine.BallPlacement(8, LANE_X, 120)),
                Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);
        rulesEngine.takeShot(UP, SPEED_POT);

        ShotVerdict verdict = play(rulesEngine);
        checks.checkNotNull("S13 清组后打 8 号产出判决书", verdict);
        if (verdict == null) {
            return null;
        }
        checks.check("S13 本组已清、无犯规、8 号落袋 → 判胜", verdict.gameOver(), "判决=" + verdict);
        checks.check("S13 胜方是击球方",
                verdict.outcome() != null && verdict.outcome().winner() == Player.ONE);
        checks.check("S13 负方是对手",
                verdict.outcome() != null && verdict.outcome().loser() == Player.TWO);
        checks.check("S13 这一杆没有犯规", verdict.fouls().isEmpty(), "犯规=" + verdict.fouls());

        settle(rulesEngine);
        checks.check("S13 横幅走完进入终局", rulesEngine.phase() == GamePhase.GAME_OVER);
        checks.check("S13 终局后不再接受出杆", !rulesEngine.takeShot(UP, SPEED_POT));
        checks.check("S13 终局后不再接受放球", !rulesEngine.placeCueBall(new Vec2(300, 300)));
        checks.check("S13 终局后阶段不变", rulesEngine.phase() == GamePhase.GAME_OVER);
        return verdict;
    }

    // S14 本组未清 → 打进 8 号判负

    /** 检查S14 提前打 8 号产出判决书，将验证结果记入传入的检查报告 */
    private static void s14_loseWhenEightEarly(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, LANE_X, 190),
                        new RulesEngine.BallPlacement(3, LANE_X, 150),
                        new RulesEngine.BallPlacement(8, LANE_X, 120)),
                Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);
        rulesEngine.takeShot(UP, SPEED_POT);

        ShotVerdict verdict = play(rulesEngine);
        checks.checkNotNull("S14 提前打 8 号产出判决书", verdict);
        if (verdict == null) {
            return;
        }
        checks.check("S14 本组还剩一颗时打进 8 号判负", verdict.gameOver(), "判决=" + verdict);
        checks.check("S14 犯规表里有「提前落袋」",
                verdict.fouls().contains(FoulType.EIGHT_POCKETED_EARLY), "犯规=" + verdict.fouls());
        checks.check("S14 「提前落袋」与「同杆犯规」互斥（一次只报一条）",
                !verdict.fouls().contains(FoulType.EIGHT_WITH_FOUL), "犯规=" + verdict.fouls());
        checks.check("S14 败方是击球方",
                verdict.outcome() != null && verdict.outcome().loser() == Player.ONE);
        checks.check("S14 胜方是对手",
                verdict.outcome() != null && verdict.outcome().winner() == Player.TWO);
        checks.check("S14 败因文案点出「提前落袋」",
                verdict.outcome() != null && verdict.outcome().reasonZh().contains("提前落袋"));
        checks.check("S14 8 号落袋的那一杆主球留在台上（换手而不是自由球）",
                rulesEngine.idsOnTable().contains(0), "在台 " + rulesEngine.idsOnTable());
    }

    // S15 8 号与犯规同杆落袋 → 判负

    /** 检查S15 8 号同杆犯规产出判决书，将验证结果记入传入的检查报告 */
    private static void s15_loseWhenEightWithFoul(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        // 击球方的组已清空，合法目标只剩 8 号；先碰到 9 号即犯规，而 9 号又把 8 号撞进袋
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, LANE_X, 190),
                        new RulesEngine.BallPlacement(9, LANE_X, 150),
                        new RulesEngine.BallPlacement(8, LANE_X, 120)),
                Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);
        rulesEngine.takeShot(UP, SPEED_POT);

        ShotVerdict verdict = play(rulesEngine);
        checks.checkNotNull("S15 8 号同杆犯规产出判决书", verdict);
        if (verdict == null) {
            return;
        }
        checks.check("S15 首次触球碰到的是 9 号（组已清时非法）",
                rulesEngine.record().firstContactBallId() == 9, "首触=" + rulesEngine.record().firstContactBallId());
        checks.check("S15 8 号确实落袋", rulesEngine.record().pocketed(8));
        checks.check("S15 判负", verdict.gameOver(), "判决=" + verdict);
        checks.check("S15 犯规表里有「8 号与犯规同杆落袋」",
                verdict.fouls().contains(FoulType.EIGHT_WITH_FOUL), "犯规=" + verdict.fouls());
        checks.check("S15 同一条里仍记着那条普通犯规",
                verdict.fouls().contains(FoulType.WRONG_BALL_FIRST), "犯规=" + verdict.fouls());
        checks.check("S15 判负而不是判「提前落袋」（本方组确实已清）",
                !verdict.fouls().contains(FoulType.EIGHT_POCKETED_EARLY), "犯规=" + verdict.fouls());
        checks.check("S15 败方是击球方",
                verdict.outcome() != null && verdict.outcome().loser() == Player.ONE);
        checks.check("S15 败因文案点出那条犯规",
                verdict.outcome() != null && verdict.outcome().reasonZh().contains("先碰到的不是本方目标球"),
                outcomeReason(verdict));
    }

    /** 返回判决结果说明；尚无结果时返回提示文字 */
    private static String outcomeReason(ShotVerdict verdict) {
        if (verdict.outcome() == null) {
            return "无判决";
        }
        return verdict.outcome().reasonZh();
    }

    // S16 「同杆清组」的判据

    /**
     * 同杆清组 + 进 8 号＝胜
     *
     * <p><b>为什么这一条不摆真实局面。</b>两颗目标球要在同一杆里都落袋，从接触点看它们的
     * 进球线必须互相垂直（一颗沿法向、一颗沿切向）。本台面的六个袋位凑不出这样一对足够近的
     * 组合——垂直于某条角袋对角线的那条线只穿过另一个角袋，而那个角袋离接触点有三百多像素；
     * 球要飞三百多像素，子步粒度带来的约 4–8° 法向误差就会被放大成四十多像素的偏差，
     * 超过角袋的 36px 捕获半径。这不是「懒得摆」，是这个引擎的接触检测精度物理上够不着
     * 那对局面，硬凑出来的场景改一个摩擦常量就会翻，钉不住
     *
     * <p>所以这里直接驱动 {@link billiards.world.ShotEventListener} 接口构造事件序列
     * 判据本身（「本杆结束后本方组还剩不剩球」）是纯函数，事件序列是它的全部输入，
     * 这样测是测在点子上；而「物理报出来的落袋事件判罚读不读得懂」由 S4/S5/S10/S14/S15
     * 那些真实物理的场景覆盖
     */
    private static void s16_sameShotClear(CheckHarness checks) {
        ShotContext context = new ShotContext(Player.ONE, BallGroup.SOLID, false, Set.of(1, 8), true);

        ShotRecord cleared = new ShotRecord();
        cleared.onBallBallContact(0, 1, ORIGIN, FIXTURE_IMPACT);
        cleared.onBallPocketed(1, 0, FIXTURE_IMPACT);
        cleared.onBallPocketed(8, 0, FIXTURE_IMPACT);
        checks.check("S16 本组最后一颗与本杆的 8 号同杆落袋算清组", context.groupClearedAfter(cleared));
        ShotVerdict win = FoulDetector.judge(context, cleared);
        checks.check("S16 同杆清组 + 8 号落袋 = 胜",
                win.gameOver() && win.outcome() != null && win.outcome().winner() == Player.ONE,
                "判决=" + win);

        ShotRecord onlyEight = new ShotRecord();
        onlyEight.onBallBallContact(0, 1, ORIGIN, FIXTURE_IMPACT);
        onlyEight.onBallPocketed(8, 0, FIXTURE_IMPACT);
        checks.check("S16 本组最后一颗没进就不算清组（出杆前的局面为准）",
                !context.groupClearedAfter(onlyEight));
        ShotVerdict loseEarly = FoulDetector.judge(context, onlyEight);
        checks.check("S16 同杆没清组就进 8 号 = 负",
                loseEarly.gameOver() && loseEarly.outcome() != null
                        && loseEarly.outcome().winner() == Player.TWO,
                "判决=" + loseEarly);

        ShotRecord withScratch = new ShotRecord();
        withScratch.onBallBallContact(0, 1, ORIGIN, FIXTURE_IMPACT);
        withScratch.onBallPocketed(1, 0, FIXTURE_IMPACT);
        withScratch.onBallPocketed(8, 0, FIXTURE_IMPACT);
        withScratch.onBallPocketed(0, 0, FIXTURE_IMPACT);
        ShotVerdict loseScratch = FoulDetector.judge(context, withScratch);
        checks.check("S16 同杆清组但主球也落袋 = 负（赢球要求零犯规）",
                loseScratch.gameOver() && loseScratch.outcome() != null
                        && loseScratch.outcome().winner() == Player.TWO,
                "判决=" + loseScratch);
        checks.check("S16 这一负的理由是「8 号与犯规同杆落袋」",
                loseScratch.fouls().contains(FoulType.EIGHT_WITH_FOUL), "犯规=" + loseScratch.fouls());

        ShotContext openCtx = new ShotContext(Player.ONE, null, false, Set.of(1, 8), true);
        ShotRecord openPot = new ShotRecord();
        openPot.onBallBallContact(0, 1, ORIGIN, FIXTURE_IMPACT);
        openPot.onBallPocketed(1, 0, FIXTURE_IMPACT);
        openPot.onBallPocketed(8, 0, FIXTURE_IMPACT);
        checks.check("S16 台面未定组时永远不算清组", !openCtx.groupClearedAfter(openPot));
    }

    // S17 放置的几何判据

    /** 检查S17 几何层：台面之外放不下（左），将验证结果记入传入的检查报告 */
    private static void s17_placementGeometry(CheckHarness checks) {
        double centerY = Layout.playfieldCenterY();
        double topR = Layout.PLAYFIELD_TOP + Ball.RADIUS;
        double botR = Layout.PLAYFIELD_BOTTOM - Ball.RADIUS;

        // 分两层测，因为生产代码就是分两层的（见 Table.canPlaceBall 的注释）：
        // 几何层只问「台面内、不压球、不陷袋」，规则层再往上叠「开球必须在开球线后」
        // 混在一起测会分不清一次拒绝到底是哪一层的功劳
        Table table = new Table();
        List<Ball> empty = List.of();

        checks.check("S17 几何层：台面之外放不下（左）",
                !table.canPlaceBall(empty, new Vec2(Layout.PLAYFIELD_LEFT - 1, centerY), -1));
        checks.check("S17 几何层：台面之外放不下（上）",
                !table.canPlaceBall(empty, new Vec2(300, Layout.PLAYFIELD_TOP - 1), -1));
        checks.check("S17 几何层：台面之外放不下（右）",
                !table.canPlaceBall(empty, new Vec2(Layout.PLAYFIELD_RIGHT + 1, centerY), -1));
        checks.check("S17 几何层：陷在角袋捕获圆里放不下",
                !table.canPlaceBall(empty, new Vec2(75, 75), -1));
        checks.check("S17 几何层：陷在中袋捕获圆里放不下",
                !table.canPlaceBall(empty, new Vec2(LANE_X, 75), -1));
        checks.check("S17 几何层：贴左库内沿放得下",
                table.canPlaceBall(empty, new Vec2(Layout.PLAYFIELD_LEFT + Ball.RADIUS, centerY), -1));
        checks.check("S17 几何层：贴上库内沿（远离袋口）放得下",
                table.canPlaceBall(empty, new Vec2(300, topR), -1));
        checks.check("S17 几何层：贴下库内沿（远离袋口）放得下",
                table.canPlaceBall(empty, new Vec2(300, botR), -1));
        checks.check("S17 几何层：离另一颗球不足两个半径放不下",
                !table.canPlaceBall(List.of(new Ball(1, new Vec2(300, centerY))),
                        new Vec2(300 + 2 * Ball.RADIUS - 1, centerY), -1));
        checks.check("S17 几何层：让开的那一颗自己不算障碍",
                table.canPlaceBall(List.of(new Ball(1, new Vec2(300, centerY))), new Vec2(300, centerY), 1));

        // 规则层：开球线后是这一层唯一的额外限制
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.startMatch();
        checks.check("S17 规则层：开球线之前放得下", rulesEngine.placeCueBall(new Vec2(150, centerY)));
        rulesEngine.startMatch();
        checks.check("S17 规则层：开球线之后放不下",
                !rulesEngine.placeCueBall(new Vec2(Layout.HEAD_STRING_X + 20, centerY)));
        checks.check("S17 规则层：压着球架上的球放不下",
                !rulesEngine.placeCueBall(new Vec2(Layout.FOOT_SPOT_X, Layout.FOOT_SPOT_Y)));
        checks.check("S17 规则层：被拒的放置不改阶段", rulesEngine.phase() == GamePhase.BREAK_SETUP);
    }

    // S18 判定横幅的时长

    /** 检查S18 球停稳后进入判定阶段，将验证结果记入传入的检查报告 */
    private static void s18_bannerDuration(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, 400, 280),
                        new RulesEngine.BallPlacement(3, 470, 280)),
                Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);
        rulesEngine.takeShot(RIGHT, SPEED_RAIL);
        play(rulesEngine);
        checks.check("S18 球停稳后进入判定阶段", rulesEngine.phase() == GamePhase.RESOLVING);

        int ticks = 0;
        while (rulesEngine.phase() == GamePhase.RESOLVING && ticks < MAX_BANNER_TICKS) {
            rulesEngine.tickFixed();
            ticks = ticks + 1;
        }
        checks.checkNear("S18 判定横幅恰好持续 VERDICT_BANNER_MS",
                Layout.VERDICT_BANNER_MS / PhysicsConfig.FIXED_STEP_MS, ticks, 0.5);
        checks.check("S18 横幅期间不出杆、不换阶段（只在最后一步切换）",
                rulesEngine.phase() == GamePhase.AIMING, "实际 " + rulesEngine.phase());
        checks.check("S18 横幅走完换手生效", rulesEngine.currentPlayer() == Player.TWO);
    }

    // S19 模拟超时处理

    /** 检查球尚未停稳时能被超时限制停止，将结果记入报告 */
    private static void s19_timeoutFallback(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, 400, 280),
                        new RulesEngine.BallPlacement(3, 470, 280)),
                Player.ONE, BallGroup.SOLID, BallGroup.STRIPE, false);
        double timeout = 50.0;
        rulesEngine.setShotTimeoutSimMs(timeout);
        rulesEngine.takeShot(RIGHT, PhysicsConfig.MAX_SHOT_SPEED);

        ShotVerdict verdict = play(rulesEngine);
        checks.check("S19 球没停稳就被截断（走了兜底分支）", rulesEngine.lastShotTimedOut());
        checks.checkNear("S19 截断正好发生在超时上限", timeout, rulesEngine.shotSimMs(), 1e-9);
        checks.check("S19 截断后照样出判决书", verdict != null, "判决=null");
        checks.check("S19 截断后所有球被强制停住", allAtRest(rulesEngine));
        checks.check("S19 截断后进入判定阶段而非卡在球在滚动",
                rulesEngine.phase() == GamePhase.RESOLVING, "实际 " + rulesEngine.phase());

        // 正常杆绝不能走到这条分支——上面 S2 已经断言过满力开球不停超时
        checks.check("S19 默认超时上限是物理配置里的值",
                PhysicsConfig.SHOT_TIMEOUT_SIM_MS > 5000.0);
    }

    // S20 判决书文案

    /** 检查S20 开球犯规文案点出犯规与自由球，将验证结果记入传入的检查报告 */
    private static void s20_messageZh(CheckHarness checks, ShotVerdict vIllegalBreak,
                                      ShotVerdict vAssign, ShotVerdict vPass, ShotVerdict vWin) {
        if (vIllegalBreak != null) {
            String msg = vIllegalBreak.messageZh();
            checks.check("S20 开球犯规文案点出犯规与自由球",
                    msg.contains("犯规") && msg.contains("自由球"), msg);
            checks.check("S20 开球犯规文案点出「须置于开球线后」", msg.contains("开球线后"), msg);
            checks.check("S20 开球犯规文案点出下一手是玩家 2", msg.contains("玩家 2"), msg);
            checks.check("S20 自由球消息在玩家编号后保留空格", msg.contains("玩家 2 获自由球"), msg);
        }
        if (vAssign != null) {
            String msg = vAssign.messageZh();
            checks.check("S20 定组文案点出组别", msg.contains("花色"), msg);
            checks.check("S20 定组文案点出击球方", msg.contains("玩家 1"), msg);
            checks.check("S20 定组消息在玩家编号后保留空格", msg.contains("玩家 1 打进"), msg);
        }
        if (vPass != null) {
            String msg = vPass.messageZh();
            checks.check("S20 换手消息在玩家名称前后保留空格",
                    msg.startsWith("换手： 玩家 ") && msg.endsWith(" 击球"), msg);
        }
        if (vWin != null && vWin.outcome() != null) {
            String msg = vWin.messageZh();
            checks.check("S20 胜负文案点出胜者", msg.contains("玩家 1") && msg.contains("获胜"), msg);
            checks.check("S20 胜负文案点出败因", msg.contains("8 号"), msg);
        }

        // 犯规文案里的多条犯规要全部列出，不能只报第一条
        ShotRecord two = new ShotRecord();
        two.onBallPocketed(0, 0, FIXTURE_IMPACT);
        ShotContext context = new ShotContext(Player.ONE, BallGroup.SOLID, false, Set.of(1, 3), true);
        ShotVerdict vTwo = FoulDetector.judge(context, two);
        checks.check("S20 一次触发两条犯规时两条都列出来",
                vTwo.fouls().size() >= 2
                        && vTwo.messageZh().contains("未击中任何球")
                        && vTwo.messageZh().contains("主球落袋"),
                vTwo.messageZh());

        ShotVerdict continuation = new ShotVerdict(
                Player.TWO, List.of(), null, Player.TWO, true, null);
        checks.check("S20 续杆消息在玩家编号后保留空格",
                continuation.messageZh().equals("玩家 2 继续击球"), continuation.messageZh());
        checks.check("S20 普通自由球消息在玩家编号后保留空格",
                vTwo.messageZh().contains("玩家 2 获自由球"), vTwo.messageZh());
    }

    // S21 定组是成对的

    /**
     * 定组从来不是一个人的事：全色与花色是一对补集（{@link BallGroup#oppositeGroup()}），
     * 一方拿到哪一组，另一方<b>当场</b>就拿到另一组——「一方已定组、另一方未定组」
     * 不是一个合法局面
     *
     * <p>这条曾经是错的：{@code applyVerdict} 只把组属写到击球方那一格，对手那一格留 {@code null}
     * 表面症状只是 HUD 一直显示「未定组」，实际后果重得多——{@code takeShot} 用
     * {@code groupOf(击球方)} 构造 {@link ShotContext}，对手拿到的是一份「台面未定组」的快照：
     * <ul>
     *   <li>合法目标变成除 8 号外的<b>所有</b>球，先碰对方的球不算犯规；</li>
     *   <li>打进任何球都算「打进本方球」，于是打进对方的球还能续杆；</li>
     *   <li>因为 {@code tableOpen()} 为真，这一杆还会给对手<b>再定一次组</b>——若他打进的是对方那组，
     *       两人会同时拥有同一组；</li>
     *   <li>{@code groupClearedBefore()} 永远为假，对手清完本组也打不了 8 号——<b>等于永远赢不了</b></li>
     * </ul>
     * 所以断言分两层：组属本身成对，以及对手看到的那份局面快照不再是「未定组」
     *
     * <p>自检原先漏掉它，是因为两处巧合：{@code arrange} 这个摆局口子总是把两组一起写进去，
     * 而 S5 甚至把「对手仍无组属」当成正确结果断言了下来。测试把 bug 写成了规格，
     * 于是 518 项全绿
     */
    private static void s21_assignmentIsPaired(CheckHarness checks) {
        RulesEngine rulesEngine = new RulesEngine();
        rulesEngine.arrange(List.of(
                        new RulesEngine.BallPlacement(0, LANE_X, 190),
                        new RulesEngine.BallPlacement(9, LANE_X, 150),    // 花色，本杆将被 1 号打进
                        new RulesEngine.BallPlacement(3, 270, 300),       // 全色，留在台上
                        new RulesEngine.BallPlacement(11, 730, 300)),     // 花色，留在台上
                Player.ONE, null, null, false);
        rulesEngine.takeShot(UP, SPEED_POT);

        ShotVerdict verdict = play(rulesEngine);
        checks.checkNotNull("S21 定组那一杆产出判决书", verdict);
        if (verdict == null) {
            return;
        }
        checks.check("S21 本杆定的组是花色", verdict.assignedGroupNow() && verdict.assignedGroup() == BallGroup.STRIPE);
        checks.check("S21 击球方拿到花色", rulesEngine.groupOf(Player.ONE) == BallGroup.STRIPE);
        checks.check("S21 对手同时拿到全色，而不是未定组",
                rulesEngine.groupOf(Player.TWO) == BallGroup.SOLID,
                "实际 " + rulesEngine.groupOf(Player.TWO));
        checks.check("S21 两人的组互为补集",
                rulesEngine.groupOf(Player.TWO) == rulesEngine.groupOf(Player.ONE).oppositeGroup());

        // 对手这一杆会看到的那份快照——表达式与 RulesEngine.takeShot 里那一行完全相同
        ShotContext ctxTwo = ShotContext.of(Player.TWO, rulesEngine.groupOf(Player.TWO), false, rulesEngine.balls());
        checks.check("S21 对手的快照认为台面已定组", !ctxTwo.tableOpen());
        checks.check("S21 对手的对方组是花色", ctxTwo.opponentGroup() == BallGroup.STRIPE);
        checks.check("S21 对手的合法目标只有全色，不含花色",
                ctxTwo.legalTargets(rulesEngine.record()).equals(Set.of(3)),
                "实际 " + ctxTwo.legalTargets(rulesEngine.record()));

        // 「对手清完本组就能打 8 号」——定组那一格若是 null，这一条永远不成立，
        // 对手也就永远赢不了。组属仍取自真实引擎，只有球号集合是手搭的
        ShotContext ctxCleared = new ShotContext(
                Player.TWO, rulesEngine.groupOf(Player.TWO), false, Set.of(BallCatalog.EIGHT_ID), true);
        checks.check("S21 对手清完本组后 8 号是合法目标",
                ctxCleared.groupClearedBefore(rulesEngine.record())
                        && ctxCleared.legalTargets(rulesEngine.record()).equals(Set.of(BallCatalog.EIGHT_ID)),
                "合法目标 " + ctxCleared.legalTargets(rulesEngine.record()));
    }

    // 驱动

    /** 一直推进到本杆结束（球停稳或超时截断），返回判决书 */
    private static ShotVerdict play(RulesEngine rulesEngine) {
        int ticks = 0;
        while (rulesEngine.phase() == GamePhase.SHOT_IN_FLIGHT && ticks < MAX_TICKS) {
            rulesEngine.tickFixed();
            ticks = ticks + 1;
        }
        return rulesEngine.lastVerdict();
    }

    /** 走完判定横幅，进入下一手 */
    private static void settle(RulesEngine rulesEngine) {
        int ticks = 0;
        while (rulesEngine.phase() == GamePhase.RESOLVING && ticks < MAX_BANNER_TICKS) {
            rulesEngine.tickFixed();
            ticks = ticks + 1;
        }
    }

    /** 检查当前引擎的球是否全部停止 */
    private static boolean allAtRest(RulesEngine rulesEngine) {
        for (Ball ball : rulesEngine.balls()) {
            if (ball.speed() != 0.0) {
                return false;
            }
        }
        return true;
    }
}
