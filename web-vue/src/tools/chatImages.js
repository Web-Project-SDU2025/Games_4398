// 图片先在浏览器缩放与压缩，单次请求的数量和体积与后端保持一致
export const CHAT_IMAGE_LIMITS = {
  count: 4,
  sourceBytes: 8388608,
  imageBytes: 262144,
  totalBytes: 786432,
  maximumEdge: 1280,
  maximumPixels: 20000000,
  quality: 0.72,
  previewEdge: 160,
  shrinkRatio: 0.75,
};
// 浏览器只接收常见光栅图片，压缩后统一发送 JPEG
const CHAT_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];

// 将本地文件读入内存，失败时返回可显示的文件名
function readImage(file) {
  // 文件读取的回调使用具名函数，完成后释放对读取器的引用
  function read(resolve, reject) {
    const reader = new FileReader();
    // FileReader 成功后返回浏览器生成的 Data URL
    function loaded() {
      resolve(reader.result);
    }
    // 读取失败时保留文件名便于用户重选
    function failed() {
      reject(new Error("图片读取失败：" + file.name));
    }
    reader.onload = loaded;
    reader.onerror = failed;
    reader.readAsDataURL(file);
  }
  return new Promise(read);
}

// 使用浏览器解码图像，后续绘制会移除原文件中的附加元数据
function decodeImage(source) {
  // 图片尺寸确定后才进入压缩，损坏文件按加载错误处理
  function decode(resolve, reject) {
    const image = new Image();
    // 返回已经可以绘制的图片对象
    function loaded() {
      resolve(image);
    }
    // 解码失败时结束当前导入
    function failed() {
      reject(new Error("图片格式无法读取"));
    }
    image.onload = loaded;
    image.onerror = failed;
    image.src = source;
  }
  return new Promise(decode);
}

// 等比缩小图像并铺设白底，透明 PNG 转 JPEG 后保持内容清晰
function compress(image, edge) {
  const scale = Math.min(
    1,
    edge / Math.max(image.naturalWidth, image.naturalHeight),
  );
  const canvas = document.createElement("canvas");
  canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
  canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
  const context = canvas.getContext("2d");
  context.fillStyle = "#ffffff";
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.drawImage(image, 0, 0, canvas.width, canvas.height);
  return canvas.toDataURL("image/jpeg", CHAT_IMAGE_LIMITS.quality);
}

// 计算 Base64 原始字节数，供压缩和总请求体检查共用
export function imageBytes(source) {
  const comma = source.indexOf(",");
  const encoded = source.substring(comma + 1);
  let padding = 0;
  if (encoded.endsWith("==")) {
    padding = 2;
  } else if (encoded.endsWith("=")) {
    padding = 1;
  }
  return Math.floor((encoded.length * 3) / 4) - padding;
}

// 导入一张图片，体积过大时继续缩小，结果包含发送图和历史缩略图
export async function prepareChatImage(file) {
  if (!CHAT_IMAGE_TYPES.includes(file.type)) {
    throw new Error("请选择 PNG、JPEG 或 WebP 图片");
  }
  if (file.size > CHAT_IMAGE_LIMITS.sourceBytes) {
    throw new Error(
      "单张原图最多 " + CHAT_IMAGE_LIMITS.sourceBytes / 1048576 + " MB",
    );
  }
  const source = await readImage(file);
  const image = await decodeImage(source);
  if (
    image.naturalWidth * image.naturalHeight >
    CHAT_IMAGE_LIMITS.maximumPixels
  ) {
    throw new Error("图片尺寸过大");
  }
  let edge = CHAT_IMAGE_LIMITS.maximumEdge;
  let encoded = compress(image, edge);
  while (imageBytes(encoded) > CHAT_IMAGE_LIMITS.imageBytes) {
    edge = Math.floor(edge * CHAT_IMAGE_LIMITS.shrinkRatio);
    encoded = compress(image, edge);
  }
  return {
    id: crypto.randomUUID(),
    name: file.name,
    source: encoded,
    preview: compress(image, CHAT_IMAGE_LIMITS.previewEdge),
  };
}
