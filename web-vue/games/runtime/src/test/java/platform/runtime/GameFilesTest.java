package platform.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** 在临时目录检查素材安装、版本更新和关卡文件交换，运行后清理自身生成的文件 */
public final class GameFilesTest {
    /** 测试数据只包含一个关卡和一份图片占位，用于核对文件内容 */
    private static final String FIRST_LEVEL = "{\"zombie_list\":[]}";

    /** 此类通过 main 执行文件操作检查 */
    private GameFilesTest() {
    }

    /** 创建独立临时目录，检查成功与失败分支，最后释放测试生成的数据 */
    public static void main(String[] arguments) throws Exception {
        Path root = Files.createTempDirectory("4398-game-files-");
        try {
            Path archive = root.resolve("assets.zip");
            Files.write(archive, archive("levels/level_0.json", FIRST_LEVEL));
            Path target = root.resolve("first");
            GameFiles.install(archive.toString(), target.toString(), "1", "levels");
            check(Files.readString(target.resolve("levels/level_0.json")).equals(FIRST_LEVEL),
                "首次安装应包含原关卡");
            GameFiles.writeText(target.toString(), "levels/level_0.json", "{\"custom\":true}");
            GameFiles.install(archive.toString(), target.toString(), "2", "levels");
            check(Files.readString(target.resolve("levels/level_0.json")).contains("custom"),
                "版本更新应保留编辑器保存的关卡");
            String backup = GameFiles.exportData(target.toString(), "levels");
            try (ZipInputStream input = new ZipInputStream(
                    new ByteArrayInputStream(Base64.getDecoder().decode(backup)))) {
                check(input.getNextEntry().getName().equals("level_0.json"), "备份采用关卡相对路径");
                check(new String(input.readAllBytes(), StandardCharsets.UTF_8).contains("custom"),
                    "备份应包含用户保存内容");
            }
            Path second = root.resolve("second");
            GameFiles.install(archive.toString(), second.toString(), "1", "levels");
            check(Files.readString(second.resolve("levels/level_0.json")).equals(FIRST_LEVEL),
                "不同账户目录各自安装原关卡");
            Files.write(archive, archive("../escaped.txt", "outside"));
            boolean rejected = false;
            try {
                GameFiles.install(archive.toString(), root.resolve("invalid").toString(), "1", "");
            } catch (java.io.IOException expected) {
                rejected = true;
            }
            check(rejected && !Files.exists(root.resolve("escaped.txt")), "越界路径应在写入前拒绝");
            rejected = false;
            try {
                GameFiles.writeText(target.toString(), "../outside.json", FIRST_LEVEL);
            } catch (java.io.IOException expected) {
                rejected = true;
            }
            check(rejected, "超出账户目录的数据路径应有明确结果");
            GameFiles.writeText(target.toString(), "saves/profile.txt", "generic-save");
            String generic = GameFiles.exportData(target.toString(), "saves");
            check(!generic.isEmpty(), "不同游戏可按配置导出自己的存档目录");
            System.out.println("PASS: asset installation, preserved levels, export, account directories and invalid paths");
        } finally {
            removeTestFiles(root);
        }
    }

    /** 生成单文件压缩包，路径和内容由具体检查指定 */
    private static byte[] archive(String name, String content) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(name));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return bytes.toByteArray();
    }

    /** 条件失败时终止检查，输出当前验证目的 */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    /** 递归清理本测试刚创建的临时目录，所有子路径均来自该目录的枚举 */
    private static void removeTestFiles(Path directory) throws Exception {
        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    removeTestFiles(entry);
                } else {
                    Files.delete(entry);
                }
            }
        }
        Files.delete(directory);
    }
}
