import assert from 'node:assert/strict';
import test from 'node:test';

import { getInviteCodePanelState } from './dashboard-state';

test('getInviteCodePanelState returns a copyable invite code when summary contains one', () => {
  assert.deepEqual(
    getInviteCodePanelState({
      totalUsers: 12,
      totalFiles: 34,
      inviteCode: ' AbCd1234 ',
    }),
    {
      inviteCode: 'AbCd1234',
      canCopy: true,
    },
  );
});

test('getInviteCodePanelState falls back to a placeholder when summary has no invite code', () => {
  assert.deepEqual(
    getInviteCodePanelState({
      totalUsers: 12,
      totalFiles: 34,
      inviteCode: '   ',
    }),
    {
      inviteCode: '未生成',
      canCopy: false,
    },
  );
});
