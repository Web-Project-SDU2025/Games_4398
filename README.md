# 4398 游戏管理网站

4398 是一个使用 Spring Boot、Vue 和 MySQL 开发的游戏管理网站，目前仍在开发中。

游戏用户可以通过验证码注册和登录，修改个人资料，将游戏加入自己的游戏库或收藏，并查看、添加和删除游玩记录。管理员还可以维护账户、游戏信息、菜单和字典。系统通过角色区分管理员和游戏用户，两类账户共用 `User` 实体。

项目沿用了示例工程的主界面和 ECharts 图表。我们已经调整了业务名称和数据结构，登录注册页也已改为简单表单，其余界面还没有统一设计。

## 目录

- `java-server/java-server` 存放当前后端代码，后端使用 JDK 21。
- `web-vue` 存放当前 Vue 前端代码。我们使用普通 JavaScript 编写新增页面，项目中仍保留部分示例工程的 TypeScript 代码。
- `database` 存放建表 SQL、角色和菜单的初始化数据，以及历史迁移说明。

## 本地配置

运行前，请先安装 JDK 21 和 Node.js，并确认电脑能够连接 MySQL 数据库。

请在后端 `src/main/resources` 目录中，将 `application-local.properties.example` 复制为 `application-local.properties`，然后填写数据库密码和 JWT 密钥。JWT 用来验证登录身份，密钥需要采用 Base64 格式，解码后至少有 32 字节。Git 会忽略真实配置文件，请勿将它提交到公开仓库。

你也可以通过环境变量 `DB_PASSWORD` 和 `JWT_SECRET` 提供这两个值。`application.yml` 已配置课程分配给第 02 组的在线数据库；团队成员在本地运行前后端时，仍连接这个在线库。

如果你使用新建的空数据库，请先选中目标数据库，再依次导入 `database/schema.sql` 和 `database/seed.sql`。已有数据库不需要重复导入。这两个文件只提供表结构、角色和菜单，不包含真实账号、密码或个人资料。用户可以在网站注册游戏账号，数据库维护者负责设置首个管理员；公开注册不能获得管理员权限。

你可以在 [数据库说明](database/README.md) 中查看表的用途和迁移记录。完整数据导出和备份只保留在本机，不随公开仓库提供。

## 开发运行

1. 你先在 IDEA 中打开后端工程，选择 JDK 21，然后启动 `JavaServerApplication`。后端端口为 `22222`。
2. 你在 `web-vue` 目录打开终端，首次运行时执行 `npm install` 安装依赖，再执行 `npm run dev` 启动前端。
3. 浏览器打开 `http://localhost:3000`。Vue 开发服务会将 `/api` 和 `/auth` 请求转发给本地后端。

开发期间，前后端服务都需要保持运行。你修改 Vue 页面后，浏览器通常会自动更新；修改 Java 代码后，一般需要重启后端。请通过上面的地址访问网站，不要直接双击 `index.html`。

## 打包运行

你先在 `web-vue` 中执行 `npm run build`。Vite 会把生成的网页文件放进后端 `src/main/resources/public` 目录。

随后，你在 `java-server/java-server` 中执行 `mvnw.cmd clean package`，生成 `target/java-server-1.0-SNAPSHOT.jar`。在同一目录运行 `java -jar target/java-server-1.0-SNAPSHOT.jar` 后，浏览器打开 `http://localhost:22222` 即可访问网站。这种方式只需要启动 JAR，不需要另开 Vue 开发服务。

JAR 包含打包时的网页和后端代码。你修改源码后，需要重新打包并重启 JAR，新代码才会生效。打包还会包含本机配置，因此请勿将 JAR 上传到公开 Git 仓库。

## 测试

你可以在后端目录执行 `mvnw.cmd test`，检查验证码注册登录、账户资料、权限、游戏库、收藏和游戏记录。测试会连接配置中的数据库，并在事务结束后回滚测试数据。部分测试依赖编号为 1、2、3 的开发账号，其中管理员编号为 1，初始密码为 `123456`。这些测试应在专用开发库运行，不要直接连接生产数据库。

你可以在前端目录执行 `npm run build`，检查代码能否完成生产构建。原有 `vue-tsc` 与当前 TypeScript 版本不兼容，`npm run typecheck` 暂时无法正常完成。

项目目前仍有一个已知问题：前端沿用了 history 路由，JAR 首页可以打开，但用户直接访问或刷新 `/Login` 等子路径时可能遇到 404。后端尚未配置这些页面的转发。
