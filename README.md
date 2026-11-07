# 4398 游戏管理网站

4398 是一个使用 Spring Boot、Vue 和 MySQL 搭建的游戏管理网站，支持 Java 游戏与原生网页游戏。

用户登录后，可以使用字块币（Blockcoin）购买和游玩游戏、管理游戏收藏与游玩记录，也可以向 AI 客服咨询游戏玩法和网站功能。

管理员可以管理账户和游戏资料、调整字块币，并查看游戏记录。

[![游戏概览](docs/images/dashboard.png)](docs/images/dashboard.png)

## 更新日志

### v1.12.5（2026/11/07）

美式八球在分出胜负后统一结算本盘成就与获胜奖励，只生成一笔字块币流水和一条到账通知。对局中仍会显示成就解锁提示，但不逐项入账。中途重开或退出的未完成对局不结算，奖励金额保持不变。

cga！美式八球新增单人对抗电脑模式（本地双人模式仍然保留），每盘由服务器随机确定先手。电脑会选择进球路线、调整力度并摆放自由球，瞄准和力度带有小幅误差。

电脑选杆在后台线程运行，思考期间隐藏上一杆的球杆方向。瞄准线、球杆和实际击球共用最终方向，普通瞄准和力度误差略微增大。

贴库时使用较小的鼠标死区，预览增加距离与近切线方向容差，减少方向不更新和交点跳动。

增加六项美式八球成就奖励，分别为 1、4、6、8、11、11 字块币，单人获胜奖励为 20 字块币。

### v1.11.3（2026/11/07）

cga！成功利用 Canvas 原生网页接入第二款游戏：美式八球。

植物大战僵尸沿用原 Java 运行方式。

### v1.9.8（2026/11/07）

cga！消息中心新增问题反馈，所有账户都可以向管理员提交反馈，管理员可以设置修复状态并回复。

公告、反馈和回复支持 Markdown，公告与反馈也可以在发布后编辑。

用户可以从顶栏直接提交反馈。

平台和游戏页面使用统一的定制鼠标指针样式。

[阅读更早的更新记录](docs/update-history.md)

## 项目概况

本项目采用前后端分离结构。Vue 和 JavaScript 负责页面与交互，Spring Boot 处理业务请求和身份验证，MySQL 保存账户、游戏、字块币流水和游玩记录。植物大战僵尸及关卡编辑器通过 CheerpJ 运行，美式八球使用 Canvas 和 JavaScript 直接运行。

用户和管理员从同一入口登录，平台按账户角色显示功能。首页汇总游戏库、收藏和近期活动，AI 客服 Octorange 与宠物可以回答游戏相关问题。

登录用户可以从顶栏或侧栏打开界面设置，调整鼠标尾迹、登录背景动画和音乐。浏览器会保存这些偏好。

## 界面预览

下列截图来自本地 Chrome 的实际运行页面。首页中的邮箱和电话已遮盖。

| 登录页面 | 注册页面 |
| :---: | :---: |
| [![登录页面](docs/images/login.png)](docs/images/login.png) | [![注册页面](docs/images/register.png)](docs/images/register.png) |
| **游戏概览** | **游戏商城** |
| [![游戏概览](docs/images/dashboard.png)](docs/images/dashboard.png) | [![游戏商城](docs/images/store.png)](docs/images/store.png) |
| **我的游戏库** | **游戏运行** |
| [![我的游戏库](docs/images/library.png)](docs/images/library.png) | [![游戏运行](docs/images/game.png)](docs/images/game.png) |
| **关卡编辑器** | **消息中心** |
| [![关卡编辑器](docs/images/editor.png)](docs/images/editor.png) | [![消息中心](docs/images/messages.png)](docs/images/messages.png) |
| **问题反馈** | **界面设置** |
| [![问题反馈](docs/images/feedback.png)](docs/images/feedback.png) | [![界面设置](docs/images/settings.png)](docs/images/settings.png) |
| **登录背景设置** | |
| [![登录背景设置](docs/images/login-settings.png)](docs/images/login-settings.png) | |

## 其余项目文档

- [项目结构与文件职责](docs/project-structure.md)
- [数据库说明](database/README.md)
- [前端说明](web-vue/README.md)
- [游戏接入说明](docs/game-integration.md)
- [字块币与游戏商城](docs/blockcoin.md)
