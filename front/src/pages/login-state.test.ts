import assert from 'node:assert/strict';
import test from 'node:test';

import { buildRegisterPayload, validateRegisterForm } from './login-state';

test('validateRegisterForm rejects mismatched passwords', () => {
  const result = validateRegisterForm({
    username: 'alice',
    email: 'alice@example.com',
    phoneNumber: '13800138000',
    password: 'StrongPass1!',
    confirmPassword: 'StrongPass2!',
    inviteCode: 'invite-code',
  });

  assert.equal(result, '两次输入的密码不一致');
});

test('validateRegisterForm rejects blank invite code', () => {
  const result = validateRegisterForm({
    username: 'alice',
    email: 'alice@example.com',
    phoneNumber: '13800138000',
    password: 'StrongPass1!',
    confirmPassword: 'StrongPass1!',
    inviteCode: '   ',
  });

  assert.equal(result, '请输入邀请码');
});

test('buildRegisterPayload trims fields and keeps invite code', () => {
  const payload = buildRegisterPayload({
    username: ' alice ',
    email: ' alice@example.com ',
    phoneNumber: '13800138000',
    password: 'StrongPass1!',
    confirmPassword: 'StrongPass1!',
    inviteCode: ' invite-code ',
  });

  assert.deepEqual(payload, {
    username: 'alice',
    email: 'alice@example.com',
    phoneNumber: '13800138000',
    password: 'StrongPass1!',
    confirmPassword: 'StrongPass1!',
    inviteCode: 'invite-code',
  });
});
