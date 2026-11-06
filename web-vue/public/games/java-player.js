import {
  GAME_REGISTRY,
  GAME_ID_PATTERN,
  normalizeManifest,
  PLAYER_DEFAULTS,
} from "./game-manifest.mjs";
import { loadGameResources } from "./game-resources.mjs";
import { centerGameDisplay } from "./game-display.mjs";

// 网页按游戏能力启动 Java，游戏规则与编辑器由自己的入口提供
(function startJavaPlayer() {
  // 运行版本、虚拟桌面尺寸和文件体积限制集中维护
  const MAIN_ADAPTER = "platform.runtime.MainGame";
  let adapter = null;
  let files = null;
  let directory = "";
  let configuration = null;
  let busy = true;
  let phase = "读取游戏配置";

  // 显示启动步骤或文件操作结果，Java 内部窗口单独在虚拟桌面显示
  function status(message) {
    document.getElementById("player-status").textContent = message;
  }

  // 普通网页异常读取原消息，Java 异常由具体适配方法返回可显示的文字
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
    return "Java 运行过程遇到异常，请刷新后重试";
  }

  // 按当前任务锁定启动与文件按钮，耗时操作完成后恢复操作
  function controls(disabled) {
    busy = disabled;
    for (const id of [
      "open-game",
      "open-editor",
      "export-levels",
      "import-level",
    ]) {
      document.getElementById(id).disabled = disabled;
    }
    if (configuration) {
      document.getElementById("open-editor").hidden =
        !configuration.capabilities.editor;
      document.getElementById("export-levels").hidden =
        !configuration.capabilities.exportData;
      document.getElementById("import-number-label").hidden =
        !configuration.capabilities.importData;
      document.getElementById("import-file-label").hidden =
        !configuration.capabilities.importData;
    }
  }

  // 从明确登记的配置加载游戏，运行时只读取站内目录中的类和资源
  async function loadConfiguration() {
    const parameters = new URLSearchParams(location.search);
    const id = parameters.get("game");
    if (!id || !GAME_ID_PATTERN.test(id)) {
      throw new Error("游戏编号格式不正确");
    }
    const registryResponse = await fetch(GAME_REGISTRY, { cache: "no-store" });
    if (!registryResponse.ok) {
      throw new Error("游戏清单读取失败");
    }
    const registry = await registryResponse.json();
    const address = "/games/" + id + "/game.json";
    if (!Array.isArray(registry) || !registry.includes(address)) {
      throw new Error("此游戏尚未登记");
    }
    const response = await fetch(address, { cache: "no-store" });
    if (!response.ok) {
      throw new Error("游戏配置读取失败");
    }
    const source = await response.json();
    const game = normalizeManifest(source, address);
    let owner = parameters.get("owner") || "standalone";
    if (!/^[a-zA-Z0-9_-]{1,64}$/.test(owner)) {
      owner = "standalone";
    }
    directory = "/files/4398/" + id + "/" + owner + "/assets";
    const number = document.getElementById("level-number");
    number.min = game.imports.minimum;
    number.max = game.imports.maximum;
    number.value = game.imports.minimum;
    document.getElementById("import-level").accept = game.imports.accept;
    document.getElementById("import-number-label").firstChild.textContent =
      game.imports.label + " ";
    return game;
  }

  // 普通 main 参数可引用账户素材目录，每项独立展开后传入 Java 入口
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

  // 根据完整类名逐层读取 Java 代理，配置中填写的适配器共用同一入口
  async function javaClass(library, name) {
    let current = library;
    for (const part of name.split(".")) {
      current = current[part];
    }
    return await current;
  }

  // 打开原 Java 游戏或编辑器，首次加载素材较慢时保持明确的等待提示
  async function open(editorMode) {
    if (busy || !adapter) {
      return;
    }
    controls(true);
    status("正在加载原 Java 素材和窗口");
    try {
      if (editorMode && !configuration.capabilities.editor) {
        throw new Error("此游戏尚未提供编辑器");
      }
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
      status("Java 游戏已就绪");
    } catch (error) {
      status("窗口打开失败：" + (await errorMessage(error)));
    } finally {
      controls(false);
    }
  }

  // 工具栏游戏入口复用统一窗口启动过程
  function openGame() {
    open(false);
  }

  // 工具栏编辑入口复用统一窗口启动过程
  function openEditor() {
    open(true);
  }

  // 导出关卡与封面为 ZIP，下载由浏览器完成
  async function exportLevels() {
    if (busy) {
      return;
    }
    controls(true);
    try {
      const encoded = await files.exportData(
        directory,
        configuration.dataDirectory,
      );
      const content = atob(encoded);
      const bytes = new Uint8Array(content.length);
      for (let index = 0; index < content.length; index = index + 1) {
        bytes[index] = content.charCodeAt(index);
      }
      const url = URL.createObjectURL(
        new Blob([bytes], { type: "application/zip" }),
      );
      const link = document.createElement("a");
      link.href = url;
      link.download = configuration.id + "-levels.zip";
      link.click();
      // 浏览器取得下载地址后释放临时对象
      function releaseDownload() {
        URL.revokeObjectURL(url);
      }
      setTimeout(releaseDownload, PLAYER_DEFAULTS.downloadReleaseDelay);
      status("关卡备份已导出");
    } catch (error) {
      status("关卡导出失败：" + (await errorMessage(error)));
    } finally {
      controls(false);
    }
  }

  // 导入单个 JSON 关卡，先验证大小和编号，再由原 Java 编辑器校验内容
  async function importLevel(event) {
    const file = event.target.files[0];
    const input = document.getElementById("level-number");
    if (!file || busy) {
      return;
    }
    if (!input.reportValidity() || file.size > configuration.imports.maxBytes) {
      status("请检查文件体积与" + configuration.imports.label + "范围");
      return;
    }
    controls(true);
    try {
      const text = await file.text();
      const result = await adapter.importData(
        directory,
        Number(input.value),
        text,
      );
      if (result !== "") {
        throw new Error(result);
      }
      status("关卡已导入，请在编辑器中重新载入该编号");
    } catch (error) {
      status("关卡导入失败：" + (await errorMessage(error)));
    } finally {
      event.target.value = "";
      controls(false);
    }
  }

  // 初始化 Java 十七环境并安装素材包，后续打开窗口复用这一个运行实例
  async function initialize() {
    try {
      configuration = await loadConfiguration();
      document.getElementById("game-title").textContent = configuration.name;
      if (typeof cheerpjInit !== "function") {
        throw new Error("Java 运行环境尚未加载，请检查网络后刷新");
      }
      phase = "自动加载浏览器运行环境";
      status("正在自动准备游戏运行环境，无需安装");
      configurePixelRatio();
      await cheerpjInit({ version: PLAYER_DEFAULTS.javaVersion });
      cheerpjCreateDisplay(
        configuration.display.width,
        configuration.display.height,
        document.getElementById("java-display"),
      );
      centerGameDisplay(
        configuration.display.width,
        configuration.display.height,
      );
      phase = "自动加载游戏资源";
      const resources = await loadGameResources(
        configuration,
        window.fetch.bind(window),
        cheerpOSAddStringFile,
        cheerpOSRemoveStringFile,
        status,
      );
      phase = "加载 Java 游戏类";
      const library = await cheerpjRunLibrary(resources.classpath.join(":"));
      status("正在安装游戏素材");
      files = await library.platform.runtime.GameFiles;
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
      let adapterClass = MAIN_ADAPTER;
      if (configuration.adapter) {
        adapterClass = configuration.adapter;
      }
      adapter = await javaClass(library, adapterClass);
      controls(false);
      const parameters = new URLSearchParams(location.search);
      await open(parameters.get("mode") === "editor");
    } catch (error) {
      console.error("Java 游戏启动失败阶段：" + phase, error);
      status("启动失败（" + phase + "）：" + (await errorMessage(error)));
      document.getElementById("retry-start").hidden = false;
    }
  }

  // 当前运行环境直接读取设备像素比，固定独立游戏页的比例，让画布按原始尺寸绘制
  function configurePixelRatio() {
    Object.defineProperty(window, "devicePixelRatio", {
      value: PLAYER_DEFAULTS.pixelRatio,
      configurable: true,
    });
  }

  // 重建当前运行页面并复用浏览器缓存，重新执行环境与资源加载
  function retryStart() {
    location.reload();
  }

  document.getElementById("retry-start").addEventListener("click", retryStart);
  document.getElementById("open-game").addEventListener("click", openGame);
  document.getElementById("open-editor").addEventListener("click", openEditor);
  document
    .getElementById("export-levels")
    .addEventListener("click", exportLevels);
  document
    .getElementById("import-level")
    .addEventListener("change", importLevel);
  initialize();
})();
