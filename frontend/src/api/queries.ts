import { useQuery } from '@tanstack/react-query';
import { apiRequest } from './client';
import {
  type AdminAuditLog,
  type AdminConfigDefinition,
  type AdminConfigHistory,
  type AdminConfigSnapshot,
  type AdminFile,
  type AdminFileBlob,
  type AdminFilesystem,
  type AdminListParams,
  type AdminPermissionResponse,
  type AdminSettings,
  type AdminShare,
  type AdminStoragePolicy,
  type AdminSummary,
  type AdminTask,
  type AdminUser,
  type BackgroundTask,
  type FileItem,
  type MediaCategory,
  type QueryPage,
  type RecycleBinItem,
  type RemoteDownloadDetail,
  type RemoteDownloadListItem,
  type ShareItem,
  type UiPage,
  type UserCapacity,
} from './types';
import { listFavoriteFiles, listFiles, listRecentFiles, listRecycleBin, searchFiles } from '../lib/files';
import { getRemoteDownload, listRemoteDownloads } from '../lib/remote-downloads';
import { getMyShares, listSavedShares, getSavedShareDetail } from '../lib/shares';
import { getTasks } from '../lib/tasks';
import { getUserCapacity } from '../lib/user-settings';

type FilesQueryResult = UiPage<FileItem> & {
  contextKey: string;
};

function normalizePage<T>(result: QueryPage<T>): UiPage<T> {
  return {
    items: result.items,
    pagination: {
      total_items: result.total,
      total_pages: Math.max(1, Math.ceil(result.total / Math.max(1, result.size))),
      page: result.page + 1,
      page_size: result.size,
    },
  };
}

function toBackendPage(params: AdminListParams) {
  return Math.max(0, params.page - 1);
}

function unsupportedQuery(name: string) {
  throw new Error(`${name} 当前后端未提供对应接口`);
}

export const useUserCapacity = () =>
  useQuery({
    queryKey: ['userCapacity'],
    queryFn: () => getUserCapacity(),
  });

export const useRecentFiles = () =>
  useQuery({
    queryKey: ['recentFiles'],
    queryFn: () => listRecentFiles(),
  });

export const useFavoriteFiles = () =>
  useQuery({
    queryKey: ['favoriteFiles'],
    queryFn: () => listFavoriteFiles(),
  });

export const useFiles = (
  path = '/',
  page = 1,
  size = 20,
  search = '',
  options?: { category?: MediaCategory },
) =>
  useQuery({
    queryKey: ['files', path, page, size, search, options?.category ?? null],
    queryFn: async (): Promise<FilesQueryResult> => {
      const contextKey = `${options?.category ?? 'directory'}::${path}::${search.trim()}`;
      if (options?.category || search.trim()) {
        const result = await searchFiles({
          name: search.trim() || undefined,
          page: Math.max(0, page - 1),
          size,
          type: options?.category ? 'file' : undefined,
          category: options?.category,
        });
        return {
          ...normalizePage(result),
          contextKey,
        };
      }
      return {
        ...normalizePage(await listFiles(path, Math.max(0, page - 1), size)),
        contextKey,
      };
    },
  });

export const useRecycleBin = (page = 1, size = 20) =>
  useQuery({
    queryKey: ['recycleBin', page, size],
    queryFn: async () => normalizePage(await listRecycleBin(Math.max(0, page - 1), size)),
  });

export const useTasks = (page = 1, size = 20) =>
  useQuery({
    queryKey: ['tasks', page, size],
    queryFn: async () => normalizePage(await getTasks(Math.max(0, page - 1), size)),
  });

export const useRemoteDownloads = () =>
  useQuery<RemoteDownloadListItem[]>({
    queryKey: ['remoteDownloads'],
    queryFn: () => listRemoteDownloads(),
  });

export const useRemoteDownloadDetail = (id: number | null) =>
  useQuery<RemoteDownloadDetail>({
    queryKey: ['remoteDownloadDetail', id],
    queryFn: () => getRemoteDownload(id as number),
    enabled: id != null,
  });

export const useMyShares = (page = 1, size = 20) =>
  useQuery({
    queryKey: ['myShares', page, size],
    queryFn: async () => normalizePage(await getMyShares(Math.max(0, page - 1), size)),
  });

export const useSharedWithMe = (page = 1, size = 20) =>
  useQuery({
    queryKey: ['sharedWithMe', page, size],
    queryFn: async () => {
      return normalizePage(await listSavedShares(Math.max(0, page - 1), size));
    },
  });

export const useSharedWithMeDetail = (id: number) =>
  useQuery({
    queryKey: ['sharedWithMeDetail', id],
    queryFn: () => getSavedShareDetail(id),
    enabled: !!id,
  });

export const useAdminSummary = () =>
  useQuery({
    queryKey: ['adminSummary'],
    queryFn: () =>
      apiRequest<AdminSummary>({
        url: '/admin/summary',
        method: 'GET',
      }),
  });

export const useAdminPermissions = () =>
  useQuery({
    queryKey: ['adminPermissions'],
    queryFn: () =>
      apiRequest<AdminPermissionResponse>({
        url: '/admin/permissions',
        method: 'GET',
      }),
  });

export const useAdminUsers = (params: AdminListParams) =>
  useQuery({
    queryKey: ['adminUsers', params],
    queryFn: async () => {
      const result = await apiRequest<QueryPage<AdminUser>>({
        url: '/admin/users',
        method: 'GET',
        params: {
          page: toBackendPage(params),
          size: params.page_size,
          query: params.query ?? '',
        },
      });
      return normalizePage(result);
    },
    placeholderData: (previousData) => previousData,
  });

export const useAdminGroups = (params: AdminListParams) =>
  useQuery({
    queryKey: ['adminGroups', params],
    queryFn: () => unsupportedQuery('用户组'),
    placeholderData: (previousData) => previousData,
    retry: false,
  });

export const useAdminPolicies = (params: AdminListParams) =>
  useQuery({
    queryKey: ['adminPolicies', params],
    queryFn: async () => {
      const items = await apiRequest<AdminStoragePolicy[]>({
        url: '/admin/storage-policies',
        method: 'GET',
      });
      const offset = Math.max(0, params.page - 1) * params.page_size;
      const pagedItems = items.slice(offset, offset + params.page_size);
      return {
        items: pagedItems,
        pagination: {
          total_items: items.length,
          total_pages: Math.max(1, Math.ceil(items.length / Math.max(1, params.page_size))),
          page: params.page,
          page_size: params.page_size,
        },
      };
    },
    placeholderData: (previousData) => previousData,
  });

export const useAdminNodes = (params: AdminListParams) =>
  useQuery({
    queryKey: ['adminNodes', params],
    queryFn: () => unsupportedQuery('存储节点'),
    placeholderData: (previousData) => previousData,
    retry: false,
  });

export const useAdminOAuthApps = (params: AdminListParams) =>
  useQuery({
    queryKey: ['adminOAuthApps', params],
    queryFn: () => unsupportedQuery('OAuth 应用'),
    placeholderData: (previousData) => previousData,
    retry: false,
  });

export const useAdminFiles = (params: AdminListParams) =>
  useQuery({
    queryKey: ['adminFiles', params],
    queryFn: async () => {
      const result = await apiRequest<QueryPage<AdminFile>>({
        url: '/admin/files',
        method: 'GET',
        params: {
          page: toBackendPage(params),
          size: params.page_size,
          query: params.query ?? '',
          ownerQuery: params.ownerQuery ?? '',
        },
      });
      return normalizePage(result);
    },
    placeholderData: (previousData) => previousData,
  });

export const useAdminBlobs = (params: AdminListParams) =>
  useQuery({
    queryKey: ['adminBlobs', params],
    queryFn: async () => {
      const result = await apiRequest<QueryPage<AdminFileBlob>>({
        url: '/admin/file-blobs',
        method: 'GET',
        params: {
          page: toBackendPage(params),
          size: params.page_size,
          userQuery: params.userQuery ?? '',
          storagePolicyId: params.storagePolicyId,
          objectKey: params.objectKey ?? '',
          entityType: params.entityType,
        },
      });
      return normalizePage(result);
    },
    placeholderData: (previousData) => previousData,
  });

export const useAdminTasks = (params: AdminListParams) =>
  useQuery({
    queryKey: ['adminTasks', params],
    queryFn: async () => {
      const result = await apiRequest<QueryPage<AdminTask>>({
        url: '/admin/tasks',
        method: 'GET',
        params: {
          page: toBackendPage(params),
          size: params.page_size,
          userQuery: params.userQuery ?? '',
        },
      });
      return normalizePage(result);
    },
    placeholderData: (previousData) => previousData,
  });

export const useAdminAudits = (params: AdminListParams) =>
  useQuery({
    queryKey: ['adminAudits', params],
    queryFn: async () => {
      const result = await apiRequest<QueryPage<AdminAuditLog>>({
        url: '/admin/audits',
        method: 'GET',
        params: {
          page: toBackendPage(params),
          size: params.page_size,
          actorQuery: params.actorQuery ?? '',
          actionType: params.actionType ?? '',
          targetType: params.targetType ?? '',
          targetId: params.targetId,
        },
      });
      return normalizePage(result);
    },
    placeholderData: (previousData) => previousData,
  });

export const useAdminShares = (params: AdminListParams) =>
  useQuery({
    queryKey: ['adminShares', params],
    queryFn: async () => {
      const result = await apiRequest<QueryPage<AdminShare>>({
        url: '/admin/shares',
        method: 'GET',
        params: {
          page: toBackendPage(params),
          size: params.page_size,
          userQuery: params.userQuery ?? '',
          fileName: params.fileName ?? '',
          token: params.token ?? '',
          passwordProtected: params.passwordProtected,
          expired: params.expired,
        },
      });
      return normalizePage(result);
    },
    placeholderData: (previousData) => previousData,
  });

export const useAdminFilesystem = () =>
  useQuery({
    queryKey: ['adminFilesystem'],
    queryFn: () =>
      apiRequest<AdminFilesystem>({
        url: '/admin/filesystem',
        method: 'GET',
      }),
  });

export const useAdminConfigDefinitions = () =>
  useQuery({
    queryKey: ['adminConfigDefinitions'],
    queryFn: () =>
      apiRequest<AdminConfigDefinition[]>({
        url: '/admin/config/definitions',
        method: 'GET',
      }),
  });

export const useAdminConfigSnapshot = () =>
  useQuery({
    queryKey: ['adminConfigSnapshot'],
    queryFn: () =>
      apiRequest<AdminConfigSnapshot>({
        url: '/admin/config/snapshot',
        method: 'GET',
      }),
  });

export const useAdminConfigHistory = (key: string | null, page = 1, size = 10) =>
  useQuery({
    queryKey: ['adminConfigHistory', key, page, size],
    queryFn: async () => {
      const result = await apiRequest<QueryPage<AdminConfigHistory>>({
        url: `/admin/config/values/${encodeURIComponent(key as string)}/history`,
        method: 'GET',
        params: {
          page: toBackendPage({ page, page_size: size }),
          size,
        },
      });
      return normalizePage(result);
    },
    enabled: key != null,
  });

export const useAdminSettings = () =>
  useQuery({
    queryKey: ['adminSettings'],
    queryFn: () =>
      apiRequest<AdminSettings>({
        url: '/admin/settings',
        method: 'GET',
      }),
  });
