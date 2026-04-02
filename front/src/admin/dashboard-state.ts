import type { AdminRequestTimelinePoint, AdminSummary } from '@/src/lib/types';

export interface InviteCodePanelState {
  inviteCode: string;
  canCopy: boolean;
}

export interface RequestLineChartPoint extends AdminRequestTimelinePoint {
  x: number;
  y: number;
}

export interface RequestLineChartModel {
  points: RequestLineChartPoint[];
  linePath: string;
  areaPath: string;
  yAxisTicks: number[];
  maxValue: number;
  peakPoint: RequestLineChartPoint | null;
}

type MetricValueKind = 'bytes' | 'count';

const BYTE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];

export function formatMetricValue(value: number, kind: MetricValueKind): string {
  if (kind === 'count') {
    return new Intl.NumberFormat('en-US').format(value);
  }
  if (value <= 0) {
    return '0 B';
  }

  const unitIndex = Math.min(Math.floor(Math.log(value) / Math.log(1024)), BYTE_UNITS.length - 1);
  const unitValue = value / 1024 ** unitIndex;
  const formatted = unitValue >= 10 || unitIndex === 0 ? unitValue.toFixed(0) : unitValue.toFixed(1);
  return `${formatted} ${BYTE_UNITS[unitIndex]}`;
}

export function parseStorageLimitInput(value: string): number | null {
  const normalized = value.trim().toLowerCase();
  const matched = normalized.match(/^(\d+(?:\.\d+)?)\s*(b|kb|mb|gb|tb|pb)?$/);
  if (!matched) {
    return null;
  }

  const amount = Number.parseFloat(matched[1] ?? '0');
  if (!Number.isFinite(amount) || amount <= 0) {
    return null;
  }

  const unit = matched[2] ?? 'b';
  const multiplier = unit === 'pb'
    ? 1024 ** 5
    : unit === 'tb'
      ? 1024 ** 4
      : unit === 'gb'
        ? 1024 ** 3
        : unit === 'mb'
          ? 1024 ** 2
          : unit === 'kb'
            ? 1024
            : 1;
  return Math.floor(amount * multiplier);
}

export function buildRequestLineChartModel(timeline: AdminRequestTimelinePoint[]): RequestLineChartModel {
  if (timeline.length === 0) {
    return {
      points: [],
      linePath: '',
      areaPath: '',
      yAxisTicks: [0, 1, 2, 3, 4],
      maxValue: 0,
      peakPoint: null,
    };
  }

  const maxValue = Math.max(...timeline.map((point) => point.requestCount), 0);
  const scaleMax = maxValue > 0 ? maxValue : 1;
  const lastIndex = Math.max(timeline.length - 1, 1);
  const points = timeline.map((point, index) => ({
    ...point,
    x: roundChartValue((index / lastIndex) * 100),
    y: roundChartValue(100 - (point.requestCount / scaleMax) * 100),
  }));
  const linePath = points
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${formatChartNumber(point.x)} ${formatChartNumber(point.y)}`)
    .join(' ');

  return {
    points,
    linePath,
    areaPath: linePath ? `${linePath} L 100 100 L 0 100 Z` : '',
    yAxisTicks: buildYAxisTicks(maxValue),
    maxValue,
    peakPoint: points.reduce<RequestLineChartPoint | null>((peak, point) => {
      if (!peak || point.requestCount > peak.requestCount) {
        return point;
      }
      return peak;
    }, null),
  };
}

export function getInviteCodePanelState(summary: AdminSummary | null | undefined): InviteCodePanelState {
  const inviteCode = summary?.inviteCode?.trim() ?? '';
  if (!inviteCode) {
    return {
      inviteCode: '未生成',
      canCopy: false,
    };
  }

  return {
    inviteCode,
    canCopy: true,
  };
}

function buildYAxisTicks(maxValue: number): number[] {
  if (maxValue <= 0) {
    return [0, 1, 2, 3, 4];
  }
  return Array.from({ length: 5 }, (_, index) => roundChartValue((maxValue / 4) * index));
}

function roundChartValue(value: number): number {
  return Math.round(value * 1000) / 1000;
}

function formatChartNumber(value: number): string {
  const rounded = roundChartValue(value);
  return Number.isInteger(rounded) ? `${rounded}` : rounded.toFixed(3).replace(/0+$/, '').replace(/\.$/, '');
}
