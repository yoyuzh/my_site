import React, { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { ChevronLeft, ChevronRight, Folder, Loader2, X } from 'lucide-react';
import { createPortal } from 'react-dom';

import { apiRequest } from '@/src/lib/api';
import { getParentNetdiskPath, joinNetdiskPath, splitNetdiskPath } from '@/src/lib/netdisk-paths';
import type { FileMetadata, PageResponse } from '@/src/lib/types';

import { Button } from './button';

interface NetdiskPathPickerModalProps {
  isOpen: boolean;
  title: string;
  description?: string;
  initialPath?: string;
  confirmLabel: string;
  confirmPathPreview?: (path: string) => string;
  onClose: () => void;
  onConfirm: (path: string) => Promise<void>;
}

export function NetdiskPathPickerModal({
  isOpen,
  title,
  description,
  initialPath = '/',
  confirmLabel,
  confirmPathPreview,
  onClose,
  onConfirm,
}: NetdiskPathPickerModalProps) {
  const [currentPath, setCurrentPath] = useState(initialPath);
  const [folders, setFolders] = useState<FileMetadata[]>([]);
  const [loading, setLoading] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    setCurrentPath(initialPath);
    setError('');
  }, [initialPath, isOpen]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    let active = true;
    setLoading(true);
    setError('');

    void apiRequest<PageResponse<FileMetadata>>(
      `/files/list?path=${encodeURIComponent(currentPath)}&page=0&size=100`,
    )
      .then((response) => {
        if (!active) {
          return;
        }
        setFolders(response.items.filter((item) => item.directory));
      })
      .catch((requestError) => {
        if (!active) {
          return;
        }
        setFolders([]);
        setError(requestError instanceof Error ? requestError.message : '读取网盘目录失败');
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [currentPath, isOpen]);

  async function handleConfirm() {
    setConfirming(true);
    setError('');

    try {
      await onConfirm(currentPath);
      onClose();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '保存目录失败');
    } finally {
      setConfirming(false);
    }
  }

  const pathSegments = splitNetdiskPath(currentPath);
  const previewPath = confirmPathPreview ? confirmPathPreview(currentPath) : currentPath;
  if (typeof document === 'undefined') {
    return null;
  }

  return createPortal(
    <AnimatePresence>
      {isOpen ? (
        <div className="fixed inset-0 z-[130] overflow-y-auto bg-black/50 p-4 backdrop-blur-sm sm:p-6">
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 20 }}
            className="mx-auto my-4 flex w-full max-w-lg flex-col overflow-hidden rounded-2xl border border-white/10 bg-[#0f172a] shadow-2xl sm:my-8 max-h-[calc(100vh-2rem)] sm:max-h-[calc(100vh-3rem)]"
          >
            <div className="flex items-center justify-between border-b border-white/10 bg-white/5 px-5 py-4">
              <div>
                <h3 className="text-lg font-semibold text-white">{title}</h3>
                {description ? <p className="mt-1 text-xs text-slate-400">{description}</p> : null}
              </div>
              <button
                type="button"
                onClick={onClose}
                className="rounded-md p-1 text-slate-400 transition-colors hover:bg-white/10 hover:text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="flex-1 space-y-4 overflow-y-auto p-5">
              <div className="rounded-xl border border-white/10 bg-black/20 p-4">
                <div className="flex items-center justify-between gap-4">
                  <div className="min-w-0">
                    <p className="text-xs font-medium uppercase tracking-wide text-slate-500">当前目录</p>
                    <div className="mt-2 flex flex-wrap items-center gap-1 text-sm text-slate-200">
                      <button
                        type="button"
                        className="rounded px-1 py-0.5 hover:bg-white/10"
                        onClick={() => setCurrentPath('/')}
                      >
                        网盘
                      </button>
                      {pathSegments.map((segment, index) => (
                        <React.Fragment key={`${segment}-${index}`}>
                          <ChevronRight className="h-3.5 w-3.5 text-slate-500" />
                          <button
                            type="button"
                            className="rounded px-1 py-0.5 hover:bg-white/10"
                            onClick={() => setCurrentPath(joinNetdiskPath(pathSegments.slice(0, index + 1)))}
                          >
                            {segment}
                          </button>
                        </React.Fragment>
                      ))}
                    </div>
                  </div>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="border-white/10 text-slate-200 hover:bg-white/10"
                    disabled={currentPath === '/'}
                    onClick={() => setCurrentPath(getParentNetdiskPath(currentPath))}
                  >
                    <ChevronLeft className="mr-1 h-4 w-4" />
                    返回上级
                  </Button>
                </div>
                <p className="mt-3 text-xs text-emerald-300">将存入: {previewPath}</p>
              </div>

              <div className="rounded-xl border border-white/10 bg-black/20">
                <div className="border-b border-white/10 px-4 py-3 text-sm font-medium text-slate-200">选择目标文件夹</div>
                <div className="max-h-72 overflow-y-auto p-3 sm:max-h-80">
                  {loading ? (
                    <div className="flex items-center justify-center gap-2 px-4 py-10 text-sm text-slate-400">
                      <Loader2 className="h-4 w-4 animate-spin" />
                      正在加载目录...
                    </div>
                  ) : folders.length === 0 ? (
                    <div className="px-4 py-10 text-center text-sm text-slate-500">这个目录下没有更多子文件夹，当前目录也可以直接使用。</div>
                  ) : (
                    <div className="space-y-2">
                      {folders.map((folder) => {
                        const nextPath = folder.path;
                        return (
                          <button
                            key={folder.id}
                            type="button"
                            className="flex w-full items-center gap-3 rounded-xl border border-white/5 bg-white/[0.03] px-4 py-3 text-left transition-colors hover:border-white/10 hover:bg-white/[0.06]"
                            onClick={() => setCurrentPath(nextPath)}
                          >
                            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#336EFF]/10">
                              <Folder className="h-4 w-4 text-[#336EFF]" />
                            </div>
                            <div className="min-w-0 flex-1">
                              <p className="truncate text-sm font-medium text-slate-100">{folder.filename}</p>
                              <p className="truncate text-xs text-slate-500">{nextPath}</p>
                            </div>
                            <ChevronRight className="h-4 w-4 text-slate-500" />
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>

              {error ? (
                <div className="rounded-xl border border-rose-500/20 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">{error}</div>
              ) : null}

              <div className="flex justify-end gap-3">
                <Button type="button" variant="outline" className="border-white/10 text-slate-300 hover:bg-white/10" onClick={onClose} disabled={confirming}>
                  取消
                </Button>
                <Button type="button" onClick={() => void handleConfirm()} disabled={confirming || loading}>
                  {confirming ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      处理中...
                    </>
                  ) : (
                    confirmLabel
                  )}
                </Button>
              </div>
            </div>
          </motion.div>
        </div>
      ) : null}
    </AnimatePresence>
    ,
    document.body,
  );
}
