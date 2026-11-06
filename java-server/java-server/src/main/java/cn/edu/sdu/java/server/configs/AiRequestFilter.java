package cn.edu.sdu.java.server.configs;

import cn.edu.sdu.java.server.util.AiImageRules;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** AI 入口先限制请求次数和大小，再交给认证与 JSON 解析 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AiRequestFilter extends OncePerRequestFilter {
    // 入口频率窗口的时长，单位为毫秒
    private static final long WINDOW_MILLISECONDS = 60000;
    // 单个窗口保存的来源数量上限
    private static final int ADDRESS_LIMIT = 10000;
    private final int bodyLimit;
    private final int attemptsPerMinute;
    private final Map<String, Integer> attempts = new HashMap<>();
    private long windowStartedAt;

    /** 从配置读取 AI 请求字节上限和每个来源的入口请求上限 */
    public AiRequestFilter(@Value("${app.ai.request-byte-limit:16384}") int bodyLimit,
            @Value("${app.ai.ip-attempt-limit:60}") int attemptsPerMinute) {
        if (bodyLimit <= 0 || attemptsPerMinute <= 0) {
            throw new IllegalArgumentException("AI 请求保护上限必须大于零");
        }
        this.bodyLimit = bodyLimit;
        this.attemptsPerMinute = attemptsPerMinute;
    }

    /** 只处理 AI 接口，其他页面和接口继续使用原有处理流程 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.isEmpty()) {
            path = request.getRequestURI();
        }
        return !path.startsWith("/api/ai/");
    }

    /** 按真实连接来源原子计数，窗口结束后清空来源表 */
    private synchronized boolean allowAttempt(String address) {
        long now = System.currentTimeMillis();
        if (now - windowStartedAt >= WINDOW_MILLISECONDS || now < windowStartedAt) {
            windowStartedAt = now;
            attempts.clear();
        }
        Integer count = attempts.get(address);
        if (count == null) {
            if (attempts.size() >= ADDRESS_LIMIT) {
                return false;
            }
            count = 0;
        }
        if (count >= attemptsPerMinute) {
            return false;
        }
        attempts.put(address, count + 1);
        return true;
    }

    /** 限制请求体后保留原始内容，后续控制器仍能正常读取 JSON */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if ("OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        if (!allowAttempt(request.getRemoteAddr())) {
            response.setHeader("Retry-After", "60");
            reject(response, 429, "请求较频繁，请稍后重试");
            return;
        }
        String contentType = request.getContentType();
        if (!"POST".equals(request.getMethod())) {
            reject(response, 405, "此接口只接受 POST 请求");
            return;
        }
        int requestLimit = bodyLimit;
        if ("/api/ai/image-chat".equals(request.getRequestURI())) {
            requestLimit = AiImageRules.MAX_REQUEST_BYTES;
        }
        if (request.getContentLengthLong() > requestLimit) {
            reject(response, 413, "AI 请求内容过大");
            return;
        }
        if (request.getHeader("Content-Encoding") != null) {
            reject(response, 415, "AI 请求不接受压缩内容");
            return;
        }
        if (contentType != null) {
            String[] contentParts = contentType.split(";", 2);
            String mediaType = contentParts[0].trim();
            if (!mediaType.equalsIgnoreCase("application/json")) {
                reject(response, 415, "AI 请求需要 JSON 内容");
                return;
            }
        }
        // 即使没有 Content-Length，也只读取上限加一个字节
        byte[] body = request.getInputStream().readNBytes(requestLimit + 1);
        if (body.length > requestLimit) {
            reject(response, 413, "AI 请求内容过大");
            return;
        }
        chain.doFilter(new JsonRequest(request, body), response);
    }

    /** 返回固定提示的 JSON 错误，前端按状态码和提示显示结果 */
    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":1,\"msg\":\"" + message + "\"}");
    }

    /** 在大小检查后提供同一份请求体，供 Spring 反序列化 */
    private static class JsonRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        /** 保存已经检查过的原始请求和字节 */
        private JsonRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        /** 为后续读取创建新的字节输入流 */
        @Override
        public ServletInputStream getInputStream() {
            return new JsonInput(body);
        }
    }

    /** 将内存中的请求字节适配为 Servlet 输入流 */
    private static class JsonInput extends ServletInputStream {
        private final ByteArrayInputStream input;

        /** 保存已经限长的请求字节 */
        private JsonInput(byte[] body) {
            input = new ByteArrayInputStream(body);
        }

        /** 返回下一个字节，读完时返回负一 */
        @Override
        public int read() {
            return input.read();
        }

        /** 判断请求体是否已经读完 */
        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        /** 内存流随时可读 */
        @Override
        public boolean isReady() {
            return true;
        }

        /** 当前控制器同步读取 JSON，异步读取请求返回明确异常 */
        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException("AI JSON 请求使用同步读取");
        }
    }
}
