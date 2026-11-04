package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.controllers.AiController;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.services.AiService;
import cn.edu.sdu.java.server.services.AiRequestGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用网络替身检查百炼协议、失败分支和账号隔离，不访问数据库 */
class AiTests {
    @TempDir
    Path temporaryDirectory;
    private final ObjectMapper mapper = spy(new ObjectMapper());
    private final HttpClient client = mock(HttpClient.class);

    /** 每项测试使用独立用量文件，固定上限覆盖正常调用与限流分支 */
    private AiService service(String apiKey) {
        Path quotaFile = temporaryDirectory.resolve("usage-" + UUID.randomUUID() + ".json");
        AiRequestGuard guard = new AiRequestGuard(new ObjectMapper(), quotaFile, Clock.systemUTC(),
                4, 8, 20, 60, 40, 500);
        return new AiService(apiKey, mapper, client, guard);
    }

    /** 为下一次调用准备确定的云端响应 */
    private void respond(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
    }

    /** 检查 JSON 编码器收到的请求参数，按调用顺序核对字段 */
    private JsonNode requestBody(int index, int count) throws Exception {
        ArgumentCaptor<Object> bodies = ArgumentCaptor.forClass(Object.class);
        verify(mapper, times(count)).writeValueAsString(bodies.capture());
        List<Object> values = bodies.getAllValues();
        return mapper.valueToTree(values.get(index));
    }

    /** 执行预期失败的请求，按错误文字核对拒绝分支 */
    private void expectError(AiService service, Integer userId, String id, String message, String expected) {
        try {
            service.chat(userId, id, message, "127.0.0.1");
            fail("此请求应当被拒绝");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains(expected));
        }
    }

    /** 固定应用 ID、游戏提示词和 Bearer 密钥都放在正确位置 */
    @Test
    void usesReferenceApplicationAndGameInstructions() throws Exception {
        respond(200, "{\"output\":{\"text\":\"五子连线获胜\",\"session_id\":\"cloud-session\"}}");
        AiService service = service("test-key");
        Map<String, Object> result = service.chat(1, UUID.randomUUID().toString(), "五子棋规则", "127.0.0.1");
        assertEquals("五子连线获胜", result.get("reply"));
        assertFalse(result.containsKey("sessionId"));
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        HttpRequest request = requests.getValue();
        assertTrue(request.uri().toString().contains(AiService.APPLICATION_ID));
        assertEquals("Bearer test-key", request.headers().firstValue("Authorization").orElse(""));
        JsonNode body = requestBody(0, 1);
        JsonNode input = body.path("input");
        String prompt = input.path("prompt").asText();
        assertTrue(prompt.contains("只讨论电子游戏"));
        assertTrue(prompt.contains("五子棋规则"));
        assertFalse(input.has("session_id"));
    }

    /** 同账号同会话延续上下文，另一账号复用本地编号也无法获取前者上下文 */
    @Test
    void cloudSessionsBelongToTheirAuthenticatedAccount() throws Exception {
        respond(200, "{\"output\":{\"text\":\"游戏回答\",\"session_id\":\"private-session\"}}");
        AiService service = service("test-key");
        String id = UUID.randomUUID().toString();
        service.chat(1, id, "五子棋规则", "127.0.0.1");
        service.chat(1, id, "继续说明", "127.0.0.1");
        service.chat(2, id, "五子棋规则", "127.0.0.1");
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(3)).send(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        JsonNode second = requestBody(1, 3);
        JsonNode secondInput = second.path("input");
        assertEquals("private-session", secondInput.path("session_id").asText());
        JsonNode third = requestBody(2, 3);
        JsonNode thirdInput = third.path("input");
        assertFalse(thirdInput.has("session_id"));
    }

    /** 验证配置缺失和无效输入的拒绝结果，并检查状态字段 */
    @Test
    void rejectsInvalidInputBeforeSending() {
        AiService missing = service("");
        String id = UUID.randomUUID().toString();
        assertEquals(false, missing.status().get("configured"));
        expectError(missing, 1, id, "游戏", "密钥");
        AiService service = service("test-key");
        expectError(service, null, id, "游戏", "登录");
        expectError(service, 1, id, " ", "问题");
        expectError(service, 1, id, "游".repeat(AiService.QUESTION_LIMIT + 1), "问题");
        expectError(service, 1, "wrong-id", "游戏", "编号");
        assertFalse(service.status().containsValue("test-key"));
        verifyNoInteractions(client);
    }

    /** 鉴权错误和繁忙只返回简短提示，下一次请求仍能正常执行 */
    @Test
    void upstreamErrorsReleaseAccountAndHideRawResponse() throws Exception {
        AiService service = service("test-key");
        String id = UUID.randomUUID().toString();
        respond(401, "{\"message\":\"private cloud details\"}");
        expectError(service, 1, id, "游戏", "鉴权");
        respond(429, "{}");
        expectError(service, 1, id, "游戏", "较忙");
        respond(200, "{\"output\":{\"text\":\"恢复回答\"}}");
        assertEquals("恢复回答", service.chat(1, id, "游戏", "127.0.0.1").get("reply"));
    }

    /** 网络超时和空回答返回失败提示，随后请求可以重新提问 */
    @Test
    void timeoutAndMalformedAnswersRemainRetryable() throws Exception {
        AiService service = service("test-key");
        String id = UUID.randomUUID().toString();
        when(client.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new java.net.http.HttpTimeoutException("internal timeout"));
        expectError(service, 1, id, "游戏", "超时");
        reset(client);
        respond(200, "null");
        expectError(service, 1, id, "游戏", "有效回答");
        respond(200, "{\"output\":{\"text\":\"\"}}");
        expectError(service, 1, id, "游戏", "有效回答");
    }

    /** 限流针对登录账号，换一个本地对话编号无法绕过频率限制 */
    @Test
    void rateLimitAppliesAcrossConversations() throws Exception {
        respond(200, "{\"output\":{\"text\":\"游戏回答\"}}");
        AiService service = service("test-key");
        for (int count = 0; count < 8; count = count + 1) {
            service.chat(1, UUID.randomUUID().toString(), "游戏", "127.0.0.1");
        }
        expectError(service, 1, UUID.randomUUID().toString(), "游戏", "频繁");
        assertEquals("游戏回答", service.chat(2, UUID.randomUUID().toString(), "游戏", "127.0.0.1").get("reply"));
        verify(client, times(9)).send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    /** 数字字段和空对象在接口校验阶段返回格式提示 */
    @Test
    void controllerRejectsWrongBodyTypes() {
        AiService service = mock(AiService.class);
        AiController controller = new AiController(service);
        DataRequest request = new DataRequest();
        request.add("message", 123);
        request.add("conversationId", UUID.randomUUID().toString());
        assertEquals(1, controller.chat(request, new MockHttpServletRequest()).getCode());
        request.setData(null);
        assertEquals(1, controller.chat(request, new MockHttpServletRequest()).getCode());
        verifyNoInteractions(service);
    }
}
