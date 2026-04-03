import assert from 'node:assert/strict';
import test from 'node:test';

import {
  SAFE_TRANSFER_CHUNK_SIZE,
  TRANSFER_PROGRESS_UPDATE_INTERVAL_MS,
  shouldPublishTransferProgress,
  resolveTransferChunkSize,
} from './transfer-runtime';

test('resolveTransferChunkSize prefers a conservative default across browsers', () => {
  assert.equal(SAFE_TRANSFER_CHUNK_SIZE, 64 * 1024);
  assert.equal(resolveTransferChunkSize(undefined), 64 * 1024);
  assert.equal(resolveTransferChunkSize(8 * 1024), 8 * 1024);
  assert.equal(resolveTransferChunkSize(256 * 1024), 64 * 1024);
});

test('shouldPublishTransferProgress throttles noisy intermediate updates but always allows forward progress after the interval', () => {
  const initialTime = 10_000;

  assert.equal(shouldPublishTransferProgress({
    nextProgress: 1,
    previousProgress: 0,
    now: initialTime,
    lastPublishedAt: initialTime,
  }), false);

  assert.equal(shouldPublishTransferProgress({
    nextProgress: 1,
    previousProgress: 0,
    now: initialTime + TRANSFER_PROGRESS_UPDATE_INTERVAL_MS,
    lastPublishedAt: initialTime,
  }), true);
});

test('shouldPublishTransferProgress always allows terminal or changed progress states through immediately', () => {
  const initialTime = 10_000;

  assert.equal(shouldPublishTransferProgress({
    nextProgress: 100,
    previousProgress: 99,
    now: initialTime,
    lastPublishedAt: initialTime,
  }), true);

  assert.equal(shouldPublishTransferProgress({
    nextProgress: 30,
    previousProgress: 30,
    now: initialTime + TRANSFER_PROGRESS_UPDATE_INTERVAL_MS * 10,
    lastPublishedAt: initialTime,
  }), false);
});
