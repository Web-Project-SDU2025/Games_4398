# 4398 游戏管理网站

4398 是一个使用 Spring Boot、Vue 和 MySQL 开发的游戏管理网站。用户可以通过验证码注册和登录，修改个人资料，将游戏加入自己的游戏库或收藏，并查看、添加和删除游玩记录。管理员还可以维护账户、游戏信息、菜单和字典。

## 目录

- `java-server/java-server` 存放当前后端代码，后端使用 JDK 21。
- `web-vue` 存放当前 Vue 前端代码。我们使用普通 JavaScript 编写新增页面，项目中仍保留部分示例工程的 TypeScript 代码。
- `database` 存放建表 SQL、角色和菜单的初始化数据，以及历史迁移说明。

## 项目结构

```text
4398/
├── .gitignore
├── README.md
├── database/                                     数据库结构与迁移说明
│   ├── README.md
│   ├── schema.sql                                当前表结构，不含账户数据
│   ├── seed.sql                                  角色和菜单初始化数据
│   ├── migrate_game.sql                          历史游戏业务迁移脚本
│   ├── merge_user.sql                            历史账户合并脚本
│   └── finish_user_merge.sql                     账户合并后的旧表清理脚本
├── java-server/
│   └── java-server/                              后端工程，以 pom.xml 为入口
│       ├── .gitattributes
│       ├── .gitignore
│       ├── .mvn/
│       │   └── wrapper/
│       │       └── maven-wrapper.properties
│       ├── mvnw.cmd                              Windows Maven 启动脚本
│       ├── pom.xml                               依赖、JDK 版本和打包配置
│       └── src/
│           ├── main/
│           │   ├── java/
│           │   │   └── cn/
│           │   │       └── edu/
│           │   │           └── sdu/
│           │   │               └── java/
│           │   │                   └── server/
│           │   │                       ├── JavaServerApplication.java       后端启动入口
│           │   │                       ├── SystemApplicationListener.java   启动后加载系统数据
│           │   │                       ├── configs/                         认证与权限配置
│           │   │                       │   ├── ApplicationConfiguration.java
│           │   │                       │   ├── JwtAuthenticationFilter.java
│           │   │                       │   └── SecurityConfiguration.java
│           │   │                       ├── controllers/                     接收前端请求
│           │   │                       │   ├── AuthController.java
│           │   │                       │   ├── BaseController.java
│           │   │                       │   ├── GameController.java
│           │   │                       │   ├── GameUserController.java
│           │   │                       │   └── StatisticsController.java
│           │   │                       ├── models/                          数据库实体
│           │   │                       │   ├── DictionaryInfo.java
│           │   │                       │   ├── EUserType.java
│           │   │                       │   ├── Game.java
│           │   │                       │   ├── GameRecord.java
│           │   │                       │   ├── MenuInfo.java
│           │   │                       │   ├── ModifyLog.java
│           │   │                       │   ├── RequestLog.java
│           │   │                       │   ├── StatisticsDay.java
│           │   │                       │   ├── SystemInfo.java
│           │   │                       │   ├── User.java
│           │   │                       │   └── UserType.java
│           │   │                       ├── payload/                         请求与返回数据结构
│           │   │                       │   ├── request/
│           │   │                       │   │   ├── DataRequest.java
│           │   │                       │   │   └── LoginRequest.java
│           │   │                       │   └── response/
│           │   │                       │       ├── DataResponse.java
│           │   │                       │       ├── JwtResponse.java
│           │   │                       │       ├── MyTreeNode.java
│           │   │                       │       ├── OptionItem.java
│           │   │                       │       └── OptionItemList.java
│           │   │                       ├── repositorys/                     数据库读写接口
│           │   │                       │   ├── DictionaryInfoRepository.java
│           │   │                       │   ├── GameRecordRepository.java
│           │   │                       │   ├── GameRepository.java
│           │   │                       │   ├── MenuInfoRepository.java
│           │   │                       │   ├── ModifyLogRepository.java
│           │   │                       │   ├── StatisticsDayRepository.java
│           │   │                       │   ├── SystemInfoRepository.java
│           │   │                       │   ├── UserRepository.java
│           │   │                       │   └── UserTypeRepository.java
│           │   │                       ├── services/                        业务处理
│           │   │                       │   ├── AuthenticationService.java
│           │   │                       │   ├── AuthService.java
│           │   │                       │   ├── BaseService.java
│           │   │                       │   ├── GameService.java
│           │   │                       │   ├── GameUserService.java
│           │   │                       │   ├── JwtService.java
│           │   │                       │   ├── StatisticsService.java
│           │   │                       │   ├── SystemService.java
│           │   │                       │   ├── UserDetailsImpl.java
│           │   │                       │   └── UserDetailsServiceImpl.java
│           │   │                       ├── uml/
│           │   │                       │   └── game.puml                    实体关系图
│           │   │                       └── util/                            公共工具
│           │   │                           ├── ComDataUtil.java
│           │   │                           ├── CommonMethod.java
│           │   │                           ├── DateTimeTool.java
│           │   │                           └── LoginControlUtil.java
│           │   └── resources/
│           │       ├── application.yml                        后端公开配置
│           │       ├── application-local.properties           本机密码与密钥，不提交
│           │       ├── application-local.properties.example   本机配置模板
│           │       ├── loggback.xml                           日志配置
│           │       └── font/
│           │           └── SourceHanSansSC-Regular.ttf
│           └── test/
│               └── java/
│                   └── cn/
│                       └── edu/
│                           └── sdu/
│                               └── java/
│                                   └── server/
│                                       ├── AuthenticationTests.java
│                                       ├── GameMigrationTests.java
│                                       ├── JavaServerApplicationTests.java
│                                       └── UserMergeTests.java
└── web-vue/                                      Vue 前端工程
    ├── .gitignore
    ├── .npmrc
    ├── README.md
    ├── index.html                                网页入口
    ├── package.json                              依赖和运行命令
    ├── package-lock.json                         依赖版本记录
    ├── tsconfig.json
    ├── vite.config.ts                            本地代理和构建路径
    ├── public/                                   静态图片和网站图标
    │   ├── 66668888.jpg
    │   ├── alarm.png
    │   ├── backgroud.png
    │   ├── certifiy.png
    │   ├── dayOrder.png
    │   ├── en.png
    │   ├── favicon.ico
    │   ├── favicon.png
    │   ├── favicon.svg
    │   ├── hi.png
    │   ├── littleT.png
    │   ├── loginName.png
    │   ├── loginOut.png
    │   ├── logo.png
    │   ├── password.png
    │   ├── question.png
    │   ├── refresh.png
    │   ├── sdu.png
    │   ├── serch.png
    │   ├── system_info.png
    │   ├── tuichu.png
    │   ├── user.png
    │   └── wx.png
    └── src/
        ├── main.ts                               Vue 启动入口
        ├── App.vue                               根组件
        ├── Login.vue                             验证码注册与登录页面
        ├── env.d.ts
        ├── components.d.ts
        ├── assets/
        │   └── vue.svg
        ├── components/
        │   └── Navi.vue                          顶部导航、左侧菜单和页面显示区域
        ├── composables/
        │   ├── dark.ts
        │   └── index.ts
        ├── models/
        │   └── general.ts                        公共数据类型
        ├── router/
        │   └── index.js                          页面地址与组件的对应关系
        ├── services/                             前端请求函数
        │   ├── gameServ.js                       游戏、游戏库、收藏和记录
        │   ├── gameUserServ.js                   账户管理与个人资料
        │   ├── genServ.ts                        通用请求、认证请求头和文件请求
        │   ├── infoServ.js                       修改密码
        │   ├── mainServ.js                       菜单与主页统计
        │   ├── systemServ.ts                     菜单与字典管理
        │   └── userServ.js                       登录、注册和验证码
        ├── stores/
        │   ├── app.js                            登录信息和菜单状态
        │   └── index.ts                          Pinia 初始化
        ├── styles/
        │   ├── game.css                          游戏和账户页面的公共样式
        │   ├── index.scss                        全局样式
        │   └── element/
        │       ├── dark.scss
        │       └── index.scss
        ├── tools/
        │   ├── comMethod.ts                      日期、对话框等工具
        │   └── messageBox.ts                     提示和确认框
        └── views/                                功能页面
            ├── MainPage.vue                      主界面与图表
            ├── account/
            │   ├── GameUserManage.vue            用户管理
            │   └── GameUserProfile.vue           个人资料
            ├── game/
            │   ├── GameList.vue                  游戏列表、游戏库和收藏共用页面
            │   └── GameRecords.vue               游玩记录
            ├── info/
            │   └── Password.vue                  修改密码
            └── system/
                ├── DictionaryManage.vue          字典管理
                └── MenuManage.vue                菜单管理
```

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
