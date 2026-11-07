const fs = require("node:fs");
const path = require("node:path");
const parser = require("@babel/parser");
const sources = require("../../scripts/source-files.cjs");

// 注释扫描复用源码范围，原连接文件和生成文件由公共收集器处理
const DEFENSIVE = /兜底|万一|免得|不值得|不用担心|只好|不是笔误|收摊|攥着/;
const EXTENSIONS = [
  ".java",
  ".js",
  ".mjs",
  ".cjs",
  ".vue",
  ".css",
  ".ps1",
  ".cmd",
  ".bat",
];
const errors = [];
let checked = 0;

// 记录注释的位置与问题类型，词组只用来定位具体表达
function inspectComment(
  text,
  filename,
  source,
  start,
) {
  const lines = text.split(/\r?\n/);
  let offset = start;
  for (const line of lines) {
    const body = line.replace(/\*\/\s*$/, "").trim();
    let message = "";
    if (/[。.]+$/.test(body)) {
      message = "注释行末不加句号";
    } else if (DEFENSIVE.test(body)) {
      message = "注释应直接说明功能、设计或边界";
    }
    if (message) {
      const lineNumber = source.substring(0, offset).split("\n").length;
      errors.push(filename + ":" + lineNumber + " " + message);
    }
    offset = offset + line.length + 1;
  }
}

// JS 使用语法解析器取得注释，Java、CSS 与脚本分别识别自己的注释定界符
function auditFile(filename, absolute) {
  const source = fs.readFileSync(absolute, "utf8");
  const extension = path.extname(filename);
  if ([".js", ".mjs", ".cjs"].includes(extension)) {
    const tree = parser.parse(source, { sourceType: "unambiguous" });
    for (const comment of tree.comments) {
      inspectComment(comment.value, filename, source, comment.start);
    }
  } else if (extension === ".vue") {
    for (const match of source.matchAll(/<!--[\s\S]*?-->/g)) {
      inspectComment(match[0].slice(4, -3), filename, source, match.index);
    }
    const script = source.match(/<script[^>]*>([\s\S]*?)<\/script>/);
    if (script) {
      const start = source.indexOf(script[1]);
      const tree = parser.parse(script[1], { sourceType: "unambiguous" });
      for (const comment of tree.comments) {
        inspectComment(comment.value, filename, source, start + comment.start);
      }
    }
  } else if (extension === ".java") {
    const tokens = source.matchAll(
      /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\//g,
    );
    for (const token of tokens) {
      if (token[0].startsWith("//") || token[0].startsWith("/*")) {
        inspectComment(token[0], filename, source, token.index);
      }
    }
  } else if (extension === ".css") {
    for (const match of source.matchAll(/\/\*[\s\S]*?\*\//g)) {
      inspectComment(match[0], filename, source, match.index);
    }
  } else {
    const lines = source.split(/\r?\n/);
    let start = 0;
    for (const line of lines) {
      if (/^\s*(#|rem\b)/i.test(line)) {
        inspectComment(line, filename, source, start);
      }
      start = start + line.length + 1;
    }
  }
  checked = checked + 1;
}

for (const file of sources.sourceFiles(EXTENSIONS)) {
  auditFile(file.relative, file.absolute);
}
if (errors.length > 0) {
  console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  console.log(
    "注释检查通过，共检查 " + checked + " 个文件，服务器连接文件按保留清单豁免",
  );
}
