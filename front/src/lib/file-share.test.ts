import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildFileShareUrl,
  FILE_SHARE_ROUTE_PREFIX,
  getPostLoginRedirectPath,
} from './file-share';

test('buildFileShareUrl builds a browser-router share url', () => {
  assert.equal(
    buildFileShareUrl('https://yoyuzh.xyz', 'share-token-1', 'browser'),
    'https://yoyuzh.xyz/share/share-token-1',
  );
});

test('buildFileShareUrl builds a hash-router share url', () => {
  assert.equal(
    buildFileShareUrl('https://yoyuzh.xyz/', 'share-token-1', 'hash'),
    'https://yoyuzh.xyz/#/share/share-token-1',
  );
});

test('getPostLoginRedirectPath keeps safe in-site paths only', () => {
  assert.equal(getPostLoginRedirectPath('/share/share-token-1'), '/share/share-token-1');
  assert.equal(getPostLoginRedirectPath('https://evil.example.com'), '/overview');
  assert.equal(getPostLoginRedirectPath(null), '/overview');
});

test('FILE_SHARE_ROUTE_PREFIX stays aligned with the public share route', () => {
  assert.equal(FILE_SHARE_ROUTE_PREFIX, '/share');
});
