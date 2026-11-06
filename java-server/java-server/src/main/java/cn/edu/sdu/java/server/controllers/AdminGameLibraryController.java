package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.AdminGameLibraryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员按目标账户查看或撤回游戏，普通账户继续使用原个人游戏库接口 */
@RestController
@RequestMapping("/api/admin/library")
@PreAuthorize("hasRole('ADMIN')")
public class AdminGameLibraryController {
    private final AdminGameLibraryService service;

    /** 控制器保留权限边界，归属判断与事务由服务完成 */
    public AdminGameLibraryController(AdminGameLibraryService service) {
        this.service = service;
    }

    /** 读取指定账户当前持有的全部游戏 */
    @PostMapping("/list")
    public DataResponse list(@RequestBody DataRequest request) {
        return service.list(request);
    }

    /** 撤回游戏并保存管理员、原因与时间，资金账本保持原值 */
    @PostMapping("/revoke")
    public DataResponse revoke(@RequestBody DataRequest request) {
        return service.revoke(request);
    }
}
