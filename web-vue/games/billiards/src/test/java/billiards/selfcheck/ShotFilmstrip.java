package billiards.selfcheck;

import billiards.physics.PhysicsEngine;
import billiards.render.BallRenderer;
import billiards.render.TableRenderer;
import billiards.render.Theme;
import billiards.rules.RackBuilder;
import billiards.world.Ball;
import billiards.world.Layout;
import billiards.world.PhysicsConfig;
import billiards.world.Table;
import billiards.world.Vec2;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * 把一杆满力开球导出成一组逐帧 PNG，用来「肉眼确认停得住、不穿透」
 *
 * <h2>为什么不是鼠标调试模式</h2>
 * 里程碑 M2 原本要求做一个「点击把主球射向球堆」的临时调试模式。但调试模式只能由人在
 * 窗口里看，而<b>看的人看不到同一帧、也没法回看</b>；更要紧的是它不可复现——换台机器、
 * 换个时间，轨迹可能不同（墙钟驱动的累加器）
 *
 * <p>这里改成无头导出：用与实时循环完全相同的 {@link PhysicsEngine#step()} 推进固定步，
 * 在若干模拟时刻各渲染一帧写成 PNG。于是「确认」变成了<b>可复现、可回看、可放进实验报告</b>
 * 的东西，而且导出顺序与玩法无关，不会因为以后删掉调试模式而失效
 *
 * <h2>为什么开球就是最坏情况</h2>
 * 穿透风险正比于单子步位移。满力开球是全场唯一让球达到 {@code MAX_SHOT_SPEED} 的时刻，
 * 而正撞一颗静止球又是球球碰撞里最容易穿透的几何（迎面相对速度最大）。所以「满力开球
 * 不穿透」等价于覆盖了最坏情况，不需要再单独构造场景
 *
 * <p>默认什么都不做；设了 {@code -Dbilliards.filmstrip=<目录>} 才导出，因此自检的默认
 * 输出保持干净（与 {@code -Dbilliards.screenshot} 同一约定）
 */
public final class ShotFilmstrip {

    /**
     * 要抓帧的模拟时刻（毫秒）
     *
     * <p>取值的意图：0 是初始摆球；120/300 是主球奔向球堆与接触瞬间；700 是球堆刚散开、
     * 最能看出「有没有球被挤进别的球里」；1500 是能量衰减中段；3000 之后多半已静止
     */
    private static final double[] CHECKPOINTS_MS = {0.0, 120.0, 300.0, 700.0, 1500.0, 3000.0};

    /** 工具类只提供静态方法，不需要创建对象 */
    private ShotFilmstrip() {
    }

    /** 读取命令行导出设置，只有提供输出位置时才生成检查文件 */
    public static void dumpIfRequested() {
        String dirPath = System.getProperty("billiards.filmstrip");
        if (dirPath == null || dirPath.isBlank()) {
            return;
        }
        File direction = new File(dirPath);
        if (!direction.isDirectory() && !direction.mkdirs()) {
            System.err.println("  [warn] 分帧目录建不出来: " + direction.getAbsolutePath());
            return;
        }

        Table table = new Table();
        List<Ball> balls = RackBuilder.buildRack();
        Ball cue = RackBuilder.cueBall(balls);
        if (cue == null) {
            System.err.println("  [warn] 找不到主球，分帧导出中止");
            return;
        }

        // 满力沿中线打向顶球：方向取 +x，初速取满力，这是全场最快的一杆
        cue.setPosition(new Vec2(RackBuilder.DEFAULT_BREAK_CUE_X, Layout.playfieldCenterY()));
        cue.setVelocity(new Vec2(PhysicsConfig.MAX_SHOT_SPEED, 0.0));

        PhysicsEngine engine = new PhysicsEngine(table, balls);
        engine.setDiagnosticsEnabled(true);

        TableRenderer tableRenderer = new TableRenderer();
        BallRenderer ballRenderer = new BallRenderer();

        System.out.println("  [img]  开球分帧导出 → " + direction.getAbsolutePath());
        System.out.println("         模拟时刻   最快速度     在台   本步子步   子步最大位移   最深球球重叠");

        int frameIndex = 0;
        double elapsedMs = 0.0;
        int nextCheckpoint = 0;
        boolean rested = false;

        while (true) {
            boolean atCheckpoint = nextCheckpoint < CHECKPOINTS_MS.length
                    && elapsedMs + PhysicsConfig.EPS >= CHECKPOINTS_MS[nextCheckpoint];
            if (atCheckpoint) {
                nextCheckpoint = nextCheckpoint + 1;
            }
            if (atCheckpoint || rested) {
                exportFrame(direction, tableRenderer, ballRenderer, engine, frameIndex, elapsedMs, rested);
                frameIndex = frameIndex + 1;
                if (rested) {
                    break;
                }
            }
            if (elapsedMs >= PhysicsConfig.SHOT_TIMEOUT_SIM_MS) {
                // 超时仍未停稳时导出最后一帧，便于查看异常运动状态
                exportFrame(direction, tableRenderer, ballRenderer, engine, frameIndex, elapsedMs, false);
                System.err.println("  [FAIL] 开球在 " + PhysicsConfig.SHOT_TIMEOUT_SIM_MS
                        + " 模拟毫秒内没有停下——分帧到此为止");
                break;
            }
            engine.step();
            elapsedMs = elapsedMs + (PhysicsConfig.FIXED_STEP_MS);
            rested = engine.allAtRest();
        }
    }

    /** 把当前物理状态绘制成图片，文件名保留帧号和模拟时刻 */
    private static void exportFrame(File direction, TableRenderer tableRenderer, BallRenderer ballRenderer,
                                    PhysicsEngine engine, int frameIndex, double elapsedMs,
                                    boolean rested) {
        BufferedImage image = new BufferedImage(
                Layout.WINDOW_W, Layout.WINDOW_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Theme.WINDOW_BG);
            graphics.fillRect(0, 0, Layout.WINDOW_W, Layout.WINDOW_H);

            tableRenderer.draw(graphics, engine.table());
            for (Ball ball : engine.balls()) {
                // 只画还在台上的球。落袋的球若照画，会像一颗停在袋口的幽灵球，
                // 把「到底进没进」这个最需要一眼看清的事搅浑
                if (ball.isOnTable()) {
                    ballRenderer.draw(graphics, ball);
                }
            }
        } finally {
            graphics.dispose();
        }

        String restSuffix = "";
        if (rested) {
            restSuffix = "-rest";
        }
        String name = String.format("break-%02d-t%05d%s.png",
                frameIndex, Math.round(elapsedMs), restSuffix);
        File file = new File(direction, name);
        try {
            javax.imageio.ImageIO.write(image, "png", file);
        } catch (Exception error) {
            System.err.println("  [warn] 分帧写入失败 " + file.getName() + ": " + error);
            return;
        }

        System.out.printf("  [img]  %-28s  %6.0fms  %8.1fpx/s  %2d/%2d   %2d  %10.4fpx  %10.4e px%n",
                name,
                elapsedMs,
                engine.maxSpeed(),
                engine.ballsOnTable(), engine.balls().size(),
                engine.substepsLastStep(),
                engine.worstSubstepTravel(),
                engine.worstPairOverlap());
    }
}
