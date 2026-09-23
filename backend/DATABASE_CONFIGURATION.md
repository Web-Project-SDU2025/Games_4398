# 数据库配置说明

## 配置文件位置

`backend/src/main/resources/application-dev.properties`

## 需要修改的配置项

### 1. 数据库地址
```properties
spring.datasource.url=jdbc:mysql://数据库地址:端口/数据库名?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
```

**说明**：
- `数据库地址`：本地数据库填 `localhost`，远程数据库填 IP 地址或域名
- `端口`：MySQL 默认 `3306`
- `数据库名`：当前项目默认为 `game_system`

### 2. 数据库用户名
```properties
spring.datasource.username=用户名
```

**说明**：
- 本地开发通常是 `root`
- 生产环境会是老师提供的专门账号

### 3. 数据库密码
```properties
spring.datasource.password=密码
```

**说明**：
- 填写实际的数据库密码
- **注意**：此文件已加入 `.gitignore`，不会提交到 Git

---

## 配置示例

### 示例 1：本地数据库
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/game_system?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=123456
```

### 示例 2：远程数据库
```properties
spring.datasource.url=jdbc:mysql://192.168.1.100:3306/game_system?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=Asia/Shanghai
spring.datasource.username=game_user
spring.datasource.password=secure_password_123
```

### 示例 3：云服务器数据库（需要 SSL）
```properties
spring.datasource.url=jdbc:mysql://db.example.com:3306/game_system?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=Asia/Shanghai
spring.datasource.username=cloud_user
spring.datasource.password=cloud_password_456
```

---

## 配置后的测试步骤

1. 修改配置文件
2. 在 IDEA 中停止后端（如果正在运行）
3. 重新启动后端
4. 观察控制台是否有错误
5. 访问测试接口：`http://localhost:8080/api/base/test`

---

## 常见错误

### 错误 1：Communications link failure

**原因**：数据库地址、端口或密码错误，或数据库未启动

**解决**：
- 检查数据库地址和端口
- 检查用户名和密码
- 确认数据库服务正在运行

### 错误 2：Unknown database 'game_system'

**原因**：数据库不存在

**解决**：
- 确认数据库名称是否正确
- 如果老师的数据库名不是 `game_system`，修改 URL 中的数据库名

### 错误 3：Access denied for user

**原因**：用户名或密码错误

**解决**：
- 检查用户名和密码是否正确
- 确认该用户是否有访问该数据库的权限

---

## 注意事项

1. ⚠️ **不要把包含真实密码的配置文件提交到 Git**
2. ⚠️ **远程数据库连接可能需要配置防火墙和安全组**
3. ⚠️ **生产环境建议使用环境变量而不是配置文件**
4. ✅ **修改配置后必须重启后端才能生效**
