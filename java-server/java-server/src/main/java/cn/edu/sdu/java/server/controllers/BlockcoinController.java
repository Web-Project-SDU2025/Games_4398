package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.BlockcoinService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 游戏商城和字块币相关接口
 *
 * 所有接口自动使用当前登录账户，客户端只需要告诉我们要操作哪个游戏
 * 这样设计是为了防止前端伪造其他人的账户ID进行操作
 */
@RestController
@RequestMapping("/api/store")
@PreAuthorize("hasAnyRole('ADMIN', 'GAME_USER')")
public class BlockcoinController {
    private final BlockcoinService service;

    /**
     * 注入字块币服务
     * 把复杂的业务逻辑和事务管理都交给Service层处理，Controller只负责接收请求
     */
    public BlockcoinController(BlockcoinService service) {
        this.service = service;
    }

    /**
     * 获取商城商品列表
     * 返回所有游戏、当前余额和最近的收支记录，前端用这些数据渲染商城页面
     */
    @PostMapping("/catalog")
    public DataResponse catalog() {
        return service.catalog();
    }

    /**
     * 购买游戏
     * 用户点击购买按钮后会调用这个接口，后端会检查余额、扣款并加入游戏库
     * 价格完全由后端控制，防止客户端传假价格过来
     */
    @PostMapping("/purchase")
    public DataResponse purchase(@RequestBody DataRequest request) {
        return service.purchase(request);
    }

    /**
     * 游戏启动前的归属验证
     * 玩家要启动游戏时先调这个接口，确认当前账户确实拥有这个游戏
     * 编辑器模式会额外检查管理员身份
     */
    @PostMapping("/authorize")
    public DataResponse authorize(@RequestBody DataRequest request) {
        return service.authorize(request);
    }

}
