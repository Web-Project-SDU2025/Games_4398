package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.AdminRecordService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 分数和记录管理接口统一验证管理员身份，权限由后端方法规则检查 */
@RestController
@RequestMapping("/api/admin/records")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRecordController {
    private final AdminRecordService service;

    /** 保存管理服务，接口只负责接收参数 */
    public AdminRecordController(AdminRecordService service) {
        this.service = service;
    }

    /** 查询全部用户的记录，可按账号或游戏名搜索 */
    @PostMapping("/list")
    public DataResponse list(@RequestBody DataRequest request) {
        return service.list(request);
    }

    /** 新增或修改指定用户的游戏记录 */
    @PostMapping("/save")
    public DataResponse save(@RequestBody DataRequest request) {
        return service.save(request);
    }

    /** 隐藏管理员当前视图中的记录，旧地址沿用同一处理 */
    @PostMapping({"/hide", "/delete"})
    public DataResponse hide(@RequestBody DataRequest request) {
        return service.hide(request);
    }

    /** 恢复当前管理员隐藏的记录，其他账户的展示单独维护 */
    @PostMapping("/restore")
    public DataResponse restore(@RequestBody DataRequest request) {
        return service.restore(request);
    }
}
