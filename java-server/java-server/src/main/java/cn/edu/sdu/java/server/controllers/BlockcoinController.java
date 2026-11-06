package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.BlockcoinService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商城和记账入口使用当前认证账户，客户端只提交游戏与本局标识 */
@RestController
@RequestMapping("/api/store")
@PreAuthorize("hasAnyRole('ADMIN', 'GAME_USER')")
public class BlockcoinController {
    private final BlockcoinService service;

    /** 业务校验与事务统一交给资金服务 */
    public BlockcoinController(BlockcoinService service) {
        this.service = service;
    }

    /** 读取商城、余额与最近收支 */
    @PostMapping("/catalog")
    public DataResponse catalog() {
        return service.catalog();
    }

    /** 明确购买后由后端按商品规则扣款 */
    @PostMapping("/purchase")
    public DataResponse purchase(@RequestBody DataRequest request) {
        return service.purchase(request);
    }

    /** 在游戏运行前核对当前账户归属 */
    @PostMapping("/authorize")
    public DataResponse authorize(@RequestBody DataRequest request) {
        return service.authorize(request);
    }

}
