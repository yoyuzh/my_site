import Uppy from '@uppy/core';
import AwsS3 from '@uppy/aws-s3';
import Tus from '@uppy/tus';
import { useCallback, useEffect, useState } from 'react';
import { buildApiHeaders, resolveApiUrl } from '../api/client';
import {
  cancelUploadSession,
  completeUploadSession,
  createUploadSession,
  prepareMultipartPartUpload,
  prepareUploadSession,
  recordMultipartPart,
  uploadUploadSessionContent,
} from '../lib/files';
import { getUserSettings } from '../lib/user-settings';
import type { UploadSessionResponse } from '../api/types';

const DEFAULT_UPLOAD_CONCURRENCY = 2;
const MAX_UPLOAD_CONCURRENCY = 8;
const STALL_TIMEOUT_MS = 30_000;
const SPEED_IDLE_THRESHOLD_MS = 1_500;
const MONITOR_INTERVAL_MS = 1_000;

export type UploadStatus = 'waiting' | 'preparing' | 'uploading' | 'success' | 'cancelled' | 'error';
type UploadTransport = 'multipart' | 'proxy' | 'single' | 'tus';

export interface UploadTask {
  id: string;
  file: File;
  path: string;
  status: UploadStatus;
  progress: number;
  uploadedBytes: number;
  speedBytesPerSecond: number;
  error?: string;
  sessionId?: string;
  transport?: UploadTransport;
  cancelUpload?: () => void;
  startedAt?: number;
  lastProgressAt?: number;
  lastByteChangeAt?: number;
}

export interface UploadTaskEntry {
  file: File;
  path: string;
}

type QueueSnapshot = {
  tasks: UploadTask[];
  uploadConcurrency: number;
};

type MultipartUploadPart = {
  PartNumber?: number | null;
  ETag?: string | null;
};

type MultipartUploadSummary = {
  parts: MultipartUploadPart[];
};

type UppyProgress = {
  bytesUploaded: number;
  bytesTotal?: number | null;
};

type Listener = (snapshot: QueueSnapshot) => void;

let tasks: UploadTask[] = [];
let listeners: Listener[] = [];
let activeUploadCount = 0;
let uploadConcurrency = DEFAULT_UPLOAD_CONCURRENCY;
let monitorHandle: number | null = null;
let settingsHydrated = false;
let settingsLoadPromise: Promise<void> | null = null;

const cloneTasks = () => tasks.map(({ cancelUpload, ...task }) => ({ ...task }));

const emit = () => {
  syncMonitorState();
  listeners.forEach((listener) => listener({
    tasks: cloneTasks(),
    uploadConcurrency,
  }));
};

function clampUploadConcurrency(value: number | null | undefined) {
  if (!Number.isFinite(value)) {
    return DEFAULT_UPLOAD_CONCURRENCY;
  }
  const rounded = Math.round(value as number);
  if (rounded < 1) {
    return 1;
  }
  if (rounded > MAX_UPLOAD_CONCURRENCY) {
    return MAX_UPLOAD_CONCURRENCY;
  }
  return rounded;
}

function findTaskIndex(taskId: string) {
  return tasks.findIndex((task) => task.id === taskId);
}

function patchTask(taskId: string, updater: (task: UploadTask) => UploadTask) {
  const taskIndex = findTaskIndex(taskId);
  if (taskIndex === -1) {
    return null;
  }
  const currentTask = tasks[taskIndex];
  const nextTask = updater(currentTask);
  const nextTasks = [...tasks];
  nextTasks[taskIndex] = nextTask;
  tasks = nextTasks;
  return nextTask;
}

function moveTaskToBottom(taskId: string, updater: (task: UploadTask) => UploadTask) {
  const taskIndex = findTaskIndex(taskId);
  if (taskIndex === -1) {
    return null;
  }
  const nextTask = updater(tasks[taskIndex]);
  const nextTasks = [...tasks];
  nextTasks.splice(taskIndex, 1);
  nextTasks.push(nextTask);
  tasks = nextTasks;
  return nextTask;
}

function syncMonitorState() {
  const hasUploadingTask = tasks.some((task) => task.status === 'uploading' || task.status === 'preparing');
  if (!hasUploadingTask) {
    if (monitorHandle != null && typeof window !== 'undefined') {
      window.clearInterval(monitorHandle);
    }
    monitorHandle = null;
    return;
  }

  if (monitorHandle == null && typeof window !== 'undefined') {
    monitorHandle = window.setInterval(runMonitorTick, MONITOR_INTERVAL_MS);
  }
}

function hasPendingUploadBytes(task: UploadTask) {
  return task.uploadedBytes < task.file.size;
}

function runMonitorTick() {
  const now = Date.now();
  let changed = false;
  const stalledCancellations: Array<() => void> = [];

  tasks = tasks.map((task) => {
    if (task.status !== 'uploading') {
      return task;
    }

    let nextTask = task;
    if (
      task.speedBytesPerSecond !== 0 &&
      task.lastProgressAt != null &&
      now - task.lastProgressAt >= SPEED_IDLE_THRESHOLD_MS
    ) {
      nextTask = {
        ...nextTask,
        speedBytesPerSecond: 0,
      };
    }

    if (
      task.cancelUpload &&
      hasPendingUploadBytes(task) &&
      task.lastByteChangeAt != null &&
      now - task.lastByteChangeAt >= STALL_TIMEOUT_MS
    ) {
      nextTask = {
        ...nextTask,
        status: 'error',
        speedBytesPerSecond: 0,
        error: '上传速度为 0 持续 30 秒，已自动终止',
      };
      if (task.cancelUpload) {
        stalledCancellations.push(task.cancelUpload);
      }
    }

    if (nextTask !== task) {
      changed = true;
    }
    return nextTask;
  });

  if (changed) {
    emit();
  }
  stalledCancellations.forEach((cancel) => cancel());
}

function handleProgress(taskId: string, loadedBytes: number, totalBytes?: number) {
  const now = Date.now();
  const updatedTask = patchTask(taskId, (task) => {
    if (task.status !== 'uploading' && task.status !== 'preparing') {
      return task;
    }

    const total = totalBytes && totalBytes > 0 ? totalBytes : task.file.size;
    const safeLoadedBytes = Math.max(task.uploadedBytes, Math.min(total, loadedBytes));
    const previousProgressAt = task.lastProgressAt ?? task.startedAt ?? now;
    const deltaBytes = Math.max(0, safeLoadedBytes - task.uploadedBytes);
    const deltaTimeMs = Math.max(1, now - previousProgressAt);

    return {
      ...task,
      status: 'uploading',
      progress: total > 0 ? Math.min(100, Math.round((safeLoadedBytes / total) * 100)) : task.progress,
      uploadedBytes: safeLoadedBytes,
      speedBytesPerSecond: deltaBytes > 0 ? (deltaBytes * 1000) / deltaTimeMs : task.speedBytesPerSecond,
      lastProgressAt: now,
      lastByteChangeAt: deltaBytes > 0 ? now : task.lastByteChangeAt ?? task.startedAt ?? now,
    };
  });

  if (updatedTask) {
    emit();
  }
}

function resolveUploadTransport(session: UploadSessionResponse): UploadTransport {
  if (session.strategy.tusUrl) {
    return 'tus';
  }
  if (session.uploadMode === 'PROXY' && session.strategy.proxyContentUrl && session.strategy.proxyFormField) {
    return 'proxy';
  }
  if (session.uploadMode === 'DIRECT_SINGLE' && session.strategy.prepareUrl) {
    return 'single';
  }
  if (session.uploadMode === 'DIRECT_MULTIPART') {
    return 'multipart';
  }
  throw new Error(`不支持的上传策略: ${session.uploadMode}`);
}

function resolvePartSize(session: UploadSessionResponse, fileSize: number, partNumber: number) {
  const zeroBasedPartIndex = Math.max(0, partNumber - 1);
  const chunkStart = zeroBasedPartIndex * session.chunkSize;
  return Math.min(session.chunkSize, Math.max(0, fileSize - chunkStart));
}

function createMultipartUppy(task: UploadTask, session: UploadSessionResponse) {
  const uppy = new Uppy({
    autoProceed: false,
    allowMultipleUploadBatches: false,
  });

  uppy.use(AwsS3, {
    shouldUseMultipart: true,
    limit: Math.max(1, Math.min(uploadConcurrency, 6)),
    retryDelays: [0, 1000, 3000],
    getChunkSize: () => Math.max(session.chunkSize, 5 * 1024 * 1024),
    async createMultipartUpload() {
      return {
        uploadId: session.sessionId,
        key: session.objectKey,
      };
    },
    async listParts() {
      return [];
    },
    async signPart(_file: unknown, { partNumber }: { partNumber: number }) {
      const prepared = await prepareMultipartPartUpload(session.sessionId, partNumber - 1);
      if (!prepared.direct || !prepared.uploadUrl) {
        throw new Error('分片上传策略未返回有效的上传地址');
      }
      if (prepared.method.toUpperCase() !== 'PUT') {
        throw new Error(`暂不支持 ${prepared.method} 方式的分片直传策略`);
      }

      return {
        method: 'PUT' as const,
        url: prepared.uploadUrl,
        headers: prepared.headers,
      };
    },
    async abortMultipartUpload() {
      await cancelUploadSession(session.sessionId).catch(() => undefined);
    },
    async completeMultipartUpload(_file: unknown, { parts }: MultipartUploadSummary) {
      for (const part of parts) {
        if (part.PartNumber == null || !part.ETag) {
          throw new Error('缺少已上传分片的确认信息');
        }
        await recordMultipartPart(
          session.sessionId,
          part.PartNumber - 1,
          part.ETag,
          resolvePartSize(session, task.file.size, part.PartNumber),
        );
      }
      await completeUploadSession(session.sessionId);
      return {
        location: session.objectKey,
      };
    },
  } as never);

  return uppy;
}

function createDirectSingleUppy(session: UploadSessionResponse) {
  if (!session.strategy.prepareUrl) {
    throw new Error('直接上传策略缺少 prepare endpoint');
  }

  const uppy = new Uppy({
    autoProceed: false,
    allowMultipleUploadBatches: false,
  });

  uppy.use(AwsS3, {
    shouldUseMultipart: false,
    retryDelays: [0, 1000, 3000],
    async getUploadParameters() {
      const prepared = await prepareUploadSession(session.strategy.prepareUrl!);
      if (!prepared.direct || !prepared.uploadUrl) {
        throw new Error('直接上传策略未返回有效的上传地址');
      }
      if (prepared.method.toUpperCase() !== 'PUT') {
        throw new Error(`暂不支持 ${prepared.method} 方式的直传策略`);
      }

      return {
        method: 'PUT' as const,
        url: resolveApiUrl(prepared.uploadUrl),
        headers: prepared.headers,
      };
    },
  });

  return uppy;
}

async function uploadProxySessionContent(taskId: string, task: UploadTask, session: UploadSessionResponse, signal: AbortSignal) {
  const proxyContentUrl = session.strategy.proxyContentUrl;
  const proxyFormField = session.strategy.proxyFormField;
  if (!proxyContentUrl || !proxyFormField) {
    throw new Error('代理上传策略缺少内容上传 endpoint');
  }

  await uploadUploadSessionContent(
    proxyContentUrl,
    proxyFormField,
    task.file,
    signal,
    (progress) => handleProgress(taskId, progress.loaded, progress.total),
  );
}

function createTusUppy(session: UploadSessionResponse) {
  if (!session.strategy.tusUrl) {
    throw new Error('Tus 上传策略缺少 endpoint');
  }

  const uppy = new Uppy({
    autoProceed: false,
    allowMultipleUploadBatches: false,
  });

  uppy.use(Tus, {
    endpoint: resolveApiUrl(session.strategy.tusUrl),
    headers: {
      ...buildApiHeaders(),
      ...(session.strategy.tusHeaders ?? {}),
    },
    chunkSize: session.chunkSize > 0 ? session.chunkSize : undefined,
    retryDelays: [0, 1000, 3000],
    allowedMetaFields: [],
  });

  return uppy;
}

async function startTask(taskId: string) {
  const taskIndex = findTaskIndex(taskId);
  if (taskIndex === -1 || tasks[taskIndex].status !== 'waiting') {
    return;
  }

  const now = Date.now();
  const originalTask = tasks[taskIndex];
  activeUploadCount += 1;

  tasks = [
    ...tasks.slice(0, taskIndex),
    {
      ...originalTask,
      status: 'preparing',
      progress: 0,
      uploadedBytes: 0,
      speedBytesPerSecond: 0,
      error: undefined,
      startedAt: now,
      lastProgressAt: now,
      lastByteChangeAt: now,
    },
    ...tasks.slice(taskIndex + 1),
  ];
  emit();

  let cancelledByUser = false;
  let uppy: Uppy | null = null;

  try {
    const session = await createUploadSession(originalTask.path, originalTask.file);
    const transport = resolveUploadTransport(session);
    if (transport === 'proxy') {
      const abortController = new AbortController();
      patchTask(taskId, (task) => ({
        ...task,
        status: 'uploading',
        sessionId: session.sessionId,
        transport,
        cancelUpload: () => {
          cancelledByUser = true;
          abortController.abort();
          void cancelUploadSession(session.sessionId).catch(() => undefined);
        },
      }));
      emit();

      await uploadProxySessionContent(taskId, originalTask, session, abortController.signal);
      await completeUploadSession(session.sessionId);

      const completedTask = moveTaskToBottom(taskId, (task) => ({
        ...task,
        status: 'success',
        progress: 100,
        uploadedBytes: task.file.size,
        speedBytesPerSecond: 0,
        cancelUpload: undefined,
      }));

      if (completedTask) {
        emit();
        window.dispatchEvent(new CustomEvent('upload-success', { detail: { path: originalTask.path, sessionId: session.sessionId } }));
      }
      return;
    }

    uppy = transport === 'multipart'
      ? createMultipartUppy(originalTask, session)
      : transport === 'single'
        ? createDirectSingleUppy(session)
        : createTusUppy(session);
    const currentUppy = uppy;

    const fileId = currentUppy.addFile({
      name: originalTask.file.name,
      type: originalTask.file.type || 'application/octet-stream',
      data: originalTask.file,
    });

    patchTask(taskId, (task) => ({
        ...task,
        sessionId: session.sessionId,
        transport,
        cancelUpload: () => {
          cancelledByUser = true;
          void cancelUploadSession(session.sessionId).catch(() => undefined);
          currentUppy.cancelAll();
        },
      }));
    emit();

    currentUppy.on('upload-progress', (_file: unknown, progress: UppyProgress) => {
      handleProgress(taskId, progress.bytesUploaded, progress.bytesTotal ?? undefined);
    });

    const result = await currentUppy.upload();
    if (!result) {
      throw new Error('上传未启动');
    }
    const failedUploads = result.failed ?? [];
    if (failedUploads.length > 0) {
      throw failedUploads[0]?.error ?? new Error('上传失败');
    }

    if (transport !== 'multipart') {
      await completeUploadSession(session.sessionId);
    }

    const completedTask = moveTaskToBottom(taskId, (task) => ({
      ...task,
      status: 'success',
      progress: 100,
      uploadedBytes: task.file.size,
      speedBytesPerSecond: 0,
      cancelUpload: undefined,
    }));

    if (completedTask) {
      emit();
      window.dispatchEvent(new CustomEvent('upload-success', { detail: { path: originalTask.path, sessionId: session.sessionId, fileId } }));
    }
  } catch (error: unknown) {
    const taskIndexAfterUpload = findTaskIndex(taskId);
    if (taskIndexAfterUpload !== -1) {
      const currentTask = tasks[taskIndexAfterUpload];
      if (currentTask.status === 'cancelled' || cancelledByUser) {
        patchTask(taskId, (task) => ({
          ...task,
          cancelUpload: undefined,
          speedBytesPerSecond: 0,
        }));
      } else {
        patchTask(taskId, (task) => ({
          ...task,
          status: 'error',
          cancelUpload: undefined,
          speedBytesPerSecond: 0,
          error: error instanceof Error ? error.message : '上传失败',
        }));
      }
      emit();
    }
  } finally {
    uppy?.destroy();
    activeUploadCount = Math.max(0, activeUploadCount - 1);
    syncMonitorState();
    pumpQueue();
  }
}

function pumpQueue() {
  while (activeUploadCount < uploadConcurrency) {
    const nextTask = tasks.find((task) => task.status === 'waiting');
    if (!nextTask) {
      return;
    }
    void startTask(nextTask.id);
  }
}

function applyUploadConcurrency(nextUploadConcurrency: number) {
  uploadConcurrency = clampUploadConcurrency(nextUploadConcurrency);
  emit();
  pumpQueue();
}

async function ensureSettingsHydrated() {
  if (settingsHydrated) {
    return;
  }
  if (settingsLoadPromise) {
    return settingsLoadPromise;
  }

  settingsLoadPromise = getUserSettings()
    .then((settings) => {
      applyUploadConcurrency(settings.uploadConcurrency ?? DEFAULT_UPLOAD_CONCURRENCY);
    })
    .catch(() => undefined)
    .finally(() => {
      settingsHydrated = true;
      settingsLoadPromise = null;
    });

  return settingsLoadPromise;
}

export const useUploadQueue = () => {
  const [snapshot, setSnapshot] = useState<QueueSnapshot>({
    tasks: cloneTasks(),
    uploadConcurrency,
  });

  useEffect(() => {
    const listener: Listener = (nextSnapshot) => setSnapshot(nextSnapshot);
    listeners.push(listener);
    void ensureSettingsHydrated();

    return () => {
      listeners = listeners.filter((item) => item !== listener);
    };
  }, []);

  const addTasks = useCallback((files: File[], path: string) => {
    const newTasks: UploadTask[] = files.map((file) => ({
      id: Math.random().toString(36).slice(2) + Date.now().toString(36),
      file,
      path,
      status: 'waiting',
      progress: 0,
      uploadedBytes: 0,
      speedBytesPerSecond: 0,
    }));

    tasks = [...tasks, ...newTasks];
    emit();
    pumpQueue();
  }, []);

  const addTaskEntries = useCallback((entries: UploadTaskEntry[]) => {
    const newTasks: UploadTask[] = entries.map(({ file, path }) => ({
      id: Math.random().toString(36).slice(2) + Date.now().toString(36),
      file,
      path,
      status: 'waiting',
      progress: 0,
      uploadedBytes: 0,
      speedBytesPerSecond: 0,
    }));

    tasks = [...tasks, ...newTasks];
    emit();
    pumpQueue();
  }, []);

  const cancelTask = useCallback((id: string) => {
    const taskIndex = findTaskIndex(id);
    if (taskIndex === -1) {
      return;
    }

    const task = tasks[taskIndex];
    const cancelUpload = task.cancelUpload;
    tasks = [
      ...tasks.slice(0, taskIndex),
      {
        ...task,
        status: 'cancelled',
        progress: 0,
        speedBytesPerSecond: 0,
        cancelUpload: undefined,
      },
      ...tasks.slice(taskIndex + 1),
    ];
    emit();
    cancelUpload?.();
  }, []);

  const cancelAllTasks = useCallback(() => {
    const cancellers = tasks
      .filter((task) => task.status === 'waiting' || task.status === 'preparing' || task.status === 'uploading')
      .map((task) => task.cancelUpload)
      .filter((cancelUpload): cancelUpload is NonNullable<UploadTask['cancelUpload']> => Boolean(cancelUpload));

    tasks = tasks.map((task) => {
      if (task.status === 'waiting' || task.status === 'preparing' || task.status === 'uploading') {
        return {
          ...task,
          status: 'cancelled',
          progress: 0,
          speedBytesPerSecond: 0,
          cancelUpload: undefined,
        };
      }
      return task;
    });
    emit();
    cancellers.forEach((cancelUpload) => cancelUpload());
  }, []);

  const setUploadConcurrency = useCallback((nextUploadConcurrency: number) => {
    applyUploadConcurrency(nextUploadConcurrency);
  }, []);

  return {
    tasks: snapshot.tasks,
    uploadConcurrency: snapshot.uploadConcurrency,
    addTasks,
    addTaskEntries,
    cancelTask,
    cancelAllTasks,
    setUploadConcurrency,
  };
};
