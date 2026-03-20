import assert from 'node:assert/strict';
import test from 'node:test';

import { normalizeNetdiskTargetPath, resolveNetdiskSaveDirectory } from './netdisk-upload';

test('normalizeNetdiskTargetPath falls back to 下载 for blank paths', () => {
  assert.equal(normalizeNetdiskTargetPath(undefined), '/下载');
  assert.equal(normalizeNetdiskTargetPath(''), '/下载');
  assert.equal(normalizeNetdiskTargetPath('   '), '/下载');
});

test('normalizeNetdiskTargetPath normalizes slash and root input', () => {
  assert.equal(normalizeNetdiskTargetPath('/'), '/');
  assert.equal(normalizeNetdiskTargetPath('下载/快传'), '/下载/快传');
  assert.equal(normalizeNetdiskTargetPath('/下载/快传/'), '/下载/快传');
});

test('resolveNetdiskSaveDirectory keeps nested transfer folders under 下载', () => {
  assert.equal(resolveNetdiskSaveDirectory('相册/旅行/cover.jpg'), '/下载/相册/旅行');
  assert.equal(resolveNetdiskSaveDirectory('cover.jpg'), '/下载');
});

test('resolveNetdiskSaveDirectory ignores unsafe path segments', () => {
  assert.equal(resolveNetdiskSaveDirectory('../相册//旅行/cover.jpg'), '/下载/相册/旅行');
});
