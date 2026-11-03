<template>
  <div class="dashboard-chart">
    <VChart
      :option="option"
      autoresize
      aria-label="最近七天个人游玩时长，单位为分钟"
    />
    <details class="dashboard-chart-data">
      <summary>查看每日数据</summary>
      <dl>
        <div v-for="day in activity" :key="day.date">
          <dt>{{ day.label }}</dt>
          <dd>{{ day.minutes.toFixed(1) }} 分钟</dd>
        </div>
      </dl>
    </details>
  </div>
</template>

<script>
import { use } from "echarts/core";
import { LineChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import VChart from "vue-echarts";

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer]);

// 读取指定颜色变量并去掉空格，styles 是工作区的计算样式
function readColor(styles, name) {
  const value = styles.getPropertyValue(name);
  return value.trim();
}

// 根据七天统计生成折线图参数，图表颜色从当前工作区样式读取
function activityOption() {
  const labels = [];
  const minutes = [];
  for (const day of this.activity) {
    labels.push(day.label);
    const roundedMinutes = day.minutes.toFixed(1);
    minutes.push(Number(roundedMinutes));
  }
  const shell = document.querySelector(".desktop-shell");
  const styles = getComputedStyle(shell);
  const accent = readColor(styles, "--color-accent");
  const muted = readColor(styles, "--color-muted");
  const rule = readColor(styles, "--color-rule");
  const surface = readColor(styles, "--color-paper-2");
  const ink = readColor(styles, "--color-ink");
  return {
    animation: false,
    grid: { left: 40, right: 16, top: 24, bottom: 32 },
    tooltip: {
      trigger: "axis",
      confine: true,
      backgroundColor: surface,
      borderColor: rule,
      textStyle: { color: ink },
    },
    xAxis: {
      type: "category",
      data: labels,
      boundaryGap: false,
      axisLine: { lineStyle: { color: rule } },
      axisTick: { show: false },
      axisLabel: { color: muted, hideOverlap: true },
    },
    yAxis: {
      type: "value",
      minInterval: 1,
      axisLabel: { color: muted },
      splitLine: { lineStyle: { color: rule, type: "dashed" } },
    },
    series: [
      {
        name: "游玩分钟",
        type: "line",
        data: minutes,
        symbolSize: 6,
        lineStyle: { color: accent, width: 2 },
        itemStyle: { color: accent },
      },
    ],
  };
}

export default {
  components: { VChart: VChart },
  props: { activity: { type: Array, required: true } },
  computed: { option: activityOption },
};
</script>
