# 4398游戏管理系统 - 快速开始

## 环境要求

- Java 21+
- Maven 3.8+
- MySQL 8.0+
- Node.js 18+
- npm 9+

---

## 后端启动

### 1. 创建数据库

在 MySQL 中执行：

```bash
mysql -u root -p
```

从项目根目录启动 MySQL 后，在 MySQL 命令行中执行：

```sql
source backend/init.sql
```

或者直接创建数据库：

```sql
CREATE DATABASE game_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 配置数据库连接和 JWT 密钥

在启动后端的同一个 PowerShell 窗口设置环境变量（输入不会写入命令历史）：

```powershell
$env:DB_USERNAME = Read-Host 'MySQL 用户名'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'MySQL 密码' -AsSecureString)).Password
$env:JWT_SECRET = [System.Net.NetworkCredential]::new('', (Read-Host 'JWT 随机密钥（至少 32 字节）' -AsSecureString)).Password
```

JWT 密钥应独立随机生成，不能使用示例字符串或重复使用数据库密码。必须设置以上三个变量，不要将真实凭据写入受 Git 跟踪的文件。Spring Boot 不会自动加载 .env 文件。

### 3. 启动后端服务

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

后端服务启动在：http://localhost:8080/api

---

## 前端启动

### 1. 安装依赖

```bash
cd frontend
npm install
```

### 2. 启动 Vite 开发服务器

```bash
npm run dev
```

前端访问地址：http://localhost:3000

其他命令：
```bash
npm run build       # 构建生产版本
npm run preview     # 预览生产版本
npm run type-check  # TypeScript 类型检查
```

---

## 登录功能状态

打开浏览器访问：http://localhost:3000

初始化脚本不创建默认管理员账号。当前后端尚未完成登录接口及真实用户加载流程，因此登录页面暂不能完成真实登录。完成并验证这些功能后，再通过安全的部署流程创建管理员；不要在初始化脚本中保存明文密码。当前项目不应直接部署到公网。

---

## 验证 API

### 1. 健康检查

```bash
curl http://localhost:8080/api/base/health
```

### 2. 测试接口

```bash
curl http://localhost:8080/api/base/test
```

预期响应：
```json
{
  "code": 0,
  "data": "Hello, Game Management System!",
  "msg": "成功"
}
```

---

## 常见问题

### 1. 后端启动失败

问题：端口被占用
```
Port 8080 was already in use
```

解决：修改端口
```properties
# application.properties
server.port=8081
```

问题：数据库连接失败
```
Communications link failure
```

解决：
- 检查 MySQL 是否启动
- 检查用户名密码是否正确
- 检查数据库是否存在

### 2. 前端编译失败

问题：TypeScript 编译错误

解决：
```bash
# 清理并重新安装依赖
rm -rf node_modules package-lock.json
npm install
npm run build
```

### 3. 跨域问题

问题：前端请求后端被拦截

解决：Vite 已配置代理，确保：
- 前端访问 http://localhost:3000
- 后端运行在 http://localhost:8080
- API 请求使用相对路径 /api/xxx

---

## 下一步

查看详细的开发文档：[开发指南.md](./开发指南.md)

---

## 提示

1. 开发时保持后端和前端同时运行
2. 修改代码后 Vite 会自动热更新
3. 使用浏览器开发者工具查看日志
4. 使用 Postman 或 Apifox 测试 API 接口
