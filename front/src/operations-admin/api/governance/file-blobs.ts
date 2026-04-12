import { fetchApi } from '@/src/lib/api';
import type { PageResponse } from '@/src/lib/files';

export type AdminFileBlobEntityType = 'VERSION' | 'THUMBNAIL' | 'LIVE_PHOTO' | 'TRANSCODE' | 'AVATAR';

export type AdminFileBlobResponse = {
  entityId: number;
  blobId: number;
  objectKey: string;
  entityType: AdminFileBlobEntityType;
  storagePolicyId: number;
  size: number;
  contentType: string;
  referenceCount: number | null;
  linkedStoredFileCount: number;
  linkedOwnerCount: number;
  sampleOwnerUsername: string | null;
  sampleOwnerEmail: string | null;
  createdByUserId: number | null;
  createdByUsername: string | null;
  createdAt: string;
  blobCreatedAt: string | null;
  blobMissing: boolean;
  orphanRisk: boolean;
  referenceMismatch: boolean;
};

export type AdminFileBlobQuery = {
  userQuery?: string;
  storagePolicyId?: number | null;
  objectKey?: string;
  entityType?: AdminFileBlobEntityType | '';
};

export async function getAdminFileBlobs(page = 0, size = 100, query: AdminFileBlobQuery = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  if (query.userQuery?.trim()) {
    params.set('userQuery', query.userQuery.trim());
  }

  if (query.storagePolicyId != null && Number.isInteger(query.storagePolicyId)) {
    params.set('storagePolicyId', String(query.storagePolicyId));
  }

  if (query.objectKey?.trim()) {
    params.set('objectKey', query.objectKey.trim());
  }

  if (query.entityType) {
    params.set('entityType', query.entityType);
  }

  return fetchApi<PageResponse<AdminFileBlobResponse>>(`/admin/file-blobs?${params.toString()}`);
}

