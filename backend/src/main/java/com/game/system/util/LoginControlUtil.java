package com.game.system.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * LoginControlUtil 登录控制工具类
 * 提供验证码生成、验证码校验、登录失败次数统计等功能
 */
public class LoginControlUtil {

    // 验证码存储Map（用户标识 -> 验证码）
    private Map<String, String> codeMap = new HashMap<>();

    // 验证码生成时间Map（用户标识 -> 时间戳）
    private Map<String, Long> codeTimeMap = new HashMap<>();

    // 登录失败次数统计Map（用户名 -> 失败次数）
    private Map<String, Integer> loginFailCountMap = new HashMap<>();

    // 登录失败锁定时间Map（用户名 -> 锁定到期时间戳）
    private Map<String, Long> loginLockTimeMap = new HashMap<>();

    // 验证码有效期（毫秒）- 5分钟
    private static final long CODE_VALID_TIME = 5 * 60 * 1000;

    // 登录失败锁定时间（毫秒）- 15分钟
    private static final long LOCK_TIME = 15 * 60 * 1000;

    // 最大登录失败次数
    private static final int MAX_FAIL_COUNT = 5;

    // 单例实例
    private static LoginControlUtil instance = new LoginControlUtil();

    /**
     * 获取单例实例
     */
    public static LoginControlUtil getInstance() {
        return instance;
    }

    // ==================== 图形验证码相关 ====================

    /**
     * 生成图形验证码（4位数字）
     * @param key 用户标识（如sessionId、IP等）
     * @return Base64编码的验证码图片
     */
    public synchronized String generateImageCode(String key) {
        try {
            // 生成4位随机数字
            Random random = new Random();
            String code = String.format("%04d", random.nextInt(10000));

            // 存储验证码
            codeMap.put(key, code);
            codeTimeMap.put(key, System.currentTimeMillis());

            // 创建图片
            int width = 120;
            int height = 40;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();

            // 设置背景色
            g.setColor(new Color(240, 240, 240));
            g.fillRect(0, 0, width, height);

            // 绘制干扰线
            g.setColor(new Color(200, 200, 200));
            for (int i = 0; i < 5; i++) {
                int x1 = random.nextInt(width);
                int y1 = random.nextInt(height);
                int x2 = random.nextInt(width);
                int y2 = random.nextInt(height);
                g.drawLine(x1, y1, x2, y2);
            }

            // 绘制验证码
            g.setFont(new Font("Arial", Font.BOLD, 28));
            for (int i = 0; i < code.length(); i++) {
                g.setColor(new Color(random.nextInt(150), random.nextInt(150), random.nextInt(150)));
                g.drawString(String.valueOf(code.charAt(i)), 20 + i * 25, 28);
            }

            g.dispose();

            // 转换为Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] bytes = baos.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 验证图形验证码
     * @param key 用户标识
     * @param code 用户输入的验证码
     * @return true-验证通过，false-验证失败
     */
    public synchronized boolean verifyImageCode(String key, String code) {
        if (key == null || code == null || code.isEmpty()) {
            return false;
        }

        String storedCode = codeMap.get(key);
        Long timestamp = codeTimeMap.get(key);

        // 检查验证码是否存在
        if (storedCode == null || timestamp == null) {
            return false;
        }

        // 检查验证码是否过期
        if (System.currentTimeMillis() - timestamp > CODE_VALID_TIME) {
            codeMap.remove(key);
            codeTimeMap.remove(key);
            return false;
        }

        // 验证码匹配（忽略大小写）
        boolean isValid = storedCode.equalsIgnoreCase(code);

        // 验证后删除验证码（一次性使用）
        if (isValid) {
            codeMap.remove(key);
            codeTimeMap.remove(key);
        }

        return isValid;
    }

    // ==================== 短信验证码相关 ====================

    /**
     * 生成短信验证码（6位数字）
     * @param phone 手机号
     * @return 验证码
     */
    public synchronized String generateSmsCode(String phone) {
        Random random = new Random();
        String code = String.format("%06d", random.nextInt(1000000));

        codeMap.put("sms_" + phone, code);
        codeTimeMap.put("sms_" + phone, System.currentTimeMillis());

        return code;
    }

    /**
     * 验证短信验证码
     * @param phone 手机号
     * @param code 验证码
     * @return true-验证通过，false-验证失败
     */
    public synchronized boolean verifySmsCode(String phone, String code) {
        String key = "sms_" + phone;
        return verifyImageCode(key, code);
    }

    // ==================== 登录失败控制相关 ====================

    /**
     * 记录登录失败
     * @param username 用户名
     */
    public synchronized void recordLoginFail(String username) {
        Integer count = loginFailCountMap.getOrDefault(username, 0);
        count++;
        loginFailCountMap.put(username, count);

        // 如果失败次数达到上限，锁定账户
        if (count >= MAX_FAIL_COUNT) {
            long lockUntil = System.currentTimeMillis() + LOCK_TIME;
            loginLockTimeMap.put(username, lockUntil);
        }
    }

    /**
     * 清除登录失败记录（登录成功后调用）
     * @param username 用户名
     */
    public synchronized void clearLoginFail(String username) {
        loginFailCountMap.remove(username);
        loginLockTimeMap.remove(username);
    }

    /**
     * 检查账户是否被锁定
     * @param username 用户名
     * @return true-已锁定，false-未锁定
     */
    public synchronized boolean isLocked(String username) {
        Long lockUntil = loginLockTimeMap.get(username);
        if (lockUntil == null) {
            return false;
        }

        // 检查锁定是否已过期
        if (System.currentTimeMillis() >= lockUntil) {
            loginLockTimeMap.remove(username);
            loginFailCountMap.remove(username);
            return false;
        }

        return true;
    }

    /**
     * 获取账户剩余锁定时间（分钟）
     * @param username 用户名
     * @return 剩余锁定时间（分钟）
     */
    public synchronized long getLockRemainingMinutes(String username) {
        Long lockUntil = loginLockTimeMap.get(username);
        if (lockUntil == null) {
            return 0;
        }

        long remaining = lockUntil - System.currentTimeMillis();
        return remaining > 0 ? (remaining / 1000 / 60) : 0;
    }

    /**
     * 获取登录失败次数
     * @param username 用户名
     * @return 失败次数
     */
    public synchronized int getLoginFailCount(String username) {
        return loginFailCountMap.getOrDefault(username, 0);
    }

    /**
     * 清空所有数据
     */
    public synchronized void clearAll() {
        codeMap.clear();
        codeTimeMap.clear();
        loginFailCountMap.clear();
        loginLockTimeMap.clear();
    }
}
