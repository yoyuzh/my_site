import assert from 'node:assert/strict';
import test from 'node:test';

import { buildTransferShareUrl } from '../lib/transfer-links';
import {
  canArchiveTransferSelection,
  buildQrImageUrl,
  canSendTransferFiles,
  createMockTransferCode,
  formatTransferSize,
  resolveInitialTransferTab,
  sanitizeReceiveCode,
} from './transfer-state';

test('createMockTransferCode returns a six digit numeric code', () => {
  const code = createMockTransferCode();

  assert.match(code, /^\d{6}$/);
});

test('sanitizeReceiveCode keeps only the first six digits', () => {
  assert.equal(sanitizeReceiveCode(' 98a76-54321 '), '987654');
});

test('formatTransferSize uses readable units', () => {
  assert.equal(formatTransferSize(0), '0 B');
  assert.equal(formatTransferSize(2048), '2 KB');
  assert.equal(formatTransferSize(2.5 * 1024 * 1024), '2.5 MB');
});

test('buildTransferShareUrl builds a browser-router receive url', () => {
  assert.equal(
    buildTransferShareUrl('https://yoyuzh.xyz', '849201', 'browser'),
    'https://yoyuzh.xyz/transfer?session=849201',
  );
});

test('buildTransferShareUrl builds a hash-router receive url', () => {
  assert.equal(
    buildTransferShareUrl('https://yoyuzh.xyz/', '849201', 'hash'),
    'https://yoyuzh.xyz/#/transfer?session=849201',
  );
});

test('buildQrImageUrl encodes the share url as a QR image endpoint', () => {
  assert.equal(
    buildQrImageUrl(buildTransferShareUrl('https://yoyuzh.xyz', '849201', 'browser')),
    'https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=https%3A%2F%2Fyoyuzh.xyz%2Ftransfer%3Fsession%3D849201',
  );
});

test('resolveInitialTransferTab prefers receive mode for public visitors and shared sessions', () => {
  assert.equal(resolveInitialTransferTab(false, null), 'receive');
  assert.equal(resolveInitialTransferTab(true, '849201'), 'receive');
  assert.equal(resolveInitialTransferTab(true, null), 'send');
});

test('canSendTransferFiles requires an authenticated session', () => {
  assert.equal(canSendTransferFiles(true), true);
  assert.equal(canSendTransferFiles(false), false);
});

test('canArchiveTransferSelection is enabled for multi-file or folder downloads', () => {
  assert.equal(canArchiveTransferSelection([
    {
      relativePath: 'report.pdf',
    },
  ]), false);

  assert.equal(canArchiveTransferSelection([
    {
      relativePath: '课程资料/report.pdf',
    },
  ]), true);

  assert.equal(canArchiveTransferSelection([
    {
      relativePath: 'report.pdf',
    },
    {
      relativePath: 'notes.txt',
    },
  ]), true);
});
