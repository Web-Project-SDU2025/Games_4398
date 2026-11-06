const fs = require("node:fs");
const path = require("node:path");

// 只整理本项目的前端生成目录，运行目录在同步完成后单独处理
const WORKSPACE = path.resolve(__dirname, "../..");
const SOURCE_OUTPUT = path.join(
  WORKSPACE,
  "java-server/java-server/src/main/resources/public",
);
const RUNTIME_OUTPUT = path.join(
  WORKSPACE,
  "java-server/java-server/target/classes/public",
);
const ASSET_DIRECTORIES = ["js", "css"];
// Vite 指纹文件由名称、八位摘要和扩展名组成，其他命名的文件继续保留
const GENERATED_FILENAME = /^[A-Za-z0-9_-]+-[A-Za-z0-9_-]{8}\.(js|css)$/;

// 读取完整构建清单后列出旧资源，检查全部路径通过后才开始删除
function staleAssets(output) {
  const root = fs.realpathSync(output);
  const workspace = fs.realpathSync(WORKSPACE);
  const normalizedRoot = root.toLowerCase();
  const workspacePrefix = workspace.toLowerCase() + path.sep;
  if (!normalizedRoot.startsWith(workspacePrefix)) {
    throw new Error("前端生成目录应位于当前工作区内");
  }
  const filename = path.join(root, "manifest.json");
  const manifest = JSON.parse(fs.readFileSync(filename, "utf8"));
  const current = new Set();
  for (const entry of Object.values(manifest)) {
    if (!entry || typeof entry.file !== "string" || !entry.file) {
      throw new Error("前端构建清单缺少资源文件");
    }
    current.add(entry.file);
    for (const field of ["css", "assets"]) {
      if (Array.isArray(entry[field])) {
        for (const resource of entry[field]) {
          current.add(resource);
        }
      }
    }
  }
  if (current.size === 0) {
    throw new Error("前端构建清单为空");
  }
  const stale = [];
  for (const directory of ASSET_DIRECTORIES) {
    const absolute = fs.realpathSync(path.join(root, directory));
    const normalizedDirectory = absolute.toLowerCase();
    const outputPrefix = normalizedRoot + path.sep;
    if (!normalizedDirectory.startsWith(outputPrefix)) {
      throw new Error("前端资源目录应位于生成目录内");
    }
    for (const entry of fs.readdirSync(absolute, { withFileTypes: true })) {
      const relative = directory + "/" + entry.name;
      if (
        entry.isFile() &&
        GENERATED_FILENAME.test(entry.name) &&
        !current.has(relative)
      ) {
        stale.push(path.join(absolute, entry.name));
      }
    }
  }
  return stale;
}

let output = SOURCE_OUTPUT;
if (process.argv.includes("--runtime")) {
  output = RUNTIME_OUTPUT;
}
const files = staleAssets(output);
if (!process.argv.includes("--check")) {
  for (const filename of files) {
    fs.unlinkSync(filename);
  }
}
console.log("当前构建之外的旧指纹资源：" + files.length + " 个");
