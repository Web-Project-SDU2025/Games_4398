package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.services.AiImageService;
import cn.edu.sdu.java.server.services.AiRequestGuard;
import cn.edu.sdu.java.server.util.AiImageRules;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实图片解码和网络替身检查多图协议与额度边界 */
class AiImageTests {
    private final ObjectMapper mapper = spy(new ObjectMapper());
    private final HttpClient client = mock(HttpClient.class);
    private final AiRequestGuard guard = mock(AiRequestGuard.class);
    private final AiImageService service = new AiImageService("test-key", "qwen3-vl-plus", mapper, client, guard);

    /** 生成尺寸可控的真实 PNG，测试不读取用户图片 */
    private String image(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }

    /** 准备不消耗真实额度的云端响应 */
    private void respond(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(response);
    }

    /** 两张图片属于同一个问题，追问复用原图片上下文，另一账户保持隔离 */
    @Test
    void multipleImagesShareOneGuardedConversation() throws Exception {
        respond(200, "{\"output\":{\"text\":\"两张游戏图片的回答\",\"session_id\":\"vision-session\"}}");
        String id = UUID.randomUUID().toString();
        String source = image(16, 16);
        Map<String, Object> result = service.chat(12, id, "比较两张游戏截图", List.of(source, source), "127.0.0.1");
        assertEquals("两张游戏图片的回答", result.get("reply"));
        assertFalse(result.containsKey("sessionId"));
        service.chat(12, id, "继续说明", List.of(), "127.0.0.1");
        service.chat(13, id, "游戏图片", List.of(source), "127.0.0.1");
        ArgumentCaptor<Object> bodies = ArgumentCaptor.forClass(Object.class);
        verify(mapper, times(3)).writeValueAsString(bodies.capture());
        JsonNode first = mapper.valueToTree(bodies.getAllValues().get(0));
        assertEquals(2, first.path("input").path("image_list").size());
        assertEquals("qwen3-vl-plus", first.path("parameters").path("model_id").asText());
        JsonNode second = mapper.valueToTree(bodies.getAllValues().get(1));
        assertEquals("vision-session", second.path("input").path("session_id").asText());
        JsonNode third = mapper.valueToTree(bodies.getAllValues().get(2));
        assertFalse(third.path("input").has("session_id"));
        verify(guard, times(2)).begin(12, "127.0.0.1");
        verify(guard, times(2)).finish(12);
    }

    /** 远程地址、错误格式、超额数量和超大尺寸在占用额度前返回 */
    @Test
    void invalidImagesAreRejectedBeforeQuota() throws Exception {
        String id = UUID.randomUUID().toString();
        String source = image(1, 1);
        List<List<?>> cases = List.of(
            List.of("http://127.0.0.1/private.png"),
            List.of("data:image/svg+xml;base64,AAAA"),
            List.of("data:image/png;base64,AAAA"),
            List.of(source, source, source, source, source),
            List.of(image(AiImageRules.MAX_EDGE + 1, 1))
        );
        for (List<?> images : cases) {
            try {
                service.chat(12, id, "游戏图片", images, "127.0.0.1");
                fail("非法图片应在调用模型前返回");
            } catch (IllegalArgumentException error) {
                assertNotNull(error.getMessage());
            }
        }
        verifyNoInteractions(client, guard);
    }

    /** 上游失败时释放并发名额，原始响应中的内部信息保持在后端 */
    @Test
    void upstreamFailureReleasesSharedGuard() throws Exception {
        respond(403, "{\"secret\":\"internal details\"}");
        try {
            service.chat(12, UUID.randomUUID().toString(), "", List.of(image(1, 1)), "127.0.0.1");
            fail("权限错误应返回图片调用失败");
        } catch (IllegalArgumentException error) {
            assertFalse(error.getMessage().contains("internal details"));
        }
        verify(guard).finish(12);
    }
}
