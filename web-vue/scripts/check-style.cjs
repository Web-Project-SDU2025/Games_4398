const fs = require("node:fs");
const parser = require("@babel/parser");
const sources = require("../../scripts/source-files.cjs");

// JavaScript 与 Vue 共用工作区范围，原服务器连接文件由清单保留
const EXTENSIONS = [".js", ".mjs", ".cjs", ".vue"];
// 大写的模块级常量集中排在函数前，避免把调参项散在业务实现中
const MODULE_CONSTANT_NAME = /^[A-Z][A-Z0-9_]+$/;
// 禁用语法与对应提示，按语法树节点名称查询
const FORBIDDEN_SYNTAX = {
  ArrowFunctionExpression: "请使用有名称的普通函数",
  ConditionalExpression: "请把三目运算符改为 if / else",
  ObjectPattern: "请使用明确的属性读取，不使用解构",
  ArrayPattern: "请使用明确的数组读取，不使用解构",
  SpreadElement: "请使用普通循环，不使用展开运算符",
  RestElement: "请写明参数，不使用剩余参数",
  UpdateExpression: "请把自增自减改为普通赋值",
  ObjectMethod: "请使用属性名和有名称的普通函数",
  OptionalMemberExpression: "请用 if 检查对象后再读取属性",
  OptionalCallExpression: "请用 if 检查函数后再调用",
};
const errors = [];

// 记录文件和行号，让检查结果能直接定位需要修改的位置
function report(
  filename,
  node,
  message,
) {
  errors.push(filename + ":" + node.loc.start.line + " " + message);
}

// 声明说明必须是紧邻上方的中文注释，上一行代码末尾的注释不算
function hasExplanation(position, comments, source) {
  for (const comment of comments) {
    if (comment.end > position) {
      break;
    }
    const gap = source.slice(comment.end, position);
    const lineStart = source.lastIndexOf("\n", comment.start - 1) + 1;
    const before = source.slice(lineStart, comment.start);
    if (!gap.trim() && !before.trim() && /\p{Script=Han}/u.test(comment.value)) {
      return true;
    }
  }
  return false;
}

// 检查普通声明及导出的常量，不把函数内的计算结果当作模块配置
function checkModuleConstants(program, comments, source, filename) {
  let functionsStarted = false;
  for (const statement of program.body) {
    const declaration = statement.declaration || statement;
    if (declaration.type === "FunctionDeclaration") {
      functionsStarted = true;
    }
    if (declaration.type !== "VariableDeclaration" || declaration.kind !== "const") {
      continue;
    }
    for (const item of declaration.declarations) {
      if (!item.id.name || !MODULE_CONSTANT_NAME.test(item.id.name)) {
        continue;
      }
      if (functionsStarted) {
        report(filename, item, "模块常量应在函数之前：" + item.id.name);
      }
      if (!hasExplanation(statement.start, comments, source)) {
        report(filename, item, "常量上方需要中文说明：" + item.id.name);
      }
    }
  }
}

// 按语法节点检查表达式，字符串中的符号由解析器识别为文字
function checkNode(
  node,
  parent,
  comments,
  source,
  filename,
) {
  if (!node || typeof node !== "object") {
    return;
  }
  if (FORBIDDEN_SYNTAX[node.type]) {
    report(filename, node, FORBIDDEN_SYNTAX[node.type]);
  }
  if (node.type === "AssignmentExpression" && node.operator !== "=") {
    report(filename, node, "请把复合赋值改为普通赋值");
  }
  if (node.type === "LogicalExpression" && node.operator === "??") {
    report(filename, node, "请用 if / else 处理空值");
  }
  if (node.type === "ObjectProperty" && node.shorthand) {
    report(filename, node, "请写明属性名和属性值");
  }
  if (
    node.type === "FunctionExpression" ||
    node.type === "FunctionDeclaration"
  ) {
    if (!node.id) {
      report(filename, node, "函数必须有名称");
    }
    let declaration = node.start;
    if (parent && parent.type === "ObjectProperty") {
      declaration = parent.start;
    } else if (parent && parent.type === "ExportNamedDeclaration") {
      declaration = parent.start;
    } else if (parent && parent.type === "CallExpression") {
      declaration = parent.start;
    }
    if (!hasExplanation(declaration, comments, source)) {
      report(filename, node, "函数上方需要中文功能说明");
    }
  }
  for (const key of Object.keys(node)) {
    if (key === "loc" || key.endsWith("Comments") || key === "comments") {
      continue;
    }
    const value = node[key];
    if (Array.isArray(value)) {
      for (const child of value) {
        checkNode(child, node, comments, source, filename);
      }
    } else if (value && typeof value === "object") {
      checkNode(value, node, comments, source, filename);
    }
  }
}

// 从 Vue 文件提取脚本，再按 JavaScript 语法检查
function checkFile(filename) {
  let source = fs.readFileSync(filename, "utf8");
  if (filename.endsWith(".vue")) {
    const start = source.indexOf("<script");
    if (start === -1) {
      return;
    }
    const contentStart = source.indexOf(">", start) + 1;
    const contentEnd = source.indexOf("</script>", contentStart);
    source = source.substring(contentStart, contentEnd);
  }
  const tree = parser.parse(source, { sourceType: "unambiguous" });
  for (const comment of tree.comments) {
    if (/[。.]+\s*$/.test(comment.value)) {
      report(filename, comment, "注释最后一句末尾不加句号");
    }
  }
  checkModuleConstants(tree.program, tree.comments, source, filename);
  checkNode(tree.program, null, tree.comments, source, filename);
}

const files = sources.sourceFiles(EXTENSIONS);
for (const file of files) {
  checkFile(file.absolute);
}
if (errors.length > 0) {
  console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  console.log("代码风格检查通过，共检查 " + files.length + " 个文件");
}
