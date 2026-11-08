package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.GameplayService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 游戏会话控制器
 *
 * 提供游戏会话的开始、心跳、结束和台球奖励接口
 * 所有游戏通过统一的会话机制上报时长和结果
 */
@RestController
@RequestMapping("/api/gameplay")
@PreAuthorize("hasAnyRole('ADMIN', 'GAME_USER')")
public class GameplayController {
    private final GameplayService service;
    private final cn.edu.sdu.java.server.services.BilliardsRewardService billiards;

    /**
     * 依赖注入
     * 接口只接收事件，账户校验、计时和奖励交由服务层处理
     */
    public GameplayController(GameplayService service,
            cn.edu.sdu.java.server.services.BilliardsRewardService billiards) {
        this.service = service;
        this.billiards = billiards;
    }

    /**
     * 开始游戏会话
     * 用游戏路径、关卡编号和文件哈希创建会话
     */
    @PostMapping("/begin")
    public DataResponse begin(@RequestBody DataRequest request) {
        return service.begin(request);
    }

    /**
     * 心跳同步
     * 保存本局累计活跃时长（毫秒）
     */
    @PostMapping("/heartbeat")
    public DataResponse heartbeat(@RequestBody DataRequest request) {
        return service.heartbeat(request);
    }

    /**
     * 结束游戏会话
     * 保存通过、失败或离开结果，并结算符合规则的通关奖励
     */
    @PostMapping("/finish")
    public DataResponse finish(@RequestBody DataRequest request) {
        return service.finish(request);
    }

    /**
     * 台球击球上报
     * 上报本盘击球证据，账户、轮次、去重和奖励金额由服务端核定
     */
    @PostMapping("/billiards")
    public DataResponse billiards(@RequestBody DataRequest request) throws Exception {
        return billiards.report(request);
    }
}
