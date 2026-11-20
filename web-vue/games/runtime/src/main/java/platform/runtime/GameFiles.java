package platform.runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** 为 Java 游戏安装浏览器素材并交换关卡，所有写入限定在当前账户游戏目录 */
public final class GameFiles {
    /** 单次解包允许的文件数量上限 */
    private static final int MAX_FILES = 4000;
    /** 单次解包允许的总字节数上限 */
    private static final long MAX_BYTES = 256L * 1024 * 1024;
    /** 复制或解包数据时每次读入的字节数 */
    private static final int BUFFER_SIZE = 8192;
    /** 单个文本文件允许写入的字节数 */
    private static final int MAX_TEXT_BYTES = 2 * 1024 * 1024;
    /** 一个账户的数据备份允许占用的最大字节数 */
    private static final int MAX_BACKUP_BYTES = 8 * 1024 * 1024;

    /** 此类提供文件操作方法，由游戏页面按账户目录调用 */
    private GameFiles() {
    }

    /** 安装素材包；版本更新保留游戏配置中声明的用户数据目录 */
    public static String install(
            String archiveName,
            String directory,
            String version,
            String preservedPaths
    ) throws IOException {
        Path root = readRoot(directory);
        Files.createDirectories(root);
        Path marker = root.resolve(".asset-version");
        if (Files.exists(marker) && Files.readString(marker).equals(version)) {
            return root.toString();
        }
        byte[] buffer = new byte[BUFFER_SIZE];
        long totalBytes = 0;
        int fileCount = 0;
        try (InputStream source = Files.newInputStream(Path.of(archiveName));
                ZipInputStream archive = new ZipInputStream(source)) {
            ZipEntry entry = archive.getNextEntry();
            while (entry != null) {
                Path target = inside(root, entry.getName());
                if (!entry.isDirectory()) {
                    fileCount = fileCount + 1;
                    if (fileCount > MAX_FILES) {
                        throw new IOException("素材包文件数量超过上限");
                    }
                    Files.createDirectories(target.getParent());
                    boolean preserve = isPreserved(entry.getName(), preservedPaths) && Files.exists(target);
                    ByteArrayOutputStream content = new ByteArrayOutputStream();
                    int count = archive.read(buffer);
                    while (count != -1) {
                        totalBytes = totalBytes + count;
                        if (totalBytes > MAX_BYTES) {
                            throw new IOException("素材包展开体积超过上限");
                        }
                        if (!preserve) {
                            content.write(buffer, 0, count);
                        }
                        count = archive.read(buffer);
                    }
                    if (!preserve) {
                        Files.write(target, content.toByteArray());
                    }
                }
                archive.closeEntry();
                entry = archive.getNextEntry();
            }
        }
        Files.writeString(marker, version, StandardCharsets.UTF_8);
        return root.toString();
    }

    /** 核对用户数据目录列表，目录本身和其中的文件都按保留规则处理 */
    private static boolean isPreserved(String name, String preservedPaths) {
        if (preservedPaths.isEmpty()) {
            return false;
        }
        for (String preserved : preservedPaths.split("\n")) {
            if (name.equals(preserved) || name.startsWith(preserved + "/")) {
                return true;
            }
        }
        return false;
    }

    /** 素材可选的游戏只创建账户目录，后续 main 参数仍可引用这个路径 */
    public static String prepare(String directory) throws IOException {
        Path root = readRoot(directory);
        Files.createDirectories(root);
        return root.toString();
    }

    /** 将账户目录转为统一的绝对路径，供读写和目录边界检查使用 */
    private static Path readRoot(String directory) {
        Path accountPath = Path.of(directory);
        Path absolutePath = accountPath.toAbsolutePath();
        return absolutePath.normalize();
    }

    /** 校验相对路径，拒绝绝对路径和跳出当前账户目录的压缩包条目 */
    private static Path inside(Path root, String relative) throws IOException {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || Path.of(relative).isAbsolute() || relative.contains("\\")) {
            throw new IOException("文件路径应位于游戏目录内");
        }
        return target;
    }

    /** 导出配置指定的数据目录为 Base64 压缩包，网页取得后可保存为本机文件 */
    public static String exportData(
            String directory,
            String relative
    ) throws IOException {
        Path root = readRoot(directory);
        Path levels = inside(root, relative);
        Files.createDirectories(levels);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            addDirectory(levels, levels, output);
        }
        if (bytes.size() > MAX_BACKUP_BYTES) {
            throw new IOException("数据备份超过八兆字节，请分批导出");
        }
        Base64.Encoder encoder = Base64.getEncoder();
        byte[] archive = bytes.toByteArray();
        return encoder.encodeToString(archive);
    }

    /** 递归导出指定数据目录，限制单文件体积并保留相对路径 */
    private static void addDirectory(
            Path root,
            Path directory,
            ZipOutputStream output
    ) throws IOException {
        try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (Files.isDirectory(file)) {
                    addDirectory(root, file, output);
                } else if (Files.isRegularFile(file)) {
                    if (Files.size(file) > MAX_TEXT_BYTES) {
                        throw new IOException("单个数据文件超过两兆字节");
                    }
                    Path relative = root.relativize(file);
                    String localName = relative.toString();
                    String name = localName.replace('\\', '/');
                    output.putNextEntry(new ZipEntry(name));
                    Files.copy(file, output);
                    output.closeEntry();
                }
            }
        }
    }

    /** 写入游戏适配器已验证的文本，目标相对路径始终限定在账户游戏目录中 */
    public static void writeText(
            String directory,
            String relative,
            String text
    ) throws IOException {
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_TEXT_BYTES) {
            throw new IOException("数据文件应小于两兆字节");
        }
        Path root = readRoot(directory);
        Path target = inside(root, relative);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }
}
