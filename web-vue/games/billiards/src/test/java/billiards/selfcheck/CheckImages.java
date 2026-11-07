package billiards.selfcheck;

import billiards.input.AimCalculator;
import billiards.render.AimView;
import billiards.render.BallView;
import billiards.render.GameRenderer;
import billiards.render.GameStateView;
import billiards.render.MatchView;
import billiards.render.Theme;
import billiards.rules.GamePhase;
import billiards.rules.MatchOutcome;
import billiards.rules.Player;
import billiards.rules.RackBuilder;
import billiards.world.Ball;
import billiards.world.BallCatalog;
import billiards.world.BallGroup;
import billiards.world.Layout;
import billiards.world.Table;
import billiards.world.Vec2;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 自检共用的位图夹具：把「造一份快照」与「把它画成图」这两件事收在一处
 *
 * <p>M3 之后有两组检查都要画整帧（R 组查表格与球，U 组查瞄准线与力度条是否真的上了屏）
 * 如果各自写一份渲染代码，就等于各写一份「什么叫一帧」的定义——两边会慢慢长歪，
 * 而且都会偏离 {@code GameRenderer} 真正做的事。<b>这里只做两件事：调生产代码、数像素</b>
 * 它自己不做任何绘制决策，所以不可能成为第二个真源
 */
final class CheckImages {

    private static final int[] NO_IDS = new int[0];
    private static final String[] NO_LINES = new String[0];

    /** 工具类只提供静态方法，不需要创建对象 */
    private CheckImages() {
    }

    /** 一张填了窗口底色的画布。{@code GameRenderer} 不负责铺底，那是 {@code GamePanel} 的事 */
    static BufferedImage blank() {
        BufferedImage image = new BufferedImage(
                Layout.WINDOW_W, Layout.WINDOW_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Theme.WINDOW_BG);
            graphics.fillRect(0, 0, Layout.WINDOW_W, Layout.WINDOW_H);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** 用生产的 {@link GameRenderer} 画一整帧 */
    static BufferedImage render(GameRenderer renderer, Table table, GameStateView view) {
        BufferedImage image = blank();
        Graphics2D graphics = image.createGraphics();
        try {
            renderer.draw(graphics, table, view);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** 与窗口底色不同的像素数。用来抓「什么都没画」 */
    static int nonBackgroundCount(BufferedImage image) {
        int background = Theme.WINDOW_BG.getRGB();
        int count = 0;
        for (int y = 0; y < image.getHeight(); y = y + 1) {
            for (int x = 0; x < image.getWidth(); x = x + 1) {
                if (image.getRGB(x, y) != background) {
                    count = count + 1;
                }
            }
        }
        return count;
    }

    /**
     * 两张同尺寸位图里不同的像素数
     *
     * <p>这是本工程检查「某个状态确实到达了屏幕」的通用手法：只改一个状态分量、连画两帧，
     * 断言两帧<b>不同</b>。比「在图上找某个颜色」稳得多——后者要预测抗锯齿与压盖之后
     * 像素究竟长什么样，前者只需要两帧不一样
     */
    static int diffCount(BufferedImage image, BufferedImage imageValue) {
        if (image.getWidth() != imageValue.getWidth() || image.getHeight() != imageValue.getHeight()) {
            return -1;
        }
        int count = 0;
        for (int y = 0; y < image.getHeight(); y = y + 1) {
            for (int x = 0; x < image.getWidth(); x = x + 1) {
                if (image.getRGB(x, y) != imageValue.getRGB(x, y)) {
                    count = count + 1;
                }
            }
        }
        return count;
    }

    /**
     * 只数给定矩形内不同的像素
     *
     * <p>用来把某个绘制物的贡献<b>从整帧里隔离出来</b>。整帧差值只能回答「有东西变了」；
     * 想断言「球杆画出来了」就必须把比较范围缩到只有球杆会出现的那块地方，否则虚影球、
     * 分离线、力度条的像素都会替它把差值撑上去——那样的话球杆根本没画也照样通过
     *
     * <p>矩形按窗口边界裁剪，越界部分不算
     */
    static int diffCount(BufferedImage image, BufferedImage imageValue, int x0, int y0, int x1, int y1) {
        if (image.getWidth() != imageValue.getWidth() || image.getHeight() != imageValue.getHeight()) {
            return -1;
        }
        int left = Math.max(0, Math.min(x0, x1));
        int top = Math.max(0, Math.min(y0, y1));
        int right = Math.min(image.getWidth(), Math.max(x0, x1));
        int bottom = Math.min(image.getHeight(), Math.max(y0, y1));
        int count = 0;
        for (int y = top; y < bottom; y = y + 1) {
            for (int x = left; x < right; x = x + 1) {
                if (image.getRGB(x, y) != imageValue.getRGB(x, y)) {
                    count = count + 1;
                }
            }
        }
        return count;
    }

    /** 逐像素的 64 位指纹，用来判「两张 sprite 是不是同一张图」 */
    static long fingerprint(BufferedImage image) {
        long height = 0xcbf29ce484222325L;
        for (int y = 0; y < image.getHeight(); y = y + 1) {
            for (int x = 0; x < image.getWidth(); x = x + 1) {
                height = (height ^ image.getRGB(x, y)) * 0x100000001b3L;
            }
        }
        return height;
    }

    // 快照夹具

    /** 标准摆球的一帧。全部球都在台上，对局信息取「一局正在打」的中性值 */
    static GameStateView rackView(AimView aim, double power, boolean shotInFlight, String status) {
        return rackView(aim, power, shotInFlight, status, match(GamePhase.AIMING));
    }

    /** 同上，但对局信息由调用方给定——M6 的浮层与托盘像素检查都要靠这一条 */
    static GameStateView rackView(AimView aim, double power, boolean shotInFlight, String status,
                                  MatchView match) {
        List<Ball> balls = RackBuilder.buildRack();
        // 拖放主球时真主球要让位给虚影——这一条必须在这里也成立，否则夹具造出的画面
        // **在生产里根本不会出现**（屏幕上会同时有两颗主球），拿它导出的截图也就说明了
        // 一个不存在的情形。判据与 MatchSession.publish 逐字相同：虚影真的画得出来才让位
        boolean ghostDrawn = match.hasPlacement();
        BallView[] views = new BallView[balls.size()];
        for (int index = 0; index < views.length; index = index + 1) {
            Ball ball = balls.get(index);
            Vec2 position = ball.position();
            boolean onTable = !(ghostDrawn && ball.id() == BallCatalog.CUE_ID);
            views[index] = new BallView(ball.id(), position.x, position.y, onTable);
        }
        return new GameStateView(views, aim, power, shotInFlight, status, match);
    }

    // ---- 对局信息夹具 ----

    /**
     * 对局信息的夹具族：每个只改一个字段，其余取「一局正在打」的中性值
     *
     * <p>它们的存在理由和一帧夹具一样，但更具体：浮层、横幅、托盘、待放虚影各自只在
     * <b>特定字段</b>下才画出来。想断言「它确实画了」，就必须先造出一帧它该出现的画面，
     * 再和同一批球的另一帧互比（见 {@code UIChecks.checkStateReachesScreen}）
     *
     * <p>阶段一律显式传进来、不用默认值：{@code MATCH_START} 与 {@code GAME_OVER} 会招来
     * 整屏浮层，而浮层会把底下所有像素都压暗一层——拿错阶段的帧去比对，差值全被那层底色
     * 吃掉（同类理由见 {@link MatchView#NONE}）
     */
    static MatchView match(GamePhase phase) {
        return matchOf(phase, Player.ONE, null, null, NO_IDS, NO_IDS, true,
                MatchView.Tone.NEUTRAL, null, "", MatchView.Tone.NEUTRAL,
                false, false, false, Double.NaN, Double.NaN, NO_LINES);
    }

    /** 带判定横幅的一帧 */
    static MatchView matchWithBanner(GamePhase phase, String banner, MatchView.Tone tone) {
        return matchOf(phase, Player.ONE, null, null, NO_IDS, NO_IDS, true,
                tone, null, banner, tone,
                false, false, false, Double.NaN, Double.NaN, NO_LINES);
    }

    /** 托盘里有球的一帧：组属已定，落袋球按各自的组进托盘 */
    static MatchView matchWithTray(BallGroup groupOne, BallGroup groupTwo,
                                   int[] pottedOne, int[] pottedTwo) {
        return matchOf(GamePhase.AIMING, Player.TWO, groupOne, groupTwo,
                pottedOne, pottedTwo, false, MatchView.Tone.NEUTRAL, null, "",
                MatchView.Tone.NEUTRAL, false, false, false, Double.NaN, Double.NaN, NO_LINES);
    }

    /** 正在拖放主球的一帧：虚影的位置与合法性都由调用方给定 */
    static MatchView matchPlacing(double x, double y, boolean valid, boolean restricted) {
        return matchOf(GamePhase.BALL_IN_HAND, Player.TWO, null, null, NO_IDS, NO_IDS, true,
                MatchView.Tone.FOUL, null, "", MatchView.Tone.NEUTRAL,
                true, restricted, valid, x, y, NO_LINES);
    }

    /** 终局的一帧 */
    static MatchView matchGameOver(MatchOutcome outcome) {
        return matchOf(GamePhase.GAME_OVER, Player.ONE, BallGroup.SOLID, BallGroup.STRIPE,
                NO_IDS, NO_IDS, false, MatchView.Tone.WIN, outcome, "",
                MatchView.Tone.NEUTRAL, false, false, false, Double.NaN, Double.NaN, NO_LINES);
    }

    /** 调试浮层开着的一帧 */
    static MatchView matchDebug() {
        return matchOf(GamePhase.AIMING, Player.ONE, null, null, NO_IDS, NO_IDS, true,
                MatchView.Tone.NEUTRAL, null, "", MatchView.Tone.NEUTRAL,
                false, false, false, Double.NaN, Double.NaN,
                new String[]{"阶段 AIMING（瞄准中）", "在台 16 颗 · 目标球 7"});
    }

    /** 把 16 个参数收在一处，上面每个夹具只写自己关心的那几个 */
    private static MatchView matchOf(GamePhase phase, Player current,
                                     BallGroup groupOne, BallGroup groupTwo,
                                     int[] pottedOne, int[] pottedTwo, boolean eightOnTable,
                                     MatchView.Tone statusTone, MatchOutcome outcome,
                                     String banner, MatchView.Tone bannerTone,
                                     boolean placing, boolean restricted, boolean valid,
                                     double pixelX, double pixelY, String[] debugLines) {
        return new MatchView(phase, current, groupOne, groupTwo, pottedOne, pottedTwo,
                eightOnTable, statusTone, outcome, banner, bannerTone,
                placing, restricted, valid, pixelX, pixelY, debugLines);
    }

    /**
     * 同一批球，但全部标成已落袋（球心坐标还留在原来位置）
     *
     * <p>它和「一颗球都没有」应当画出完全一样的图——这正是用来查「绘制时有没有漏掉
     * {@code onTable} 守卫」的判据，见 {@code RenderingChecks}
     */
    static GameStateView rackViewAllOffTable() {
        List<Ball> balls = RackBuilder.buildRack();
        BallView[] views = new BallView[balls.size()];
        for (int index = 0; index < views.length; index = index + 1) {
            Ball ball = balls.get(index);
            Vec2 position = ball.position();
            views[index] = new BallView(ball.id(), position.x, position.y, false);
        }
        return new GameStateView(views, AimView.NONE, 0.0, false, "");
    }

    /**
     * 从主球沿 +x 的那条瞄准线：正对球堆，所以虚影球与两条分离线都会出现
     *
     * <p>走的是生产的 {@link AimCalculator#preview}，和游戏里同一条几何——夹具自己编一个
     * 终点的话，查出来的就只是「我编的那个数画上去了」
     */
    static AimView aimAlongPositiveX(Table table) {
        return aimFromBreakPosition(table, new Vec2(1.0, 0.0));
    }

    /**
     * 同一起点、正对<b>上边中袋缺口</b>的瞄准线
     *
     * <p>方向不是随手取的 45°，而是「从开球位指向缺口正中 (500, 60)」——这个点落在两侧
     * 库边段之间的空档里（上库被削成 [86, 486] 与 [514, 914] 两段），所以这条射线既不撞球
     * 也不撞库，一路射出台面，{@code ghost == null}。它跑的正是渲染器里「撞不到任何东西」
     * 那条分支：没有虚影球可画，但瞄准线仍然要画满一个出画的长度
     *
     * <p>取 45° 会错：45° 从开球位出发在 y = 60 处落在 x = 412，那是实打实的库边，
     * 于是这条「穿袋口」的夹具会悄悄退化成一条普通撞库线，把该测的分支测没了
     */
    static AimView aimThroughSidePocketMouth(Table table) {
        Vec2 origin = new Vec2(RackBuilder.DEFAULT_BREAK_CUE_X, Layout.playfieldCenterY());
        Vec2 mouthCentre = new Vec2(
                Layout.PLAYFIELD_LEFT + Layout.playfieldWidth() * 0.5, Layout.PLAYFIELD_TOP);
        return aimFromBreakPosition(table, mouthCentre.minus(origin).normalized());
    }

    /**
     * 从开球位<b>斜切</b>一颗单摆的球（两球心连线与瞄准线约成 30°）
     *
     * <p>为什么要单独造这一条，而不是用现成的正撞线：正撞时两条分离线<b>都与瞄准线重合</b>
     * （目标球沿瞄准线出去、主球也沿瞄准线跟进），三条线叠在一处，画对了和根本没画在像素上
     * 没有区别。斜切时目标球沿球心连线斜着走、主球几乎垂直地走开，三条线才分得开，
     * 「分离线到底画没画」这个问题才有答案
     */
    static AimView aimCutShot(Table table) {
        Vec2 origin = new Vec2(RackBuilder.DEFAULT_BREAK_CUE_X, Layout.playfieldCenterY());
        List<Ball> balls = List.of(
                new Ball(BallCatalog.CUE_ID, origin),
                // 目标球心比瞄准线低一个半径 → 连线恰好偏离 30°，虚影球位置有闭式解
                new Ball(1, new Vec2(Layout.PLAYFIELD_LEFT + 440.0,
                        Layout.playfieldCenterY() + Ball.RADIUS)));
        return aimFrom(table, origin, new Vec2(1.0, 0.0), balls);
    }

    /**
     * 把一条瞄准线里的<b>接触几何</b>全部去掉，只留下方向与终点
     *
     * <p>用来把虚影球与分离线的像素贡献单独隔离出来：两张图只在「有没有接触几何」这一点上
     * 不同，差值就只可能来自它们（以及虚线为让位虚影球而缩短的那一小段）
     */
    static AimView withoutContact(AimView aim) {
        return new AimView(aim.dir(), aim.lineEnd(), null, -1, null, null);
    }

    /**
     * 保留虚影球、只去掉两条分离线
     *
     * <p>与 {@link #withoutContact} 配成一对，就能把三样东西的像素分成两份来问：虚影球
     * （这一对之差）与分离线（本方法与带分离线之差）。有了它，「分离线画了没有」才是一条
     * 独立成立的断言，而不是靠虚影球把差值撑过去
     */
    static AimView withoutSeparation(AimView aim) {
        return new AimView(aim.dir(), aim.lineEnd(), aim.ghost(), aim.targetId(), null, null);
    }

    /** 从默认开球位置计算瞄准预览，复用正式瞄准算法 */
    private static AimView aimFromBreakPosition(Table table, Vec2 direction) {
        return aimFrom(table, new Vec2(RackBuilder.DEFAULT_BREAK_CUE_X, Layout.playfieldCenterY()),
                direction, RackBuilder.buildRack());
    }

    /** 按给定球位和方向构造显示预览，没有接触时保留延伸线 */
    private static AimView aimFrom(Table table, Vec2 origin, Vec2 direction, List<Ball> balls) {
        AimCalculator.Preview preview = AimCalculator.preview(origin, direction, table, balls);
        Vec2 end = preview.ghost();
        if (end == null) {
            end = origin.plus(direction.times(Layout.AIM_ESCAPE_LENGTH_PX));
        }
        return new AimView(direction, end, preview.ghost(), preview.targetId(), preview.targetSeparation(), preview.cueSeparation());
    }
}
