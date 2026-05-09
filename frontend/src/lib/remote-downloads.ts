import { apiRequest } from '../api/client';
import type {
  RemoteDownloadDetail,
  RemoteDownloadListItem,
  RemoteDownloadSourceType,
} from '../api/types';

export interface CreateRemoteDownloadPayload {
  sourceType: RemoteDownloadSourceType;
  sourceValue?: string;
  torrentFile?: File | null;
  targetPath: string;
}

export async function createRemoteDownload(payload: CreateRemoteDownloadPayload) {
  const formData = new FormData();
  formData.set('sourceType', payload.sourceType);
  formData.set('targetPath', payload.targetPath);
  if (payload.sourceValue?.trim()) {
    formData.set('sourceValue', payload.sourceValue.trim());
  }
  if (payload.torrentFile) {
    formData.set('torrentFile', payload.torrentFile);
  }
  return apiRequest<RemoteDownloadDetail>({
    url: '/transfer/remote-downloads',
    method: 'POST',
    data: formData,
  });
}

export async function listRemoteDownloads() {
  return apiRequest<RemoteDownloadListItem[]>({
    url: '/transfer/remote-downloads',
    method: 'GET',
  });
}

export async function getRemoteDownload(id: number) {
  return apiRequest<RemoteDownloadDetail>({
    url: `/transfer/remote-downloads/${id}`,
    method: 'GET',
  });
}

export async function cancelRemoteDownload(id: number) {
  return apiRequest<RemoteDownloadDetail>({
    url: `/transfer/remote-downloads/${id}`,
    method: 'DELETE',
  });
}

export async function retryRemoteDownload(id: number) {
  return apiRequest<RemoteDownloadDetail>({
    url: `/transfer/remote-downloads/${id}/retry`,
    method: 'POST',
  });
}

export async function selectRemoteDownloadFiles(id: number, fileKeys: string[]) {
  return apiRequest<RemoteDownloadDetail>({
    url: `/transfer/remote-downloads/${id}/selection`,
    method: 'POST',
    data: { fileKeys },
  });
}
