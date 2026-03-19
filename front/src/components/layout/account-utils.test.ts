import assert from 'node:assert/strict';
import test from 'node:test';

import type { UserProfile } from '@/src/lib/types';

import { buildAccountDraft, getRoleLabel, shouldLoadAvatarWithAuth } from './account-utils';

test('buildAccountDraft prefers display name and fills fallback values', () => {
  const profile: UserProfile = {
    id: 1,
    username: 'alice',
    displayName: 'Alice',
    email: 'alice@example.com',
    bio: null,
    preferredLanguage: null,
    role: 'USER',
    createdAt: '2026-03-19T17:00:00',
  };

  assert.deepEqual(buildAccountDraft(profile), {
    displayName: 'Alice',
    email: 'alice@example.com',
    bio: '',
    preferredLanguage: 'zh-CN',
  });
});

test('getRoleLabel maps backend roles to readable chinese labels', () => {
  assert.equal(getRoleLabel('ADMIN'), '管理员');
  assert.equal(getRoleLabel('MODERATOR'), '协管员');
  assert.equal(getRoleLabel('USER'), '普通用户');
});

test('shouldLoadAvatarWithAuth only treats relative avatar urls as protected resources', () => {
  assert.equal(shouldLoadAvatarWithAuth('/api/user/avatar/content?v=1'), true);
  assert.equal(shouldLoadAvatarWithAuth('https://cdn.example.com/avatar.png?sig=1'), false);
  assert.equal(shouldLoadAvatarWithAuth(null), false);
});
