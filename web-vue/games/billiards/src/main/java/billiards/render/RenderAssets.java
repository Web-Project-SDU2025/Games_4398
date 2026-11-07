package billiards.render;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** 加载构建时生成的静态图像，浏览器无需逐像素计算木纹和球体 */
public final class RenderAssets {
    /** 资源目录随游戏 JAR 发布，构建工具按同一路径写入 */
    public static final String DIRECTORY = "/billiards/render/";
    /** 浏览器模式由适配器设置，桌面自检保留程序绘制以验证生成结果 */
    public static final String BROWSER_PROPERTY = "billiards.browser";

    /** 工具类只提供资源加载方法 */
    private RenderAssets() {
    }

    /** 返回当前是否运行在浏览器适配器中 */
    public static boolean browserMode() {
        return Boolean.getBoolean(BROWSER_PROPERTY);
    }

    /** 按名称读取完整 PNG，缺少构建资源时立即报告，避免出现长时间空白 */
    public static BufferedImage load(String name) {
        try (InputStream input = RenderAssets.class.getResourceAsStream(DIRECTORY + name + ".png")) {
            if (input == null) {
                throw new IllegalStateException("游戏静态图像缺失：" + name);
            }
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IllegalStateException("游戏静态图像无法读取：" + name);
            }
            return image;
        } catch (IOException error) {
            throw new IllegalStateException("游戏静态图像加载失败：" + name, error);
        }
    }
}
