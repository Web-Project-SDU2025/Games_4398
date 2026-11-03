package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.GameUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** GameUserController 提供账户管理和个人资料接口。 */
@RestController
@RequestMapping("/api/gameUser")
public class GameUserController {
    private final GameUserService service;

    public GameUserController(GameUserService service) {
        this.service = service;
    }

    /**
     * 查询账户列表，只允许管理员调用。
     * @param request data 中可包含账号或昵称的搜索文字
     * @return 账户列表
     */
    @PostMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse list(@RequestBody DataRequest request) {
        return service.getGameUserList(request);
    }

    /**
     * 创建一个管理员或游戏用户账户，只允许管理员调用。
     * @param request data 中包含账号、密码、昵称和角色
     * @return 创建成功或失败的响应
     */
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse create(@RequestBody DataRequest request) {
        return service.createAccount(request);
    }

    /**
     * 查询当前登录账户的个人资料。
     * @return 当前账户资料
     */
    @PostMapping("/profile")
    public DataResponse profile() {
        return service.getMyProfile();
    }

    /**
     * 保存个人资料，普通用户只能修改自己的资料。
     * @param request data 中包含昵称、邮箱、电话和签名
     * @return 保存成功或失败的响应
     */
    @PostMapping("/save")
    public DataResponse save(@RequestBody DataRequest request) {
        return service.saveProfile(request);
    }

    /**
     * 删除账号，只允许管理员调用，系统 admin 受后端保护
     * @param request data 中包含 userId
     * @return 删除成功或失败的响应
     */
    @PostMapping("/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse delete(@RequestBody DataRequest request) {
        return service.deleteAccount(request);
    }
    /** 管理员专用的账户资料和角色保存接口，普通资料接口不接受角色修改 */
    @PostMapping("/adminSave")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse adminSave(@RequestBody DataRequest request) {
        return service.saveAccount(request);
    }
}
