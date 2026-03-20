import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildObjectKey,
  createAuthorizationHeader,
  getFrontendSpaAliasContentType,
  getFrontendSpaAliasKeys,
  getCacheControl,
  getContentType,
  normalizeEndpoint,
} from './oss-deploy-lib.mjs';

test('normalizeEndpoint strips scheme and trailing slashes', () => {
  assert.equal(normalizeEndpoint('https://oss-ap-northeast-1.aliyuncs.com/'), 'oss-ap-northeast-1.aliyuncs.com');
});

test('buildObjectKey joins optional prefix with relative path', () => {
  assert.equal(buildObjectKey('', 'assets/index.js'), 'assets/index.js');
  assert.equal(buildObjectKey('portal', 'assets/index.js'), 'portal/assets/index.js');
});

test('getCacheControl keeps index uncached and assets immutable', () => {
  assert.equal(getCacheControl('index.html'), 'no-cache');
  assert.equal(getCacheControl('assets/index.js'), 'public,max-age=31536000,immutable');
  assert.equal(getCacheControl('race/index.html'), 'public,max-age=300');
});

test('getContentType resolves common frontend asset types', () => {
  assert.equal(getContentType('index.html'), 'text/html; charset=utf-8');
  assert.equal(getContentType('assets/app.css'), 'text/css; charset=utf-8');
  assert.equal(getContentType('assets/app.js'), 'text/javascript; charset=utf-8');
  assert.equal(getContentType('favicon.png'), 'image/png');
});

test('frontend spa aliases are uploaded as html entry points', () => {
  const aliases = getFrontendSpaAliasKeys();

  assert.ok(aliases.includes('t/index.html'));
  assert.ok(aliases.includes('overview'));
  assert.ok(aliases.includes('transfer/index.html'));
  assert.ok(aliases.includes('admin/users'));
  assert.equal(getFrontendSpaAliasContentType(), 'text/html; charset=utf-8');
});

test('createAuthorizationHeader is stable for a known request', () => {
  const header = createAuthorizationHeader({
    method: 'PUT',
    bucket: 'demo-bucket',
    objectKey: 'assets/index.js',
    contentType: 'text/javascript; charset=utf-8',
    date: 'Tue, 17 Mar 2026 12:00:00 GMT',
    accessKeyId: 'test-id',
    accessKeySecret: 'test-secret',
  });

  assert.equal(header, 'OSS test-id:JgyH7mTiSILGGWsnXJwg4KIBRO4=');
});
