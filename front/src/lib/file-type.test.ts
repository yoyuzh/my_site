import assert from 'node:assert/strict';
import test from 'node:test';

import { resolveFileType, resolveStoredFileType } from './file-type';

test('resolveFileType maps common extensions and content types to richer groups', () => {
  assert.deepEqual(
    resolveFileType({
      fileName: '预算.xlsx',
      contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    }),
    {
      extension: 'xlsx',
      kind: 'spreadsheet',
      label: '表格',
    },
  );

  assert.deepEqual(
    resolveFileType({
      fileName: '发布会.key',
      contentType: 'application/vnd.apple.keynote',
    }),
    {
      extension: 'key',
      kind: 'presentation',
      label: '演示文稿',
    },
  );

  assert.deepEqual(
    resolveFileType({
      fileName: '首页.fig',
      contentType: 'application/octet-stream',
    }),
    {
      extension: 'fig',
      kind: 'design',
      label: '设计稿',
    },
  );

  assert.deepEqual(
    resolveFileType({
      fileName: 'Brand.woff2',
      contentType: 'font/woff2',
    }),
    {
      extension: 'woff2',
      kind: 'font',
      label: '字体',
    },
  );

  assert.deepEqual(
    resolveFileType({
      fileName: 'README',
      contentType: 'text/plain',
    }),
    {
      extension: '',
      kind: 'text',
      label: '文本',
    },
  );
});

test('resolveStoredFileType prioritizes folders and content types for listed files', () => {
  assert.deepEqual(
    resolveStoredFileType({
      filename: '素材库',
      contentType: null,
      directory: true,
    }),
    {
      extension: '',
      kind: 'folder',
      label: '文件夹',
    },
  );

  assert.deepEqual(
    resolveStoredFileType({
      filename: '封面',
      contentType: 'image/webp',
      directory: false,
    }),
    {
      extension: '',
      kind: 'image',
      label: '图片',
    },
  );

  assert.deepEqual(
    resolveStoredFileType({
      filename: 'episode.mkv',
      contentType: 'video/x-matroska',
      directory: false,
    }),
    {
      extension: 'mkv',
      kind: 'video',
      label: '视频',
    },
  );

  assert.deepEqual(
    resolveStoredFileType({
      filename: 'manual.epub',
      contentType: 'application/epub+zip',
      directory: false,
    }),
    {
      extension: 'epub',
      kind: 'ebook',
      label: '电子书',
    },
  );
});
