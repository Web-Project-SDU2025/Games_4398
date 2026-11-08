package cn.edu.sdu.java.server.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 消息页面控制器
 *
 * 刷新消息中心地址时返回Vue入口
 * 不参与消息接口或服务器连接配置
 */
@Controller
public class MessagePageController {
    /**
     * 消息页面路由
     * 页面数据由前端路由读取，后端只返回应用入口
     */
    @GetMapping("/Messages")
    public String messagesPage() {
        return "forward:/index.html";
    }
}
