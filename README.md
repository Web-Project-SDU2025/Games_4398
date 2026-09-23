# 4398游戏管理系统

## 项目简介

基于 Spring Boot + Vue 3 + TypeScript 的游戏管理系统，包含完整的前后端分离架构。

详细的开发指南请查看：[DEVELOPMENT_GUIDE.md](./DEVELOPMENT_GUIDE.md)

## 技术栈

后端：Java 21 + Spring Boot 3.2.0 + Spring Data JPA + MySQL 8.0+ + JWT 认证
前端：Vue 3 + TypeScript + Vite + HTML5 + CSS3

## 项目结构

```
4398/
├── backend/                 # 后端项目
│   ├── src/main/java/com/game/system/
│   │   ├── config/         # 配置类
│   │   ├── util/           # 工具类
│   │   ├── payload/        # 请求响应封装
│   │   └── controller/     # 控制器
│   ├── src/main/resources/
│   │   └── application.properties
│   ├── init.sql            # 数据库初始化脚本
│   └── pom.xml
│
└── frontend/                # 前端项目
    ├── src/
    │   ├── components/      # Vue组件
    │   ├── api/            # API接口
    │   ├── utils/          # 工具类
    │   └── types/          # 类型定义
    ├── vite.config.ts      # Vite配置
    └── package.json
```

## 核心功能

### 后端工具类
- CommonMethod - 通用方法（响应封装、数据提取、编号生成）
- DateTimeTool - 日期时间处理
- JwtUtil - JWT令牌管理
- DataRequest/DataResponse - 统一请求响应格式

### 前端工具类
- http.ts - HTTP请求封装（Fetch API + Token认证）
- datetime.ts - 日期时间工具（与后端对应）
- common.ts - 通用工具（验证、存储、防抖节流）
- api/index.ts - API接口封装

### Vue 3 组件
- Login.vue - 登录组件
- Home.vue - 主页组件

### 特性
- 热模块替换（HMR）- 代码修改立即生效
- API自动代理 - 前端 /api 自动转发到后端
- TypeScript支持 - 完整类型检查
- 路径别名 - @/ 映射到 src/

## 快速开始

详细步骤请查看：[DEVELOPMENT_GUIDE.md](./DEVELOPMENT_GUIDE.md) 或 [QUICKSTART.md](./QUICKSTART.md)

### 后端启动

1. 配置数据库环境变量

```powershell
$env:DB_USERNAME = Read-Host 'MySQL 用户名'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'MySQL 密码' -AsSecureString)).Password
$env:JWT_SECRET = [System.Net.NetworkCredential]::new('', (Read-Host 'JWT 随机密钥（至少 32 字节）' -AsSecureString)).Password
```

2. 创建数据库并初始化

```bash
mysql -u root -p
source backend/init.sql
```

3. 启动后端

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

后端服务：http://localhost:8080/api

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端服务：http://localhost:3000

## API接口规范

### 请求格式
```json
{
  "data": {
    "key1": "value1",
    "key2": "value2"
  }
}
```

### 响应格式
```json
{
  "code": 0,
  "data": {},
  "msg": "操作成功"
}
```

code: 0-成功 1-失败

## 开发建议

### 添加新接口
1. 后端：创建 Controller → Service → Repository
2. 前端：在 api/index.ts 添加接口方法
3. 类型定义：在 types/index.ts 添加 TypeScript 接口

### 后端示例
```java
// 返回成功
return CommonMethod.getReturnData(data, "操作成功");

// 返回失败
return CommonMethod.getReturnMessageError("操作失败");

// 格式化日期
String dateStr = DateTimeTool.formatDateTime(new Date(), "yyyy-MM-dd");
```

### 前端示例
```typescript
// 发送请求
const response = await userApi.login({ username, password });

// 格式化日期
const dateStr = formatDate(new Date(), 'yyyy-MM-dd HH:mm:ss');

// 本地存储
storage.set('token', token);
const token = storage.get<string>('token');
```

## 注意事项

1. 编码统一：所有文件使用 UTF-8 编码
2. 时区处理：后端和前端统一使用 Asia/Shanghai 时区
3. 安全性：数据库凭据和 JWT 密钥必须通过环境变量配置。初始化脚本不创建默认管理员，完整认证流程仍待实现和验证，不应直接部署到公网
4. 数据库：首次运行会自动创建表结构（JPA DDL auto）

## 项目文档

- [DEVELOPMENT_GUIDE.md](./DEVELOPMENT_GUIDE.md) - 完整的开发文档和任务清单
- [QUICKSTART.md](./QUICKSTART.md) - 快速开始指南
- [MIGRATION_REPORT.md](./MIGRATION_REPORT.md) - 框架移植报告
