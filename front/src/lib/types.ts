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
  usersWithSchoolCache: number;
}

export interface AdminUser {
  id: number;
  username: string;
  email: string;
  phoneNumber: string | null;
  createdAt: string;
  lastSchoolStudentId: string | null;
  lastSchoolSemester: string | null;
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

export interface AdminSchoolSnapshot {
  id: number;
  userId: number;
  username: string;
  email: string;
  studentId: string | null;
  semester: string | null;
  scheduleCount: number;
  gradeCount: number;
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

export interface CourseResponse {
  courseName: string;
  teacher: string | null;
  classroom: string | null;
  dayOfWeek: number | null;
  startTime: number | null;
  endTime: number | null;
}

export interface GradeResponse {
  courseName: string;
  grade: number | null;
  semester: string | null;
}

export interface LatestSchoolDataResponse {
  studentId: string;
  semester: string;
  schedule: CourseResponse[];
  grades: GradeResponse[];
}
