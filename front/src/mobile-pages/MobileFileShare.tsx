import React, { useEffect, useState } from 'react';
import { CheckCircle2, DownloadCloud, Link2, Loader2, LogIn, Save, X } from 'lucide-react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

import { useAuth } from '@/src/auth/AuthProvider';
import { NetdiskPathPickerModal } from '@/src/components/ui/NetdiskPathPickerModal';
import { Button } from '@/src/components/ui/button';
import { getFileShareDetails, importSharedFile } from '@/src/lib/file-share';
import { normalizeNetdiskTargetPath } from '@/src/lib/netdisk-upload';
import type { FileMetadata, FileShareDetailsResponse } from '@/src/lib/types';
import { cn } from '@/src/lib/utils';
import { FileTypeIcon, getFileTypeTheme } from '@/src/components/ui/FileTypeIcon';
import { resolveStoredFileType } from '@/src/lib/file-type';

function formatFileSize(size: number) {
  if (size <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const unitIndex = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1);
  const value = size / 1024 ** unitIndex;
  return `${value.toFixed(value >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

export default function MobileFileShare() {
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
      setLoading(false); setError('分享链接无效'); return;
    }
    let active = true;
    setLoading(true); setError(''); setImportedFile(null);

    void getFileShareDetails(token)
      .then((res) => { if (active) setDetails(res); })
      .catch((err) => { if (active) setError(err instanceof Error ? err.message : '无法读取分享详情'); })
      .finally(() => { if (active) setLoading(false); });

    return () => { active = false; };
  }, [token]);

  async function handleImportToPath(nextPath: string) {
    setPath(normalizeNetdiskTargetPath(nextPath));
    await handleImportAtPath(nextPath);
  }

  async function handleImportAtPath(nextPath: string) {
    if (!token) return;
    setImporting(true); setError('');
    try {
      const normalizedPath = normalizeNetdiskTargetPath(nextPath);
      const savedFile = await importSharedFile(token, normalizedPath);
      setPath(normalizedPath);
      setImportedFile(savedFile);
    } catch (err) {
      setError(err instanceof Error ? err.message : '导入失败');
      throw err;
    } finally {
      setImporting(false);
    }
  }

  return (
    <div className="flex flex-col min-h-[100dvh] bg-[#07101D] text-white">
      {/* 顶部插画背景 */}
      <div className="relative pt-12 pb-8 px-6 bg-gradient-to-b from-blue-900/30 to-[#07101D] flex flex-col items-center">
         <div className="absolute top-0 right-[-10%] w-[80%] h-[150%] bg-[#336EFF] rounded-full mix-blend-screen filter blur-[80px] opacity-20" />
         <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-[#336EFF] to-cyan-400 flex items-center justify-center shadow-[0_10px_40px_rgba(51,110,255,0.3)] mb-4">
            <Link2 className="w-8 h-8 text-white" />
         </div>
         <h1 className="text-2xl font-bold tracking-tight z-10 text-center">网盘提取</h1>
         <p className="text-xs text-slate-400 text-center mt-2 max-w-[240px] z-10">
            打开分享链接，直接导入到自己网盘
         </p>
      </div>

      <div className="flex-1 px-4 pb-12 flex flex-col items-center relative z-10">
         <div className="w-full max-w-sm">
            {loading ? (
              <div className="glass-panel p-10 rounded-3xl flex flex-col items-center gap-4 text-slate-400">
                <Loader2 className="w-8 h-8 animate-spin text-blue-400" />
                <span className="text-sm">读取中...</span>
              </div>
            ) : error ? (
              <div className="glass-panel p-6 rounded-3xl bg-rose-500/5 border border-rose-500/20 flex flex-col items-center gap-3">
                 <div className="w-12 h-12 rounded-full bg-rose-500/10 flex items-center justify-center">
                    <X className="w-6 h-6 text-rose-400" />
                 </div>
                 <p className="text-sm text-rose-300 font-medium">{error}</p>
              </div>
            ) : details ? (
              <div className="space-y-4">
                 <div className="glass-panel p-5 rounded-3xl flex flex-col items-center shadow-lg border border-white/5 bg-white/[0.02]">
                    <div className="p-4 rounded-full bg-black/20 border border-white/5 mb-3 shadow-inner">
                       <FileTypeIcon type={resolveStoredFileType({ filename: details.filename, contentType: details.contentType, directory: false }).kind} size="lg" />
                    </div>
                    <h2 className="text-lg font-bold text-white text-center break-all w-full leading-tight">{details.filename}</h2>
                    <div className="flex items-center gap-2 mt-3 text-xs text-slate-400">
                       <span className="bg-white/5 px-2 py-0.5 rounded-full">{details.ownerUsername} 的分享</span>
                       <span>{formatFileSize(details.size)}</span>
                    </div>
                    <div className="text-[10px] text-slate-500 mt-2">
                       {new Date(details.createdAt).toLocaleDateString('zh-CN')}
                    </div>
                 </div>

                 {!session?.token ? (
                   <div className="glass-panel p-5 rounded-3xl bg-amber-500/5 border border-amber-400/20 text-center">
                      <p className="text-xs text-amber-200/90 mb-4">你需要登录才能保存他人分享的文件</p>
                      <Button className="w-full h-12 rounded-xl bg-gradient-to-r from-amber-500 to-orange-400 hover:from-amber-600 hover:to-orange-500 text-white shadow-lg"
                        onClick={() => navigate(`/login?next=${encodeURIComponent(location.pathname + location.search)}`)}>
                         <LogIn className="mr-2 w-4 h-4" /> 登录以保存
                      </Button>
                   </div>
                 ) : (
                   <div className="glass-panel p-5 rounded-3xl border border-white/5 bg-white/[0.02]">
                      <div className="text-center mb-4">
                         <p className="text-xs text-slate-400">将保存至网盘目录：<span className="text-emerald-400 font-medium">{path}</span></p>
                      </div>

                      {importedFile ? (
                        <div className="flex flex-col items-center gap-4 pt-2">
                           <div className="w-12 h-12 rounded-full bg-emerald-500/20 flex items-center justify-center">
                             <CheckCircle2 className="w-6 h-6 text-emerald-400" />
                           </div>
                           <p className="text-sm font-medium text-emerald-300">保存成功！</p>
                           <Button className="w-full h-12 rounded-xl bg-white/10 hover:bg-white/15 text-white mt-2" onClick={() => navigate('/files')}>
                              进入我的网盘
                           </Button>
                        </div>
                      ) : (
                        <Button className="w-full h-12 rounded-xl bg-[#336EFF] hover:bg-blue-600 text-white shadow-xl text-base"
                          disabled={importing} onClick={() => setPathPickerOpen(true)}>
                           {importing ? <><Loader2 className="w-5 h-5 mr-2 animate-spin"/>正在保存...</> : <><Save className="w-5 h-5 mr-2"/>一键保存到网盘</>}
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
        title="选择保存位置"
        description="选择你要把文件保存到哪个文件夹"
        initialPath={path}
        confirmLabel="确认保存"
        onClose={() => setPathPickerOpen(false)}
        onConfirm={handleImportToPath}
      />
    </div>
  );
}
