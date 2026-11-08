# 项目结构

[返回项目说明](../README.md)

后端工程位于 `java-server/java-server`，前端工程位于 `web-vue`。下方目录树展示各文件和目录的职责。

```text
4398/
├── .gitignore
├── .gitattributes
├── README.md
├── docs/
│   ├── game-integration.md                                                     游戏接入与关卡文件说明
│   ├── blockcoin.md                                                            字块币、购买与通关奖励
│   ├── images/                                                                 项目界面截图
│   └── project-structure.md                                                    项目结构与文件职责
├── start.cmd                                                                   双击启动本地前后端
├── package.cmd                                                                 构建教师用完整运行 ZIP
├── packaging/                                                                  教师启动器、配置检查和运行说明模板
├── scripts/
│   ├── style-scope.json                                                        全局检查范围与生成文件清单
│   ├── preserved-files.txt                                                     原服务器连接文件保留清单
│   ├── CheckJavaStyle.java                                                     后端源码与测试风格检查
│   ├── package.ps1                                                            构建网页、游戏、后端并组合 Java 运行环境
│   ├── Test-Delivery.ps1                                                       在独立端口验证交付包及真实 AI
│   ├── source-files.cjs                                                        语法和注释检查共用的源码收集
│   └── start.ps1                                                               环境检查、端口检查和进程管理
├── database/                                                                   数据库结构与迁移说明
│   ├── README.md
│   ├── schema.sql                                                              当前数据库表结构
│   ├── seed.sql                                                                角色和菜单初始化数据
│   ├── migrate_game.sql                                                        历史游戏业务迁移脚本
│   ├── merge_user.sql                                                          历史账户合并脚本
│   └── finish_user_merge.sql                                                   账户合并后的旧表清理脚本
├── java-server/
│   └── java-server/                                                            后端工程，以 pom.xml 为入口
│       ├── .gitattributes
│       ├── .gitignore
│       ├── .mvn/
│       │   └── wrapper/
│       │       └── maven-wrapper.properties
│       ├── mvnw.cmd                                                            后端依赖与构建命令入口
│       ├── pom.xml                                                             依赖、JDK 版本和打包配置
│       └── src/
│           ├── main/
│           │   ├── java/
│           │   │   └── cn/
│           │   │       └── edu/
│           │   │           └── sdu/
│           │   │               └── java/
│           │   │                   └── server/
│           │   │                       ├── JavaServerApplication.java          后端启动入口
│           │   │                       ├── SystemApplicationListener.java      启动后加载系统数据
│           │   │                       ├── configs/                            认证与权限配置
│           │   │                       │   ├── AiRequestFilter.java            智能问答入口频率与请求大小检查
│           │   │                       │   ├── GameFrameHeaders.java           游戏启动页同源嵌入规则
│           │   │                       │   ├── BlockcoinSetup.java             增量记账表与首个商品初始化
│           │   │                       │   ├── ApplicationConfiguration.java
│           │   │                       │   ├── JwtAuthenticationFilter.java
│           │   │                       │   └── SecurityConfiguration.java
│           │   │                       ├── controllers/                        接收前端请求
│           │   │                       │   ├── AuthController.java
│           │   │                       │   ├── AdminRecordController.java      管理员分数和记录接口
│           │   │                       │   ├── AdminBlockcoinController.java   管理员收支查询与资金调整接口
│           │   │                       │   ├── AdminGameLibraryController.java 管理员查看与撤回游戏接口
│           │   │                       │   ├── MessageController.java          消息读取与已读接口
│           │   │                       │   ├── AnnouncementController.java     管理员全站公告接口
│           │   │                       │   ├── MessagePageController.java      消息中心页面转发
│           │   │                       │   ├── AiController.java               百炼客服与宠物共用接口
│           │   │                       │   ├── AiImageController.java          多图问题与图片会话接口
│           │   │                       │   ├── BaseController.java
│           │   │                       │   ├── GameController.java
│           │   │                       │   ├── BlockcoinController.java        商城、购买与通关结算接口
│           │   │                       │   ├── GameplayController.java         游戏开局、心跳与结束接口
│           │   │                       │   ├── GameUserController.java
│           │   │                       │   ├── LocalAccountController.java     本机检查切号
│           │   │                       │   ├── PageController.java             前端地址转发
│           │   │                       │   └── StatisticsController.java
│           │   │                       ├── models/                             数据库实体
│           │   │                       │   ├── DictionaryInfo.java
│           │   │                       │   ├── Game.java
│           │   │                       │   ├── GameRecord.java
│           │   │                       │   ├── BlockcoinWallet.java            账户字块币余额
│           │   │                       │   ├── BlockcoinEntry.java             唯一业务流水
│           │   │                       │   ├── GamePlay.java                   通用游戏会话与关卡结果
│           │   │                       │   ├── GameRevocation.java             游戏撤回与重新购买记录
│           │   │                       │   ├── RecordVisibility.java           当前查看账户的记录隐藏选择
│           │   │                       │   ├── SiteMessage.java                通知、公告和问题反馈正文
│           │   │                       │   ├── FeedbackResolution.java         反馈处理决定、回复与时间
│           │   │                       │   ├── MessageReceipt.java             每账户单独的已读状态
│           │   │                       │   ├── MenuInfo.java
│           │   │                       │   ├── ModifyLog.java
│           │   │                       │   ├── RequestLog.java
│           │   │                       │   ├── StatisticsDay.java
│           │   │                       │   ├── SystemInfo.java
│           │   │                       │   ├── User.java
│           │   │                       │   └── UserType.java
│           │   │                       ├── payload/                            请求与返回数据结构
│           │   │                       │   ├── request/
│           │   │                       │   │   ├── DataRequest.java
│           │   │                       │   │   └── LoginRequest.java
│           │   │                       │   └── response/
│           │   │                       │       ├── DataResponse.java
│           │   │                       │       ├── JwtResponse.java
│           │   │                       │       ├── MyTreeNode.java
│           │   │                       │       ├── OptionItem.java
│           │   │                       │       └── OptionItemList.java
│           │   │                       ├── repositorys/                        数据库读写接口
│           │   │                       │   ├── DictionaryInfoRepository.java
│           │   │                       │   ├── GameRecordRepository.java
│           │   │                       │   ├── GameRepository.java
│           │   │                       │   ├── BlockcoinWalletRepository.java  字块币余额读写
│           │   │                       │   ├── BlockcoinEntryRepository.java   业务流水与收支查询
│           │   │                       │   ├── GamePlayRepository.java         会话读取与并发频率统计
│           │   │                       │   ├── GameRevocationRepository.java   有效撤回判断与重新购买标记
│           │   │                       │   ├── RecordVisibilityRepository.java 记录隐藏选择的批量查询
│           │   │                       │   ├── SiteMessageRepository.java      消息列表、未读数量与发布频率查询
│           │   │                       │   ├── FeedbackResolutionRepository.java
│           │   │                       │   ├── MessageReceiptRepository.java   当前页已读回执读取
│           │   │                       │   ├── MenuInfoRepository.java
│           │   │                       │   ├── StatisticsDayRepository.java
│           │   │                       │   ├── SystemInfoRepository.java
│           │   │                       │   ├── UserRepository.java
│           │   │                       │   └── UserTypeRepository.java
│           │   │                       ├── services/                           业务处理
│           │   │                       │   ├── AuthService.java
│           │   │                       │   ├── AdminRecordService.java         管理员记录增删改查
│           │   │                       │   ├── AdminBlockcoinService.java      管理员流水筛选与资金汇总
│           │   │                       │   ├── AdminGameLibraryService.java    目标游戏库查询、撤回与会话结束
│           │   │                       │   ├── RecordVisibilityService.java    记录隐藏、权限与统计数据保留
│           │   │                       │   ├── MessageService.java             通知、公告、反馈、回复和已读处理
│           │   │                       │   ├── AiService.java                  百炼请求、游戏范围提示和会话隔离
│           │   │                       │   ├── AiImageService.java             多图校验、视觉模型与图片上下文
│           │   │                       │   ├── AiRequestGuard.java             并发、调用频率与每日用量
│           │   │                       │   ├── BaseService.java
│           │   │                       │   ├── GameService.java
│           │   │                       │   ├── BlockcoinService.java           购买、资金调整、持有与奖励事务
│           │   │                       │   ├── GameplayService.java            时长、关卡结果与原子结算
│           │   │                       │   ├── OfficialLevelCatalog.java       官方关卡指纹与时长检查
│           │   │                       │   ├── GameUserService.java
│           │   │                       │   ├── JwtService.java
│           │   │                       │   ├── StatisticsService.java
│           │   │                       │   ├── SystemService.java
│           │   │                       │   ├── UserDetailsImpl.java
│           │   │                       │   └── UserDetailsServiceImpl.java
│           │   │                       ├── uml/
│           │   │                       │   └── game.puml                       实体关系图
│           │   │                       └── util/                               公共工具
│           │   │                           ├── ComDataUtil.java
│           │   │                           ├── CommonMethod.java
│           │   │                           ├── DateTimeTool.java
│           │   │                           ├── FieldLimits.java                实体与业务校验共用的字段上限
│           │   │                           ├── BlockcoinRules.java             初始余额、商品价格和奖励参数
│           │   │                           ├── GameplayRules.java              会话计时、并发与过期参数
│           │   │                           ├── AiImageRules.java               图片数量、体积、尺寸与会话参数
│           │   │                           ├── AdminQueryRules.java            管理列表分页、搜索和日期范围
│           │   │                           ├── MessageRules.java               公告长度、频率和回执批次参数
│           │   │                           └── LoginControlUtil.java
│           │   └── resources/
│           │       ├── application.yml                                         后端公开配置
│           │       ├── blockcoin-schema.sql                                    字块币增量表初始化
│           │       ├── application-local.properties                            本机密码与密钥
│           │       ├── application-local.properties.example                    本机配置模板
│           │       ├── loggback.xml                                            日志配置
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
│                                       ├── AiTests.java                        百炼接口与会话测试
│                                       ├── AiImageTests.java                   多图协议、图片校验和额度测试
│                                       ├── AiProtectionTests.java              智能问答限额与入口保护测试
│                                       ├── GameMigrationTests.java
│                                       ├── CoreUtilityTests.java               令牌、验证码和空参数测试
│                                       ├── JavaServerApplicationTests.java
│                                       ├── ManagementTests.java                管理中心权限与记录测试
│                                       ├── GameplayTests.java                  通用会话与自动记录测试
│                                       ├── AdminBlockcoinTests.java            收支筛选、分页与权限测试
│                                       ├── AdminGameLibraryTests.java          游戏撤回、操作重试与归属测试
│                                       ├── BlockcoinTests.java                 购买、资金调整与余额边界测试
│                                       ├── RecordVisibilityTests.java          隐藏权限、统计保留与旧地址测试
│                                       ├── MessageTests.java                   消息范围、反馈处理、已读与权限测试
│                                       └── UserMergeTests.java
└── web-vue/                                                                    前端工程
    ├── .gitignore
    ├── README.md
    ├── index.html                                                              网页入口
    ├── package.json                                                            依赖和运行命令
    ├── package-lock.json                                                       依赖版本记录
    ├── tsconfig.json
    ├── vite.config.js                                                          本地代理和构建路径
    ├── games/                                                                  浏览器游戏源码与运行素材
    │   ├── registry.json                                                       唯一源码游戏清单
    │   ├── runtime/                                                            共用浏览器运行器
    │   │   ├── src/                                                            素材安装、数据交换及普通入口
    │   │   └── web/                                                            浏览器运行模块
    │   │       ├── game-display.mjs                                            游戏窗口居中与署名定位
    │   │       ├── game-manifest.mjs                                           构建与页面共用的配置校验
    │   │       ├── game-registry.mjs                                           平台与启动器共用配置读取
    │   │       ├── game-resources.mjs                                          后台完整预载与资源完整性检查
    │   │       ├── game-session.mjs                                            账户目录与窗口消息约定
    │   │       ├── game-economy.mjs                                            运行授权与通关结算请求
    │   │       ├── game-play.mjs                                               通用事件、计时、重试与结束保存
    │   │       ├── java-player.html                                            共用游戏运行页源码
    │   │       ├── java-player.css                                             游戏画布与编辑器窗口样式
    │   │       ├── java-player.js                                              自动加载与编辑器交互
    │   │       ├── native-player.html                                          原生游戏运行页
    │   │       ├── native-player.js                                            原生游戏授权、就绪与关闭
    │   │       └── native-player.css                                           原生游戏运行区样式
    │   ├── pvz/                                                                植物大战僵尸
    │   │   ├── game.json                                                       游戏资料、能力与构建参数
    │   │   ├── src/                                                            游戏、编辑器、自检及适配器，编辑器外观由 EditorAppearance 维护
    │   │   ├── assets/                                                         原图片、动画和关卡
    │   │   ├── lib/                                                            原依赖及第三方许可
    │   │   ├── LICENSE                                                         原代码许可
    │   │   └── UPSTREAM.md                                                     原项目说明
    │   └── billiards/                                                          美式八球
    │       ├── game.json                                                       台球资料、浏览器入口与构建参数
    │       ├── web/                                                            当前 Canvas 游戏源码
    │       │   ├── assets/                                                     台球图像和音效
    │       │   ├── config.mjs                                                  尺寸、物理与交互参数
    │       │   ├── physics.mjs                                                 Matter 碰撞与台呢摩擦
    │       │   ├── rules.mjs                                                   对局、判罚和六项成就
    │       │   ├── render.mjs                                                  Canvas 绘制
    │       │   ├── aim.mjs                                                     瞄准预览与力度
    │       │   ├── ai.mjs                                                      电脑选球、摆位、物理预演与小幅误差
    │       │   ├── ai-worker.mjs                                               后台选杆线程入口
    │       │   ├── computer.mjs                                                电脑回合、等待、后台计算与取消
    │       │   ├── hud.mjs                                                     比赛文字、球组托盘与开场浮层
    │       │   ├── stroke.mjs                                                  瞄准、蓄力、出杆与触球动作
    │       │   ├── assets.mjs                                                  图像加载与浏览器音频
    │       │   ├── main.mjs                                                    输入、平台消息与帧循环
    │       │   ├── index.html                                                  游戏画布与操作区
    │       │   └── style.css                                                   游戏布局与提示样式
    │       ├── tests/                                                          台球规则、物理、渲染与电脑控制测试
    │       ├── media/table.png                                                 实际游戏画面封面
    │       ├── AUDIO-SOURCE.txt                                                课程音效来源
    │       ├── MATTER-LICENSE.txt                                              Matter.js 许可
    │       └── UPSTREAM.md                                                     原仓库和迁入内容说明
    ├── public/                                                                 静态图片和网站图标
    │   ├── audio/                                                              背景音乐音轨
    │   ├── cursors/                                                            鼠标箭头和点击手形
    │   ├── icons/                                                              音符图标与来源许可
    │   ├── games/                                                              构建生成的注册表、启动页和游戏包
    │   ├── favicon.ico
    │   ├── favicon.svg
    │   └── images/
    │       ├── game-collection.png                                             游戏库空状态插画
    │       ├── shandong-university.png                                         用户提供的山东大学校标
    │       └── pet/
    │           ├── idling.png                                                  宠物待机图片
    │           ├── hovering.png                                                宠物悬停图片
    │           └── thinking.png                                                宠物思考图片
    ├── scripts/
    │   ├── build-java-games.mjs                                                编译游戏并生成发布配置
    │   ├── build-native-game.mjs                                               打包原生网页游戏与来源说明
    │   ├── clean-web-output.cjs                                                按构建清单清理旧指纹资源
    │   ├── import-game.mjs                                                     通用游戏包导入命令
    │   ├── game-project.mjs                                                    共用路径、工程及工具校验
    │   ├── comment-audit.cjs                                                   全局注释与豁免边界检查
    │   └── check-style.cjs                                                     代码风格检查
    ├── tests/
    │   ├── admin-assets.test.mjs                                               资金调整、游戏撤回与切号测试
    │   ├── ai.test.mjs                                                         客服与宠物交互测试
    │   ├── dashboard.test.mjs                                                  首页统计与日期测试
    │   ├── game-import.test.mjs                                                源码及预编译包导入检查
    │   ├── game-runtime.test.mjs                                               游戏能力与入口参数检查
    │   ├── game-play.test.mjs                                                  自动计时、重试与关卡结果检查
    │   ├── game-registry.test.mjs                                              注册表路径与读取边界检查
    │   ├── game-session.test.mjs                                               窗口消息和账户目录检查
    │   ├── messages.test.mjs                                                   消息筛选、阅读与公告发布测试
    │   ├── game-display.test.mjs                                               游戏与编辑器窗口范围检查
    │   ├── game-resources.test.mjs                                             资源完整性与失败回收检查
    │   ├── music.test.mjs                                                      音乐状态与播放请求检查
    │   ├── logic.test.mjs                                                      表单、导航和刷新逻辑测试
    │   ├── request.test.mjs                                                    写入结果与切号缓存测试
    │   ├── preferences.test.mjs                                                界面偏好校验与保存测试
    │   └── session.test.mjs                                                    登录身份与权限测试
    └── src/
        ├── main.js                                                             前端启动入口
        ├── App.vue                                                             根组件
        ├── Login.vue                                                           验证码注册与登录页面
        ├── env.d.ts
        ├── components.d.ts
        ├── components/
        │   ├── Navi.vue                                                        顶部导航、左侧菜单和页面显示区域
        │   ├── BackgroundMusic.vue                                             共用音乐播放器与快捷开关
        │   ├── GameLauncher.vue                                                游戏资料、启动与收藏条目
        │   ├── GamePlayer.vue                                                  跨页面保留的游戏窗口与消息处理
        │   ├── MessageBell.vue                                                 顶栏未读数量与消息入口
        │   ├── MessageComposer.vue                                              公告和反馈的新增、编辑与提交
        │   ├── BlockcoinAdjustment.vue                                         管理员发放与收回字块币弹窗
        │   ├── BlockcoinHistory.vue                                            当前账户的字块币收支足迹
        │   ├── AccountGameLibrary.vue                                          目标账户游戏库与撤回确认弹窗
        │   ├── DashboardActivity.vue                                           最近七天游玩活动图
        │   ├── DashboardAccount.vue                                            首页账户资料、签名与字块币
        │   ├── Octorange.vue                                                   宠物位置、状态和浮窗
        │   ├── SupportChat.vue                                                 客服与宠物共用的聊天窗口
        │   ├── InterfaceSettings.vue                                           各角色共用的界面设置窗口
        │   ├── PointerTrail.vue                                                鼠标位置采样与尾迹清理
        │   └── AccountSwitcher.vue                                             本机检查账号切换与异常提示
        ├── logic/                                                              普通 JS 处理，不操作页面元素
        │   ├── account.js                                                      账号显示、密码校验和请求资料
        │   ├── admin.js                                                        管理导航顺序、筛选和分页参数
        │   ├── messages.js                                                     消息类型、分页上限和错误提示
        │   ├── auth.js                                                         登录表单、验证码和注册流程
        │   ├── dashboard.js                                                    首页统计、记录排序和北京时间处理
        │   ├── forms.js                                                        游戏、账户与记录的独立编辑表单
        │   ├── gameRuntime.js                                                  游戏配置校验与入口地址
        │   ├── limits.js                                                       共用表单字段长度与数字上限
        │   ├── navigation.js                                                   菜单查找、标签和时钟格式
        │   ├── pet.js                                                          宠物显示设置和位置边界
        │   ├── preferences.js                                                  界面偏好默认值、范围和存储
        │   ├── records.js                                                      个人、管理员和首页共用结果描述
        │   └── support.js                                                      会话整理和按账号存储历史
        ├── router/
        │   └── index.js                                                        页面地址与组件的对应关系
        ├── services/                                                           前端请求函数
        │   ├── gameServ.js                                                     游戏、游戏库、收藏和记录
        │   ├── storeServ.js                                                    商城、余额与购买请求
        │   ├── gameRuntime.js                                                  读取站内游戏注册表
        │   ├── adminServ.js                                                    管理员收支、资金调整与游戏库请求
        │   ├── messageServ.js                                                  消息分页、已读和公告请求
        │   ├── aiServ.js                                                       百炼请求、超时和停止等待
        │   ├── gameUserServ.js                                                 账户管理与个人资料
        │   ├── genServ.js                                                      通用请求和登录凭证
        │   ├── systemServ.js                                                   导航菜单与修改密码
        │   └── userServ.js                                                     登录、注册和验证码
        ├── stores/
        │   ├── app.js                                                          登录信息和菜单状态
        │   ├── preferences.js                                                  跨页面共享界面偏好
        │   ├── music.js                                                        当前音轨进度与设置跳转控制
        │   ├── gamePlayer.js                                                   当前游戏入口、账户与显示状态
        │   ├── messages.js                                                     当前账户的未读数量
        │   ├── wallet.js                                                       账户隔离的字块币与商品状态
        │   └── index.js                                                        状态仓库初始化
        ├── styles/
        │   ├── tokens.css                                                      公共颜色、间距和动效参数
        │   ├── cursor.css                                                      平台和游戏共用指针与滚动条
        │   ├── game.css                                                        游戏和账户表格布局
        │   ├── game-runtime.css                                                游戏入口与运行窗口布局
        │   ├── store.css                                                       商城、商品和字块币账户布局
        │   ├── blockcoin-history.css                                           字块币足迹和收支箭头样式
        │   ├── messages.css                                                    消息收件箱和公告编辑布局
        │   ├── auth.css                                                        登录和注册页面
        │   ├── home.css                                                        首页概览、统计和活动图布局
        │   ├── initial.css                                                     脚本加载前的背景颜色
        │   ├── motion.css                                                      路由、刷新、弹窗和控件过渡
        │   ├── music.css                                                       音乐图标状态与登录页位置
        │   ├── platform.css                                                    公共控件、表单和系统页面
        │   ├── support.css                                                     客服会话布局
        │   ├── pet.css                                                         宠物与浮窗布局
        │   ├── settings.css                                                    设置窗口与尾迹外观
        │   └── workspace.css                                                   顶栏、左侧菜单、标签和状态栏
        ├── tools/
        │   ├── messageBox.js                                                   提示和确认框
        │   ├── markdown.js                                                     消息格式解析、内容清理和链接处理
        │   ├── chatImages.js                                                   图片读取、缩放、压缩与缩略图
        │   ├── music.js                                                        音轨启停、进度跳转与音量控制
        │   └── motion.js                                                       刷新反馈和动画清理
        └── views/                                                              功能页面
            ├── MainPage.vue                                                    游戏概览与账户入口，复用游戏和记录接口
            ├── AiSupport.vue                                                   智能客服会话页面
            ├── Messages.vue                                                    账户消息收件箱和未读筛选
            ├── account/
            │   ├── AccountManage.vue                                           账户资料、余额与登录信息管理
            │   ├── BlockcoinManage.vue                                         账户收支筛选与流水分页
            │   ├── RecordManage.vue                                            管理员游戏记录与分数
            │   └── GameUserProfile.vue                                         个人资料
            ├── game/
            │   ├── GameStore.vue                                               游戏商城、购买与游戏资料维护
            │   ├── GameList.vue                                                游戏库、收藏与字块币足迹
            │   └── GameRecords.vue                                             游玩记录
            └── info/
                └── Password.vue                                                修改密码
```
