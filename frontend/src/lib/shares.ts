import { apiRequest } from '../api/client';
import type {
  FileItem,
  QueryPage,
  ShareItem,
  SharePasswordPayload,
  CreateSharePayload,
  UpdateSharePolicyPayload,
  SavedShareItem
} from '../api/types';

export async function createShare(payload: CreateSharePayload) {
  return apiRequest<ShareItem>({
    url: '/v2/shares',
    method: 'POST',
    data: payload,
  });
}

export async function updateSharePolicy(id: number, payload: UpdateSharePolicyPayload) {
  return apiRequest<ShareItem>({
    url: `/v2/shares/${id}/policy`,
    method: 'PATCH',
    data: payload,
  });
}

export async function deleteShare(id: number) {
  return apiRequest<void>({
    url: `/v2/shares/${id}`,
    method: 'DELETE',
  });
}

export async function getMyShares(page = 0, size = 20) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return apiRequest<QueryPage<ShareItem>>({
    url: `/v2/shares/mine?${params.toString()}`,
    method: 'GET',
  });
}

export async function getShareDetails(token: string) {
  return apiRequest<ShareItem>({
    url: `/v2/shares/${token}`,
    method: 'GET',
    authRequired: false,
  });
}

export async function verifySharePassword(token: string, payload: SharePasswordPayload) {
  return apiRequest<ShareItem>({
    url: `/v2/shares/${token}/verify-password`,
    method: 'POST',
    authRequired: false,
    data: payload,
  });
}

export async function importShare(token: string, path: string, password?: string) {
  return apiRequest<FileItem>({
    url: `/v2/shares/${token}/import`,
    method: 'POST',
    data: { path, password },
  });
}

export async function saveShare(token: string, password?: string) {
  return apiRequest<SavedShareItem>({
    url: `/v2/shares/${token}/save`,
    method: 'POST',
    data: { password },
  });
}

export async function listSavedShares(page = 0, size = 20) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return apiRequest<QueryPage<SavedShareItem>>({
    url: `/v2/shares/shared-with-me?${params.toString()}`,
    method: 'GET',
  });
}

export async function getSavedShareDetail(id: number) {
  return apiRequest<SavedShareItem>({
    url: `/v2/shares/shared-with-me/${id}`,
    method: 'GET',
  });
}

export async function deleteSavedShare(id: number) {
  return apiRequest<void>({
    url: `/v2/shares/shared-with-me/${id}`,
    method: 'DELETE',
  });
}

export function buildShareDownloadUrl(token: string, password?: string) {
  const url = new URL(`/api/v2/shares/${token}`, window.location.origin);
  url.searchParams.set('download', '1');
  if (password) {
    url.searchParams.set('password', password);
  }
  return url.toString();
}

export function buildFullShareUrl(token: string) {
  return `${window.location.origin}/share/${token}`;
}
