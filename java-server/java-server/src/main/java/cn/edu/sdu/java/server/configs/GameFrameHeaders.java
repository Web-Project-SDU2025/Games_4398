package cn.edu.sdu.java.server.configs;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;

/** 只允许游戏启动页由同源平台嵌入，其余页面继续使用默认框架保护 */
public final class GameFrameHeaders implements HeaderWriter {
    /** 仅放行实际游戏页面，查询参数不改变路径匹配，其余页面继续禁止嵌入 */
    private static final java.util.List<String> PLAYER_PATHS = java.util.List.of(
            "/games/java-player.html", "/games/native-player.html",
            "/games/billiards/runtime/index.html");

    /** 默认安全头写入后，仅覆盖游戏启动页的同源嵌入规则 */
    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        for (String path : PLAYER_PATHS) {
            String playerAddress = request.getContextPath() + path;
            if (playerAddress.equals(request.getRequestURI())) {
                response.setHeader("X-Frame-Options", "SAMEORIGIN");
                response.setHeader("Content-Security-Policy", "frame-ancestors 'self'");
                return;
            }
        }
    }
}
