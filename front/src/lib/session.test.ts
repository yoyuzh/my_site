import test from 'node:test';
import assert from 'node:assert/strict';
import { canAccessAdmin, getDefaultSignedInRoute, getPortalRoleLabel } from './session';

test('canAccessAdmin allows moderator and admin only', () => {
  assert.equal(canAccessAdmin('USER'), false);
  assert.equal(canAccessAdmin('MODERATOR'), true);
  assert.equal(canAccessAdmin('ADMIN'), true);
  assert.equal(canAccessAdmin(undefined), false);
});

test('getDefaultSignedInRoute sends admin-capable roles to admin dashboard', () => {
  assert.equal(getDefaultSignedInRoute('USER'), '/overview');
  assert.equal(getDefaultSignedInRoute('MODERATOR'), '/admin/dashboard');
  assert.equal(getDefaultSignedInRoute('ADMIN'), '/admin/dashboard');
});

test('getPortalRoleLabel exposes moderator label', () => {
  assert.equal(getPortalRoleLabel('USER'), '普通用户');
  assert.equal(getPortalRoleLabel('MODERATOR'), '运营管理员');
  assert.equal(getPortalRoleLabel('ADMIN'), '管理员');
});
