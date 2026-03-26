import assert from 'node:assert/strict';
import test from 'node:test';

import {
  cancelFilesUploadTask,
  clearFilesUploads,
  getFilesUploadStoreSnapshot,
  registerFilesUploadTaskCanceler,
  replaceFilesUploads,
  resetFilesUploadStoreForTests,
  setFilesUploadPanelOpen,
  subscribeFilesUploadStore,
  unregisterFilesUploadTaskCanceler,
  updateFilesUploadTask,
} from './files-upload-store';
import { createUploadTask } from './files-upload';

test('files upload store keeps tasks after page-level subscriber unmounts', () => {
  resetFilesUploadStoreForTests();
  const task = createUploadTask(new File(['hello'], 'manual.pdf', {type: 'application/pdf'}), ['文档'], 'task-1');

  const unsubscribe = subscribeFilesUploadStore(() => undefined);
  replaceFilesUploads([task]);
  unsubscribe();

  const snapshot = getFilesUploadStoreSnapshot();
  assert.equal(snapshot.uploads.length, 1);
  assert.equal(snapshot.uploads[0].fileName, 'manual.pdf');
});

test('files upload store supports task updates and panel visibility toggles', () => {
  resetFilesUploadStoreForTests();
  const task = createUploadTask(new File(['hello'], 'manual.pdf', {type: 'application/pdf'}), ['文档'], 'task-2');

  replaceFilesUploads([task]);
  updateFilesUploadTask(task.id, (current) => ({
    ...current,
    progress: 80,
  }));
  setFilesUploadPanelOpen(false);

  const snapshot = getFilesUploadStoreSnapshot();
  assert.equal(snapshot.uploads[0].progress, 80);
  assert.equal(snapshot.isUploadPanelOpen, false);

  clearFilesUploads();
  assert.equal(getFilesUploadStoreSnapshot().uploads.length, 0);
});

test('files upload store cancels one task by its id', () => {
  resetFilesUploadStoreForTests();
  const cancelled: string[] = [];
  registerFilesUploadTaskCanceler('task-1', () => {
    cancelled.push('task-1');
  });
  registerFilesUploadTaskCanceler('task-2', () => {
    cancelled.push('task-2');
  });

  const task = createUploadTask(new File(['hello'], 'manual.pdf', {type: 'application/pdf'}), ['文档'], 'task-1');
  replaceFilesUploads([task]);

  const didCancel = cancelFilesUploadTask('task-1');
  const didCancelUnknown = cancelFilesUploadTask('missing-task');
  unregisterFilesUploadTaskCanceler('task-2');

  assert.equal(didCancel, true);
  assert.equal(didCancelUnknown, false);
  assert.deepEqual(cancelled, ['task-1']);
});
