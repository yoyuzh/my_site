import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildUploadProgressSnapshot,
  completeUploadTask,
  createUploadTasks,
  createUploadTask,
  prepareUploadTaskForCompletion,
  formatTransferSpeed,
  prepareFolderUploadEntries,
  prepareUploadFile,
  shouldUploadEntriesSequentially,
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

test('prepareFolderUploadEntries keeps relative directories and renames conflicting root folders', () => {
  const first = new File(['alpha'], 'a.txt', {type: 'text/plain'});
  Object.defineProperty(first, 'webkitRelativePath', {
    configurable: true,
    value: '设计稿/a.txt',
  });

  const second = new File(['beta'], 'b.txt', {type: 'text/plain'});
  Object.defineProperty(second, 'webkitRelativePath', {
    configurable: true,
    value: '设计稿/子目录/b.txt',
  });

  const entries = prepareFolderUploadEntries([first, second], ['文档'], ['设计稿']);

  assert.equal(entries[0].pathParts.join('/'), '文档/设计稿 (1)');
  assert.equal(entries[1].pathParts.join('/'), '文档/设计稿 (1)/子目录');
  assert.equal(entries[0].noticeMessage, '检测到同名文件夹，已自动重命名为 设计稿 (1)');
  assert.equal(entries[1].noticeMessage, '检测到同名文件夹，已自动重命名为 设计稿 (1)');
  assert.equal(shouldUploadEntriesSequentially(entries), true);
});

test('shouldUploadEntriesSequentially keeps plain file uploads in parallel mode', () => {
  const entries = [
    {
      file: new File(['alpha'], 'a.txt', {type: 'text/plain'}),
      pathParts: ['文档'],
      source: 'file' as const,
    },
    {
      file: new File(['beta'], 'b.txt', {type: 'text/plain'}),
      pathParts: ['文档'],
      source: 'file' as const,
    },
  ];

  assert.equal(shouldUploadEntriesSequentially(entries), false);
});

test('createUploadTasks creates a stable task list for the whole batch', () => {
  const entries = [
    {
      file: new File(['alpha'], 'a.txt', {type: 'text/plain'}),
      pathParts: ['文档'],
      source: 'file' as const,
      noticeMessage: 'alpha',
    },
    {
      file: new File(['beta'], 'b.txt', {type: 'text/plain'}),
      pathParts: ['文档', '资料'],
      source: 'folder' as const,
      noticeMessage: 'beta',
    },
  ];

  const tasks = createUploadTasks(entries);

  assert.equal(tasks.length, 2);
  assert.equal(tasks[0].fileName, 'a.txt');
  assert.equal(tasks[0].destination, '/文档');
  assert.equal(tasks[0].noticeMessage, 'alpha');
  assert.equal(tasks[1].fileName, 'b.txt');
  assert.equal(tasks[1].destination, '/文档/资料');
  assert.equal(tasks[1].noticeMessage, 'beta');
});

test('prepareUploadTaskForCompletion keeps a visible progress state before marking complete', () => {
  const task = createUploadTask(new File(['alpha'], 'a.txt', {type: 'text/plain'}), ['文档'], 'task-3');

  const nextTask = prepareUploadTaskForCompletion(task);

  assert.equal(nextTask.status, 'uploading');
  assert.equal(nextTask.progress, 99);
  assert.equal(nextTask.speed, '即将完成...');
});
