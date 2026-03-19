import assert from 'node:assert/strict';
import test from 'node:test';

import {
  clearSelectionIfDeleted,
  getActionErrorMessage,
  getNextAvailableName,
  removeUiFile,
  replaceUiFile,
  syncSelectedFile,
} from './files-state';

const files = [
  {id: 1, name: 'notes.txt', type: 'txt', size: '2 KB', modified: '2026/03/18 10:00'},
  {id: 2, name: 'photos', type: 'folder', size: '—', modified: '2026/03/18 09:00'},
];

test('replaceUiFile updates the matching file only', () => {
  const nextFiles = replaceUiFile(files, {
    id: 2,
    name: 'photos-2026',
    type: 'folder',
    size: '—',
    modified: '2026/03/18 09:00',
  });

  assert.deepEqual(nextFiles, [
    files[0],
    {
      id: 2,
      name: 'photos-2026',
      type: 'folder',
      size: '—',
      modified: '2026/03/18 09:00',
    },
  ]);
});

test('removeUiFile drops the deleted file from the current list', () => {
  assert.deepEqual(removeUiFile(files, 1), [files[1]]);
});

test('syncSelectedFile keeps details sidebar in sync after rename', () => {
  const selectedFile = files[1];
  const renamedFile = {
    ...selectedFile,
    name: 'photos-2026',
  };

  assert.deepEqual(syncSelectedFile(selectedFile, renamedFile), renamedFile);
  assert.equal(syncSelectedFile(files[0], renamedFile), files[0]);
});

test('clearSelectionIfDeleted removes details selection for deleted file', () => {
  assert.equal(clearSelectionIfDeleted(files[0], 1), null);
  assert.equal(clearSelectionIfDeleted(files[1], 1), files[1]);
});

test('getActionErrorMessage uses backend message when present', () => {
  assert.equal(getActionErrorMessage(new Error('重命名失败：同名文件已存在'), '重命名失败，请稍后重试'), '重命名失败：同名文件已存在');
  assert.equal(getActionErrorMessage(null, '重命名失败，请稍后重试'), '重命名失败，请稍后重试');
});

test('getNextAvailableName appends an incrementing suffix for duplicate folder names', () => {
  assert.equal(
    getNextAvailableName('新建文件夹', new Set(['新建文件夹'])),
    '新建文件夹 (1)',
  );
  assert.equal(
    getNextAvailableName('新建文件夹', new Set(['新建文件夹', '新建文件夹 (1)', '新建文件夹 (2)'])),
    '新建文件夹 (3)',
  );
});

test('getNextAvailableName keeps the original name when no duplicate exists', () => {
  assert.equal(getNextAvailableName('课程资料', new Set(['实验数据', '下载'])), '课程资料');
});
