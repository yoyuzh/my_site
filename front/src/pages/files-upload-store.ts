import { useSyncExternalStore } from 'react';

import type { UploadTask } from './files-upload';

interface FilesUploadStoreSnapshot {
  uploads: UploadTask[];
  isUploadPanelOpen: boolean;
}

const listeners = new Set<() => void>();
const uploadTaskCancelers = new Map<string, () => void>();

let snapshot: FilesUploadStoreSnapshot = {
  uploads: [],
  isUploadPanelOpen: true,
};

function emitFilesUploadStoreChange() {
  for (const listener of listeners) {
    listener();
  }
}

function setFilesUploadStoreSnapshot(
  updater: (current: FilesUploadStoreSnapshot) => FilesUploadStoreSnapshot,
) {
  const nextSnapshot = updater(snapshot);
  if (nextSnapshot === snapshot) {
    return;
  }

  snapshot = nextSnapshot;
  emitFilesUploadStoreChange();
}

export function subscribeFilesUploadStore(listener: () => void) {
  listeners.add(listener);

  return () => {
    listeners.delete(listener);
  };
}

export function getFilesUploadStoreSnapshot() {
  return snapshot;
}

export function useFilesUploadStore() {
  return useSyncExternalStore(
    subscribeFilesUploadStore,
    getFilesUploadStoreSnapshot,
    getFilesUploadStoreSnapshot,
  );
}

export function replaceFilesUploads(uploads: UploadTask[]) {
  setFilesUploadStoreSnapshot((current) => ({
    ...current,
    uploads,
  }));
}

export function clearFilesUploads() {
  setFilesUploadStoreSnapshot((current) => {
    if (current.uploads.length === 0) {
      return current;
    }

    return {
      ...current,
      uploads: [],
    };
  });
}

export function updateFilesUploadTask(
  taskId: string,
  updateTask: (task: UploadTask) => UploadTask,
) {
  setFilesUploadStoreSnapshot((current) => ({
    ...current,
    uploads: current.uploads.map((task) => (task.id === taskId ? updateTask(task) : task)),
  }));
}

export function setFilesUploadPanelOpen(isOpen: boolean) {
  setFilesUploadStoreSnapshot((current) => {
    if (current.isUploadPanelOpen === isOpen) {
      return current;
    }

    return {
      ...current,
      isUploadPanelOpen: isOpen,
    };
  });
}

export function toggleFilesUploadPanelOpen() {
  setFilesUploadStoreSnapshot((current) => ({
    ...current,
    isUploadPanelOpen: !current.isUploadPanelOpen,
  }));
}

export function resetFilesUploadStoreForTests() {
  snapshot = {
    uploads: [],
    isUploadPanelOpen: true,
  };
  listeners.clear();
  uploadTaskCancelers.clear();
}

export function registerFilesUploadTaskCanceler(taskId: string, cancel: () => void) {
  uploadTaskCancelers.set(taskId, cancel);
}

export function unregisterFilesUploadTaskCanceler(taskId: string) {
  uploadTaskCancelers.delete(taskId);
}

export function cancelFilesUploadTask(taskId: string) {
  const cancel = uploadTaskCancelers.get(taskId);
  if (!cancel) {
    return false;
  }

  uploadTaskCancelers.delete(taskId);
  cancel();
  return true;
}
