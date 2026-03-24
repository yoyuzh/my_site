import { getNextAvailableName } from './files-state';
import { resolveFileType, type FileTypeKind } from '@/src/lib/file-type';

export type UploadTaskStatus = 'uploading' | 'completed' | 'error';

export interface UploadTask {
  id: string;
  fileName: string;
  progress: number;
  speed: string;
  destination: string;
  status: UploadTaskStatus;
  type: FileTypeKind;
  typeLabel: string;
  errorMessage?: string;
  noticeMessage?: string;
}

export interface UploadMeasurement {
  startedAt: number;
  lastLoaded: number;
  lastUpdatedAt: number;
}

export interface PendingUploadEntry {
  file: File;
  pathParts: string[];
  source: 'file' | 'folder';
  noticeMessage?: string;
}

function getUploadType(file: File) {
  return resolveFileType({
    fileName: file.name,
    contentType: file.type,
  });
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

function getRelativePathSegments(file: File) {
  const rawRelativePath = ('webkitRelativePath' in file && typeof file.webkitRelativePath === 'string' && file.webkitRelativePath)
    ? file.webkitRelativePath
    : file.name;

  return rawRelativePath
    .replaceAll('\\', '/')
    .split('/')
    .map((segment) => segment.trim())
    .filter(Boolean);
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

export function prepareFolderUploadEntries(
  files: File[],
  currentPathParts: string[],
  existingRootNames: string[],
): PendingUploadEntry[] {
  const rootReservedNames = new Set(existingRootNames);
  const renamedRootFolders = new Map<string, string>();
  const usedNamesByDestination = new Map<string, Set<string>>();

  return files.map((file) => {
    const relativeSegments = getRelativePathSegments(file);
    if (relativeSegments.length === 0) {
      return {
        file,
        pathParts: [...currentPathParts],
        source: 'folder',
      };
    }

    let noticeMessage: string | undefined;
    if (relativeSegments.length > 1) {
      const originalRootFolder = relativeSegments[0];
      let renamedRootFolder = renamedRootFolders.get(originalRootFolder);
      if (!renamedRootFolder) {
        renamedRootFolder = getNextAvailableName(originalRootFolder, rootReservedNames);
        rootReservedNames.add(renamedRootFolder);
        renamedRootFolders.set(originalRootFolder, renamedRootFolder);
      }

      if (renamedRootFolder !== originalRootFolder) {
        relativeSegments[0] = renamedRootFolder;
        noticeMessage = `检测到同名文件夹，已自动重命名为 ${renamedRootFolder}`;
      }
    }

    const pathParts = [...currentPathParts, ...relativeSegments.slice(0, -1)];
    const destinationKey = getUploadDestination(pathParts);
    const usedNames = usedNamesByDestination.get(destinationKey) ?? new Set<string>();
    const preparedUpload = prepareUploadFile(
      new File([file], relativeSegments.at(-1) ?? file.name, {
        type: file.type,
        lastModified: file.lastModified,
      }),
      usedNames,
    );
    usedNames.add(preparedUpload.file.name);
    usedNamesByDestination.set(destinationKey, usedNames);

    return {
      file: preparedUpload.file,
      pathParts,
      source: 'folder',
      noticeMessage: noticeMessage ?? preparedUpload.noticeMessage,
    };
  });
}

export function shouldUploadEntriesSequentially(entries: PendingUploadEntry[]) {
  return entries.some((entry) => entry.source === 'folder');
}

export function createUploadTask(
  file: File,
  pathParts: string[],
  taskId: string = createTaskId(),
  noticeMessage?: string,
): UploadTask {
  const fileType = getUploadType(file);

  return {
    id: taskId,
    fileName: file.name,
    progress: 0,
    speed: '等待上传...',
    destination: getUploadDestination(pathParts),
    status: 'uploading',
    type: fileType.kind,
    typeLabel: fileType.label,
    noticeMessage,
  };
}

export function createUploadTasks(entries: PendingUploadEntry[]) {
  return entries.map((entry) =>
    createUploadTask(entry.file, entry.pathParts, undefined, entry.noticeMessage),
  );
}

export function createUploadMeasurement(startedAt: number): UploadMeasurement {
  return {
    startedAt,
    lastLoaded: 0,
    lastUpdatedAt: startedAt,
  };
}

export function prepareUploadTaskForCompletion(task: UploadTask): UploadTask {
  return {
    ...task,
    progress: Math.max(task.progress, 99),
    speed: task.speed && task.speed !== '等待上传...' ? task.speed : '即将完成...',
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
