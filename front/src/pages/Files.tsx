import React, { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  CheckCircle2,
  ChevronDown,
  Folder,
  FileText,
  Image as ImageIcon,
  Download,
  ChevronRight,
  ChevronUp,
  FileUp,
  FolderUp,
  Upload,
  UploadCloud,
  Plus,
  LayoutGrid,
  List,
  MoreVertical,
  Copy,
  Share2,
  TriangleAlert,
  X,
  Edit2,
  Trash2,
} from 'lucide-react';

import { NetdiskPathPickerModal } from '@/src/components/ui/NetdiskPathPickerModal';
import { Button } from '@/src/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/src/components/ui/card';
import { Input } from '@/src/components/ui/input';
import { ApiError, apiBinaryUploadRequest, apiDownload, apiRequest, apiUploadRequest } from '@/src/lib/api';
import { copyFileToNetdiskPath } from '@/src/lib/file-copy';
import { moveFileToNetdiskPath } from '@/src/lib/file-move';
import { readCachedValue, writeCachedValue } from '@/src/lib/cache';
import { createFileShareLink, getCurrentFileShareUrl } from '@/src/lib/file-share';
import { getFilesLastPathCacheKey, getFilesListCacheKey } from '@/src/lib/page-cache';
import type { DownloadUrlResponse, FileMetadata, InitiateUploadResponse, PageResponse } from '@/src/lib/types';
import { cn } from '@/src/lib/utils';

import {
  buildUploadProgressSnapshot,
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
  mergeDirectoryChildren,
  toDirectoryPath,
  type DirectoryChildrenMap,
  type DirectoryTreeNode,
} from './files-tree';

function sleep(ms: number) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function toBackendPath(pathParts: string[]) {
  return toDirectoryPath(pathParts);
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
  const extension = file.filename.includes('.') ? file.filename.split('.').pop()?.toLowerCase() : '';
  let type = extension || 'document';

  if (file.directory) {
    type = 'folder';
  } else if (file.contentType?.startsWith('image/')) {
    type = 'image';
  } else if (file.contentType?.includes('pdf')) {
    type = 'pdf';
  }

  return {
    id: file.id,
    name: file.filename,
    type,
    size: file.directory ? '—' : formatFileSize(file.size),
    modified: formatDateTime(file.createdAt),
  };
}

type UiFile = ReturnType<typeof toUiFile>;
type NetdiskTargetAction = 'move' | 'copy';

export default function Files() {
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
  const [uploads, setUploads] = useState<UploadTask[]>([]);
  const [isUploadPanelOpen, setIsUploadPanelOpen] = useState(true);
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

  const handleSidebarClick = (pathParts: string[]) => {
    setCurrentPath(pathParts);
    setSelectedFile(null);
    setActiveDropdown(null);
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
      shouldLoadChildren = !(path in directoryChildren);
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
      setCurrentPath([...currentPath, file.name]);
      setSelectedFile(null);
    }
  };

  const handleBreadcrumbClick = (index: number) => {
    setCurrentPath(currentPath.slice(0, index + 1));
    setSelectedFile(null);
    setActiveDropdown(null);
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

    setIsUploadPanelOpen(true);
    uploadMeasurementsRef.current.clear();

    const batchTasks = createUploadTasks(entries);
    setUploads(batchTasks);

    const runSingleUpload = async (
      {file: uploadFile, pathParts: uploadPathParts}: PendingUploadEntry,
      uploadTask: UploadTask,
    ) => {
      const uploadPath = toBackendPath(uploadPathParts);
      const startedAt = Date.now();
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
          setUploads((previous) =>
            previous.map((task) =>
              task.id === uploadTask.id
                ? {
                    ...task,
                    progress: snapshot.progress,
                    speed: snapshot.speed,
                  }
                : task,
            ),
          );
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
            });

            uploadedFile = await apiRequest<FileMetadata>('/files/upload/complete', {
              method: 'POST',
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
          });
        } else {
          const formData = new FormData();
          formData.append('file', uploadFile);
          uploadedFile = await apiUploadRequest<FileMetadata>(`/files/upload?path=${encodeURIComponent(uploadPath)}`, {
            body: formData,
            onProgress: updateProgress,
          });
        }

        uploadMeasurementsRef.current.delete(uploadTask.id);
        setUploads((previous) =>
          previous.map((task) => (task.id === uploadTask.id ? prepareUploadTaskForCompletion(task) : task)),
        );
        await sleep(120);
        setUploads((previous) =>
          previous.map((task) => (task.id === uploadTask.id ? completeUploadTask(task) : task)),
        );
        return uploadedFile;
      } catch (error) {
        uploadMeasurementsRef.current.delete(uploadTask.id);
        const message = error instanceof Error && error.message ? error.message : '上传失败，请稍后重试';
        setUploads((previous) =>
          previous.map((task) => (task.id === uploadTask.id ? failUploadTask(task, message) : task)),
        );
        return null;
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

  const handleClearUploads = () => {
    uploadMeasurementsRef.current.clear();
    setUploads([]);
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

  return (
    <div className="flex flex-col lg:flex-row gap-6 h-[calc(100vh-8rem)]">
      {/* Left Sidebar */}
      <Card className="w-full lg:w-64 shrink-0 flex flex-col h-full overflow-y-auto">
        <CardContent className="p-4">
          <div className="space-y-2">
            <p className="px-3 text-xs font-semibold text-slate-500 uppercase tracking-wider">网盘目录</p>
            <div className="rounded-2xl border border-white/5 bg-black/20 p-2">
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
              <div className="mt-1 space-y-0.5">
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

        {/* File List */}
        <div className="flex-1 overflow-y-auto p-4">
          {currentFiles.length === 0 ? (
            <div className="flex flex-col items-center justify-center space-y-3 py-12 text-slate-500">
              <Folder className="w-12 h-12 opacity-20" />
              <p className="text-sm">此文件夹为空</p>
            </div>
          ) : viewMode === 'list' ? (
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="text-xs font-semibold text-slate-500 uppercase tracking-wider border-b border-white/5">
                  <th className="pb-3 pl-4 font-medium">名称</th>
                  <th className="pb-3 font-medium hidden md:table-cell">修改日期</th>
                  <th className="pb-3 font-medium hidden lg:table-cell">类型</th>
                  <th className="pb-3 font-medium">大小</th>
                  <th className="pb-3"></th>
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
                    <td className="py-3 pl-4">
                      <div className="flex items-center gap-3">
                        {file.type === 'folder' ? (
                          <Folder className="w-5 h-5 text-[#336EFF]" />
                        ) : file.type === 'image' ? (
                          <ImageIcon className="w-5 h-5 text-purple-400" />
                        ) : (
                          <FileText className="w-5 h-5 text-blue-400" />
                        )}
                        <span className={cn('text-sm font-medium', selectedFile?.id === file.id ? 'text-[#336EFF]' : 'text-slate-200')}>
                          {file.name}
                        </span>
                      </div>
                    </td>
                    <td className="py-3 text-sm text-slate-400 hidden md:table-cell">{file.modified}</td>
                    <td className="py-3 text-sm text-slate-400 hidden lg:table-cell uppercase">{file.type}</td>
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

                  <div className="mb-3 flex h-16 w-16 items-center justify-center rounded-2xl bg-white/5 transition-colors group-hover:bg-white/10">
                    {file.type === 'folder' ? (
                      <Folder className="w-8 h-8 text-[#336EFF]" />
                    ) : file.type === 'image' ? (
                      <ImageIcon className="w-8 h-8 text-purple-400" />
                    ) : (
                      <FileText className="w-8 h-8 text-blue-400" />
                    )}
                  </div>

                  <span className={cn('w-full truncate px-2 text-center text-sm font-medium', selectedFile?.id === file.id ? 'text-[#336EFF]' : 'text-slate-200')}>
                    {file.name}
                  </span>
                  <span className="mt-1 text-xs text-slate-500">
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

      {/* Right Sidebar (Details) */}
      {selectedFile && (
        <motion.div
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          className="w-full lg:w-72 shrink-0"
        >
          <Card className="h-full">
            <CardHeader className="pb-4 border-b border-white/10">
              <CardTitle className="text-base">详细信息</CardTitle>
            </CardHeader>
            <CardContent className="p-6 space-y-6">
              <div className="flex flex-col items-center text-center space-y-3">
                <div className="w-16 h-16 rounded-2xl bg-[#336EFF]/10 flex items-center justify-center">
                  {selectedFile.type === 'folder' ? (
                    <Folder className="w-8 h-8 text-[#336EFF]" />
                  ) : selectedFile.type === 'image' ? (
                    <ImageIcon className="w-8 h-8 text-purple-400" />
                  ) : (
                    <FileText className="w-8 h-8 text-blue-400" />
                  )}
                </div>
                <h3 className="text-sm font-medium text-white break-all">{selectedFile.name}</h3>
              </div>

              <div className="space-y-4">
                <DetailItem label="位置" value={`网盘 > ${currentPath.length === 0 ? '根目录' : currentPath.join(' > ')}`} />
                <DetailItem label="大小" value={selectedFile.size} />
                <DetailItem label="修改时间" value={selectedFile.modified} />
                <DetailItem label="类型" value={selectedFile.type.toUpperCase()} />
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
        </motion.div>
      )}

      <AnimatePresence>
        {uploads.length > 0 && (
          <motion.div
            initial={{ opacity: 0, y: 50, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 50, scale: 0.95 }}
            className="fixed bottom-6 right-6 z-50 flex w-[min(24rem,calc(100vw-2rem))] flex-col overflow-hidden rounded-xl border border-white/10 bg-[#0f172a]/95 shadow-2xl backdrop-blur-xl"
          >
            <div
              className="flex cursor-pointer items-center justify-between border-b border-white/10 bg-white/5 px-4 py-3 transition-colors hover:bg-white/10"
              onClick={() => setIsUploadPanelOpen((previous) => !previous)}
            >
              <div className="flex items-center gap-2">
                <UploadCloud className="h-4 w-4 text-[#336EFF]" />
                <span className="text-sm font-medium text-white">
                  上传进度 ({uploads.filter((task) => task.status === 'completed').length}/{uploads.length})
                </span>
              </div>
              <div className="flex items-center gap-1">
                <button className="rounded p-1 text-slate-400 transition-colors hover:bg-white/10 hover:text-white">
                  {isUploadPanelOpen ? <ChevronDown className="h-4 w-4" /> : <ChevronUp className="h-4 w-4" />}
                </button>
                <button
                  className="rounded p-1 text-slate-400 transition-colors hover:bg-white/10 hover:text-white"
                  onClick={(event) => {
                    event.stopPropagation();
                    handleClearUploads();
                  }}
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            </div>

            <AnimatePresence initial={false}>
              {isUploadPanelOpen && (
                <motion.div initial={{ height: 0 }} animate={{ height: 'auto' }} exit={{ height: 0 }} className="max-h-80 overflow-y-auto">
                  <div className="space-y-1 p-2">
                    {uploads.map((task) => (
                      <div
                        key={task.id}
                        className={cn(
                          'group relative overflow-hidden rounded-lg p-3 transition-colors hover:bg-white/5',
                          task.status === 'error' && 'bg-rose-500/5',
                        )}
                      >
                        {task.status === 'uploading' && (
                          <div
                            className="absolute inset-y-0 left-0 bg-[#336EFF]/10 transition-all duration-300 ease-out"
                            style={{ width: `${task.progress}%` }}
                          />
                        )}

                        <div className="relative z-10 flex items-start gap-3">
                          <div className="mt-0.5">
                            {task.status === 'completed' ? (
                              <CheckCircle2 className="h-5 w-5 text-emerald-500" />
                            ) : task.status === 'error' ? (
                              <TriangleAlert className="h-5 w-5 text-rose-400" />
                            ) : (
                              <FileUp className="h-5 w-5 animate-pulse text-[#336EFF]" />
                            )}
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-medium text-slate-200">{task.fileName}</p>
                            <p className="mt-0.5 truncate text-xs text-slate-500">上传至: {task.destination}</p>
                            {task.noticeMessage && (
                              <p className="mt-2 truncate text-xs text-amber-300">{task.noticeMessage}</p>
                            )}

                            {task.status === 'uploading' && (
                              <div className="mt-2 flex items-center justify-between text-xs">
                                <span className="font-medium text-[#336EFF]">{Math.round(task.progress)}%</span>
                                <span className="font-mono text-slate-400">{task.speed}</span>
                              </div>
                            )}
                            {task.status === 'completed' && (
                              <p className="mt-2 text-xs text-emerald-400">上传完成</p>
                            )}
                            {task.status === 'error' && (
                              <p className="mt-2 truncate text-xs text-rose-400">{task.errorMessage ?? '上传失败，请稍后重试'}</p>
                            )}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        )}
      </AnimatePresence>

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
                  确定要删除 <span className="rounded bg-white/10 px-1 py-0.5 font-medium text-white">{fileToDelete?.name}</span> 吗？此操作无法撤销。
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
                    删除
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
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
