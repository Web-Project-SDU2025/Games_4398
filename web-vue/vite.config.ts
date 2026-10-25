import path from "path";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";

import Unocss from "unocss/vite";
import {
  presetAttributify,
  presetIcons,
  presetUno,
  transformerDirectives,
  transformerVariantGroup,
} from "unocss";

const pathSrc = path.resolve(__dirname, "src");

// https://vitejs.dev/config/
export default defineConfig({
  resolve: {
    alias: {
      "~/": `${pathSrc}/`,
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@use "~/styles/element/index.scss" as *;`,
      },
    },
  },
  define: { "process.env": {} },
  plugins: [
    vue(),
    Components({
      // allow auto load markdown components under `./src/components/`
      extensions: ["vue", "md"],
      // allow auto import and register components used in markdown
      include: [/\.vue$/, /\.vue\?vue/, /\.md$/],
      resolvers: [
        ElementPlusResolver({
          importStyle: "sass",
        }),
      ],
      dts: "src/components.d.ts",
    }),

    // https://github.com/antfu/unocss
    // see unocss.config.ts for config
    Unocss({
      presets: [
        presetUno(),
        presetAttributify(),
        presetIcons({
          scale: 1.2,
          warn: true,
        }),
      ],
      transformers: [transformerDirectives(), transformerVariantGroup()],
    }),
  ],
  build: {
    // 将网页打包到当前后端项目中，随 JAR 一起部署。
    outDir: path.resolve(__dirname, "../java-server/java-server/src/main/resources/public"), //指定打包输出路径
    assetsDir: "assets", //指定静态资源存放路径
    cssCodeSplit: true, //css代码拆分,禁用则所有样式保存在一个css里面
    sourcemap: false, //是否构建source map 文件

    // 生产环境取消 console
    minify: "terser",
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
      },
    },

    //会打包出 css js 等文件夹 目录显得清晰
    rollupOptions: {
      output: {
        chunkFileNames: "js/[name]-[hash].js",
        entryFileNames: "js/[name]-[hash].js",
        assetFileNames: "[ext]/[name]-[hash].[ext]",
      },
    },
  },
  server: {
    port: 3000,
    proxy: {
      "/api": {
        target: "http://localhost:22222",
        // target: "http://202.194.7.29:22222",
      },
      "/auth": {
        target: "http://localhost:22222",
        // target: "http://202.194.7.29:22222",
      },
    },
  },
});
