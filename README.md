# 4398 游戏管理网站

使用 Spring Boot、Vue 和 MySQL，提供验证码注册登录、账户管理、个人资料、游戏列表、游戏库、收藏和游戏记录。

## 目录

- `java-server/java-server`：当前 Spring Boot 后端，JDK 21。
- `web-vue`：当前 Vue 前端，新增页面使用普通 JavaScript。
- `database`：表结构、角色菜单初始化和历史迁移说明。
- `test`：旧版前后端原型，保留用于参考，不是当前运行入口。

## 本地配置

在后端 `src/main/resources` 下，将 `application-local.properties.example` 复制为 `application-local.properties`，填写数据库密码和 Base64 格式的 JWT 密钥。JWT 密钥解码后至少需要 32 字节。真实配置已被 Git 忽略，不要提交。

也可以通过环境变量 `DB_PASSWORD` 和 `JWT_SECRET` 提供这两个值。数据库地址及第 02 组库名在 `application.yml` 中。

新环境需要先在对应数据库导入 `database/schema.sql`，再导入 `database/seed.sql`。这两个文件不包含真实账号、密码或个人资料。网站注册创建游戏用户，管理员由数据库维护者设置，不能公开注册管理员。

## 开发运行

1. 用 JDK 21 在 IDEA 中启动后端的 `JavaServerApplication`，端口为 `22222`。
2. 在 `web-vue` 目录执行 `npm install`，然后执行 `npm run dev`。
3. 打开 `http://localhost:3000`。前端通过 `/api` 和 `/auth` 代理连接本地后端。

## 打包运行

先在 `web-vue` 中执行 `npm run build`，网页会输出到后端 `src/main/resources/public`。

然后在 `java-server/java-server` 中执行 `mvnw.cmd clean package`。生成的文件是 `target/java-server-1.0-SNAPSHOT.jar`。运行 `java -jar target/java-server-1.0-SNAPSHOT.jar` 后，打开 `http://localhost:22222`。

打包会包含本机配置，所以 JAR 不要上传到公开 Git 仓库。源码、SQL 备份和构建产物是不同文件：完整数据导出与备份仅保留在本机。

## 测试

在后端目录执行 `mvnw.cmd test`。现有集成测试连接配置中的数据库，事务结束后回滚测试数据；部分测试依赖开发数据库里编号为 1、2、3 的测试账号，管理员编号为 1，初始密码为 `123456`。请在专用开发库运行，不要直接用于生产数据库。

前端执行 `npm run build` 检查生产构建。原有 `vue-tsc` 与当前 TypeScript 版本存在兼容问题，`npm run typecheck` 暂不能作为通过的验证项。

原项目使用 history 路由，JAR 首页可打开，但直接刷新 `/Login` 等子路径可能返回 404，此处尚未添加额外的后端页面转发。
