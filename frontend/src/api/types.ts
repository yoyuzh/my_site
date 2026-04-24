export interface PaginationResults {
  total_items: number;
  total_pages: number;
  page: number;
  page_size: number;
}

export interface QueryPage<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface UiPage<T> {
  items: T[];
  pagination: PaginationResults;
}

export interface AdminListParams {
  page: number;
  page_size: number;
  query?: string;
  userQuery?: string;
  ownerQuery?: string;
  fileName?: string;
  token?: string;
  expired?: boolean;
  passwordProtected?: boolean;
  storagePolicyId?: number;
  objectKey?: string;
  entityType?: string;
}

export interface UserCapacity {
  totalBytes: number;
  usedBytes: number;
  availableBytes: number;
  maxUploadSizeBytes: number;
}

export interface UserSettings {
  displayName: string;
  preferredLanguage: string;
  preferredTheme: 'system' | 'light' | 'dark';
  disableViewSync: boolean;
}

export interface FileItem {
  id: number;
  filename: string;
  path: string;
  size: number;
  contentType: string;
  directory: boolean;
  createdAt: string;
}

export interface FileDetail extends FileItem {
  favorite: boolean;
  shared: boolean;
  updatedAt: string | null;
}

export interface FavoriteFileResponse {
  fileId: number;
  favorite: boolean;
}

export interface ThumbnailResponse {
  fileId: number;
  available: boolean;
  url: string;
}

export interface DownloadUrlResponse {
  url: string;
}

export interface RecycleBinItem extends FileItem {
  deletedAt: string;
  expiresAt: string;
}

export interface ShareItem {
  id: number;
  token: string;
  shareName: string | null;
  ownerUsername: string;
  passwordRequired: boolean;
  passwordVerified: boolean;
  allowImport: boolean;
  allowDownload: boolean;
  maxDownloads: number | null;
  downloadCount: number;
  viewCount: number;
  expiresAt: string | null;
  createdAt: string;
  file: FileItem | null;
}

export interface ShareStats {
  token: string;
  visits: number;
  downloads: number;
  maxDownloads: number | null;
  downloadLimitReached: boolean;
}

export interface SharePasswordPayload {
  password: string;
}

export interface BackgroundTask {
  id: number;
  type: string;
  status: string;
  userId: number;
  publicStateJson: string | null;
  correlationId: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
}

export interface TaskProgress {
  taskId: number;
  status: string;
  progressPercent: number;
  processedItems: number;
  totalItems: number;
  message: string;
}

export type TransferMode = 'ONLINE' | 'OFFLINE';

export interface TransferFileItem {
  id?: string | null;
  name: string;
  relativePath: string;
  size: number;
  contentType: string;
  uploaded?: boolean | null;
}

export interface TransferSessionResponse {
  sessionId: string;
  pickupCode: string;
  mode: TransferMode;
  expiresAt: string;
  files: TransferFileItem[];
}

export interface LookupTransferSessionResponse {
  sessionId: string;
  pickupCode: string;
  mode: TransferMode;
  expiresAt: string;
}

export interface TransferSignalEnvelope {
  cursor: number;
  type: string;
  payload: string;
}

export interface PollTransferSignalsResponse {
  items: TransferSignalEnvelope[];
  nextCursor: number;
}

export interface AdminSummary {
  totalUsers: number;
  totalFiles: number;
  totalStorageBytes: number;
  downloadTrafficBytes: number;
  requestCount: number;
  transferUsageBytes: number;
  offlineTransferStorageBytes: number;
  offlineTransferStorageLimitBytes: number;
  favoriteFileCount: number;
  shareDownloadCount: number;
  activeTaskCount: number;
  dailyActiveUsers: {
    metricDate: string;
    label: string;
    userCount: number;
    usernames: string[];
  }[];
  requestTimeline: {
    hour: number;
    label: string;
    requestCount: number;
  }[];
  inviteCode: string;
}

export interface AdminUser {
  id: number;
  username: string;
  email: string;
  phoneNumber: string | null;
  createdAt: string;
  role: 'USER' | 'MODERATOR' | 'ADMIN';
  banned: boolean;
  usedStorageBytes: number;
  storageQuotaBytes: number;
  maxUploadSizeBytes: number;
}

export interface AdminStoragePolicy {
  id: number;
  name: string;
  type: string;
  bucketName: string | null;
  endpoint: string | null;
  region: string | null;
  privateBucket: boolean;
  prefix: string | null;
  credentialMode: string;
  maxSizeBytes: number;
  capabilities: StoragePolicyCapabilities;
  enabled: boolean;
  defaultPolicy: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface StoragePolicyCapabilities {
  directUpload: boolean;
  multipartUpload: boolean;
  signedDownloadUrl: boolean;
  serverProxyDownload: boolean;
  thumbnailNative: boolean;
  friendlyDownloadName: boolean;
  requiresCors: boolean;
  supportsInternalEndpoint: boolean;
  maxObjectSize: number;
}

export interface AdminFile {
  id: number;
  filename: string;
  path: string;
  size: number;
  contentType: string;
  directory: boolean;
  createdAt: string;
  ownerId: number | null;
  ownerUsername: string | null;
  ownerEmail: string | null;
  favorite: boolean;
  thumbnailAvailable: boolean;
}

export interface AdminFileBlob {
  entityId: number;
  blobId: number;
  objectKey: string;
  entityType: string;
  storagePolicyId: number | null;
  size: number | null;
  contentType: string | null;
  referenceCount: number | null;
  linkedStoredFileCount: number;
  linkedOwnerCount: number;
  sampleOwnerUsername: string | null;
  sampleOwnerEmail: string | null;
  createdByUserId: number | null;
  createdByUsername: string | null;
  createdAt: string;
  blobCreatedAt: string;
  blobMissing: boolean;
  orphanRisk: boolean;
  referenceMismatch: boolean;
}

export interface AdminTask {
  id: number;
  type: string;
  status: string;
  userId: number;
  ownerUsername: string | null;
  ownerEmail: string | null;
  publicStateJson: string | null;
  correlationId: string | null;
  errorMessage: string | null;
  attemptCount: number | null;
  maxAttempts: number | null;
  nextRunAt: string | null;
  leaseOwner: string | null;
  leaseExpiresAt: string | null;
  heartbeatAt: string | null;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
  failureCategory: string | null;
  retryScheduled: boolean | null;
  workerOwner: string | null;
  leaseState: string | null;
}

export interface AdminShare {
  id: number;
  token: string;
  shareName: string | null;
  passwordProtected: boolean;
  expired: boolean;
  createdAt: string;
  expiresAt: string | null;
  maxDownloads: number | null;
  downloadCount: number;
  viewCount: number;
  allowImport: boolean;
  allowDownload: boolean;
  ownerId: number | null;
  ownerUsername: string | null;
  ownerEmail: string | null;
  fileId: number | null;
  fileName: string | null;
  filePath: string | null;
  fileContentType: string | null;
  fileSize: number;
  directory: boolean;
}

export interface AdminFilesystem {
  overview: {
    storageProvider: string;
    totalFiles: number;
    totalBlobs: number;
    totalEntities: number;
  };
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
}

export interface AdminSettings {
  registration: {
    inviteCodeRequired: boolean;
    currentInviteCode: string;
    managementRoles: string[];
    writeSupported: boolean;
  };
  userSession: {
    accessExpirationSeconds: number;
    refreshExpirationSeconds: number;
    tokenBlacklistEnabled: boolean;
    tokenBlacklistTtlBufferSeconds: number;
    writeSupported: boolean;
  };
  transfer: {
    offlineTransferStorageLimitBytes: number;
    writeSupported: boolean;
  };
  mediaProcessing: {
    metadataExtractionEnabled: boolean;
    thumbnailGenerationEnabled: boolean;
    videoPosterEnabled: boolean;
    writeSupported: boolean;
  };
  queue: {
    backend: string;
    mediaMetadataFixedDelayMs: number;
    mediaMetadataInitialDelayMs: number;
    writeSupported: boolean;
  };
  server: {
    storageProvider: string;
    redisEnabled: boolean;
    writeSupported: boolean;
  };
  site: {
    supported: boolean;
    writeSupported: boolean;
  };
  appearance: {
    supported: boolean;
    writeSupported: boolean;
  };
}
