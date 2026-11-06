package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.AdminBlockcoinService;
import cn.edu.sdu.java.server.services.BlockcoinService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 字块币管理入口统一要求管理员身份 */
@RestController
@RequestMapping("/api/admin/blockcoin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBlockcoinController {
    private final AdminBlockcoinService service;
    private final BlockcoinService coins;

    /** 控制器接收筛选参数，分页与账户校验由服务完成 */
    public AdminBlockcoinController(AdminBlockcoinService service, BlockcoinService coins) {
        this.service = service;
        this.coins = coins;
    }

    /** 查询全部账户或指定账户的收支记录与资金汇总 */
    @PostMapping("/list")
    public DataResponse list(@RequestBody DataRequest request) {
        return service.list(request);
    }

    /** 调整指定账户余额，目标账户、数量与原因均由业务服务校验 */
    @PostMapping("/adjust")
    public DataResponse adjust(@RequestBody DataRequest request) {
        return coins.adjust(request);
    }
}
