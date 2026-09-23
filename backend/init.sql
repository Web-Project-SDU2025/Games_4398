-- 4398游戏管理系统数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS game_system
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE game_system;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（加密）',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `avatar` VARCHAR(255) COMMENT '头像URL',
    `role_id` BIGINT COMMENT '角色ID',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_username` (`username`),
    INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    `name` VARCHAR(50) NOT NULL UNIQUE COMMENT '角色名称',
    `code` VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    `description` VARCHAR(255) COMMENT '角色描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 菜单表
CREATE TABLE IF NOT EXISTS `menu` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '菜单ID',
    `name` VARCHAR(50) NOT NULL COMMENT '菜单名称',
    `title` VARCHAR(100) NOT NULL COMMENT '菜单标题',
    `path` VARCHAR(255) COMMENT '路由路径',
    `icon` VARCHAR(100) COMMENT '图标',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父菜单ID，0为顶级菜单',
    `order_num` INT DEFAULT 0 COMMENT '排序号',
    `menu_type` TINYINT DEFAULT 1 COMMENT '类型：1-菜单 2-按钮',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

-- 角色菜单关联表
CREATE TABLE IF NOT EXISTS `role_menu` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    INDEX `idx_role_id` (`role_id`),
    INDEX `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

-- 字典类型表
CREATE TABLE IF NOT EXISTS `dictionary_type` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '字典类型ID',
    `name` VARCHAR(100) NOT NULL COMMENT '字典名称',
    `code` VARCHAR(100) NOT NULL UNIQUE COMMENT '字典编码',
    `description` VARCHAR(255) COMMENT '描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

-- 字典数据表
CREATE TABLE IF NOT EXISTS `dictionary_data` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '字典数据ID',
    `type_id` BIGINT NOT NULL COMMENT '字典类型ID',
    `label` VARCHAR(100) NOT NULL COMMENT '字典标签',
    `value` VARCHAR(100) NOT NULL COMMENT '字典值',
    `order_num` INT DEFAULT 0 COMMENT '排序号',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_type_id` (`type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    `user_id` BIGINT COMMENT '操作用户ID',
    `username` VARCHAR(50) COMMENT '操作用户名',
    `operation` VARCHAR(100) COMMENT '操作类型',
    `method` VARCHAR(200) COMMENT '请求方法',
    `params` TEXT COMMENT '请求参数',
    `ip` VARCHAR(50) COMMENT 'IP地址',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- 游戏表
CREATE TABLE IF NOT EXISTS `game` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '游戏ID',
    `name` VARCHAR(100) NOT NULL COMMENT '游戏名称',
    `description` TEXT COMMENT '游戏描述',
    `cover_image` VARCHAR(255) COMMENT '封面图片URL',
    `category` VARCHAR(50) COMMENT '游戏分类',
    `tags` VARCHAR(255) COMMENT '游戏标签（逗号分隔）',
    `game_url` VARCHAR(500) COMMENT '游戏链接或路径',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-下架 1-上架',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_name` (`name`),
    INDEX `idx_status` (`status`),
    INDEX `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏表';

-- 用户游戏清单表
CREATE TABLE IF NOT EXISTS `user_game_list` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `game_id` BIGINT NOT NULL COMMENT '游戏ID',
    `status` TINYINT DEFAULT 1 COMMENT '状态：1-收藏 2-正在玩 3-已完成',
    `play_time` INT DEFAULT 0 COMMENT '游戏时长（分钟）',
    `last_play_time` DATETIME COMMENT '最后游玩时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_game` (`user_id`, `game_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_game_id` (`game_id`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`game_id`) REFERENCES `game`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户游戏清单表';

-- 插入初始数据

-- 插入管理员角色
INSERT INTO `role` (`name`, `code`, `description`) VALUES
('超级管理员', 'ROLE_ADMIN', '系统超级管理员'),
('普通用户', 'ROLE_USER', '普通用户角色');

-- 不预置管理员账号或密码。完成密码哈希和认证功能后，按部署流程创建管理员。

-- 插入基础菜单
INSERT INTO `menu` (`name`, `title`, `path`, `icon`, `parent_id`, `order_num`, `menu_type`) VALUES
('system', '系统管理', '/system', 'setting', 0, 1, 1),
('user', '用户管理', '/system/user', 'user', 1, 1, 1),
('role', '角色管理', '/system/role', 'team', 1, 2, 1),
('menu', '菜单管理', '/system/menu', 'menu', 1, 3, 1),
('dictionary', '字典管理', '/system/dictionary', 'book', 1, 4, 1);

-- 给管理员角色分配所有菜单权限
INSERT INTO `role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `menu`;

-- 插入字典类型示例
INSERT INTO `dictionary_type` (`name`, `code`, `description`) VALUES
('用户状态', 'user_status', '用户账号状态'),
('性别', 'gender', '性别分类');

-- 插入字典数据示例
INSERT INTO `dictionary_data` (`type_id`, `label`, `value`, `order_num`) VALUES
(1, '启用', '1', 1),
(1, '禁用', '0', 2),
(2, '男', 'male', 1),
(2, '女', 'female', 2),
(2, '未知', 'unknown', 3);

-- 插入游戏分类字典
INSERT INTO `dictionary_type` (`name`, `code`, `description`) VALUES
('游戏分类', 'game_category', '游戏类型分类'),
('游戏状态', 'game_status', '游戏上下架状态'),
('用户游戏状态', 'user_game_status', '用户游戏清单状态');

INSERT INTO `dictionary_data` (`type_id`, `label`, `value`, `order_num`) VALUES
(3, '动作', 'action', 1),
(3, '冒险', 'adventure', 2),
(3, '角色扮演', 'rpg', 3),
(3, '策略', 'strategy', 4),
(3, '休闲', 'casual', 5),
(4, '上架', '1', 1),
(4, '下架', '0', 2),
(5, '收藏', '1', 1),
(5, '正在玩', '2', 2),
(5, '已完成', '3', 3);

-- 完成初始化
SELECT '数据库已完成初始化' AS message;
