import assert from "node:assert/strict";
import test from "node:test";
import sources from "../../scripts/source-files.cjs";

// 根目录入口和手写迁移也接受检查，备份、生成代码和连接文件保持原样
test("工作区扫描覆盖独立入口和手写 SQL", function testSourceScope() {
  const files = sources.sourceFiles([".cmd", ".html", ".sql", ".xml", ".yml", ".js"]);
  const paths = new Set();
  for (const file of files) {
    paths.add(file.relative);
  }
  assert.ok(paths.has("package.cmd"));
  assert.ok(paths.has("web-vue/index.html"));
  assert.ok(paths.has("database/merge_user.sql"));
  assert.ok(paths.has("java-server/java-server/pom.xml"));
  assert.ok(!paths.has("database/schema.sql"));
  assert.ok(!paths.has("database/backups/before_user_merge.sql"));
  assert.ok(!paths.has("web-vue/public/games/java-player.js"));
  assert.ok(!paths.has("web-vue/games/joker-table/web/game.js"));
  assert.ok(!paths.has("java-server/java-server/src/main/resources/application.yml"));
});
