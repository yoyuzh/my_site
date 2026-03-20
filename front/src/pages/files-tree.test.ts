import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildDirectoryTree,
  createExpandedDirectorySet,
  getMissingDirectoryListingPaths,
  mergeDirectoryChildren,
} from './files-tree';

test('createExpandedDirectorySet keeps the root and every ancestor expanded', () => {
  assert.deepEqual(
    [...createExpandedDirectorySet(['文档', '课程资料', '实验'])],
    ['/', '/文档', '/文档/课程资料', '/文档/课程资料/实验'],
  );
});

test('mergeDirectoryChildren keeps directory names unique while preserving existing order', () => {
  assert.deepEqual(
    mergeDirectoryChildren(
      {
        '/': ['图片'],
      },
      '/',
      ['下载', '图片', '文档'],
    ),
    {
      '/': ['图片', '下载', '文档'],
    },
  );
});

test('buildDirectoryTree marks the active branch and nested folders correctly', () => {
  const tree = buildDirectoryTree(
    {
      '/': ['下载', '文档'],
      '/文档': ['课程资料'],
      '/文档/课程资料': ['实验'],
    },
    ['文档', '课程资料'],
    createExpandedDirectorySet(['文档', '课程资料']),
  );

  assert.deepEqual(tree, [
    {
      id: '/下载',
      name: '下载',
      path: ['/下载'.replace(/^\//, '')].filter(Boolean),
      depth: 0,
      active: false,
      expanded: false,
      children: [],
    },
    {
      id: '/文档',
      name: '文档',
      path: ['文档'],
      depth: 0,
      active: false,
      expanded: true,
      children: [
        {
          id: '/文档/课程资料',
          name: '课程资料',
          path: ['文档', '课程资料'],
          depth: 1,
          active: true,
          expanded: true,
          children: [
            {
              id: '/文档/课程资料/实验',
              name: '实验',
              path: ['文档', '课程资料', '实验'],
              depth: 2,
              active: false,
              expanded: false,
              children: [],
            },
          ],
        },
      ],
    },
  ]);
});

test('getMissingDirectoryListingPaths requests any unloaded ancestors for a deep current path', () => {
  assert.deepEqual(
    getMissingDirectoryListingPaths(
      ['文档', '课程资料', '实验'],
      new Set(['/文档/课程资料/实验']),
    ),
    [[], ['文档'], ['文档', '课程资料']],
  );
});

test('getMissingDirectoryListingPaths ignores ancestors that were only inferred by the tree', () => {
  assert.deepEqual(
    getMissingDirectoryListingPaths(
      ['文档', '课程资料'],
      new Set(['/文档/课程资料']),
    ),
    [[], ['文档']],
  );
});
