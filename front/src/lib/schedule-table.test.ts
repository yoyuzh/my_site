import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { CourseResponse } from './types';
import { buildScheduleTable, getScheduleCellHeight, getScheduleDividerOffsets } from './schedule-table';

test('buildScheduleTable creates 12 sections with empty slots preserved', () => {
  const schedule: CourseResponse[] = [
    {
      courseName: 'Advanced Java',
      teacher: 'Li',
      classroom: 'A101',
      dayOfWeek: 1,
      startTime: 1,
      endTime: 2,
    },
    {
      courseName: 'Networks',
      teacher: 'Wang',
      classroom: 'B202',
      dayOfWeek: 3,
      startTime: 5,
      endTime: 6,
    },
  ];

  const table = buildScheduleTable(schedule);

  assert.equal(table.length, 12);
  assert.equal(table[0].slots.length, 7);
  assert.equal(table[0].section, 1);
  assert.equal(table[11].section, 12);
  assert.equal(table[0].period, 'morning');
  assert.equal(table[4].period, 'noon');
  assert.equal(table[5].period, 'afternoon');
  assert.equal(table[9].period, 'evening');
  assert.equal(table[0].slots[0]?.course?.courseName, 'Advanced Java');
  assert.equal(table[1].slots[0]?.type, 'covered');
  assert.equal(table[2].slots[0]?.type, 'empty');
  assert.equal(table[4].slots[2]?.course?.courseName, 'Networks');
  assert.equal(table[5].slots[2]?.type, 'covered');
  assert.equal(table[8].slots[4]?.type, 'empty');
  assert.equal(table[8].slots[6]?.type, 'empty');
});

test('buildScheduleTable clamps invalid section ranges safely', () => {
  const schedule: CourseResponse[] = [
    {
      courseName: 'Night Studio',
      teacher: 'Xu',
      classroom: 'C303',
      dayOfWeek: 5,
      startTime: 11,
      endTime: 14,
    },
  ];

  const table = buildScheduleTable(schedule);

  assert.equal(table[10].slots[4]?.rowSpan, 2);
  assert.equal(table[11].slots[4]?.type, 'covered');
});

test('getScheduleCellHeight returns merged visual height for rowspan cells', () => {
  assert.equal(getScheduleCellHeight(1), 96);
  assert.equal(getScheduleCellHeight(2), 200);
  assert.equal(getScheduleCellHeight(4), 408);
});

test('getScheduleDividerOffsets returns internal section boundaries for merged cells', () => {
  assert.deepEqual(getScheduleDividerOffsets(1), []);
  assert.deepEqual(getScheduleDividerOffsets(2), [100]);
  assert.deepEqual(getScheduleDividerOffsets(4), [100, 204, 308]);
});
