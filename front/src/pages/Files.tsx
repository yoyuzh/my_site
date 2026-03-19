import React, { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  CheckCircle2,
  ChevronDown,
  Folder,
  FileText,
  Image as ImageIcon,
  Download,
  Monitor,
  ChevronRight,
  ChevronUp,
  FileUp,
  Upload,
  UploadCloud,
  Plus,
  LayoutGrid,
  List,
  MoreVertical,
  TriangleAlert,
  X,
  Edit2,
  Trash2,
} from 'lucide-react';

import { Button } from '@/src/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/src/components/ui/card';
import { Input } from '@/src/components/ui/input';
import { ApiError, apiBinaryUploadRequest, apiDownload, apiRequest, apiUploadRequest } from '@/src/lib/api';
import { readCachedValue, writeCachedValue } from '@/src/lib/cache';
import { getFilesLastPathCacheKey, getFilesListCacheKey } from '@/src/lib/page-cache';
import type { DownloadUrlResponse, FileMetadata, InitiateUploadResponse, PageResponse } from '@/src/lib/types';
import { cn } from '@/src/lib/utils';

import {
  buildUploadProgressSnapshot,
  completeUploadTask,
  createUploadTask,
  failUploadTask,
  prepareUploadFile,
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

const QUICK_ACCESS = [
  { name: '桌面', icon: Monitor, path: [] as string[] },
  { name: '下载', icon: Download, path: ['下载'] },
  { name: '文档', icon: FileText, path: ['文档'] },
  { name: '图片', icon: ImageIcon, path: ['图片'] },
];

const DIRECTORIES = [
  { name: '下载', icon: Folder },
  { name: '文档', icon: Folder },
  { name: '图片', icon: Folder },
];

function toBackendPath(pathParts: string[]) {
  return pathParts.length === 0 ? '/' : `/${pathParts.join('/')}`;
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

export default function Files() {
  const initialPath = readCachedValue<string[]>(getFilesLastPathCacheKey()) ?? [];
  const initialCachedFiles = readCachedValue<FileMetadata[]>(getFilesListCacheKey(toBackendPath(initialPath))) ?? [];
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const uploadMeasurementsRef = useRef(new Map<string, UploadMeasurement>());
  const [currentPath, setCurrentPath] = useState<string[]>(initialPath);
  const currentPathRef = useRef(currentPath);
  const [selectedFile, setSelectedFile] = useState<UiFile | null>(null);
  const [currentFiles, setCurrentFiles] = useState<UiFile[]>(initialCachedFiles.map(toUiFile));
  const [uploads, setUploads] = useState<UploadTask[]>([]);
  const [isUploadPanelOpen, setIsUploadPanelOpen] = useState(true);
  const [renameModalOpen, setRenameModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [fileToRename, setFileToRename] = useState<UiFile | null>(null);
  const [fileToDelete, setFileToDelete] = useState<UiFile | null>(null);
  const [newFileName, setNewFileName] = useState('');
  const [activeDropdown, setActiveDropdown] = useState<number | null>(null);
  const [viewMode, setViewMode] = useState<'list' | 'grid'>('grid');
  const [renameError, setRenameError] = useState('');
  const [isRenaming, setIsRenaming] = useState(false);

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
      if (!cachedFiles) {
        setCurrentFiles([]);
      }
    });
  }, [currentPath]);

  const handleSidebarClick = (pathParts: string[]) => {
    setCurrentPath(pathParts);
    setSelectedFile(null);
    setActiveDropdown(null);
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

  const handleUploadClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files ? (Array.from(event.target.files) as File[]) : [];
    event.target.value = '';

    if (files.length === 0) {
      return;
    }

    const uploadPathParts = [...currentPath];
    const uploadPath = toBackendPath(uploadPathParts);
    const reservedNames = new Set<string>(currentFiles.map((file) => file.name));
    setIsUploadPanelOpen(true);

    const uploadJobs = files.map(async (file) => {
      const preparedUpload = prepareUploadFile(file, reservedNames);
      reservedNames.add(preparedUpload.file.name);
      const uploadFile = preparedUpload.file;
      const uploadTask = createUploadTask(uploadFile, uploadPathParts, undefined, preparedUpload.noticeMessage);
      setUploads((previous) => [...previous, uploadTask]);

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
    });

    const results = await Promise.all(uploadJobs);
    if (results.some(Boolean) && toBackendPath(currentPathRef.current) === uploadPath) {
      await loadCurrentPath(uploadPathParts).catch(() => undefined);
    }
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

  const handleDownload = async () => {
    if (!selectedFile || selectedFile.type === 'folder') {
      return;
    }

    try {
      const response = await apiRequest<DownloadUrlResponse>(`/files/download/${selectedFile.id}/url`);
      const url = response.url;
      const link = document.createElement('a');
      link.href = url;
      link.download = selectedFile.name;
      link.rel = 'noreferrer';
      link.target = '_blank';
      link.click();
      return;
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 404)) {
        throw error;
      }
    }

    const response = await apiDownload(`/files/download/${selectedFile.id}`);
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = selectedFile.name;
    link.click();
    window.URL.revokeObjectURL(url);
  };

  const handleClearUploads = () => {
    uploadMeasurementsRef.current.clear();
    setUploads([]);
  };

  return (
    <div className="flex flex-col lg:flex-row gap-6 h-[calc(100vh-8rem)]">
      {/* Left Sidebar */}
      <Card className="w-full lg:w-64 shrink-0 flex flex-col h-full overflow-y-auto">
        <CardContent className="p-4 space-y-6">
          <div className="space-y-1">
            <p className="px-3 text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">快速访问</p>
            {QUICK_ACCESS.map((item) => (
              <button
                key={item.name}
                onClick={() => handleSidebarClick(item.path)}
                className={cn(
                  'w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                  currentPath.join('/') === item.path.join('/')
                    ? 'bg-[#336EFF]/20 text-[#336EFF]'
                    : 'text-slate-300 hover:text-white hover:bg-white/5'
                )}
              >
                <item.icon className={cn('w-4 h-4', currentPath.join('/') === item.path.join('/') ? 'text-[#336EFF]' : 'text-slate-400')} />
                {item.name}
              </button>
            ))}
          </div>

          <div className="space-y-1">
            <p className="px-3 text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">网盘目录</p>
            {DIRECTORIES.map((item) => (
              <button
                key={item.name}
                onClick={() => handleSidebarClick([item.name])}
                className={cn(
                  'w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                  currentPath.length === 1 && currentPath[0] === item.name
                    ? 'bg-[#336EFF]/20 text-[#336EFF]'
                    : 'text-slate-300 hover:text-white hover:bg-white/5'
                )}
              >
                <item.icon className={cn('w-4 h-4', currentPath.length === 1 && currentPath[0] === item.name ? 'text-[#336EFF]' : 'text-slate-400')} />
                {item.name}
              </button>
            ))}
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
          <Button variant="outline" className="gap-2" onClick={handleCreateFolder}>
            <Plus className="w-4 h-4" /> 新建文件夹
          </Button>
          <input ref={fileInputRef} type="file" multiple className="hidden" onChange={handleFileChange} />
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
                  <Button variant="outline" className="w-full gap-2 bg-white/5 border-white/10 hover:bg-white/10" onClick={() => openRenameModal(selectedFile)}>
                    <Edit2 className="w-4 h-4" /> 重命名
                  </Button>
                  <Button
                    variant="outline"
                    className="w-full gap-2 border-red-500/20 bg-red-500/5 text-red-400 hover:bg-red-500/10 hover:text-red-300"
                    onClick={() => openDeleteModal(selectedFile)}
                  >
                    <Trash2 className="w-4 h-4" /> 删除
                  </Button>
                </div>
                {selectedFile.type !== 'folder' && (
                  <Button variant="default" className="w-full gap-2" onClick={handleDownload}>
                    <Download className="w-4 h-4" /> 下载文件
                  </Button>
                )}
                {selectedFile.type === 'folder' && (
                  <Button variant="default" className="w-full gap-2" onClick={() => handleFolderDoubleClick(selectedFile)}>
                    打开文件夹
                  </Button>
                )}
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
  onRename,
  onDelete,
  onClose,
}: {
  file: UiFile;
  activeDropdown: number | null;
  onToggle: (fileId: number) => void;
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
