package billiards.render;

import billiards.world.Ball;
import billiards.world.BallCatalog;
import billiards.world.Table;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** 构建时使用原绘制器生成静态资源，资源随参数变化重新生成 */
public final class RenderAssetBuilder {
    /** 球影边长包含渐隐边缘，单位为逻辑像素 */
    private static final int SHADOW_SIZE = 48;

    /** 此工具只提供构建入口 */
    private RenderAssetBuilder() {
    }

    /** 参数为编译目录，球台、球体和球影 PNG 写入统一类路径资源目录 */
    public static void main(String[] arguments) throws Exception {
        Path output = Path.of(arguments[0], "billiards", "render");
        Files.createDirectories(output);
        TableTexture table = TableTexture.bake(new Table());
        ImageIO.write(table.image(), "png", output.resolve("table.png").toFile());
        BallSpriteCache sprites = new BallSpriteCache();
        for (int ballId = 0; ballId < BallCatalog.BALL_COUNT; ballId = ballId + 1) {
            ImageIO.write(sprites.sprite(ballId), "png", output.resolve("ball-" + ballId + ".png").toFile());
        }
        BufferedImage shadow = new BufferedImage(SHADOW_SIZE, SHADOW_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = shadow.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            new BallRenderer().paintShadow(graphics, SHADOW_SIZE / 2.0, SHADOW_SIZE / 2.0, Ball.RADIUS);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(shadow, "png", output.resolve("shadow.png").toFile());
    }
}
