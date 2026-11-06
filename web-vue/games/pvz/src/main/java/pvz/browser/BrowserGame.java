package pvz.browser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import platform.runtime.GameFiles;
import pvz.editor.LevelDesign;
import pvz.editor.LevelEditor;
import pvz.game.Game;
import pvz.world.Assets;
import pvz.world.Layout;
import pvz.level.LevelJson;
import pvz.zombie.ZombieCatalog;
import pvz.zombie.ZombieSpawn;

/** 浏览器适配入口，原游戏和编辑器继续使用 Swing，仅在此处管理网页内的窗口生命周期 */
public final class BrowserGame {
    /** 浏览器桌面的默认关卡和画面入口 */
    private static final int FIRST_LEVEL = 0;
    /** 文件导入允许的关卡规模，避免错误时间和数量让编辑器难以操作 */
    private static final int MAX_IMPORTED_SPAWNS = 4000;
    private static final long MAX_IMPORTED_TIME = 3600000;
    /** 编辑器编号范围与原有图形界面保持一致 */
    private static final int MAX_LEVEL_NUMBER = 99;
    /** 浏览器通过启动参数传入当前选中的关卡，编辑器沿用原编号范围 */
    private static final String EDITOR_LEVEL_PROPERTY = "platform.editor.level";
    /** 同一浏览器运行实例共享素材，重复打开编辑器时复用已经解码的图片 */
    private static Assets assets;
    private static Path assetRoot;

    /** 此类通过静态方法供网页启动器调用 */
    private BrowserGame() {
    }

    /** 在浏览器事件线程中打开原游戏或编辑器，素材首次读取完成后再创建窗口 */
    public static void open(String directory, boolean editorMode) throws Exception {
        System.setProperty("pvz.browser", "true");
        if (editorMode && !BrowserAccess.canEdit()) {
            throw new SecurityException("关卡编辑器仅管理员可用");
        }
        Path requestedRoot = Path.of(directory);
        if (assets == null || !requestedRoot.equals(assetRoot)) {
            assetRoot = requestedRoot;
            assets = new Assets(assetRoot);
        }
        SwingUtilities.invokeAndWait(new OpenWindow(editorMode));
    }

    /** 保存启动模式，具名任务保证 Swing 窗口始终在事件线程创建 */
    private static final class OpenWindow implements Runnable {
        private final boolean editorMode;

        /** 参数 editor 表示直接打开编辑器，普通入口显示原游戏主菜单 */
        OpenWindow(boolean editor) {
            editorMode = editor;
        }

        /** 创建可独立关闭的窗口，浏览器运行环境保留到用户退出游戏页 */
        public void run() {
            if (restoreWindow(editorMode)) {
                return;
            }
            if (editorMode) {
                if (openGameEditor()) {
                    return;
                }
                int level = Integer.getInteger(EDITOR_LEVEL_PROPERTY, FIRST_LEVEL);
                LevelEditor editor = LevelEditor.createWindow(assets, assetRoot, level);
                editor.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                editor.setVisible(true);
                return;
            }
            Game game = new Game(assets, FIRST_LEVEL, true, false);
            JFrame window = new JFrame("植物大战僵尸 · Windy-Field / Octorange");
            // 游戏使用平台自己的窗口操作，画面保留原始尺寸
            window.setUndecorated(true);
            window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            window.setContentPane(game);
            window.addWindowListener(new StopGame(game));
            window.pack();
            window.setLocationRelativeTo(null);
            window.setResizable(false);
            window.setVisible(true);
            game.requestFocusInWindow();
        }
    }

    /** 已有游戏时直接使用它的原编辑器，平台按钮与游戏内按钮共享未保存内容 */
    private static boolean openGameEditor() {
        for (Window window : Window.getWindows()) {
            if (window instanceof JFrame && window.isDisplayable()) {
                JFrame frame = (JFrame) window;
                if (frame.getContentPane() instanceof Game) {
                    Game game = (Game) frame.getContentPane();
                    game.showEditor();
                    return true;
                }
            }
        }
        return false;
    }

    /** 相同入口复用已有窗口，重复点击只恢复焦点，已关闭的游戏重新创建 */
    private static boolean restoreWindow(boolean editorMode) {
        for (Window window : Window.getWindows()) {
            if (!(window instanceof JFrame) || !window.isDisplayable()) {
                continue;
            }
            JFrame frame = (JFrame) window;
            boolean matches = frame.getContentPane() instanceof Game;
            if (editorMode) {
                matches = frame instanceof LevelEditor;
            }
            if (matches) {
                frame.setState(JFrame.NORMAL);
                frame.setVisible(true);
                frame.toFront();
                frame.requestFocus();
                return true;
            }
        }
        return false;
    }

    /** 保存当前游戏对象，关闭窗口时停止该局计时器 */
    private static final class StopGame extends WindowAdapter {
        private final Game game;

        /** 参数 current 是此窗口显示的游戏实例 */
        StopGame(Game current) {
            game = current;
        }

        /** 关闭窗口后释放本局更新计时器，编辑器窗口保持独立 */
        public void windowClosed(WindowEvent event) {
            game.stop();
        }
    }

    /** 导入成功返回空字符串，校验失败返回说明，让网页直接显示 Java 的原始提示 */
    public static String importData(String directory, int number, String text) {
        try {
            if (number < 0 || number > MAX_LEVEL_NUMBER) {
                return "关卡编号应为零至九十九";
            }
            validateLevel(text);
            GameFiles.writeText(directory, "levels/level_" + number + ".json", text);
            return "";
        } catch (Exception failure) {
            String message = failure.getMessage();
            if (message == null || message.isEmpty()) {
                return "关卡内容格式不正确";
            }
            return message;
        }
    }

    /** 关卡导入先交给原编辑器解析，检查出怪规模和行号后再写入账户目录 */
    private static void validateLevel(String text) {
        JsonObject json = JsonParser.parseString(text).getAsJsonObject();
        if (!json.has(LevelJson.ZOMBIE_LIST)
                || json.getAsJsonArray(LevelJson.ZOMBIE_LIST).size() > MAX_IMPORTED_SPAWNS) {
            throw new IllegalArgumentException("关卡应包含四千条以内的出场记录");
        }
        for (ZombieSpawn spawn : LevelJson.readSpawns(json.getAsJsonArray(LevelJson.ZOMBIE_LIST))) {
            ZombieCatalog.definitionOf(spawn.name);
            if (spawn.row < -1 || spawn.row >= Layout.ROW_COUNT
                    || spawn.spawnTime < 0 || spawn.spawnTime > MAX_IMPORTED_TIME) {
                throw new IllegalArgumentException("请检查出场记录中的行号和时间");
            }
        }
        LevelDesign design = LevelDesign.fromJson(json);
        if (design.barType != Layout.BAR_NORMAL && design.cardPool.isEmpty()) {
            throw new IllegalArgumentException("传送带和保龄球卡池至少需要一张卡片");
        }
    }

    /** 退出时在 Swing 事件线程关闭全部窗口，停止原游戏保留的计时器 */
    public static void close() throws Exception {
        SwingUtilities.invokeAndWait(new CloseWindows());
    }

    /** 单独关闭原编辑器及其试玩窗口，当前游戏继续保留 */
    public static void closeEditor() throws Exception {
        SwingUtilities.invokeAndWait(new CloseEditor());
    }

    /** 网页按通用协议读取开局、时长和结果，其他游戏可提供相同方法 */
    public static String gameEvents() {
        return BrowserGameplay.drain();
    }

    /** 编辑器清理放在原事件线程，已有 dispose 方法负责结束试玩和时钟 */
    private static final class CloseEditor implements Runnable {
        /** 遍历并关闭编辑器，普通游戏窗口保持运行 */
        public void run() {
            for (Window window : Window.getWindows()) {
                if (window instanceof LevelEditor) {
                    window.dispose();
                }
            }
        }
    }

    /** 将窗口清理放入具名任务，统一在事件线程执行 */
    private static final class CloseWindows implements Runnable {
        /** 遍历当前运行实例的窗口，编辑器自己的 dispose 方法负责结束试玩和时钟 */
        public void run() {
            for (Window window : Window.getWindows()) {
                if (window instanceof JFrame) {
                    JFrame frame = (JFrame) window;
                    if (frame.getContentPane() instanceof Game) {
                        Game game = (Game) frame.getContentPane();
                        game.stop();
                    }
                }
                window.dispose();
            }
        }
    }
}
