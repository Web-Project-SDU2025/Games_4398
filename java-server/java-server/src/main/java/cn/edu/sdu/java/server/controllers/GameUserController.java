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

    @PostMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse list(@RequestBody DataRequest request) {
        return service.getGameUserList(request);
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse create(@RequestBody DataRequest request) {
        return service.createAccount(request);
    }

    @PostMapping("/profile")
    public DataResponse profile() {
        return service.getMyProfile();
    }

    @PostMapping("/save")
    public DataResponse save(@RequestBody DataRequest request) {
        return service.saveProfile(request);
    }

    @PostMapping("/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse delete(@RequestBody DataRequest request) {
        return service.deleteAccount(request);
    }
}
