package pvz.browser;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Queue;
import java.util.UUID;

/** 将开局、累计时长和结果交给网页，所有游戏事件使用同一协议 */
public final class BrowserGameplay {
    private static final Queue<JsonObject> EVENTS = new ArrayDeque<JsonObject>();

    /** 此类只保存浏览器运行实例中的待处理事件 */
    private BrowserGameplay() {
    }

    /** 正式开局生成独立尝试编号，关卡文件保持原字节以便后端核对官方指纹 */
    public static synchronized String begin(Path path, int level, boolean practice) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String run = UUID.randomUUID().toString();
            JsonObject event = new JsonObject();
            event.addProperty("type", "begin");
            event.addProperty("runId", run);
            event.addProperty("level", level);
            event.addProperty("hash", HexFormat.of().formatHex(digest.digest(bytes)));
            String mode = "official";
            if (practice) {
                mode = "practice";
            }
            event.addProperty("mode", mode);
            event.addProperty("activeMilliseconds", 0);
            EVENTS.add(event);
            return run;
        } catch (Exception failure) {
            System.err.println("游戏记录事件准备失败：" + failure.getMessage());
            return "";
        }
    }

    /** 定期提交累计真实毫秒数，倍速只影响战斗，记录时长保持原倍率 */
    public static synchronized void progress(String run, long milliseconds) {
        event(run, "heartbeat", "", milliseconds);
    }

    /** 通过、失败和离开都提交最终结果，空编号表示本局尚未开始 */
    public static synchronized void finish(String run, String outcome, long milliseconds) {
        event(run, "finish", outcome, milliseconds);
    }

    /** 构造后续事件，编号与开局一致以便服务器识别重试 */
    private static void event(String run, String type, String outcome, long milliseconds) {
        if (run.isEmpty()) {
            return;
        }
        JsonObject event = new JsonObject();
        event.addProperty("type", type);
        event.addProperty("runId", run);
        event.addProperty("outcome", outcome);
        event.addProperty("activeMilliseconds", milliseconds);
        EVENTS.add(event);
    }

    /** 网页一次取走已产生的事件，后续重试使用网页中保留的相同编号 */
    public static synchronized String drain() {
        JsonArray events = new JsonArray();
        while (!EVENTS.isEmpty()) {
            events.add(EVENTS.remove());
        }
        return events.toString();
    }
}
