import assert from 'node:assert/strict';
import test from 'node:test';

import { buildTransferShareUrl } from '../lib/transfer-links';
import {
  getAvailableTransferModes,
  getOfflineTransferSessionLabel,
  getOfflineTransferSessionSize,
  canArchiveTransferSelection,
  buildQrImageUrl,
  canSendTransferFiles,
  createMockTransferCode,
  formatTransferSize,
  getTransferModeSummary,
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
  assert.equal(resolveInitialTransferTab(false, null), 'send');
  assert.equal(resolveInitialTransferTab(true, '849201'), 'receive');
  assert.equal(resolveInitialTransferTab(true, null), 'send');
});

test('canSendTransferFiles allows public online transfer entry', () => {
  assert.equal(canSendTransferFiles(true), true);
  assert.equal(canSendTransferFiles(false), true);
});

test('getAvailableTransferModes keeps offline mode behind login', () => {
  assert.deepEqual(getAvailableTransferModes(false), ['ONLINE']);
  assert.deepEqual(getAvailableTransferModes(true), ['ONLINE', 'OFFLINE']);
});

test('getTransferModeSummary describes the offline seven-day retention rule', () => {
  assert.deepEqual(getTransferModeSummary('OFFLINE'), {
    title: '发离线',
    description: '文件先上传到站点存储，保留 7 天，到期自动销毁，可被多次接收。',
  });
});

test('offline transfer history helpers summarize the session title and total size', () => {
  const singleFileSession = {
    sessionId: 'session-1',
    pickupCode: '723325',
    mode: 'OFFLINE' as const,
    expiresAt: '2026-04-09T08:00:00Z',
    files: [
      {name: 'cover.png', relativePath: '活动图/cover.png', size: 2048, contentType: 'image/png', uploaded: true},
    ],
  };

  const multiFileSession = {
    ...singleFileSession,
    sessionId: 'session-2',
    files: [
      {name: 'cover.png', relativePath: '活动图/cover.png', size: 2048, contentType: 'image/png', uploaded: true},
      {name: 'notes.pdf', relativePath: '活动图/notes.pdf', size: 1024, contentType: 'application/pdf', uploaded: true},
    ],
  };

  assert.equal(getOfflineTransferSessionLabel(singleFileSession), 'cover.png');
  assert.equal(getOfflineTransferSessionLabel(multiFileSession), 'cover.png 等 2 项');
  assert.equal(getOfflineTransferSessionSize(multiFileSession), '3 KB');
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
