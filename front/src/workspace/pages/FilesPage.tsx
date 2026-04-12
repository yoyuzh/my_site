import { useEffect, useMemo, useRef, useState } from 'react';
import { ChevronRight, Copy, Download, FolderPlus, HardDrive, Move, RefreshCw, Search, Share2, Trash2, Upload } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'motion/react';
import { createMediaMetadataTask } from '@/src/lib/background-tasks';
import { copyFile, createDirectory, deleteFile, getDownloadUrl, moveFile, renameFile, searchFiles, type FileItem } from '@/src/lib/files';
import { formatBytes, formatDateTime } from '@/src/lib/format';
import { buildSharePublicUrl, createShare } from '@/src/lib/shares-v2';
import { uploadRuntime } from '@/src/lib/upload-runtime';
import { cn } from '@/src/lib/utils';

import { useDirectoryData } from '@/src/hooks/use-directory-data';
import { filesCache } from '@/src/lib/files-cache';

function joinPath(basePath: string, name: string) {
  if (basePath === '/') {
    return `/${name}`;
  }
  return `${basePath}/${name}`;
}

function splitPath(path: string) {
  return path.split('/').filter(Boolean);
}

function isPathExpanded(currentPath: string, candidatePath: string) {
  return currentPath === candidatePath || currentPath.startsWith(`${candidatePath}/`);
}

const container = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: {
      staggerChildren: 0.03
    }
  }
};

const itemVariants = {
  hidden: { y: 10, opacity: 0 },
  show: { y: 0, opacity: 1 }
};

export default function FilesPage() {
  const navigate = useNavigate();
  const uploadInputRef = useRef<HTMLInputElement | null>(null);
  
  // 核心路径状态
  const [path, setPath] = useState('/');
  
  // 搜索相关状态（与目录视图解耦）
  const [query, setQuery] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [searchResults, setSearchResults] = useState<FileItem[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState('');

  // 目录数据 Hook
  const { 
    items: directoryFiles, 
    loading: directoryLoading, 
    error: directoryError, 
    refresh,
    isStale 
  } = useDirectoryData(path);

  const [selectedFile, setSelectedFile] = useState<FileItem | null>(null);
  const [directoryTree, setDirectoryTree] = useState<Record<string, FileItem[]>>({});

  // 最终展示出的文件列表
  const displayFiles = isSearching ? searchResults : directoryFiles;
  const isLoading = isSearching ? searchLoading : directoryLoading;
  const error = isSearching ? searchError : directoryError;

  // 当目录数据变化时，更新左侧树
  useEffect(() => {
    if (!isSearching && directoryFiles.length > 0) {
      setDirectoryTree((current) => ({
        ...current,
        [path]: directoryFiles.filter((item) => item.directory),
      }));
    }
  }, [directoryFiles, path, isSearching]);

  // 处理搜索
  async function performSearch(val: string) {
    if (!val.trim()) {
      setIsSearching(false);
      return;
    }
    setIsSearching(true);
    setSearchLoading(true);
    setSearchError('');
    try {
      const result = await searchFiles(val.trim(), 0, 100);
      setSearchResults(result.items);
    } catch (err) {
      setSearchError(err instanceof Error ? err.message : '搜索失败');
    } finally {
      setSearchLoading(false);
    }
  }

  const breadcrumbs = useMemo(() => splitPath(path), [path]);

  function renderTreeNodes(basePath: string) {
    const directories = directoryTree[basePath] ?? [];

    return directories.map((item) => {
      const nodePath = joinPath(basePath, item.filename);
      const expanded = isPathExpanded(path, nodePath);

      return (
        <div key={item.id} className="space-y-1">
          <button
            type="button"
            onClick={() => {
              setIsSearching(false);
              setQuery('');
              setPath(nodePath);
            }}
            className={cn(
               "group flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm font-black uppercase tracking-wider transition-all",
               path === nodePath 
                ? "bg-blue-600/10 text-blue-600 dark:text-blue-400 shadow-sm border border-blue-500/20" 
                : "text-gray-700 dark:text-gray-200 hover:bg-white/30 dark:hover:bg-white/5"
            )}
          >
            <ChevronRight className={cn("h-3.5 w-3.5 opacity-40 transition-transform", expanded && "rotate-90")} />
            <span className="truncate">{item.filename}</span>
          </button>
          {expanded && directoryTree[nodePath]?.length ? (
            <div className="ml-4 border-l border-white/10 pl-2">
              {renderTreeNodes(nodePath)}
            </div>
          ) : null}
        </div>
      );
    });
  }

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex gap-6 h-full w-full p-8 overflow-hidden text-gray-900 dark:text-gray-100"
    >
      <aside className="hidden lg:flex w-72 flex-col flex-shrink-0 glass-panel-no-hover rounded-lg overflow-hidden shadow-2xl border-white/10">
        <div className="border-b border-white/10 px-6 py-6 flex items-center justify-between">
          <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-40">目录结构</h2>
          {isStale && <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" title="后台刷新中..."></span>}
        </div>
        <div className="flex-1 space-y-1.5 overflow-y-auto p-4 custom-scrollbar">
          <button
            type="button"
            onClick={() => {
              setIsSearching(false);
              setQuery('');
              setPath('/');
            }}
            className={cn(
              "flex w-full items-center gap-2 rounded-lg px-3 py-2.5 text-sm font-black uppercase tracking-wider transition-all",
              path === '/' ? "bg-blue-600/10 text-blue-600 dark:text-blue-400 shadow-sm border border-blue-500/20" : "text-gray-700 dark:text-gray-200 hover:bg-white/30 dark:hover:bg-white/5"
            )}
          >
            <HardDrive className="h-4 w-4" />
            根目录
          </button>
          {renderTreeNodes('/')}
        </div>
        <div className="border-t border-white/10 p-4">
          <button
            type="button"
            onClick={() => navigate('/recycle-bin')}
            className="flex w-full items-center gap-2 rounded-lg px-3 py-3 text-sm font-black uppercase tracking-widest text-gray-700 dark:text-gray-200 hover:text-red-500 hover:bg-red-500/5 transition-all"
          >
            <Trash2 className="h-4 w-4" />
            回收站
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col glass-panel-no-hover rounded-lg shadow-2xl overflow-hidden border-white/10">
        <div className="border-b border-white/10 bg-white/5 dark:bg-black/20">
          <div className="flex flex-col gap-6 px-8 py-6">
            <div className="flex flex-wrap items-center justify-between gap-6">
              <div className="flex flex-wrap items-center text-[11px] font-black uppercase tracking-widest">
                <button 
                  type="button" 
                  onClick={() => {
                    setIsSearching(false);
                    setQuery('');
                    setPath('/');
                  }} className="opacity-40 hover:opacity-100 transition-opacity"
                >
                  文件系统
                </button>
                {breadcrumbs.map((segment, index) => {
                  const target = `/${breadcrumbs.slice(0, index + 1).join('/')}`;
                  return (
                    <div key={target} className="flex items-center">
                      <ChevronRight className="mx-2 h-3 w-3 opacity-20" />
                      <button 
                        type="button" 
                        onClick={() => {
                          setIsSearching(false);
                          setQuery('');
                          setPath(target);
                        }} className="opacity-40 hover:opacity-100 transition-opacity"
                      >
                        {segment}
                      </button>
                    </div>
                  );
                })}
              </div>
              <div className="relative w-full max-w-sm group">
                <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 opacity-70 group-focus-within:opacity-100 text-blue-500 transition-opacity" />
                <input
                  value={query}
                  onChange={(event) => {
                    const val = event.target.value;
                    setQuery(val);
                    if (!val.trim()) {
                      setIsSearching(false);
                    }
                  }}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      void performSearch(event.currentTarget.value);
                    }
                  }}
                  placeholder="搜索文件..."
                  className="w-full rounded-lg glass-panel bg-white/20 dark:bg-black/40 py-3 pl-11 pr-5 outline-none focus:ring-4 focus:ring-blue-500/10 border-white/10 focus:border-blue-500/50 transition-all text-sm font-black uppercase tracking-widest placeholder:opacity-50"
                />
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <input
                ref={uploadInputRef}
                type="file"
                className="hidden"
                onChange={async (event) => {
                  const file = event.target.files?.[0];
                  if (!file) return;
                  try {
                    await uploadRuntime.uploadFile(file, path);
                    filesCache.invalidate(path);
                    refresh();
                  } catch (err) {

                    console.error('上传失败', err);
                  } finally {
                    event.target.value = '';
                  }
                }}
              />
              <button
                type="button"
                onClick={() => uploadInputRef.current?.click()}
                className="flex items-center gap-2 rounded-lg bg-blue-600 px-6 py-2.5 text-sm font-black uppercase tracking-widest text-white shadow-xl hover:bg-blue-500 hover:scale-[1.02] active:scale-[0.98] transition-all"
              >
                <Upload className="h-4 w-4" />
                上传文件
              </button>
              <button
                type="button"
                onClick={async () => {
                  const name = window.prompt('请输入文件夹名称');
                  if (!name) return;
                  try {
                    await createDirectory(joinPath(path, name));
                    filesCache.invalidate(path);
                    refresh();
                  } catch (err) {
                    console.error('创建失败', err);
                  }
                }}
                className="flex items-center gap-2 rounded-lg glass-panel border-white/10 px-6 py-2.5 text-sm font-black uppercase tracking-widest text-gray-700 dark:text-gray-200 hover:bg-white/40 transition-all"
              >
                <FolderPlus className="h-4 w-4" />
                新建文件夹
              </button>
              <button
                type="button"
                onClick={() => {
                  refresh();
                }}
                className="flex items-center gap-2 rounded-lg glass-panel border-white/10 px-6 py-2.5 text-sm font-black uppercase tracking-widest text-gray-700 dark:text-gray-200 hover:bg-white/40 transition-all border-white/10"
              >
                <RefreshCw className={cn("h-4 w-4", isLoading && "animate-spin")} />
                刷新
              </button>
            </div>
          </div>
        </div>

        <div className="flex min-h-0 flex-1 relative z-10">
          <div className="min-w-0 flex-1 overflow-y-auto p-8 custom-scrollbar">
            {error ? <div className="mb-6 rounded-lg bg-red-500/10 border border-red-500/20 px-6 py-4 text-xs text-red-600 dark:text-red-400 font-bold backdrop-blur-md">{error}</div> : null}
            {isLoading && !displayFiles.length ? (
              <div className="rounded-lg glass-panel border-white/10 px-4 py-24 text-center text-[10px] font-black uppercase tracking-[0.3em] opacity-40">加载中...</div>
            ) : (
              <div className="overflow-hidden rounded-lg glass-panel border-white/10 shadow-2xl relative shadow-blue-500/5">
                <table className="min-w-full divide-y divide-white/10">
                  <thead className="bg-white/10 dark:bg-black/40">
                    <tr>
                      <th className="px-8 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">名称</th>
                      <th className="px-8 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">路径</th>
                      <th className="px-8 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">大小</th>
                      <th className="px-8 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">创建时间</th>
                    </tr>
                  </thead>
                  <motion.tbody 
                    variants={container}
                    initial="hidden"
                    animate="show"
                    className="divide-y divide-white/10 dark:divide-white/5"
                  >
                    {displayFiles.map((file) => (
                      <motion.tr
                        key={file.id}
                        variants={itemVariants}
                        onClick={() => setSelectedFile(file)}
                        onDoubleClick={() => {
                          if (file.directory) {
                            setPath(joinPath(file.path, file.filename));
                          }
                        }}
                        className={cn(
                          "cursor-pointer transition-all hover:bg-white/10 dark:hover:bg-white/5 group",
                          selectedFile?.id === file.id ? "bg-white/15 dark:bg-black/40 shadow-inner" : ""
                        )}
                      >
                        <td className="px-8 py-5 text-[13px] font-black tracking-tight group-hover:text-blue-500 transition-colors uppercase">{file.filename}</td>
                        <td className="px-8 py-5 text-sm font-bold opacity-80 dark:opacity-90 tracking-tight uppercase">{file.path}</td>
                        <td className="px-8 py-5 text-[10px] font-black opacity-50 tracking-tighter">{file.directory ? '目录' : formatBytes(file.size)}</td>
                        <td className="px-8 py-5 text-sm font-bold opacity-80 dark:opacity-90 tracking-tighter uppercase">{formatDateTime(file.createdAt)}</td>
                      </motion.tr>
                    ))}
                    {displayFiles.length === 0 ? (
                      <tr>
                        <td colSpan={4} className="px-8 py-24 text-center text-sm font-black uppercase tracking-widest opacity-70">
                          {isSearching ? '没有匹配文件' : '当前目录为空'}
                        </td>
                      </tr>
                    ) : null}
                  </motion.tbody>
                </table>
              </div>
            )}
          </div>

          <AnimatePresence mode="wait">
            {selectedFile && (
              <motion.aside 
                initial={{ x: 300, opacity: 0 }}
                animate={{ x: 0, opacity: 1 }}
                exit={{ x: 300, opacity: 0 }}
                className="hidden xl:flex w-96 flex-shrink-0 flex-col glass-panel-no-hover rounded-lg overflow-hidden shadow-2xl border-white/10 m-8 ml-0"
              >
                <div className="flex-1 overflow-y-auto p-8 space-y-10 custom-scrollbar">
                  <div>
                    <div className="text-sm font-black uppercase tracking-[0.3em] opacity-70 mb-2">文件信息</div>
                    <h2 className="text-2xl font-black text-gray-900 group-hover:text-blue-500 uppercase tracking-tighter break-all">{selectedFile.filename}</h2>
                    <div className="mt-3 text-sm font-bold opacity-80 dark:opacity-90 bg-white/5 rounded px-2 py-1 inline-block uppercase tracking-tight">{selectedFile.path}</div>
                  </div>

                  <div className="grid grid-cols-2 gap-6">
                    <div>
                      <div className="text-xs font-black uppercase tracking-widest opacity-70 mb-1">类型</div>
                      <div className="text-xs font-black uppercase">{selectedFile.directory ? '目录' : selectedFile.contentType || '文件'}</div>
                    </div>
                    <div>
                      <div className="text-xs font-black uppercase tracking-widest opacity-70 mb-1">大小</div>
                      <div className="text-xs font-black">{selectedFile.directory ? '-' : formatBytes(selectedFile.size)}</div>
                    </div>
                  </div>

                  <div>
                    <div className="text-xs font-black uppercase tracking-widest opacity-70 mb-2">操作</div>
                    <div className="space-y-2">
                      <button
                        type="button"
                        onClick={async () => {
                          const result = await getDownloadUrl(selectedFile.id);
                          window.open(result.url, '_blank', 'noopener,noreferrer');
                        }}
                        className="flex w-full items-center gap-3 rounded-lg glass-panel border-white/10 px-4 py-4 text-sm font-black uppercase tracking-[0.2em] text-gray-700 dark:text-gray-200 hover:bg-blue-600 hover:text-white transition-all group"
                      >
                        <Download className="h-4 w-4 group-hover:scale-110 transition-transform" />
                        下载
                      </button>
                      <button
                        type="button"
                        onClick={async () => {
                          const result = await createShare({ fileId: selectedFile.id });
                          await navigator.clipboard.writeText(buildSharePublicUrl(result.token));
                          window.alert('分享链接已复制');
                        }}
                        className="flex w-full items-center gap-3 rounded-lg glass-panel border-white/10 px-4 py-4 text-sm font-black uppercase tracking-[0.2em] text-gray-700 dark:text-gray-200 hover:bg-white/40 transition-all border-white/10"
                      >
                        <Share2 className="h-4 w-4" />
                        创建分享
                      </button>
                      
                      <div className="grid grid-cols-2 gap-2">
                        <button
                          type="button"
                          onClick={async () => {
                            const nextName = window.prompt('请输入新名称', selectedFile.filename);
                            if (nextName) { 
                              await renameFile(selectedFile.id, nextName); 
                              filesCache.invalidate(path);
                              refresh(); 
                            }
                          }}
                          className="flex items-center justify-center gap-2 rounded-lg glass-panel border-white/10 p-4 text-xs font-black uppercase tracking-widest hover:bg-white/40 transition-all"
                        >
                          重命名
                        </button>
                        <button
                          type="button"
                          onClick={async () => {
                            const targetPath = window.prompt('请输入目标路径', selectedFile.path);
                            if (targetPath) { 
                              await moveFile(selectedFile.id, targetPath); 
                              filesCache.invalidate(path);
                              filesCache.invalidate(targetPath);
                              refresh(); 
                            }
                          }}
                          className="flex items-center justify-center gap-2 rounded-lg glass-panel border-white/10 p-4 text-xs font-black uppercase tracking-widest hover:bg-white/40 transition-all"
                        >
                          移动
                        </button>
                      </div>

                      <button
                        type="button"
                        onClick={async () => {
                          if (!window.confirm(`确认删除 ${selectedFile.filename} 吗？`)) return;
                          await deleteFile(selectedFile.id);
                          filesCache.invalidate(path);
                          refresh();
                        }}
                        className="flex w-full items-center gap-3 rounded-lg glass-panel border-white/10 px-4 py-4 text-sm font-black uppercase tracking-[0.2em] text-red-500 hover:bg-red-500 hover:text-white transition-all border-red-500/20"
                      >
                        <Trash2 className="h-4 w-4" />
                        删除
                      </button>
                    </div>
                  </div>
                </div>
              </motion.aside>
            )}
          </AnimatePresence>
        </div>
      </div>
    </motion.div>
  );
}
