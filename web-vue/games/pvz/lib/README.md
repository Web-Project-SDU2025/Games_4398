# 项目依赖

本目录保存 Gson 2.11.0。独立 Java 工程中的 IntelliJ IDEA、`build.ps1` 和 `package.ps1` 共用这份依赖，开发者分发源码时应保留整个目录。

- 原始文件：[Maven Central 的 Gson 2.11.0](https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar)。
- SHA256：`57928D6E5A6EDEB2ABD3770A8F95BA44DCE45F3B23B7A9DC2B309C581552A78B`。
- Gson 采用 Apache License 2.0，原始许可文本见 `GSON-LICENSE`。项目代码与第三方依赖分别遵循各自的许可。

独立工程的 `pom.xml` 通过 Maven 的 `system` 依赖引用本目录中的 JAR，便于分发桌面程序源码。若开发者后续将项目发布为供其他项目引用的 Maven 库，应改用普通仓库依赖。
