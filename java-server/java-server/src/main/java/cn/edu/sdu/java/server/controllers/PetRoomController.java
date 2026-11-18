package cn.edu.sdu.java.server.controllers;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.services.PetRoomService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 小屋接口只操作已登录账户自己的宠物 */
@RestController
@RequestMapping("/api/pet")
@PreAuthorize("hasAnyRole('ADMIN', 'GAME_USER')")
public class PetRoomController {
    private final PetRoomService pets;

    /** 交由宠物房服务统一处理库存和养成规则 */
    public PetRoomController(PetRoomService pets) {
        this.pets = pets;
    }

    /** 返回小屋当前状态 */
    @PostMapping("/room")
    public DataResponse read() {
        return pets.readRoom();
    }

    /** 用当前登录身份保存宠物昵称，不接受其他账户编号 */
    @PostMapping("/rename")
    public DataResponse rename(@RequestBody DataRequest request) {
        return pets.rename(request.getString("nickname"));
    }

    /** 为浮动桌宠读取当前状态，不创建存档或修改余额 */
    @PostMapping("/mood")
    public DataResponse mood() {
        return pets.readMood();
    }

    /** 返回成长排行榜和当前账户的名次 */
    @PostMapping("/leaderboard")
    public DataResponse leaderboard() {
        return pets.leaderboard();
    }

    /** 按订单号购买一份猫粮，金额由服务端确定 */
    @PostMapping("/buy")
    public DataResponse buy(@RequestBody DataRequest request) {
        return pets.buyFood(request.getString("requestId"));
    }

    /** 离开小屋时汇总已支付的猫粮订单，只发消息不重复扣款 */
    @PostMapping("/settle")
    public DataResponse settle() {
        return pets.settlePurchases();
    }

    /** 向当前宠物执行指定的照顾动作 */
    @PostMapping("/interact")
    public DataResponse interact(@RequestBody DataRequest request) {
        return pets.interact(request.getString("action"));
    }
}
