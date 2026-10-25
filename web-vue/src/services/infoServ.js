import { generalRequest } from "./genServ";

// 修改口令，参数包括原密码和新密码。
export function updatePassword(passwords) {
  return generalRequest("/api/base/updatePassword", passwords);
}
