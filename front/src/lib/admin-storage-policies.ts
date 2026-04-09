import { fetchApi } from './api';

export type StoragePolicyCapabilities = {
  directUpload: boolean;
  multipartUpload: boolean;
  signedDownloadUrl: boolean;
  serverProxyDownload: boolean;
  thumbnailNative: boolean;
  friendlyDownloadName: boolean;
  requiresCors: boolean;
  supportsInternalEndpoint: boolean;
  maxObjectSize: number;
};

export type AdminStoragePolicy = {
  id: number;
  name: string;
  type: 'LOCAL' | 'S3_COMPATIBLE';
  bucketName: string | null;
  endpoint: string | null;
  region: string | null;
  privateBucket: boolean;
  prefix: string | null;
  credentialMode: 'NONE' | 'STATIC' | 'DOGECLOUD_TEMP';
  maxSizeBytes: number;
  capabilities: StoragePolicyCapabilities;
  enabled: boolean;
  defaultPolicy: boolean;
  createdAt: string;
  updatedAt: string;
};

export type StoragePolicyUpsertPayload = {
  name: string;
  type: AdminStoragePolicy['type'];
  bucketName?: string;
  endpoint?: string;
  region?: string;
  privateBucket: boolean;
  prefix?: string;
  credentialMode: AdminStoragePolicy['credentialMode'];
  maxSizeBytes: number;
  capabilities: StoragePolicyCapabilities;
  enabled: boolean;
};

export async function getStoragePolicies() {
  return fetchApi<AdminStoragePolicy[]>('/admin/storage-policies');
}

export async function createStoragePolicy(policyData: StoragePolicyUpsertPayload) {
  return fetchApi<AdminStoragePolicy>('/admin/storage-policies', {
    method: 'POST',
    body: JSON.stringify(policyData),
  });
}

export async function updateStoragePolicy(policyId: number, policyData: StoragePolicyUpsertPayload) {
  return fetchApi<AdminStoragePolicy>(`/admin/storage-policies/${policyId}`, {
    method: 'PUT',
    body: JSON.stringify(policyData),
  });
}

export async function updateStoragePolicyStatus(policyId: number, enabled: boolean) {
  return fetchApi<AdminStoragePolicy>(`/admin/storage-policies/${policyId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ enabled }),
  });
}

export async function createStorageMigration(sourcePolicyId: number, targetPolicyId: number) {
  return fetchApi('/admin/storage-policies/migrations', {
    method: 'POST',
    body: JSON.stringify({ sourcePolicyId, targetPolicyId }),
  });
}
