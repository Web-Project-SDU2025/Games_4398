# 数据库迁移脚本目录

此目录用于存放数据库版本管理和迁移脚本。

## 数据库版本管理工具

### 1. Flyway（推荐）

轻量级数据库迁移工具，Spring Boot 官方集成。

#### 添加依赖

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

#### 配置

```properties
# Flyway 配置
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.validate-on-migrate=true
```

#### 脚本命名规范

```
V1__init_database.sql           # 初始化数据库
V2__add_user_table.sql          # 添加用户表
V3__add_role_table.sql          # 添加角色表
V4__alter_user_add_avatar.sql  # 修改用户表，添加头像字段
```

命名规则：`V{版本号}__{描述}.sql`

- 版本号：数字，可以用点分隔（如 V1.1）
- 双下划线分隔版本号和描述
- 描述：使用下划线分隔单词

### 2. Liquibase

功能更强大的数据库版本管理工具，支持多种格式。

#### 添加依赖

```xml
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
</dependency>
```

#### 配置

```properties
# Liquibase 配置
spring.liquibase.enabled=true
spring.liquibase.change-log=classpath:db/migration/changelog-master.xml
```

## 目录结构

```
db/migration/
├── V1__init_database.sql
├── V2__add_user_table.sql
├── V3__add_role_table.sql
├── V4__add_menu_table.sql
└── README.md
```

## 迁移脚本示例

### V1__init_database.sql

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS game_system
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE game_system;
```

### V2__add_user_table.sql

```sql
-- 创建用户表
CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    email VARCHAR(100) COMMENT '邮箱',
    status TINYINT DEFAULT 1 COMMENT '状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

### V3__alter_user_add_avatar.sql

```sql
-- 给用户表添加头像字段
ALTER TABLE user ADD COLUMN avatar VARCHAR(255) COMMENT '头像URL';
```

## 使用流程

1. **开发阶段**：编写新的迁移脚本
2. **测试阶段**：在测试环境执行迁移
3. **生产阶段**：在生产环境执行迁移

## 注意事项

1. **不可修改**：已执行的脚本不能修改，只能新增
2. **顺序执行**：脚本按版本号顺序执行
3. **幂等性**：脚本应该可以重复执行（使用 IF NOT EXISTS 等）
4. **备份数据**：执行前备份数据库
5. **回滚脚本**：重要变更应准备回滚脚本

## 查看迁移状态

Flyway 会在数据库中创建 `flyway_schema_history` 表记录迁移历史：

```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

## 当前项目建议

由于项目已有 `init.sql`，建议：

1. 将 `init.sql` 改名为 `V1__init_database.sql`
2. 移动到 `db/migration/` 目录
3. 后续数据库变更都通过迁移脚本管理
