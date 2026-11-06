package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.GameService;
import cn.edu.sdu.java.server.services.BlockcoinService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** GameController 提供游戏、游戏库、收藏和游玩记录接口 */
@RestController
@RequestMapping("/api/game")
public class GameController {
    private final GameService service;
    private final BlockcoinService store;

    /** 保存游戏业务服务，接口校验与事务由服务处理 */
    public GameController(GameService service, BlockcoinService store) {
        this.service = service;
        this.store = store;
    }

    /**
     * 查询游戏列表、当前用户的游戏库或当前用户的收藏
     * @param request data 中可包含 mode 和 search
     * @return 游戏列表
     */
    @PostMapping("/list")
    public DataResponse list(@RequestBody DataRequest request) {
        return service.list(request);
    }

    /**
     * 保存游戏资料，只允许管理员调用
     * @param request data 中包含游戏编号、名称和其他游戏资料
     * @return 保存成功或失败的响应
     */
    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse save(@RequestBody DataRequest request) {
        return service.save(request);
    }

    /**
     * 删除一个没有被其他数据使用的游戏，只允许管理员调用
     * @param request data 中包含 gameId
     * @return 删除成功或失败的响应
     */
    @PostMapping("/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse delete(@RequestBody DataRequest request) {
        return service.delete(request);
    }

    /**
     * 更新当前用户的收藏，游戏库加入请求按原购买关系校验
     * @param request data 中包含 gameId、mode 和 added
     * @return 操作成功或失败的响应
     */
    @PostMapping("/collection")
    public DataResponse collection(@RequestBody DataRequest request) {
        if ("library".equals(request.getString("mode")) && request.getBoolean("added")) {
            return store.restoreLibrary(request);
        }
        return service.changeCollection(request);
    }

    /**
     * 查询游戏记录
     * @return 管理员可以看到全部记录，游戏用户只能看到自己的记录
     */
    @PostMapping("/records")
    public DataResponse records() {
        return service.records();
    }

    /**
     * 保存当前用户的一次游玩记录
     * @param request data 中包含 gameId、score 和 durationSeconds
     * @return 保存成功或失败的响应
     */
    @PostMapping("/recordSave")
    public DataResponse recordSave(@RequestBody DataRequest request) {
        return service.saveRecord(request);
    }

    /** 隐藏当前账户看到的记录，旧地址也进入同一隐藏流程 */
    @PostMapping({"/recordHide", "/recordDelete"})
    public DataResponse recordHide(@RequestBody DataRequest request) {
        return service.hideRecord(request);
    }

    /** 恢复当前查看账户隐藏的记录，原统计资料继续保持完整 */
    @PostMapping("/recordRestore")
    public DataResponse recordRestore(@RequestBody DataRequest request) {
        return service.restoreRecord(request);
    }
}
