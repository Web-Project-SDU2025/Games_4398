package pvz;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import pvz.browser.BrowserGameplay;
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
        checkMenuLoading(assets);
        checkBattleTiming(assets);
        checkPauseAndRestart(assets);
        checkDisplayImages(assets);
        checkChooserAnimation(assets);
        checkSpawnAnimation(assets);
        checkEndingCoverage(assets);
        checkVictoryReward(assets);
        System.out.println("PASS: Static repaint scheduling and browser battle timing");
    }

    /** 验证真实运行模式的两个菜单入口立即返回界面线程，后台完成后才切换页面 */
    private static void checkMenuLoading(Assets assets) throws Exception {
        int[] screens = {GameScreen.LEVEL_SELECT, GameScreen.ALMANAC};
        for (int screen : screens) {
            MenuLoadCheck check = new MenuLoadCheck(assets, screen);
            SwingUtilities.invokeAndWait(check);
            check.worker.get(30, TimeUnit.SECONDS);
            check(check.finished.await(5, TimeUnit.SECONDS), "界面加载没有发布完成状态");
            check(check.intermediateProgress, "加载进度没有显示中间步骤");
            check(check.worker.getProgress() == 100, "界面加载没有报告实际完成比例");
            check(check.game.getScreen() == screen, "后台完成后未切换目标画面");
        }
    }

    /** 在事件线程上模拟正常点击并接收后台完成事件 */
    private static final class MenuLoadCheck implements Runnable, PropertyChangeListener {
        private final Assets assets;
        private final int target;
        private final CountDownLatch finished = new CountDownLatch(1);
        private Game game;
        private SwingWorker<?, ?> worker;
        private boolean intermediateProgress;

        /** 参数 target 是点击后应打开的画面 */
        MenuLoadCheck(Assets assets, int target) {
            this.assets = assets;
            this.target = target;
        }

        /** 关闭自动计时后仍走真实入口，便于在可控的时机确认首帧状态 */
        public void run() {
            String previous = System.getProperty("pvz.browser");
            try {
                System.setProperty("pvz.browser", "true");
                game = new Game(assets, 0, true, false);
                game.stop();
                if (target == GameScreen.ALMANAC) {
                    game.click(Layout.ALMANAC_BUTTON_LEFT + 10, Layout.ALMANAC_BUTTON_TOP + 10);
                } else {
                    game.click(Layout.MENU_BUTTON_LEFT + 10, Layout.MENU_BUTTON_TOP + 10);
                }
            } finally {
                if (previous == null) {
                    System.clearProperty("pvz.browser");
                } else {
                    System.setProperty("pvz.browser", previous);
                }
            }
            check(game.getScreen() == GameScreen.MENU, "入口在界面线程上同步加载");
            try {
                Field field = Game.class.getDeclaredField("menuLoad");
                field.setAccessible(true);
                worker = (SwingWorker<?, ?>) field.get(game);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
            check(worker != null, "入口没有启动后台加载");
            check(worker.getState() == SwingWorker.StateValue.PENDING, "首帧绘制前已经开始读取素材");
            game.setSize(Layout.WINDOW_WIDTH, Layout.WINDOW_HEIGHT);
            BufferedImage image = new BufferedImage(Layout.WINDOW_WIDTH,
                Layout.WINDOW_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D painter = image.createGraphics();
            game.paint(painter);
            painter.dispose();
            check(image.getRGB(280, 235) == new Color(43, 57, 37).getRGB(), "点击后首帧没有加载面板");
            worker.addPropertyChangeListener(this);
        }

        /** 参数 event 提供任务状态，完成状态在界面切换后发送 */
        public void propertyChange(PropertyChangeEvent event) {
            if ("progress".equals(event.getPropertyName())) {
                int progress = ((Integer) event.getNewValue()).intValue();
                if (progress > 0 && progress < 100) {
                    intermediateProgress = true;
                }
            }
            if ("state".equals(event.getPropertyName())
                    && event.getNewValue() == SwingWorker.StateValue.DONE) {
                finished.countDown();
            }
        }
    }

    /** 验证死亡位置、点击前等待、全白帧和原有胜利页的衔接 */
    private static void checkVictoryReward(Assets assets) throws Exception {
        Game game = new Game(assets, 4, false, false);
        game.loadLevel();
        Field stateField = Game.class.getDeclaredField("state");
        stateField.setAccessible(true);
        GameState state = (GameState) stateField.get(game);
        state.schedule.clear();
        Zombie last = new Zombie("Zombie", 2, Layout.rowBottom(2), assets);
        last.x = 430;
        last.die(assets, state.time, false);
        last.deathTime = state.time - 10000;
        state.zombies.add(last);
        int expectedX = (int) last.bounds(assets, state.time).getCenterX();
        game.step(32);
        check(game.getScreen() == GameScreen.VICTORY_REWARD && state.zombies.isEmpty(),
            "最后一个僵尸死亡后没有进入奖励画面");
        Field rewardX = GameState.class.getDeclaredField("victoryX");
        rewardX.setAccessible(true);
        check(rewardX.getInt(state) == expectedX, "奖励没有定位在最后死亡的僵尸上");

        Method tick = Game.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        state.time = state.time + Layout.ENDING_SCREEN_DURATION + 5000;
        tick.invoke(game);
        check(game.getScreen() == GameScreen.VICTORY_REWARD, "等待玩家点击时提前跳关");
        game.click(5, 5);
        long started = state.time;
        state.time = started + 2500;
        game.click(5, 5);
        GameRenderer renderer = new GameRenderer(assets);
        BufferedImage white = new BufferedImage(Layout.WINDOW_WIDTH,
            Layout.WINDOW_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D painter = white.createGraphics();
        renderer.draw(painter, state.screen, state.time, state, false);
        painter.dispose();
        check(white.getRGB(0, 0) == Color.WHITE.getRGB()
            && white.getRGB(Layout.WINDOW_WIDTH - 1, Layout.WINDOW_HEIGHT - 1) == Color.WHITE.getRGB(),
            "白光没有照亮全屏，或第二次点击重启了动画");
        state.time = started + 3000;
        tick.invoke(game);
        check(game.getScreen() == GameScreen.VICTORY, "白光之后没有衔接原有胜利画面");
        game.click(286, 414);
        check(game.getLevelNumber() == 4, "白光淡出时误触发了下一关");
        state.time = state.screenStart + Layout.ENDING_SCREEN_DURATION + 5000;
        tick.invoke(game);
        check(game.getScreen() == GameScreen.VICTORY && game.getLevelNumber() == 4,
            "胜利画面没有等待玩家选择");
        game.click(286, 414);
        check(game.getScreen() == GameScreen.PLAY && game.getLevelNumber() == 5,
            "进入下一关没有载入第六关");

        state.schedule.clear();
        game.step(32);
        check(game.getScreen() == GameScreen.VICTORY_REWARD, "最后一关没有显示奖励");
        game.click(5, 5);
        state.time = state.time + 3000;
        tick.invoke(game);
        state.time = state.screenStart + 1000;
        game.click(286, 414);
        check(game.getScreen() == GameScreen.VICTORY && game.getLevelNumber() == 5,
            "最后一关的下一关按钮仍然有效");
        game.click(514, 414);
        check(game.getScreen() == GameScreen.MENU, "返回主菜单没有退出胜利画面");
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

    /** 暂停冻结战斗和游玩计时，继续保留进度，重开结束旧记录并载入同一关 */
    private static void checkPauseAndRestart(Assets assets) throws Exception {
        Field stateField = Game.class.getDeclaredField("state");
        stateField.setAccessible(true);
        Field sample = Game.class.getDeclaredField("lastGameplaySample");
        sample.setAccessible(true);
        Field active = Game.class.getDeclaredField("activePlayMilliseconds");
        active.setAccessible(true);
        Field run = Game.class.getDeclaredField("playRun");
        run.setAccessible(true);
        Method startPlay = Game.class.getDeclaredMethod("startPlay");
        startPlay.setAccessible(true);
        Method tick = Game.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        int menuX = Layout.SPEED_BUTTON_LEFT + 1;
        int menuY = Layout.SPEED_BUTTON_TOP + Layout.SPEED_BUTTON_HEIGHT + 5;
        int centerX = Layout.WINDOW_WIDTH / 2;
        int centerY = Layout.WINDOW_HEIGHT / 2;
        int[] levels = {0, 4, 5};
        for (int level : levels) {
            Game game = new Game(assets, level, false, false);
            game.loadLevel();
            GameState state = (GameState) stateField.get(game);
            if (state.screen == GameScreen.CHOOSE) {
                startPlay.invoke(game);
            }
            int initialSun = state.sunValue;
            game.step(1000);
            long battleTime = state.time;
            int speed = state.speedMultiplier;
            String runId = BrowserGameplay.begin(assets.levelPath(level), level, false);
            run.set(game, runId);
            BrowserGameplay.drain();

            game.click(menuX, menuY);
            long activeAtPause = active.getLong(game);
            long oldSample = sample.getLong(game) - 30_000_000_000L;
            sample.setLong(game, oldSample);
            state.lastRealTime = state.lastRealTime - 30000;
            game.step(5000);
            tick.invoke(game);
            game.click(Layout.SPEED_BUTTON_LEFT + 1, Layout.SPEED_BUTTON_TOP + 1);
            game.click(Layout.RETURN_SELECTION_LEFT + 1, Layout.RETURN_SELECTION_TOP + 1);
            check(state.time == battleTime && active.getLong(game) == activeAtPause,
                "暂停期间战斗或网页游玩计时仍在推进");
            check(state.screen == GameScreen.PLAY && state.speedMultiplier == speed,
                "暂停菜单下方的按钮仍在响应");
            check(BrowserGameplay.drain().equals("[]"), "暂停期间产生了额外的战斗记录事件");

            game.click(centerX, centerY);
            check(sample.getLong(game) > oldSample && runId.equals(run.get(game)),
                "继续游戏没有重置计时采样点或保留原本的对局");
            game.step(2000);
            check(state.time > battleTime, "继续游戏后战斗没有恢复");

            state.sunValue = initialSun + 100;
            game.click(menuX, menuY);
            game.click(centerX, centerY + 52);
            check(game.getLevelNumber() == level && state.sunValue == initialSun
                && state.nextSpawnIndex == 0 && state.zombies.isEmpty(), "重开没有重置同一关");
            int expectedScreen = GameScreen.PLAY;
            if (level == 0) {
                expectedScreen = GameScreen.CHOOSE;
            }
            check(game.getScreen() == expectedScreen, "重开进入了错误的画面");
            JsonArray events = JsonParser.parseString(BrowserGameplay.drain()).getAsJsonArray();
            check(events.size() == 1, "重开没有且仅有一次结束记录");
            JsonObject finish = events.get(0).getAsJsonObject();
            check("finish".equals(finish.get("type").getAsString())
                && "abandoned".equals(finish.get("outcome").getAsString())
                && runId.equals(finish.get("runId").getAsString()), "重开未放弃原来的对局");
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
