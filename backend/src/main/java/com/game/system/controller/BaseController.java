package com.game.system.controller;

import com.game.system.payload.request.DataRequest;
import com.game.system.payload.response.DataResponse;
import com.game.system.util.CommonMethod;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 基础控制器
 * 提供通用的控制器方法
 */
@RestController
@RequestMapping("/base")
public class BaseController {

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public DataResponse health() {
        return CommonMethod.getReturnMessageOK("服务运行正常");
    }

    /**
     * 测试接口
     */
    @PostMapping("/test")
    public DataResponse test(@RequestBody DataRequest dataRequest) {
        Map<String, Object> data = dataRequest.getData();
        String message = CommonMethod.getString(data, "message");
        return CommonMethod.getReturnData("收到消息: " + message, "测试成功");
    }

    /**
     * 获取服务器时间
     */
    @GetMapping("/serverTime")
    public DataResponse getServerTime() {
        return CommonMethod.getReturnData(System.currentTimeMillis(), "获取成功");
    }
}
