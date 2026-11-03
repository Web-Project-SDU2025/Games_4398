const fs = require("node:fs");
const path = require("node:path");
const parser = require("@babel/parser");

const SOURCE_DIRECTORY = "src";
const errors = [];

// 收集手写的 JS 和 Vue 文件，类型声明由工具生成，不在检查范围内
function collectFiles(directory, files) {
  const entries = fs.readdirSync(directory, { withFileTypes: true });
  for (const entry of entries) {
    const filename = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      collectFiles(filename, files);
    } else if (filename.endsWith(".js") || filename.endsWith(".vue")) {
      files.push(filename);
    }
  }
}

// 记录文件和行号，让检查结果能直接定位需要修改的位置
function report(filename, node, message) {
  errors.push(filename + ":" + node.loc.start.line + " " + message);
}

// 检查一个语法节点，使用语法树可以避免把字符串里的问号和加号误判为代码
function checkNode(node, parent, comments, source, filename) {
  if (!node || typeof node !== "object") {
    return;
  }
  const forbidden = {
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
  if (forbidden[node.type]) {
    report(filename, node, forbidden[node.type]);
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
    }
    let explained = false;
    for (const comment of comments) {
      const gap = source.substring(comment.end, declaration);
      if (comment.end <= declaration && gap.trim() === "") {
        if (/[\u4e00-\u9fff]/.test(comment.value)) {
          explained = true;
        }
      }
    }
    if (!explained) {
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

// Vue 只解析脚本；模板里的文字和样式不会作为 JavaScript 处理
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
  const tree = parser.parse(source, { sourceType: "module" });
  for (const comment of tree.comments) {
    if (/[。.]+\s*$/.test(comment.value)) {
      report(filename, comment, "注释最后一句末尾不加句号");
    }
  }
  checkNode(tree.program, null, tree.comments, source, filename);
}

const files = [];
collectFiles(SOURCE_DIRECTORY, files);
for (const filename of files) {
  checkFile(filename);
}
if (errors.length > 0) {
  console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  console.log("代码风格检查通过，共检查 " + files.length + " 个文件");
}
