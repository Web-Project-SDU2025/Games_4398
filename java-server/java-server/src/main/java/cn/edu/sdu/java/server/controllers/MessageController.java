package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.MessageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录账户使用个人消息接口，接收范围由服务端认证身份决定 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService service;

    /** 消息读取与已读回执共用同一业务服务 */
    public MessageController(MessageService service) {
        this.service = service;
    }

    /** 分页读取当前账户的通知和公告 */
    @PostMapping("/list")
    public DataResponse list(@RequestBody DataRequest request) {
        return service.list(request);
    }

    /** 返回当前账户未读数量 */
    @PostMapping("/unread")
    public DataResponse unread() {
        return service.unread();
    }

    /** 标记一条可见消息为已读 */
    @PostMapping("/read")
    public DataResponse read(@RequestBody DataRequest request) {
        return service.read(request);
    }

    /** 标记操作开始前已收到的消息为已读 */
    @PostMapping("/readAll")
    public DataResponse readAll() {
        return service.readAll();
    }

    /** 接收当前登录账户提交的问题反馈 */
    @PostMapping("/feedback")
    public DataResponse feedback(@RequestBody DataRequest request) {
        return service.feedback(request);
    }

    /** 修改当前账户可编辑的公告或反馈 */
    @PostMapping("/edit")
    public DataResponse edit(@RequestBody DataRequest request) {
        return service.edit(request);
    }

    /** 管理员设置反馈处理决定并发送回复 */
    @PostMapping("/respond")
    public DataResponse respond(@RequestBody DataRequest request) {
        return service.respondFeedback(request);
    }
}
