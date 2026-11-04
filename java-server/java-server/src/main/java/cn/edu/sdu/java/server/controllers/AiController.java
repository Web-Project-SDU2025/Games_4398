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

/** 客服页面和宠物共用登录后的百炼接口 */
@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasRole('ADMIN') or hasRole('GAME_USER')")
public class AiController {
    private final AiService service;

    /** 注入同一个服务，统一管理频率和云端会话 */
    public AiController(AiService service) {
        this.service = service;
    }

    /** 返回是否已配置百炼，不产生模型调用费用 */
    @PostMapping("/status")
    public DataResponse status() {
        return CommonMethod.getReturnData(service.status());
    }

    /** 只接受问题和本地对话编号，账号身份由登录凭证确定 */
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
