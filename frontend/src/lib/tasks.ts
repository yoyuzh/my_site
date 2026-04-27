import { apiRequest } from '../api/client';
import type { BackgroundTask, QueryPage, TaskProgress } from '../api/types';

export async function getTasks(page = 0, size = 20) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return apiRequest<QueryPage<BackgroundTask>>({
    url: `/v2/tasks?${params.toString()}`,
    method: 'GET',
  });
}

export async function cancelTask(taskId: number) {
  return apiRequest<BackgroundTask>({
    url: `/v2/tasks/${taskId}`,
    method: 'DELETE',
  });
}

export async function retryTask(taskId: number) {
  return apiRequest<BackgroundTask>({
    url: `/v2/tasks/${taskId}/retry`,
    method: 'POST',
  });
}

export async function getTaskProgress(taskId: number) {
  return apiRequest<TaskProgress>({
    url: `/v2/tasks/${taskId}/progress`,
    method: 'GET',
  });
}

export async function rebuildSearchIndex() {
  return apiRequest<BackgroundTask>({
    url: '/v2/tasks/search-index/rebuild',
    method: 'POST',
  });
}

export function readTaskPublicState(publicStateJson: string | null | undefined): Record<string, unknown> | null {
  if (!publicStateJson) {
    return null;
  }

  try {
    return JSON.parse(publicStateJson) as Record<string, unknown>;
  } catch {
    return null;
  }
}

export function readTaskProgressSnapshot(publicStateJson: string | null | undefined): TaskProgress | null {
  const state = readTaskPublicState(publicStateJson);
  if (!state) {
    return null;
  }

  const processedItems = readCount(state.processedItems) ?? sumCounts(state.processedFileCount, state.processedDirectoryCount);
  const totalItems = readCount(state.totalItems) ?? sumCounts(state.totalFileCount, state.totalDirectoryCount);
  const explicitPercent = readCount(state.progressPercent);
  const progressPercent =
    explicitPercent == null
      ? totalItems > 0
        ? Math.min(100, Math.max(0, Math.floor((processedItems * 100) / totalItems)))
        : 0
      : Math.min(100, Math.max(0, explicitPercent));

  return {
    taskId: readCount(state.taskId) ?? 0,
    status: typeof state.phase === 'string' ? state.phase : '',
    progressPercent,
    processedItems,
    totalItems,
    message: typeof state.message === 'string' ? state.message : '',
  };
}

export function getTaskTypeLabel(type: string): string {
  const typeMap: Record<string, string> = {
    ARCHIVE: '打包归档',
    EXTRACT: '解压任务',
    SEARCH_INDEX_REBUILD: '重建搜索索引',
    STORAGE_POLICY_MIGRATION: '存储策略迁移',
    THUMBNAIL: '缩略图生成',
    MEDIA_META: '媒体元数据解析',
    REMOTE_DOWNLOAD: '离线下载',
    HLS_TRANSCODE: '视频转码',
    CLEANUP: '清理任务',
  };
  return typeMap[type] || type;
}

export function getTaskStatusLabel(status: string): string {
  const statusMap: Record<string, string> = {
    QUEUED: '排队中',
    PENDING: '排队中',
    RUNNING: '处理中',
    COMPLETED: '已完成',
    FAILED: '已失败',
    CANCELLED: '已取消',
    RETRYING: '重试中',
    WAITING: '等待中',
  };
  return statusMap[status] || status;
}

export function getRemoteDownloadPhaseLabel(phase: string) {
  const phaseMap: Record<string, string> = {
    queued: '排队中',
    pending: '待提交',
    running: '处理中',
    downloading: '下载中',
    'fetching-metadata': '获取种子信息中',
    importing: '导入网盘中',
    completed: '已完成',
    failed: '已失败',
    cancelled: '已取消',
  };
  return phaseMap[phase] || phase;
}

export function getRemoteDownloadSourceLabel(sourceType: string) {
  const sourceMap: Record<string, string> = {
    HTTP: 'HTTP/HTTPS',
    MAGNET: '磁力链接',
    TORRENT_FILE: '种子文件',
  };
  return sourceMap[sourceType] || sourceType;
}

export function getRemoteDownloadStatusLabel(status: string) {
  const statusMap: Record<string, string> = {
    PENDING: '待提交',
    SUBMITTED: '已提交',
    FETCHING_METADATA: '获取种子信息中',
    AWAITING_FILE_SELECTION: '等待选中文件',
    DOWNLOADING: '下载中',
    IMPORTING: '导入网盘中',
    COMPLETED: '已完成',
    FAILED: '已失败',
    CANCELED: '已取消',
  };
  return statusMap[status] || status;
}

function readCount(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string') {
    const parsed = Number.parseInt(value, 10);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }
  return null;
}

function sumCounts(first: unknown, second: unknown) {
  return (readCount(first) ?? 0) + (readCount(second) ?? 0);
}
