import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildOfflineTransferDownloadUrl,
  sanitizePickupCode,
  toTransferFilePayload,
} from './transfer';

test('sanitizePickupCode keeps only uppercase letters and digits', () => {
  assert.equal(sanitizePickupCode(' ab-12 cD*3 '), 'AB12CD');
});

test('toTransferFilePayload preserves relative paths and content types', () => {
  const file = new File(['hello'], 'greeting.txt', {
    type: 'text/plain',
    lastModified: 1710000000000,
  });
  Object.defineProperty(file, 'webkitRelativePath', {
    value: 'docs/greeting.txt',
    configurable: true,
  });

  assert.deepEqual(toTransferFilePayload([file]), [
    {
      name: 'greeting.txt',
      relativePath: 'docs/greeting.txt',
      size: 5,
      contentType: 'text/plain',
    },
  ]);
});

test('buildOfflineTransferDownloadUrl uses the local api base', () => {
  assert.equal(
    buildOfflineTransferDownloadUrl('session-1', 'file-1'),
    '/api/transfer/sessions/session-1/files/file-1/download',
  );
});
