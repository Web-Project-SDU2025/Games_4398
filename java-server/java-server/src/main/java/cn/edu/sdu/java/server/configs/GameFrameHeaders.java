package cn.edu.sdu.java.server.configs;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;

/** 只允许游戏启动页由同源平台嵌入，其余页面继续使用默认框架保护 */
public final class GameFrameHeaders implements HeaderWriter {
    /** 唯一允许嵌入的站内游戏页面，查询参数沿用同一路径 */
    private static final String PLAYER_PATH = "/games/java-player.html";

    /** 默认安全头写入后，仅覆盖游戏启动页的同源嵌入规则 */
    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        String playerAddress = request.getContextPath() + PLAYER_PATH;
        if (playerAddress.equals(request.getRequestURI())) {
            response.setHeader("X-Frame-Options", "SAMEORIGIN");
            response.setHeader("Content-Security-Policy", "frame-ancestors 'self'");
        }
    }
}
