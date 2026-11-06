# 4398 前端

本项目前端主要使用 Vue 3 和 JavaScript。表格与表单采用原生控件，确认弹窗复用 Element Plus。

## 安装依赖

```bash
npm install
```

## 本地开发

开发者先在 IDEA 中启动端口为 `22222` 的后端，再运行前端。开发页面使用端口 `3000`。

```bash
npm run dev
```

## 检查和打包

```bash
npm run lint
npm run lint:comments
npm test
npm run games:test
npm run build
```

`lint` 检查基础写法、函数说明和注释结尾，测试使用内存数据。构建工具将发布文件写入后端的 `src/main/resources/public`，后端打包时会将这些文件一并放入 JAR。

## 文件职责

- `src/logic` 保存账号检查、表单复制、菜单和客服资料处理
- `src/logic/limits.js` 集中维护表单字段上限，页面共用这些参数
- `src/services` 负责后端请求，写入成功后标记相关缓存过期
- `src/components` 保存工作区、账号切换、共用聊天窗口和 Octorange 宠物
- `src/views` 负责页面布局、绑定与事件，`src/styles` 按页面和控件用途维护样式
- 公共颜色和间距放在 `src/styles/tokens.css`
- `games/` 保存浏览器游戏的 Java 源码、素材和单份配置，`games/runtime/web/` 保存启动页、样式与共用浏览器模块
- `public/games/` 由构建工具生成，修改运行器时维护对应源码目录

开发者通过 `npm run games:import -- <游戏包目录>` 导入新游戏，再执行 `npm run games:build`。源码工程和预编译 JAR 使用同一套接入配置，具体字段见[游戏接入说明](../docs/game-integration.md)。

页面通过 Vue 过渡切换。列表会保留已加载的数据，并在数据变化、手动查询或失败重试时重新读取。

AI 客服与宠物通过 `src/services/aiServ.js` 调用 Java 后端的百炼接口，密钥由后端保存。前端按账号保存对话历史，用户可以停止等待或重试失败的问题，回答内容由接口返回。

客服和宠物共用多图输入。用户可选择、粘贴或拖入最多四张 PNG、JPEG、WebP 图片，浏览器会先缩小和压缩图片，历史消息只保存缩略图。后端通过独立图片入口调用百炼应用的视觉模型，并沿用现有调用额度和并发限制。图片会话默认选择 `qwen3-vl-plus`，后端可通过 `app.ai.image-model` 调整模型；应用账号需要具备对应模型权限。

两处聊天共用 `src/tools/markdown.js` 渲染 Markdown 回复：`marked` 负责解析，`DOMPurify` 负责清理内容。用户问题以纯文本显示，复制回复时保留原 Markdown，表格和代码块可以在各自区域内滚动。

消息中心支持账户通知、全站公告和问题反馈。管理员可以设置反馈是否修复并回复提交者；公告、反馈和回复正文共用 Markdown 渲染。

`src/services/genServ.js` 统一发送请求，并在写入成功后更新缓存状态；`src/services/systemServ.js` 处理导航菜单和修改密码请求。管理员通过三个独立页面管理账户、字块币收支和游戏记录，收支页与记录页共用 `src/services/adminServ.js`。构建配置位于 `vite.config.js`，组件样式由本地 CSS 文件维护。
