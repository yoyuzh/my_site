import type { AdminSummary } from '@/src/lib/types';

export interface InviteCodePanelState {
  inviteCode: string;
  canCopy: boolean;
}

export function getInviteCodePanelState(summary: AdminSummary | null | undefined): InviteCodePanelState {
  const inviteCode = summary?.inviteCode?.trim() ?? '';
  if (!inviteCode) {
    return {
      inviteCode: '未生成',
      canCopy: false,
    };
  }

  return {
    inviteCode,
    canCopy: true,
  };
}
