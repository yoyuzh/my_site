import { fetchApi } from './api';
import type { PageResponse } from './files';

export type AdminUser = {
  id: number;
  username: string;
  email: string;
  phoneNumber: string | null;
  createdAt: string;
  role: 'USER' | 'ADMIN';
  banned: boolean;
  usedStorageBytes: number;
  storageQuotaBytes: number;
  maxUploadSizeBytes: number;
};

export type AdminPasswordResetResponse = {
  temporaryPassword: string;
};

export async function getAdminUsers(page = 0, size = 50, query = '') {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    query,
  });
  return fetchApi<PageResponse<AdminUser>>(`/admin/users?${params.toString()}`);
}

export async function updateUserRole(userId: number, role: AdminUser['role']) {
  return fetchApi<AdminUser>(`/admin/users/${userId}/role`, {
    method: 'PATCH',
    body: JSON.stringify({ role }),
  });
}

export async function updateUserStatus(userId: number, banned: boolean) {
  return fetchApi<AdminUser>(`/admin/users/${userId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ banned }),
  });
}

export async function updateUserPassword(userId: number, newPassword: string) {
  return fetchApi<AdminUser>(`/admin/users/${userId}/password`, {
    method: 'PUT',
    body: JSON.stringify({ newPassword }),
  });
}

export async function updateUserStorageQuota(userId: number, storageQuotaBytes: number) {
  return fetchApi<AdminUser>(`/admin/users/${userId}/storage-quota`, {
    method: 'PATCH',
    body: JSON.stringify({ storageQuotaBytes }),
  });
}

export async function updateUserMaxUploadSize(userId: number, maxUploadSizeBytes: number) {
  return fetchApi<AdminUser>(`/admin/users/${userId}/max-upload-size`, {
    method: 'PATCH',
    body: JSON.stringify({ maxUploadSizeBytes }),
  });
}

export async function resetUserPassword(userId: number) {
  return fetchApi<AdminPasswordResetResponse>(`/admin/users/${userId}/password/reset`, {
    method: 'POST',
  });
}
