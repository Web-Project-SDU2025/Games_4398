package billiards.selfcheck;

import billiards.app.MatchSession;
import billiards.render.AimView;
import billiards.render.BallView;
import billiards.render.GameRenderer;
import billiards.render.GameStateView;
import billiards.render.MatchView;
import billiards.rules.Achievement;
import billiards.rules.FoulDetector;
import billiards.rules.GamePhase;
import billiards.rules.MatchAchievements;
import billiards.rules.MatchOutcome;
import billiards.rules.Player;
import billiards.rules.RackBuilder;
import billiards.rules.RulesEngine;
import billiards.rules.ShotContext;
import billiards.rules.ShotRecord;
import billiards.rules.ShotVerdict;
import billiards.world.Ball;
import billiards.world.BallGroup;
import billiards.world.Layout;
import billiards.world.Table;
import billiards.world.Vec2;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;

/** 检查成就的事件顺序、重复解锁、对局接线与画面，以及预览缓存是否及时更新 */
final class AchievementChecks {
    /** 构造事件时使用的接触速度，单位为逻辑像素每秒 */
    private static final double IMPACT_SPEED = 500.0;
    /** 真实进袋检查的出杆速度，单位为逻辑像素每秒 */
    private static final double POT_SPEED = 1040.0;
    /** 真实击球检查允许的最大模拟步数，复用界面检查的超时边界 */
    private static final int MAX_SHOT_TICKS = UIFixtures.MAX_SHOT_TICKS;

    /** 工具类只提供自检入口，不需要创建对象 */
    private AchievementChecks() {
    }

    /** 检查成就与缓存行为，将结果记录到传入的报告 */
    static void run(CheckHarness checks) {
        checkMultiplePots(checks);
        checkRailOrder(checks);
        checkRunsAndReset(checks);
        checkEngineAndCache(checks);
        checkRendering(checks);
    }

    /** 检查多球成就只计算本方球，且犯规杆不奖励 */
    private static void checkMultiplePots(CheckHarness checks) {
        MatchAchievements achievements = new MatchAchievements();
        ShotContext context = context(Player.ONE, BallGroup.SOLID, false);
        ShotRecord doublePot = pots(new int[]{1, 2, 9});
        accept(achievements, context, doublePot);
        checks.check("成就：一杆两颗本方球解锁双收", achievements.has(Player.ONE, Achievement.DOUBLE_POT));
        checks.check("成就：对方球不计入三收", !achievements.has(Player.ONE, Achievement.TRIPLE_POT));
        checks.check("成就：提示保留玩家编号两侧空格", achievements.notice().startsWith("玩家 1 · "));
        accept(achievements, context, pots(new int[]{1, 2}));
        checks.check("成就：同一项本局不重复提示", achievements.notice().isEmpty());

        achievements.reset();
        accept(achievements, context, pots(new int[]{1, 2, 3}));
        checks.check("成就：三颗本方球解锁三收", achievements.has(Player.ONE, Achievement.TRIPLE_POT));
        checks.check("成就：三收不同时刷出双收", !achievements.has(Player.ONE, Achievement.DOUBLE_POT));

        achievements.reset();
        ShotRecord foul = pots(new int[]{1, 2, 0});
        accept(achievements, context, foul);
        checks.check("成就：主球落袋时不奖励双收", !achievements.has(Player.ONE, Achievement.DOUBLE_POT));
        accept(achievements, context, pots(new int[]{1, 1}));
        checks.check("成就：重复球号不能凑成双收", !achievements.has(Player.ONE, Achievement.DOUBLE_POT));

        achievements.reset();
        ShotContext open = context(Player.TWO, null, false);
        accept(achievements, open, pots(new int[]{9, 10, 1}));
        checks.check("成就：定组杆按新球组计数", achievements.has(Player.TWO, Achievement.DOUBLE_POT));
        checks.check("成就：双方解锁记录独立", !achievements.has(Player.ONE, Achievement.DOUBLE_POT));
        achievements.reset();
        accept(achievements, context(Player.ONE, null, true), pots(new int[]{1}));
        checks.check("成就：合法开球进球解锁开门红", achievements.has(Player.ONE, Achievement.OPENING_POT));
    }

    /** 检查碰库必须早于落袋或首次触球，防止把事后反弹误判成技巧球 */
    private static void checkRailOrder(CheckHarness checks) {
        MatchAchievements achievements = new MatchAchievements();
        ShotContext context = context(Player.ONE, BallGroup.SOLID, false);
        ShotRecord bank = new ShotRecord();
        bank.onBallBallContact(0, 1, Vec2.ZERO, IMPACT_SPEED);
        bank.onCushionContact(1, 0, IMPACT_SPEED);
        bank.onBallPocketed(1, 0, IMPACT_SPEED);
        accept(achievements, context, bank);
        checks.check("成就：目标球先碰库后进袋解锁翻袋", achievements.has(Player.ONE, Achievement.BANK_POT));

        achievements.reset();
        ShotRecord lateRail = pots(new int[]{1});
        lateRail.onCushionContact(1, 0, IMPACT_SPEED);
        accept(achievements, context, lateRail);
        checks.check("成就：落袋后的碰库不算翻袋", !achievements.has(Player.ONE, Achievement.BANK_POT));
        checks.check("成就：触球后的主球碰库不算勾球", !achievements.has(Player.ONE, Achievement.KICK_POT));

        ShotRecord kick = new ShotRecord();
        kick.onCushionContact(0, 0, IMPACT_SPEED);
        kick.onBallBallContact(0, 1, Vec2.ZERO, IMPACT_SPEED);
        kick.onBallPocketed(1, 0, IMPACT_SPEED);
        accept(achievements, context, kick);
        checks.check("成就：主球先碰库后合法进球解锁勾球", achievements.has(Player.ONE, Achievement.KICK_POT));
        kick.reset();
        checks.check("成就：每杆重置会清空先后顺序证据",
                !kick.cueRailBeforeFirstContact() && !kick.bankedBeforePocket(1));
    }

    /** 检查连击按杆计数，换手和犯规中断，成就记录与短时提示独立 */
    private static void checkRunsAndReset(CheckHarness checks) {
        MatchAchievements achievements = new MatchAchievements();
        ShotContext first = context(Player.ONE, BallGroup.SOLID, false);
        accept(achievements, first, pots(new int[]{1, 2, 3}));
        checks.check("成就：同杆三球不等于连续三杆", !achievements.has(Player.ONE, Achievement.THREE_SHOT_RUN));
        accept(achievements, first, pots(new int[]{4}));
        accept(achievements, first, pots(new int[]{5}));
        checks.check("成就：连续三杆进球解锁连下三城", achievements.has(Player.ONE, Achievement.THREE_SHOT_RUN));
        achievements.tick(5000.0);
        checks.check("成就：提示到期后自动消失", achievements.notice().isEmpty());
        checks.check("成就：提示消失后结算记录仍在", achievements.summary(Player.ONE).contains("连下三城"));

        achievements.reset();
        accept(achievements, first, pots(new int[]{1}));
        accept(achievements, first, pots(new int[]{2}));
        accept(achievements, first, pots(new int[0]));
        accept(achievements, first, pots(new int[]{3}));
        checks.check("成就：未进球换手会中断连击", !achievements.has(Player.ONE, Achievement.THREE_SHOT_RUN));
        accept(achievements, first, pots(new int[]{4, 0}));
        accept(achievements, first, pots(new int[]{5}));
        checks.check("成就：犯规也会中断连击", !achievements.has(Player.ONE, Achievement.THREE_SHOT_RUN));
        achievements.reset();
        checks.check("成就：新局清空双方记录和提示",
                achievements.summary(Player.ONE).isEmpty() && achievements.summary(Player.TWO).isEmpty()
                        && achievements.notice().isEmpty());
    }

    /** 让真实引擎完成一次定组进球，并检查静止预览复用和外部摆局后的缓存失效 */
    private static void checkEngineAndCache(CheckHarness checks) {
        RulesEngine engine = new RulesEngine();
        engine.arrange(List.of(new RulesEngine.BallPlacement(0, 500, 190),
                        new RulesEngine.BallPlacement(1, 500, 150)),
                Player.ONE, null, null, false);
        engine.takeShot(new Vec2(0, -1), POT_SPEED);
        finishShot(engine);
        checks.check("成就：真实判决产生进球结果", engine.lastVerdict() != null && engine.lastVerdict().continues());
        checks.check("成就：定组消息来自正式对局", engine.lastVerdict() != null
                && engine.lastVerdict().messageZh().contains("玩家 1 打进"));

        engine.arrange(List.of(new RulesEngine.BallPlacement(0, 500, 190),
                        new RulesEngine.BallPlacement(1, 500, 150)),
                Player.TWO, null, null, true);
        engine.takeShot(new Vec2(0, -1), POT_SPEED);
        finishShot(engine);
        checks.check("成就：真实开球进袋由引擎解锁开门红",
                engine.achievements().has(Player.TWO, Achievement.OPENING_POT));
        checks.check("成就：真实判决将奖励记给出杆者",
                engine.achievements().notice().equals("玩家 2 · 开门红"));
        engine.startMatch();
        checks.check("成就：引擎重开会清空上一局成就",
                engine.achievements().summary(Player.TWO).isEmpty());

        MatchSession session = new MatchSession();
        session.resetMatch();
        session.engine().placeCueBall(new Vec2(190, Layout.playfieldCenterY()));
        session.tickFixed();
        GameStateView previous = session.view();
        session.tickFixed();
        checks.check("缓存：静止球快照可以安全复用", previous.ball(0) == session.view().ball(0));
        checks.check("缓存：静止瞄准预览不重复计算", previous.aim() == session.view().aim());
        Ball target = RackBuilder.ballById(session.engine().balls(), 1);
        target.setPosition(new Vec2(400, Layout.playfieldCenterY()));
        session.tickFixed();
        checks.check("缓存：目标球移动后重新计算预览", previous.aim() != session.view().aim());
        checks.check("缓存：新预览使用新的目标位置", session.view().aim().ghost() != null
                && session.view().aim().ghost().x < 400);
        checks.check("缓存：旧快照中的球位不被新局面改写", previous.ball(1).x() != session.view().ball(1).x());
    }

    /** 验证成就真的画到提示区并可按开关导出截图，结算摘要也走正式渲染器 */
    private static void checkRendering(CheckHarness checks) {
        MatchAchievements achievements = new MatchAchievements();
        ShotRecord record = new ShotRecord();
        record.onCushionContact(0, 0, IMPACT_SPEED);
        record.onBallBallContact(0, 1, Vec2.ZERO, IMPACT_SPEED);
        record.onCushionContact(1, 0, IMPACT_SPEED);
        record.onBallPocketed(1, 0, IMPACT_SPEED);
        record.onBallPocketed(2, 0, IMPACT_SPEED);
        record.onBallPocketed(3, 0, IMPACT_SPEED);
        accept(achievements, context(Player.ONE, BallGroup.SOLID, false), record);

        Table table = new Table();
        GameRenderer renderer = new GameRenderer();
        GameStateView base = CheckImages.rackView(AimView.NONE, 0, false, "");
        BallView[] balls = new BallView[base.ballCount()];
        for (int index = 0; index < balls.length; index = index + 1) {
            balls[index] = base.ball(index);
        }
        MatchView match = CheckImages.matchWithBanner(GamePhase.RESOLVING,
                "玩家 1 继续击球", MatchView.Tone.LEGAL);
        GameStateView decorated = new GameStateView(balls, AimView.NONE, 0, false, "", match,
                achievements.notice(), "");
        BufferedImage normal = CheckImages.render(renderer, table,
                new GameStateView(balls, AimView.NONE, 0, false, "", match));
        BufferedImage notice = CheckImages.render(renderer, table, decorated);
        checks.check("成就：提示实际绘制到上方区域",
                CheckImages.diffCount(normal, notice, 0, 70, Layout.WINDOW_W, 150) > 5000);
        checks.check("成就：提示不遮盖中央判罚横幅",
                CheckImages.diffCount(normal, notice, 0, 240, Layout.WINDOW_W, 320) == 0);
        MatchView over = CheckImages.matchGameOver(MatchOutcome.wonBy(Player.ONE, "本方球已清，8 号落袋"));
        BufferedImage result = CheckImages.render(renderer, table, new GameStateView(
                balls, AimView.NONE, 0, false, "", over, "", achievements.summary(Player.ONE)));
        String path = System.getProperty("billiards.screenshot");
        if (path != null && !path.isBlank()) {
            RenderingChecks.writePng(RenderingChecks.sibling(path, "-achievement"), notice);
            RenderingChecks.writePng(RenderingChecks.sibling(path, "-achievement-over"), result);
        }
    }

    /** 创建含两组球的出杆前局面，参数提供击球方、球组和开球标记 */
    private static ShotContext context(Player shooter, BallGroup group, boolean breakShot) {
        return new ShotContext(shooter, group, breakShot,
                Set.of(1, 2, 3, 4, 5, 6, 7, 9, 10, 11), false);
    }

    /** 构造合法首次触球与落袋记录，空数组代表合法碰库但没有进球 */
    private static ShotRecord pots(int[] ids) {
        ShotRecord record = new ShotRecord();
        int firstContact = 1;
        if (ids.length > 0) {
            firstContact = ids[0];
        }
        record.onBallBallContact(0, firstContact, Vec2.ZERO, IMPACT_SPEED);
        record.onCushionContact(0, 0, IMPACT_SPEED);
        for (int ballId : ids) {
            record.onBallPocketed(ballId, 0, IMPACT_SPEED);
        }
        return record;
    }

    /** 使用正式判罚生成判决，再交给成就记录器，避免测试凭空假定一杆合法 */
    private static void accept(MatchAchievements achievements, ShotContext context, ShotRecord record) {
        ShotVerdict verdict = FoulDetector.judge(context, record);
        achievements.accept(context, record, verdict);
    }

    /** 推进真实物理直到本杆结束，到达统一超时边界后停止等待 */
    private static void finishShot(RulesEngine engine) {
        int ticks = 0;
        while (engine.phase() == GamePhase.SHOT_IN_FLIGHT && ticks < MAX_SHOT_TICKS) {
            engine.tickFixed();
            ticks = ticks + 1;
        }
    }
}
