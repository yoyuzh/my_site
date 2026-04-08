import { apiV2Request } from './api';
import type { FileMetadata, PageResponse } from './types';

export type FileSearchType = 'file' | 'directory' | 'folder' | 'all';

export interface FileSearchParams {
  name?: string;
  type?: FileSearchType;
  sizeGte?: number;
  sizeLte?: number;
  createdGte?: string;
  createdLte?: string;
  updatedGte?: string;
  updatedLte?: string;
  page?: number;
  size?: number;
}

function appendStringParam(searchParams: URLSearchParams, key: string, value?: string) {
  const normalizedValue = value?.trim();
  if (!normalizedValue) {
    return;
  }

  searchParams.set(key, normalizedValue);
}

function appendNumberParam(searchParams: URLSearchParams, key: string, value?: number) {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return;
  }

  searchParams.set(key, String(value));
}

export function buildFileSearchPath(params: FileSearchParams = {}) {
  const searchParams = new URLSearchParams();

  appendStringParam(searchParams, 'name', params.name);
  appendStringParam(searchParams, 'type', params.type);
  appendNumberParam(searchParams, 'sizeGte', params.sizeGte);
  appendNumberParam(searchParams, 'sizeLte', params.sizeLte);
  appendStringParam(searchParams, 'createdGte', params.createdGte);
  appendStringParam(searchParams, 'createdLte', params.createdLte);
  appendStringParam(searchParams, 'updatedGte', params.updatedGte);
  appendStringParam(searchParams, 'updatedLte', params.updatedLte);
  appendNumberParam(searchParams, 'page', params.page);
  appendNumberParam(searchParams, 'size', params.size);

  const query = searchParams.toString();
  return query ? `/files/search?${query}` : '/files/search';
}

export function searchFiles(params: FileSearchParams = {}) {
  return apiV2Request<PageResponse<FileMetadata>>(buildFileSearchPath(params));
}
