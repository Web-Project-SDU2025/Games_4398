import { PLAYER_DEFAULTS } from "./game-manifest.mjs";
import { loadGameResources } from "./game-resources.mjs";
import { centerGameDisplay } from "./game-display.mjs";
import {
  PLAYER_MESSAGES,
  MAIN_ADAPTER,
  EDITOR_LEVEL_PROPERTY,
  gameOwner,
  playerMessage,
  editorLevel,
  EDITOR_ACCESS_PROPERTY,
} from "./game-session.mjs";
import { readRegisteredGame } from "./game-registry.mjs";
import { gameIdentity, economyRequest } from "./game-economy.mjs";
import { createGameplayTracker } from "./game-play.mjs";

// 网页加载原 Java 入口，游戏与原编辑器共用素材目录和运行环境
(function startJavaPlayer() {
  let directory = "";
  let configuration = null;
  let phase = "读取游戏配置";
  let runtimeLibrary = null;
  let busy = false;
  let editorRequested = false;
  let editorAllowed = false;
  let tracker = null;

  // 仅在操作失败时显示提示，正常加载由游戏画面自行显示
  function status(message) {
    document.getElementById("player-feedback").hidden = false;
    document.getElementById("player-status").textContent = message;
  }

  // 读取网页或 Java 异常中的消息，便于识别具体失败阶段
  async function errorMessage(error) {
    if (error && typeof error.message === "string") {
      return error.message;
    }
    if (error && typeof error.toString === "function") {
      try {
        const text = await error.toString();
        if (typeof text === "string" && text !== "[object Object]") {
          return text;
        }
      } catch (conversionError) {
        console.error("读取 Java 异常详情失败", conversionError);
      }
    }
    return "游戏运行遇到异常，请重试";
  }

  // 按站内注册表校验游戏配置，用户目录按账户独立保存
  async function loadConfiguration() {
    const parameters = new URLSearchParams(location.search);
    const id = parameters.get("game");
    const game = await readRegisteredGame(id, window.fetch.bind(window));
    const owner = gameOwner(parameters.get("owner"));
    directory = "/files/4398/" + id + "/" + owner + "/assets";
    return game;
  }

  // 展开普通 Java main 的素材与数据路径，各参数保持独立
  function mainArguments(editorMode) {
    let source = configuration.arguments;
    if (editorMode) {
      source = configuration.editorArguments;
    }
    const values = [];
    for (const value of source) {
      const assets = value.replaceAll("{assets}", directory);
      const expanded = assets.replaceAll(
        "{data}",
        directory + "/" + configuration.dataDirectory,
      );
      values.push(expanded);
    }
    return values.join("\n");
  }

  // 根据完整类名逐层查找 Java 类，普通入口与专用适配器共用查找过程
  async function javaClass(library, name) {
    let current = library;
    for (const part of name.split(".")) {
      current = current[part];
    }
    return await current;
  }

  // 标题按钮复用当前 Java 实例，首次准备期间的请求在游戏就绪后处理
  async function openCurrentEditor() {
    if (!editorAllowed) {
      return;
    }
    if (!runtimeLibrary) {
      editorRequested = true;
      return;
    }
    if (busy || !configuration.capabilities.editor) {
      return;
    }
    busy = true;
    try {
      await open(runtimeLibrary, true);
    } catch (error) {
      status("编辑器打开失败：" + (await errorMessage(error)));
    } finally {
      busy = false;
    }
  }

  // 仅关闭原编辑器，试玩和时钟由原编辑器自己的清理方法结束
  async function closeCurrentEditor() {
    if (!runtimeLibrary || busy) {
      return;
    }
    busy = true;
    try {
      const adapter = await javaClass(runtimeLibrary, configuration.adapter);
      await adapter.closeEditor();
    } catch (error) {
      status("编辑器关闭失败：" + (await errorMessage(error)));
    } finally {
      busy = false;
    }
  }

  // 只接收同源平台发送的窗口指令，游戏内按钮继续走原 Java 编辑器入口
  async function receiveCommand(event) {
    const message = playerMessage(event, window.parent, location.origin);
    if (!message) {
      return;
    }
    if (message.type === PLAYER_MESSAGES.openEditor) {
      openCurrentEditor();
    } else if (message.type === PLAYER_MESSAGES.closeEditor) {
      closeCurrentEditor();
    } else if (message.type === PLAYER_MESSAGES.stopGame) {
      try {
        if (runtimeLibrary && configuration.adapter) {
          const adapter = await javaClass(
            runtimeLibrary,
            configuration.adapter,
          );
          await adapter.close();
        }
        if (tracker) {
          await tracker.stop();
        }
      } finally {
        window.parent.postMessage(
          { type: PLAYER_MESSAGES.gameStopped },
          location.origin,
        );
      }
    }
  }

  // 将 Java 桌面中的编辑器状态交给平台，原 iframe 保持挂载并继续使用同一运行环境
  function editorModeChanged(opened) {
    if (tracker) {
      tracker.pause(opened);
    }
    if (window.parent !== window) {
      window.parent.postMessage(
        { type: PLAYER_MESSAGES.editorState, open: opened },
        location.origin,
      );
    }
  }

  // 启动原 Java 游戏或编辑器，文件读写与关卡操作继续由原程序完成
  async function open(library, editorMode) {
    if (editorMode && (!configuration.capabilities.editor || !editorAllowed)) {
      throw new Error("关卡编辑器仅管理员可用");
    }
    let adapterClass = MAIN_ADAPTER;
    if (configuration.adapter) {
      adapterClass = configuration.adapter;
    }
    const adapter = await javaClass(library, adapterClass);
    if (configuration.adapter) {
      await adapter.open(directory, editorMode);
    } else {
      let entry = configuration.mainClass;
      if (editorMode) {
        entry = configuration.editorMainClass;
      }
      const result = await adapter.open(entry, mainArguments(editorMode));
      if (result !== "") {
        throw new Error(result);
      }
    }
  }

  // 自动安装素材并打开游戏，正常加载保持无文字提示，失败时显示具体原因
  async function initialize() {
    try {
      configuration = await loadConfiguration();
      const parameters = new URLSearchParams(location.search);
      const editorMode = parameters.get("mode") === "editor";
      const identity = gameIdentity(localStorage);
      const authorization = await economyRequest(
        "authorize",
        {
          gamePath: "/games/" + configuration.id + "/game.json",
          editor: editorMode,
        },
        identity,
        window.fetch.bind(window),
      );
      if (authorization.code !== 0) {
        throw new Error(authorization.msg);
      }
      directory =
        "/files/4398/" +
        configuration.id +
        "/" +
        authorization.data.userId +
        "/assets";
      editorAllowed = authorization.data.editorAllowed === true;
      document.title = configuration.name;
      if (editorMode) {
        document.title = configuration.name + " · 关卡编辑器";
      }
      if (typeof cheerpjInit !== "function") {
        throw new Error("游戏运行环境尚未加载，请检查网络后重试");
      }
      phase = "准备游戏运行环境";
      configurePixelRatio();
      await cheerpjInit({
        version: PLAYER_DEFAULTS.javaVersion,
      });
      cheerpjCreateDisplay(
        configuration.display.width,
        configuration.display.height,
        document.getElementById("java-display"),
      );
      centerGameDisplay(
        configuration.display.width,
        configuration.display.height,
        editorModeChanged,
      );
      phase = "加载游戏资源";
      const resources = await loadGameResources(
        configuration,
        window.fetch.bind(window),
        cheerpOSAddStringFile,
        cheerpOSRemoveStringFile,
      );
      phase = "加载游戏";
      const library = await cheerpjRunLibrary(resources.classpath.join(":"));
      const files = await library.platform.runtime.GameFiles;
      if (configuration.assets) {
        phase = "安装游戏素材";
        await files.install(
          resources.assets,
          directory,
          configuration.assetVersion,
          configuration.preservedPaths.join("\n"),
        );
        cheerpOSRemoveStringFile(resources.assets);
      } else {
        await files.prepare(directory);
      }
      const level = editorLevel(parameters.get("level"), configuration.imports);
      const system = await library.java.lang.System;
      await system.setProperty(EDITOR_LEVEL_PROPERTY, String(level));
      await system.setProperty(EDITOR_ACCESS_PROPERTY, String(editorAllowed));
      phase = "打开窗口";
      await open(library, editorMode);
      runtimeLibrary = library;
      let eventAdapter = null;
      if (configuration.capabilities.gameplayEvents) {
        eventAdapter = await javaClass(library, configuration.adapter);
      }
      if (eventAdapter || !editorMode) {
        tracker = createGameplayTracker(
          eventAdapter,
          configuration,
          identity,
          window.fetch.bind(window),
          window,
        );
      }
      document.getElementById("player-feedback").hidden = true;
      document.body.dataset.ready = "true";
      if (editorRequested) {
        editorRequested = false;
        await openCurrentEditor();
      }
    } catch (error) {
      console.error("游戏启动失败阶段：" + phase, error);
      status("启动失败（" + phase + "）：" + (await errorMessage(error)));
      document.getElementById("retry-start").hidden = false;
    }
  }

  // 固定独立游戏页的绘制像素比，高分屏继续使用原始画布大小
  function configurePixelRatio() {
    Object.defineProperty(window, "devicePixelRatio", {
      value: PLAYER_DEFAULTS.pixelRatio,
      configurable: true,
    });
  }

  // 重建当前运行环境并复用浏览器缓存，重新执行启动过程
  function retryStart() {
    location.reload();
  }

  document.getElementById("retry-start").addEventListener("click", retryStart);
  window.addEventListener("message", receiveCommand);
  initialize();
})();
