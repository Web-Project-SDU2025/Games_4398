import { generalRequest } from "~/services/genServ";

// 获取当前账户可见的导航菜单，读取失败时交给调用方处理
export async function getMenuList() {
  const result = await generalRequest("/api/base/getMenuList", {});
  if (result.code !== 0) {
    throw new Error(result.msg);
  }
  return result.data;
}

// 提交当前账户的旧密码和新密码，返回处理结果
export function updatePassword(passwords) {
  return generalRequest("/api/base/updatePassword", passwords);
}
//获取菜单列表数据后台数据请求方法
export function getMenuTreeNodeList() {
  return generalRequest("/api/base/getMenuTreeNodeList", {});
}
//菜单删除后台数据请求方法
export function menuDelete(id) {
  return generalRequest("/api/base/menuDelete", {
    id: id,
  });
}
//菜单保存后台数据请求方法
export function menuSave(editType, node) {
  return generalRequest("/api/base/menuSave", {
    editType: editType,
    node: node,
  });
}
//获取字典树列表数据后台数据请求方法
export function getDictionaryTreeNodeList() {
  return generalRequest("/api/base/getDictionaryTreeNodeList", {});
}
//字典删除后台数据请求方法
export function dictionaryDelete(id) {
  return generalRequest("/api/base/dictionaryDelete", {
    id: id,
  });
}
//字典保存后台数据请求方法
export function dictionarySave(node) {
  return generalRequest("/api/base/dictionarySave", node);
}
