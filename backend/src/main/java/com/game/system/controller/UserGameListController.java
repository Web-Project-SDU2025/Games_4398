package com.game.system.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户游戏清单控制器
 * 提供用户游戏清单相关的接口：添加游戏、查看清单、更新游戏状态等
 */
@RestController
@RequestMapping("/api/user-game")
public class UserGameListController {

    // TODO: [数据库连接] 注入 UserGameListService，用于处理用户游戏清单业务逻辑
    // 步骤：
    // 1. 创建 UserGameListService 接口和实现类
    // 2. 在 UserGameListService 中注入 UserGameListRepository
    // 3. 取消下面代码的注释
    //
    // private final UserGameListService userGameListService;
    //
    // public UserGameListController(UserGameListService userGameListService) {
    //     this.userGameListService = userGameListService;
    // }

    // TODO: [数据库连接] 实现获取当前用户的游戏清单接口
    // @GetMapping("/list")
    // public DataResponse getMyGameList() {
    //     // 从 SecurityContext 获取当前用户ID
    //     // 调用 userGameListService.getUserGameList(userId)
    //     // 返回用户的游戏清单
    // }

    // TODO: [数据库连接] 实现添加游戏到清单接口
    // @PostMapping("/add")
    // public DataResponse addGameToList(@RequestBody DataRequest request) {
    //     Map<String, Object> data = request.getData();
    //     Long gameId = CommonMethod.getLong(data, "gameId");
    //     Integer status = CommonMethod.getInteger(data, "status"); // 1-收藏 2-正在玩 3-已完成
    //
    //     // 从 SecurityContext 获取当前用户ID
    //     // 调用 userGameListService.addGameToList(userId, gameId, status)
    //     // 返回添加结果
    // }

    // TODO: [数据库连接] 实现更新游戏状态接口
    // @PutMapping("/update-status")
    // public DataResponse updateGameStatus(@RequestBody DataRequest request) {
    //     Map<String, Object> data = request.getData();
    //     Long gameId = CommonMethod.getLong(data, "gameId");
    //     Integer status = CommonMethod.getInteger(data, "status");
    //
    //     // 从 SecurityContext 获取当前用户ID
    //     // 调用 userGameListService.updateGameStatus(userId, gameId, status)
    //     // 返回更新结果
    // }

    // TODO: [数据库连接] 实现从清单中移除游戏接口
    // @DeleteMapping("/remove/{gameId}")
    // public DataResponse removeGameFromList(@PathVariable Long gameId) {
    //     // 从 SecurityContext 获取当前用户ID
    //     // 调用 userGameListService.removeGameFromList(userId, gameId)
    //     // 返回移除结果
    // }

    // TODO: [数据库连接] 实现更新游戏时长接口
    // @PutMapping("/update-playtime")
    // public DataResponse updatePlayTime(@RequestBody DataRequest request) {
    //     Map<String, Object> data = request.getData();
    //     Long gameId = CommonMethod.getLong(data, "gameId");
    //     Integer playTime = CommonMethod.getInteger(data, "playTime"); // 游戏时长（分钟）
    //
    //     // 从 SecurityContext 获取当前用户ID
    //     // 调用 userGameListService.updatePlayTime(userId, gameId, playTime)
    //     // 返回更新结果
    // }

    // TODO: [数据库连接] 实现根据状态筛选游戏清单接口
    // @GetMapping("/list-by-status")
    // public DataResponse getGameListByStatus(@RequestParam Integer status) {
    //     // 从 SecurityContext 获取当前用户ID
    //     // 调用 userGameListService.getGameListByStatus(userId, status)
    //     // 返回筛选后的游戏清单
    // }
}
