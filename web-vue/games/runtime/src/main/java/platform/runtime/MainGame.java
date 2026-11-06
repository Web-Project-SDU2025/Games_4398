package platform.runtime;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** 启动普通 Java main 游戏，游戏自己的界面和事件循环继续由原程序管理 */
public final class MainGame {
    /** 启动参数使用换行分隔，每次运行只保留一个入口任务 */
    private static final String ARGUMENT_SEPARATOR = "\n";
    private static Thread mainThread;

    /** 此类由共用网页启动器调用 */
    private MainGame() {
    }

    /** 在具名线程运行 main，参数文本由已校验配置生成，快速重复点击复用当前任务 */
    public static synchronized String open(String className, String argumentText) {
        if (mainThread != null && mainThread.isAlive()) {
            return "游戏入口仍在运行，请使用已有窗口";
        }
        try {
            Class<?> entry = Class.forName(className);
            Method main = entry.getMethod("main", String[].class);
            if (!Modifier.isStatic(main.getModifiers()) || main.getReturnType() != Void.TYPE) {
                return "游戏入口应为 public static void main(String[] arguments)";
            }
            String[] arguments = new String[0];
            if (!argumentText.isEmpty()) {
                arguments = argumentText.split(ARGUMENT_SEPARATOR, -1);
            }
            mainThread = new Thread(new RunMain(main, arguments), "platform-game-main");
            mainThread.start();
            return "";
        } catch (ReflectiveOperationException failure) {
            return "Java 入口应包含 public static void main(String[] arguments)：" + className;
        }
    }

    /** 保存已经找到的入口和启动参数，让反射调用只发生在明确的游戏线程 */
    private static final class RunMain implements Runnable {
        private final Method main;
        private final String[] arguments;

        /** 参数 entry 是游戏 main 方法，values 是本次配置展开后的参数 */
        RunMain(Method entry, String[] values) {
            main = entry;
            arguments = values;
        }

        /** 执行原入口并输出游戏异常，用户退出页面后整个运行上下文一起释放 */
        @Override
        public void run() {
            try {
                main.invoke(null, (Object) arguments);
            } catch (ReflectiveOperationException failure) {
                failure.printStackTrace();
            }
        }
    }
}
