import { useCallback, useEffect, useState } from 'react';
import { uploadFile } from '../lib/files';
import { getUserSettings } from '../lib/user-settings';

const DEFAULT_UPLOAD_CONCURRENCY = 2;
const MAX_UPLOAD_CONCURRENCY = 8;
const STALL_TIMEOUT_MS = 30_000;
const SPEED_IDLE_THRESHOLD_MS = 1_500;
const MONITOR_INTERVAL_MS = 1_000;

export type UploadStatus = 'waiting' | 'uploading' | 'success' | 'cancelled' | 'error';

export interface UploadTask {
  id: string;
  file: File;
  path: string;
  status: UploadStatus;
  progress: number;
  uploadedBytes: number;
  speedBytesPerSecond: number;
  error?: string;
  abortController?: AbortController;
  startedAt?: number;
  lastProgressAt?: number;
  lastByteChangeAt?: number;
}

type QueueSnapshot = {
  tasks: UploadTask[];
  uploadConcurrency: number;
};

type Listener = (snapshot: QueueSnapshot) => void;

let tasks: UploadTask[] = [];
let listeners: Listener[] = [];
let activeUploadCount = 0;
let uploadConcurrency = DEFAULT_UPLOAD_CONCURRENCY;
let monitorHandle: number | null = null;
let settingsHydrated = false;
let settingsLoadPromise: Promise<void> | null = null;

const cloneTasks = () => tasks.map((task) => ({ ...task }));

const emit = () => {
  syncMonitorState();
  const snapshot = {
    tasks: cloneTasks(),
    uploadConcurrency,
  };
  listeners.forEach((listener) => listener(snapshot));
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
  if (nextTask === currentTask) {
    return currentTask;
  }

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
  const hasUploadingTask = tasks.some((task) => task.status === 'uploading');
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
      task.abortController &&
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
      task.abortController.abort();
    }

    if (nextTask !== task) {
      changed = true;
    }
    return nextTask;
  });

  if (changed) {
    emit();
  }
}

function handleProgress(taskId: string, loadedBytes: number, totalBytes?: number) {
  const now = Date.now();
  const updatedTask = patchTask(taskId, (task) => {
    if (task.status !== 'uploading') {
      return task;
    }

    const total = totalBytes && totalBytes > 0 ? totalBytes : task.file.size;
    const safeLoadedBytes = Math.max(task.uploadedBytes, Math.min(total, loadedBytes));
    const previousProgressAt = task.lastProgressAt ?? task.startedAt ?? now;
    const deltaBytes = Math.max(0, safeLoadedBytes - task.uploadedBytes);
    const deltaTimeMs = Math.max(1, now - previousProgressAt);
    const speedBytesPerSecond = deltaBytes > 0
      ? (deltaBytes * 1000) / deltaTimeMs
      : task.speedBytesPerSecond;

    return {
      ...task,
      progress: total > 0 ? Math.min(100, Math.round((safeLoadedBytes / total) * 100)) : task.progress,
      uploadedBytes: safeLoadedBytes,
      speedBytesPerSecond,
      lastProgressAt: now,
      lastByteChangeAt: deltaBytes > 0 ? now : task.lastByteChangeAt ?? task.startedAt ?? now,
    };
  });

  if (updatedTask) {
    emit();
  }
}

async function startTask(taskId: string) {
  const taskIndex = findTaskIndex(taskId);
  if (taskIndex === -1 || tasks[taskIndex].status !== 'waiting') {
    return;
  }

  const now = Date.now();
  const controller = new AbortController();
  const originalTask = tasks[taskIndex];

  activeUploadCount += 1;
  tasks = [
    ...tasks.slice(0, taskIndex),
    {
      ...originalTask,
      status: 'uploading',
      progress: 0,
      uploadedBytes: 0,
      speedBytesPerSecond: 0,
      error: undefined,
      abortController: controller,
      startedAt: now,
      lastProgressAt: now,
      lastByteChangeAt: now,
    },
    ...tasks.slice(taskIndex + 1),
  ];
  emit();

  try {
    await uploadFile(originalTask.path, originalTask.file, controller.signal, ({ loaded, total }) => {
      handleProgress(taskId, loaded, total);
    });

    const completedTask = moveTaskToBottom(taskId, (task) => {
      if (task.status !== 'uploading') {
        return task;
      }
      return {
        ...task,
        status: 'success',
        progress: 100,
        uploadedBytes: task.file.size,
        speedBytesPerSecond: 0,
        abortController: undefined,
      };
    });

    if (completedTask) {
      emit();
      window.dispatchEvent(new CustomEvent('upload-success', { detail: { path: originalTask.path } }));
    }
  } catch (error: unknown) {
    const taskIndexAfterUpload = findTaskIndex(taskId);
    if (taskIndexAfterUpload !== -1) {
      const currentTask = tasks[taskIndexAfterUpload];
      const isAbortError =
        error instanceof DOMException && error.name === 'AbortError';
      const isAxiosCancelledError =
        error instanceof Error &&
        (error.name === 'CanceledError' || error.name === 'AbortError');

      if (currentTask.status === 'cancelled') {
        patchTask(taskId, (task) => ({
          ...task,
          speedBytesPerSecond: 0,
          abortController: undefined,
        }));
      } else if (currentTask.status === 'error') {
        patchTask(taskId, (task) => ({
          ...task,
          speedBytesPerSecond: 0,
          abortController: undefined,
        }));
      } else if (isAbortError || isAxiosCancelledError) {
        patchTask(taskId, (task) => ({
          ...task,
          status: 'cancelled',
          speedBytesPerSecond: 0,
          abortController: undefined,
        }));
      } else {
        patchTask(taskId, (task) => ({
          ...task,
          status: 'error',
          speedBytesPerSecond: 0,
          abortController: undefined,
          error: error instanceof Error ? error.message : '上传失败',
        }));
      }
      emit();
    }
  } finally {
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
    .catch(() => {
      // Keep the local default when the settings endpoint is unavailable.
    })
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

  const cancelTask = useCallback((id: string) => {
    const taskIndex = findTaskIndex(id);
    if (taskIndex === -1) {
      return;
    }

    const task = tasks[taskIndex];
    if (task.status === 'uploading' && task.abortController) {
      task.abortController.abort();
    }

    tasks = [
      ...tasks.slice(0, taskIndex),
      {
        ...task,
        status: 'cancelled',
        progress: 0,
        speedBytesPerSecond: 0,
        abortController: undefined,
      },
      ...tasks.slice(taskIndex + 1),
    ];
    emit();
  }, []);

  const cancelAllTasks = useCallback(() => {
    tasks = tasks.map((task) => {
      if (task.status === 'uploading' && task.abortController) {
        task.abortController.abort();
      }

      if (task.status === 'waiting' || task.status === 'uploading') {
        return {
          ...task,
          status: 'cancelled',
          progress: 0,
          speedBytesPerSecond: 0,
          abortController: undefined,
        };
      }

      return task;
    });
    emit();
  }, []);

  const setUploadConcurrency = useCallback((nextUploadConcurrency: number) => {
    applyUploadConcurrency(nextUploadConcurrency);
  }, []);

  return {
    tasks: snapshot.tasks,
    uploadConcurrency: snapshot.uploadConcurrency,
    addTasks,
    cancelTask,
    cancelAllTasks,
    setUploadConcurrency,
  };
};
