package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.controllers.AiController;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.services.AiService;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用网络替身检查百炼协议、失败分支和账号隔离，不访问数据库 */
class AiTests {
    private final ObjectMapper mapper = spy(new ObjectMapper());
    private final HttpClient client = mock(HttpClient.class);

    /** 为下一次调用准备确定的云端响应 */
    private void respond(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
    }

    /** 检查交给 JSON 编码器的请求参数，避免为测试额外编写网络数据订阅器 */
    private JsonNode requestBody(int index, int count) throws Exception {
        ArgumentCaptor<Object> bodies = ArgumentCaptor.forClass(Object.class);
        verify(mapper, times(count)).writeValueAsString(bodies.capture());
        List<Object> values = bodies.getAllValues();
        return mapper.valueToTree(values.get(index));
    }

    /** 验证错误提示，同时避免为测试写匿名回调 */
    private void expectError(AiService service, Integer userId, String id, String message, String expected) {
        try {
            service.chat(userId, id, message);
            fail("此请求应当被拒绝");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains(expected));
        }
    }

    /** 固定应用 ID、游戏提示词和 Bearer 密钥都放在正确位置 */
    @Test
    void usesReferenceApplicationAndGameInstructions() throws Exception {
        respond(200, "{\"output\":{\"text\":\"五子连线获胜\",\"session_id\":\"cloud-session\"}}");
        AiService service = new AiService("test-key", mapper, client);
        Map<String, Object> result = service.chat(1, UUID.randomUUID().toString(), "五子棋规则");
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
        AiService service = new AiService("test-key", mapper, client);
        String id = UUID.randomUUID().toString();
        service.chat(1, id, "五子棋规则");
        service.chat(1, id, "继续说明");
        service.chat(2, id, "五子棋规则");
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(3)).send(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        JsonNode second = requestBody(1, 3);
        JsonNode secondInput = second.path("input");
        assertEquals("private-session", secondInput.path("session_id").asText());
        JsonNode third = requestBody(2, 3);
        JsonNode thirdInput = third.path("input");
        assertFalse(thirdInput.has("session_id"));
    }

    /** 未配置和输入无效时不调用模型，也不把密钥放入状态信息 */
    @Test
    void rejectsInvalidInputBeforeSending() {
        AiService missing = new AiService("", mapper, client);
        String id = UUID.randomUUID().toString();
        assertEquals(false, missing.status().get("configured"));
        expectError(missing, 1, id, "游戏", "密钥");
        AiService service = new AiService("test-key", mapper, client);
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
        AiService service = new AiService("test-key", mapper, client);
        String id = UUID.randomUUID().toString();
        respond(401, "{\"message\":\"private cloud details\"}");
        expectError(service, 1, id, "游戏", "鉴权");
        respond(429, "{}");
        expectError(service, 1, id, "游戏", "较忙");
        respond(200, "{\"output\":{\"text\":\"恢复回答\"}}");
        assertEquals("恢复回答", service.chat(1, id, "游戏").get("reply"));
    }

    /** 网络超时和空回答不会成为成功消息，发生异常后可以重新提问 */
    @Test
    void timeoutAndMalformedAnswersRemainRetryable() throws Exception {
        AiService service = new AiService("test-key", mapper, client);
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
        AiService service = new AiService("test-key", mapper, client);
        for (int count = 0; count < 8; count = count + 1) {
            service.chat(1, UUID.randomUUID().toString(), "游戏");
        }
        expectError(service, 1, UUID.randomUUID().toString(), "游戏", "频繁");
        assertEquals("游戏回答", service.chat(2, UUID.randomUUID().toString(), "游戏").get("reply"));
        verify(client, times(9)).send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    /** 接口拒绝数字或空对象，不把错误输入直接转换成模型问题 */
    @Test
    void controllerRejectsWrongBodyTypes() {
        AiService service = mock(AiService.class);
        AiController controller = new AiController(service);
        DataRequest request = new DataRequest();
        request.add("message", 123);
        request.add("conversationId", UUID.randomUUID().toString());
        assertEquals(1, controller.chat(request).getCode());
        request.setData(null);
        assertEquals(1, controller.chat(request).getCode());
        verifyNoInteractions(service);
    }
}
