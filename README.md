# 4398 游戏管理系统

## 项目简介

基于 Spring Boot + Vue 3 + TypeScript 的游戏管理系统，包含完整的前后端分离架构。

详细的开发指南请查看：[DEVELOPMENT_GUIDE.md](./DEVELOPMENT_GUIDE.md)

## 技术栈

后端：Java 21 + Spring Boot 3.2.0 + Spring Data JPA + MySQL 8.0+ + JWT 认证

前端：Vue 3 + TypeScript + Vite + HTML5 + CSS3

## 项目结构

设计者：Octorange/Windy-Field（强迫症患者 bushi）

下面的目录树只记录当前存在的文件。只有 `README.md` 的目录是预留目录，说明中的规划类名不是已经实现的代码。构建产物、依赖和 IDE 文件不列入。

```
4398/
├── backend/                                                # 后端项目
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/game/system/
│   │   │   │   ├── GameManagementSystemApplication.java    # 主启动类
│   │   │   │   │
│   │   │   │   ├── config/                                 # 配置类
│   │   │   │   │   ├── ApplicationConfiguration.java       # 应用配置
│   │   │   │   │   ├── CorsConfig.java                     # 跨域配置
│   │   │   │   │   ├── JwtAuthenticationFilter.java        # JWT 过滤器
│   │   │   │   │   └── SecurityConfiguration.java          # 安全配置
│   │   │   │   │
│   │   │   │   ├── controller/                             # 控制器层
│   │   │   │   │   ├── BaseController.java                 # 基础控制器
│   │   │   │   │   ├── GameController.java
│   │   │   │   │   ├── UserController.java
│   │   │   │   │   └── UserGameListController.java
│   │   │   │   │
│   │   │   │   ├── service/                                # 服务层
│   │   │   │   │   ├── impl/                               # 服务实现
│   │   │   │   │   │   └── README.md                       # 服务实现说明，暂无实现类
│   │   │   │   │   ├── JwtService.java
│   │   │   │   │   ├── UserDetailsImpl.java
│   │   │   │   │   ├── UserDetailsServiceImpl.java
│   │   │   │   │   └── README.md                           # 服务层说明
│   │   │   │   │
│   │   │   │   ├── repository/                             # 数据访问层
│   │   │   │   │   └── README.md                           # 数据访问层说明
│   │   │   │   │
│   │   │   │   ├── entity/                                 # 实体类
│   │   │   │   │   └── README.md                           # 实体类说明
│   │   │   │   │
│   │   │   │   ├── dto/                                    # 数据传输对象
│   │   │   │   │   └── README.md                           # DTO 说明
│   │   │   │   │
│   │   │   │   ├── payload/                                # 现有请求和响应封装
│   │   │   │   │   ├── request/DataRequest.java
│   │   │   │   │   └── response/DataResponse.java
│   │   │   │   │
│   │   │   │   ├── exception/                              # 自定义异常
│   │   │   │   │   └── README.md                           # 异常处理说明
│   │   │   │   │
│   │   │   │   ├── enums/                                  # 枚举类
│   │   │   │   │   └── README.md                           # 枚举类说明
│   │   │   │   │
│   │   │   │   ├── util/                                   # 工具类
│   │   │   │   │   ├── CommonMethod.java                   # 通用方法
│   │   │   │   │   ├── DateTimeTool.java                   # 日期工具
│   │   │   │   │   ├── JwtUtil.java                        # JWT 工具
│   │   │   │   │   ├── ComDataUtil.java
│   │   │   │   │   └── LoginControlUtil.java
│   │   │   │   │
│   │   │   │   ├── aspect/                                 # AOP 切面
│   │   │   │   │   └── README.md                           # AOP 切面说明
│   │   │   │   │
│   │   │   │   ├── interceptor/                            # 拦截器
│   │   │   │   │   └── README.md                           # 拦截器说明
│   │   │   │   │
│   │   │   │   └── validator/                              # 自定义验证器
│   │   │   │       └── README.md                           # 验证器说明
│   │   │   │
│   │   │   └── resources/                                  # 资源文件
│   │   │       ├── application.properties                  # 主配置文件
│   │   │       ├── application-dev.properties              # 本地开发配置（Git 忽略）
│   │   │       │
│   │   │       ├── static/                                 # 静态资源
│   │   │       │   └── README.md                           # 静态资源说明
│   │   │       │
│   │   │       ├── templates/                              # 模板文件
│   │   │       │   └── README.md                           # 模板文件说明
│   │   │       │
│   │   │       └── db/                                     # 数据库相关
│   │   │           └── migration/                          # 数据库迁移脚本
│   │   │               └── README.md                       # 迁移脚本说明
│   │   │
│   │   └── test/                                           # 测试目录
│   │       ├── java/com/game/system/
│   │       │   ├── README.md                               # 测试总说明
│   │       │   │
│   │       │   ├── controller/                             # 控制器测试
│   │       │   │   └── README.md                           # 控制器测试说明
│   │       │   │
│   │       │   ├── service/                                # 服务测试
│   │       │   │   └── README.md                           # 服务测试说明
│   │       │   │
│   │       │   └── repository/                             # 数据访问测试
│   │       │       └── README.md                           # 数据访问测试说明
│   │       │
│   │       └── resources/                                  # 测试资源
│   │           └── README.md                               # 测试资源说明
│   │
│   ├── .gitignore
│   ├── DATABASE_CONFIGURATION.md                           # 数据库配置说明
│   ├── DATABASE_TODO_COMPLETION_REPORT.md
│   ├── DATABASE_TODO_LIST.md
│   ├── init.sql                                            # 数据库初始化脚本
│   ├── pom.xml                                             # Maven 配置文件
│   └── test-api.http                                       # API 测试文件
│
├── frontend/                                               # 前端项目
│   ├── src/
│   │   ├── api/                                            # API 接口
│   │   │   └── index.ts                                    # API 接口封装
│   │   │
│   │   ├── assets/                                         # 静态资源
│   │   │   └── README.md                                   # 静态资源说明
│   │   │
│   │   ├── components/                                     # 组件
│   │   │   ├── Login.vue                                   # 登录组件
│   │   │   └── Home.vue                                    # 主页组件
│   │   │
│   │   ├── views/                                          # 页面组件
│   │   │   └── README.md                                   # 页面组件说明
│   │   │
│   │   ├── router/                                         # 路由配置
│   │   │   └── README.md                                   # 路由配置说明
│   │   │
│   │   ├── store/                                          # 状态管理
│   │   │   └── README.md                                   # 状态管理说明
│   │   │
│   │   ├── composables/                                    # 组合式函数
│   │   │   └── README.md                                   # 组合式函数说明
│   │   │
│   │   ├── utils/                                          # 工具函数
│   │   │   ├── http.ts                                     # HTTP 请求封装
│   │   │   ├── datetime.ts                                 # 日期工具
│   │   │   └── common.ts                                   # 通用工具
│   │   │
│   │   ├── types/                                          # TypeScript 类型
│   │   │   └── index.ts                                    # 类型定义
│   │   │
│   │   ├── constants/                                      # 常量定义
│   │   │   ├── status.ts                                   # 状态码常量
│   │   │   ├── regex.ts                                    # 正则表达式常量
│   │   │   ├── api.ts                                      # API 常量
│   │   │   └── index.ts                                    # 常量统一导出
│   │   │
│   │   ├── directives/                                     # 自定义指令
│   │   │   └── README.md                                   # 自定义指令说明
│   │   │
│   │   ├── plugins/                                        # 插件
│   │   │   └── README.md                                   # 插件说明
│   │   │
│   │   ├── env.d.ts                                        # Vite 类型声明
│   │   ├── main.ts                                         # 登录页入口
│   │   └── home.ts                                         # 主页入口
│   │
│   ├── assets/style.css                                    # 页面样式
│   ├── views/
│   │   └── home.html                                       # 主页 HTML
│   │
│   ├── .gitignore
│   ├── index.html                                          # 登录页 HTML
│   ├── vite.config.ts                                      # Vite 配置
│   ├── tsconfig.json                                       # TypeScript 配置
│   ├── tsconfig.node.json
│   ├── package.json                                        # NPM 配置
│   ├── package-lock.json
│   └── .env.example                                        # 环境变量示例
│
├── .gitignore                                              # Git 忽略文件
├── .env.example                                            # 环境变量示例
├── README.md                                               # 项目说明
├── QUICKSTART.md                                           # 快速开始指南
├── DEVELOPMENT_GUIDE.md                                    # 开发指南
└── STRUCTURE.md                                            # 规划目录草案
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
- [STRUCTURE.md](./STRUCTURE.md) - 规划目录草案（不代表当前文件）
