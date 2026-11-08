# 4398 前端

本项目前端使用 Vue 3 和 JavaScript。页面以原生表格和表单为主，确认弹窗使用 Element Plus。

## 安装依赖

```bash
npm install
```

## 本地开发

开发者在 IDEA 中启动后端后，再运行前端。前端开发服务使用端口 `3000`，并通过 `22222` 访问后端。

```bash
npm run games:build
npm run dev
```

首次下载先构建游戏资源，再启动 IDEA 后端，最后运行前端。网页通过 `localhost` 或 `127.0.0.1` 访问时，登录后默认可以在右上角切换已有账户。

## 检查和打包

```bash
npm run lint
npm run lint:comments
npm test
npm run games:test
npm run build
```

`lint` 检查基础写法和注释规范，测试使用内存数据。构建工具将前端文件写入后端的 `src/main/resources/public`，后端打包时会把这些文件放入 JAR。

## 文件职责

- `src/logic` 保存账号、表单、菜单和客服等业务逻辑
- `src/services` 管理后端请求
- `src/components` 保存导航、设置、聊天和游戏等共用组件
- `src/views` 保存功能页面，`src/styles` 按用途维护样式
- `src/styles/tokens.css` 集中维护公共颜色和间距
- `games/` 保存浏览器游戏源码、素材和配置；`games/runtime/web/` 保存共用运行器
- `public/games/` 由构建工具生成

开发者通过 `npm run games:import -- <游戏包目录>` 导入新游戏，再执行 `npm run games:build`。源码工程和预编译 JAR 使用同一套接入配置，具体字段见[游戏接入说明](../docs/game-integration.md)。

当前清单包含植物大战僵尸和美式八球。植物大战僵尸使用 Java 运行器，台球的 Canvas 代码和素材维护在 `games/billiards/web/`，使用原生网页运行器。两种入口都核对购买权限、记录游玩时长，并在关闭时停止游戏任务。

八球的规则、物理、渲染与电脑控制测试集中在 `games/billiards/tests/`，`npm test` 同时运行平台与八球测试。平台共用的启动、计时和账户协议留在 `games/runtime/`，后端的奖励结算与数据库业务留在后端工程。

页面操作结果通过 `src/tools/toast.js` 统一显示，默认四秒后关闭，用户也可以手动关闭。`v-toast` 绑定原消息字段，提示不占用页面布局；原生对话框中的提示使用独立顶层，避免被表单裁剪。外观维护在 `src/styles/toast.css`。

Vue Router 负责页面切换，页面过渡沿用 Vue 动画。列表会在数据变化、用户查询或失败重试时重新读取。

AI 客服与宠物通过 `src/services/aiServ.js` 调用 Java 后端接口，密钥由后端保存。前端按账号保存对话历史，并支持停止等待或重试。

客服和宠物共用多图输入。浏览器会缩小并压缩图片，后端负责校验并调用视觉模型。

客服和宠物共用 `src/tools/markdown.js` 显示 Markdown 回复。`marked` 负责解析，`DOMPurify` 负责清理内容。

消息中心支持账户通知、全站公告和问题反馈。管理员可以设置反馈状态并回复，消息正文支持 Markdown。

`src/services/genServ.js` 统一发送请求，`src/services/systemServ.js` 处理导航菜单和密码修改。管理员分别管理账户、字块币收支和游戏记录。构建配置位于 `vite.config.js`，页面样式由 CSS 文件维护。
