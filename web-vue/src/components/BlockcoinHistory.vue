<template>
  <section class="blockcoin-history" aria-labelledby="blockcoin-history-title">
    <header>
      <h2 id="blockcoin-history-title">字块币足迹</h2>
      <span>最近 {{ history.length }} 笔收支</span>
    </header>
    <ol>
      <li v-for="entry in history" :key="entry.entryId">
        <span
          class="blockcoin-entry-icon"
          :class="{ 'is-income': entry.amount > 0 }"
          aria-hidden="true"
        >
          <span v-if="entry.amount > 0" class="i-ep-top-right"></span>
          <span v-else class="i-ep-bottom-right"></span>
        </span>
        <div>
          <strong>{{ entry.reason }}</strong>
          <time>{{ formatTime(entry.createdAt) }}</time>
        </div>
        <span
          class="blockcoin-entry-amount"
          :class="{ 'is-income': entry.amount > 0 }"
        >
          <span v-if="entry.amount > 0">+</span
          >{{ formatCoins(entry.amount) }} BC
        </span>
        <small>余额 {{ formatCoins(entry.balanceAfter) }}</small>
      </li>
    </ol>
  </section>
</template>

<script>
import { coinAmount, transactionTime } from "../logic/store.js";

export default {
  props: { history: { type: Array, required: true } },
  methods: {
    formatCoins: coinAmount,
    formatTime: transactionTime,
  },
};
</script>
