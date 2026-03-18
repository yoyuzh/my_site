import assert from 'node:assert/strict';
import { test } from 'node:test';

import { getOverviewLoadErrorMessage } from './overview-state';

test('post-login failures are presented as overview initialization issues', () => {
  assert.equal(
    getOverviewLoadErrorMessage(true),
    '登录已成功，但总览数据加载失败，请稍后重试。'
  );
});

test('generic overview failures stay generic when not coming right after login', () => {
  assert.equal(
    getOverviewLoadErrorMessage(false),
    '总览数据加载失败，请稍后重试。'
  );
});
