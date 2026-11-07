package billiards.browser;

import billiards.audio.SampleBank;
import billiards.app.GamePanel;
import billiards.render.BallSpriteCache;
import billiards.render.RenderAssets;
import billiards.render.TableTexture;
import billiards.world.Table;
import billiards.world.Vec2;
import billiards.world.BallCatalog;
import java.awt.image.BufferedImage;
import java.lang.reflect.Modifier;

/** 无窗口检查网页协议入口和 JAR 内音效资源，实际窗口由浏览器运行验证 */
public final class BrowserAdapterTest {
    /** 此类只提供独立自检入口 */
    private BrowserAdapterTest() {
    }

    /** 确认适配器具备公开静态入口，音效可以直接从类路径读取 */
    public static void main(String[] arguments) throws Exception {
        int open = BrowserGame.class.getMethod("open", String.class, Boolean.TYPE).getModifiers();
        int close = BrowserGame.class.getMethod("close").getModifiers();
        if (!Modifier.isStatic(open) || !Modifier.isStatic(close)) {
            throw new IllegalStateException("浏览器入口应为公开静态方法");
        }
        if (!SampleBank.load().isLoaded()) {
            throw new IllegalStateException("台球音效应随游戏 JAR 发布");
        }
        checkPreparedImages();
        checkBatchedUpdates();
        BrowserGame.close();
        BrowserGame.close();
        boolean rejected = false;
        try {
            BrowserGame.open("/files/check", true);
        } catch (IllegalArgumentException error) {
            rejected = true;
        }
        if (!rejected) {
            throw new IllegalStateException("台球没有编辑器，不能以编辑模式启动");
        }
        System.out.println("PASS: billiards browser entry, classpath audio and repeated close");
    }

    /** 核对构建图像与原绘制器逐像素相同，优化不能改变袋口和球号外观 */
    private static void checkPreparedImages() {
        BufferedImage preparedTable = TableTexture.loadPrepared().image();
        BufferedImage originalTable = TableTexture.bake(new Table()).image();
        compareImages(preparedTable, originalTable);
        BallSpriteCache originalBalls = new BallSpriteCache();
        for (int id = 0; id < BallCatalog.BALL_COUNT; id = id + 1) {
            compareImages(originalBalls.sprite(id), RenderAssets.load("ball-" + id));
        }
    }

    /** 比较图像尺寸和每个像素，有差异时报告位置 */
    private static void compareImages(BufferedImage first, BufferedImage second) {
        if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) {
            throw new IllegalStateException("预生成图像尺寸与绘制器不一致");
        }
        for (int row = 0; row < first.getHeight(); row = row + 1) {
            for (int column = 0; column < first.getWidth(); column = column + 1) {
                if (first.getRGB(column, row) != second.getRGB(column, row)) {
                    throw new IllegalStateException("预生成像素不一致：" + column + "," + row);
                }
            }
        }
    }

    /** 同样的补步分别逐次发布与批量发布，物理坐标应按位一致 */
    private static void checkBatchedUpdates() {
        GamePanel individual = new GamePanel();
        GamePanel batched = new GamePanel();
        individual.session().resetMatch();
        batched.session().resetMatch();
        individual.session().engine().placeCueBall(new Vec2(190, 280));
        batched.session().engine().placeCueBall(new Vec2(190, 280));
        individual.session().engine().takeShot(new Vec2(1, 0), 1500);
        batched.session().engine().takeShot(new Vec2(1, 0), 1500);
        for (int frame = 0; frame < 60; frame = frame + 1) {
            for (int step = 0; step < 4; step = step + 1) {
                individual.tickFixed();
            }
            batched.advanceFrame(4);
        }
        for (int index = 0; index < individual.session().view().ballCount(); index = index + 1) {
            double first = individual.session().view().ball(index).x();
            double second = batched.session().view().ball(index).x();
            if (Double.doubleToLongBits(first) != Double.doubleToLongBits(second)) {
                throw new IllegalStateException("批量快照不能改变物理坐标");
            }
            double firstY = individual.session().view().ball(index).y();
            double secondY = batched.session().view().ball(index).y();
            boolean firstVisible = individual.session().view().ball(index).onTable();
            boolean secondVisible = batched.session().view().ball(index).onTable();
            if (Double.doubleToLongBits(firstY) != Double.doubleToLongBits(secondY)
                    || firstVisible != secondVisible) {
                throw new IllegalStateException("批量快照不能改变竖直坐标或落袋状态");
            }
        }
        GamePanel idle = new GamePanel();
        Object before = idle.session().view();
        idle.advanceFrame(4);
        if (before != idle.session().view()) {
            throw new IllegalStateException("静止画面应复用同一份快照");
        }
    }
}
