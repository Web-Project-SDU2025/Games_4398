package com.game.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 4398游戏管理系统主启动类
 *
 * @author 4398团队
 * @version 1.0.0
 */
@SpringBootApplication
public class GameManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameManagementSystemApplication.class, args);
        System.out.println("========================================");
        System.out.println("4398游戏管理系统启动成功！");
        System.out.println("访问地址: http://localhost:8080/api");
        System.out.println("========================================");
    }
}
