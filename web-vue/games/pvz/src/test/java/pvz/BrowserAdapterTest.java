package pvz;

import java.nio.file.Files;
import java.nio.file.Path;
import pvz.browser.BrowserGame;
import pvz.browser.BrowserAccess;
import pvz.browser.BrowserGameplay;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

/** 检查浏览器关卡入口的校验与错误消息，全程使用临时目录并清理生成文件 */
public final class BrowserAdapterTest {
    /** 合法与非法记录共用同一字段结构，以检查错误输入是否覆盖已有资料 */
    private static final String VALID_LEVEL =
        "{\"background_type\":0,\"zombie_list\":[{\"time\":1000,\"map_y\":2,\"name\":\"Zombie\"}]}";

    /** 此类只提供自检入口 */
    private BrowserAdapterTest() {
    }

    /** 导入合法关卡后检查内容，再验证未知品种和越界行号的可读提示 */
    public static void main(String[] arguments) throws Exception {
        checkEditorAccess();
        Path root = Files.createTempDirectory("4398-pvz-adapter-");
        Path directory = root.resolve("levels");
        Path target = directory.resolve("level_99.json");
        try {
            check(BrowserGame.importData(root.toString(), 99, VALID_LEVEL).isEmpty(),
                "合法关卡应返回空成功消息");
            check(Files.readString(target).equals(VALID_LEVEL), "导入保留原关卡时间和字段");
            String run = BrowserGameplay.begin(target, 99, false);
            JsonArray started = JsonParser.parseString(BrowserGameplay.drain()).getAsJsonArray();
            check(started.size() == 1, "正式开局应产生一个事件");
            check(started.get(0).getAsJsonObject().get("hash").getAsString().length() == 64,
                "关卡事件应包含原文件指纹");
            BrowserGameplay.progress(run, 5000);
            BrowserGameplay.finish(run, "passed", 6000);
            JsonArray completed = JsonParser.parseString(BrowserGameplay.drain()).getAsJsonArray();
            check(completed.get(0).getAsJsonObject().get("runId").getAsString().equals(run),
                "胜利应使用同一次尝试编号");
            check(completed.size() == 2, "计时和结算应各有一个事件");
            check(completed.get(1).getAsJsonObject().get("outcome").getAsString().equals("passed"),
                "胜利事件应标记通过结果");
            check(BrowserGameplay.drain().equals("[]"), "已取走的事件重复发送");
            String invalidRow = VALID_LEVEL.replace("\"map_y\":2", "\"map_y\":99");
            String warning = BrowserGame.importData(root.toString(), 99, invalidRow);
            check(warning.contains("行号"), "越界行号应返回可读消息");
            check(Files.readString(target).equals(VALID_LEVEL), "失败导入应保留已有关卡");
            warning = BrowserGame.importData(root.toString(), 99, VALID_LEVEL.replace("Zombie", "Unknown"));
            check(warning.contains("僵尸"), "未知品种应返回原 Java 校验消息");
            warning = BrowserGame.importData(root.toString(), 99, "{broken");
            check(!warning.isEmpty(), "损坏 JSON 应有明确结果");
            System.out.println("PASS: Java browser adapter imports and readable validation messages");
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
            Files.delete(root);
        }
    }

    /** 本地编辑器沿用原行为，浏览器中的普通账户无法使用选关页编辑入口 */
    private static void checkEditorAccess() {
        String browser = System.getProperty("pvz.browser");
        String allowed = System.getProperty("platform.editor.allowed");
        try {
            System.clearProperty("pvz.browser");
            check(BrowserAccess.canEdit(), "本地编辑器入口应保持可用");
            System.setProperty("pvz.browser", "true");
            System.setProperty("platform.editor.allowed", "false");
            check(!BrowserAccess.canEdit(), "普通用户仍可以使用编辑器");
            System.setProperty("platform.editor.allowed", "true");
            check(BrowserAccess.canEdit(), "管理员编辑器入口不可用");
        } finally {
            if (browser == null) {
                System.clearProperty("pvz.browser");
            } else {
                System.setProperty("pvz.browser", browser);
            }
            if (allowed == null) {
                System.clearProperty("platform.editor.allowed");
            } else {
                System.setProperty("platform.editor.allowed", allowed);
            }
        }
    }

    /** 条件失败时输出具体检查目的并结束测试 */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
