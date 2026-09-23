package com.game.system.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 游戏控制器
 * 提供游戏相关的接口：游戏列表、游戏详情、游戏搜索等
 */
@RestController
@RequestMapping("/api/game")
public class GameController {

    // TODO: [数据库连接] 注入 GameService，用于处理游戏业务逻辑
    // 步骤：
    // 1. 创建 GameService 接口和实现类
    // 2. 在 GameService 中注入 GameRepository
    // 3. 取消下面代码的注释
    //
    // private final GameService gameService;
    //
    // public GameController(GameService gameService) {
    //     this.gameService = gameService;
    // }

    // TODO: [数据库连接] 实现获取游戏列表接口（分页）
    // @GetMapping("/list")
    // public DataResponse getGameList(@RequestParam(defaultValue = "0") int page,
    //                                  @RequestParam(defaultValue = "10") int size) {
    //     // 调用 gameService.getGameList(page, size)
    //     // 返回分页的游戏列表
    // }

    // TODO: [数据库连接] 实现根据ID获取游戏详情接口
    // @GetMapping("/{id}")
    // public DataResponse getGameById(@PathVariable Long id) {
    //     // 调用 gameService.getGameById(id)
    //     // 返回游戏详细信息
    // }

    // TODO: [数据库连接] 实现搜索游戏接口
    // @GetMapping("/search")
    // public DataResponse searchGames(@RequestParam String keyword) {
    //     // 调用 gameService.searchGames(keyword)
    //     // 返回搜索结果
    // }

    // TODO: [数据库连接] 实现根据分类获取游戏接口
    // @GetMapping("/category/{category}")
    // public DataResponse getGamesByCategory(@PathVariable String category) {
    //     // 调用 gameService.getGamesByCategory(category)
    //     // 返回该分类下的所有游戏
    // }

    // TODO: [数据库连接] 实现添加游戏接口（管理员功能）
    // @PostMapping("/add")
    // public DataResponse addGame(@RequestBody DataRequest request) {
    //     // 检查当前用户是否是管理员
    //     // 调用 gameService.addGame(gameInfo)
    //     // 返回添加结果
    // }

    // TODO: [数据库连接] 实现更新游戏信息接口（管理员功能）
    // @PutMapping("/update/{id}")
    // public DataResponse updateGame(@PathVariable Long id, @RequestBody DataRequest request) {
    //     // 检查当前用户是否是管理员
    //     // 调用 gameService.updateGame(id, gameInfo)
    //     // 返回更新结果
    // }

    // TODO: [数据库连接] 实现删除游戏接口（管理员功能）
    // @DeleteMapping("/delete/{id}")
    // public DataResponse deleteGame(@PathVariable Long id) {
    //     // 检查当前用户是否是管理员
    //     // 调用 gameService.deleteGame(id)
    //     // 返回删除结果
    // }
}
