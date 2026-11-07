import { ElMessageBox } from "element-plus";
import { showToast } from "./toast.js";

// 提示框使用独立标记，确认与取消的页面顺序和键盘顺序保持一致
const CONFIRM_BUTTON_CLASS = "platform-confirm-action";
const CANCEL_BUTTON_CLASS = "platform-cancel-action";

// 组件挂载后把确认按钮移动到取消按钮前，观察范围只处理带标记的提示框
function alignConfirmButtons() {
  const buttons = document.querySelectorAll("." + CONFIRM_BUTTON_CLASS);
  for (const confirm of buttons) {
    const parent = confirm.parentElement;
    const cancel = parent.querySelector("." + CANCEL_BUTTON_CLASS);
    if (cancel && parent.firstElementChild !== confirm) {
      parent.insertBefore(confirm, cancel);
    }
  }
}

// 显示页面提示，self 是当前页面，msg 是需要显示的文字
export function message(self, msg) {
  showToast(msg);
}

// 显示删除确认，用户确认时返回 true，取消时返回 false
export async function messageConform(content) {
  const observer = new MutationObserver(alignConfirmButtons);
  observer.observe(document.body, { childList: true, subtree: true });
  try {
    await ElMessageBox.confirm(content, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      confirmButtonClass: CONFIRM_BUTTON_CLASS,
      cancelButtonClass: CANCEL_BUTTON_CLASS,
      type: "warning",
    });
    return true;
  } catch (error) {
    // 取消时返回 false，由页面保留原数据
    return false;
  } finally {
    observer.disconnect();
  }
}
