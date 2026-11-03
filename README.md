# 4398 游戏管理网站

4398 是一个使用 Spring Boot、Vue 和 MySQL 开发的游戏管理网站。用户可以通过验证码注册和登录，修改个人资料，将游戏加入自己的游戏库或收藏，并查看、添加和删除游玩记录。管理员还可以维护账户、游戏信息、菜单和字典。

管理员登录入口是 `/AdminLogin`，只接受已有管理员账号。管理中心 `/AccountManage` 包含账户、游戏分数和游戏记录三个视图，普通用户不能访问；后端也会独立检查权限。系统账号 `admin` 不允许删除或降级。分数对应一次游玩记录，修改分数会同步反映在记录中。

左侧的“AI 客服”通过后端调用百炼应用，回答游戏规则、玩法和本站功能问题。客服和宠物的回复支持 Markdown 标题、加粗、列表、引用、表格、链接和代码块，宽表格与代码块可以单独横向滚动。右下角的 Octorange 也使用同一个接口，支持待机、悬停和思考状态，可以拖动、隐藏和恢复。聊天历史按账号保存在浏览器中，云端会话编号由后端管理，密钥不会发送到前端。

## 目录

- `java-server/java-server` 存放当前后端代码，后端使用 JDK 21。
- `web-vue` 存放前端代码。请求、校验和数据处理使用普通 JavaScript；Vue 负责页面绑定、路由和树形菜单等交互。业务源码不再使用 TypeScript，构建配置与工具生成的类型声明仍保留。
- `database` 存放建表 SQL、角色和菜单的初始化数据，以及历史迁移说明。

## 项目结构

```text
4398/
├── .gitignore
├── README.md
├── start.cmd                                    双击启动本地前后端
├── scripts/
│   └── start.ps1                                环境检查、端口检查和进程管理
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
│           │   │                       │   ├── AdminRecordController.java       管理员分数和记录接口
│           │   │                       │   ├── AiController.java                百炼客服与宠物共用接口
│           │   │                       │   ├── BaseController.java
│           │   │                       │   ├── GameController.java
│           │   │                       │   ├── GameUserController.java
│           │   │                       │   ├── LocalAccountController.java      本机检查切号
│           │   │                       │   ├── PageController.java              前端地址转发
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
│           │   │                       │   ├── AdminRecordService.java          管理员记录增删改查
│           │   │                       │   ├── AiService.java                   百炼请求、游戏范围提示和会话隔离
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
    │   ├── images/
    │   │   └── pet/
    │   │       ├── idling.png                      Octorange 待机
    │   │       ├── hovering.png                    Octorange 悬停
    │   │       └── thinking.png                    Octorange 思考
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
        ├── main.js                               前端启动入口
        ├── App.vue                               根组件
        ├── Login.vue                             验证码注册与登录页面
        ├── env.d.ts
        ├── components.d.ts
        ├── components/
        │   ├── Navi.vue                          顶部导航、左侧菜单和页面显示区域
        │   ├── Octorange.vue                     宠物位置、状态和浮窗
        │   ├── SupportChat.vue                   客服与宠物共用的聊天窗口
        │   └── AccountSwitcher.vue               本机检查账号切换与异常提示
        ├── logic/                                普通 JS 处理，不操作页面元素
        │   ├── account.js                        账号显示、密码校验和请求资料
        │   ├── auth.js                           登录表单、验证码和注册流程
        │   ├── forms.js                          独立编辑表单和角色编号整理
        │   ├── navigation.js                     菜单查找、标签和时钟格式
        │   ├── pet.js                            宠物提示、设置和位置边界
        │   └── support.js                        会话整理和按账号存储历史
        ├── router/
        │   └── index.js                          页面地址与组件的对应关系
        ├── services/                             前端请求函数
        │   ├── gameServ.js                       游戏、游戏库、收藏和记录
        │   ├── adminServ.js                      管理员记录和分数请求
        │   ├── aiServ.js                         百炼请求、超时和停止等待
        │   ├── gameUserServ.js                   账户管理与个人资料
        │   ├── genServ.js                        通用请求和登录凭证
        │   ├── infoServ.js                       修改密码
        │   ├── mainServ.js                       权限菜单
        │   ├── systemServ.js                     菜单与字典管理
        │   └── userServ.js                       登录、注册和验证码
        ├── stores/
        │   ├── app.js                            登录信息和菜单状态
        │   └── index.js                          Pinia 初始化
        ├── styles/
        │   ├── game.css                          游戏和账户表格布局
        │   ├── auth.css                          登录和注册页面
        │   ├── home.css                          首页功能入口
        │   ├── initial.css                       脚本加载前的背景颜色
        │   ├── motion.css                        路由、刷新、弹窗和控件过渡
        │   ├── platform.css                      公共控件、表单和系统页面
        │   ├── support.css                       客服会话布局
        │   ├── pet.css                           宠物与浮窗布局
        │   ├── workspace.css                     顶栏、左侧菜单、标签和状态栏
        │   └── element/
        │       ├── dark.scss
        │       └── index.scss
        ├── tools/
        │   ├── messageBox.js                     提示和确认框
        │   ├── markdown.js                       Markdown 解析、危险内容清理和链接处理
        │   └── motion.js                         刷新反馈和动画清理
        └── views/                                功能页面
            ├── MainPage.vue                      首页功能入口，不调用统计接口
            ├── AiSupport.vue                     AI 客服会话页面
            ├── account/
            │   ├── AccountManage.vue             管理中心分类入口
            │   ├── RecordManage.vue              管理员游戏分数和记录
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

百炼应用 ID 沿用参考项目的 `08f487dd5e10446287bec082430afe7f`。在后端的 `src/main/resources/application-local.properties` 中配置 `app.ai.api-key`，也可以通过环境变量 `DASHSCOPE_API_KEY` 提供。这个本地配置文件已被 Git 忽略，请勿把实际密钥写入前端或提交到仓库。百炼应用本身也应设置游戏助手角色，代码中的范围提示不能保证模型永远不偏题。

客服每条问题最多 2000 字，同一账号每分钟最多 8 次且同时只处理一条。停止按钮会取消浏览器等待；后端已经发给百炼的请求可能继续执行，最多等待 60 秒，期间再次提问会提示稍后重试。重启后端或较长时间不提问会重建云端上下文，浏览器中保存的历史仍可查看。

运行前，请先安装 JDK 21 和 Node.js，并确认电脑能够连接 MySQL 数据库。

请在后端 `src/main/resources` 目录中，将 `application-local.properties.example` 复制为 `application-local.properties`，然后填写数据库密码和 JWT 密钥。JWT 用来验证登录身份，密钥需要采用 Base64 格式，解码后至少有 32 字节。Git 会忽略真实配置文件，请勿将它提交到公开仓库。

你也可以通过环境变量 `DB_PASSWORD` 和 `JWT_SECRET` 提供这两个值。`application.yml` 已配置课程分配给第 02 组的在线数据库；团队成员在本地运行前后端时，仍连接这个在线库。

如果你使用新建的空数据库，请先选中目标数据库，再依次导入 `database/schema.sql` 和 `database/seed.sql`。已有数据库不需要重复导入。这两个文件只提供表结构、角色和菜单，不包含真实账号、密码或个人资料。用户可以在网站注册游戏账号，数据库维护者负责设置首个管理员；公开注册不能获得管理员权限。

你可以在 [数据库说明](database/README.md) 中查看表的用途和迁移记录。完整数据导出和备份只保留在本机，不随公开仓库提供。

## 开发运行

本机可以直接双击根目录的 `start.cmd`。程序会检查 JDK 21、Node.js、配置和端口，依次启动后端 `22222` 和前端 `3000`，然后自动打开网页。后端运行的是当前源码，不是旧 JAR。

请保留启动窗口，按回车停止本次启动的前后端。端口已被占用时，程序会提示占用进程，不会强制关闭其他程序。启动日志保存在根目录 `logs` 中。首次使用前，仍需填写本地配置，并在 `web-vue` 执行 `npm install`。

如果你想在 IDEA 中调试，也可以手动启动：

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

前端可以执行 `npm run lint` 检查基础写法和函数说明，执行 `npm test` 检查标签、密码、注册流程、表单复制和列表刷新，再执行 `npm run build` 检查生产构建。测试使用内存数据，不写入数据库。原有 `vue-tsc` 与当前 TypeScript 版本不兼容，`npm run typecheck` 暂时无法正常完成。

后端通过 `PageController` 将已知前端页面转发到 `index.html`，直接打开或刷新 `/MainPage`、`/AdminLogin`、`/AccountManage` 等地址可以正常加载。未知接口和不存在的静态文件不会被转成首页。

本机检查时可以在不提交的 `application-local.properties` 中设置 `app.local-account-switch=true`，登录后右上角可免密切换账号。后端同时要求请求直接来自本机，默认配置关闭该能力。切换时重新签发目标账号的凭证并读取对应菜单，旧账号的标签、列表缓存和客服历史不会混用。普通部署请保持该开关关闭。

## 前端维护

普通计算和资料整理放在 `src/logic`，接口请求放在 `src/services`，页面布局和组件事件留在 Vue，样式按用途放进 `src/styles`。文件只在职责清楚时拆分，不为减少行数增加转发层。异常提示、失败重试和取消编辑仍须保留。

页面切换只使用 Vue 的一次淡化，不再给同一页面叠加 CSS 入场动画。已加载的游戏列表和记录会保留，写入成功后递增数据版本，相关缓存再次显示时才重新读取；主动查询和刷新始终读取后端。这样既减少重复请求，也避免看到旧数据。
