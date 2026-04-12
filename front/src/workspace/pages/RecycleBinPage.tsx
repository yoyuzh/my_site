import { useEffect, useState } from 'react';
import { RotateCcw } from 'lucide-react';
import { motion } from 'motion/react';
import { formatBytes, formatDateTime } from '@/src/lib/format';
import { listRecycleBin, restoreRecycleBinItem, type RecycleBinItem } from '@/src/lib/files';
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

export default function RecycleBin() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [items, setItems] = useState<RecycleBinItem[]>([]);

  async function loadItems() {
    setError('');
    try {
      const result = await listRecycleBin(0, 100);
      setItems(result.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载回收站失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadItems();
  }, []);

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col p-8 text-gray-900 dark:text-gray-100 overflow-y-auto"
    >
      <div className="mb-10">
        <h1 className="text-4xl font-black tracking-tight animate-text-reveal">回收站</h1>
        <p className="mt-3 text-sm font-black uppercase tracking-[0.2em] opacity-70">延迟删除 / 待过期清理</p>
      </div>

      {error ? <div className="mb-8 rounded-lg bg-red-500/10 border border-red-500/20 px-6 py-4 text-xs text-red-600 dark:text-red-400 font-bold backdrop-blur-md">{error}</div> : null}

      <div className="flex-1 min-h-0">
        {loading ? (
          <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-sm font-black uppercase tracking-widest opacity-70">正在扫描回收站...</div>
        ) : items.length === 0 ? (
          <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-sm font-black uppercase tracking-widest opacity-70">回收站为空</div>
        ) : (
          <div className="glass-panel-no-hover rounded-lg overflow-hidden shadow-2xl border-white/10">
            <table className="min-w-full divide-y divide-white/10">
              <thead className="bg-white/10 dark:bg-black/40">
                <tr>
                  <th className="px-8 py-5 text-left text-xs font-black uppercase tracking-[0.2em] opacity-70">文件</th>
                  <th className="px-8 py-5 text-left text-xs font-black uppercase tracking-[0.2em] opacity-70">原路径</th>
                  <th className="px-8 py-5 text-left text-xs font-black uppercase tracking-[0.2em] opacity-70">大小</th>
                  <th className="px-8 py-5 text-left text-xs font-black uppercase tracking-[0.2em] opacity-70">删除时间</th>
                  <th className="px-8 py-5 text-left text-xs font-black uppercase tracking-[0.2em] opacity-70">过期时间</th>
                  <th className="px-8 py-5 text-right text-xs font-black uppercase tracking-[0.2em] opacity-70">操作</th>
                </tr>
              </thead>
              <motion.tbody 
                variants={container}
                initial="hidden"
                animate="show"
                className="divide-y divide-white/10 dark:divide-white/5"
              >
                {items.map((item) => (
                  <motion.tr 
                    key={item.id} 
                    variants={itemVariants}
                    className="hover:bg-white/10 dark:hover:bg-white/5 transition-colors group"
                  >
                    <td className="px-8 py-5 text-[13px] font-black tracking-tight uppercase">{item.filename}</td>
                    <td className="px-8 py-5 text-sm font-bold opacity-80 dark:opacity-90 tracking-tight uppercase truncate max-w-[150px]">{item.path}</td>
                    <td className="px-8 py-5 text-[10px] font-black opacity-50 tracking-tighter">{item.directory ? '目录' : formatBytes(item.size)}</td>
                    <td className="px-8 py-5 text-sm font-bold opacity-80 dark:opacity-90 tracking-tighter uppercase">{formatDateTime(item.deletedAt)}</td>
                    <td className="px-8 py-5 text-[10px] font-black text-amber-500 uppercase tracking-tighter">{formatDateTime(item.expiresAt)}</td>
                    <td className="px-8 py-5 text-right">
                      <button
                        type="button"
                        onClick={async () => {
                          await restoreRecycleBinItem(item.id);
                          await loadItems();
                        }}
                        className="p-2.5 rounded-lg glass-panel hover:bg-blue-600 hover:text-white text-blue-500 transition-all border-white/10"
                        title="恢复"
                      >
                        <RotateCcw className="h-4 w-4" />
                      </button>
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
