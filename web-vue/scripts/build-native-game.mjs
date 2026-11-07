import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { inside } from "./game-project.mjs";

// 原生游戏只发布网页运行文件和来源说明，代码由 Vite 按内容生成指纹
export async function buildNativeGame(project, frontendRoot) {
  const require = createRequire(import.meta.url);
  const vite = require("vite");
  const output = path.join(frontendRoot, "public/games", project.manifest.id);
  const runtime = inside(project.root, project.build.web, "网页源码");
  fs.mkdirSync(output, { recursive: true });
  // 输出目录来自已验证的游戏编号，删除生成文件前再次核对实际路径
  const realOutput = fs.realpathSync(output);
  const publicRoot = fs.realpathSync(path.join(frontendRoot, "public/games"));
  if (path.dirname(realOutput) !== publicRoot) {
    throw new Error("原生游戏输出目录超出发布范围");
  }
  for (const name of ["runtime", "game.jar", "resources"]) {
    const target = inside(realOutput, name, "旧游戏发布文件");
    fs.rmSync(target, { recursive: true, force: true });
  }
  await vite.build({
    configFile: false,
    root: runtime,
    base: "./",
    build: {
      outDir: path.join(output, "runtime"),
      emptyOutDir: false,
      target: "es2020",
    },
  });
  const cover = inside(output, project.manifest.cover, "游戏封面");
  fs.mkdirSync(path.dirname(cover), { recursive: true });
  fs.copyFileSync(inside(project.root, project.build.cover, "源码封面"), cover);
  for (const file of project.build.licenses || []) {
    fs.copyFileSync(inside(project.root, file, "来源说明"), path.join(output, path.basename(file)));
  }
  fs.writeFileSync(path.join(output, "game.json"), JSON.stringify(project.manifest, null, 2) + "\n");
  console.log("原生网页游戏已构建：" + project.manifest.id);
}
