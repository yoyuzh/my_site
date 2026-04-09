import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Copy, Download, Key, ShieldCheck, Clock, Activity, FileText, Folder, ChevronRight } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { cn } from '@/src/lib/utils';
import { buildShareDownloadUrl, getShareDetails, importShare, verifySharePassword, type ShareItem } from '@/src/lib/shares-v2';
import { formatBytes, formatDateTime } from '@/src/lib/format';
import { getSession } from '@/src/lib/session';

export default function FileShare() {
  const navigate = useNavigate();
  const { token = '' } = useParams();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [password, setPassword] = useState('');
  const [share, setShare] = useState<ShareItem | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadShare() {
      setLoading(true);
      setError('');
      try {
        const result = await getShareDetails(token);
        if (!cancelled) {
          setShare(result);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : '加载分享失败');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    if (token) {
      void loadShare();
    }

    return () => {
      cancelled = true;
    };
  }, [token]);

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-black">
        <div className="flex flex-col items-center gap-4">
          <div className="h-1 w-48 bg-white/5 rounded-full overflow-hidden">
            <motion.div 
              initial={{ x: '-100%' }}
              animate={{ x: '100%' }}
              transition={{ repeat: Infinity, duration: 1, ease: 'linear' }}
              className="h-full w-full bg-blue-500 shadow-[0_0_15px_rgba(59,130,246,0.5)]"
            />
          </div>
          <span className="text-[10px] font-black tracking-[0.3em] uppercase opacity-20">正在校验分享链接</span>
        </div>
      </div>
    );
  }

  if (!share) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-black p-4">
        <div className="glass-panel-no-hover p-10 rounded-lg border border-red-500/20 max-w-md text-center">
          <div className="text-red-500 font-black text-[10px] tracking-[0.3em] uppercase mb-4">访问失败</div>
          <div className="text-gray-900 dark:text-white font-black tracking-tight">{error || '分享不存在'}</div>
        </div>
      </div>
    );
  }

  const needsPassword = share.passwordRequired && !share.passwordVerified;

  return (
    <div className="flex min-h-screen items-center justify-center bg-aurora px-4 py-12 text-gray-900 dark:text-gray-100 transition-colors overflow-hidden">
      <AnimatePresence mode="wait">
        <motion.div 
          key={needsPassword ? 'auth' : 'content'}
          initial={{ opacity: 0, y: 20, scale: 0.98 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: -20, scale: 0.98 }}
          className="w-full max-w-xl glass-panel-no-hover rounded-lg p-12 shadow-3xl border border-white/10 relative overflow-hidden"
        >
          {/* Decorative scanner line */}
          <div className="absolute top-0 left-0 w-full h-[1px] bg-gradient-to-r from-transparent via-blue-500 to-transparent opacity-30 animate-scan"></div>
          
          <div className="relative z-10">
            <header className="mb-12 text-center">
              <div className="inline-flex items-center gap-3 text-[9px] font-black uppercase tracking-[0.4em] mb-6 px-4 py-2 rounded-lg bg-blue-500/10 text-blue-500 border border-blue-500/20 shadow-inner">
                <ShieldCheck className="h-3.5 w-3.5" />
                安全分享
              </div>
              <h1 className="break-all text-4xl font-black tracking-tight mb-4 text-gray-900 dark:text-white drop-shadow-2xl">{share.shareName || share.file.filename}</h1>
              <div className="flex flex-wrap items-center justify-center gap-3">
                <span className="text-[10px] font-black uppercase tracking-widest bg-white/5 px-3 py-1.5 rounded-lg border border-white/5 opacity-60">
                   分享者：{share.ownerUsername}
                </span>
                <span className="text-[10px] font-black uppercase tracking-widest bg-blue-500/20 px-3 py-1.5 rounded-lg border border-blue-500/20 text-blue-400">
                   {share.file.directory ? '目录' : `文件大小 / ${formatBytes(share.file.size)}`}
                </span>
              </div>
            </header>

            {error ? (
              <motion.div 
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                className="mb-10 rounded-lg bg-red-500/10 border border-red-500/20 px-8 py-5 text-xs text-red-500 font-bold uppercase tracking-widest backdrop-blur-md"
              >
                错误：{error}
              </motion.div>
            ) : null}

            {needsPassword ? (
              <form
                className="space-y-8"
                onSubmit={async (event) => {
                  event.preventDefault();
                  setError('');
                  try {
                    const verified = await verifySharePassword(token, password);
                    setShare(verified);
                  } catch (err) {
                    setError(err instanceof Error ? err.message : '校验密码失败');
                  }
                }}
              >
                <div className="space-y-3">
                  <label className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30 ml-2">访问密码</label>
                  <div className="relative">
                    <Key className="pointer-events-none absolute left-5 top-1/2 h-5 w-5 -translate-y-1/2 text-blue-500 opacity-60" />
                    <input
                      type="password"
                      value={password}
                      onChange={(event) => setPassword(event.target.value)}
                      placeholder="请输入密码"
                      className="w-full rounded-lg glass-panel bg-white/5 py-6 pl-14 pr-6 outline-none border border-white/10 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10 transition-all font-black tracking-[0.5em] text-lg placeholder:tracking-widest placeholder:opacity-10 text-gray-900 dark:text-white"
                      required
                    />
                  </div>
                </div>
                <button 
                  type="submit" 
                  className="w-full rounded-lg bg-blue-600 py-6 text-[12px] font-black uppercase tracking-[0.3em] text-white shadow-[0_10px_30px_rgba(37,99,235,0.3)] hover:bg-blue-500 hover:scale-[1.01] active:scale-[0.99] transition-all flex items-center justify-center gap-3"
                >
                  验证密码
                  <ChevronRight className="h-4 w-4" />
                </button>
              </form>
            ) : (
              <div className="space-y-10">
                <div className="grid grid-cols-1 gap-1 rounded-lg bg-black/40 p-8 border border-white/10 shadow-inner">
                  <div className="flex justify-between items-center text-[10px] font-black uppercase tracking-widest py-3">
                    <span className="opacity-30 flex items-center gap-2"><Clock className="h-3 w-3" /> 创建时间</span>
                    <span className="text-gray-700 dark:text-white/80">{formatDateTime(share.createdAt)}</span>
                  </div>
                  <div className="h-[1px] w-full bg-white/5"></div>
                  <div className="flex justify-between items-center text-[10px] font-black uppercase tracking-widest py-3">
                    <span className="opacity-30 flex items-center gap-2"><Activity className="h-3 w-3" /> 有效期</span>
                    <span className={cn("font-black", share.expiresAt ? "text-amber-500/80" : "text-green-500/80")}>
                      {share.expiresAt ? `截止：${formatDateTime(share.expiresAt)}` : '永久有效'}
                    </span>
                  </div>
                  <div className="h-[1px] w-full bg-white/5"></div>
                  <div className="flex justify-between items-center text-[10px] font-black uppercase tracking-widest py-3">
                    <span className="opacity-30 flex items-center gap-2"><Download className="h-3 w-3" /> 下载统计</span>
                    <span className="text-gray-700 dark:text-white/80">
                      {share.downloadCount} 次
                      {share.maxDownloads ? ` / 上限 ${share.maxDownloads}` : ''}
                    </span>
                  </div>
                </div>

                <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
                  {share.allowDownload ? (
                    <button
                      type="button"
                      onClick={() => window.open(buildShareDownloadUrl(token, password || undefined), '_blank', 'noopener,noreferrer')}
                      className="flex items-center justify-center gap-4 rounded-lg bg-blue-600 py-6 text-[11px] font-black uppercase tracking-[0.2em] text-white shadow-[0_10px_30px_rgba(37,99,235,0.3)] hover:bg-blue-500 hover:scale-[1.02] active:scale-[0.98] transition-all group"
                    >
                      <Download className="h-5 w-5 group-hover:translate-y-1 transition-transform" />
                      下载文件
                    </button>
                  ) : null}

                  {share.allowImport ? (
                    <button
                      type="button"
                      onClick={async () => {
                        if (!getSession()) {
                          navigate('/login');
                          return;
                        }
                        const path = window.prompt('请输入保存目录：', '/') || '/';
                        try {
                          await importShare(token, path, password || undefined);
                          window.alert('已导入到网盘');
                        } catch (err) {
                          setError(err instanceof Error ? err.message : '保存失败');
                        }
                      }}
                      className="flex items-center justify-center gap-4 rounded-lg glass-panel py-6 text-[11px] font-black uppercase tracking-[0.2em] hover:bg-white/10 border border-white/10 hover:scale-[1.02] active:scale-[0.98] transition-all group"
                    >
                      <Copy className="h-5 w-5 group-hover:scale-110 transition-transform" />
                      导入网盘
                    </button>
                  ) : null}
                </div>
              </div>
            )}
          </div>
          
          {/* Version/System stamp */}
          <div className="mt-12 pt-8 border-t border-white/5 flex items-center justify-between opacity-20 text-[8px] font-black uppercase tracking-[0.4em]">
            <span>分享协议 v4.0.21</span>
            <span>加密链路::TC-99</span>
          </div>
        </motion.div>
      </AnimatePresence>
    </div>
  );
}
