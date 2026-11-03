# 项目结构

[返回项目说明](../README.md)

后端工程位于 `java-server/java-server`，前端工程位于 `web-vue`。目录树列出源码、配置和文档；本机备份、依赖目录与构建产物另行说明。

```text
4398/
├── .gitignore
├── README.md
├── docs/
│   └── project-structure.md                                                   项目结构与文件职责
├── design.md                                                                  界面设计与参考来源
├── tokens.css                                                                 公共颜色、间距和动效参数
├── start.cmd                                                                  双击启动本地前后端
├── scripts/
│   └── start.ps1                                                              环境检查、端口检查和进程管理
├── database/                                                                  数据库结构与迁移说明
│   ├── README.md
│   ├── schema.sql                                                             当前表结构，不含账户数据
│   ├── seed.sql                                                               角色和菜单初始化数据
│   ├── migrate_game.sql                                                       历史游戏业务迁移脚本
│   ├── merge_user.sql                                                         历史账户合并脚本
│   └── finish_user_merge.sql                                                  账户合并后的旧表清理脚本
├── java-server/
│   └── java-server/                                                           后端工程，以 pom.xml 为入口
│       ├── .gitattributes
│       ├── .gitignore
│       ├── .mvn/
│       │   └── wrapper/
│       │       └── maven-wrapper.properties
│       ├── mvnw.cmd                                                           Windows Maven 启动脚本
│       ├── pom.xml                                                            依赖、JDK 版本和打包配置
│       └── src/
│           ├── main/
│           │   ├── java/
│           │   │   └── cn/
│           │   │       └── edu/
│           │   │           └── sdu/
│           │   │               └── java/
│           │   │                   └── server/
│           │   │                       ├── JavaServerApplication.java         后端启动入口
│           │   │                       ├── SystemApplicationListener.java     启动后加载系统数据
│           │   │                       ├── configs/                           认证与权限配置
│           │   │                       │   ├── ApplicationConfiguration.java
│           │   │                       │   ├── JwtAuthenticationFilter.java
│           │   │                       │   └── SecurityConfiguration.java
│           │   │                       ├── controllers/                       接收前端请求
│           │   │                       │   ├── AuthController.java
│           │   │                       │   ├── AdminRecordController.java     管理员分数和记录接口
│           │   │                       │   ├── AiController.java              百炼客服与宠物共用接口
│           │   │                       │   ├── BaseController.java
│           │   │                       │   ├── GameController.java
│           │   │                       │   ├── GameUserController.java
│           │   │                       │   ├── LocalAccountController.java    本机检查切号
│           │   │                       │   ├── PageController.java            前端地址转发
│           │   │                       │   └── StatisticsController.java
│           │   │                       ├── models/                            数据库实体
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
│           │   │                       ├── payload/                           请求与返回数据结构
│           │   │                       │   ├── request/
│           │   │                       │   │   ├── DataRequest.java
│           │   │                       │   │   └── LoginRequest.java
│           │   │                       │   └── response/
│           │   │                       │       ├── DataResponse.java
│           │   │                       │       ├── JwtResponse.java
│           │   │                       │       ├── MyTreeNode.java
│           │   │                       │       ├── OptionItem.java
│           │   │                       │       └── OptionItemList.java
│           │   │                       ├── repositorys/                       数据库读写接口
│           │   │                       │   ├── DictionaryInfoRepository.java
│           │   │                       │   ├── GameRecordRepository.java
│           │   │                       │   ├── GameRepository.java
│           │   │                       │   ├── MenuInfoRepository.java
│           │   │                       │   ├── ModifyLogRepository.java
│           │   │                       │   ├── StatisticsDayRepository.java
│           │   │                       │   ├── SystemInfoRepository.java
│           │   │                       │   ├── UserRepository.java
│           │   │                       │   └── UserTypeRepository.java
│           │   │                       ├── services/                          业务处理
│           │   │                       │   ├── AuthenticationService.java
│           │   │                       │   ├── AuthService.java
│           │   │                       │   ├── AdminRecordService.java        管理员记录增删改查
│           │   │                       │   ├── AiService.java                 百炼请求、游戏范围提示和会话隔离
│           │   │                       │   ├── BaseService.java
│           │   │                       │   ├── GameService.java
│           │   │                       │   ├── GameUserService.java
│           │   │                       │   ├── JwtService.java
│           │   │                       │   ├── StatisticsService.java
│           │   │                       │   ├── SystemService.java
│           │   │                       │   ├── UserDetailsImpl.java
│           │   │                       │   └── UserDetailsServiceImpl.java
│           │   │                       ├── uml/
│           │   │                       │   └── game.puml                      实体关系图
│           │   │                       └── util/                              公共工具
│           │   │                           ├── ComDataUtil.java
│           │   │                           ├── CommonMethod.java
│           │   │                           ├── DateTimeTool.java
│           │   │                           └── LoginControlUtil.java
│           │   └── resources/
│           │       ├── application.yml                                        后端公开配置
│           │       ├── application-local.properties                           本机密码与密钥，不提交
│           │       ├── application-local.properties.example                   本机配置模板
│           │       ├── loggback.xml                                           日志配置
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
│                                       ├── AiTests.java                       百炼接口与会话测试
│                                       ├── GameMigrationTests.java
│                                       ├── JavaServerApplicationTests.java
│                                       ├── ManagementTests.java               管理中心权限与记录测试
│                                       └── UserMergeTests.java
└── web-vue/                                                                   Vue 前端工程
    ├── .gitignore
    ├── .npmrc
    ├── README.md
    ├── index.html                                                             网页入口
    ├── package.json                                                           依赖和运行命令
    ├── package-lock.json                                                      依赖版本记录
    ├── tsconfig.json
    ├── vite.config.ts                                                         本地代理和构建路径
    ├── public/                                                                静态图片和网站图标
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
    │   │   ├── game-collection.png                                            游戏库空状态插画
    │   │   └── pet/
    │   │       ├── idling.png                                                 Octorange 待机
    │   │       ├── hovering.png                                               Octorange 悬停
    │   │       └── thinking.png                                               Octorange 思考
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
    ├── scripts/
    │   └── check-style.cjs                                                    代码风格检查
    ├── tests/
    │   ├── ai.test.mjs                                                        客服与宠物交互测试
    │   ├── dashboard.test.mjs                                                 首页统计与日期测试
    │   ├── logic.test.mjs                                                     表单、导航和刷新逻辑测试
    │   └── session.test.mjs                                                   登录身份与权限测试
    └── src/
        ├── main.js                                                            前端启动入口
        ├── App.vue                                                            根组件
        ├── Login.vue                                                          验证码注册与登录页面
        ├── env.d.ts
        ├── components.d.ts
        ├── components/
        │   ├── Navi.vue                                                       顶部导航、左侧菜单和页面显示区域
        │   ├── DashboardActivity.vue                                          最近七天游玩活动图
        │   ├── Octorange.vue                                                  宠物位置、状态和浮窗
        │   ├── SupportChat.vue                                                客服与宠物共用的聊天窗口
        │   └── AccountSwitcher.vue                                            本机检查账号切换与异常提示
        ├── logic/                                                             普通 JS 处理，不操作页面元素
        │   ├── account.js                                                     账号显示、密码校验和请求资料
        │   ├── auth.js                                                        登录表单、验证码和注册流程
        │   ├── dashboard.js                                                   首页统计、记录排序和北京时间处理
        │   ├── forms.js                                                       独立编辑表单和角色编号整理
        │   ├── navigation.js                                                  菜单查找、标签和时钟格式
        │   ├── pet.js                                                         宠物提示、设置和位置边界
        │   └── support.js                                                     会话整理和按账号存储历史
        ├── router/
        │   └── index.js                                                       页面地址与组件的对应关系
        ├── services/                                                          前端请求函数
        │   ├── gameServ.js                                                    游戏、游戏库、收藏和记录
        │   ├── adminServ.js                                                   管理员记录和分数请求
        │   ├── aiServ.js                                                      百炼请求、超时和停止等待
        │   ├── gameUserServ.js                                                账户管理与个人资料
        │   ├── genServ.js                                                     通用请求和登录凭证
        │   ├── infoServ.js                                                    修改密码
        │   ├── mainServ.js                                                    权限菜单
        │   ├── systemServ.js                                                  菜单与字典管理
        │   └── userServ.js                                                    登录、注册和验证码
        ├── stores/
        │   ├── app.js                                                         登录信息和菜单状态
        │   └── index.js                                                       Pinia 初始化
        ├── styles/
        │   ├── game.css                                                       游戏和账户表格布局
        │   ├── auth.css                                                       登录和注册页面
        │   ├── home.css                                                       首页概览、统计和活动图布局
        │   ├── initial.css                                                    脚本加载前的背景颜色
        │   ├── motion.css                                                     路由、刷新、弹窗和控件过渡
        │   ├── platform.css                                                   公共控件、表单和系统页面
        │   ├── support.css                                                    客服会话布局
        │   ├── pet.css                                                        宠物与浮窗布局
        │   ├── workspace.css                                                  顶栏、左侧菜单、标签和状态栏
        │   └── element/
        │       ├── dark.scss
        │       └── index.scss
        ├── tools/
        │   ├── messageBox.js                                                  提示和确认框
        │   ├── markdown.js                                                    Markdown 解析、危险内容清理和链接处理
        │   └── motion.js                                                      刷新反馈和动画清理
        └── views/                                                             功能页面
            ├── MainPage.vue                                                   游戏概览与账户入口，复用游戏和记录接口
            ├── AiSupport.vue                                                  AI 客服会话页面
            ├── account/
            │   ├── AccountManage.vue                                          管理中心分类入口
            │   ├── RecordManage.vue                                           管理员游戏分数和记录
            │   ├── GameUserManage.vue                                         用户管理
            │   └── GameUserProfile.vue                                        个人资料
            ├── game/
            │   ├── GameList.vue                                               游戏列表、游戏库和收藏共用页面
            │   └── GameRecords.vue                                            游玩记录
            ├── info/
            │   └── Password.vue                                               修改密码
            └── system/
                ├── DictionaryManage.vue                                       字典管理
                └── MenuManage.vue                                             菜单管理
```

## 本机文件与构建产物

`database/backups/` 保存历史迁移前的完整备份，`web-db.sql` 保存完整数据库导出。Git 忽略这些数据文件。

启动程序将日志写入根目录 `logs/`。前端依赖安装在 `web-vue/node_modules/`，Vite 将网页构建到后端 `src/main/resources/public/`，Maven 将编译和打包结果写入后端 `target/`。这些目录由工具生成。
