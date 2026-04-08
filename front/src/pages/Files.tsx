import React, { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  ChevronDown,
  Folder,
  Download,
  ChevronRight,
  FolderUp,
  Upload,
  Plus,
  LayoutGrid,
  List,
  MoreVertical,
  Copy,
  Share2,
  X,
  Edit2,
  Trash2,
  RotateCcw,
} from 'lucide-react';

import { NetdiskPathPickerModal } from '@/src/components/ui/NetdiskPathPickerModal';
import { Button } from '@/src/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/src/components/ui/card';
import { FileTypeIcon, getFileTypeTheme } from '@/src/components/ui/FileTypeIcon';
import { Input } from '@/src/components/ui/input';
import { ApiError, apiBinaryUploadRequest, apiDownload, apiRequest, apiUploadRequest } from '@/src/lib/api';
import { copyFileToNetdiskPath } from '@/src/lib/file-copy';
import { moveFileToNetdiskPath } from '@/src/lib/file-move';
import { resolveStoredFileType, type FileTypeKind } from '@/src/lib/file-type';
import { readCachedValue, removeCachedValue, writeCachedValue } from '@/src/lib/cache';
import {
  cancelBackgroundTask,
  createMediaMetadataTask,
  listBackgroundTasks,
  type BackgroundTask,
} from '@/src/lib/background-tasks';
import { createFileShareLink, getCurrentFileShareUrl } from '@/src/lib/file-share';
import { subscribeFileEvents } from '@/src/lib/file-events';
import { searchFiles } from '@/src/lib/file-search';
import { ellipsizeFileName } from '@/src/lib/file-name';
import { getFilesLastPathCacheKey, getFilesListCacheKey } from '@/src/lib/page-cache';
import type { DownloadUrlResponse, FileMetadata, InitiateUploadResponse, PageResponse } from '@/src/lib/types';
import { cn } from '@/src/lib/utils';

import {
  buildUploadProgressSnapshot,
  cancelUploadTask,
  createUploadMeasurement,
  createUploadTasks,
  completeUploadTask,
  failUploadTask,
  prepareUploadTaskForCompletion,
  prepareFolderUploadEntries,
  prepareUploadFile,
  shouldUploadEntriesSequentially,
  type PendingUploadEntry,
  type UploadMeasurement,
  type UploadTask,
} from './files-upload';
import {
  registerFilesUploadTaskCanceler,
  replaceFilesUploads,
  setFilesUploadPanelOpen,
  unregisterFilesUploadTaskCanceler,
  updateFilesUploadTask,
} from './files-upload-store';
import {
  clearSelectionIfDeleted,
  getNextAvailableName,
  getActionErrorMessage,
  removeUiFile,
  replaceUiFile,
  syncSelectedFile,
} from './files-state';
import {
  buildDirectoryTree,
  createExpandedDirectorySet,
  getMissingDirectoryListingPaths,
  hasLoadedDirectoryListing,
  mergeDirectoryChildren,
  toDirectoryPath,
  type DirectoryChildrenMap,
  type DirectoryTreeNode,
} from './files-tree';
import { getFilesSidebarFooterEntries, RECYCLE_BIN_RETENTION_DAYS, RECYCLE_BIN_ROUTE } from './recycle-bin-state';

function sleep(ms: number) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function toBackendPath(pathParts: string[]) {
  return toDirectoryPath(pathParts);
}

function splitBackendPath(path: string) {
  return path.split('/').filter(Boolean);
}

function DirectoryTreeItem({
  node,
  onSelect,
  onToggle,
}: {
  node: DirectoryTreeNode;
  onSelect: (path: string[]) => void;
  onToggle: (path: string[]) => void;
}) {
  return (
    <div>
      <div
        className={cn(
          'group flex items-center gap-1 rounded-xl px-2 py-1.5 transition-colors',
          node.active ? 'bg-[#336EFF]/15' : 'hover:bg-white/5',
        )}
        style={{ paddingLeft: `${node.depth * 14 + 8}px` }}
      >
        <button
          type="button"
          className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md text-slate-500 transition-colors hover:bg-white/5 hover:text-white"
          onClick={() => onToggle(node.path)}
          aria-label={`${node.expanded ? '收起' : '展开'} ${node.name}`}
        >
          {node.expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
        </button>
        <button
          type="button"
          className={cn(
            'flex min-w-0 flex-1 items-center gap-2 rounded-lg px-2 py-1 text-left text-sm transition-colors',
            node.active ? 'text-[#336EFF]' : 'text-slate-300 hover:text-white',
          )}
          onClick={() => onSelect(node.path)}
        >
          <Folder className={cn('h-4 w-4 shrink-0', node.active ? 'text-[#336EFF]' : 'text-slate-500')} />
          <span className="truncate">{node.name}</span>
        </button>
      </div>
      {node.expanded ? node.children.map((child) => (
        <DirectoryTreeItem key={child.id} node={child} onSelect={onSelect} onToggle={onToggle} />
      )) : null}
    </div>
  );
}

function formatFileSize(size: number) {
  if (size <= 0) {
    return '—';
  }

  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1);
  const value = size / 1024 ** index;
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

function toUiFile(file: FileMetadata) {
  const resolvedType = resolveStoredFileType({
    filename: file.filename,
    contentType: file.contentType,
    directory: file.directory,
  });

  return {
    id: file.id,
    name: file.filename,
    type: resolvedType.kind,
    typeLabel: resolvedType.label,
    size: file.directory ? '—' : formatFileSize(file.size),
    modified: formatDateTime(file.createdAt),
  };
}

interface UiFile {
  id: FileMetadata['id'];
  modified: string;
  name: string;
  size: string;
  type: FileTypeKind;
  typeLabel: string;
}

type NetdiskTargetAction = 'move' | 'copy';

export default function Files() {
  const navigate = useNavigate();
  const location = useLocation();
  const initialPath = readCachedValue<string[]>(getFilesLastPathCacheKey()) ?? [];
  const initialCachedFiles = readCachedValue<FileMetadata[]>(getFilesListCacheKey(toBackendPath(initialPath))) ?? [];
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const directoryInputRef = useRef<HTMLInputElement | null>(null);
  const uploadMeasurementsRef = useRef(new Map<string, UploadMeasurement>());
  const [currentPath, setCurrentPath] = useState<string[]>(initialPath);
  const currentPathRef = useRef(currentPath);
  const [directoryChildren, setDirectoryChildren] = useState<DirectoryChildrenMap>(() => {
    if (initialCachedFiles.length === 0) {
      return {};
    }

    return mergeDirectoryChildren(
      {},
      toBackendPath(initialPath),
      initialCachedFiles.filter((file) => file.directory).map((file) => file.filename),
    );
  });
  const [loadedDirectoryPaths, setLoadedDirectoryPaths] = useState<Set<string>>(
    () => new Set(initialCachedFiles.length === 0 ? [] : [toBackendPath(initialPath)]),
  );
  const [expandedDirectories, setExpandedDirectories] = useState(() => createExpandedDirectorySet(initialPath));
  const [selectedFile, setSelectedFile] = useState<UiFile | null>(null);
  const [currentFiles, setCurrentFiles] = useState<UiFile[]>(initialCachedFiles.map(toUiFile));
  const [renameModalOpen, setRenameModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [fileToRename, setFileToRename] = useState<UiFile | null>(null);
  const [fileToDelete, setFileToDelete] = useState<UiFile | null>(null);
  const [targetActionFile, setTargetActionFile] = useState<UiFile | null>(null);
  const [targetAction, setTargetAction] = useState<NetdiskTargetAction | null>(null);
  const [newFileName, setNewFileName] = useState('');
  const [activeDropdown, setActiveDropdown] = useState<number | null>(null);
  const [viewMode, setViewMode] = useState<'list' | 'grid'>('list');
  const [renameError, setRenameError] = useState('');
  const [isRenaming, setIsRenaming] = useState(false);
  const [shareStatus, setShareStatus] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchAppliedQuery, setSearchAppliedQuery] = useState('');
  const [searchResults, setSearchResults] = useState<FileMetadata[] | null>(null);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [selectedSearchFile, setSelectedSearchFile] = useState<FileMetadata | null>(null);
  const searchRequestIdRef = useRef(0);
  const [backgroundTasks, setBackgroundTasks] = useState<BackgroundTask[]>([]);
  const [backgroundTasksLoading, setBackgroundTasksLoading] = useState(false);
  const [backgroundTasksError, setBackgroundTasksError] = useState('');
  const [backgroundTaskNotice, setBackgroundTaskNotice] = useState<{ kind: 'success' | 'error'; message: string } | null>(null);
  const [backgroundTaskActionId, setBackgroundTaskActionId] = useState<number | null>(null);

  const recordDirectoryChildren = (pathParts: string[], items: FileMetadata[]) => {
    setDirectoryChildren((previous) => {
      let next = mergeDirectoryChildren(
        previous,
        toBackendPath(pathParts),
        items.filter((file) => file.directory).map((file) => file.filename),
      );

      for (let index = 0; index < pathParts.length; index += 1) {
        next = mergeDirectoryChildren(
          next,
          toBackendPath(pathParts.slice(0, index)),
          [pathParts[index]],
        );
      }

      return next;
    });
  };

  const markDirectoryLoaded = (pathParts: string[]) => {
    const path = toBackendPath(pathParts);
    setLoadedDirectoryPaths((previous) => {
      if (previous.has(path)) {
        return previous;
      }

      const next = new Set(previous);
      next.add(path);
      return next;
    });
  };

  const loadCurrentPath = async (pathParts: string[]) => {
    const response = await apiRequest<PageResponse<FileMetadata>>(
      `/files/list?path=${encodeURIComponent(toBackendPath(pathParts))}&page=0&size=100`
    );
    writeCachedValue(getFilesListCacheKey(toBackendPath(pathParts)), response.items);
    writeCachedValue(getFilesLastPathCacheKey(), pathParts);
    recordDirectoryChildren(pathParts, response.items);
    markDirectoryLoaded(pathParts);
    setCurrentFiles(response.items.map(toUiFile));
  };

  useEffect(() => {
    currentPathRef.current = currentPath;
    setExpandedDirectories((previous) => {
      const next = new Set(previous);
      for (const path of createExpandedDirectorySet(currentPath)) {
        next.add(path);
      }
      return next;
    });
    const cachedFiles = readCachedValue<FileMetadata[]>(getFilesListCacheKey(toBackendPath(currentPath)));
    writeCachedValue(getFilesLastPathCacheKey(), currentPath);

    if (cachedFiles) {
      recordDirectoryChildren(currentPath, cachedFiles);
      setCurrentFiles(cachedFiles.map(toUiFile));
    }

    loadCurrentPath(currentPath).catch(() => {
      if (!cachedFiles) {
        setCurrentFiles([]);
      }
    });
  }, [currentPath]);

  useEffect(() => {
    const missingAncestors = getMissingDirectoryListingPaths(currentPath, loadedDirectoryPaths);

    if (missingAncestors.length === 0) {
      return;
    }

    let cancelled = false;

    Promise.all(
      missingAncestors.map(async (pathParts) => {
        const path = toBackendPath(pathParts);
        const response = await apiRequest<PageResponse<FileMetadata>>(
          `/files/list?path=${encodeURIComponent(path)}&page=0&size=100`
        );
        writeCachedValue(getFilesListCacheKey(path), response.items);
        return { pathParts, items: response.items };
      }),
    )
      .then((responses) => {
        if (cancelled) {
          return;
        }

        for (const response of responses) {
          recordDirectoryChildren(response.pathParts, response.items);
          markDirectoryLoaded(response.pathParts);
        }
      })
      .catch(() => {
        // The main content area already loaded the current directory; keep the tree best-effort.
      });

    return () => {
      cancelled = true;
    };
  }, [currentPath, loadedDirectoryPaths]);

  useEffect(() => {
    if (!directoryInputRef.current) {
      return;
    }

    directoryInputRef.current.setAttribute('webkitdirectory', '');
    directoryInputRef.current.setAttribute('directory', '');
  }, []);

  useEffect(() => {
    const subscription = subscribeFileEvents({
      path: toBackendPath(currentPath),
      onFileEvent: () => {
        const activePath = currentPathRef.current;
        removeCachedValue(getFilesListCacheKey(toBackendPath(activePath)));
        loadCurrentPath(activePath).catch(() => undefined);
      },
      onError: () => undefined,
    });

    return () => {
      subscription.close();
    };
  }, [currentPath]);

  useEffect(() => {
    void loadBackgroundTasks();
  }, []);

  const clearSearchState = () => {
    searchRequestIdRef.current += 1;
    setSearchQuery('');
    setSearchAppliedQuery('');
    setSearchResults(null);
    setSearchLoading(false);
    setSearchError('');
    setSelectedSearchFile(null);
  };

  const handleNavigateToPath = (pathParts: string[]) => {
    clearSearchState();
    setCurrentPath(pathParts);
    setSelectedFile(null);
    setActiveDropdown(null);
  };

  const handleSidebarClick = (pathParts: string[]) => {
    handleNavigateToPath(pathParts);
  };

  const handleDirectoryToggle = async (pathParts: string[]) => {
    const path = toBackendPath(pathParts);
    let shouldLoadChildren = false;

    setExpandedDirectories((previous) => {
      const next = new Set(previous);
      if (next.has(path)) {
        next.delete(path);
        return next;
      }

      next.add(path);
      shouldLoadChildren = !hasLoadedDirectoryListing(pathParts, loadedDirectoryPaths);
      return next;
    });

    if (!shouldLoadChildren) {
      return;
    }

    try {
      const response = await apiRequest<PageResponse<FileMetadata>>(
        `/files/list?path=${encodeURIComponent(path)}&page=0&size=100`
      );
      writeCachedValue(getFilesListCacheKey(path), response.items);
      recordDirectoryChildren(pathParts, response.items);
      markDirectoryLoaded(pathParts);
    } catch {
      // Keep the branch expanded even if lazy loading fails; the main content area remains the source of truth.
    }
  };

  const handleFolderDoubleClick = (file: UiFile) => {
    if (file.type === 'folder') {
      handleNavigateToPath([...currentPath, file.name]);
    }
  };

  const handleBreadcrumbClick = (index: number) => {
    handleNavigateToPath(currentPath.slice(0, index + 1));
  };

  const handleSearchSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const nextQuery = searchQuery.trim();
    if (!nextQuery) {
      clearSearchState();
      return;
    }

    const requestId = searchRequestIdRef.current + 1;
    searchRequestIdRef.current = requestId;
    setSearchAppliedQuery(nextQuery);
    setSearchLoading(true);
    setSearchError('');
    setSearchResults(null);
    setSelectedSearchFile(null);
    setSelectedFile(null);
    setActiveDropdown(null);

    try {
      const response = await searchFiles({
        name: nextQuery,
        type: 'all',
        page: 0,
        size: 100,
      });

      if (searchRequestIdRef.current !== requestId) {
        return;
      }

      setSearchResults(response.items);
    } catch (error) {
      if (searchRequestIdRef.current !== requestId) {
        return;
      }

      setSearchResults([]);
      setSearchError(error instanceof Error ? error.message : '搜索失败');
    } finally {
      if (searchRequestIdRef.current === requestId) {
        setSearchLoading(false);
      }
    }
  };

  const loadBackgroundTasks = async () => {
    setBackgroundTasksLoading(true);
    setBackgroundTasksError('');

    try {
      const response = await listBackgroundTasks({ page: 0, size: 10 });
      setBackgroundTasks(response.items);
    } catch (error) {
      setBackgroundTasksError(error instanceof Error ? error.message : '获取后台任务失败');
    } finally {
      setBackgroundTasksLoading(false);
    }
  };

  const handleCreateMediaMetadataTask = async () => {
    if (!selectedFile || selectedFile.type === 'folder') {
      return;
    }

    const taskPath = currentPath.length === 0 ? `/${selectedFile.name}` : `${toBackendPath(currentPath)}/${selectedFile.name}`;
    const correlationId = `media-meta:${selectedFile.id}:${Date.now()}`;

    setBackgroundTaskNotice(null);
    setBackgroundTaskActionId(selectedFile.id);

    try {
      await createMediaMetadataTask({
        fileId: selectedFile.id,
        path: taskPath,
        correlationId,
      });
      setBackgroundTaskNotice({
        kind: 'success',
        message: '已创建媒体信息提取任务，可在右侧后台任务面板查看状态。',
      });
      await loadBackgroundTasks();
    } catch (error) {
      setBackgroundTaskNotice({
        kind: 'error',
        message: error instanceof Error ? error.message : '创建媒体信息提取任务失败',
      });
    } finally {
      setBackgroundTaskActionId(null);
    }
  };

  const handleCancelBackgroundTask = async (taskId: number) => {
    setBackgroundTaskNotice(null);
    setBackgroundTaskActionId(taskId);

    try {
      await cancelBackgroundTask(taskId);
      setBackgroundTaskNotice({
        kind: 'success',
        message: `已取消任务 ${taskId}，后台列表已刷新。`,
      });
      await loadBackgroundTasks();
    } catch (error) {
      setBackgroundTaskNotice({
        kind: 'error',
        message: error instanceof Error ? error.message : '取消任务失败',
      });
    } finally {
      setBackgroundTaskActionId(null);
    }
  };

  const openRenameModal = (file: UiFile) => {
    setFileToRename(file);
    setNewFileName(file.name);
    setRenameError('');
    setRenameModalOpen(true);
  };

  const openDeleteModal = (file: UiFile) => {
    setFileToDelete(file);
    setDeleteModalOpen(true);
  };

  const openTargetActionModal = (file: UiFile, action: NetdiskTargetAction) => {
    setTargetAction(action);
    setTargetActionFile(file);
    setActiveDropdown(null);
  };

  const handleUploadClick = () => {
    fileInputRef.current?.click();
  };

  const handleUploadFolderClick = () => {
    directoryInputRef.current?.click();
  };

  const runUploadEntries = async (entries: PendingUploadEntry[]) => {
    if (entries.length === 0) {
      return;
    }

    setFilesUploadPanelOpen(true);
    uploadMeasurementsRef.current.clear();

    const batchTasks = createUploadTasks(entries);
    replaceFilesUploads(batchTasks);

    const runSingleUpload = async (
      {file: uploadFile, pathParts: uploadPathParts}: PendingUploadEntry,
      uploadTask: UploadTask,
    ) => {
      const uploadPath = toBackendPath(uploadPathParts);
      const startedAt = Date.now();
      const uploadAbortController = new AbortController();
      registerFilesUploadTaskCanceler(uploadTask.id, () => {
        uploadAbortController.abort();
      });
      uploadMeasurementsRef.current.set(uploadTask.id, createUploadMeasurement(startedAt));

      try {
        const updateProgress = ({loaded, total}: {loaded: number; total: number}) => {
          const snapshot = buildUploadProgressSnapshot({
            loaded,
            total,
            now: Date.now(),
            previous: uploadMeasurementsRef.current.get(uploadTask.id),
          });

          uploadMeasurementsRef.current.set(uploadTask.id, snapshot.measurement);
          updateFilesUploadTask(uploadTask.id, (task) => ({
            ...task,
            progress: snapshot.progress,
            speed: snapshot.speed,
          }));
        };

        let initiated: InitiateUploadResponse | null = null;
        try {
          initiated = await apiRequest<InitiateUploadResponse>('/files/upload/initiate', {
              method: 'POST',
              body: {
                path: uploadPath,
                filename: uploadFile.name,
                contentType: uploadFile.type || null,
                size: uploadFile.size,
              },
            });
        } catch (error) {
          if (!(error instanceof ApiError && error.status === 404)) {
            throw error;
          }
        }

        let uploadedFile: FileMetadata;
        if (initiated?.direct) {
          try {
            await apiBinaryUploadRequest(initiated.uploadUrl, {
              method: initiated.method,
              headers: initiated.headers,
              body: uploadFile,
              onProgress: updateProgress,
              signal: uploadAbortController.signal,
            });

            uploadedFile = await apiRequest<FileMetadata>('/files/upload/complete', {
              method: 'POST',
              signal: uploadAbortController.signal,
              body: {
                path: uploadPath,
                filename: uploadFile.name,
                storageName: initiated.storageName,
                contentType: uploadFile.type || null,
                size: uploadFile.size,
              },
            });
          } catch (error) {
            if (!(error instanceof ApiError && error.isNetworkError)) {
              throw error;
            }

            const formData = new FormData();
            formData.append('file', uploadFile);
            uploadedFile = await apiUploadRequest<FileMetadata>(`/files/upload?path=${encodeURIComponent(uploadPath)}`, {
              body: formData,
              onProgress: updateProgress,
              signal: uploadAbortController.signal,
            });
          }
        } else if (initiated) {
          const formData = new FormData();
          formData.append('file', uploadFile);
          uploadedFile = await apiUploadRequest<FileMetadata>(initiated.uploadUrl, {
            body: formData,
            method: initiated.method,
            headers: initiated.headers,
            onProgress: updateProgress,
            signal: uploadAbortController.signal,
          });
        } else {
          const formData = new FormData();
          formData.append('file', uploadFile);
          uploadedFile = await apiUploadRequest<FileMetadata>(`/files/upload?path=${encodeURIComponent(uploadPath)}`, {
            body: formData,
            onProgress: updateProgress,
            signal: uploadAbortController.signal,
          });
        }

        updateFilesUploadTask(uploadTask.id, (task) => prepareUploadTaskForCompletion(task));
        await sleep(120);
        updateFilesUploadTask(uploadTask.id, (task) => completeUploadTask(task));
        return uploadedFile;
      } catch (error) {
        if (uploadAbortController.signal.aborted) {
          updateFilesUploadTask(uploadTask.id, (task) => cancelUploadTask(task));
          return null;
        }

        const message = error instanceof Error && error.message ? error.message : '上传失败，请稍后重试';
        updateFilesUploadTask(uploadTask.id, (task) => failUploadTask(task, message));
        return null;
      } finally {
        uploadMeasurementsRef.current.delete(uploadTask.id);
        unregisterFilesUploadTaskCanceler(uploadTask.id);
      }
    };

    const results = shouldUploadEntriesSequentially(entries)
      ? await entries.reduce<Promise<Array<FileMetadata | null>>>(
          async (previousPromise, entry, index) => {
            const previous = await previousPromise;
            const current = await runSingleUpload(entry, batchTasks[index]);
            return [...previous, current];
          },
          Promise.resolve([]),
        )
      : await Promise.all(entries.map((entry, index) => runSingleUpload(entry, batchTasks[index])));

    if (results.some(Boolean)) {
      await loadCurrentPath(currentPathRef.current).catch(() => undefined);
    }
  };

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files ? (Array.from(event.target.files) as File[]) : [];
    event.target.value = '';

    if (files.length === 0) {
      return;
    }

    const reservedNames = new Set<string>(currentFiles.map((file) => file.name));
    const entries: PendingUploadEntry[] = files.map((file) => {
      const preparedUpload = prepareUploadFile(file, reservedNames);
      reservedNames.add(preparedUpload.file.name);
      return {
        file: preparedUpload.file,
        pathParts: [...currentPath],
        source: 'file' as const,
        noticeMessage: preparedUpload.noticeMessage,
      };
    });

    await runUploadEntries(entries);
  };

  const handleFolderChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files ? (Array.from(event.target.files) as File[]) : [];
    event.target.value = '';

    if (files.length === 0) {
      return;
    }

    const entries = prepareFolderUploadEntries(
      files,
      [...currentPath],
      currentFiles.map((file) => file.name),
    );

    await runUploadEntries(entries);
  };

  const handleCreateFolder = async () => {
    const folderName = window.prompt('请输入新文件夹名称');
    if (!folderName?.trim()) {
      return;
    }

    const normalizedFolderName = folderName.trim();
    const nextFolderName = getNextAvailableName(
      normalizedFolderName,
      new Set(currentFiles.filter((file) => file.type === 'folder').map((file) => file.name)),
    );
    if (nextFolderName !== normalizedFolderName) {
      window.alert(`检测到同名文件夹，已自动重命名为 ${nextFolderName}`);
    }

    const basePath = toBackendPath(currentPath).replace(/\/$/, '');
    const fullPath = `${basePath}/${nextFolderName}` || '/';

    await apiRequest('/files/mkdir', {
      method: 'POST',
      body: new URLSearchParams({
        path: fullPath.startsWith('/') ? fullPath : `/${fullPath}`,
      }),
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
      },
    });

    await loadCurrentPath(currentPath);
  };

  const handleRename = async () => {
    if (!fileToRename || !newFileName.trim() || isRenaming) {
      return;
    }
    setIsRenaming(true);
    setRenameError('');

    try {
      const renamedFile = await apiRequest<FileMetadata>(`/files/${fileToRename.id}/rename`, {
        method: 'PATCH',
        body: {
          filename: newFileName.trim(),
        },
      });

      const nextUiFile = toUiFile(renamedFile);
      setCurrentFiles((previous) => replaceUiFile(previous, nextUiFile));
      setSelectedFile((previous) => syncSelectedFile(previous, nextUiFile));
      setRenameModalOpen(false);
      setFileToRename(null);
      setNewFileName('');
      await loadCurrentPath(currentPath).catch(() => undefined);
    } catch (error) {
      setRenameError(getActionErrorMessage(error, '重命名失败，请稍后重试'));
    } finally {
      setIsRenaming(false);
    }
  };

  const handleDelete = async () => {
    if (!fileToDelete) {
      return;
    }

    await apiRequest(`/files/${fileToDelete.id}`, {
      method: 'DELETE',
    });

    setCurrentFiles((previous) => removeUiFile(previous, fileToDelete.id));
    setSelectedFile((previous) => clearSelectionIfDeleted(previous, fileToDelete.id));
    setDeleteModalOpen(false);
    setFileToDelete(null);
    await loadCurrentPath(currentPath).catch(() => undefined);
  };

  const handleMoveToPath = async (path: string) => {
    if (!targetActionFile || !targetAction) {
      return;
    }

    if (targetAction === 'move') {
      await moveFileToNetdiskPath(targetActionFile.id, path);
      setSelectedFile((previous) => clearSelectionIfDeleted(previous, targetActionFile.id));
    } else {
      await copyFileToNetdiskPath(targetActionFile.id, path);
    }

    setTargetAction(null);
    setTargetActionFile(null);
    await loadCurrentPath(currentPath).catch(() => undefined);
  };

  const handleDownload = async (targetFile: UiFile | null = selectedFile) => {
    if (!targetFile) {
      return;
    }

    if (targetFile.type === 'folder') {
      const response = await apiDownload(`/files/download/${targetFile.id}`);
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${targetFile.name}.zip`;
      link.click();
      window.URL.revokeObjectURL(url);
      return;
    }

    try {
      const response = await apiRequest<DownloadUrlResponse>(`/files/download/${targetFile.id}/url`);
      const url = response.url;
      const link = document.createElement('a');
      link.href = url;
      link.download = targetFile.name;
      link.rel = 'noreferrer';
      link.target = '_blank';
      link.click();
      return;
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 404)) {
        throw error;
      }
    }

    const response = await apiDownload(`/files/download/${targetFile.id}`);
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = targetFile.name;
    link.click();
    window.URL.revokeObjectURL(url);
  };

  const handleShare = async (targetFile: UiFile) => {
    try {
      const response = await createFileShareLink(targetFile.id);
      const shareUrl = getCurrentFileShareUrl(response.token);
      try {
        await navigator.clipboard.writeText(shareUrl);
        setShareStatus('分享链接已复制到剪贴板');
      } catch {
        setShareStatus(`分享链接：${shareUrl}`);
      }
    } catch (error) {
      setShareStatus(error instanceof Error ? error.message : '创建分享链接失败');
    }
  };

  const directoryTree = buildDirectoryTree(directoryChildren, currentPath, expandedDirectories);
  const isSearchActive = searchAppliedQuery.trim().length > 0;

  return (
    <div className="flex flex-col lg:flex-row gap-6 h-[calc(100vh-8rem)]">
      {/* Left Sidebar */}
      <Card className="w-full lg:w-64 shrink-0 flex flex-col h-full overflow-hidden">
        <CardContent className="flex h-full flex-col p-4">
          <div className="min-h-0 flex-1 space-y-2">
            <p className="px-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">网盘目录</p>
            <div className="flex min-h-0 flex-1 flex-col rounded-2xl border border-white/5 bg-black/20 p-2">
              <button
                type="button"
                onClick={() => handleSidebarClick([])}
                className={cn(
                  'flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-sm font-medium transition-colors',
                  currentPath.length === 0 ? 'bg-[#336EFF]/15 text-[#336EFF]' : 'text-slate-200 hover:bg-white/5 hover:text-white',
                )}
              >
                <Folder className={cn('h-4 w-4', currentPath.length === 0 ? 'text-[#336EFF]' : 'text-slate-500')} />
                <span className="truncate">网盘</span>
              </button>
              <div className="mt-1 min-h-0 flex-1 space-y-0.5 overflow-y-auto pr-1">
                {directoryTree.map((node) => (
                  <DirectoryTreeItem
                    key={node.id}
                    node={node}
                    onSelect={handleSidebarClick}
                    onToggle={(path) => void handleDirectoryToggle(path)}
                  />
                ))}
              </div>
            </div>
          </div>
          <div className="mt-4 border-t border-white/10 pt-4">
            {getFilesSidebarFooterEntries().map((entry) => {
              const isActive = location.pathname === entry.path || location.pathname === RECYCLE_BIN_ROUTE;
              return (
                <button
                  key={entry.path}
                  type="button"
                  onClick={() => navigate(entry.path)}
                  className={cn(
                    'flex w-full items-center gap-3 rounded-2xl border px-3 py-3 text-left text-sm transition-colors',
                    isActive
                      ? 'border-[#336EFF]/30 bg-[#336EFF]/15 text-[#7ea6ff]'
                      : 'border-white/10 bg-white/5 text-slate-300 hover:bg-white/10 hover:text-white',
                  )}
                >
                  <RotateCcw className={cn('h-4 w-4', isActive ? 'text-[#7ea6ff]' : 'text-slate-400')} />
                  <div className="min-w-0">
                    <p className="font-medium">{entry.label}</p>
                    <p className="truncate text-xs text-slate-500">删除后保留 {RECYCLE_BIN_RETENTION_DAYS} 天</p>
                  </div>
                </button>
              );
            })}
          </div>
        </CardContent>
      </Card>

      {/* Middle Content */}
      <Card className="flex-1 flex flex-col h-full overflow-hidden">
        {/* Header / Breadcrumbs */}
        <div className="p-4 border-b border-white/10 flex items-center justify-between shrink-0">
          <div className="flex items-center text-sm text-slate-400">
            <button className="hover:text-white transition-colors" onClick={() => handleSidebarClick([])}>
              网盘
            </button>
            {currentPath.map((pathItem, index) => (
              <React.Fragment key={index}>
                <ChevronRight className="w-4 h-4 mx-1" />
                <button
                  onClick={() => handleBreadcrumbClick(index)}
                  className={cn('transition-colors', index === currentPath.length - 1 ? 'text-white font-medium' : 'hover:text-white')}
                >
                  {pathItem}
                </button>
              </React.Fragment>
            ))}
          </div>
          {shareStatus ? (
            <div className="hidden max-w-xs truncate text-xs text-emerald-300 md:block">{shareStatus}</div>
          ) : null}
          <div className="flex items-center gap-2 bg-black/20 p-1 rounded-lg">
            <button
              onClick={() => setViewMode('list')}
              className={cn(
                'p-1.5 rounded-md transition-colors',
                viewMode === 'list' ? 'bg-white/10 text-white' : 'text-slate-400 hover:text-white',
              )}
            >
              <List className="w-4 h-4" />
            </button>
            <button
              onClick={() => setViewMode('grid')}
              className={cn(
                'p-1.5 rounded-md transition-colors',
                viewMode === 'grid' ? 'bg-white/10 text-white' : 'text-slate-400 hover:text-white',
              )}
            >
              <LayoutGrid className="w-4 h-4" />
            </button>
          </div>
        </div>

        <form className="border-b border-white/10 p-4 pt-0" onSubmit={handleSearchSubmit}>
          <div className="mt-3 flex flex-col gap-2 md:flex-row">
            <Input
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder="按文件名搜索"
              className="h-10 border-white/10 bg-black/20 text-white placeholder:text-slate-500 focus-visible:ring-[#336EFF]"
            />
            <div className="flex gap-2">
              <Button type="submit" className="shrink-0" disabled={searchLoading}>
                {searchLoading ? '搜索中...' : '搜索'}
              </Button>
              {isSearchActive ? (
                <Button
                  type="button"
                  variant="outline"
                  className="shrink-0 border-white/10 text-slate-300 hover:bg-white/10"
                  onClick={() => {
                    clearSearchState();
                  }}
                >
                  清空
                </Button>
              ) : null}
            </div>
          </div>
          {searchError ? <p className="mt-2 text-sm text-red-400">{searchError}</p> : null}
        </form>

        {/* File List */}
        {isSearchActive ? (
          <div className="flex-1 overflow-y-auto p-4">
            {searchLoading ? (
              <div className="flex flex-col items-center justify-center space-y-3 py-12 text-slate-500">
                <Folder className="h-12 w-12 opacity-20" />
                <p className="text-sm">搜索中...</p>
              </div>
            ) : (searchResults?.length ?? 0) === 0 ? (
              <div className="flex flex-col items-center justify-center space-y-3 py-12 text-slate-500">
                <Folder className="h-12 w-12 opacity-20" />
                <p className="text-sm">未找到匹配项</p>
              </div>
            ) : viewMode === 'list' ? (
              <table className="w-full table-fixed border-collapse text-left">
                <thead>
                  <tr className="border-b border-white/5 text-xs font-semibold uppercase tracking-wider text-slate-500">
                    <th className="w-[40%] pb-3 pl-4 font-medium">名称</th>
                    <th className="hidden w-[26%] pb-3 font-medium md:table-cell">位置</th>
                    <th className="hidden w-[20%] pb-3 font-medium lg:table-cell">修改时间</th>
                    <th className="w-[10%] pb-3 font-medium">大小</th>
                    <th className="w-[4%] pb-3"></th>
                  </tr>
                </thead>
                <tbody>
                  {searchResults?.map((file) => {
                    const uiFile = toUiFile(file);
                    const selected = selectedSearchFile?.id === file.id;

                    return (
                      <tr
                        key={file.id}
                        onClick={() => setSelectedSearchFile(file)}
                        onDoubleClick={() => {
                          if (file.directory) {
                            handleNavigateToPath(splitBackendPath(file.path));
                          }
                        }}
                        className={cn(
                          'group cursor-pointer border-b border-white/5 transition-colors last:border-0',
                          selected ? 'bg-[#336EFF]/10' : 'hover:bg-white/[0.02]',
                        )}
                      >
                        <td className="max-w-0 py-3 pl-4">
                          <div className="flex min-w-0 items-center gap-3">
                            <FileTypeIcon type={uiFile.type} size="sm" />
                            <div className="min-w-0">
                              <span
                                className={cn('block truncate text-sm font-medium', selected ? 'text-[#336EFF]' : 'text-slate-200')}
                                title={uiFile.name}
                              >
                                {ellipsizeFileName(uiFile.name, 48)}
                              </span>
                              <span className="hidden truncate text-xs text-slate-500 md:block" title={file.path}>
                                {file.path}
                              </span>
                            </div>
                          </div>
                        </td>
                        <td className="hidden py-3 text-sm text-slate-400 md:table-cell">{file.path}</td>
                        <td className="hidden py-3 text-sm text-slate-400 lg:table-cell">{uiFile.modified}</td>
                        <td className="py-3 font-mono text-sm text-slate-400">{uiFile.size}</td>
                        <td className="py-3 pr-4 text-right">
                          <FileActionMenu
                            file={uiFile}
                            activeDropdown={activeDropdown}
                            onToggle={(fileId) => setActiveDropdown((previous) => (previous === fileId ? null : fileId))}
                            onDownload={handleDownload}
                            onShare={handleShare}
                            onMove={(targetFile) => openTargetActionModal(targetFile, 'move')}
                            onCopy={(targetFile) => openTargetActionModal(targetFile, 'copy')}
                            onRename={openRenameModal}
                            onDelete={openDeleteModal}
                            onClose={() => setActiveDropdown(null)}
                            allowMutatingActions={false}
                          />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            ) : (
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
                {searchResults?.map((file) => {
                  const uiFile = toUiFile(file);
                  const selected = selectedSearchFile?.id === file.id;

                  return (
                    <div
                      key={file.id}
                      onClick={() => setSelectedSearchFile(file)}
                      onDoubleClick={() => {
                        if (file.directory) {
                          handleNavigateToPath(splitBackendPath(file.path));
                        }
                      }}
                      className={cn(
                        'group relative flex cursor-pointer flex-col items-center rounded-xl border p-4 transition-all',
                        selected
                          ? 'border-[#336EFF]/30 bg-[#336EFF]/10'
                          : 'border-white/5 bg-white/[0.02] hover:border-white/10 hover:bg-white/[0.04]',
                      )}
                    >
                      <div className="absolute right-2 top-2">
                        <FileActionMenu
                          file={uiFile}
                          activeDropdown={activeDropdown}
                          onToggle={(fileId) => setActiveDropdown((previous) => (previous === fileId ? null : fileId))}
                          onDownload={handleDownload}
                          onShare={handleShare}
                          onMove={(targetFile) => openTargetActionModal(targetFile, 'move')}
                          onCopy={(targetFile) => openTargetActionModal(targetFile, 'copy')}
                          onRename={openRenameModal}
                          onDelete={openDeleteModal}
                          onClose={() => setActiveDropdown(null)}
                          allowMutatingActions={false}
                        />
                      </div>

                      <FileTypeIcon type={uiFile.type} size="lg" className="mb-3 transition-transform duration-200 group-hover:scale-[1.03]" />

                      <span className={cn('w-full truncate px-2 text-center text-sm font-medium', selected ? 'text-[#336EFF]' : 'text-slate-200')}>
                        {ellipsizeFileName(uiFile.name, 24)}
                      </span>
                      <span className={cn('mt-1 inline-flex rounded-full px-2 py-1 text-[11px] font-medium', getFileTypeTheme(uiFile.type).badgeClassName)}>
                        {uiFile.typeLabel}
                      </span>
                      <span className="mt-2 text-xs text-slate-500">
                        {uiFile.type === 'folder' ? uiFile.modified : uiFile.size}
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        ) : null}
        <div className={cn('flex-1 overflow-y-auto p-4', isSearchActive ? 'hidden' : '')}>
          {currentFiles.length === 0 ? (
            <div className="flex flex-col items-center justify-center space-y-3 py-12 text-slate-500">
              <Folder className="w-12 h-12 opacity-20" />
              <p className="text-sm">此文件夹为空</p>
            </div>
          ) : viewMode === 'list' ? (
            <table className="w-full table-fixed text-left border-collapse">
              <thead>
                <tr className="text-xs font-semibold text-slate-500 uppercase tracking-wider border-b border-white/5">
                  <th className="pb-3 pl-4 font-medium w-[44%]">名称</th>
                  <th className="pb-3 font-medium hidden md:table-cell w-[22%]">修改日期</th>
                  <th className="pb-3 font-medium hidden lg:table-cell w-[14%]">类型</th>
                  <th className="pb-3 font-medium w-[10%]">大小</th>
                  <th className="pb-3 w-[10%]"></th>
                </tr>
              </thead>
              <tbody>
                {currentFiles.map((file) => (
                  <tr
                    key={file.id}
                    onClick={() => setSelectedFile(file)}
                    onDoubleClick={() => handleFolderDoubleClick(file)}
                    className={cn(
                      'group cursor-pointer transition-colors border-b border-white/5 last:border-0',
                      selectedFile?.id === file.id ? 'bg-[#336EFF]/10' : 'hover:bg-white/[0.02]',
                    )}
                  >
                    <td className="py-3 pl-4 max-w-0">
                      <div className="flex min-w-0 items-center gap-3">
                        <FileTypeIcon type={file.type} size="sm" />
                        <span
                          className={cn('block truncate text-sm font-medium', selectedFile?.id === file.id ? 'text-[#336EFF]' : 'text-slate-200')}
                          title={file.name}
                        >
                          {ellipsizeFileName(file.name, 48)}
                        </span>
                      </div>
                    </td>
                    <td className="py-3 text-sm text-slate-400 hidden md:table-cell">{file.modified}</td>
                    <td className="py-3 text-sm text-slate-400 hidden lg:table-cell">
                      <span
                        className={cn(
                          'inline-flex items-center rounded-full px-2.5 py-1 text-[11px] font-medium tracking-wide',
                          getFileTypeTheme(file.type).badgeClassName,
                        )}
                      >
                        {file.typeLabel}
                      </span>
                    </td>
                    <td className="py-3 text-sm text-slate-400 font-mono">{file.size}</td>
                    <td className="py-3 pr-4 text-right">
                      <FileActionMenu
                        file={file}
                        activeDropdown={activeDropdown}
                        onToggle={(fileId) => setActiveDropdown((previous) => (previous === fileId ? null : fileId))}
                        onDownload={handleDownload}
                        onShare={handleShare}
                        onMove={(targetFile) => openTargetActionModal(targetFile, 'move')}
                        onCopy={(targetFile) => openTargetActionModal(targetFile, 'copy')}
                        onRename={openRenameModal}
                        onDelete={openDeleteModal}
                        onClose={() => setActiveDropdown(null)}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
              {currentFiles.map((file) => (
                <div
                  key={file.id}
                  onClick={() => setSelectedFile(file)}
                  onDoubleClick={() => handleFolderDoubleClick(file)}
                  className={cn(
                    'group relative flex cursor-pointer flex-col items-center rounded-xl border p-4 transition-all',
                    selectedFile?.id === file.id
                      ? 'border-[#336EFF]/30 bg-[#336EFF]/10'
                      : 'border-white/5 bg-white/[0.02] hover:border-white/10 hover:bg-white/[0.04]',
                  )}
                >
                  <div className="absolute right-2 top-2">
                    <FileActionMenu
                      file={file}
                      activeDropdown={activeDropdown}
                      onToggle={(fileId) => setActiveDropdown((previous) => (previous === fileId ? null : fileId))}
                      onDownload={handleDownload}
                      onShare={handleShare}
                      onMove={(file) => openTargetActionModal(file, 'move')}
                      onCopy={(file) => openTargetActionModal(file, 'copy')}
                      onRename={openRenameModal}
                      onDelete={openDeleteModal}
                      onClose={() => setActiveDropdown(null)}
                    />
                  </div>

                  <FileTypeIcon type={file.type} size="lg" className="mb-3 transition-transform duration-200 group-hover:scale-[1.03]" />

                  <span className={cn('w-full truncate px-2 text-center text-sm font-medium', selectedFile?.id === file.id ? 'text-[#336EFF]' : 'text-slate-200')}>
                    {ellipsizeFileName(file.name, 24)}
                  </span>
                  <span className={cn('mt-1 inline-flex rounded-full px-2 py-1 text-[11px] font-medium', getFileTypeTheme(file.type).badgeClassName)}>
                    {file.typeLabel}
                  </span>
                  <span className="mt-2 text-xs text-slate-500">
                    {file.type === 'folder' ? file.modified : file.size}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Bottom Actions */}
        <div className="p-4 border-t border-white/10 flex items-center gap-3 shrink-0 bg-white/[0.01]">
          <Button variant="default" className="gap-2" onClick={handleUploadClick}>
            <Upload className="w-4 h-4" /> 上传文件
          </Button>
          <Button variant="outline" className="gap-2" onClick={handleUploadFolderClick}>
            <FolderUp className="w-4 h-4" /> 上传文件夹
          </Button>
          <Button variant="outline" className="gap-2" onClick={handleCreateFolder}>
            <Plus className="w-4 h-4" /> 新建文件夹
          </Button>
          <input ref={fileInputRef} type="file" multiple className="hidden" onChange={handleFileChange} />
          <input ref={directoryInputRef} type="file" multiple className="hidden" onChange={handleFolderChange} />
        </div>
      </Card>

      {/* Right Sidebar (Details + Tasks) */}
      <motion.div
        initial={{ opacity: 0, x: 20 }}
        animate={{ opacity: 1, x: 0 }}
        className="w-full lg:w-72 shrink-0 space-y-4"
      >
        {selectedFile && (
          <Card className="h-full">
            <CardHeader className="pb-4 border-b border-white/10">
              <CardTitle className="text-base">详细信息</CardTitle>
            </CardHeader>
            <CardContent className="p-6 space-y-6">
              <div className="flex w-full flex-col items-center text-center space-y-3">
                <FileTypeIcon type={selectedFile.type} size="lg" />
                <h3 className="w-full truncate text-sm font-medium text-white" title={selectedFile.name}>
                  {selectedFile.name}
                </h3>
              </div>

              <div className="space-y-4">
                <DetailItem label="位置" value={`网盘 > ${currentPath.length === 0 ? '根目录' : currentPath.join(' > ')}`} />
                <DetailItem label="大小" value={selectedFile.size} />
                <DetailItem label="修改时间" value={selectedFile.modified} />
                <DetailItem label="类型" value={selectedFile.typeLabel} />
              </div>

              <div className="pt-4 space-y-3 border-t border-white/10">
                <div className="grid grid-cols-2 gap-3">
                  {selectedFile.type !== 'folder' ? (
                    <Button variant="outline" className="w-full gap-2 bg-white/5 border-white/10 hover:bg-white/10" onClick={() => void handleShare(selectedFile)}>
                      <Share2 className="w-4 h-4" /> 分享链接
                    </Button>
                  ) : null}
                  <Button variant="outline" className="w-full gap-2 bg-white/5 border-white/10 hover:bg-white/10" onClick={() => openRenameModal(selectedFile)}>
                    <Edit2 className="w-4 h-4" /> 重命名
                  </Button>
                  <Button variant="outline" className="w-full gap-2 bg-white/5 border-white/10 hover:bg-white/10" onClick={() => openTargetActionModal(selectedFile, 'move')}>
                    <Folder className="w-4 h-4" /> 移动
                  </Button>
                  <Button variant="outline" className="w-full gap-2 bg-white/5 border-white/10 hover:bg-white/10" onClick={() => openTargetActionModal(selectedFile, 'copy')}>
                    <Copy className="w-4 h-4" /> 复制到
                  </Button>
                  {selectedFile.type !== 'folder' ? (
                    <Button
                      variant="outline"
                      className="col-span-2 w-full gap-2 border-white/10 bg-white/5 hover:bg-white/10"
                      onClick={() => void handleCreateMediaMetadataTask()}
                      disabled={backgroundTaskActionId === selectedFile.id}
                    >
                      <RotateCcw className={cn('w-4 h-4', backgroundTaskActionId === selectedFile.id ? 'animate-spin' : '')} />
                      {backgroundTaskActionId === selectedFile.id ? '创建中...' : '提取媒体信息'}
                    </Button>
                  ) : null}
                  <Button
                    variant="outline"
                    className="w-full gap-2 border-red-500/20 bg-red-500/5 text-red-400 hover:bg-red-500/10 hover:text-red-300"
                    onClick={() => openDeleteModal(selectedFile)}
                  >
                    <Trash2 className="w-4 h-4" /> 删除
                  </Button>
                </div>
                {selectedFile.type === 'folder' && (
                  <div className="space-y-3">
                    <Button variant="default" className="w-full gap-2" onClick={() => handleFolderDoubleClick(selectedFile)}>
                      打开文件夹
                    </Button>
                    <Button variant="default" className="w-full gap-2" onClick={() => void handleDownload(selectedFile)}>
                      <Download className="w-4 h-4" /> 下载文件夹
                    </Button>
                  </div>
                )}
                {selectedFile.type !== 'folder' && (
                  <Button variant="default" className="w-full gap-2" onClick={() => void handleDownload(selectedFile)}>
                    <Download className="w-4 h-4" /> 下载文件
                  </Button>
                )}
                {shareStatus && selectedFile.type !== 'folder' ? (
                  <div className="rounded-xl border border-emerald-500/20 bg-emerald-500/10 px-3 py-2 text-xs text-emerald-200">
                    {shareStatus}
                  </div>
                ) : null}
              </div>
            </CardContent>
          </Card>
        )}

        <Card>
          <CardHeader className="border-b border-white/10 pb-4">
            <div className="flex items-center justify-between gap-3">
              <CardTitle className="text-base">后台任务</CardTitle>
              <button
                type="button"
                className="flex h-8 w-8 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-white/10 hover:text-white"
                onClick={() => void loadBackgroundTasks()}
                aria-label="刷新后台任务"
              >
                <RotateCcw className={cn('h-4 w-4', backgroundTasksLoading ? 'animate-spin' : '')} />
              </button>
            </div>
          </CardHeader>
          <CardContent className="space-y-3 p-4">
            {backgroundTaskNotice ? (
              <div
                className={cn(
                  'rounded-xl border px-3 py-2 text-xs leading-relaxed',
                  backgroundTaskNotice.kind === 'error'
                    ? 'border-red-500/20 bg-red-500/10 text-red-200'
                    : 'border-emerald-500/20 bg-emerald-500/10 text-emerald-200',
                )}
                aria-live="polite"
              >
                {backgroundTaskNotice.message}
              </div>
            ) : null}
            {backgroundTasksError ? (
              <div className="rounded-xl border border-red-500/20 bg-red-500/10 px-3 py-2 text-xs text-red-200">
                {backgroundTasksError}
              </div>
            ) : null}
            {backgroundTasksLoading ? (
              <div className="rounded-xl border border-white/10 bg-white/[0.02] px-3 py-4 text-sm text-slate-400">
                加载最近任务中...
              </div>
            ) : backgroundTasks.length === 0 ? (
              <div className="rounded-xl border border-white/10 bg-white/[0.02] px-3 py-4 text-sm text-slate-400">
                暂无后台任务
              </div>
            ) : (
              <div className="max-h-[32rem] space-y-3 overflow-y-auto pr-1">
                {backgroundTasks.map((task) => {
                  const canCancel = task.status === 'QUEUED' || task.status === 'RUNNING';
                  return (
                    <div key={task.id} className="rounded-xl border border-white/10 bg-white/[0.03] p-3">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium text-white">{getBackgroundTaskTypeLabel(task.type)}</p>
                          <p className={cn('text-xs', getBackgroundTaskStatusClassName(task.status))}>
                            {getBackgroundTaskStatusLabel(task.status)}
                          </p>
                        </div>
                        {canCancel ? (
                          <Button
                            type="button"
                            variant="outline"
                            className="shrink-0 border-white/10 bg-white/5 px-3 text-xs text-slate-200 hover:bg-white/10"
                            onClick={() => void handleCancelBackgroundTask(task.id)}
                            disabled={backgroundTaskActionId === task.id}
                          >
                            {backgroundTaskActionId === task.id ? '取消中...' : '取消'}
                          </Button>
                        ) : null}
                      </div>
                      <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
                        <div className="min-w-0">
                          <p className="text-slate-500">创建时间</p>
                          <p className="truncate text-slate-300">{formatTaskDateTime(task.createdAt)}</p>
                        </div>
                        <div className="min-w-0">
                          <p className="text-slate-500">完成时间</p>
                          <p className="truncate text-slate-300">{task.finishedAt ? formatTaskDateTime(task.finishedAt) : '未完成'}</p>
                        </div>
                      </div>
                      {task.errorMessage ? (
                        <div className="mt-3 break-words rounded-lg border border-red-500/20 bg-red-500/10 px-2 py-1 text-xs leading-relaxed text-red-200">
                          {task.errorMessage}
                        </div>
                      ) : null}
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      </motion.div>

      <AnimatePresence>
        {renameModalOpen && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              className="w-full max-w-sm overflow-hidden rounded-xl border border-white/10 bg-[#0f172a] shadow-2xl"
            >
              <div className="flex items-center justify-between border-b border-white/10 bg-white/5 p-4">
                <h3 className="flex items-center gap-2 text-lg font-semibold text-white">
                  <Edit2 className="w-5 h-5 text-[#336EFF]" />
                  重命名
                </h3>
                <button
                  onClick={() => {
                    setRenameModalOpen(false);
                    setFileToRename(null);
                    setRenameError('');
                  }}
                  className="rounded-md p-1 text-slate-400 transition-colors hover:bg-white/10 hover:text-white"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
              <div className="space-y-5 p-5">
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-300">新名称</label>
                  <Input
                    value={newFileName}
                    onChange={(event) => setNewFileName(event.target.value)}
                    className="bg-black/20 border-white/10 text-white focus-visible:ring-[#336EFF]"
                    autoFocus
                    disabled={isRenaming}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' && !isRenaming) {
                        void handleRename();
                      }
                    }}
                  />
                </div>
                {renameError && (
                  <div className="rounded-xl border border-red-500/20 bg-red-500/10 p-3 text-sm text-red-400">
                    {renameError}
                  </div>
                )}
                <div className="flex justify-end gap-3 pt-2">
                  <Button
                    variant="outline"
                    onClick={() => {
                      setRenameModalOpen(false);
                      setFileToRename(null);
                      setRenameError('');
                    }}
                    disabled={isRenaming}
                    className="border-white/10 text-slate-300 hover:bg-white/10"
                  >
                    取消
                  </Button>
                  <Button variant="default" onClick={() => void handleRename()} disabled={isRenaming}>
                    {isRenaming ? (
                      <span className="flex items-center gap-2">
                        <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/20 border-t-white" />
                        重命名中...
                      </span>
                    ) : (
                      '确定'
                    )}
                  </Button>
                </div>
              </div>
            </motion.div>
          </div>
        )}

        {deleteModalOpen && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              className="w-full max-w-sm overflow-hidden rounded-xl border border-white/10 bg-[#0f172a] shadow-2xl"
            >
              <div className="flex items-center justify-between border-b border-white/10 bg-white/5 p-4">
                <h3 className="flex items-center gap-2 text-lg font-semibold text-white">
                  <Trash2 className="w-5 h-5 text-red-500" />
                  确认删除
                </h3>
                <button
                  onClick={() => {
                    setDeleteModalOpen(false);
                    setFileToDelete(null);
                  }}
                  className="rounded-md p-1 text-slate-400 transition-colors hover:bg-white/10 hover:text-white"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
              <div className="space-y-5 p-5">
                <p className="text-sm leading-relaxed text-slate-300">
                  确定要将 <span className="rounded bg-white/10 px-1 py-0.5 font-medium text-white">{fileToDelete?.name}</span> 移入回收站吗？文件会保留 {RECYCLE_BIN_RETENTION_DAYS} 天，期间可以恢复。
                </p>
                <div className="flex justify-end gap-3 pt-2">
                  <Button
                    variant="outline"
                    onClick={() => {
                      setDeleteModalOpen(false);
                      setFileToDelete(null);
                    }}
                    className="border-white/10 text-slate-300 hover:bg-white/10"
                  >
                    取消
                  </Button>
                  <Button
                    variant="outline"
                    className="border-red-500/30 bg-red-500 text-white hover:bg-red-600"
                    onClick={() => void handleDelete()}
                  >
                    移入回收站
                  </Button>
                </div>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      <NetdiskPathPickerModal
        isOpen={Boolean(targetActionFile && targetAction)}
        title={targetAction === 'copy' ? '选择复制目标' : '选择移动目标'}
        description={
          targetAction === 'copy'
            ? '选择要把当前文件或文件夹复制到哪个目录。'
            : '选择要把当前文件或文件夹移动到哪个目录。'
        }
        initialPath={toBackendPath(currentPath)}
        confirmLabel={targetAction === 'copy' ? '复制到这里' : '移动到这里'}
        onClose={() => {
          setTargetAction(null);
          setTargetActionFile(null);
        }}
        onConfirm={handleMoveToPath}
      />
    </div>
  );
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs font-medium text-slate-500 mb-1">{label}</p>
      <p className="text-sm text-slate-300">{value}</p>
    </div>
  );
}

function formatTaskDateTime(value: string) {
  return formatDateTime(value);
}

function getBackgroundTaskTypeLabel(type: BackgroundTask['type']) {
  switch (type) {
    case 'ARCHIVE':
      return '压缩任务';
    case 'EXTRACT':
      return '解压任务';
    case 'MEDIA_META':
      return '媒体信息提取任务';
  }
}

function getBackgroundTaskStatusLabel(status: BackgroundTask['status']) {
  switch (status) {
    case 'QUEUED':
      return '排队中';
    case 'RUNNING':
      return '执行中';
    case 'COMPLETED':
      return '已完成';
    case 'FAILED':
      return '已失败';
    case 'CANCELLED':
      return '已取消';
  }
}

function getBackgroundTaskStatusClassName(status: BackgroundTask['status']) {
  switch (status) {
    case 'QUEUED':
      return 'text-amber-300';
    case 'RUNNING':
      return 'text-sky-300';
    case 'COMPLETED':
      return 'text-emerald-300';
    case 'FAILED':
      return 'text-red-300';
    case 'CANCELLED':
      return 'text-slate-400';
  }
}

function FileActionMenu({
  file,
  activeDropdown,
  onToggle,
  onDownload,
  onShare,
  onMove,
  onCopy,
  onRename,
  onDelete,
  onClose,
  allowMutatingActions = true,
}: {
  file: UiFile;
  activeDropdown: number | null;
  onToggle: (fileId: number) => void;
  onDownload: (file: UiFile) => Promise<void>;
  onShare: (file: UiFile) => Promise<void>;
  onMove: (file: UiFile) => void;
  onCopy: (file: UiFile) => void;
  onRename: (file: UiFile) => void;
  onDelete: (file: UiFile) => void;
  onClose: () => void;
  allowMutatingActions?: boolean;
}) {
  return (
    <div className="relative inline-block text-left">
      <button
        onClick={(event) => {
          event.stopPropagation();
          onToggle(file.id);
        }}
        className="rounded-md p-1.5 text-slate-500 opacity-0 transition-all hover:bg-white/10 hover:text-white group-hover:opacity-100"
      >
        <MoreVertical className="w-4 h-4" />
      </button>
      {activeDropdown === file.id && (
        <div
          className="fixed inset-0 z-40"
          onClick={(event) => {
            event.stopPropagation();
            onClose();
          }}
        />
      )}
      <AnimatePresence>
        {activeDropdown === file.id && (
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 10 }}
            transition={{ duration: 0.15 }}
            className="absolute right-0 top-full z-50 mt-1 w-32 overflow-hidden rounded-lg border border-white/10 bg-[#1e293b] py-1 shadow-xl"
          >
            <button
              onClick={(event) => {
                event.stopPropagation();
                void onDownload(file);
                onClose();
              }}
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
            >
              <Download className="w-4 h-4" /> {file.type === 'folder' ? '下载文件夹' : '下载文件'}
            </button>
            {file.type !== 'folder' ? (
              <button
                onClick={(event) => {
                  event.stopPropagation();
                  void onShare(file);
                  onClose();
                }}
                className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
              >
                <Share2 className="w-4 h-4" /> 分享链接
              </button>
            ) : null}
            {allowMutatingActions ? (
              <>
                <button
                  onClick={(event) => {
                    event.stopPropagation();
                    onMove(file);
                    onClose();
                  }}
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
                >
                  <Folder className="w-4 h-4" /> 移动
                </button>
                <button
                  onClick={(event) => {
                    event.stopPropagation();
                    onCopy(file);
                    onClose();
                  }}
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
                >
                  <Copy className="w-4 h-4" /> 复制到
                </button>
                <button
                  onClick={(event) => {
                    event.stopPropagation();
                    onRename(file);
                    onClose();
                  }}
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
                >
                  <Edit2 className="w-4 h-4" /> 重命名
                </button>
                <button
                  onClick={(event) => {
                    event.stopPropagation();
                    onDelete(file);
                    onClose();
                  }}
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-red-400 transition-colors hover:bg-red-500/10 hover:text-red-300"
                >
                  <Trash2 className="w-4 h-4" /> 删除
                </button>
              </>
            ) : null}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
