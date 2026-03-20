import type { DataProvider, GetListParams, GetListResult, Identifier } from 'react-admin';

import { apiRequest } from '@/src/lib/api';
import type {
  AdminFile,
  AdminUser,
  PageResponse,
} from '@/src/lib/types';

const FILES_RESOURCE = 'files';
const USERS_RESOURCE = 'users';

function createUnsupportedError(resource: string, action: string) {
  return new Error(`当前管理台暂未为资源 "${resource}" 实现 ${action} 操作`);
}

function ensureSupportedResource(resource: string, action: string) {
  if (![FILES_RESOURCE, USERS_RESOURCE].includes(resource)) {
    throw createUnsupportedError(resource, action);
  }
}

function normalizeFilterValue(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

export function buildAdminListPath(resource: string, params: Pick<GetListParams, 'pagination' | 'filter'>) {
  const page = Math.max(0, params.pagination.page - 1);
  const size = Math.max(1, params.pagination.perPage);
  const query = normalizeFilterValue(params.filter?.query);

  if (resource === USERS_RESOURCE) {
    return `/admin/users?page=${page}&size=${size}${query ? `&query=${encodeURIComponent(query)}` : ''}`;
  }

  throw createUnsupportedError(resource, 'list');
}

export function buildFilesListPath(params: Pick<GetListParams, 'pagination' | 'filter'>) {
  const page = Math.max(0, params.pagination.page - 1);
  const size = Math.max(1, params.pagination.perPage);
  const query = normalizeFilterValue(params.filter?.query);
  const ownerQuery = normalizeFilterValue(params.filter?.ownerQuery);
  const search = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  if (query) {
    search.set('query', query);
  }
  if (ownerQuery) {
    search.set('ownerQuery', ownerQuery);
  }
  return `/admin/files?${search.toString()}`;
}

export function mapFilesListResponse(
  payload: PageResponse<AdminFile>,
): GetListResult<AdminFile> {
  return {
    data: payload.items,
    total: payload.total,
  };
}

async function deleteFile(id: Identifier) {
  await apiRequest(`/admin/files/${id}`, {
    method: 'DELETE',
  });
}

export const portalAdminDataProvider: DataProvider = {
  getList: async (resource, params) => {
    ensureSupportedResource(resource, 'list');

    if (resource === FILES_RESOURCE) {
      const payload = await apiRequest<PageResponse<AdminFile>>(buildFilesListPath(params));
      return mapFilesListResponse(payload) as GetListResult;
    }

    if (resource === USERS_RESOURCE) {
      const payload = await apiRequest<PageResponse<AdminUser>>(buildAdminListPath(resource, params));
      return {
        data: payload.items,
        total: payload.total,
      } as GetListResult;
    }

    throw createUnsupportedError(resource, 'list');
  },
  getOne: async (resource) => {
    ensureSupportedResource(resource, 'getOne');
    throw createUnsupportedError(resource, 'getOne');
  },
  getMany: async (resource) => {
    ensureSupportedResource(resource, 'getMany');
    throw createUnsupportedError(resource, 'getMany');
  },
  getManyReference: async (resource) => {
    ensureSupportedResource(resource, 'getManyReference');
    throw createUnsupportedError(resource, 'getManyReference');
  },
  update: async (resource) => {
    ensureSupportedResource(resource, 'update');
    throw createUnsupportedError(resource, 'update');
  },
  updateMany: async (resource) => {
    ensureSupportedResource(resource, 'updateMany');
    throw createUnsupportedError(resource, 'updateMany');
  },
  create: async (resource) => {
    ensureSupportedResource(resource, 'create');
    throw createUnsupportedError(resource, 'create');
  },
  delete: async (resource, params) => {
    if (resource !== FILES_RESOURCE) {
      throw createUnsupportedError(resource, 'delete');
    }
    await deleteFile(params.id);
    const fallbackRecord = { id: params.id } as typeof params.previousData;
    return {
      data: (params.previousData ?? fallbackRecord) as typeof params.previousData,
    };
  },
  deleteMany: async (resource, params) => {
    if (resource !== FILES_RESOURCE) {
      throw createUnsupportedError(resource, 'deleteMany');
    }
    await Promise.all(params.ids.map((id) => deleteFile(id)));
    return {
      data: params.ids,
    };
  },
};
