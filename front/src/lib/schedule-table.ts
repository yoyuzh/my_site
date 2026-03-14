import type { CourseResponse } from './types';

export interface ScheduleSlot {
  type: 'empty' | 'course' | 'covered';
  course?: CourseResponse;
  rowSpan?: number;
}

export interface ScheduleRow {
  section: number;
  period: 'morning' | 'noon' | 'afternoon' | 'evening';
  slots: ScheduleSlot[];
}

const SECTION_COUNT = 12;
const WEEKDAY_COUNT = 7;
const SECTION_CELL_HEIGHT = 96;
const SECTION_CELL_GAP = 8;

function getPeriod(section: number): ScheduleRow['period'] {
  if (section <= 4) {
    return 'morning';
  }
  if (section === 5) {
    return 'noon';
  }
  if (section <= 8) {
    return 'afternoon';
  }
  return 'evening';
}

export function buildScheduleTable(schedule: CourseResponse[]) {
  const rows: ScheduleRow[] = Array.from({ length: SECTION_COUNT }, (_, index) => ({
    section: index + 1,
    period: getPeriod(index + 1),
    slots: Array.from({ length: WEEKDAY_COUNT }, () => ({ type: 'empty' as const })),
  }));

  for (const course of schedule) {
    const dayIndex = (course.dayOfWeek ?? 0) - 1;
    if (dayIndex < 0 || dayIndex >= WEEKDAY_COUNT) {
      continue;
    }

    const startSection = Math.max(1, Math.min(SECTION_COUNT, course.startTime ?? 1));
    const endSection = Math.max(startSection, Math.min(SECTION_COUNT, course.endTime ?? startSection));
    const rowSpan = endSection - startSection + 1;
    const startRowIndex = startSection - 1;

    rows[startRowIndex].slots[dayIndex] = {
      type: 'course',
      course,
      rowSpan,
    };

    for (let section = startSection + 1; section <= endSection; section += 1) {
      rows[section - 1].slots[dayIndex] = {
        type: 'covered',
      };
    }
  }

  return rows;
}

export function getScheduleCellHeight(rowSpan: number) {
  const safeRowSpan = Math.max(1, rowSpan);
  return safeRowSpan * SECTION_CELL_HEIGHT + (safeRowSpan - 1) * SECTION_CELL_GAP;
}

export function getScheduleDividerOffsets(rowSpan: number) {
  const safeRowSpan = Math.max(1, rowSpan);
  return Array.from({ length: safeRowSpan - 1 }, (_, index) =>
    (index + 1) * SECTION_CELL_HEIGHT + index * SECTION_CELL_GAP + SECTION_CELL_GAP / 2,
  );
}
