import React, { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { useNavigate } from 'react-router-dom';
import {
  ChevronRight,
  Folder,
  Download,
  Upload,
  Plus,
  MoreVertical,
  Copy,
  Share2,
  X,
  Edit2,
  Trash2,
  FolderPlus,
  ChevronLeft,
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
import { readCachedValue, writeCachedValue } from '@/src/lib/cache';
import { createFileShareLink, getCurrentFileShareUrl } from '@/src/lib/file-share';
import { ellipsizeFileName } from '@/src/lib/file-name';
import { getFilesLastPathCacheKey, getFilesListCacheKey } from '@/src/lib/page-cache';
import type { DownloadUrlResponse, FileMetadata, InitiateUploadResponse, PageResponse } from '@/src/lib/types';
import { cn } from '@/src/lib/utils';

// Imports directly from the original pages directories
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
} from '@/src/pages/files-upload';
import {
  registerFilesUploadTaskCanceler,
  replaceFilesUploads,
  setFilesUploadPanelOpen,
  unregisterFilesUploadTaskCanceler,
  updateFilesUploadTask,
} from '@/src/pages/files-upload-store';
import {
  clearSelectionIfDeleted,
  getNextAvailableName,
  getActionErrorMessage,
  removeUiFile,
  replaceUiFile,
  syncSelectedFile,
} from '@/src/pages/files-state';
import {
  toDirectoryPath,
} from '@/src/pages/files-tree';
import { RECYCLE_BIN_RETENTION_DAYS, RECYCLE_BIN_ROUTE } from '@/src/pages/recycle-bin-state';

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function toBackendPath(pathParts: string[]) {
  return toDirectoryPath(pathParts);
}

function formatFileSize(size: number) {
  if (size <= 0) return '—';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1);
  const value = size / 1024 ** index;
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatDateTime(value: string) {
  const date = new Date(value);
  return `${date.getMonth() + 1}-${date.getDate()} ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
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
    originalSize: file.directory ? 0 : file.size,
    modified: formatDateTime(file.createdAt),
  };
}

interface UiFile {
  id: FileMetadata['id'];
  modified: string;
  name: string;
  size: string;
  originalSize: number;
  type: FileTypeKind;
  typeLabel: string;
}

type NetdiskTargetAction = 'move' | 'copy';

export function getMobileFilesLayoutClassNames() {
  return {
    root: 'relative flex min-h-full flex-col text-white bg-transparent',
    toolbar: 'sticky top-0 z-30 flex-none px-4 py-2',
    toolbarInner: 'glass-panel flex items-center gap-3 rounded-[22px] border border-white/10 bg-[#0f172a]/72 px-3.5 py-2.5 shadow-md backdrop-blur-2xl',
    list: 'relative z-10 flex-1 px-3 pt-2 pb-4 space-y-1.5',
  };
}

export default function MobileFiles() {
  const navigate = useNavigate();
  const initialPath = readCachedValue<string[]>(getFilesLastPathCacheKey()) ?? [];
  const initialCachedFiles = readCachedValue<FileMetadata[]>(getFilesListCacheKey(toBackendPath(initialPath))) ?? [];
  
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const directoryInputRef = useRef<HTMLInputElement | null>(null);
  const uploadMeasurementsRef = useRef(new Map<string, UploadMeasurement>());
  
  const [currentPath, setCurrentPath] = useState<string[]>(initialPath);
  const currentPathRef = useRef(currentPath);
  
  const [currentFiles, setCurrentFiles] = useState<UiFile[]>(initialCachedFiles.map(toUiFile));
  const [selectedFile, setSelectedFile] = useState<UiFile | null>(null);
  
  // Modals inside mobile action sheet
  const [actionSheetOpen, setActionSheetOpen] = useState(false);
  const [renameModalOpen, setRenameModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [fileToRename, setFileToRename] = useState<UiFile | null>(null);
  const [fileToDelete, setFileToDelete] = useState<UiFile | null>(null);
  const [targetActionFile, setTargetActionFile] = useState<UiFile | null>(null);
  const [targetAction, setTargetAction] = useState<NetdiskTargetAction | null>(null);
  const [newFileName, setNewFileName] = useState('');
  
  const [renameError, setRenameError] = useState('');
  const [isRenaming, setIsRenaming] = useState(false);
  const [shareStatus, setShareStatus] = useState('');
  
  // Floating Action Button
  const [fabOpen, setFabOpen] = useState(false);
  const layoutClassNames = getMobileFilesLayoutClassNames();

  const loadCurrentPath = async (pathParts: string[]) => {
    const response = await apiRequest<PageResponse<FileMetadata>>(
      `/files/list?path=${encodeURIComponent(toBackendPath(pathParts))}&page=0&size=100`
    );
    writeCachedValue(getFilesListCacheKey(toBackendPath(pathParts)), response.items);
    writeCachedValue(getFilesLastPathCacheKey(), pathParts);
    setCurrentFiles(response.items.map(toUiFile));
  };

  useEffect(() => {
    currentPathRef.current = currentPath;
    const cachedFiles = readCachedValue<FileMetadata[]>(getFilesListCacheKey(toBackendPath(currentPath)));
    writeCachedValue(getFilesLastPathCacheKey(), currentPath);

    if (cachedFiles) {
      setCurrentFiles(cachedFiles.map(toUiFile));
    }
    loadCurrentPath(currentPath).catch(() => {
      if (!cachedFiles) setCurrentFiles([]);
    });
  }, [currentPath]);

  useEffect(() => {
    if (directoryInputRef.current) {
      directoryInputRef.current.setAttribute('webkitdirectory', '');
      directoryInputRef.current.setAttribute('directory', '');
    }
  }, []);

  const handleBreadcrumbClick = (index: number) => {
    setCurrentPath(currentPath.slice(0, index + 1));
  };
  
  const handleBackClick = () => {
    if (currentPath.length > 0) {
      setCurrentPath(currentPath.slice(0, -1));
    }
  };

  const handleFolderClick = (file: UiFile) => {
    if (file.type === 'folder') {
      setCurrentPath([...currentPath, file.name]);
    } else {
      openActionSheet(file);
    }
  };

  const openActionSheet = (file: UiFile) => {
    setSelectedFile(file);
    setActionSheetOpen(true);
    setShareStatus('');
  };

  const closeActionSheet = () => {
    setActionSheetOpen(false);
  };

  const openRenameModal = (file: UiFile) => {
    setFileToRename(file);
    setNewFileName(file.name);
    setRenameError('');
    setRenameModalOpen(true);
    closeActionSheet();
  };

  const openDeleteModal = (file: UiFile) => {
    setFileToDelete(file);
    setDeleteModalOpen(true);
    closeActionSheet();
  };

  const openTargetActionModal = (file: UiFile, action: NetdiskTargetAction) => {
    setTargetAction(action);
    setTargetActionFile(file);
    closeActionSheet();
  };

  // Upload Logic (Identical to reference)
  const runUploadEntries = async (entries: PendingUploadEntry[]) => {
    if (entries.length === 0) return;
    setFilesUploadPanelOpen(true);
    uploadMeasurementsRef.current.clear();

    const batchTasks = createUploadTasks(entries);
    replaceFilesUploads(batchTasks);

    const runSingleUpload = async ({file: uploadFile, pathParts: uploadPathParts}: PendingUploadEntry, uploadTask: UploadTask) => {
      const uploadPath = toBackendPath(uploadPathParts);
      const uploadAbortController = new AbortController();
      registerFilesUploadTaskCanceler(uploadTask.id, () => uploadAbortController.abort());
      uploadMeasurementsRef.current.set(uploadTask.id, createUploadMeasurement(Date.now()));

      try {
        const updateProgress = ({loaded, total}: {loaded: number; total: number}) => {
          const snapshot = buildUploadProgressSnapshot({
            loaded, total, now: Date.now(), previous: uploadMeasurementsRef.current.get(uploadTask.id),
          });
          uploadMeasurementsRef.current.set(uploadTask.id, snapshot.measurement);
          updateFilesUploadTask(uploadTask.id, (task) => ({ ...task, progress: snapshot.progress, speed: snapshot.speed }));
        };

        let initiated: InitiateUploadResponse | null = null;
        try {
          initiated = await apiRequest<InitiateUploadResponse>('/files/upload/initiate', {
            method: 'POST', body: { path: uploadPath, filename: uploadFile.name, contentType: uploadFile.type || null, size: uploadFile.size },
          });
        } catch (e) { if (!(e instanceof ApiError && e.status === 404)) throw e; }

        let uploadedFile: FileMetadata;
        if (initiated?.direct) {
          try {
            await apiBinaryUploadRequest(initiated.uploadUrl, { method: initiated.method, headers: initiated.headers, body: uploadFile, onProgress: updateProgress, signal: uploadAbortController.signal });
            uploadedFile = await apiRequest<FileMetadata>('/files/upload/complete', { method: 'POST', signal: uploadAbortController.signal, body: { path: uploadPath, filename: uploadFile.name, storageName: initiated.storageName, contentType: uploadFile.type || null, size: uploadFile.size } });
          } catch (error) {
            if (!(error instanceof ApiError && error.isNetworkError)) throw error;
            const formData = new FormData(); formData.append('file', uploadFile);
            uploadedFile = await apiUploadRequest<FileMetadata>(`/files/upload?path=${encodeURIComponent(uploadPath)}`, { body: formData, onProgress: updateProgress, signal: uploadAbortController.signal });
          }
        } else if (initiated) {
          const formData = new FormData(); formData.append('file', uploadFile);
          uploadedFile = await apiUploadRequest<FileMetadata>(initiated.uploadUrl, { body: formData, method: initiated.method, headers: initiated.headers, onProgress: updateProgress, signal: uploadAbortController.signal });
        } else {
          const formData = new FormData(); formData.append('file', uploadFile);
          uploadedFile = await apiUploadRequest<FileMetadata>(`/files/upload?path=${encodeURIComponent(uploadPath)}`, { body: formData, onProgress: updateProgress, signal: uploadAbortController.signal });
        }

        updateFilesUploadTask(uploadTask.id, (task) => prepareUploadTaskForCompletion(task));
        await sleep(120);
        updateFilesUploadTask(uploadTask.id, (task) => completeUploadTask(task));
        return uploadedFile;
      } catch (error) {
        if (uploadAbortController.signal.aborted) { updateFilesUploadTask(uploadTask.id, (task) => cancelUploadTask(task)); return null; }
        updateFilesUploadTask(uploadTask.id, (task) => failUploadTask(task, error instanceof Error && error.message ? error.message : '上传失败'));
        return null;
      } finally {
        uploadMeasurementsRef.current.delete(uploadTask.id);
        unregisterFilesUploadTaskCanceler(uploadTask.id);
      }
    };

    if (shouldUploadEntriesSequentially(entries)) {
      let previousPromise = Promise.resolve<Array<FileMetadata | null>>([]);
      for (let i = 0; i < entries.length; i++) {
        previousPromise = previousPromise.then(async (prev) => {
          const current = await runSingleUpload(entries[i], batchTasks[i]);
          return [...prev, current];
        });
      }
      const results = await previousPromise;
      if (results.some(Boolean)) await loadCurrentPath(currentPathRef.current).catch(() => {});
    } else {
      const results = await Promise.all(entries.map((entry, index) => runSingleUpload(entry, batchTasks[index])));
      if (results.some(Boolean)) await loadCurrentPath(currentPathRef.current).catch(() => {});
    }
  };

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    setFabOpen(false);
    const files = event.target.files ? (Array.from(event.target.files) as File[]) : [];
    event.target.value = '';
    if (files.length === 0) return;

    const reservedNames = new Set<string>(currentFiles.map((file) => file.name));
    const entries: PendingUploadEntry[] = files.map((file) => {
      const preparedUpload = prepareUploadFile(file, reservedNames);
      reservedNames.add(preparedUpload.file.name);
      return { file: preparedUpload.file, pathParts: [...currentPath], source: 'file', noticeMessage: preparedUpload.noticeMessage };
    });
    await runUploadEntries(entries);
  };

  const handleFolderChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    setFabOpen(false);
    const files = event.target.files ? (Array.from(event.target.files) as File[]) : [];
    event.target.value = '';
    if (files.length === 0) return;

    const entries = prepareFolderUploadEntries(files, [...currentPath], currentFiles.map((file) => file.name));
    await runUploadEntries(entries);
  };

  const handleCreateFolder = async () => {
    setFabOpen(false);
    const folderName = window.prompt('请输入新文件夹名称');
    if (!folderName?.trim()) return;

    const normalizedFolderName = folderName.trim();
    const nextFolderName = getNextAvailableName(normalizedFolderName, new Set(currentFiles.filter(f => f.type === 'folder').map(f => f.name)));
    if (nextFolderName !== normalizedFolderName) window.alert(`名称冲突，重命名为 ${nextFolderName}`);

    const basePath = toBackendPath(currentPath).replace(/\/$/, '');
    const fullPath = `${basePath}/${nextFolderName}` || '/';

    await apiRequest('/files/mkdir', {
      method: 'POST',
      body: new URLSearchParams({ path: fullPath.startsWith('/') ? fullPath : `/${fullPath}` }),
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
    });
    await loadCurrentPath(currentPath);
  };

  const handleRename = async () => {
    if (!fileToRename || !newFileName.trim() || isRenaming) return;
    setIsRenaming(true); setRenameError('');
    try {
      const renamedFile = await apiRequest<FileMetadata>(`/files/${fileToRename.id}/rename`, {
        method: 'PATCH', body: { filename: newFileName.trim() },
      });
      const nextUiFile = toUiFile(renamedFile);
      setCurrentFiles((prev) => replaceUiFile(prev, nextUiFile));
      setSelectedFile((prev) => syncSelectedFile(prev, nextUiFile));
      setRenameModalOpen(false); setFileToRename(null); setNewFileName('');
      await loadCurrentPath(currentPath).catch(() => {});
    } catch (error) {
      setRenameError(getActionErrorMessage(error, '重命名失败'));
    } finally { setIsRenaming(false); }
  };

  const handleDelete = async () => {
    if (!fileToDelete) return;
    await apiRequest(`/files/${fileToDelete.id}`, { method: 'DELETE' });
    setCurrentFiles((prev) => removeUiFile(prev, fileToDelete.id));
    setSelectedFile((prev) => clearSelectionIfDeleted(prev, fileToDelete.id));
    setDeleteModalOpen(false); setFileToDelete(null);
    await loadCurrentPath(currentPath).catch(() => {});
  };

  const handleMoveToPath = async (path: string) => {
    if (!targetActionFile || !targetAction) return;
    if (targetAction === 'move') {
      await moveFileToNetdiskPath(targetActionFile.id, path);
      setSelectedFile((prev) => clearSelectionIfDeleted(prev, targetActionFile.id));
    } else {
      await copyFileToNetdiskPath(targetActionFile.id, path);
    }
    setTargetAction(null); setTargetActionFile(null);
    await loadCurrentPath(currentPath).catch(() => {});
  };

  const handleDownload = async (targetFile: UiFile | null = selectedFile) => {
    const actFile = targetFile || selectedFile;
    if (!actFile) return;

    if (actFile.type === 'folder') {
      const response = await apiDownload(`/files/download/${actFile.id}`);
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url; link.download = `${actFile.name}.zip`; link.click();
      window.URL.revokeObjectURL(url);
      return;
    }

    try {
      const response = await apiRequest<DownloadUrlResponse>(`/files/download/${actFile.id}/url`);
      const link = document.createElement('a'); link.href = response.url; link.download = actFile.name; link.rel = 'noreferrer'; link.target = '_blank';
      link.click(); return;
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 404)) throw error;
    }

    const response = await apiDownload(`/files/download/${actFile.id}`);
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a'); link.href = url; link.download = actFile.name; link.click();
    window.URL.revokeObjectURL(url);
  };

  const handleShare = async (targetFile: UiFile) => {
    try {
      const response = await createFileShareLink(targetFile.id);
      const shareUrl = getCurrentFileShareUrl(response.token);
      try {
        await navigator.clipboard.writeText(shareUrl);
        setShareStatus('链接已复制到剪贴板，快发送给朋友吧');
      } catch {
        setShareStatus(`可全选复制链接：${shareUrl}`);
      }
    } catch (error) {
      setShareStatus(error instanceof Error ? error.message : '分享失败');
    }
  };

  return (
    <div className={layoutClassNames.root}>
      <div className="pointer-events-none absolute inset-0 z-0">
        <div className="absolute top-[-12%] left-[-24%] h-72 w-72 rounded-full bg-[#336EFF] opacity-20 mix-blend-screen blur-[100px] animate-blob" />
        <div className="absolute top-[22%] right-[-20%] h-80 w-80 rounded-full bg-purple-600 opacity-20 mix-blend-screen blur-[100px] animate-blob animation-delay-2000" />
        <div className="absolute bottom-[-18%] left-[8%] h-80 w-80 rounded-full bg-indigo-600 opacity-20 mix-blend-screen blur-[100px] animate-blob animation-delay-4000" />
      </div>

      <input type="file" multiple ref={fileInputRef} className="hidden" onChange={handleFileChange} />
      <input type="file" ref={directoryInputRef} className="hidden" onChange={handleFolderChange} />
      
      {/* Top Header - Path navigation */}
      <div className={layoutClassNames.toolbar}>
        <div className={layoutClassNames.toolbarInner}>
          <div className="flex min-w-0 flex-1 flex-nowrap items-center text-sm overflow-x-auto custom-scrollbar whitespace-nowrap">
            {currentPath.length > 0 && (
              <button className="mr-3 p-1.5 rounded-full bg-white/5 text-slate-300 active:bg-white/10" onClick={handleBackClick}>
                <ChevronLeft className="w-4 h-4" />
              </button>
            )}
            <button className="text-slate-400 hover:text-white" onClick={() => handleBreadcrumbClick(-1)}>根目录</button>
            {currentPath.map((pathItem, index) => (
              <React.Fragment key={index}>
                <ChevronRight className="w-3 h-3 mx-1 text-slate-600 shrink-0" />
                <button onClick={() => handleBreadcrumbClick(index)} className={cn(index === currentPath.length - 1 ? 'text-white font-medium' : 'text-slate-400', 'shrink-0')}>{pathItem}</button>
              </React.Fragment>
            ))}
          </div>
          <button
            type="button"
            onClick={() => navigate(RECYCLE_BIN_ROUTE)}
            className="flex shrink-0 items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-slate-200"
          >
            <RotateCcw className="h-3.5 w-3.5" />
            回收站
          </button>
        </div>
      </div>

      {/* File List */}
      <div className={layoutClassNames.list}>
        {currentFiles.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-48 text-slate-500">
            <FolderPlus className="w-10 h-10 mb-3 opacity-20" />
            <p className="text-sm">文件夹是空的</p>
          </div>
        ) : (
          currentFiles.map((file) => (
            <div key={file.id} className="glass-panel w-full rounded-xl p-3 flex flex-row items-center gap-3 active:bg-white/5 select-none" onClick={() => handleFolderClick(file)}>
              <div className="shrink-0 p-1.5 rounded-xl bg-black/20 border border-white/5">
                <FileTypeIcon type={file.type} size="md" />
              </div>
              <div className="flex-1 min-w-0 flex flex-col justify-center">
                <span className="text-sm text-white truncate w-full block">{file.name}</span>
                <div className="flex items-center text-[10px] text-slate-400 mt-0.5 gap-2">
                  <span className={cn('px-1.5 py-0.5 rounded text-[9px] font-medium', getFileTypeTheme(file.type).badgeClassName)}>{file.typeLabel}</span>
                  <span>{file.modified}</span>
                  {file.type !== 'folder' && <span>{file.size}</span>}
                </div>
              </div>
              {file.type !== 'folder' && (
                <button className="p-2 shrink-0 text-slate-400 hover:text-white" onClick={(e) => { e.stopPropagation(); openActionSheet(file); }}>
                  <MoreVertical className="w-5 h-5" />
                </button>
              )}
            </div>
          ))
        )}
      </div>

      {/* Floating Action Button (FAB) + Menu */}
      <div className="fixed bottom-20 right-6 z-30 flex flex-col items-end gap-3 pointer-events-none">
        <AnimatePresence>
          {fabOpen && (
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 20 }} className="flex flex-col gap-3 pointer-events-auto items-end mr-1">
              <button onClick={() => { fileInputRef.current?.click(); setFabOpen(false); }} className="flex items-center gap-2 px-4 py-2.5 rounded-full bg-blue-500 text-white shadow-lg active:scale-95 text-sm font-medium">
                <Upload className="w-4 h-4"/> 上传文件
              </button>
              <button onClick={() => { directoryInputRef.current?.click(); setFabOpen(false); }} className="flex items-center gap-2 px-4 py-2.5 rounded-full bg-emerald-500 text-white shadow-lg active:scale-95 text-sm font-medium">
                <FolderPlus className="w-4 h-4"/> 上传文件夹
              </button>
              <button onClick={handleCreateFolder} className="flex items-center gap-2 px-4 py-2.5 rounded-full bg-purple-500 text-white shadow-lg active:scale-95 text-sm font-medium">
                <Plus className="w-4 h-4"/> 新建文件夹
              </button>
            </motion.div>
          )}
        </AnimatePresence>
        <button onClick={() => setFabOpen(!fabOpen)} className={cn("pointer-events-auto flex items-center justify-center w-14 h-14 rounded-full shadow-2xl transition-transform active:scale-95", fabOpen ? "bg-[#0f172a] border border-white/10 rotate-45" : "bg-[#336EFF]")}>
          <Plus className="w-6 h-6 text-white" />
        </button>
      </div>

      {/* FAB Backdrop */}
      <AnimatePresence>
        {fabOpen && <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-20 bg-black/40 backdrop-blur-sm" onClick={() => setFabOpen(false)} />}
      </AnimatePresence>

      {/* Action Sheet */}
      <AnimatePresence>
        {actionSheetOpen && selectedFile && (
          <div className="fixed inset-0 z-50 flex items-end">
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={closeActionSheet} />
            <motion.div initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }} transition={{ type: "spring", damping: 25, stiffness: 200 }} className="relative w-full bg-[#0f172a] rounded-t-3xl border-t border-white/10 pt-4 pb-8 px-4 flex flex-col z-10 glass-panel">
              <div className="w-12 h-1 bg-white/20 rounded-full mx-auto mb-4" />
              <div className="flex border-b border-white/10 pb-4 mb-4 gap-4 items-center px-2">
                 <FileTypeIcon type={selectedFile.type} size="md" />
                 <div className="min-w-0">
                    <p className="text-sm font-semibold truncate text-white">{selectedFile.name}</p>
                    <p className="text-xs text-slate-400 mt-1">{selectedFile.size} • {selectedFile.modified}</p>
                 </div>
              </div>
              <div className="grid grid-cols-4 gap-2 mb-4 px-2">
                 <ActionButton icon={Download} label="下载" onClick={() => { handleDownload(); closeActionSheet(); }} color="text-amber-400" />
                 <ActionButton icon={Share2} label="分享" onClick={() => handleShare(selectedFile)} color="text-emerald-400" />
                 <ActionButton icon={Copy} label="复制" onClick={() => openTargetActionModal(selectedFile, 'copy')} color="text-blue-400" />
                 <ActionButton icon={Folder} label="移动" onClick={() => openTargetActionModal(selectedFile, 'move')} color="text-indigo-400" />
                 <ActionButton icon={Edit2} label="重命名" onClick={() => openRenameModal(selectedFile)} color="text-slate-300" />
                 <ActionButton icon={Trash2} label="删除" onClick={() => openDeleteModal(selectedFile)} color="text-red-400" />
              </div>
              {shareStatus && <div className="mx-2 mt-2 p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-[10px] text-emerald-400 break-all">{shareStatus}</div>}
              <Button variant="ghost" onClick={closeActionSheet} className="mt-4 text-slate-400 py-6 text-sm">取消</Button>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Target Action Modal */}
      {targetAction && (
        <NetdiskPathPickerModal
          isOpen
          title={targetAction === 'move' ? '移动到' : '复制到'}
          confirmLabel={targetAction === 'move' ? '移动至此' : '复制至此'}
          onClose={() => setTargetAction(null)}
          onConfirm={(path) => void handleMoveToPath(path)}
        />
      )}

      {/* Rename Modal */}
      <AnimatePresence>
        {renameModalOpen && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
             <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={() => setRenameModalOpen(false)} />
             <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} className="relative w-full max-w-sm glass-panel bg-[#0f172a] border border-white/10 rounded-2xl p-5 z-10 shadow-2xl">
                <h3 className="text-lg font-bold text-white mb-4">重命名文件</h3>
                <Input value={newFileName} onChange={(e) => setNewFileName(e.target.value)} className="bg-black/20 text-white mb-2 h-12" placeholder="请输入新名称" />
                {renameError && <p className="text-xs text-red-400 mb-4">{renameError}</p>}
                <div className="flex gap-3 mt-6">
                  <Button variant="outline" className="flex-1 bg-white/5 border-white/10 text-white" onClick={() => setRenameModalOpen(false)}>取消</Button>
                  <Button className="flex-1 bg-[#336EFF] hover:bg-[#2958cc] text-white" onClick={handleRename} disabled={isRenaming}>{isRenaming ? '保存中' : '保存'}</Button>
                </div>
             </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Delete Modal */}
      <AnimatePresence>
        {deleteModalOpen && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
             <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={() => setDeleteModalOpen(false)} />
             <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} className="relative w-full max-w-sm glass-panel bg-[#0f172a] border border-white/10 rounded-2xl p-5 z-10 shadow-2xl">
                <h3 className="text-lg font-bold text-white mb-2 flex items-center gap-2"><Trash2 className="text-red-400 w-5 h-5"/>确认删除</h3>
                <p className="text-sm text-slate-300 mb-6 mt-3">确定要将 <span className="text-white font-medium break-all">{fileToDelete?.name}</span> 移入回收站吗？文件会保留 {RECYCLE_BIN_RETENTION_DAYS} 天，期间可以恢复。</p>
                <div className="flex gap-3">
                  <Button variant="outline" className="flex-1 bg-white/5 border-white/10 text-white" onClick={() => setDeleteModalOpen(false)}>取消</Button>
                  <Button className="flex-1 bg-red-500 text-white hover:bg-red-600" onClick={handleDelete}>移入回收站</Button>
                </div>
             </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}

function ActionButton({ icon: Icon, label, color, onClick }: any) {
  return (
    <div className="flex flex-col items-center gap-2 p-2 hover:bg-white/5 rounded-xl transition-colors active:bg-white/10" onClick={onClick}>
       <div className={cn("p-3 rounded-full bg-black/20 border border-white/5 shadow-inner", color)}>
          <Icon className="w-5 h-5" />
       </div>
       <span className="text-xs text-slate-300">{label}</span>
    </div>
  );
}
