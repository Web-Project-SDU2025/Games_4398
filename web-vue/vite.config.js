import path from "path";
import vue from "@vitejs/plugin-vue";

import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";

import Unocss from "unocss/vite";
import { presetIcons } from "unocss";

// 源码别名指向的目录
const SOURCE_PATH = path.resolve(__dirname, "src");
// 前端构建输出目录，后端打包时读取此处的静态文件
const PUBLIC_PATH = path.resolve(
  __dirname,
  "../java-server/java-server/src/main/resources/public",
);
// 本地开发服务转发业务请求使用的后端地址
const BACKEND_ADDRESS = "http://localhost:22222";
// 前端开发服务监听端口
const FRONTEND_PORT = 3000;

export default {
  resolve: {
    alias: {
      "~/": SOURCE_PATH + "/",
    },
  },
  define: { "process.env": {} },
  plugins: [
    vue(),
    Components({
      extensions: ["vue"],
      include: [/\.vue$/, /\.vue\?vue/],
      resolvers: [
        ElementPlusResolver({
          importStyle: "css",
        }),
      ],
      dts: "src/components.d.ts",
    }),

    // 页面样式由 CSS 文件维护，UnoCSS 只生成已有图标类
    Unocss({
      presets: [
        presetIcons({
          scale: 1.2,
          warn: true,
        }),
      ],
    }),
  ],
  build: {
    // 网页随后端 JAR 部署，保留后端目录中的其他静态文件
    outDir: PUBLIC_PATH,
    assetsDir: "assets",
    cssCodeSplit: true,
    sourcemap: false,

    // 生产环境取消 console
    minify: "terser",
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
      },
    },

    rollupOptions: {
      output: {
        chunkFileNames: "js/[name]-[hash].js",
        entryFileNames: "js/[name]-[hash].js",
        assetFileNames: "[ext]/[name]-[hash].[ext]",
      },
    },
  },
  server: {
    port: FRONTEND_PORT,
    proxy: {
      "/api": {
        target: BACKEND_ADDRESS,
        // target: "http://202.194.7.29:22222",
      },
      "/auth": {
        target: BACKEND_ADDRESS,
        // target: "http://202.194.7.29:22222",
      },
    },
  },
};
