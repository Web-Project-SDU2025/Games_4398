// 资源由网页后台获取并送入 Java 内存目录，下载管理器无需参与运行过程
export const RESOURCE_LIMITS = {
  maximumBytes: 134217728,
  timeoutMilliseconds: 60000,
  attempts: 2,
};

// 完整读取一个资源，服务器错误、返回网页或体积不符时直接显示资源名称
export async function readResource(address, fetchResource, expectedBytes) {
  let failure = null;
  for (
    let attempt = 0;
    attempt < RESOURCE_LIMITS.attempts;
    attempt = attempt + 1
  ) {
    const controller = new AbortController();
    // 当前请求超过限定时间时结束等待，由调用方显示具体阶段
    function expire() {
      controller.abort();
    }
    const timer = setTimeout(expire, RESOURCE_LIMITS.timeoutMilliseconds);
    try {
      const response = await fetchResource(address, {
        signal: controller.signal,
        cache: "no-cache",
        credentials: "same-origin",
        redirect: "error",
      });
      if (!response.ok) {
        throw new Error("资源请求失败：" + response.status + " " + address);
      }
      const type = response.headers.get("content-type") || "";
      if (type.includes("text/html")) {
        throw new Error("资源地址返回了网页：" + address);
      }
      const buffer = await response.arrayBuffer();
      if (
        buffer.byteLength === 0 ||
        buffer.byteLength > RESOURCE_LIMITS.maximumBytes
      ) {
        throw new Error("资源体积不正确：" + address);
      }
      if (expectedBytes && buffer.byteLength !== expectedBytes) {
        throw new Error("资源未完整加载：" + address);
      }
      return new Uint8Array(buffer);
    } catch (error) {
      failure = error;
    } finally {
      clearTimeout(timer);
    }
  }
  throw new Error("游戏资源加载失败：" + address + "，" + failure.message);
}

// 按发布配置预载全部 JAR 与素材包，成功后返回虚拟路径，失败时释放本次内存文件
export async function loadGameResources(
  configuration,
  fetchResource,
  addFile,
  removeFile,
  report,
) {
  const base = "/games/" + configuration.id + "/";
  const memoryFiles = [];
  const classpath = [];
  let assets = "";
  const names = [];
  for (const name of configuration.classpath) {
    names.push(name);
  }
  if (configuration.assets) {
    names.push(configuration.assets);
  }
  try {
    for (let index = 0; index < names.length; index = index + 1) {
      const name = names[index];
      let relative = name;
      let expectedBytes = 0;
      if (configuration.resources[name]) {
        relative = configuration.resources[name].path;
        expectedBytes = configuration.resources[name].bytes;
      }
      report("正在自动加载游戏资源 " + (index + 1) + " / " + names.length);
      const bytes = await readResource(
        base + relative,
        fetchResource,
        expectedBytes,
      );
      let virtual = "/str/4398-" + configuration.id + "-" + index + ".jar";
      if (name === configuration.assets) {
        virtual = "/str/4398-" + configuration.id + "-assets.zip";
        assets = virtual;
      } else {
        classpath.push(virtual);
      }
      await addFile(virtual, bytes);
      memoryFiles.push(virtual);
    }
    return { classpath: classpath, assets: assets, memoryFiles: memoryFiles };
  } catch (error) {
    for (const filename of memoryFiles) {
      removeFile(filename);
    }
    throw error;
  }
}
