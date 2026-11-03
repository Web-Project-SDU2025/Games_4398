# 4398 前端

前端使用 Vue 3 和普通 JavaScript，表格与表单使用原生控件，树形组件和确认弹窗复用 Element Plus

## 安装依赖

```bash
npm install
```

## 本地开发

先启动端口为 `22222` 的后端，再运行前端。开发页面使用端口 `3000`

```bash
npm run dev
```

## 检查和打包

```bash
npm run lint
npm test
npm run build
```

`lint` 检查基础写法、函数说明和注释结尾，测试使用内存数据。构建结果进入后端的 `src/main/resources/public`，重新打包 JAR 后生效

## 文件职责

- `src/logic` 保存账号检查、表单复制、菜单和客服资料处理
- `src/services` 负责后端请求，写入成功后标记相关缓存过期
- `src/components` 保存工作区、账号切换、共用聊天窗口和 Octorange 宠物
- `src/views` 负责页面布局、绑定与事件，`src/styles` 按页面和控件用途维护样式
- 公共颜色和间距放在项目根目录的 `tokens.css`

页面切换只使用 Vue 过渡，不要再叠加第二套 CSS 入场动画。已加载的列表在数据变化后才重新读取，手动查询、异常提示和失败重试仍保留

AI 客服与宠物通过 `src/services/aiServ.js` 调用 Java 后端的百炼接口。密钥只配置在后端；前端保留按账号区分的历史，支持停止等待与失败重试，不使用本地固定问答

两处聊天共用 `src/tools/markdown.js` 渲染 Markdown 回复，使用 `marked` 解析、`DOMPurify` 清理内容。用户问题保持纯文本，复制回复保留原来的 Markdown，表格和代码块在各自区域内滚动
