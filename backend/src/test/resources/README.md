# 测试资源目录

此目录用于存放测试相关的配置文件和资源。

## 目录内容

1. **application-test.properties** - 测试环境配置
2. **测试数据文件** - SQL 脚本、JSON 数据等
3. **Mock 数据** - 模拟数据文件

## 示例配置

### application-test.properties

```properties
# 测试环境配置

# 使用内存数据库 H2
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.username=sa
spring.datasource.password=

# JPA 配置
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# 日志级别
logging.level.root=INFO
logging.level.com.game.system=DEBUG

# JWT 测试配置
jwt.secret=test-secret-key-for-testing-only-do-not-use-in-production
jwt.expiration=3600000

# 禁用缓存
spring.cache.type=none
```

## 测试数据示例

### test-data.sql

```sql
-- 插入测试角色
INSERT INTO role (id, name, code, description, status) VALUES
(1, '测试管理员', 'TEST_ADMIN', '测试用管理员角色', 1),
(2, '测试用户', 'TEST_USER', '测试用普通用户角色', 1);

-- 插入测试用户
INSERT INTO user (id, username, password, email, phone, role_id, status) VALUES
(1, 'testadmin', '$2a$10$...', 'admin@test.com', '13800138000', 1, 1),
(2, 'testuser', '$2a$10$...', 'user@test.com', '13800138001', 2, 1);
```

## 使用方法

在测试类中使用 `@ActiveProfiles("test")` 激活测试配置：

```java
@SpringBootTest
@ActiveProfiles("test")
class UserServiceTest {
    // 测试代码
}
```
