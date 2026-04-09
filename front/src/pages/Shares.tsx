import { useEffect, useState } from 'react';
import { ExternalLink, Key, Trash2 } from 'lucide-react';
import { motion } from 'motion/react';
import { buildSharePublicUrl, deleteShare, getMyShares, type ShareItem } from '@/src/lib/shares-v2';
import { formatDateTime } from '@/src/lib/format';
import { cn } from '@/src/lib/utils';

const container = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: {
      staggerChildren: 0.05
    }
  }
};

const itemVariants = {
  hidden: { y: 10, opacity: 0 },
  show: { y: 0, opacity: 1 }
};

export default function Shares() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [shares, setShares] = useState<ShareItem[]>([]);

  async function loadShares() {
    setError('');
    try {
      const result = await getMyShares(0, 100);
      setShares(result.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载分享失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadShares();
  }, []);

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col p-8 text-gray-900 dark:text-gray-100 overflow-y-auto"
    >
      <div className="flex items-center justify-between mb-10">
        <div>
          <h1 className="text-4xl font-black tracking-tight animate-text-reveal">公开分享</h1>
          <p className="mt-3 text-sm font-black uppercase tracking-[0.2em] opacity-70">外链映射 / 公开访问域</p>
        </div>
      </div>

      {error ? <div className="mb-8 rounded-lg bg-red-500/10 border border-red-500/20 px-6 py-4 text-xs text-red-600 dark:text-red-400 font-bold backdrop-blur-md">{error}</div> : null}

      <div className="flex-1 min-h-0">
        {loading ? (
          <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-sm font-black uppercase tracking-widest opacity-70">正在读取分享列表...</div>
        ) : shares.length === 0 ? (
          <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-sm font-black uppercase tracking-widest opacity-70">暂无分享</div>
        ) : (
          <div className="glass-panel-no-hover rounded-lg overflow-hidden shadow-2xl border-white/10">
            <table className="min-w-full divide-y divide-white/10">
              <thead className="bg-white/10 dark:bg-black/40">
                <tr>
                  <th className="px-8 py-5 text-left text-xs font-black uppercase tracking-[0.2em] opacity-70">分享名称</th>
                  <th className="px-8 py-5 text-left text-xs font-black uppercase tracking-[0.2em] opacity-70">权限</th>
                  <th className="px-8 py-5 text-left text-xs font-black uppercase tracking-[0.2em] opacity-70">过期时间</th>
                  <th className="px-8 py-5 text-left text-xs font-black uppercase tracking-[0.2em] opacity-70">统计</th>
                  <th className="px-8 py-5 text-right text-xs font-black uppercase tracking-[0.2em] opacity-70">操作</th>
                </tr>
              </thead>
              <motion.tbody 
                variants={container}
                initial="hidden"
                animate="show"
                className="divide-y divide-white/10 dark:divide-white/5"
              >
                {shares.map((share) => (
                  <motion.tr key={share.id} variants={itemVariants} className="hover:bg-white/10 dark:hover:bg-white/5 transition-colors group">
                    <td className="px-8 py-5">
                      <div className="font-black text-[13px] tracking-tight uppercase">{share.shareName || share.file.filename}</div>
                      <div className="mt-1 text-sm opacity-80 dark:opacity-90 font-bold uppercase tracking-tighter truncate max-w-[200px]">{share.file.path}</div>
                    </td>
                    <td className="px-8 py-5 text-xs">
                      <div className="flex flex-wrap items-center gap-2">
                        {share.passwordRequired ? (
                          <span className="flex items-center gap-1.5 px-2 py-0.5 rounded-sm bg-amber-500/10 text-amber-600 dark:text-amber-400 text-[8px] font-black border border-amber-500/20 uppercase tracking-widest">
                            <Key className="h-2.5 w-2.5" /> 需密码
                          </span>
                        ) : null}
                        <span className="px-2 py-0.5 rounded-sm bg-blue-500/10 text-blue-600 dark:text-blue-400 text-[8px] font-black border border-blue-500/20 uppercase tracking-widest">
                          {share.allowDownload ? '可下载' : '仅查看'}
                        </span>
                        <span className="px-2 py-0.5 rounded-sm bg-purple-500/10 text-purple-600 dark:text-purple-400 text-[8px] font-black border border-purple-500/20 uppercase tracking-widest">
                          {share.allowImport ? '可导入' : '受保护'}
                        </span>
                      </div>
                    </td>
                    <td className="px-8 py-5 text-sm font-bold opacity-80 dark:opacity-90 tracking-tighter uppercase">{share.expiresAt ? formatDateTime(share.expiresAt) : '永久有效'}</td>
                    <td className="px-8 py-5 text-sm font-black tracking-tighter uppercase">
                      <div className="text-blue-500">DL::{share.downloadCount}</div>
                      <div className="opacity-80 dark:opacity-90">VW::{share.viewCount}</div>
                    </td>
                    <td className="px-8 py-5 text-right">
                      <div className="flex justify-end gap-2.5">
                        <button
                          type="button"
                          onClick={() => window.open(buildSharePublicUrl(share.token), '_blank', 'noopener,noreferrer')}
                          className="p-2.5 rounded-lg glass-panel hover:bg-blue-600 hover:text-white text-blue-500 transition-all border-white/10 shadow-sm"
                          title="打开链接"
                        >
                          <ExternalLink className="h-4 w-4" />
                        </button>
                        <button
                          type="button"
                          onClick={async () => {
                            if (!window.confirm('确认删除这个分享吗？')) return;
                            await deleteShare(share.id);
                            await loadShares();
                          }}
                          className="p-2.5 rounded-lg glass-panel hover:bg-red-500 hover:text-white text-red-500 transition-all border-white/10 shadow-sm"
                          title="删除分享"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </td>
                  </motion.tr>
                ))}
              </motion.tbody>
            </table>
          </div>
        )}
      </div>
    </motion.div>
  );
}
