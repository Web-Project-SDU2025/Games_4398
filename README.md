# 4398 游戏管理网站

4398 是一个使用 Spring Boot、Vue 和 MySQL 开发的游戏管理网站。所有用户都可以在此游戏、整理自己的游戏库和收藏、记录游玩情况，也可以通过 AI 客服了解游戏玩法和网站功能。管理员负责维护账户、游戏资料和系统菜单。

[![游戏概览](docs/images/dashboard.png)](docs/images/dashboard.png)

## 更新日志

### v1.1（2026/11/04）

cga！接入了 AI 客服功能（基于百炼应用）。

增加登录页背景动态效果。

增加设置界面。

手动合并或拆分了部分代码，优化了部分变量名，优化了项目结构，使代码更易维护。

### v1.0（2026/11/04 及之前）

设计完成前端基础样式与部分后端功能，数据库访问测试通过。接下来，团队将继续讨论部分功能的取舍，并完善页面交互和业务流程。

## 项目概况

本项目采用前后端分离结构。Vue + Js 负责页面和交互，Spring Boot 处理业务请求与身份验证，MySQL 保存账户、游戏和游玩记录。

用户和管理员均通过同一入口登录，网站根据账户角色显示相应功能。首页汇总游戏库、收藏和近期活动，AI 客服与右下角宠物提供游戏相关的问答。

所有登录用户都可以从顶栏齿轮或侧栏打开界面设置，调整鼠标尾迹和登录背景动画。浏览器会保存这些偏好。

## 界面预览

以下截图展示登录、注册、首页和界面设置。首页与设置截图使用示例账户，游戏和记录展示为空数据状态。

| 登录页面 | 注册页面 |
| :---: | :---: |
| [![登录页面](docs/images/login.png)](docs/images/login.png) | [![注册页面](docs/images/register.png)](docs/images/register.png) |
| **游戏概览** | **界面设置** |
| [![游戏概览](docs/images/dashboard.png)](docs/images/dashboard.png) | [![界面设置](docs/images/settings.png)](docs/images/settings.png) |

## 项目文档

- [项目结构与文件职责](docs/project-structure.md)
- [数据库说明](database/README.md)
- [前端说明](web-vue/README.md)
- [界面设计与参考来源](design.md)

## 开发与维护

项目目前处于本地开发阶段。团队按页面、业务逻辑、接口请求和样式分别维护前端代码，后端按控制器、服务和数据访问职责组织代码。

开发者完成修改后，通过代码风格检查、相关功能测试和前端构建验证结果。前端检查命令为 `npm run lint`、`npm test` 和 `npm run build`；Java 代码风格检查入口为 `scripts/CheckJavaStyle.java`。
