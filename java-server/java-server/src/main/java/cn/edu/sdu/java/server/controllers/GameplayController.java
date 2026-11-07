package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.GameplayService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 游戏统一通过会话接口上报开局、实际时长与结果 */
@RestController
@RequestMapping("/api/gameplay")
@PreAuthorize("hasAnyRole('ADMIN', 'GAME_USER')")
public class GameplayController {
    private final GameplayService service;
    private final cn.edu.sdu.java.server.services.BilliardsRewardService billiards;

    /** 接口只接收事件，账户校验、计时和奖励交由同一业务服务 */
    public GameplayController(GameplayService service,
            cn.edu.sdu.java.server.services.BilliardsRewardService billiards) {
        this.service = service;
        this.billiards = billiards;
    }

    /** 以游戏入口、关卡编号和文件摘要创建会话 */
    @PostMapping("/begin")
    public DataResponse begin(@RequestBody DataRequest request) {
        return service.begin(request);
    }

    /** 保存本局累计有效毫秒数 */
    @PostMapping("/heartbeat")
    public DataResponse heartbeat(@RequestBody DataRequest request) {
        return service.heartbeat(request);
    }

    /** 保存通过、失败或离开结果，并结算符合规则的通关奖励 */
    @PostMapping("/finish")
    public DataResponse finish(@RequestBody DataRequest request) {
        return service.finish(request);
    }

    /** 上报本盘击球证据，账户、轮次、去重与奖励金额由服务端核定 */
    @PostMapping("/billiards")
    public DataResponse billiards(@RequestBody DataRequest request) throws Exception {
        return billiards.report(request);
    }
}
