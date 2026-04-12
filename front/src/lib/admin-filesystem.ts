import { fetchApi } from './api';
import type { AdminStoragePolicy } from './admin-storage-policies';

export type AdminFilesystemResponse = {
  overview: {
    storageProvider: string;
    totalFiles: number;
    totalBlobs: number;
    totalEntities: number;
  };
  defaultPolicy: AdminStoragePolicy;
  upload: {
    proxyUpload: boolean;
    directSingleUpload: boolean;
    directMultipartUpload: boolean;
    effectiveMaxFileSizeBytes: number;
  };
  mediaProcessing: {
    metadataExtractionEnabled: boolean;
    nativeThumbnailSupport: boolean;
  };
  cache: {
    backend: string;
    filesListTtlSeconds: number;
    directoryVersionTtlSeconds: number;
  };
  webdav: {
    enabled: boolean;
  };
};

export async function getAdminFilesystem() {
  return fetchApi<AdminFilesystemResponse>('/admin/filesystem');
}
