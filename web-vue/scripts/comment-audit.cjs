const fs = require("node:fs");
const path = require("node:path");
const parser = require("@babel/parser");
const sources = require("../../scripts/source-files.cjs");

// 扫描项目维护的源码，文件保留和生成目录沿用公共清单
const DEFENSIVE = /兜底|万一|免得|不值得|不用担心|只好|不是笔误|收摊|攥着/;
const EXTENSIONS = [
  ".java",
  ".js",
  ".mjs",
  ".cjs",
  ".cs",
  ".html",
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
  const lines = text.split("\n");
  let lineNumber = source.substring(0, start).split("\n").length;
  for (const line of lines) {
    const body = line.replace(/\*\/\s*$/, "").trim();
    let message = "";
    if (/[。.]+$/.test(body)) {
      message = "注释行末不加句号";
    } else if (DEFENSIVE.test(body)) {
      message = "注释应直接说明功能、设计或边界";
    }
    if (message) {
      errors.push(filename + ":" + lineNumber + " " + message);
    }
    lineNumber = lineNumber + 1;
  }
}

// 按文件类型识别注释，避免把字符串和页面内容算作注释
function auditFile(filename, absolute) {
  const source = fs.readFileSync(absolute, "utf8");
  const extension = path.extname(filename);
  if ([".js", ".mjs", ".cjs"].includes(extension)) {
    const tree = parser.parse(source, { sourceType: "unambiguous" });
    for (const comment of tree.comments) {
      inspectComment(comment.value, filename, source, comment.start);
    }
  } else if (extension === ".vue" || extension === ".html") {
    for (const match of source.matchAll(/<!--[\s\S]*?-->/g)) {
      inspectComment(match[0].slice(4, -3), filename, source, match.index);
    }
    for (const script of source.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)) {
      const start = script.index + script[0].indexOf(">") + 1;
      const tree = parser.parse(script[1], { sourceType: "unambiguous" });
      for (const comment of tree.comments) {
        inspectComment(comment.value, filename, source, start + comment.start);
      }
    }
    for (const style of source.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)) {
      const start = style.index + style[0].indexOf(">") + 1;
      for (const comment of style[1].matchAll(/\/\*[\s\S]*?\*\//g)) {
        inspectComment(comment[0], filename, source, start + comment.index);
      }
    }
  } else if (extension === ".java" || extension === ".cs") {
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
    const lines = source.split("\n");
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
