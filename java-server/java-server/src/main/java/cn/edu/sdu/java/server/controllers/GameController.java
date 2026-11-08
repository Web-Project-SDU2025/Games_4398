package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.GameService;
import cn.edu.sdu.java.server.services.BlockcoinService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 游戏控制器
 *
 * 提供游戏、游戏库、收藏和游玩记录相关接口
 */
@RestController
@RequestMapping("/api/game")
public class GameController {
    private final GameService service;
    private final BlockcoinService store;

    /**
     * 依赖注入
     * 保存游戏业务服务，接口校验和事务由服务层处理
     */
    public GameController(GameService service, BlockcoinService store) {
        this.service = service;
        this.store = store;
    }

    /**
     * 查询游戏列表
     * 支持三种模式：全部游戏、游戏库、收藏
     */
    @PostMapping("/list")
    public DataResponse list(@RequestBody DataRequest request) {
        return service.list(request);
    }

    /**
     * 保存游戏资料
     * 只允许管理员调用，新增或修改游戏信息
     */
    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse save(@RequestBody DataRequest request) {
        return service.save(request);
    }

    /**
     * 删除游戏
     * 只允许管理员调用，会检查游戏是否被其他数据使用
     */
    @PostMapping("/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public DataResponse delete(@RequestBody DataRequest request) {
        return service.delete(request);
    }

    /**
     * 修改收藏或游戏库
     * 收藏可以自由添加删除，游戏库加入需要按购买关系校验
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
     * 管理员能看全部记录，普通用户只能看自己的
     */
    @PostMapping("/records")
    public DataResponse records() {
        return service.records();
    }

    /**
     * 保存游戏记录
     * 用户手动保存的游玩记录
     */
    @PostMapping("/recordSave")
    public DataResponse recordSave(@RequestBody DataRequest request) {
        return service.saveRecord(request);
    }

    /**
     * 隐藏游戏记录
     * 记录本身还在，只是列表里不显示
     */
    @PostMapping({"/recordHide", "/recordDelete"})
    public DataResponse recordHide(@RequestBody DataRequest request) {
        return service.hideRecord(request);
    }

    /**
     * 恢复游戏记录
     * 把之前隐藏的记录重新显示出来，统计数据保持完整
     */
    @PostMapping("/recordRestore")
    public DataResponse recordRestore(@RequestBody DataRequest request) {
        return service.restoreRecord(request);
    }
}
