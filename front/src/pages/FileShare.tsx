import React, { useEffect, useState } from 'react';
import { CheckCircle2, DownloadCloud, Link2, Loader2, LogIn, Save } from 'lucide-react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

import { useAuth } from '@/src/auth/AuthProvider';
import { NetdiskPathPickerModal } from '@/src/components/ui/NetdiskPathPickerModal';
import { Button } from '@/src/components/ui/button';
import { getFileShareDetails, importSharedFile } from '@/src/lib/file-share';
import { normalizeNetdiskTargetPath } from '@/src/lib/netdisk-upload';
import type { FileMetadata, FileShareDetailsResponse } from '@/src/lib/types';

function formatFileSize(size: number) {
  if (size <= 0) {
    return '0 B';
  }

  const units = ['B', 'KB', 'MB', 'GB'];
  const unitIndex = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1);
  const value = size / 1024 ** unitIndex;
  return `${value.toFixed(value >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

export default function FileShare() {
  const { token } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { session } = useAuth();

  const [details, setDetails] = useState<FileShareDetailsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [path, setPath] = useState('/下载');
  const [importing, setImporting] = useState(false);
  const [importedFile, setImportedFile] = useState<FileMetadata | null>(null);
  const [pathPickerOpen, setPathPickerOpen] = useState(false);

  useEffect(() => {
    if (!token) {
      setLoading(false);
      setError('分享链接无效');
      return;
    }

    let active = true;
    setLoading(true);
    setError('');
    setImportedFile(null);

    void getFileShareDetails(token)
      .then((response) => {
        if (!active) {
          return;
        }
        setDetails(response);
      })
      .catch((requestError) => {
        if (!active) {
          return;
        }
        setError(requestError instanceof Error ? requestError.message : '无法读取分享详情');
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [token]);

  async function handleImportToPath(nextPath: string) {
    setPath(normalizeNetdiskTargetPath(nextPath));
    await handleImportAtPath(nextPath);
  }

  async function handleImportAtPath(nextPath: string) {
    if (!token) {
      return;
    }

    setImporting(true);
    setError('');

    try {
      const normalizedPath = normalizeNetdiskTargetPath(nextPath);
      const savedFile = await importSharedFile(token, normalizedPath);
      setPath(normalizedPath);
      setImportedFile(savedFile);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '导入共享文件失败');
      throw requestError;
    } finally {
      setImporting(false);
    }
  }

  return (
    <div className="min-h-screen bg-[#07101D] px-4 py-10 text-white">
      <div className="mx-auto w-full max-w-3xl">
        <div className="mb-10 text-center">
          <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-cyan-500 via-sky-500 to-blue-500 shadow-lg shadow-cyan-500/20">
            <Link2 className="h-8 w-8 text-white" />
          </div>
          <h1 className="text-3xl font-bold">网盘分享导入</h1>
          <p className="mt-3 text-slate-400">打开分享链接后，可以把别人分享给你的文件直接导入到自己的网盘。</p>
        </div>

        <div className="rounded-3xl border border-white/10 bg-[#0f172a]/80 p-8 shadow-2xl backdrop-blur-xl">
          {loading ? (
            <div className="flex items-center justify-center gap-3 py-20 text-slate-300">
              <Loader2 className="h-5 w-5 animate-spin" />
              正在读取分享详情...
            </div>
          ) : error ? (
            <div className="rounded-2xl border border-rose-500/20 bg-rose-500/10 px-5 py-4 text-sm text-rose-200">
              {error}
            </div>
          ) : details ? (
            <div className="space-y-6">
              <div className="rounded-2xl border border-white/5 bg-black/20 p-6">
                <div className="flex items-start gap-4">
                  <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-cyan-500/10">
                    <DownloadCloud className="h-6 w-6 text-cyan-300" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <h2 className="truncate text-xl font-semibold text-white">{details.filename}</h2>
                    <p className="mt-2 text-sm text-slate-400">
                      来自 <span className="text-slate-200">{details.ownerUsername}</span> · {formatFileSize(details.size)}
                    </p>
                    <p className="mt-1 text-xs text-slate-500">
                      创建于 {new Date(details.createdAt).toLocaleString('zh-CN')}
                    </p>
                  </div>
                </div>
              </div>

              {!session?.token ? (
                <div className="rounded-2xl border border-amber-400/20 bg-amber-500/10 p-6">
                  <p className="text-sm text-amber-100">登录后才能把这个文件导入你的网盘。</p>
                  <Button
                    className="mt-4 bg-[#336EFF] hover:bg-blue-600 text-white"
                    onClick={() => navigate(`/login?next=${encodeURIComponent(location.pathname + location.search)}`)}
                  >
                    <LogIn className="mr-2 h-4 w-4" />
                    登录后继续
                  </Button>
                </div>
              ) : (
                <div className="rounded-2xl border border-white/5 bg-black/20 p-6">
                  <div className="rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-4">
                    <p className="text-sm font-medium text-slate-200">存入位置</p>
                    <p className="mt-2 text-sm text-emerald-300">{path}</p>
                    <p className="mt-1 text-xs text-slate-500">点击下方按钮后，会弹出目录选择窗口。</p>
                  </div>

                  {importedFile ? (
                    <div className="mt-5 rounded-2xl border border-emerald-500/20 bg-emerald-500/10 px-4 py-4 text-sm text-emerald-100">
                      <div className="flex items-center gap-2">
                        <CheckCircle2 className="h-5 w-5 text-emerald-300" />
                        已导入到 {importedFile.path}/{importedFile.filename}
                      </div>
                      <Button
                        variant="outline"
                        className="mt-4 border-white/10 text-slate-100 hover:bg-white/10"
                        onClick={() => navigate('/files')}
                      >
                        打开我的网盘
                      </Button>
                    </div>
                  ) : (
                    <Button
                      className="mt-5 bg-emerald-500 hover:bg-emerald-600 text-white"
                      disabled={importing}
                      onClick={() => setPathPickerOpen(true)}
                    >
                      {importing ? (
                        <>
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          导入中...
                        </>
                      ) : (
                        <>
                          <Save className="mr-2 h-4 w-4" />
                          选择位置后导入
                        </>
                      )}
                    </Button>
                  )}
                </div>
              )}
            </div>
          ) : null}
        </div>
      </div>

      <NetdiskPathPickerModal
        isOpen={pathPickerOpen}
        title="选择导入位置"
        description="选择这个分享文件要导入到你网盘中的哪个目录。"
        initialPath={path}
        confirmLabel="导入到这里"
        onClose={() => setPathPickerOpen(false)}
        onConfirm={handleImportToPath}
      />
    </div>
  );
}
