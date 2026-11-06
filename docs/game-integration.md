# 游戏接入

[返回项目说明](../README.md)

## 目录归属

游戏在浏览器端运行，开发者将源码、素材和依赖统一放在 `web-vue/games/`。CheerpJ 负责运行 Java 代码，平台后端负责账户、游戏资料和记录。

每个游戏使用一份 `game.json`。构建工具读取这份配置，生成 `web-vue/public/games/<编号>/game.json`、JAR、素材包、封面和网页游戏清单。开发者在 `web-vue/games/runtime/web/` 维护共用启动页、样式和浏览器模块，在 `web-vue/src/styles/tokens.css` 维护主题参数；构建工具负责更新发布目录。

## 导入游戏包

开发者在 `web-vue` 目录执行以下命令：

```powershell
npm run games:import -- D:/Games/MyGame
npm run games:build
```

游戏包目录需要包含 `game.json`，开发者也可以单独指定配置文件：

```powershell
npm run games:import -- D:/Games/MyGame D:/Games/my-game.json
```

导入工具检查编号、路径、依赖和文件体积后，会复制配置声明的文件，并将编号加入 `web-vue/games/registry.json`。遇到已有编号时，工具会保留现有工程，开发者可在原游戏目录中继续维护。

## 普通 Java 入口

Swing 或 AWT 游戏提供 `public static void main(String[] arguments)` 后，即可使用普通入口：

```json
{
  "id": "puzzle",
  "name": "拼图",
  "runtime": "java17",
  "mainClass": "puzzle.Main",
  "classpath": ["game.jar"],
  "arguments": ["{assets}"],
  "cover": "assets/cover.png",
  "build": {
    "source": "src/main/java",
    "cover": "cover.png",
    "libraries": []
  }
}
```

`build.source` 指定源码目录。开发者使用已有 JAR 时，将这一项替换为 `"jar": "binary/original.jar"`。两种方式都通过共用 Java 运行器发布，游戏规则继续由 Java 代码实现。

开发者在 `build.libraries` 中填写依赖，并在 `classpath` 中依次列出 `game.jar` 和各依赖文件名。需要素材包时，通过 `build.assets` 指定素材目录，同时设置运行字段 `"assets": "assets.zip"`。

运行器将参数中的 `{assets}` 替换为当前账户的游戏素材目录，将 `{data}` 替换为该目录下的数据目录。需要读取相对路径的程序应通过参数接收素材目录。游戏若依赖本机原生库、外部进程或专用渲染方式，开发者需要提供相应适配。当前运行器支持 Java 17 字节码及其兼容版本。

## 可选能力

| 配置 | 用途 |
| :--- | :--- |
| `capabilities.editor` | 显示编辑器入口 |
| `editorMainClass`、`editorArguments` | 普通 Java 工程的编辑器类与参数 |
| `adapter` | 游戏专用浏览器适配器，使用后由它接收打开操作 |
| `capabilities.importData` | 显示数据导入，适配器负责业务校验 |
| `capabilities.exportData` | 显示数据导出，共用文件工具按配置目录打包 |
| `capabilities.gameplayEvents` | 适配器提供统一关卡事件，网页自动记时与结算 |
| `gameplay` | 服务器核对关卡使用的目录、文件前缀、奖励和时间字段 |
| `imports` | 导入编号范围、最大体积、文件类型和字段名称 |
| `dataDirectory` | 导出数据所在的相对目录，默认 `data` |
| `preservedPaths` | 素材升级时保留的用户目录，如 `levels`、`saves` |
| `display` | 虚拟桌面宽度与高度 |
| `build.licenses` | 发布时一并保留的许可文件 |
| `build.include` | 导入时同时复制的说明或附加文件 |

普通游戏可以省略上述能力，此时平台只显示开始入口。开发者开启数据导入后，需要由专用适配器提供校验方法，并在配置中指定文件编号范围和体积上限。

专用适配器采用以下约定：

| Java 方法 | 用途 |
| :--- | :--- |
| `public static void open(String directory, boolean editorMode)` | 打开游戏或编辑器 |
| `public static String importData(String directory, int number, String text)` | 验证并导入文本，成功返回空字符串，失败返回说明 |
| `public static void close()` | 结束窗口与计时器 |
| `public static String gameEvents()` | 返回 JSON 事件数组并清空本次已读取事件 |

关卡事件统一使用 `begin`、`heartbeat` 和 `finish`。每局游戏创建一个 UUID 作为 `runId`。开局事件包含零起始的 `level`、原关卡文件的 SHA-256 摘要 `hash`，以及 `official` 或 `practice` 模式。后续事件提交同一编号和累计 `activeMilliseconds`。结束事件再提供 `passed`、`failed`、`abandoned` 或 `closed` 结果。网页按顺序请求后端，并在重复请求时复用原编号和挑战码。

普通 main 入口可以省略关卡事件，此时网页统计可见运行时间，打开编辑器时暂停该入口的计时。游戏需要通关记录和奖励时，开发者实现 `gameEvents()` 并启用对应能力。奖励配置维护在游戏包中，例如当前 PVZ 使用 `reward`、`levelDirectory`、`levelPrefix`、`timeArray` 和 `timeField`；服务器只读取本地发布包，并核对原关卡摘要。

平台将运行窗口放在导航公共层。用户切换站内页面时，平台只隐藏画面，iframe 保持挂载，游戏仍按原规则运行。用户返回原页面或点击顶栏“返回游戏”后，可以继续使用同一实例。返回列表按钮也只隐藏画面，点击关闭游戏或退出账户时才结束实例。刷新浏览器会重新加载运行环境。

共用文件工具 `platform.runtime.GameFiles` 负责素材安装、指定目录导出和文本写入，具体文件格式由各游戏适配器处理。

## 植物大战僵尸

开发者在 `web-vue/games/pvz/media/menu.png` 维护商城封面，当前使用主菜单的实际截图。游戏介绍根据百度搜索中的百度百科条目整理，简要说明玩法。

用户购买游戏后，可以从商城或游戏库启动。后端账本负责结算正式关卡的通关奖励，具体规则见[字块币与游戏商城](blockcoin.md)。

游戏库将封面、玩法介绍、开始游戏、收藏和编辑器集中在同一条目中，管理员可展开资料区维护游戏。植物大战僵尸提供六个关卡、图鉴和 Swing 编辑器。游戏在平台内容区按原始尺寸居中显示，并沿用导航与标题栏。窄屏用户可以滚动查看完整画面，全屏按钮则将整个平台切换到全屏。

标题右侧的“关卡编辑器”和选关页入口打开同一个 Java 编辑器，编辑器与游戏共用内嵌内容区。游戏已加载时，编辑器会复用当前运行环境和素材；重复打开会保留未保存的内容，关闭后返回游戏。管理员可以使用编辑器，普通用户看到的网页按钮处于不可用状态，悬停时显示“仅管理员可用”。游戏内入口也按当前角色启用。

浏览器版将画面刷新与战斗计算分开安排：画面约每 33 毫秒刷新一次，战斗仍按原来的 16 毫秒步长推进。菜单、选关和结算在内容变化时重绘，相同入口重复打开时复用已有窗口。

选卡页的展示僵尸持续播放原动作，展示与战斗中的动画统一使用游戏时钟记录起点。浏览器加载较慢或页面恢复后，角色仍从本局当前时刻开始播放。

游戏使用 `levels` 作为数据目录，关卡编号范围为 0–99。编辑器负责导入、导出和保存关卡，并将资料存入当前浏览器中对应账户的目录。

管理员可以在商城中维护商品资料。删除已登记游戏前，确认框会列出关联清理范围；平台会保留安装文件，管理员之后可以重新登记。游戏自动上报游玩时长和关卡结果，原手动录入入口继续保留。

## 检查与发布

```powershell
npm run lint
npm run lint:comments
npm test
npm run games:test
npm run build
```

`games:test` 会读取各游戏配置中的测试目录和入口。植物大战僵尸的测试包括六个关卡、浏览器导入和公共文件工具。构建工具每次编译都使用独立临时目录，结束后清理类文件；测试截图保存在各游戏的 `build/` 目录中，该目录由 Git 忽略。发布成功后，构建工具会清理已被新指纹资源替换的旧文件，并保留当前清单与其他资料。

用户首次运行游戏时，网页需要联网加载 CheerpJ。页面保留社区版署名及相关许可，游戏包保留作者署名、代码许可和依赖许可。游戏启动页只接受同源平台嵌入，后端沿用原服务器连接配置。

网页会自动加载运行环境和游戏包，用户直接点击平台入口即可启动。构建工具将游戏包发布为带内容指纹的站内资源，网页完整读取后，将其放入 Java 内存文件系统供游戏使用。正常启动时页面保持简洁，加载失败时会显示具体阶段与原因。
