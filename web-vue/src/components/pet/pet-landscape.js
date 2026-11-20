import { CanvasTexture, SRGBColorSpace } from "three";

// 贴图保持窗玻璃的比例，绘画坐标以左上角为起点
const LANDSCAPE_SIZE = { width: 480, height: 576 };
// 日月共用同一片天空，夜里的碎星避开月牙和可点的星位
const SKY_DISK = { x: 365, y: 122, radius: 36, clearRadius: 60 };
// 月牙由偏移的小圆遮掉圆盘右上侧，遮去的部分仍保留天空
const MOON_CUTOUT = { horizontal: 17, vertical: -14, radius: 31 };
// 碎星和水纹的密度，固定分布使重进小屋时景色保持一致
const DETAIL_COUNTS = { stars: 44, ripples: 88, reflections: 17 };
// 明信片复用窗外夜景，在原画上用金墨印出连成的星轨
const POSTCARD_INK = { line: "#ffd28e", star: "#fff2bd", width: 2.5, starRadius: 5 };
// 山脊按从后到前的顺序排列，最后一层在村落后面收成缓坡
const RIDGES = [
  [[0, 241], [52, 213], [91, 168], [113, 196], [140, 206], [184, 239], [232, 192], [271, 156], [305, 188], [327, 195], [360, 232], [420, 190], [480, 224]],
  [[0, 283], [33, 252], [82, 222], [122, 262], [177, 274], [223, 249], [260, 226], [288, 243], [344, 279], [402, 252], [448, 224], [480, 242]],
  [[0, 329], [66, 313], [124, 326], [181, 314], [222, 322], [272, 311], [322, 307], [381, 315], [433, 292], [480, 301]],
];
// 岸线和村落的位置横跨两扇窗，避免所有细节落在窗棂后面
const SHORE = [[0, 359], [65, 348], [132, 365], [184, 369], [228, 362], [281, 368], [337, 354], [395, 357], [480, 344], [480, 384], [0, 386]];
// 每间房屋记录横向位置、地基高度和宽度
const HOUSES = [[83, 359, 24], [116, 366, 29], [151, 369, 19], [259, 367, 23], [289, 366, 33], [328, 360, 22], [390, 355, 25]];
// 云带由左到右分布，元组依次是横向位置、高度和长度
const CLOUDS = [[52, 108, 99], [147, 136, 108], [264, 87, 75], [401, 182, 59], [32, 195, 52]];
// 每根近岸松树记录位置、地面高度和树高
const PINES = [[9, 497, 96], [36, 501, 70], [61, 512, 79], [87, 526, 55], [114, 551, 62], [401, 557, 52], [429, 529, 79], [457, 510, 112], [476, 516, 86]];
// 各时段独立配色，村屋、远山、水面和松林有各自的亮度层次
const LANDSCAPES = {
  morning: {
    sky: ["#599bc4", "#acd3df", "#ffe9bf"], glow: "#fff0b7ba", disk: "#fff0b0", cloud: "#fff6e5a3",
    mountains: ["#a9c4d0", "#739da9", "#548374"], ridgeLight: "#e6e9d8a8", shore: "#4b7266",
    lake: ["#b6d5cd", "#7dabae", "#427982"], ripple: "#dbede5", forest: "#214c49",
    wall: "#8c6560", roof: "#624c4e", window: "#fff4bb",
  },
  evening: {
    sky: ["#695c89", "#e49a98", "#ffcc92"], glow: "#ffbb72b5", disk: "#ffdf92", cloud: "#ffdeae95",
    mountains: ["#b78692", "#8a6f8a", "#5a596f"], ridgeLight: "#ffe2c294", shore: "#514e62",
    lake: ["#d3979e", "#a4849c", "#535f81"], ripple: "#ffd4ab", forest: "#303f56",
    wall: "#845d63", roof: "#493a53", window: "#ffd788",
  },
  night: {
    sky: ["#101c37", "#283b5d", "#546784"], glow: "#c9daf450", disk: "#f3ecc7", cloud: "#9dafca48",
    mountains: ["#485c79", "#354b66", "#283d52"], ridgeLight: "#aebfd37a", shore: "#243942",
    lake: ["#546d82", "#314c65", "#1e364e"], ripple: "#a9cbe1", forest: "#152b36",
    wall: "#3f4656", roof: "#252e42", window: "#ffd17b",
  },
};

// 把一组像素坐标填成闭合轮廓，供山脊、屋顶和树冠共用
function polygon(painter, color, points) {
  painter.fillStyle = color;
  painter.beginPath();
  painter.moveTo(points[0][0], points[0][1]);
  for (let index = 1; index < points.length; index = index + 1) {
    painter.lineTo(points[index][0], points[index][1]);
  }
  painter.closePath();
  painter.fill();
}

// 画三段纵向渐变，使地平线和前景平滑过渡
function verticalGradient(painter, colors, top, bottom) {
  const gradient = painter.createLinearGradient(0, top, 0, bottom);
  gradient.addColorStop(0, colors[0]);
  gradient.addColorStop(0.55, colors[1]);
  gradient.addColorStop(1, colors[2]);
  return gradient;
}

// 用不规则的细云和日月光晕给天空留出可辨认的层次
function paintSky(painter, colors, night) {
  painter.fillStyle = verticalGradient(painter, colors.sky, 0, 350);
  painter.fillRect(0, 0, LANDSCAPE_SIZE.width, LANDSCAPE_SIZE.height);
  const horizontal = SKY_DISK.x;
  const vertical = SKY_DISK.y;
  const halo = painter.createRadialGradient(horizontal, vertical, 12, horizontal, vertical, 105);
  halo.addColorStop(0, colors.glow);
  halo.addColorStop(1, "#ffffff00");
  painter.fillStyle = halo;
  painter.fillRect(horizontal - 105, vertical - 105, 210, 210);
  for (const cloud of CLOUDS) {
    const left = cloud[0];
    const top = cloud[1];
    const width = cloud[2];
    polygon(painter, colors.cloud, [[left, top], [left + width * 0.27, top - 5], [left + width * 0.5, top - 13], [left + width * 0.73, top - 6], [left + width, top - 2], [left + width * 0.42, top + 4]]);
  }
  paintDisk(painter, colors.disk, night);
  if (night) {
    paintDistantStars(painter, colors.disk);
  }
}

// 画日轮或月牙，裁剪到圆盘内以免遮月的圆露出多余亮边
function paintDisk(painter, color, night) {
  const fullTurn = Math.PI * 2;
  painter.fillStyle = color;
  painter.beginPath();
  painter.arc(SKY_DISK.x, SKY_DISK.y, SKY_DISK.radius, 0, fullTurn);
  if (!night) {
    painter.fill();
    return;
  }
  painter.save();
  painter.clip();
  const centerX = SKY_DISK.x + MOON_CUTOUT.horizontal;
  const centerY = SKY_DISK.y + MOON_CUTOUT.vertical;
  painter.moveTo(centerX + MOON_CUTOUT.radius, centerY);
  painter.arc(centerX, centerY, MOON_CUTOUT.radius, 0, fullTurn);
  painter.fill("evenodd");
  painter.restore();
}

// 用固定分布绘制碎星，让同一幅夜景每次打开都一样
function paintDistantStars(painter, color) {
  painter.fillStyle = color;
  for (let index = 0; index < DETAIL_COUNTS.stars; index = index + 1) {
    const horizontal = 13 + index * 97 % 456;
    const vertical = 16 + index * 53 % 187;
    const distance = Math.hypot(horizontal - SKY_DISK.x, vertical - SKY_DISK.y);
    if (distance < SKY_DISK.clearRadius) {
      continue;
    }
    painter.globalAlpha = 0.24 + (index % 5) * 0.13;
    const size = 1 + index % 3;
    painter.fillRect(horizontal, vertical, size, size);
  }
  painter.globalAlpha = 1;
}

// 每一层远山只画自己的山脊，亮面提示清晨、夕阳或月光的方向
function paintMountains(painter, colors) {
  for (let index = 0; index < RIDGES.length; index = index + 1) {
    const outline = RIDGES[index].slice();
    outline.push([480, 386], [0, 386]);
    polygon(painter, colors.mountains[index], outline);
    if (index === 0) {
      polygon(painter, colors.ridgeLight, [[58, 207], [91, 168], [113, 196], [105, 193], [99, 205], [95, 186], [81, 197]]);
      polygon(painter, colors.ridgeLight, [[234, 190], [271, 156], [305, 188], [289, 183], [283, 196], [274, 172], [257, 181], [250, 196]]);
    }
  }
}

// 水纹远处细密、近处变长，日月和村灯在湖面留下断开的倒影
function paintLake(painter, colors) {
  painter.fillStyle = verticalGradient(painter, colors.lake, 370, 576);
  painter.fillRect(0, 370, LANDSCAPE_SIZE.width, 206);
  painter.fillStyle = colors.ripple;
  for (let index = 0; index < DETAIL_COUNTS.ripples; index = index + 1) {
    const depth = index * 43 % 189;
    const horizontal = index * 79 % LANDSCAPE_SIZE.width;
    const width = 6 + depth / 6 + index % 17;
    painter.globalAlpha = 0.12 + index % 4 * 0.054;
    painter.fillRect(horizontal, 386 + depth, width, 1 + depth / 110);
  }
  painter.fillStyle = colors.disk;
  for (let row = 0; row < DETAIL_COUNTS.reflections; row = row + 1) {
    const width = 11 + row * 5 + row % 3 * 10;
    const left = SKY_DISK.x - width / 2 + row % 4 * 4 - 8;
    painter.globalAlpha = 0.46 - row * 0.019;
    painter.fillRect(left, 393 + row * 10, width, 1.5 + row / 9);
  }
  painter.globalAlpha = 1;
}

// 两岸房屋尺寸略有差别，屋顶、窗户与水中的灯光各画一层
function paintVillage(painter, colors, night) {
  polygon(painter, colors.shore, SHORE);
  for (const house of HOUSES) {
    const left = house[0];
    const ground = house[1];
    const width = house[2];
    const height = width * 0.67;
    const top = ground - height;
    painter.fillStyle = colors.wall;
    painter.fillRect(left, top, width, height);
    polygon(painter, colors.roof, [[left - 4, top], [left + width * 0.39, top - height * 0.67], [left + width + 4, top], [left + width, top + 3], [left, top + 3]]);
    painter.fillStyle = colors.window;
    painter.fillRect(left + width * 0.2, top + 6, 4, 6);
    painter.fillRect(left + width * 0.65, top + 6, 4, 6);
    painter.fillStyle = colors.roof;
    painter.fillRect(left + width * 0.44, ground - 8, 4, 8);
    if (night) {
      painter.fillStyle = colors.window;
      painter.globalAlpha = 0.43;
      painter.fillRect(left + width * 0.2, 387, 10, 2);
      painter.globalAlpha = 0.24;
      painter.fillRect(left + width * 0.2 - 2, 392, 14, 1.5);
      painter.globalAlpha = 1;
    }
  }
}

// 用三层下垂树冠画松树，比单个三角形更容易辨认
function paintPine(painter, color, horizontal, ground, height) {
  painter.fillStyle = color;
  painter.fillRect(horizontal - 1.5, ground - height * 0.55, 3, height * 0.55);
  for (let layer = 0; layer < 3; layer = layer + 1) {
    const top = ground - height + layer * height * 0.18;
    const width = height * (0.12 + layer * 0.036);
    const bottom = top + height * 0.49;
    polygon(painter, color, [[horizontal, top], [horizontal - width, bottom], [horizontal, bottom - height * 0.08], [horizontal + width, bottom]]);
  }
}

// 近处树和岸线框住湖面，中间留空让倒影还能看清
function paintForeground(painter, colors) {
  const forest = colors.forest;
  polygon(painter, forest, [[0, 484], [28, 481], [75, 501], [120, 536], [157, 576], [0, 576]]);
  polygon(painter, forest, [[480, 490], [453, 488], [431, 510], [395, 535], [376, 576], [480, 576]]);
  for (const tree of PINES) {
    paintPine(painter, forest, tree[0], tree[1], tree[2]);
  }
}

// 返回一个时段的完整纹理，渲染时不再重画湖畔细节
export function drawLandscape(key) {
  const canvas = document.createElement("canvas");
  canvas.width = LANDSCAPE_SIZE.width;
  canvas.height = LANDSCAPE_SIZE.height;
  const painter = canvas.getContext("2d");
  const colors = LANDSCAPES[key];
  const night = key === "night";
  paintSky(painter, colors, night);
  paintMountains(painter, colors);
  paintLake(painter, colors);
  paintVillage(painter, colors, night);
  paintForeground(painter, colors);
  const texture = new CanvasTexture(canvas);
  texture.colorSpace = SRGBColorSpace;
  return texture;
}

// 从夜景底图制作明信片插画，不往游戏中正在使用的纹理上写入
export function printNightLandscape(image, stars, pane) {
  const canvas = document.createElement("canvas");
  canvas.width = LANDSCAPE_SIZE.width;
  canvas.height = LANDSCAPE_SIZE.height;
  const painter = canvas.getContext("2d");
  painter.drawImage(image, 0, 0);
  const points = [];
  for (const star of stars) {
    const horizontal = (0.5 + star[0] / pane.width) * canvas.width;
    const vertical = (0.5 - star[1] / pane.height) * canvas.height;
    points.push([horizontal, vertical]);
  }
  painter.strokeStyle = POSTCARD_INK.line;
  painter.lineWidth = POSTCARD_INK.width;
  painter.beginPath();
  painter.moveTo(points[0][0], points[0][1]);
  for (let index = 1; index < points.length; index = index + 1) {
    painter.lineTo(points[index][0], points[index][1]);
  }
  painter.stroke();
  painter.fillStyle = POSTCARD_INK.star;
  for (const point of points) {
    painter.beginPath();
    painter.arc(point[0], point[1], POSTCARD_INK.starRadius, 0, Math.PI * 2);
    painter.fill();
  }
  return canvas.toDataURL("image/png");
}
