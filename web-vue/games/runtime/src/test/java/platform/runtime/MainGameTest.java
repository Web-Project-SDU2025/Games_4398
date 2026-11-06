package platform.runtime;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 检查普通 main 游戏的参数转交和入口错误，直接复用共用启动器 */
public final class MainGameTest {
    /** 主线程在限定时间内等待具名游戏线程返回结果 */
    private static final int WAIT_SECONDS = 10;
    private static final CountDownLatch COMPLETED = new CountDownLatch(1);
    private static String[] received;

    /** 此类只提供独立检查入口 */
    private MainGameTest() {
    }

    /** 启动普通入口并核对参数，缺少公开入口时返回说明 */
    public static void main(String[] arguments) throws Exception {
        String result = MainGame.open("platform.runtime.MainGameTest$Entry", "/files/game/assets\nstart");
        if (!result.isEmpty() || !COMPLETED.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("普通 main 游戏应在限定时间内接收启动参数");
        }
        if (received.length != 2 || !received[0].equals("/files/game/assets") || !received[1].equals("start")) {
            throw new IllegalStateException("main 参数应保持原顺序和内容");
        }
        System.out.println("PASS: generic Java main adapter and argument forwarding");
    }

    /** 以普通 main 方法模拟任意游戏入口，实例状态与窗口均由实际游戏自行管理 */
    public static final class Entry {
        /** 静态入口由共用运行器调用 */
        private Entry() {
        }

        /** 保存收到的参数并通知检查线程，原数组直接从普通 main 参数取得 */
        public static void main(String[] arguments) {
            received = arguments;
            COMPLETED.countDown();
        }
    }
}
