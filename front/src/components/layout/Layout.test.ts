import assert from 'node:assert/strict';
import test from 'node:test';

import { getVisibleNavItems } from './Layout';

test('getVisibleNavItems hides the admin entry for non-admin users', () => {
  assert.equal(getVisibleNavItems(false).some((item) => item.path === '/admin'), false);
});

test('getVisibleNavItems keeps the admin entry for admin users', () => {
  assert.equal(getVisibleNavItems(true).some((item) => item.path === '/admin'), true);
});
