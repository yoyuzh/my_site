import assert from 'node:assert/strict';
import test from 'node:test';

import { buildOfflineTransferDownloadUrl, toTransferFilePayload } from './transfer';

test('toTransferFilePayload keeps relative folder paths for transfer files', () => {
  const report = new File(['hello'], 'report.pdf', {
    type: 'application/pdf',
  });
  Object.defineProperty(report, 'webkitRelativePath', {
    configurable: true,
    value: '课程资料/第一周/report.pdf',
  });

  assert.deepEqual(toTransferFilePayload([report]), [
    {
      name: 'report.pdf',
      relativePath: '课程资料/第一周/report.pdf',
      size: 5,
      contentType: 'application/pdf',
    },
  ]);
});

test('buildOfflineTransferDownloadUrl points to the public offline download endpoint', () => {
  assert.equal(
    buildOfflineTransferDownloadUrl('session-1', 'file-1'),
    '/api/transfer/sessions/session-1/files/file-1/download',
  );
});
