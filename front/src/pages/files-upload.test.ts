import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildUploadProgressSnapshot,
  completeUploadTask,
  createUploadTask,
  formatTransferSpeed,
  prepareUploadFile,
} from './files-upload';

test('createUploadTask uses current path as upload destination', () => {
  const task = createUploadTask(new File(['hello'], 'notes.md', {type: 'text/markdown'}), ['文档', '课程资料'], 'task-1');

  assert.equal(task.id, 'task-1');
  assert.equal(task.fileName, 'notes.md');
  assert.equal(task.destination, '/文档/课程资料');
  assert.equal(task.progress, 0);
  assert.equal(task.status, 'uploading');
  assert.equal(task.speed, '等待上传...');
});

test('formatTransferSpeed chooses a readable unit', () => {
  assert.equal(formatTransferSpeed(800), '800 B/s');
  assert.equal(formatTransferSpeed(2048), '2.0 KB/s');
  assert.equal(formatTransferSpeed(3.5 * 1024 * 1024), '3.5 MB/s');
});

test('buildUploadProgressSnapshot derives progress and speed from bytes transferred', () => {
  const firstSnapshot = buildUploadProgressSnapshot({
    loaded: 1024,
    total: 4096,
    now: 1_000,
  });

  assert.equal(firstSnapshot.progress, 25);
  assert.equal(firstSnapshot.speed, '1.0 KB/s');

  const nextSnapshot = buildUploadProgressSnapshot({
    loaded: 3072,
    total: 4096,
    now: 2_000,
    previous: firstSnapshot.measurement,
  });

  assert.equal(nextSnapshot.progress, 75);
  assert.equal(nextSnapshot.speed, '2.0 KB/s');
});

test('buildUploadProgressSnapshot keeps progress below 100 until request completes', () => {
  const snapshot = buildUploadProgressSnapshot({
    loaded: 4096,
    total: 4096,
    now: 1_500,
  });

  assert.equal(snapshot.progress, 99);
});

test('completeUploadTask marks upload as completed', () => {
  const task = createUploadTask(new File(['hello'], 'photo.png', {type: 'image/png'}), [], 'task-2');

  const nextTask = completeUploadTask(task);

  assert.equal(nextTask.destination, '/');
  assert.equal(nextTask.progress, 100);
  assert.equal(nextTask.status, 'completed');
  assert.equal(nextTask.speed, '');
});

test('prepareUploadFile appends an incrementing suffix when the same file name already exists', () => {
  const firstDuplicate = prepareUploadFile(
    new File(['hello'], 'notes.md', {type: 'text/markdown'}),
    new Set(['notes.md']),
  );

  assert.equal(firstDuplicate.file.name, 'notes (1).md');
  assert.equal(firstDuplicate.noticeMessage, '检测到同名文件，已自动重命名为 notes (1).md');

  const secondDuplicate = prepareUploadFile(
    new File(['hello'], 'notes.md', {type: 'text/markdown'}),
    new Set(['notes.md', 'notes (1).md']),
  );

  assert.equal(secondDuplicate.file.name, 'notes (2).md');
  assert.equal(secondDuplicate.noticeMessage, '检测到同名文件，已自动重命名为 notes (2).md');
});

test('prepareUploadFile keeps files without conflicts unchanged', () => {
  const prepared = prepareUploadFile(
    new File(['hello'], 'syllabus', {type: 'text/plain'}),
    new Set(['notes.md']),
  );

  assert.equal(prepared.file.name, 'syllabus');
  assert.equal(prepared.noticeMessage, undefined);
});
