import { useEffect, useState } from 'react';
import { RefreshCw, Search, Trash2, Folder, FileText, ChevronRight } from 'lucide-react';
import { motion } from 'motion/react';
import { cn } from '@/src/lib/utils';
import { AdminAlertDialog } from '@/src/components/admin/AdminAlertDialog';
import { deleteAdminFile, listAdminFiles, type AdminFile } from '@/src/operations-admin/api/governance/files';
import { formatBytes, formatDateTime } from '@/src/lib/format';

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

export default function AdminFilesList() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [ownerQuery, setOwnerQuery] = useState('');
  const [files, setFiles] = useState<AdminFile[]>([]);
  const [pendingDeleteFile, setPendingDeleteFile] = useState<AdminFile | null>(null);

  async function loadFiles(nextQuery = query, nextOwnerQuery = ownerQuery) {
    setError('');
    try {
      const result = await listAdminFiles(0, 100, nextQuery, nextOwnerQuery);
      setFiles(result.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载文件失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadFiles();
  }, []);

  async function handleConfirmDeleteFile() {
    if (!pendingDeleteFile) {
      return;
    }

    const target = pendingDeleteFile;
    setPendingDeleteFile(null);
    try {
      await deleteAdminFile(target.id);
      await loadFiles();
    } catch (err) {
      setError(err instanceof Error ? err.message : '彻底删除文件失败');
    }
  }

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col p-8 text-gray-900 dark:text-gray-100 overflow-y-auto"
    >
      <div className="mb-10 flex items-center justify-between">
        <div>
          <h1 className="text-4xl font-black tracking-tight animate-text-reveal text-gray-900 dark:text-white">全站审计</h1>
          <p className="mt-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">全站对象索引 / 审计日志</p>
        </div>
        <button
          type="button"
          onClick={() => {
            setLoading(true);
            void loadFiles();
          }}
          className="flex items-center gap-3 px-6 py-3 rounded-lg glass-panel hover:bg-white/40 transition-all font-black text-[11px] uppercase tracking-widest"
        >
          <RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} />
          刷新索引
        </button>
      </div>

      <div className="mb-10 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="relative group">
          <Search className="absolute left-5 top-1/2 h-4 w-4 -translate-y-1/2 opacity-30 group-focus-within:text-blue-500 transition-colors" />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                setLoading(true);
                void loadFiles(event.currentTarget.value, ownerQuery);
              }
            }}
            placeholder="搜索文件名或路径...（回车）"
            className="w-full rounded-lg glass-panel bg-white/10 py-5 pl-14 pr-6 outline-none border border-white/10 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10 transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20"
          />
        </div>
        <div className="relative group">
          <Search className="absolute left-5 top-1/2 h-4 w-4 -translate-y-1/2 opacity-30 group-focus-within:text-blue-500 transition-colors" />
          <input
            value={ownerQuery}
            onChange={(event) => setOwnerQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                setLoading(true);
                void loadFiles(query, event.currentTarget.value);
              }
            }}
            placeholder="搜索所属用户...（回车）"
            className="w-full rounded-lg glass-panel bg-white/10 py-5 pl-14 pr-6 outline-none border border-white/10 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10 transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20"
          />
        </div>
      </div>

      {error ? <div className="mb-8 rounded-lg bg-red-500/10 border border-red-500/20 px-6 py-4 text-xs text-red-600 font-bold backdrop-blur-md uppercase tracking-widest">{error}</div> : null}

      <div className="flex-1 min-h-0">
        {loading && files.length === 0 ? (
          <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">正在扫描全站文件...</div>
        ) : (
          <div className="glass-panel-no-hover rounded-lg overflow-hidden shadow-3xl border border-white/10">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-white/10">
                <thead className="bg-white/10 dark:bg-black/40">
                  <tr>
                    <th className="px-8 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">文件</th>
                    <th className="px-8 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">所属用户</th>
                    <th className="px-8 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">大小</th>
                    <th className="px-8 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">创建时间</th>
                    <th className="px-8 py-5 text-right text-[9px] font-black uppercase tracking-[0.2em] opacity-40">操作</th>
                  </tr>
                </thead>
                <motion.tbody 
                  variants={container}
                  initial="hidden"
                  animate="show"
                  className="divide-y divide-white/10 dark:divide-white/5"
                >
                  {files.map((file) => (
                    <motion.tr key={file.id} variants={itemVariants} className="hover:bg-white/10 dark:hover:bg-white/5 transition-colors group">
                      <td className="px-8 py-5">
                        <div className="text-[12px] font-black tracking-tight uppercase group-hover:text-blue-500 transition-colors uppercase">{file.filename}</div>
                        <div className="mt-1 text-[9px] opacity-30 font-black uppercase tracking-widest truncate max-w-xs">{file.path}</div>
                      </td>
                      <td className="px-8 py-5">
                        <div className="flex items-center gap-2">
                           <span className="text-[10px] font-black text-blue-500 uppercase tracking-widest">{file.ownerUsername || file.ownerEmail}</span>
                        </div>
                      </td>
                      <td className="px-8 py-5">
                        <div className="text-[11px] font-black uppercase tracking-widest">{file.directory ? '-' : formatBytes(file.size)}</div>
                        <div className="text-[9px] opacity-20 font-black tracking-[0.2em] uppercase mt-1">
                          {file.directory ? '目录' : '文件'}
                        </div>
                      </td>
                      <td className="px-8 py-5 text-[10px] font-bold opacity-30 tracking-tighter uppercase">
                        {formatDateTime(file.createdAt)}
                      </td>
                      <td className="px-8 py-5 text-right">
                        <button
                          type="button"
                          onClick={() => setPendingDeleteFile(file)}
                          className="p-2.5 rounded-lg glass-panel hover:bg-red-600 hover:text-white text-red-500 border border-white/10 transition-all opacity-0 group-hover:opacity-100 shadow-sm"
                          title="彻底删除"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </td>
                    </motion.tr>
                  ))}
                  {files.length === 0 && (
                    <tr>
                      <td colSpan={5} className="px-8 py-20 text-center text-[10px] font-black uppercase tracking-widest opacity-30">
                        没有匹配的文件
                      </td>
                    </tr>
                  )}
                </motion.tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      <AdminAlertDialog
        open={pendingDeleteFile !== null}
        title="彻底删除文件"
        description={
          pendingDeleteFile
            ? `确认物理擦除 ${pendingDeleteFile.filename} 吗？此操作将触发硬件级销毁。`
            : ''
        }
        confirmLabel="确认删除"
        cancelLabel="取消"
        confirmTone="danger"
        busy={loading && pendingDeleteFile !== null}
        onConfirm={handleConfirmDeleteFile}
        onCancel={() => setPendingDeleteFile(null)}
      />
    </motion.div>
  );
}
