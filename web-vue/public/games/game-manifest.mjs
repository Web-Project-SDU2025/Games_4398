// 平台与启动器共用的游戏配置边界，构建时将本模块复制为站内运行文件
export const GAME_REGISTRY = "/games/registry.json";
export const GAME_ID_PATTERN = /^[a-z][a-z0-9-]{0,39}$/;
export const JAVA_CLASS_PATTERN =
  /^[a-zA-Z_$][a-zA-Z0-9_$]*(\.[a-zA-Z_$][a-zA-Z0-9_$]*)+$/;
export const PLAYER_DEFAULTS = {
  width: 1440,
  height: 960,
  javaVersion: 17,
  // 独立游戏页采用原始像素比例，主平台继续使用浏览器自己的屏幕比例
  pixelRatio: 1,
  downloadReleaseDelay: 1000,
};
export const IMPORT_DEFAULTS = {
  minimum: 0,
  maximum: 99,
  maxBytes: 2097152,
  accept: ".json,application/json",
  label: "关卡编号",
};
export const DEFAULT_CAPABILITIES = {
  editor: false,
  importData: false,
  exportData: false,
};
export const MANIFEST_LIMITS = {
  nameLength: 50,
  dependencyCount: 32,
  argumentCount: 32,
  argumentLength: 512,
  minimumWidth: 320,
  maximumWidth: 3840,
  minimumHeight: 240,
  maximumHeight: 2160,
  maximumImportNumber: 1000000,
  maximumImportBytes: 2097152,
  importLabelLength: 120,
};

// 检查相对资源路径，所有文件都应位于当前游戏目录，编码和跳目录写法按错误配置处理
export function relativePath(value, label) {
  if (typeof value !== "string" || !/^[a-zA-Z0-9_./-]+$/.test(value)) {
    throw new Error(label + "应为游戏目录中的相对路径");
  }
  const parts = value.split("/");
  for (const part of parts) {
    if (part === "" || part === "." || part === "..") {
      throw new Error(label + "包含无效路径段");
    }
  }
  return value;
}

// 校验可配置整数，省略字段时使用共享默认值，异常输入显示字段名
function number(value, fallback, minimum, maximum, label) {
  if (value === undefined) {
    return fallback;
  }
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new Error(label + "超出允许范围");
  }
  return value;
}

// 检查类名配置，Java 普通入口和专用适配器共用同一命名规则
function className(value, label) {
  if (typeof value !== "string" || !JAVA_CLASS_PATTERN.test(value)) {
    throw new Error(label + "应为完整 Java 类名");
  }
  return value;
}

// 按能力决定入口和可见按钮，普通 main 工程可省略编辑器和文件操作
export function normalizeManifest(source, address) {
  if (
    !source ||
    typeof source !== "object" ||
    typeof source.id !== "string" ||
    !GAME_ID_PATTERN.test(source.id)
  ) {
    throw new Error("游戏编号应使用英文小写、数字和连字符");
  }
  const expected = "/games/" + source.id + "/game.json";
  if (address !== expected || source.runtime !== "java17") {
    throw new Error("游戏配置路径或运行类型不正确");
  }
  if (
    typeof source.name !== "string" ||
    source.name.trim() === "" ||
    source.name.length > MANIFEST_LIMITS.nameLength
  ) {
    throw new Error("游戏名称应为一至五十个字符");
  }
  const cover = relativePath(source.cover, "封面");
  if (!/\.(png|jpg|jpeg|gif|webp)$/i.test(cover)) {
    throw new Error("游戏封面应为图片文件");
  }
  let capabilities = source.capabilities;
  if (
    capabilities !== undefined &&
    (!capabilities ||
      typeof capabilities !== "object" ||
      Array.isArray(capabilities))
  ) {
    throw new Error("游戏能力应为开关配置对象");
  }
  if (!capabilities || typeof capabilities !== "object") {
    capabilities = DEFAULT_CAPABILITIES;
  }
  for (const name of Object.keys(DEFAULT_CAPABILITIES)) {
    if (
      capabilities[name] !== undefined &&
      typeof capabilities[name] !== "boolean"
    ) {
      throw new Error("游戏能力 " + name + " 应使用 true 或 false");
    }
  }
  const result = {
    id: source.id,
    name: source.name,
    number: source.number || source.id,
    category: source.category || "小游戏",
    description: source.description || "",
    runtime: "java17",
    cover: cover,
    classpath: [],
    adapter: "",
    mainClass: "",
    editorMainClass: "",
    arguments: [],
    editorArguments: [],
    assets: "",
    assetVersion: source.assetVersion || "1",
    capabilities: {
      editor: capabilities.editor === true,
      importData: capabilities.importData === true,
      exportData: capabilities.exportData === true,
    },
    display: { width: PLAYER_DEFAULTS.width, height: PLAYER_DEFAULTS.height },
    imports: {
      minimum: IMPORT_DEFAULTS.minimum,
      maximum: IMPORT_DEFAULTS.maximum,
      maxBytes: IMPORT_DEFAULTS.maxBytes,
      accept: IMPORT_DEFAULTS.accept,
      label: IMPORT_DEFAULTS.label,
    },
    preservedPaths: [],
    dataDirectory: "data",
    resources: {},
  };
  if (
    !Array.isArray(source.classpath) ||
    source.classpath.length === 0 ||
    source.classpath.length > MANIFEST_LIMITS.dependencyCount
  ) {
    throw new Error("游戏依赖应为一至三十二个 JAR 路径");
  }
  for (const value of source.classpath) {
    const file = relativePath(value, "Java 依赖");
    if (!file.endsWith(".jar") || result.classpath.includes(file)) {
      throw new Error("Java 依赖应为各自独立的 JAR 文件");
    }
    result.classpath.push(file);
  }
  if (source.resources) {
    for (const name of Object.keys(source.resources)) {
      const entry = source.resources[name];
      if (!entry || typeof entry !== "object") {
        throw new Error("发布资源清单格式不正确");
      }
      result.resources[name] = {
        path: relativePath(entry.path, "发布资源"),
        bytes: number(entry.bytes, 0, 1, 134217728, "发布资源体积"),
      };
    }
  }
  if (source.adapter) {
    result.adapter = className(source.adapter, "适配器");
  } else {
    result.mainClass = className(source.mainClass, "游戏入口");
    if (result.capabilities.editor) {
      result.editorMainClass = className(source.editorMainClass, "编辑器入口");
    }
    if (result.capabilities.importData) {
      throw new Error("数据导入需要提供游戏专用校验适配器");
    }
  }
  for (const name of ["arguments", "editorArguments"]) {
    if (source[name] !== undefined) {
      if (
        !Array.isArray(source[name]) ||
        source[name].length > MANIFEST_LIMITS.argumentCount
      ) {
        throw new Error("启动参数应为最多三十二项的数组");
      }
      for (const value of source[name]) {
        if (
          typeof value !== "string" ||
          value.length > MANIFEST_LIMITS.argumentLength ||
          /[\r\n]/.test(value)
        ) {
          throw new Error("启动参数应为五百一十二字符以内的字符串");
        }
        result[name].push(value);
      }
    }
  }
  if (source.assets) {
    result.assets = relativePath(source.assets, "素材包");
    if (!result.assets.endsWith(".zip")) {
      throw new Error("素材包应为 ZIP 文件");
    }
  }
  if (source.preservedPaths !== undefined) {
    if (!Array.isArray(source.preservedPaths)) {
      throw new Error("用户数据目录应为路径数组");
    }
    for (const value of source.preservedPaths) {
      result.preservedPaths.push(relativePath(value, "用户数据目录"));
    }
  }
  if (source.dataDirectory) {
    result.dataDirectory = relativePath(source.dataDirectory, "游戏数据目录");
  }
  const display = source.display || {};
  result.display.width = number(
    display.width,
    PLAYER_DEFAULTS.width,
    MANIFEST_LIMITS.minimumWidth,
    MANIFEST_LIMITS.maximumWidth,
    "虚拟桌面宽度",
  );
  result.display.height = number(
    display.height,
    PLAYER_DEFAULTS.height,
    MANIFEST_LIMITS.minimumHeight,
    MANIFEST_LIMITS.maximumHeight,
    "虚拟桌面高度",
  );
  const imports = source.imports || {};
  result.imports.minimum = number(
    imports.minimum,
    IMPORT_DEFAULTS.minimum,
    0,
    MANIFEST_LIMITS.maximumImportNumber,
    "导入最小编号",
  );
  result.imports.maximum = number(
    imports.maximum,
    IMPORT_DEFAULTS.maximum,
    result.imports.minimum,
    MANIFEST_LIMITS.maximumImportNumber,
    "导入最大编号",
  );
  result.imports.maxBytes = number(
    imports.maxBytes,
    IMPORT_DEFAULTS.maxBytes,
    1,
    MANIFEST_LIMITS.maximumImportBytes,
    "导入体积",
  );
  for (const name of ["accept", "label"]) {
    if (imports[name] !== undefined) {
      if (
        typeof imports[name] !== "string" ||
        imports[name].length > MANIFEST_LIMITS.importLabelLength
      ) {
        throw new Error("导入说明格式不正确");
      }
      result.imports[name] = imports[name];
    }
  }
  return result;
}
