import React, { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { useNavigate } from 'react-router-dom';
import { ChevronRight, ChevronLeft, RotateCcw, Plus, Upload, FolderPlus, Trash2 } from 'lucide-react';
import { NetdiskPathPickerModal } from '@/src/components/ui/NetdiskPathPickerModal';
import { Button } from '@/src/components/ui/button';
import { Input } from '@/src/components/ui/input';
import { ApiError, apiDownload, apiRequest } from '@/src/lib/api';
import { copyFileToNetdiskPath } from '@/src/lib/file-copy';
import { moveFileToNetdiskPath } from '@/src/lib/file-move';
import { createFileShareLink, getCurrentFileShareUrl } from '@/src/lib/file-share';
import { uploadFileToNetdiskViaSession } from '@/src/lib/upload-session';
import type { DownloadUrlResponse, FileMetadata } from '@/src/lib/types';
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
import { RECYCLE_BIN_RETENTION_DAYS, RECYCLE_BIN_ROUTE } from '@/src/pages/recycle-bin-state';
import { useFilesDirectoryState, splitBackendPath, toBackendPath } from '@/src/pages/files/useFilesDirectoryState';
import { toUiFile, type UiFile } from '@/src/pages/files/file-types';
import { MobileFilesList } from './MobileFilesList';
import { MobileFileActionSheet } from './MobileFileActionSheet';

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function getMobileFilesLayoutClassNames() {
  return {
    root: 'relative flex min-h-full flex-col text-white bg-transparent',
    toolbar: 'sticky top-0 z-30 flex-none px-4 py-2',
    toolbarInner: 'glass-panel flex items-center gap-3 rounded-[22px] border border-white/10 bg-[#0f172a]/72 px-3.5 py-2.5 shadow-md backdrop-blur-2xl',
    list: 'relative z-10 flex-1 px-3 pt-2 pb-4 space-y-1.5',
  };
}

export function MobileFilesPage() {
  const navigate = useNavigate();
  const directoryState = useFilesDirectoryState();
  const layoutClassNames = getMobileFilesLayoutClassNames();
  
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const directoryInputRef = useRef<HTMLInputElement | null>(null);
  const uploadMeasurementsRef = useRef(new Map());
  
  const [selectedFile, setSelectedFile] = useState<UiFile | null>(null);
  const [actionSheetOpen, setActionSheetOpen] = useState(false);
  const [renameModalOpen, setRenameModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [fileToRename, setFileToRename] = useState<UiFile | null>(null);
  const [fileToDelete, setFileToDelete] = useState<UiFile | null>(null);
  const [targetActionFile, setTargetActionFile] = useState<UiFile | null>(null);
  const [targetAction, setTargetAction] = useState<'move' | 'copy' | null>(null);
  const [newFileName, setNewFileName] = useState('');
  const [renameError, setRenameError] = useState('');
  const [isRenaming, setIsRenaming] = useState(false);
  const [shareStatus, setShareStatus] = useState('');
  const [fabOpen, setFabOpen] = useState(false);

  useEffect(() => {
    if (directoryInputRef.current) {
      directoryInputRef.current.setAttribute('webkitdirectory', '');
      directoryInputRef.current.setAttribute('directory', '');
    }
  }, []);

  const handleBreadcrumbClick = (index: number) => {
    directoryState.setCurrentPath(directoryState.currentPath.slice(0, index + 1));
  };
  
  const handleBackClick = () => {
    if (directoryState.currentPath.length > 0) {
      directoryState.setCurrentPath(directoryState.currentPath.slice(0, -1));
    }
  };

  const handleFolderClick = (file: UiFile) => {
    if (file.type === 'folder') {
      directoryState.setCurrentPath([...directoryState.currentPath, file.name]);
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

  const openTargetActionModal = (file: UiFile, action: 'move' | 'copy') => {
    setTargetAction(action);
    setTargetActionFile(file);
    closeActionSheet();
  };

  const runUploadEntries = async (entries: PendingUploadEntry[]) => {
    if (entries.length === 0) return;
    setFilesUploadPanelOpen(true);
    uploadMeasurementsRef.current.clear();

    const batchTasks = createUploadTasks(entries);
    replaceFilesUploads(batchTasks);

    const runSingleUpload = async ({file: uploadFile, pathParts: uploadPathParts}: PendingUploadEntry, uploadTask: any) => {
      const uploadPath = toBackendPath(uploadPathParts);
      const uploadAbortController = new AbortController();
      registerFilesUploadTaskCanceler(uploadTask.id, () => uploadAbortController.abort());
      uploadMeasurementsRef.current.set(uploadTask.id, createUploadMeasurement(Date.now()));

      try {
        const updateProgress = ({loaded, total}: {loaded: number; total: number}) => {
          const snapshot = buildUploadProgressSnapshot({ loaded, total, now: Date.now(), previous: uploadMeasurementsRef.current.get(uploadTask.id) });
          uploadMeasurementsRef.current.set(uploadTask.id, snapshot.measurement);
          updateFilesUploadTask(uploadTask.id, (task) => ({ ...task, progress: snapshot.progress, speed: snapshot.speed }));
        };

        const uploadedFile = await uploadFileToNetdiskViaSession(uploadFile, uploadPath, {
          onProgress: updateProgress,
          signal: uploadAbortController.signal,
        });

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
      let previousPromise = Promise.resolve<Array<Awaited<ReturnType<typeof runSingleUpload>>>>([]);
      for (let i = 0; i < entries.length; i++) {
        previousPromise = previousPromise.then(async (prev) => {
          const current = await runSingleUpload(entries[i], batchTasks[i]);
          return [...prev, current];
        });
      }
      const results = await previousPromise;
      if (results.some(Boolean)) await directoryState.loadCurrentPath(directoryState.currentPath).catch(() => {});
    } else {
      const results = await Promise.all(entries.map((entry, index) => runSingleUpload(entry, batchTasks[index])));
      if (results.some(Boolean)) await directoryState.loadCurrentPath(directoryState.currentPath).catch(() => {});
    }
  };

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    setFabOpen(false);
    const files = event.target.files ? (Array.from(event.target.files) as File[]) : [];
    event.target.value = '';
    if (files.length === 0) return;

    const reservedNames = new Set<string>(directoryState.currentFiles.map((file) => file.name));
    const entries: PendingUploadEntry[] = files.map((file) => {
      const preparedUpload = prepareUploadFile(file, reservedNames);
      reservedNames.add(preparedUpload.file.name);
      return { file: preparedUpload.file, pathParts: [...directoryState.currentPath], source: 'file', noticeMessage: preparedUpload.noticeMessage };
    });
    await runUploadEntries(entries);
  };

  const handleFolderChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    setFabOpen(false);
    const files = event.target.files ? (Array.from(event.target.files) as File[]) : [];
    event.target.value = '';
    if (files.length === 0) return;

    const entries = prepareFolderUploadEntries(files, [...directoryState.currentPath], directoryState.currentFiles.map((file) => file.name));
    await runUploadEntries(entries);
  };

  const handleCreateFolder = async () => {
    setFabOpen(false);
    const folderName = window.prompt('请输入新文件夹名称');
    if (!folderName?.trim()) return;

    const normalizedFolderName = folderName.trim();
    const nextFolderName = getNextAvailableName(normalizedFolderName, new Set(directoryState.currentFiles.filter(f => f.type === 'folder').map(f => f.name)));
    if (nextFolderName !== normalizedFolderName) window.alert(`名称冲突，重命名为 ${nextFolderName}`);

    const basePath = toBackendPath(directoryState.currentPath).replace(/\/$/, '');
    const fullPath = `${basePath}/${nextFolderName}` || '/';

    await apiRequest('/files/mkdir', {
      method: 'POST',
      body: new URLSearchParams({ path: fullPath.startsWith('/') ? fullPath : `/${fullPath}` }),
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
    });
    await directoryState.loadCurrentPath(directoryState.currentPath);
  };

  const handleRename = async () => {
    if (!fileToRename || !newFileName.trim() || isRenaming) return;
    setIsRenaming(true); setRenameError('');
    try {
      const renamedFile = await apiRequest<FileMetadata>(`/files/${fileToRename.id}/rename`, {
        method: 'PATCH', body: { filename: newFileName.trim() },
      });
      const nextUiFile = toUiFile(renamedFile);
      directoryState.setCurrentFiles((prev) => replaceUiFile(prev, nextUiFile));
      setSelectedFile((prev) => syncSelectedFile(prev, nextUiFile));
      setRenameModalOpen(false); setFileToRename(null); setNewFileName('');
      await directoryState.loadCurrentPath(directoryState.currentPath).catch(() => {});
    } catch (error) {
      setRenameError(getActionErrorMessage(error, '重命名失败'));
    } finally { setIsRenaming(false); }
  };

  const handleDelete = async () => {
    if (!fileToDelete) return;
    await apiRequest(`/files/${fileToDelete.id}`, { method: 'DELETE' });
    directoryState.setCurrentFiles((prev) => removeUiFile(prev, fileToDelete.id));
    setSelectedFile((prev) => clearSelectionIfDeleted(prev, fileToDelete.id));
    setDeleteModalOpen(false); setFileToDelete(null);
    await directoryState.loadCurrentPath(directoryState.currentPath).catch(() => {});
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
    await directoryState.loadCurrentPath(directoryState.currentPath).catch(() => {});
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
      
      <div className={layoutClassNames.toolbar}>
        <div className={layoutClassNames.toolbarInner}>
          <div className="flex min-w-0 flex-1 flex-nowrap items-center text-sm overflow-x-auto custom-scrollbar whitespace-nowrap">
            {directoryState.currentPath.length > 0 && (
              <button className="mr-3 p-1.5 rounded-full bg-white/5 text-slate-300 active:bg-white/10" onClick={handleBackClick}>
                <ChevronLeft className="w-4 h-4" />
              </button>
            )}
            <button className="text-slate-400 hover:text-white" onClick={() => handleBreadcrumbClick(-1)}>根目录</button>
            {directoryState.currentPath.map((pathItem, index) => (
              <React.Fragment key={index}>
                <ChevronRight className="w-3 h-3 mx-1 text-slate-600 shrink-0" />
                <button onClick={() => handleBreadcrumbClick(index)} className={cn(index === directoryState.currentPath.length - 1 ? 'text-white font-medium' : 'text-slate-400', 'shrink-0')}>{pathItem}</button>
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

      <div className={layoutClassNames.list}>
        <MobileFilesList currentFiles={directoryState.currentFiles} onFolderClick={handleFolderClick} onOpenActionSheet={openActionSheet} />
      </div>

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

      <AnimatePresence>
        {fabOpen && <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-20 bg-black/40 backdrop-blur-sm" onClick={() => setFabOpen(false)} />}
      </AnimatePresence>

      <MobileFileActionSheet
        isOpen={actionSheetOpen}
        selectedFile={selectedFile}
        shareStatus={shareStatus}
        onClose={closeActionSheet}
        onDownload={handleDownload}
        onShare={handleShare}
        onMove={(f) => openTargetActionModal(f, 'move')}
        onCopy={(f) => openTargetActionModal(f, 'copy')}
        onRename={openRenameModal}
        onDelete={openDeleteModal}
      />

      {targetAction && (
        <NetdiskPathPickerModal
          isOpen
          title={targetAction === 'move' ? '移动到' : '复制到'}
          confirmLabel={targetAction === 'move' ? '移动至此' : '复制至此'}
          onClose={() => setTargetAction(null)}
          onConfirm={(path) => void handleMoveToPath(path)}
        />
      )}

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

export default MobileFilesPage;
