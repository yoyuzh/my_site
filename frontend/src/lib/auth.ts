import { apiRequest } from '../api/client';
import { getSession, setSession, clearSession, type PortalSession, type PortalUser } from './session';

type LoginPayload = {
  username: string;
  password: string;
};

type RegisterPayload = {
  username: string;
  email: string;
  phoneNumber: string;
  password: string;
  confirmPassword: string;
  inviteCode: string;
};

export async function login(payload: LoginPayload) {
  const session = await apiRequest<PortalSession>({
    url: '/auth/login',
    method: 'POST',
    authRequired: false,
    data: payload,
  });
  setSession(session);
  return session;
}

export async function register(payload: RegisterPayload) {
  const session = await apiRequest<PortalSession>({
    url: '/auth/register',
    method: 'POST',
    authRequired: false,
    data: payload,
  });
  setSession(session);
  return session;
}

export async function getProfile() {
  const profile = await apiRequest<PortalUser>({
    url: '/user/profile',
    method: 'GET',
  });
  const session = getSession();
  if (session) {
    setSession({ ...session, user: profile });
  }
  return profile;
}

export type UpdateProfilePayload = {
  displayName?: string | null;
  email?: string | null;
  phoneNumber?: string | null;
  bio?: string | null;
  preferredLanguage?: string | null;
};

export async function updateProfile(payload: UpdateProfilePayload) {
  const profile = await apiRequest<PortalUser>({
    url: '/user/profile',
    method: 'PUT',
    data: payload,
  });
  const session = getSession();
  if (session) {
    setSession({ ...session, user: profile });
  }
  return profile;
}

export type ChangePasswordPayload = {
  currentPassword: string;
  newPassword: string;
};

export async function changePassword(payload: ChangePasswordPayload) {
  const session = await apiRequest<PortalSession>({
    url: '/user/password',
    method: 'POST',
    data: payload,
  });
  setSession(session);
  return session;
}

export async function uploadAvatar(file: File) {
  const filename = file.name?.trim() || 'avatar.png';
  const contentType = file.type?.trim() || 'image/png';
  const size = file.size;
  const initiatePayload = {
    filename,
    contentType,
    size,
    storageName: '',
  };

  const initiate = await apiRequest<{
    direct: boolean;
    uploadUrl: string;
    method: string;
    headers: Record<string, string>;
    storageName: string;
  }>({
    url: '/user/avatar/upload/initiate',
    method: 'POST',
    data: initiatePayload,
  });

  if (initiate.direct) {
    const response = await fetch(initiate.uploadUrl, {
      method: initiate.method || 'PUT',
      headers: initiate.headers,
      body: file,
    });
    if (!response.ok) {
      throw new Error('头像上传失败');
    }
  } else {
    const formData = new FormData();
    formData.append('file', file);
    await apiRequest({
      url: '/user/avatar/upload',
      method: 'POST',
      params: {
        storageName: initiate.storageName,
      },
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  }

  const profile = await apiRequest<PortalUser>({
    url: '/user/avatar/upload/complete',
    method: 'POST',
    data: {
      filename,
      contentType,
      size,
      storageName: initiate.storageName,
    },
  });

  const session = getSession();
  if (session) {
    setSession({ ...session, user: profile });
  }
  return profile;
}

export function logout() {
  clearSession();
}
