export type UploadTaskStatus = 'uploading' | 'completed' | 'error';

export interface UploadTask {
  id: string;
  fileName: string;
  progress: number;
  speed: string;
  destination: string;
  status: UploadTaskStatus;
  type: string;
  errorMessage?: string;
  noticeMessage?: string;
}

export interface UploadMeasurement {
  startedAt: number;
  lastLoaded: number;
  lastUpdatedAt: number;
}

function getUploadType(file: File) {
  const extension = file.name.includes('.') ? file.name.split('.').pop()?.toLowerCase() : '';

  if (file.type.startsWith('image/')) {
    return 'image';
  }
  if (file.type.includes('pdf') || extension === 'pdf') {
    return 'pdf';
  }
  if (extension === 'doc' || extension === 'docx') {
    return 'word';
  }
  if (extension === 'xls' || extension === 'xlsx' || extension === 'csv') {
    return 'excel';
  }

  return extension || 'document';
}

function createTaskId() {
  return globalThis.crypto?.randomUUID?.() ?? `upload-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function splitFileName(fileName: string) {
  const lastDotIndex = fileName.lastIndexOf('.');
  if (lastDotIndex <= 0) {
    return {
      stem: fileName,
      extension: '',
    };
  }

  return {
    stem: fileName.slice(0, lastDotIndex),
    extension: fileName.slice(lastDotIndex),
  };
}

export function getUploadDestination(pathParts: string[]) {
  return pathParts.length === 0 ? '/' : `/${pathParts.join('/')}`;
}

export function prepareUploadFile(file: File, usedNames: Set<string>) {
  if (!usedNames.has(file.name)) {
    return {
      file,
      noticeMessage: undefined,
    };
  }

  const {stem, extension} = splitFileName(file.name);
  let index = 1;
  let nextName = `${stem} (${index})${extension}`;

  while (usedNames.has(nextName)) {
    index += 1;
    nextName = `${stem} (${index})${extension}`;
  }

  return {
    file: new File([file], nextName, {
      type: file.type,
      lastModified: file.lastModified,
    }),
    noticeMessage: `检测到同名文件，已自动重命名为 ${nextName}`,
  };
}

export function createUploadTask(
  file: File,
  pathParts: string[],
  taskId: string = createTaskId(),
  noticeMessage?: string,
): UploadTask {
  return {
    id: taskId,
    fileName: file.name,
    progress: 0,
    speed: '等待上传...',
    destination: getUploadDestination(pathParts),
    status: 'uploading',
    type: getUploadType(file),
    noticeMessage,
  };
}

export function formatTransferSpeed(bytesPerSecond: number) {
  if (bytesPerSecond < 1024) {
    return `${Math.round(bytesPerSecond)} B/s`;
  }

  const units = ['KB/s', 'MB/s', 'GB/s'];
  let value = bytesPerSecond / 1024;
  let unitIndex = 0;

  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }

  return `${value.toFixed(1)} ${units[unitIndex]}`;
}

export function buildUploadProgressSnapshot({
  loaded,
  total,
  now,
  previous,
}: {
  loaded: number;
  total: number;
  now: number;
  previous?: UploadMeasurement;
}) {
  const safeTotal = total > 0 ? total : loaded;
  const rawProgress = safeTotal > 0 ? Math.round((loaded / safeTotal) * 100) : 0;
  const progress = Math.min(loaded >= safeTotal ? 99 : rawProgress, 99);

  const measurement: UploadMeasurement = previous
    ? {
        startedAt: previous.startedAt,
        lastLoaded: loaded,
        lastUpdatedAt: now,
      }
    : {
        startedAt: now,
        lastLoaded: loaded,
        lastUpdatedAt: now,
      };

  let bytesPerSecond = 0;

  if (previous) {
    const bytesDelta = Math.max(0, loaded - previous.lastLoaded);
    const timeDelta = Math.max(1, now - previous.lastUpdatedAt);
    bytesPerSecond = (bytesDelta * 1000) / timeDelta;
  } else if (loaded > 0) {
    bytesPerSecond = loaded;
  }

  return {
    progress,
    speed: formatTransferSpeed(bytesPerSecond),
    measurement,
  };
}

export function completeUploadTask(task: UploadTask): UploadTask {
  return {
    ...task,
    progress: 100,
    speed: '',
    status: 'completed',
    errorMessage: undefined,
  };
}

export function failUploadTask(task: UploadTask, errorMessage: string): UploadTask {
  return {
    ...task,
    speed: '',
    status: 'error',
    errorMessage,
  };
}
