import { generalRequest } from "~/services/genServ";
//获取菜单列表数据后台数据请求方法
export async function getMenuTreeNodeList() {
  const result = await generalRequest("/api/base/getMenuTreeNodeList", {});
  return result;
}
//菜单删除后台数据请求方法
export async function menuDelete(id) {
  const result = await generalRequest("/api/base/menuDelete", {
    id: id,
  });
  return result;
}
//菜单保存后台数据请求方法
export async function menuSave(editType, node) {
  const result = await generalRequest("/api/base/menuSave", {
    editType: editType,
    node: node,
  });
  return result;
}
//获取字典树列表数据后台数据请求方法
export async function getDictionaryTreeNodeList() {
  const result = await generalRequest(
    "/api/base/getDictionaryTreeNodeList",
    {}
  );
  return result;
}
//字典删除后台数据请求方法
export async function dictionaryDelete(id) {
  const result = await generalRequest("/api/base/dictionaryDelete", {
    id: id,
  });
  return result;
}
//字典保存后台数据请求方法
export async function dictionarySave(node) {
  const result = await generalRequest("/api/base/dictionarySave", node);
  return result;
}
