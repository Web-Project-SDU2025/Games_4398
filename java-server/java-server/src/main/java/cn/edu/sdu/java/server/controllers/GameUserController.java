package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.GameUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 用户账户控制器
 *
 * 提供账户管理和个人资料相关接口
 */
@RestController
@RequestMapping("/api/gameUser")
public class GameUserController {
    private final GameUserService service;

    /**
     * 依赖注入
     * 保存账户业务服务，权限和资料处理由服务层负责
     */
    public GameUserController(GameUserService service) {
        this.service = service;
    }

    /**
     * 查询账户列表
     * 只允许管理员调用，可按账号或昵称搜索
     */
    @PostMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse list(@RequestBody DataRequest request) {
        return service.getGameUserList(request);
    }

    /**
     * 创建账户
     * 只允许管理员调用，可创建管理员或普通用户
     */
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse create(@RequestBody DataRequest request) {
        return service.createAccount(request);
    }

    /**
     * 查询个人资料
     * 返回当前登录用户的个人资料
     */
    @PostMapping("/profile")
    public DataResponse profile() {
        return service.getMyProfile();
    }

    /**
     * 保存个人资料
     * 普通用户只能修改自己的资料
     */
    @PostMapping("/save")
    public DataResponse save(@RequestBody DataRequest request) {
        return service.saveProfile(request);
    }

    /**
     * 删除账户
     * 只允许管理员调用，系统admin账户受后端保护
     */
    @PostMapping("/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse delete(@RequestBody DataRequest request) {
        return service.deleteAccount(request);
    }
    /**
     * 管理员保存账户
     * 管理员专用接口，可修改账户资料和角色
     */
    @PostMapping("/adminSave")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse adminSave(@RequestBody DataRequest request) {
        return service.saveAccount(request);
    }
}
