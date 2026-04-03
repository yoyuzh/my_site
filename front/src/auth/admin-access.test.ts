import assert from 'node:assert/strict';
import test from 'node:test';

import { ApiError } from '@/src/lib/api';

import { fetchAdminAccessStatus } from './admin-access';

test('fetchAdminAccessStatus returns true when the admin summary request succeeds', async () => {
  const request = async () => ({
    totalUsers: 1,
    totalFiles: 2,
    totalStorageBytes: 0,
    downloadTrafficBytes: 0,
    requestCount: 0,
    transferUsageBytes: 0,
    offlineTransferStorageBytes: 0,
    offlineTransferStorageLimitBytes: 0,
    dailyActiveUsers: [],
    requestTimeline: [],
    inviteCode: 'invite-code',
  });

  await assert.doesNotReject(async () => {
    const allowed = await fetchAdminAccessStatus(request);
    assert.equal(allowed, true);
  });
});

test('fetchAdminAccessStatus returns false when the server rejects the user with 403', async () => {
  const request = async () => {
    throw new ApiError('没有后台权限', 403);
  };

  const allowed = await fetchAdminAccessStatus(request);
  assert.equal(allowed, false);
});
