<template>
  <div class="dashboard-chart">
    <div class="dashboard-chart-caption">
      <strong>{{ totalHours }}<small>小时</small></strong>
      <span>游玩时长</span>
      <fieldset class="dashboard-activity-intervals" aria-label="统计粒度">
        <label v-for="interval in intervals" :key="interval.hours">
          <input
            v-model="intervalHours"
            type="radio"
            name="activity-interval"
            :value="interval.hours"
          />
          <span>{{ interval.label }}</span>
        </label>
      </fieldset>
    </div>
    <VChart :option="option" autoresize :aria-label="chartLabel" />
  </div>
</template>

<script>
import { use } from "echarts/core";
import { LineChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import VChart from "vue-echarts";
import {
  DURATION_DECIMALS,
  ACTIVITY_INTERVALS,
  DEFAULT_ACTIVITY_HOURS,
  aggregateActivity,
} from "../logic/dashboard.js";

// 活动图内部边距，预留坐标轴文字区域
const CHART_GRID = { left: 40, right: 16, top: 24, bottom: 32 };
// 折线上的数据点直径，单位为像素
const POINT_SIZE = 6;
// 折线宽度，单位为像素
const LINE_WIDTH = 2;
// 纵轴刻度的最小间隔，单位为分钟
const MINIMUM_TICK_MINUTES = 1;

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer]);

// 读取指定颜色变量并去掉空格，styles 是工作区的计算样式
function readColor(
  styles,
  name,
) {
  const value = styles.getPropertyValue(name);
  return value.trim();
}

// 根据当前时段统计生成折线图参数，图表颜色从当前工作区样式读取
function activityOption() {
  const labels = [];
  const minutes = [];
  for (const period of this.activity) {
    labels.push(period.label);
    const roundedMinutes = period.minutes.toFixed(DURATION_DECIMALS);
    minutes.push(Number(roundedMinutes));
  }
  let shell = document.querySelector(".desktop-shell");
  if (!shell) {
    shell = document.documentElement;
  }
  const styles = getComputedStyle(shell);
  const accent = readColor(styles, "--color-accent");
  const muted = readColor(styles, "--color-muted");
  const rule = readColor(styles, "--color-rule");
  const surface = readColor(styles, "--color-paper-2");
  const ink = readColor(styles, "--color-ink");
  return {
    animation: false,
    grid: CHART_GRID,
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
      minInterval: MINIMUM_TICK_MINUTES,
      axisLabel: { color: muted },
      splitLine: { lineStyle: { color: rule, type: "dashed" } },
    },
    series: [
      {
        name: "游玩分钟",
        type: "line",
        data: minutes,
        symbolSize: POINT_SIZE,
        lineStyle: { color: accent, width: LINE_WIDTH },
        itemStyle: { color: accent },
      },
    ],
  };
}

export default {
  components: { VChart: VChart },
  props: {
    records: { type: Array, required: true },
    generatedAt: { type: Number, required: true },
    totalHours: { type: String, required: true },
  },
  // 切换只重算本地记录，保留当前图表范围与时长精度
  data: function activityState() {
    return {
      intervals: ACTIVITY_INTERVALS,
      intervalHours: DEFAULT_ACTIVITY_HOURS,
    };
  },
  computed: {
    option: activityOption,
    // 按所选粒度汇总图表数据，刷新记录时保持当前选择
    activity: function activity() {
      const now = new Date(this.generatedAt);
      return aggregateActivity(this.records, now, this.intervalHours);
    },
    // 图表的可访问名称同步统计粒度和时长单位
    chartLabel: function chartLabel() {
      for (const interval of this.intervals) {
        if (interval.hours === this.intervalHours) {
          return "最近七天个人游玩时长，" + interval.label + "汇总，单位为分钟";
        }
      }
      return "最近七天个人游玩时长，单位为分钟";
    },
  },
};
</script>
