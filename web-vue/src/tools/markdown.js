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

// 先解析回复再按标签白名单清理，保留常用 Markdown 排版
export function renderMarkdown(container, text) {
  const html = marked.parse(text, MARKDOWN_OPTIONS);
  container.innerHTML = DOMPurify.sanitize(html, HTML_OPTIONS);
  prepareLinks(container);
  prepareScrollAreas(container);
}

// 按网页协议校验链接，新窗口使用独立浏览上下文
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

// 为宽表格和代码块提供独立滚动区域，键盘焦点可进入该区域
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

// 首次显示或正文改变时解析回复，其他状态更新复用已有内容
function updateMarkdown(element, binding) {
  if (binding.value !== binding.oldValue) {
    renderMarkdown(element, binding.value);
  }
}

export const markdownDirective = {
  mounted: updateMarkdown,
  updated: updateMarkdown,
};
