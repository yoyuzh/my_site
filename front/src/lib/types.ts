export interface UserProfile {
  id: number;
  username: string;
  email: string;
  createdAt: string;
}

export interface AuthSession {
  token: string;
  user: UserProfile;
}

export interface AuthResponse {
  token: string;
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
