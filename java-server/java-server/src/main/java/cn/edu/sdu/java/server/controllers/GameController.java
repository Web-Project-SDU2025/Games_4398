package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.GameService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** GameController 提供游戏、游戏库、收藏和游玩记录接口。 */
@RestController
@RequestMapping("/api/game")
public class GameController {
    private final GameService service;

    public GameController(GameService service) {
        this.service = service;
    }

    @PostMapping("/list")
    public DataResponse list(@RequestBody DataRequest request) {
        return service.list(request);
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse save(@RequestBody DataRequest request) {
        return service.save(request);
    }

    @PostMapping("/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse delete(@RequestBody DataRequest request) {
        return service.delete(request);
    }

    @PostMapping("/collection")
    public DataResponse collection(@RequestBody DataRequest request) {
        return service.changeCollection(request);
    }

    @PostMapping("/records")
    public DataResponse records() {
        return service.records();
    }

    @PostMapping("/recordSave")
    public DataResponse recordSave(@RequestBody DataRequest request) {
        return service.saveRecord(request);
    }

    @PostMapping("/recordDelete")
    public DataResponse recordDelete(@RequestBody DataRequest request) {
        return service.deleteRecord(request);
    }
}
