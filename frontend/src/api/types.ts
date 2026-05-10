import type {
  AdminConfigFieldType,
  AdminConfigOption,
  AdminConfigValidationRules,
} from '../components/admin/adminSchemaTypes';

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
  actorQuery?: string;
  actionType?: string;
  targetType?: string;
  targetId?: number;
  ownerQuery?: string;
  fileName?: string;
  token?: string;
  expired?: boolean;
  passwordProtected?: boolean;
  storagePolicyId?: number;
  objectKey?: string;
  entityType?: string;
}

export interface AdminPermissionResponse {
  permissions: string[];
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
  defaultOpenWithByExt: Record<string, string>;
  uploadConcurrency: number;
}

export interface UpdateUserSettingsPayload {
  preferredLanguage?: string;
  preferredTheme?: 'system' | 'light' | 'dark';
  disableViewSync?: boolean;
  defaultOpenWithByExt?: Record<string, string>;
  uploadConcurrency?: number;
}

export type FileViewerType = 'builtin' | 'custom' | 'wopi';

export interface FileViewerTemplate {
  viewerId: string;
  extension: string;
  displayName: string;
  filename: string;
  content: string;
  contentType: string;
}

export interface FileViewerDefinition {
  id: string;
  type: FileViewerType;
  displayName: string;
  icon: string;
  extensions: string[];
  maxSizeBytes: number | null;
  openInNew: boolean;
  recommended: boolean;
  templates: FileViewerTemplate[];
  props: Record<string, unknown>;
}

export interface FileViewerConfig {
  fileViewers: FileViewerDefinition[];
  defaultViewerMapping: Record<string, string>;
}

export interface FileItem {
  id: number;
  filename: string;
  path: string;
  size: number;
  contentType: string;
  directory: boolean;
  createdAt: string;
  updatedAt: string | null;
  hasChildDirectory: boolean;
  customEmoji: string | null;
  folderColor: string | null;
}

export type MoveConflictStrategy = 'AUTO_RENAME' | 'SKIP';

export interface MoveResultItem {
  fileId: number;
  filename: string;
  fromPath: string | null;
  toPath: string | null;
  renamed: boolean;
  skipped: boolean;
  customEmoji: string | null;
  folderColor: string | null;
}

export interface MoveResponse {
  status: 'SUCCESS' | 'CONFLICT' | 'INVALID_TARGET';
  items: MoveResultItem[];
  conflicts: MoveResultItem[];
  message: string | null;
}

export interface FileTag {
  id: number;
  name: string;
  color: string;
}

export type MediaCategory = 'image' | 'video' | 'audio' | 'document';
export type RemoteDownloadSourceType = 'HTTP' | 'MAGNET' | 'TORRENT_FILE';

export interface FileDetail extends FileItem {
  favorite: boolean;
  shared: boolean;
  tags: FileTag[];
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

export interface ArchiveEntry {
  relativePath: string;
  directory: boolean;
  size: number;
  contentType: string;
}

export interface ArchiveListing {
  entries: ArchiveEntry[];
  commonRootDirectoryName: string | null;
}

export interface UploadSessionStrategy {
  prepareUrl: string | null;
  proxyContentUrl: string | null;
  partPrepareUrlTemplate: string | null;
  partRecordUrlTemplate: string | null;
  completeUrl: string | null;
  proxyFormField: string | null;
  tusUrl: string | null;
  tusHeaders: Record<string, string> | null;
}

export interface UploadSessionRuntimeState {
  phase: string;
  uploadedBytes: number;
  uploadedPartCount: number;
  progressPercent: number | null;
  lastUpdatedAt: string;
  expiresAt: string;
}

export interface PreparedUploadResponse {
  direct: boolean;
  uploadUrl: string;
  method: string;
  headers: Record<string, string>;
  storageName: string;
}

export interface UploadSessionResponse {
  sessionId: string;
  objectKey: string;
  directUpload: boolean;
  multipartUpload: boolean;
  uploadMode: 'PROXY' | 'DIRECT_SINGLE' | 'DIRECT_MULTIPART';
  path: string;
  filename: string;
  contentType: string;
  size: number;
  storagePolicyId: number | null;
  status: string;
  chunkSize: number;
  chunkCount: number;
  expiresAt: string;
  createdAt: string;
  updatedAt: string;
  runtime: UploadSessionRuntimeState | null;
  strategy: UploadSessionStrategy;
}

export interface RecycleBinItem extends FileItem {
  deletedAt: string;
  expiresAt: string;
}

export type FileDeleteMode = 'RECYCLE' | 'PERMANENT';

export interface FileBatchDeletePayload {
  fileIds: number[];
  mode: FileDeleteMode;
}

export interface ShareItem {
  id: number;
  token: string;
  shareName: string | null;
  ownerUsername: string;
  passwordRequired: boolean;
  passwordVerified: boolean;
  password?: string | null; // Owner-facing only
  allowImport: boolean;
  allowDownload: boolean;
  maxDownloads: number | null;
  downloadCount: number;
  viewCount: number;
  expiresAt: string | null;
  expireAfterConsume: boolean;
  status: 'ACTIVE' | 'EXPIRED' | 'CONSUMED' | 'REMOVED';
  createdAt: string;
  file: FileItem | null;
}

export interface SavedShareItem {
  id: number;
  savedAt: string;
  share: ShareItem;
}

export interface CreateSharePayload {
  fileId: number;
  shareName?: string;
  password?: string;
  expiresAt?: string;
  maxDownloads?: number;
  allowImport?: boolean;
  allowDownload?: boolean;
  expireAfterConsume?: boolean;
}

export interface UpdateSharePolicyPayload {
  password?: string;
  expiresAt?: string;
  maxDownloads?: number;
  expireAfterConsume?: boolean;
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

export interface RemoteDownloadCandidateFile {
  fileKey: string;
  relativePath: string;
  size: number;
  selected: boolean;
}

export interface RemoteDownloadDetail {
  id: number;
  backgroundTaskId: number | null;
  status: string;
  sourceType: RemoteDownloadSourceType;
  engineType: string;
  targetPath: string;
  sourceValue: string;
  filename: string;
  downloadNodeId: string;
  selectedFileCount: number;
  importedFileCount: number;
  failureCode: string | null;
  failureMessage: string | null;
  candidateFiles: RemoteDownloadCandidateFile[];
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
}

export interface RemoteDownloadListItem {
  id: number;
  backgroundTaskId: number | null;
  status: string;
  sourceType: RemoteDownloadSourceType;
  engineType: string;
  targetPath: string;
  sourceValue: string;
  filename: string;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
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
}

export type AdminConfigSource = 'runtime' | 'environment' | 'database' | 'computed';

export interface AdminConfigDefinition {
  key: string;
  group: string;
  subgroup?: string | null;
  title: string;
  description?: string | null;
  type: AdminConfigFieldType;
  defaultValue?: unknown;
  value?: unknown;
  options?: AdminConfigOption[];
  required: boolean;
  editable: boolean;
  sensitive: boolean;
  restartRequired: boolean;
  validationRules?: AdminConfigValidationRules;
  permissionCode?: string | null;
  source: AdminConfigSource;
}

export interface AdminConfigSnapshot {
  fields: AdminConfigDefinition[];
}

export interface AdminConfigHistory {
  id: number;
  key: string;
  beforeValue: unknown;
  afterValue: unknown;
  version: number;
  reason: string;
  actorUserId: number | null;
  actorUsername: string;
  createdAt: string;
}

export interface AdminAuditLog {
  id: number;
  actorUserId: number | null;
  actorUsername: string;
  actorAuthorities: string;
  actionType: string;
  targetType: string;
  targetId: number | null;
  summary: string;
  detailsJson: string;
  createdAt: string;
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
