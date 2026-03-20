import { apiRequest } from './api';
import { getTransferRouterMode, type TransferRouterMode } from './transfer-links';
import type { CreateFileShareLinkResponse, FileMetadata, FileShareDetailsResponse } from './types';

export const FILE_SHARE_ROUTE_PREFIX = '/share';

export function buildFileShareUrl(
  origin: string,
  token: string,
  routerMode: TransferRouterMode = 'browser',
) {
  const normalizedOrigin = origin.replace(/\/+$/, '');
  const encodedToken = encodeURIComponent(token);

  if (routerMode === 'hash') {
    return `${normalizedOrigin}/#${FILE_SHARE_ROUTE_PREFIX}/${encodedToken}`;
  }

  return `${normalizedOrigin}${FILE_SHARE_ROUTE_PREFIX}/${encodedToken}`;
}

export function getPostLoginRedirectPath(nextPath: string | null, fallback = '/overview') {
  if (!nextPath || !nextPath.startsWith('/') || nextPath.startsWith('//')) {
    return fallback;
  }

  return nextPath;
}

export function createFileShareLink(fileId: number) {
  return apiRequest<CreateFileShareLinkResponse>(`/files/${fileId}/share-links`, {
    method: 'POST',
  });
}

export function getFileShareDetails(token: string) {
  return apiRequest<FileShareDetailsResponse>(`/files/share-links/${encodeURIComponent(token)}`);
}

export function importSharedFile(token: string, path: string) {
  return apiRequest<FileMetadata>(`/files/share-links/${encodeURIComponent(token)}/import`, {
    method: 'POST',
    body: { path },
  });
}

export function getCurrentFileShareUrl(token: string) {
  return buildFileShareUrl(window.location.origin, token, getTransferRouterMode());
}
