import { apiRequest, resolveApiUrl } from '../api/client';

export type WebDavCredential = {
  username: string;
  endpoint: string;
  enabled: boolean;
  createdAt?: string | null;
  updatedAt?: string | null;
  plaintextPassword?: string | null;
};

export function getWebDavUrl(endpoint = '/dav') {
  return resolveApiUrl(endpoint);
}

export async function getWebDavCredential() {
  return apiRequest<WebDavCredential>({
    url: '/user/webdav-credential',
    method: 'GET',
  });
}

export async function issueWebDavCredential() {
  return apiRequest<WebDavCredential>({
    url: '/user/webdav-credential',
    method: 'POST',
  });
}
