package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.configs.AiRequestFilter;
import cn.edu.sdu.java.server.services.AiRequestGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

/** 用临时文件与本机请求替身验证 AI 保护的拒绝和恢复分支 */
class AiProtectionTests {
    // 所有请求替身共用的文档示例地址
    private static final String ADDRESS = "192.0.2.1";
    @TempDir
    Path temporaryDirectory;

    /** 创建固定日期的保护对象，测试次数由参数指定 */
    private AiRequestGuard guard(String filename, int concurrent, int accountDaily, int globalDaily) {
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T04:00:00Z"), ZoneOffset.UTC);
        return new AiRequestGuard(new ObjectMapper(), temporaryDirectory.resolve(filename), clock,
                concurrent, 8, 20, 60, accountDaily, globalDaily);
    }

    /** 断言拒绝文字，命名方法直接执行待验证操作 */
    private void expectRejection(AiRequestGuard guard, int userId, String address, String text) {
        try {
            guard.begin(userId, address);
            fail("此请求应该达到保护上限");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains(text));
        }
    }

    /** 全局按请求数限制并发，同一账号可同时占用多个名额 */
    @Test
    void concurrentRequestsHaveBoundedSlots() {
        AiRequestGuard guard = guard("concurrent.json", 2, 40, 500);
        guard.begin(1, ADDRESS);
        guard.begin(1, ADDRESS);
        expectRejection(guard, 3, ADDRESS, "并发");
        guard.finish(1);
        guard.begin(3, ADDRESS);
        expectRejection(guard, 2, ADDRESS, "并发");
        guard.finish(1);
        guard.finish(3);
        guard.begin(2, ADDRESS);
        guard.finish(2);
    }

    /** 同一账号可占满五个名额，第六条被拒绝，释放一个后恢复一次调用 */
    @Test
    void accountCanUseFiveConcurrentSlots() {
        AiRequestGuard guard = guard("five-slots.json", 5, 40, 500);
        for (int index = 0; index < 5; index = index + 1) {
            guard.begin(1, ADDRESS);
        }
        expectRejection(guard, 1, ADDRESS, "并发");
        expectRejection(guard, 2, ADDRESS, "并发");
        guard.finish(1);
        guard.begin(2, ADDRESS);
        for (int index = 0; index < 4; index = index + 1) {
            guard.finish(1);
        }
        // 已释放的账户再次结束时保持现有计数，其他账户名额继续生效
        guard.finish(1);
        for (int index = 0; index < 4; index = index + 1) {
            guard.begin(3, ADDRESS);
        }
        expectRejection(guard, 4, ADDRESS, "并发");
        guard.finish(2);
        guard.begin(4, ADDRESS);
    }

    /** 每日用量保存在文件中，重新创建服务仍保留已预扣的次数 */
    @Test
    void dailyQuotaSurvivesRestart() {
        AiRequestGuard original = guard("daily.json", 2, 1, 2);
        original.begin(1, ADDRESS);
        original.finish(1);
        AiRequestGuard restored = guard("daily.json", 2, 1, 2);
        expectRejection(restored, 1, ADDRESS, "今日");
        restored.begin(2, ADDRESS);
        restored.finish(2);
        expectRejection(restored, 3, ADDRESS, "今日");
    }

    /** 同一来源轮换账户时仍受每分钟 IP 限制 */
    @Test
    void switchingAccountsKeepsAddressLimit() {
        AiRequestGuard guard = guard("address.json", 2, 40, 500);
        for (int userId = 1; userId <= 20; userId = userId + 1) {
            guard.begin(userId, ADDRESS);
            guard.finish(userId);
        }
        expectRejection(guard, 21, ADDRESS, "频繁");
        guard.begin(21, "192.0.2.2");
        guard.finish(21);
    }

    /** 下一天按北京时间开始新的日额度，分钟与并发独立计算 */
    @Test
    void quotaResetsAtBeijingMidnight() {
        AiRequestGuard original = guard("midnight.json", 2, 1, 2);
        original.begin(1, ADDRESS);
        original.finish(1);
        Clock tomorrow = Clock.fixed(Instant.parse("2026-11-04T16:00:00Z"), ZoneOffset.UTC);
        AiRequestGuard nextDay = new AiRequestGuard(new ObjectMapper(), temporaryDirectory.resolve("midnight.json"),
                tomorrow, 2, 8, 20, 60, 1, 2);
        nextDay.begin(1, ADDRESS);
        nextDay.finish(1);
    }

    /** 超长请求和频繁入口请求在 JSON 解析前返回明确错误 */
    @Test
    void filterRejectsLargeBodiesAndFrequentAttempts() throws Exception {
        AiRequestFilter filter = new AiRequestFilter(32, 1);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ai/chat");
        request.setContentType("application/json");
        request.setContent("a".repeat(33).getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(413, response.getStatus());
        assertNull(chain.getRequest());
        MockHttpServletRequest repeated = new MockHttpServletRequest("POST", "/api/ai/chat");
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(repeated, limited, new MockFilterChain());
        assertEquals(429, limited.getStatus());
    }

    /** 正常 JSON 通过限长包装后保留原始字节 */
    @Test
    void acceptedBodyRemainsReadable() throws Exception {
        AiRequestFilter filter = new AiRequestFilter(64, 2);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ai/status");
        request.setContentType("application/json");
        byte[] body = "{\"data\":{}}".getBytes(StandardCharsets.UTF_8);
        request.setContent(body);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        byte[] received = chain.getRequest().getInputStream().readAllBytes();
        assertArrayEquals(body, received);
    }

    /** 两个服务对象共用文件时，每次预扣读取最新计数 */
    @Test
    void sharedQuotaFileKeepsLatestCounters() {
        AiRequestGuard first = guard("shared.json", 2, 1, 2);
        AiRequestGuard second = guard("shared.json", 2, 1, 2);
        first.begin(1, ADDRESS);
        first.finish(1);
        expectRejection(second, 1, ADDRESS, "今日");
        second.begin(2, ADDRESS);
        second.finish(2);
        expectRejection(first, 3, ADDRESS, "今日");
    }

    /** 用量文件在运行中损坏时返回拒绝提示，修复后下一次请求恢复 */
    @Test
    void corruptedQuotaStopsCallsUntilRepaired() throws Exception {
        AiRequestGuard guard = guard("corrupt.json", 2, 40, 500);
        Path file = temporaryDirectory.resolve("corrupt.json");
        Files.writeString(file, "broken-json");
        expectRejection(guard, 1, ADDRESS, "用量记录");
        Files.writeString(file, "{\"day\":\"2026-11-04\",\"total\":0,\"accounts\":{}}");
        guard.begin(1, ADDRESS);
        guard.finish(1);
    }

    /** 用量父路径无法创建时拒绝调用，失败请求释放后续检查机会 */
    @Test
    void unwritableQuotaRejectsBeforeCloudCall() throws Exception {
        Path parent = temporaryDirectory.resolve("blocked");
        AiRequestGuard guard = guard("blocked/usage.json", 2, 40, 500);
        Files.writeString(parent, "file-instead-of-directory");
        expectRejection(guard, 1, ADDRESS, "用量记录");
        expectRejection(guard, 1, ADDRESS, "用量记录");
    }

    /** 伪 JSON 类型、压缩请求和错误方法分别在入口返回协议错误 */
    @Test
    void filterChecksMediaTypeEncodingAndMethod() throws Exception {
        AiRequestFilter filter = new AiRequestFilter(64, 10);
        MockHttpServletRequest wrongType = new MockHttpServletRequest("POST", "/api/ai/chat");
        wrongType.setContentType("application/json-other");
        MockHttpServletResponse typeResponse = new MockHttpServletResponse();
        filter.doFilter(wrongType, typeResponse, new MockFilterChain());
        assertEquals(415, typeResponse.getStatus());

        MockHttpServletRequest compressed = new MockHttpServletRequest("POST", "/api/ai/chat");
        compressed.addHeader("Content-Encoding", "gzip");
        MockHttpServletResponse encodingResponse = new MockHttpServletResponse();
        filter.doFilter(compressed, encodingResponse, new MockFilterChain());
        assertEquals(415, encodingResponse.getStatus());

        MockHttpServletRequest wrongMethod = new MockHttpServletRequest("GET", "/api/ai/chat");
        MockHttpServletResponse methodResponse = new MockHttpServletResponse();
        filter.doFilter(wrongMethod, methodResponse, new MockFilterChain());
        assertEquals(405, methodResponse.getStatus());
    }
}
