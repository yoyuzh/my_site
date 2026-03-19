import type { AdminUserRole, UserProfile } from '@/src/lib/types';

export interface AccountDraft {
  displayName: string;
  email: string;
  bio: string;
  preferredLanguage: string;
}

export function buildAccountDraft(profile: UserProfile): AccountDraft {
  return {
    displayName: profile.displayName || profile.username,
    email: profile.email,
    bio: profile.bio || '',
    preferredLanguage: profile.preferredLanguage || 'zh-CN',
  };
}

export function getRoleLabel(role: AdminUserRole | undefined) {
  switch (role) {
    case 'ADMIN':
      return '管理员';
    case 'MODERATOR':
      return '协管员';
    default:
      return '普通用户';
  }
}

export function shouldLoadAvatarWithAuth(avatarUrl: string | null | undefined) {
  return Boolean(avatarUrl && avatarUrl.startsWith('/'));
}
