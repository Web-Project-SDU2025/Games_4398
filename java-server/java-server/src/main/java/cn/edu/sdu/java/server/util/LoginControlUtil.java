package cn.edu.sdu.java.server.util;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** 生成图形验证码，限制内存数量和有效期，验证后立即删除 */
public class LoginControlUtil {
    // 验证码图片宽度，单位为像素
    private static final int IMAGE_WIDTH = 54;
    // 验证码图片高度，单位为像素
    private static final int IMAGE_HEIGHT = 20;
    // 每张图片包含的数字个数
    private static final int CODE_LENGTH = 4;
    // 同时保存在内存中的验证码数量上限
    private static final int CODE_LIMIT = 2000;
    // 验证码有效期，单位为毫秒
    private static final long CODE_LIFETIME = 5 * 60 * 1000;
    private static final LoginControlUtil INSTANCE = new LoginControlUtil();
    private final SecureRandom random = new SecureRandom();
    private final Map<Integer, Captcha> codes = new HashMap<>();

    /** 返回所有验证码接口共用的管理对象 */
    public static LoginControlUtil getInstance() {
        return INSTANCE;
    }

    /** 生成随机数字和 PNG 图片，返回验证码编号与图片地址 */
    public synchronized Map<String, Object> getValidateCodeDataMap() {
        removeExpiredCodes();
        if (codes.size() >= CODE_LIMIT) {
            throw new IllegalArgumentException("验证码请求较多，请稍后重试");
        }
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        StringBuilder text = new StringBuilder();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
            graphics.setFont(new Font("Fixedsys", Font.BOLD, IMAGE_HEIGHT - 2));
            graphics.setColor(new Color(147, 14, 20));
            int spacing = IMAGE_WIDTH / (CODE_LENGTH + 1);
            for (int index = 0; index < CODE_LENGTH; index = index + 1) {
                String digit = Integer.toString(random.nextInt(10));
                int left = (index + 1) * spacing - 6;
                graphics.drawString(digit, left, IMAGE_HEIGHT - 4);
                text.append(digit);
            }
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            String encoded = Base64.getEncoder().encodeToString(output.toByteArray());
            Integer codeId = random.nextInt();
            while (codes.containsKey(codeId)) {
                codeId = random.nextInt();
            }
            codes.put(codeId, new Captcha(text.toString(), System.currentTimeMillis()));
            Map<String, Object> result = new HashMap<>();
            result.put("validateCodeId", codeId);
            result.put("img", "data:image/png;base64," + encoded);
            return result;
        } catch (IOException error) {
            throw new IllegalStateException("验证码图片生成失败", error);
        }
    }

    /** 读取仍有效的验证码，供原有检查接口和测试使用 */
    public synchronized String getValidateCode(Integer codeId) {
        removeExpiredCodes();
        Captcha captcha = codes.get(codeId);
        if (captcha == null) {
            return null;
        }
        return captcha.text;
    }

    /** 比较并移除验证码，空输入或过期输入返回失败 */
    public synchronized boolean checkAndRemoveCode(Integer codeId, String code) {
        removeExpiredCodes();
        if (codeId == null || code == null || code.isBlank()) {
            return false;
        }
        Captcha captcha = codes.remove(codeId);
        if (captcha == null) {
            return false;
        }
        return code.equals(captcha.text);
    }

    /** 清除超过有效期的验证码，后续请求复用释放的内存名额 */
    private void removeExpiredCodes() {
        long now = System.currentTimeMillis();
        Iterator<Captcha> iterator = codes.values().iterator();
        while (iterator.hasNext()) {
            Captcha captcha = iterator.next();
            if (now - captcha.createdAt >= CODE_LIFETIME) {
                iterator.remove();
            }
        }
    }

    /** 保存验证码文字和生成时间 */
    private static class Captcha {
        private final String text;
        private final long createdAt;

        /** 保存本次验证码的内容和时间 */
        private Captcha(String text, long createdAt) {
            this.text = text;
            this.createdAt = createdAt;
        }
    }
}
