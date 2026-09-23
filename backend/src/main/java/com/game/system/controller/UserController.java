package com.game.system.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器
 * 提供用户相关的接口：注册、登录、个人信息管理等
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    // TODO: [数据库连接] 注入 UserService，用于处理用户业务逻辑
    // 步骤：
    // 1. 创建 UserService 接口和实现类
    // 2. 在 UserService 中注入 UserRepository
    // 3. 取消下面代码的注释
    //
    // private final UserService userService;
    //
    // public UserController(UserService userService) {
    //     this.userService = userService;
    // }

    // TODO: [数据库连接] 实现用户注册接口
    // @PostMapping("/register")
    // public DataResponse register(@RequestBody DataRequest request) {
    //     Map<String, Object> data = request.getData();
    //     String username = CommonMethod.getString(data, "username");
    //     String password = CommonMethod.getString(data, "password");
    //     String email = CommonMethod.getString(data, "email");
    //
    //     // 调用 userService.register(username, password, email)
    //     // 返回注册结果
    // }

    // TODO: [数据库连接] 实现用户登录接口
    // @PostMapping("/login")
    // public DataResponse login(@RequestBody DataRequest request) {
    //     Map<String, Object> data = request.getData();
    //     String username = CommonMethod.getString(data, "username");
    //     String password = CommonMethod.getString(data, "password");
    //
    //     // 调用 userService.login(username, password)
    //     // 返回 JWT Token
    // }

    // TODO: [数据库连接] 实现获取当前用户信息接口
    // @GetMapping("/info")
    // public DataResponse getUserInfo() {
    //     // 从 SecurityContext 获取当前用户
    //     // 调用 userService.getUserInfo(userId)
    //     // 返回用户信息
    // }

    // TODO: [数据库连接] 实现修改用户信息接口
    // @PutMapping("/update")
    // public DataResponse updateUser(@RequestBody DataRequest request) {
    //     // 调用 userService.updateUser(userId, userInfo)
    //     // 返回更新结果
    // }

    // TODO: [数据库连接] 实现修改密码接口
    // @PutMapping("/password")
    // public DataResponse changePassword(@RequestBody DataRequest request) {
    //     // 调用 userService.changePassword(userId, oldPassword, newPassword)
    //     // 返回修改结果
    // }
}
