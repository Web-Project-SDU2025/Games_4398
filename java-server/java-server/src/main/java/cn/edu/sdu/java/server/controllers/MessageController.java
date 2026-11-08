package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.MessageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息控制器
 *
 * 提供站内消息、通知、公告和反馈相关接口
 * 接收范围由用户身份和消息类型决定
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService service;

    /**
     * 依赖注入
     * 消息读取和已读回执共用同一个服务
     */
    public MessageController(MessageService service) {
        this.service = service;
    }

    /**
     * 分页查询消息列表
     * 返回当前用户的通知和公告
     */
    @PostMapping("/list")
    public DataResponse list(@RequestBody DataRequest request) {
        return service.list(request);
    }

    /**
     * 查询未读数量
     * 返回当前用户的未读消息数
     */
    @PostMapping("/unread")
    public DataResponse unread() {
        return service.unread();
    }

    /**
     * 标记消息已读
     * 将一条可见消息标记为已读
     */
    @PostMapping("/read")
    public DataResponse read(@RequestBody DataRequest request) {
        return service.read(request);
    }

    /**
     * 全部标记已读
     * 将操作前已收到的所有消息标记为已读
     */
    @PostMapping("/readAll")
    public DataResponse readAll() {
        return service.readAll();
    }

    /**
     * 提交问题反馈
     * 用户提交反馈给管理员
     */
    @PostMapping("/feedback")
    public DataResponse feedback(@RequestBody DataRequest request) {
        return service.feedback(request);
    }

    /**
     * 编辑消息
     * 修改当前用户可编辑的公告或反馈
     */
    @PostMapping("/edit")
    public DataResponse edit(@RequestBody DataRequest request) {
        return service.edit(request);
    }

    /**
     * 回复反馈
     * 管理员设置处理决定并发送回复
     */
    @PostMapping("/respond")
    public DataResponse respond(@RequestBody DataRequest request) {
        return service.respondFeedback(request);
    }
}
