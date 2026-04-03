import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildRequestLineChartModel,
  buildRequestLineChartXAxisPoints,
  formatMetricValue,
  getInviteCodePanelState,
  parseStorageLimitInput,
} from './dashboard-state';

test('getInviteCodePanelState returns a copyable invite code when summary contains one', () => {
  assert.deepEqual(
    getInviteCodePanelState({
      totalUsers: 12,
      totalFiles: 34,
      totalStorageBytes: 0,
      downloadTrafficBytes: 0,
      requestCount: 0,
      transferUsageBytes: 0,
      offlineTransferStorageBytes: 0,
      offlineTransferStorageLimitBytes: 0,
      dailyActiveUsers: [],
      requestTimeline: [],
      inviteCode: ' AbCd1234 ',
    }),
    {
      inviteCode: 'AbCd1234',
      canCopy: true,
    },
  );
});

test('getInviteCodePanelState falls back to a placeholder when summary has no invite code', () => {
  assert.deepEqual(
    getInviteCodePanelState({
      totalUsers: 12,
      totalFiles: 34,
      totalStorageBytes: 0,
      downloadTrafficBytes: 0,
      requestCount: 0,
      transferUsageBytes: 0,
      offlineTransferStorageBytes: 0,
      offlineTransferStorageLimitBytes: 0,
      dailyActiveUsers: [],
      requestTimeline: [],
      inviteCode: '   ',
    }),
    {
      inviteCode: '未生成',
      canCopy: false,
    },
  );
});

test('formatMetricValue formats byte metrics with binary units', () => {
  assert.equal(formatMetricValue(1536, 'bytes'), '1.5 KB');
  assert.equal(formatMetricValue(50 * 1024 * 1024 * 1024, 'bytes'), '50 GB');
});

test('formatMetricValue formats count metrics with locale separators', () => {
  assert.equal(formatMetricValue(1234567, 'count'), '1,234,567');
});

test('parseStorageLimitInput accepts common storage unit inputs', () => {
  assert.equal(parseStorageLimitInput('20GB'), 20 * 1024 * 1024 * 1024);
  assert.equal(parseStorageLimitInput('512 mb'), 512 * 1024 * 1024);
});

test('parseStorageLimitInput rejects invalid or non-positive inputs', () => {
  assert.equal(parseStorageLimitInput('0GB'), null);
  assert.equal(parseStorageLimitInput('abc'), null);
});

test('buildRequestLineChartModel converts hourly request data into chart coordinates', () => {
  const model = buildRequestLineChartModel([
    { hour: 0, label: '00:00', requestCount: 0 },
    { hour: 1, label: '01:00', requestCount: 30 },
    { hour: 2, label: '02:00', requestCount: 60 },
    { hour: 3, label: '03:00', requestCount: 15 },
  ]);

  assert.equal(model.points.length, 4);
  assert.equal(model.points[0]?.x, 0);
  assert.equal(model.points[0]?.y, 100);
  assert.equal(model.points[2]?.y, 0);
  assert.equal(model.points[3]?.x, 100);
  assert.equal(model.maxValue, 60);
  assert.equal(model.linePath, 'M 0 100 L 33.333 50 L 66.667 0 L 100 75');
  assert.deepEqual(model.yAxisTicks, [0, 15, 30, 45, 60]);
  assert.equal(model.peakPoint?.label, '02:00');
});

test('buildRequestLineChartModel stretches only the available hours across the chart width', () => {
  const model = buildRequestLineChartModel([
    { hour: 0, label: '00:00', requestCount: 2 },
    { hour: 1, label: '01:00', requestCount: 4 },
    { hour: 2, label: '02:00', requestCount: 3 },
    { hour: 3, label: '03:00', requestCount: 6 },
    { hour: 4, label: '04:00', requestCount: 5 },
    { hour: 5, label: '05:00', requestCount: 1 },
    { hour: 6, label: '06:00', requestCount: 2 },
    { hour: 7, label: '07:00', requestCount: 4 },
  ]);

  assert.equal(model.points[0]?.x, 0);
  assert.equal(model.points.at(-1)?.x, 100);
  assert.equal(model.points.length, 8);
});

test('buildRequestLineChartXAxisPoints only shows elapsed-hour labels plus start and end', () => {
  const model = buildRequestLineChartModel([
    { hour: 0, label: '00:00', requestCount: 2 },
    { hour: 1, label: '01:00', requestCount: 4 },
    { hour: 2, label: '02:00', requestCount: 3 },
    { hour: 3, label: '03:00', requestCount: 6 },
    { hour: 4, label: '04:00', requestCount: 5 },
    { hour: 5, label: '05:00', requestCount: 1 },
    { hour: 6, label: '06:00', requestCount: 2 },
    { hour: 7, label: '07:00', requestCount: 4 },
  ]);

  assert.deepEqual(
    buildRequestLineChartXAxisPoints(model.points).map((point) => point.label),
    ['00:00', '06:00', '07:00'],
  );
});
