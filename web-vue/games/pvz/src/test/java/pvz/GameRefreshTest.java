package pvz;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.Timer;
import pvz.game.Game;
import pvz.game.GameScreen;
import pvz.game.GameState;
import pvz.game.GameRenderer;
import pvz.game.LevelSystem;
import pvz.world.Assets;
import pvz.world.Layout;
import pvz.tools.AssetToolkit;
import pvz.plant.Plant;
import pvz.zombie.Zombie;
import pvz.zombie.ZombieSpawn;
import java.util.Random;

/** 检查静态画面停止重复绘制，并验证浏览器降低绘制频率后仍使用原战斗步长 */
public final class GameRefreshTest {
    /** 用相同总时长模拟两种画面刷新节奏，检查游戏逻辑保持一致 */
    private static final long TOTAL_TIME = 16000;
    private static final long[] FRAME_INTERVALS = {16, 33};
    /** 用明显落后于系统时间的游戏时刻复现浏览器加载后的状态 */
    private static final long ANIMATION_TIME = 1000;
    private static final long ANIMATION_SAMPLE_INTERVAL = 150;

    /** 此类只提供自检入口 */
    private GameRefreshTest() {
    }

    /** 参数 assets 是共用素材，整个检查通过手动推帧完成，不创建实际窗口 */
    public static void run(Assets assets) throws Exception {
        checkStaticRefresh(assets);
        checkBrowserSettings(assets);
        checkBattleTiming(assets);
        checkDisplayImages(assets);
        checkChooserAnimation(assets);
        checkSpawnAnimation(assets);
        checkEndingCoverage(assets);
        System.out.println("PASS: Static repaint scheduling and browser battle timing");
    }

    /** 胜负画面覆盖整张画布，不同旧背景渲染后的右侧结果应完全一致 */
    private static void checkEndingCoverage(Assets assets) {
        GameRenderer renderer = new GameRenderer(assets);
        GameState state = new GameState();
        int[] screens = {GameScreen.VICTORY, GameScreen.LOSS};
        for (int screen : screens) {
            state.screen = screen;
            BufferedImage first = drawEndingOver(renderer, state, Color.MAGENTA);
            BufferedImage second = drawEndingOver(renderer, state, Color.CYAN);
            check(!picturesDiffer(first, second), "结算画面右侧仍保留旧背景：" + screen);
        }
    }

    /** 先填入可辨认的旧背景，再通过实际绘图入口绘制结算页，检查素材边界之外的残影 */
    private static BufferedImage drawEndingOver(
            GameRenderer renderer,
            GameState state,
            Color previous
    ) {
        BufferedImage image = new BufferedImage(Layout.WINDOW_WIDTH,
            Layout.WINDOW_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D painter = image.createGraphics();
        painter.setColor(previous);
        painter.fillRect(0, 0, image.getWidth(), image.getHeight());
        renderer.draw(painter, state.screen, ANIMATION_TIME, state, false);
        painter.dispose();
        return image;
    }

    /** 游戏时钟落后时，选卡页的僵尸仍持续换帧，位置与关卡出场进度保持不变 */
    private static void checkChooserAnimation(Assets assets) throws Exception {
        Game game = new Game(assets, 0, false, false);
        Field field = Game.class.getDeclaredField("state");
        field.setAccessible(true);
        GameState state = (GameState) field.get(game);
        state.time = ANIMATION_TIME;
        state.schedule.add(new ZombieSpawn(10000, 0, "Zombie"));
        Method build = Game.class.getDeclaredMethod("buildIntroZombies");
        build.setAccessible(true);
        build.invoke(game);
        Zombie zombie = state.introZombies.get(0);
        check(zombie.animationStart == state.time, "展示僵尸使用了系统时间作为动画起点");
        state.screen = GameScreen.CHOOSE;
        state.introChooser = true;
        state.cameraOffset = Layout.CAMERA_RIGHT_OFFSET;
        state.screenStart = state.time - Layout.CHOOSER_RISE_TIME;
        double left = zombie.x;
        double top = zombie.y;
        GameRenderer renderer = new GameRenderer(assets);
        BufferedImage first = drawChooser(renderer, state);
        state.time = state.time + ANIMATION_SAMPLE_INTERVAL;
        BufferedImage second = drawChooser(renderer, state);
        check(picturesDiffer(first, second), "选卡页的僵尸画面没有随游戏时间变化");
        check(zombie.x == left && zombie.y == top, "展示动画改变了僵尸位置");
        check(state.nextSpawnIndex == 0 && state.zombies.isEmpty(), "展示动画提前启动了战斗出怪");
    }

    /** 按真实绘图入口生成选卡页，验证动画像素而非只检查重绘次数 */
    private static BufferedImage drawChooser(
            GameRenderer renderer,
            GameState state
    ) {
        BufferedImage image = new BufferedImage(Layout.WINDOW_WIDTH,
            Layout.WINDOW_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D painter = image.createGraphics();
        renderer.draw(painter, state.screen, state.time, state, false);
        painter.dispose();
        return image;
    }

    /** 比较右侧展示区的像素，选卡面板保持静止时也能确认僵尸本身在播放动画 */
    private static boolean picturesDiffer(
            BufferedImage first,
            BufferedImage second
    ) {
        for (int row = 0; row < first.getHeight(); row = row + 1) {
            for (int column = first.getWidth() / 2; column < first.getWidth(); column = column + 1) {
                if (first.getRGB(column, row) != second.getRGB(column, row)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 正式出场的僵尸同样使用游戏时钟，加载停顿与倍速都能正常播放首段动画 */
    private static void checkSpawnAnimation(Assets assets) {
        GameState state = new GameState();
        state.time = ANIMATION_TIME;
        state.playStart = ANIMATION_TIME;
        state.schedule.add(new ZombieSpawn(0, 0, "Zombie"));
        LevelSystem system = new LevelSystem(state, assets, new Random(0));
        system.updateSpawnsAndDrops();
        Zombie zombie = state.zombies.get(0);
        check(zombie.animationStart == state.time, "战斗僵尸使用了系统时间作为动画起点");
        BufferedImage first = zombie.picture(assets, state.time);
        BufferedImage second = zombie.picture(assets, state.time + ANIMATION_SAMPLE_INTERVAL);
        check(first != second, "战斗僵尸的动画仍停在首帧");
        Plant plant = new Plant("Peashooter", Layout.columnCenter(0), Layout.rowBottom(0),
            0, 0, assets, state.time, true);
        check(plant.animationStart == state.time, "种植动画使用了系统时间");
        state.backgroundIndex = 0;
        state.time = state.time + state.skySunInterval + 1;
        system.updateSpawnsAndDrops();
        check(state.suns.get(0).animationStart == state.time, "天空阳光动画使用了系统时间");
    }

    /** 检查背景使用直接复制格式，重复卡片缩放共用图片，透明素材保留原透明度 */
    private static void checkDisplayImages(Assets assets) {
        BufferedImage background = assets.frame("Background", 0);
        check(background.getType() == BufferedImage.TYPE_INT_RGB, "背景没有使用直接复制格式");
        BufferedImage card = assets.image("card_peashooter", Layout.CARD_SCALE);
        check(card == assets.image("card_peashooter", Layout.CARD_SCALE), "重复绘制重新缩放了卡片");
        BufferedImage original = assets.image("card_peashooter");
        check(card.getWidth() == (int) (original.getWidth() * Layout.CARD_SCALE),
            "卡片宽度改变了原有点击范围");
        BufferedImage transparent = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        transparent.setRGB(0, 0, 0x80FF0000);
        BufferedImage converted = AssetToolkit.forDisplay(transparent);
        check(converted.getType() == BufferedImage.TYPE_INT_ARGB_PRE, "透明颜色没有预先计算");
        check(converted.getRGB(0, 0) == transparent.getRGB(0, 0), "转换改变了透明颜色");
        check(converted.getRGB(1, 1) == 0, "转换填充了原透明区域");
    }

    /** 连续计时通知应保持静态菜单不重绘，点击后和动画状态仍然能请求绘制 */
    private static void checkStaticRefresh(Assets assets) throws Exception {
        CountingGame game = new CountingGame(assets);
        Method tick = Game.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        game.repaintCount = 0;
        tick.invoke(game);
        tick.invoke(game);
        check(game.repaintCount == 0, "静态菜单持续请求重绘");

        Field field = Game.class.getDeclaredField("state");
        field.setAccessible(true);
        GameState state = (GameState) field.get(game);
        state.startingMenu = true;
        state.screenStart = System.currentTimeMillis();
        tick.invoke(game);
        check(game.repaintCount > 0, "冒险按钮动画停止刷新");

        state.startingMenu = false;
        state.screen = GameScreen.LEVEL_SELECT;
        game.repaintCount = 0;
        tick.invoke(game);
        check(game.repaintCount == 0, "静态选关持续请求重绘");
        game.click(80, 550);
        check(game.repaintCount > 0, "静态页面点击后没有刷新");
    }

    /** 浏览器使用独立绘制间隔并关闭中间缓冲，桌面入口继续沿用自己的刷新设置 */
    private static void checkBrowserSettings(Assets assets) throws Exception {
        String previous = System.getProperty("pvz.browser");
        try {
            System.setProperty("pvz.browser", "true");
            Game game = new Game(assets, 0, false, false);
            Field field = Game.class.getDeclaredField("timer");
            field.setAccessible(true);
            Timer timer = (Timer) field.get(game);
            check(timer.getDelay() == 33, "浏览器绘制间隔与设定不一致");
            check(!game.isDoubleBuffered(), "浏览器仍在复制中间缓冲画布");
        } finally {
            if (previous == null) {
                System.clearProperty("pvz.browser");
            } else {
                System.setProperty("pvz.browser", previous);
            }
        }
    }

    /** 用十六和三十三毫秒分别推进同一关卡，出怪数量和位置应完全一致 */
    private static void checkBattleTiming(Assets assets) {
        Game reference = null;
        for (long interval : FRAME_INTERVALS) {
            Game game = new Game(assets, 0, false, false);
            game.loadLevel();
            long elapsed = 0;
            while (elapsed < TOTAL_TIME) {
                long step = Math.min(interval, TOTAL_TIME - elapsed);
                game.step(step);
                elapsed = elapsed + step;
            }
            if (reference == null) {
                reference = game;
            } else {
                check(game.getZombieCount() == reference.getZombieCount(), "绘制间隔改变了出怪数量");
                for (int index = 0; index < game.getZombieCount(); index = index + 1) {
                    check(game.getZombieX(index) == reference.getZombieX(index),
                        "绘制间隔改变了僵尸移动速度");
                }
            }
        }
    }

    /** 用具名子类记录重绘请求，避免依赖机器快慢来判断静态页面的行为 */
    private static final class CountingGame extends Game {
        private int repaintCount;

        /** 参数 assets 由原自检共用，计时器由测试手动驱动 */
        CountingGame(Assets assets) {
            super(assets, 0, false, false);
        }

        /** 仅累计重绘请求，窗口和实际像素绘制由浏览器功能检查覆盖 */
        public void repaint() {
            repaintCount = repaintCount + 1;
        }
    }

    /** 条件失败时输出对应问题，成功时继续后续检查 */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
