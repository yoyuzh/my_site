import assert from 'node:assert/strict';
import test from 'node:test';

import {
  getParentNetdiskPath,
  joinNetdiskPath,
  resolveTransferSaveDirectory,
  splitNetdiskPath,
} from './netdisk-paths';

test('splitNetdiskPath normalizes root and nested paths', () => {
  assert.deepEqual(splitNetdiskPath('/'), []);
  assert.deepEqual(splitNetdiskPath('/下载/旅行/照片'), ['下载', '旅行', '照片']);
  assert.deepEqual(splitNetdiskPath('下载//旅行/照片/'), ['下载', '旅行', '照片']);
});

test('joinNetdiskPath rebuilds a normalized absolute path', () => {
  assert.equal(joinNetdiskPath([]), '/');
  assert.equal(joinNetdiskPath(['下载', '旅行']), '/下载/旅行');
});

test('getParentNetdiskPath returns the previous directory level', () => {
  assert.equal(getParentNetdiskPath('/下载/旅行'), '/下载');
  assert.equal(getParentNetdiskPath('/下载'), '/');
});

test('resolveTransferSaveDirectory keeps nested transfer folders under the selected root path', () => {
  assert.equal(resolveTransferSaveDirectory('相册/旅行/cover.jpg', '/下载'), '/下载/相册/旅行');
  assert.equal(resolveTransferSaveDirectory('cover.jpg', '/下载'), '/下载');
});
