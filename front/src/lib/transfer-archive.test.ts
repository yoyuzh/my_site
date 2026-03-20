import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildTransferArchiveFileName,
  createTransferZipArchive,
} from './transfer-archive';

test('buildTransferArchiveFileName always returns a zip filename', () => {
  assert.equal(buildTransferArchiveFileName('课堂资料'), '课堂资料.zip');
  assert.equal(buildTransferArchiveFileName('课堂资料.zip'), '课堂资料.zip');
});

test('createTransferZipArchive creates a zip payload that keeps nested file paths', async () => {
  const archive = await createTransferZipArchive([
    {
      name: 'report.pdf',
      relativePath: '课程资料/report.pdf',
      data: new TextEncoder().encode('report'),
    },
    {
      name: 'notes.txt',
      relativePath: '课程资料/notes.txt',
      data: new TextEncoder().encode('notes'),
    },
  ]);

  const bytes = new Uint8Array(await archive.arrayBuffer());
  const text = new TextDecoder().decode(bytes);

  assert.equal(String.fromCharCode(...bytes.slice(0, 4)), 'PK\u0003\u0004');
  assert.match(text, /课程资料\/report\.pdf/);
  assert.match(text, /课程资料\/notes\.txt/);
});
