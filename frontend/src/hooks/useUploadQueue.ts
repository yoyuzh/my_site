import { useState, useEffect, useCallback } from 'react';
import { uploadFile } from '../lib/files';

export type UploadStatus = 'waiting' | 'uploading' | 'success' | 'cancelled' | 'error';

export interface UploadTask {
  id: string;
  file: File;
  path: string;
  status: UploadStatus;
  progress: number;
  error?: string;
  abortController?: AbortController;
}

type Listener = (tasks: UploadTask[]) => void;
let tasks: UploadTask[] = [];
let listeners: Listener[] = [];
let isProcessingQueue = false;

const notify = () => {
  listeners.forEach((l) => l([...tasks]));
};

const processQueue = async () => {
  if (isProcessingQueue) {
    return;
  }

  isProcessingQueue = true;

  try {
    while (true) {
      const taskIndex = tasks.findIndex((task) => task.status === 'waiting');
      if (taskIndex === -1) {
        break;
      }

      const nextTask = tasks[taskIndex];
      const controller = new AbortController();

      tasks[taskIndex] = {
        ...nextTask,
        status: 'uploading',
        abortController: controller,
      };
      notify();

      try {
        await uploadFile(nextTask.path, nextTask.file, controller.signal);

        const currentIndex = tasks.findIndex((task) => task.id === nextTask.id);
        if (currentIndex !== -1 && tasks[currentIndex].status === 'uploading') {
          tasks[currentIndex] = {
            ...tasks[currentIndex],
            status: 'success',
            progress: 100,
            abortController: undefined,
          };
          notify();

          window.dispatchEvent(new CustomEvent('upload-success', { detail: { path: nextTask.path } }));
        }
      } catch (error: unknown) {
        const currentIndex = tasks.findIndex((task) => task.id === nextTask.id);
        if (currentIndex === -1) {
          continue;
        }

        const isCancelledError =
          error instanceof DOMException && error.name === 'AbortError';
        const isAxiosCancelledError =
          error instanceof Error &&
          (error.name === 'CanceledError' || error.name === 'AbortError');

        if (isCancelledError || isAxiosCancelledError || tasks[currentIndex].status === 'cancelled') {
          tasks[currentIndex] = {
            ...tasks[currentIndex],
            status: 'cancelled',
            abortController: undefined,
          };
          notify();
          continue;
        }

        tasks[currentIndex] = {
          ...tasks[currentIndex],
          status: 'error',
          error: error instanceof Error ? error.message : '上传失败',
          abortController: undefined,
        };
        notify();
      }
    }
  } finally {
    isProcessingQueue = false;
  }
};

export const useUploadQueue = () => {
  const [currentTasks, setCurrentTasks] = useState<UploadTask[]>(tasks);

  useEffect(() => {
    listeners.push(setCurrentTasks);
    return () => {
      listeners = listeners.filter((l) => l !== setCurrentTasks);
    };
  }, []);

  const addTasks = useCallback((files: File[], path: string) => {
    const newTasks: UploadTask[] = files.map((file) => ({
      id: Math.random().toString(36).slice(2) + Date.now().toString(36),
      file,
      path,
      status: 'waiting',
      progress: 0,
    }));

    tasks = [...tasks, ...newTasks];
    notify();
    
    // If no task is currently uploading, start processing
    if (!tasks.some((t) => t.status === 'uploading')) {
      processQueue();
    }
  }, []);

  const cancelTask = useCallback((id: string) => {
    const taskIndex = tasks.findIndex((t) => t.id === id);
    if (taskIndex === -1) return;

    const task = tasks[taskIndex];
    if (task.status === 'uploading' && task.abortController) {
      task.abortController.abort();
    }

    tasks[taskIndex] = {
      ...task,
      status: 'cancelled',
      progress: 0,
      abortController: undefined,
    };
    notify();

    if (task.status === 'uploading') {
      void processQueue();
    }
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
          abortController: undefined,
        };
      }

      return task;
    });
    notify();
  }, []);

  return {
    tasks: currentTasks,
    addTasks,
    cancelTask,
    cancelAllTasks,
  };
};
