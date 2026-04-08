export interface UserProfile {
  id: number;
  username: string;
  displayName?: string | null;
  email: string;
  phoneNumber?: string | null;
  bio?: string | null;
  preferredLanguage?: string | null;
  avatarUrl?: string | null;
  role?: AdminUserRole;
  createdAt: string;
  storageQuotaBytes?: number;
  maxUploadSizeBytes?: number;
}

export type AdminUserRole = 'USER' | 'MODERATOR' | 'ADMIN';

export interface AdminRequestTimelinePoint {
  hour: number;
  label: string;
  requestCount: number;
}

export interface AdminDailyActiveUserSummary {
  metricDate: string;
  label: string;
  userCount: number;
  usernames: string[];
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
  dailyActiveUsers: AdminDailyActiveUserSummary[];
  requestTimeline: AdminRequestTimelinePoint[];
  inviteCode: string;
}

export interface AdminOfflineTransferStorageLimitResponse {
  offlineTransferStorageLimitBytes: number;
}

export interface AdminUser {
  id: number;
  username: string;
  email: string;
  phoneNumber: string | null;
  createdAt: string;
  role: AdminUserRole;
  banned: boolean;
  usedStorageBytes: number;
  storageQuotaBytes: number;
  maxUploadSizeBytes: number;
}

export interface AdminFile {
  id: number;
  filename: string;
  path: string;
  size: number;
  contentType: string | null;
  directory: boolean;
  createdAt: string;
  ownerId: number;
  ownerUsername: string;
  ownerEmail: string;
}

export type StoragePolicyType = 'LOCAL' | 'S3_COMPATIBLE';
export type StoragePolicyCredentialMode = 'NONE' | 'STATIC' | 'DOGECLOUD_TEMP';

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

export interface AdminStoragePolicy {
  id: number;
  name: string;
  type: StoragePolicyType;
  bucketName: string | null;
  endpoint: string | null;
  region: string | null;
  privateBucket: boolean;
  prefix: string | null;
  credentialMode: StoragePolicyCredentialMode;
  maxSizeBytes: number;
  capabilities: StoragePolicyCapabilities;
  enabled: boolean;
  defaultPolicy: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminPasswordResetResponse {
  temporaryPassword: string;
}

export interface AuthSession {
  token: string;
  refreshToken?: string | null;
  user: UserProfile;
}

export interface AuthResponse {
  token: string;
  accessToken?: string;
  refreshToken?: string | null;
  user: UserProfile;
}

export interface PageResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface FileMetadata {
  id: number;
  filename: string;
  path: string;
  size: number;
  contentType: string | null;
  directory: boolean;
  createdAt: string;
}

export interface RecycleBinItem {
  id: number;
  filename: string;
  path: string;
  size: number;
  contentType: string | null;
  directory: boolean;
  createdAt: string;
  deletedAt: string;
  expiresAt: string;
}

export interface InitiateUploadResponse {
  direct: boolean;
  uploadUrl: string;
  method: 'POST' | 'PUT';
  headers: Record<string, string>;
  storageName: string;
}

export interface DownloadUrlResponse {
  url: string;
}

export interface AndroidReleaseInfo {
  downloadUrl: string;
  fileName: string;
  versionCode: string | null;
  versionName: string | null;
  publishedAt: string | null;
}

export interface CreateFileShareLinkResponse {
  token: string;
  filename: string;
  size: number;
  contentType: string | null;
  createdAt: string;
}

export interface FileShareDetailsResponse {
  token: string;
  ownerUsername: string;
  filename: string;
  size: number;
  contentType: string | null;
  directory: boolean;
  createdAt: string;
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
