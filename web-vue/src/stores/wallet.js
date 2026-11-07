import { defineStore } from "pinia";
import { getStore } from "../services/storeServ.js";
import { useAppStore } from "./app.js";

export const useWalletStore = defineStore("blockcoinWallet", {
  // 只显示后端余额，账户切换时重新读取
  state: function walletState() {
    return {
      owner: "",
      balance: null,
      history: [],
      games: [],
      initialBalance: 0,
      notice: "",
      pending: null,
    };
  },
  actions: {
    // 清空旧账户显示，后端持久余额保持原值
    reset: function resetWallet() {
      this.balance = null;
      this.history = [];
      this.games = [];
      this.initialBalance = 0;
      this.notice = "";
      this.pending = null;
    },
    // 相同账户的同时刷新共用请求，迟到响应按令牌丢弃
    refresh: async function refreshWallet() {
      const token = useAppStore().userInfo.token;
      if (this.owner !== token) {
        this.reset();
        this.owner = token;
      }
      if (this.pending) {
        return this.pending;
      }
      const task = this.read(token);
      this.pending = task;
      try {
        return await task;
      } finally {
        if (this.owner === token) {
          this.pending = null;
        }
      }
    },
    // 网络失败保留已有余额，返回数据只写回同一账户
    read: async function readWallet(token) {
      try {
        const result = await getStore();
        if (token !== useAppStore().userInfo.token) {
          return;
        }
        if (result.code !== 0) {
          throw new Error(result.msg);
        }
        this.balance = result.data.balance;
        this.games = result.data.games;
        this.history = result.data.history;
        this.initialBalance = result.data.initialBalance;
        this.notice = "";
      } catch (error) {
        if (token === useAppStore().userInfo.token) {
          this.notice = error.message || "字块币账户暂时无法读取";
        }
      }
    },
  },
});
