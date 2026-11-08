package cn.edu.sdu.java.server.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面控制器
 *
 * 将明确的前端页面地址交给Vue
 * 接口和不存在的静态文件仍按原规则处理
 */
@Controller
public class PageController {
    /**
     * 页面转发路由
     * 直接访问或刷新页面时返回前端入口，页面内容由Vue路由决定
     */
    @GetMapping({
        "/Login", "/MainPage", "/Password",
        "/MenuManage", "/DictionaryManage", "/GameCatalog", "/GameLibrary",
        "/GameFavorites", "/GameRecords", "/GameUserProfile",
        "/GameUserManage", "/AccountManage", "/BlockcoinManage", "/RecordManage", "/AiSupport"
    })
    public String page() {
        return "forward:/index.html";
    }
}
