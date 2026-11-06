package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.AiImageService;
import cn.edu.sdu.java.server.util.CommonMethod;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 图片会话使用独立入口，原文本接口和服务器连接代码保留原样 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'GAME_USER')")
public class AiImageController {
    private final AiImageService service;

    /** 控制器只负责参数形状与身份，图片解码和额度交给服务处理 */
    public AiImageController(AiImageService service) {
        this.service = service;
    }

    /** 接受同一问题的多张内存图片，远程图片地址由服务拒绝 */
    @PostMapping("/api/ai/image-chat")
    public DataResponse chat(@RequestBody DataRequest request, HttpServletRequest http) {
        try {
            if (request == null || request.getData() == null || request.getData().size() != 3
                    || !(request.get("message") instanceof String)
                    || !(request.get("conversationId") instanceof String)
                    || !(request.get("images") instanceof java.util.List<?>)) {
                return CommonMethod.getReturnMessageError("图片问题格式不正确");
            }
            return CommonMethod.getReturnData(service.chat(CommonMethod.getUserId(),
                request.getString("conversationId"), request.getString("message"),
                request.getList("images"), http.getRemoteAddr()));
        } catch (IllegalArgumentException error) {
            return CommonMethod.getReturnMessageError(error.getMessage());
        }
    }
}
