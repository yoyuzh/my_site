import React, { useRef, useState, useEffect } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Edit2, X, Trash2 } from 'lucide-react';
import { Input } from '@/src/components/ui/input';
import { Button } from '@/src/components/ui/button';
import { NetdiskPathPickerModal } from '@/src/components/ui/NetdiskPathPickerModal';
import { ApiError, apiDownload, apiRequest } from '@/src/lib/api';
import { copyFileToNetdiskPath } from '@/src/lib/file-copy';
import { moveFileToNetdiskPath } from '@/src/lib/file-move';
import { createFileShareLink, getCurrentFileShareUrl } from '@/src/lib/file-share';
import { uploadFileToNetdiskViaSession } from '@/src/lib/upload-session';
import { getNextAvailableName, getActionErrorMessage, removeUiFile, replaceUiFile, syncSelectedFile, clearSelectionIfDeleted } from '../files-state';
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
} from '../files-upload';
import {
  registerFilesUploadTaskCanceler,
  replaceFilesUploads,
  setFilesUploadPanelOpen,
  unregisterFilesUploadTaskCanceler,
  updateFilesUploadTask,
} from '../files-upload-store';
import { buildDirectoryTree } from '../files-tree';
import { RECYCLE_BIN_RETENTION_DAYS } from '../recycle-bin-state';
import type { FileMetadata } from '@/src/lib/types';
import { toUiFile, type UiFile } from './file-types';

import { useFilesDirectoryState, splitBackendPath, toBackendPath } from './useFilesDirectoryState';
import { useFilesSearchState } from './useFilesSearchState';
import { useBackgroundTasksState } from './useBackgroundTasksState';
import { useFilesOverlayState } from './useFilesOverlayState';

import { FilesDirectoryRail } from './FilesDirectoryRail';
import { FilesMainPane } from './FilesMainPane';
import { FilesInspector } from './FilesInspector';
import { FilesTaskPanel } from './FilesTaskPanel';
import { FilesToolbar } from './FilesToolbar';
import { AppPageShell } from '@/src/components/ui/AppPageShell';

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function FilesPage() {
  const directoryState = useFilesDirectoryState();
  const searchState = useFilesSearchState();
  const tasksState = useBackgroundTasksState();
  const overlayState = useFilesOverlayState();

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const directoryInputRef = useRef<HTMLInputElement | null>(null);
  const uploadMeasurementsRef = useRef(new Map<string, UploadMeasurement>());
  const [viewMode, setViewMode] = useState<'list' | 'grid'>('list');
  const [shareStatus, setShareStatus] = useState('');
  const [selectedFile, setSelectedFile] = useState<UiFile | null>(null);

  useEffect(() => {
    if (directoryInputRef.current) {
      directoryInputRef.current.setAttribute('webkitdirectory', '');
      directoryInputRef.current.setAttribute('directory', '');
    }
    void tasksState.loadBackgroundTasks();
  }, [tasksState.loadBackgroundTasks]);

  const handleNavigateToPath = (pathParts: string[]) => {
    searchState.clearSearchState();
    directoryState.setCurrentPath(pathParts);
    setSelectedFile(null);
    overlayState.setActiveDropdown(null);
  };

  const directoryTree = buildDirectoryTree(directoryState.directoryChildren, directoryState.currentPath, directoryState.expandedDirectories);

  const handleDownload = async (targetFile: UiFile | null = selectedFile) => {
    if (!targetFile) return;

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
      const response = await apiRequest<{url: string}>(`/files/download/${targetFile.id}/url`);
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

  const runUploadEntries = async (entries: PendingUploadEntry[]) => {
    if (entries.length === 0) return;

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
      registerFilesUploadTaskCanceler(uploadTask.id, () => uploadAbortController.abort());
      uploadMeasurementsRef.current.set(uploadTask.id, createUploadMeasurement(startedAt));

      try {
        const updateProgress = ({loaded, total}: {loaded: number; total: number}) => {
          const snapshot = buildUploadProgressSnapshot({
            loaded, total, now: Date.now(), previous: uploadMeasurementsRef.current.get(uploadTask.id),
          });
          uploadMeasurementsRef.current.set(uploadTask.id, snapshot.measurement);
          updateFilesUploadTask(uploadTask.id, (task) => ({
            ...task, progress: snapshot.progress, speed: snapshot.speed,
          }));
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
        if (uploadAbortController.signal.aborted) {
          updateFilesUploadTask(uploadTask.id, (task) => cancelUploadTask(task));
          return null;
        }
        updateFilesUploadTask(uploadTask.id, (task) => failUploadTask(task, error instanceof Error && error.message ? error.message : '上传失败没查到原因'));
        return null;
      } finally {
        uploadMeasurementsRef.current.delete(uploadTask.id);
        unregisterFilesUploadTaskCanceler(uploadTask.id);
      }
    };

    const results = shouldUploadEntriesSequentially(entries)
      ? await entries.reduce<Promise<Array<Awaited<ReturnType<typeof runSingleUpload>>>>>(async (prev, entry, i) => [...await prev, await runSingleUpload(entry, batchTasks[i])], Promise.resolve([]))
      : await Promise.all(entries.map((entry, index) => runSingleUpload(entry, batchTasks[index])));

    if (results.some(Boolean)) {
      await directoryState.loadCurrentPath(directoryState.currentPath).catch(() => undefined);
    }
  };

  const handleRename = async () => {
    if (!overlayState.fileToRename || !overlayState.newFileName.trim() || overlayState.isRenaming) return;
    overlayState.setIsRenaming(true);
    overlayState.setRenameError('');

    try {
      const renamedFile = await apiRequest<FileMetadata>(`/files/${overlayState.fileToRename.id}/rename`, {
        method: 'PATCH', body: { filename: overlayState.newFileName.trim() },
      });
      const nextUiFile = toUiFile(renamedFile);
      directoryState.setCurrentFiles((prev) => replaceUiFile(prev, nextUiFile));
      setSelectedFile((prev) => syncSelectedFile(prev, nextUiFile));
      overlayState.setRenameModalOpen(false);
      overlayState.setFileToRename(null);
      overlayState.setNewFileName('');
      await directoryState.loadCurrentPath(directoryState.currentPath).catch(() => undefined);
    } catch (error) {
      overlayState.setRenameError(getActionErrorMessage(error, '重命名失败'));
    } finally {
      overlayState.setIsRenaming(false);
    }
  };

  const handleDelete = async () => {
    if (!overlayState.fileToDelete) return;
    await apiRequest(`/files/${overlayState.fileToDelete.id}`, { method: 'DELETE' });
    directoryState.setCurrentFiles((prev) => removeUiFile(prev, overlayState.fileToDelete!.id));
    setSelectedFile((prev) => clearSelectionIfDeleted(prev, overlayState.fileToDelete!.id));
    overlayState.setDeleteModalOpen(false);
    overlayState.setFileToDelete(null);
    await directoryState.loadCurrentPath(directoryState.currentPath).catch(() => undefined);
  };

  const handleMoveToPath = async (path: string) => {
    if (!overlayState.targetActionFile || !overlayState.targetAction) return;
    if (overlayState.targetAction === 'move') {
      await moveFileToNetdiskPath(overlayState.targetActionFile.id, path);
      setSelectedFile((prev) => clearSelectionIfDeleted(prev, overlayState.targetActionFile!.id));
    } else {
      await copyFileToNetdiskPath(overlayState.targetActionFile.id, path);
    }
    overlayState.setTargetAction(null);
    overlayState.setTargetActionFile(null);
    await directoryState.loadCurrentPath(directoryState.currentPath).catch(() => undefined);
  };

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
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
    const files = event.target.files ? (Array.from(event.target.files) as File[]) : [];
    event.target.value = '';
    if (files.length === 0) return;
    const entries = prepareFolderUploadEntries(files, [...directoryState.currentPath], directoryState.currentFiles.map((f) => f.name));
    await runUploadEntries(entries);
  };

  const handleCreateFolder = async () => {
    const folderName = window.prompt('请输入新文件夹名称');
    if (!folderName?.trim()) return;
    const nextFolderName = getNextAvailableName(folderName.trim(), new Set(directoryState.currentFiles.filter((f) => f.type === 'folder').map((f) => f.name)));
    const basePath = toBackendPath(directoryState.currentPath).replace(/\/$/, '');
    const fullPath = `${basePath}/${nextFolderName}` || '/';
    await apiRequest('/files/mkdir', { method: 'POST', body: new URLSearchParams({ path: fullPath.startsWith('/') ? fullPath : `/${fullPath}` }), headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' } });
    await directoryState.loadCurrentPath(directoryState.currentPath).catch(() => undefined);
  };

  const toolbar = (
    <FilesToolbar
      currentPath={directoryState.currentPath}
      shareStatus={shareStatus}
      viewMode={viewMode}
      onNavigateToRoot={() => handleNavigateToPath([])}
      onBreadcrumbClick={(index) => handleNavigateToPath(directoryState.currentPath.slice(0, index + 1))}
      onViewModeChange={setViewMode}
      onUploadClick={() => fileInputRef.current?.click()}
      onUploadFolderClick={() => directoryInputRef.current?.click()}
      onCreateFolder={handleCreateFolder}
      fileInputRef={fileInputRef}
      directoryInputRef={directoryInputRef}
      onFileChange={handleFileChange}
      onFolderChange={handleFolderChange}
    />
  );

  const rail = (
    <div className="h-full p-4 pl-0 pr-0">
      <FilesDirectoryRail 
        currentPath={directoryState.currentPath}
        directoryTree={directoryTree}
        onNavigateToPath={handleNavigateToPath}
        onDirectoryToggle={directoryState.handleDirectoryToggle}
      />
    </div>
  );

  const inspector = (
    <div className="h-full space-y-4 p-4 pr-0">
      {selectedFile && (
        <FilesInspector 
          selectedFile={selectedFile}
          currentPath={directoryState.currentPath}
          shareStatus={shareStatus}
          backgroundTaskActionId={tasksState.backgroundTaskActionId}
          onShare={handleShare}
          onRename={overlayState.openRenameModal}
          onMove={(f) => overlayState.openTargetActionModal(f, 'move')}
          onCopy={(f) => overlayState.openTargetActionModal(f, 'copy')}
          onCreateMediaMetadataTask={() => tasksState.handleCreateMediaMetadataTask(selectedFile.id, selectedFile.name, selectedFile.type === 'folder', directoryState.currentPath)}
          onDelete={overlayState.openDeleteModal}
          onFolderDoubleClick={(f) => f.type === 'folder' && handleNavigateToPath([...directoryState.currentPath, f.name])}
          onDownload={handleDownload}
        />
      )}
      <FilesTaskPanel 
        backgroundTasks={tasksState.backgroundTasks}
        backgroundTasksLoading={tasksState.backgroundTasksLoading}
        backgroundTasksError={tasksState.backgroundTasksError}
        backgroundTaskNotice={tasksState.backgroundTaskNotice}
        backgroundTaskActionId={tasksState.backgroundTaskActionId}
        onRefresh={tasksState.loadBackgroundTasks}
        onCancelTask={tasksState.handleCancelBackgroundTask}
      />
    </div>
  );

  return (
    <AppPageShell toolbar={toolbar} rail={rail} inspector={inspector}>
      <FilesMainPane 
        currentPath={directoryState.currentPath}
        currentFiles={directoryState.currentFiles}
        shareStatus={shareStatus}
        viewMode={viewMode}
        isSearchActive={searchState.isSearchActive}
        searchQuery={searchState.searchQuery}
        searchLoading={searchState.searchLoading}
        searchError={searchState.searchError}
        searchResults={searchState.searchResults}
        selectedSearchFile={searchState.selectedSearchFile}
        selectedFile={selectedFile}
        activeDropdown={overlayState.activeDropdown}
        onViewModeChange={setViewMode}
        onSearchQueryChange={searchState.setSearchQuery}
        onSearchSubmit={searchState.handleSearchSubmit}
        onClearSearch={searchState.clearSearchState}
        onFileClick={(f) => setSelectedFile(f)}
        onFileDoubleClick={(f) => f.type === 'folder' && handleNavigateToPath([...directoryState.currentPath, f.name])}
        onSearchFileClick={(f) => searchState.setSelectedSearchFile(f)}
        onSearchFileDoubleClick={(f) => f.directory && handleNavigateToPath(splitBackendPath(f.path))}
        onToggleDropdown={(id) => overlayState.setActiveDropdown(overlayState.activeDropdown === id ? null : id)}
        onDownload={handleDownload}
        onShare={handleShare}
        onMove={(f) => overlayState.openTargetActionModal(f, 'move')}
        onCopy={(f) => overlayState.openTargetActionModal(f, 'copy')}
        onRename={overlayState.openRenameModal}
        onDelete={overlayState.openDeleteModal}
        onCloseDropdown={() => overlayState.setActiveDropdown(null)}
      />

      <AnimatePresence>
        {overlayState.renameModalOpen && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
            <motion.div initial={{ opacity: 0, scale: 0.95, y: 20 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.95, y: 20 }} className="w-full max-w-sm overflow-hidden rounded-xl border border-white/10 bg-[#0f172a] shadow-2xl">
              <div className="flex items-center justify-between border-b border-white/10 bg-white/5 p-4">
                <h3 className="flex items-center gap-2 text-lg font-semibold text-white"><Edit2 className="w-5 h-5 text-[#336EFF]" /> 重命名</h3>
                <button onClick={() => { overlayState.setRenameModalOpen(false); overlayState.setFileToRename(null); overlayState.setRenameError(''); }} className="rounded-md p-1 text-slate-400 transition-colors hover:bg-white/10 hover:text-white"><X className="w-5 h-5" /></button>
              </div>
              <div className="space-y-5 p-5">
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-300">新名称</label>
                  <Input value={overlayState.newFileName} onChange={(e) => overlayState.setNewFileName(e.target.value)} className="bg-black/20 border-white/10 text-white focus-visible:ring-[#336EFF]" autoFocus disabled={overlayState.isRenaming} onKeyDown={(e) => { if (e.key === 'Enter' && !overlayState.isRenaming) void handleRename(); }} />
                </div>
                {overlayState.renameError && <div className="rounded-xl border border-red-500/20 bg-red-500/10 p-3 text-sm text-red-400">{overlayState.renameError}</div>}
                <div className="flex justify-end gap-3 pt-2">
                  <Button variant="outline" onClick={() => { overlayState.setRenameModalOpen(false); overlayState.setFileToRename(null); overlayState.setRenameError(''); }} disabled={overlayState.isRenaming} className="border-white/10 text-slate-300 hover:bg-white/10">取消</Button>
                  <Button variant="default" onClick={() => void handleRename()} disabled={overlayState.isRenaming}>{overlayState.isRenaming ? '重命名中...' : '确定'}</Button>
                </div>
              </div>
            </motion.div>
          </div>
        )}
        {overlayState.deleteModalOpen && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
            <motion.div initial={{ opacity: 0, scale: 0.95, y: 20 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.95, y: 20 }} className="w-full max-w-sm overflow-hidden rounded-xl border border-white/10 bg-[#0f172a] shadow-2xl">
              <div className="flex items-center justify-between border-b border-white/10 bg-white/5 p-4">
                <h3 className="flex items-center gap-2 text-lg font-semibold text-white"><Trash2 className="w-5 h-5 text-red-500" /> 确认删除</h3>
                <button onClick={() => { overlayState.setDeleteModalOpen(false); overlayState.setFileToDelete(null); }} className="rounded-md p-1 text-slate-400 transition-colors hover:bg-white/10 hover:text-white"><X className="w-5 h-5" /></button>
              </div>
              <div className="space-y-5 p-5">
                <p className="text-sm leading-relaxed text-slate-300">确定要将 <span className="rounded bg-white/10 px-1 py-0.5 font-medium text-white">{overlayState.fileToDelete?.name}</span> 移入回收站吗？文件会保留 {RECYCLE_BIN_RETENTION_DAYS} 天，期间可以恢复。</p>
                <div className="flex justify-end gap-3 pt-2">
                  <Button variant="outline" onClick={() => { overlayState.setDeleteModalOpen(false); overlayState.setFileToDelete(null); }} className="border-white/10 text-slate-300 hover:bg-white/10">取消</Button>
                  <Button variant="outline" className="border-red-500/30 bg-red-500 text-white hover:bg-red-600" onClick={() => void handleDelete()}>移入回收站</Button>
                </div>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      <NetdiskPathPickerModal
        isOpen={Boolean(overlayState.targetActionFile && overlayState.targetAction)}
        title={overlayState.targetAction === 'copy' ? '选择复制目标' : '选择移动目标'}
        description={overlayState.targetAction === 'copy' ? '选择要把当前文件或文件夹复制到哪个目录。' : '选择要把当前文件或文件夹移动到哪个目录。'}
        initialPath={toBackendPath(directoryState.currentPath)}
        confirmLabel={overlayState.targetAction === 'copy' ? '复制到这里' : '移动到这里'}
        onClose={() => { overlayState.setTargetAction(null); overlayState.setTargetActionFile(null); }}
        onConfirm={handleMoveToPath}
      />
    </AppPageShell>
  );
}

export default FilesPage;
