import { marked } from "marked";
import DOMPurify from "dompurify";

const MARKDOWN_OPTIONS = { breaks: true, gfm: true, async: false };
const HTML_OPTIONS = {
  ALLOWED_TAGS: [
    "p",
    "br",
    "strong",
    "em",
    "del",
    "blockquote",
    "ul",
    "ol",
    "li",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "pre",
    "code",
    "table",
    "thead",
    "tbody",
    "tr",
    "th",
    "td",
    "hr",
    "a",
  ],
  ALLOWED_ATTR: ["href", "title", "start", "align"],
  ALLOW_DATA_ATTR: false,
  ALLOW_ARIA_ATTR: false,
};

// 使用参考项目的解析器与清理库显示回复，只允许常用排版，不执行模型返回的 HTML
export function renderMarkdown(container, text) {
  const html = marked.parse(text, MARKDOWN_OPTIONS);
  container.innerHTML = DOMPurify.sanitize(html, HTML_OPTIONS);
  prepareLinks(container);
  prepareScrollAreas(container);
}

// 链接只允许网页协议，新窗口打开时不向目标页面交出本站窗口
function prepareLinks(container) {
  const links = container.querySelectorAll("a");
  for (const link of links) {
    const href = link.getAttribute("href");
    try {
      if (!href) {
        continue;
      }
      const address = new URL(href, window.location.href);
      if (address.protocol !== "https:" && address.protocol !== "http:") {
        link.removeAttribute("href");
        continue;
      }
      link.href = address.href;
      link.target = "_blank";
      link.rel = "noopener noreferrer";
    } catch (error) {
      link.removeAttribute("href");
    }
  }
}

// 宽表格和代码块在自己的区域横向滚动，避免把手机上的整个页面撑宽
function prepareScrollAreas(container) {
  const tables = container.querySelectorAll("table");
  for (const table of tables) {
    const wrapper = document.createElement("div");
    wrapper.className = "support-table-scroll";
    wrapper.tabIndex = 0;
    wrapper.setAttribute("role", "region");
    wrapper.setAttribute("aria-label", "回复中的表格");
    table.before(wrapper);
    wrapper.appendChild(table);
  }
  const codeBlocks = container.querySelectorAll("pre");
  for (const block of codeBlocks) {
    block.tabIndex = 0;
    block.setAttribute("role", "region");
    block.setAttribute("aria-label", "回复中的代码");
  }
}

// 首次显示或回复正文改变时才解析，输入框和时钟更新不会反复重建旧回复
function updateMarkdown(element, binding) {
  if (binding.value !== binding.oldValue) {
    renderMarkdown(element, binding.value);
  }
}

export const markdownDirective = {
  mounted: updateMarkdown,
  updated: updateMarkdown,
};
