package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.AiService;
import cn.edu.sdu.java.server.util.CommonMethod;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.servlet.http.HttpServletRequest;

/**
 * AI助手控制器
 *
 * 提供百炼AI对话接口，支持客服页面和宠物功能
 * 需要登录后才能使用
 */
@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasRole('ADMIN') or hasRole('GAME_USER')")
public class AiController {
    private final AiService service;

    /**
     * 依赖注入
     * 注入AI服务，统一管理频率限制和云端会话
     */
    public AiController(AiService service) {
        this.service = service;
    }

    /**
     * 查询AI状态
     * 返回是否已配置百炼密钥，不产生模型调用费用
     */
    @PostMapping("/status")
    public DataResponse status() {
        return CommonMethod.getReturnData(service.status());
    }

    /**
     * AI对话接口
     * 只接受问题和本地对话ID，用户身份由登录凭证确定
     */
    @PostMapping("/chat")
    public DataResponse chat(@RequestBody DataRequest request, HttpServletRequest httpRequest) {
        try {
            if (request == null || request.getData() == null || request.getData().size() != 2
                    || !(request.get("message") instanceof String)
                    || !(request.get("conversationId") instanceof String)) {
                return CommonMethod.getReturnMessageError("问题或对话编号格式不正确");
            }
            Integer userId = CommonMethod.getUserId();
            return CommonMethod.getReturnData(service.chat(userId,
                    request.getString("conversationId"), request.getString("message"), httpRequest.getRemoteAddr()));
        } catch (IllegalArgumentException error) {
            return CommonMethod.getReturnMessageError(error.getMessage());
        }
    }
}
