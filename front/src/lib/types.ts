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
}

export type AdminUserRole = 'USER' | 'MODERATOR' | 'ADMIN';

export interface AdminSummary {
  totalUsers: number;
  totalFiles: number;
  inviteCode: string;
}

export interface AdminUser {
  id: number;
  username: string;
  email: string;
  phoneNumber: string | null;
  createdAt: string;
  role: AdminUserRole;
  banned: boolean;
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
