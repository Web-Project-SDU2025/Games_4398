package pvz.game;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import pvz.tools.TextDrawing;
import pvz.world.Layout;

/** 在原战场上弹出字块币，白光扩散后淡入原有胜利画面 */
final class VictoryAnimation {
    static final long POP_DURATION = 640;
    static final long FLASH_DURATION = 2850;
    static final long FADE_DURATION = 650;
    private static final Color GOLD = new Color(255, 210, 121);
    private static final Color COIN_FACE = new Color(66, 46, 23);
    private static BufferedImage light;

    private VictoryAnimation() {
    }

    /** 轻微弹跳结束时停在僵尸死亡处上方 */
    static int coinY(GameState state, long time) {
        double progress = Math.min(1, (double) (time - state.screenStart) / POP_DURATION);
        return state.victoryY - (int) (20 * progress + 32 * Math.sin(Math.PI * progress));
    }

    /** 参数 time 是当前画面时间，点击前显示奖励，点击后以奖励位置为光源 */
    static void draw(Graphics2D painter, long time, GameState state) {
        Graphics2D effect = (Graphics2D) painter.create();
        effect.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (state.victoryFlashStart < 0) {
            int y = coinY(state, time);
            drawCoin(effect, state.victoryX, y);
            int hintTop = Math.min(Layout.WINDOW_HEIGHT - 30, y + 38);
            int hintLeft = Math.max(4, Math.min(Layout.WINDOW_WIDTH - 148, state.victoryX - 72));
            effect.setColor(new Color(28, 33, 24, 230));
            effect.fillRoundRect(hintLeft, hintTop, 144, 26, 8, 8);
            effect.setColor(GOLD);
            effect.setFont(new Font("Dialog", Font.BOLD, 15));
            TextDrawing.drawCentered(effect, "点击领取字块币",
                new Rectangle(hintLeft, hintTop, 144, 26));
        } else {
            double progress = (double) (time - state.victoryFlashStart) / FLASH_DURATION;
            if (progress >= .86) {
                drawWhite(effect, 1);
            } else {
                drawCoin(effect, state.victoryX, state.victoryY);
                drawLight(effect, state.victoryX, state.victoryY, progress);
            }
        }
        effect.dispose();
    }

    /** 三层金色线条沿用平台字块币图标的外形 */
    private static void drawCoin(Graphics2D painter, int x, int y) {
        painter.setColor(new Color(255, 212, 120, 50));
        painter.fillOval(x - 36, y - 36, 72, 72);
        painter.setColor(COIN_FACE);
        painter.fillOval(x - 30, y - 30, 60, 60);
        painter.setColor(GOLD);
        painter.setStroke(new BasicStroke(2));
        painter.drawOval(x - 30, y - 30, 60, 60);
        painter.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        painter.drawOval(x - 17, y - 16, 34, 15);
        painter.drawArc(x - 17, y - 8, 34, 16, 180, 180);
        painter.drawArc(x - 17, y, 34, 16, 180, 180);
    }

    /** 光束先照向四周，白色柔边随后覆盖距光源最远的角落 */
    private static void drawLight(Graphics2D painter, int x, int y, double progress) {
        double growth = Math.min(1, progress / .82);
        double corner = Math.hypot(Math.max(x, Layout.WINDOW_WIDTH - x),
            Math.max(y, Layout.WINDOW_HEIGHT - y));
        int radius = 10 + (int) (corner / .64 * growth * growth);
        int rayLength = (int) (corner * Math.min(1, growth * 1.5));
        int rayAlpha = (int) (135 * Math.sin(Math.PI * growth));
        painter.setColor(new Color(255, 248, 211, rayAlpha));
        for (int ray = 0; ray < 7; ray = ray + 1) {
            double angle = ray * Math.PI * 2 / 7 + growth * .38;
            int leftX = x + (int) (Math.cos(angle - .075) * rayLength);
            int leftY = y + (int) (Math.sin(angle - .075) * rayLength);
            int rightX = x + (int) (Math.cos(angle + .075) * rayLength);
            int rightY = y + (int) (Math.sin(angle + .075) * rayLength);
            painter.fillPolygon(new int[] {x, leftX, rightX}, new int[] {y, leftY, rightY}, 3);
        }
        if (light == null) {
            light = createLight();
        }
        painter.drawImage(light, x - radius, y - radius, radius * 2, radius * 2, null);
    }

    /** 生成一张可复用的白光贴图，中心为白色，外缘平滑透明 */
    private static BufferedImage createLight() {
        int size = 128;
        double center = (size - 1) / 2.0;
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y = y + 1) {
            for (int x = 0; x < size; x = x + 1) {
                double distance = Math.hypot(x - center, y - center) / center;
                double edge = Math.max(0, Math.min(1, (1 - distance) / .35));
                int alpha = (int) (255 * edge * edge * (3 - 2 * edge));
                pixels[y * size + x] = alpha << 24 | 0x00ffffff;
            }
        }
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
        image.setRGB(0, 0, size, size, pixels, 0, size);
        return image;
    }

    /** 原胜利画面铺好后，让白光逐渐退去 */
    static void drawFade(Graphics2D painter, long elapsed) {
        if (elapsed < FADE_DURATION) {
            drawWhite(painter, 1 - (float) elapsed / FADE_DURATION);
        }
    }

    private static void drawWhite(Graphics2D painter, float opacity) {
        Graphics2D overlay = (Graphics2D) painter.create();
        overlay.setComposite(AlphaComposite.SrcOver.derive(opacity));
        overlay.setColor(Color.WHITE);
        overlay.fillRect(0, 0, Layout.WINDOW_WIDTH, Layout.WINDOW_HEIGHT);
        overlay.dispose();
    }
}
