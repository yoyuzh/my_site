import assert from 'node:assert/strict';
import test from 'node:test';

import type { AuthSession } from '@/src/lib/types';

import { buildAdminIdentity, hasAdminSession, portalAdminAuthProvider } from './auth-provider';

const session: AuthSession = {
  token: 'token-123',
  refreshToken: 'refresh-123',
  user: {
    id: 7,
    username: 'alice',
    email: 'alice@example.com',
    createdAt: '2026-03-19T15:00:00',
  },
};

test('hasAdminSession returns true only when a token is present', () => {
  assert.equal(hasAdminSession(session), true);
  assert.equal(hasAdminSession({...session, token: ''}), false);
  assert.equal(hasAdminSession(null), false);
});

test('buildAdminIdentity maps the portal session user to react-admin identity', () => {
  assert.deepEqual(buildAdminIdentity(session), {
    id: '7',
    fullName: 'alice',
  });
});

test('checkError keeps the session when admin API returns 403', async () => {
  await assert.doesNotReject(() => portalAdminAuthProvider.checkError?.({status: 403}));
});

test('checkError rejects when admin API returns 401', async () => {
  await assert.rejects(() => portalAdminAuthProvider.checkError?.({status: 401}));
});
