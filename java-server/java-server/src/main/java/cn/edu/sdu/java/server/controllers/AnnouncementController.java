package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.MessageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 全站公告通过独立管理员接口发布，阅读仍走当前账户消息接口 */
@RestController
@RequestMapping("/api/admin/announcements")
@PreAuthorize("hasRole('ADMIN')")
public class AnnouncementController {
    private final MessageService service;

    /** 发布权限在控制器和业务服务分别核对 */
    public AnnouncementController(MessageService service) {
        this.service = service;
    }

    /** 发布标题与正文，重试使用原操作编号 */
    @PostMapping("/publish")
    public DataResponse publish(@RequestBody DataRequest request) {
        return service.publish(request);
    }
}
