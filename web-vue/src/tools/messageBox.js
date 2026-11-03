import { ElMessageBox } from "element-plus";

// 显示页面提示，self 是当前页面，msg 是需要显示的文字
export function message(self, msg) {
  self.$message({ message: msg, type: "warning" });
}

// 显示删除确认，用户确认时返回 true，取消时返回 false
export async function messageConform(content) {
  try {
    await ElMessageBox.confirm(content, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
    return true;
  } catch (error) {
    // 取消是正常选择，返回 false 后页面就不会继续执行删除
    return false;
  }
}
