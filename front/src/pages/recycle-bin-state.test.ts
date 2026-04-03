import assert from 'node:assert/strict';
import test from 'node:test';

import {
  RECYCLE_BIN_ROUTE,
  RECYCLE_BIN_RETENTION_DAYS,
  formatRecycleBinExpiresLabel,
  getFilesSidebarFooterEntries,
} from './recycle-bin-state';

test('files sidebar keeps the recycle bin entry at the bottom footer area', () => {
  const footerEntries = getFilesSidebarFooterEntries();

  assert.equal(footerEntries.at(-1)?.path, RECYCLE_BIN_ROUTE);
  assert.equal(footerEntries.at(-1)?.label, '回收站');
});

test('recycle bin retention stays fixed at ten days', () => {
  assert.equal(RECYCLE_BIN_RETENTION_DAYS, 10);
});

test('recycle bin expiry labels show the remaining days before purge', () => {
  assert.equal(
    formatRecycleBinExpiresLabel('2026-04-13T10:00:00', new Date('2026-04-03T10:00:00')),
    '10 天后清理'
  );
  assert.equal(
    formatRecycleBinExpiresLabel('2026-04-04T09:00:00', new Date('2026-04-03T10:00:00')),
    '1 天后清理'
  );
});
