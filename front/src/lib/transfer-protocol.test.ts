import assert from 'node:assert/strict';
import test from 'node:test';

import {
  createTransferFileManifest,
  createTransferCompleteMessage,
  createTransferFileCompleteMessage,
  createTransferFileId,
  createTransferFileManifestMessage,
  createTransferFileMetaMessage,
  createTransferReceiveRequestMessage,
  parseTransferControlMessage,
  toTransferChunk,
} from './transfer-protocol';

test('createTransferFileId uses stable file identity parts', () => {
  assert.equal(
    createTransferFileId({
      name: 'report.pdf',
      lastModified: 1730000000000,
      size: 2048,
    }),
    'report.pdf-1730000000000-2048',
  );
});

test('createTransferFileMetaMessage encodes the control payload for sender and receiver', () => {
  const payload = parseTransferControlMessage(
    createTransferFileMetaMessage({
      id: 'report-1',
      name: 'report.pdf',
      size: 2048,
      contentType: 'application/pdf',
      relativePath: '课程资料/report.pdf',
    }),
  );

  assert.deepEqual(payload, {
    type: 'file-meta',
    id: 'report-1',
    name: 'report.pdf',
    size: 2048,
    contentType: 'application/pdf',
    relativePath: '课程资料/report.pdf',
  });
});

test('createTransferFileManifest keeps folder relative paths from selected files', () => {
  const report = new File(['report'], 'report.pdf', {
    type: 'application/pdf',
    lastModified: 1730000000000,
  });
  Object.defineProperty(report, 'webkitRelativePath', {
    configurable: true,
    value: '课程资料/report.pdf',
  });

  const manifest = createTransferFileManifest([report]);

  assert.deepEqual(manifest, [
    {
      id: 'report.pdf-1730000000000-6',
      name: 'report.pdf',
      size: 6,
      contentType: 'application/pdf',
      relativePath: '课程资料/report.pdf',
    },
  ]);
});

test('createTransferFileManifestMessage and createTransferReceiveRequestMessage stay parseable', () => {
  const manifestPayload = parseTransferControlMessage(
    createTransferFileManifestMessage([
      {
        id: 'report-1',
        name: 'report.pdf',
        size: 2048,
        contentType: 'application/pdf',
        relativePath: '课程资料/report.pdf',
      },
    ]),
  );

  assert.deepEqual(manifestPayload, {
    type: 'manifest',
    files: [
      {
        id: 'report-1',
        name: 'report.pdf',
        size: 2048,
        contentType: 'application/pdf',
        relativePath: '课程资料/report.pdf',
      },
    ],
  });

  assert.deepEqual(parseTransferControlMessage(createTransferReceiveRequestMessage(['report-1'], true)), {
    type: 'receive-request',
    fileIds: ['report-1'],
    archive: true,
  });
});

test('createTransferFileCompleteMessage and createTransferCompleteMessage create parseable control messages', () => {
  assert.deepEqual(parseTransferControlMessage(createTransferFileCompleteMessage('report-1')), {
    type: 'file-complete',
    id: 'report-1',
  });

  assert.deepEqual(parseTransferControlMessage(createTransferCompleteMessage()), {
    type: 'transfer-complete',
  });
});

test('parseTransferControlMessage returns null for invalid payloads', () => {
  assert.equal(parseTransferControlMessage('{not-json'), null);
});

test('toTransferChunk normalizes ArrayBuffer and Blob data into bytes', async () => {
  assert.deepEqual(Array.from(await toTransferChunk(new Uint8Array([1, 2, 3]).buffer)), [1, 2, 3]);
  assert.deepEqual(Array.from(await toTransferChunk(new Blob(['hi']))), [104, 105]);
});
