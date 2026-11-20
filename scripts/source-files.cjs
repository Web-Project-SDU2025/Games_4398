const fs = require("node:fs");
const path = require("node:path");

// 手写源码、生成目录与保留文件统一读取项目范围清单
const WORKSPACE = path.resolve(__dirname, "..");
// 从清单读取源码范围，避免各语言检查分别维护目录
const scopePath = path.join(WORKSPACE, "scripts/style-scope.json");
const scopeText = fs.readFileSync(scopePath, "utf8");
const scope = JSON.parse(scopeText);
const preservedPath = path.join(WORKSPACE, scope.preservedFileList);
const preservedSource = fs.readFileSync(preservedPath, "utf8");
// 保留清单逐行记录相对路径，扫描时按完整路径排除
const PRESERVED = preservedSource.split(/\r?\n/);

// 按扩展名递归收集源码，目录和文件都应用同一份排除规则
function collectFiles(directory, extensions, files) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const absolute = path.join(directory, entry.name);
    const nativePath = path.relative(WORKSPACE, absolute);
    const relative = nativePath.replaceAll("\\", "/");
    if (entry.isDirectory()) {
      if (
        !scope.ignoredDirectories.includes(entry.name) &&
        !scope.generatedDirectories.includes(relative)
      ) {
        collectFiles(absolute, extensions, files);
      }
    } else if (entry.isFile()) {
      addSourceFile(relative, extensions, files);
    }
  }
}

// 入口文件和目录中的文件都应用同一份保留清单
function addSourceFile(relative, extensions, files) {
  const extension = path.extname(relative);
  if (!extensions.includes(extension)) {
    return;
  }
  if (PRESERVED.includes(relative) || scope.generatedFiles.includes(relative)) {
    return;
  }
  const absolute = path.join(WORKSPACE, relative);
  files.push({ absolute: absolute, relative: relative });
}

// 返回指定语言的手写文件列表，调用方分别执行语法或注释检查
function sourceFiles(extensions) {
  const files = [];
  for (const directory of scope.sourceRoots) {
    collectFiles(path.join(WORKSPACE, directory), extensions, files);
  }
  for (const relative of scope.standaloneFiles) {
    addSourceFile(relative, extensions, files);
  }
  return files;
}

module.exports = { sourceFiles: sourceFiles };
