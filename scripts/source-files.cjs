const fs = require("node:fs");
const path = require("node:path");

// 手写源码、生成目录与保留文件统一读取项目范围清单
const WORKSPACE = path.resolve(__dirname, "..");
const SCOPE = JSON.parse(
  fs.readFileSync(path.join(WORKSPACE, "scripts/style-scope.json"), "utf8"),
);
const preservedSource = fs.readFileSync(
  path.join(WORKSPACE, SCOPE.preservedFileList),
  "utf8",
);
const PRESERVED = preservedSource.split(/\r?\n/);

// 按扩展名递归收集源码，目录和文件都应用同一份排除规则
function collectFiles(directory, extensions, files) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const absolute = path.join(directory, entry.name);
    const relative = path.relative(WORKSPACE, absolute).replaceAll("\\", "/");
    if (entry.isDirectory()) {
      if (
        !SCOPE.ignoredDirectories.includes(entry.name) &&
        !SCOPE.generatedDirectories.includes(relative)
      ) {
        collectFiles(absolute, extensions, files);
      }
    } else if (
      extensions.includes(path.extname(entry.name)) &&
      !PRESERVED.includes(relative) &&
      !SCOPE.generatedFiles.includes(relative)
    ) {
      files.push({ absolute: absolute, relative: relative });
    }
  }
}

// 返回指定语言的手写文件列表，调用方分别执行语法或注释检查
function sourceFiles(extensions) {
  const files = [];
  for (const directory of SCOPE.sourceRoots) {
    collectFiles(path.join(WORKSPACE, directory), extensions, files);
  }
  return files;
}

module.exports = { sourceFiles: sourceFiles };
