import { useEffect, useState } from 'react';
import { HardDrive, ListTodo, Send, Share2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { motion } from 'motion/react';
import { getProfile } from '@/src/lib/auth';
import { getTasks, type BackgroundTask } from '@/src/lib/background-tasks';
import { formatBytes, formatDateTime } from '@/src/lib/format';
import { listRecentFiles, type FileItem } from '@/src/lib/files';
import { getPortalRoleLabel, getSession } from '@/src/lib/session';

const container = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: {
      staggerChildren: 0.1
    }
  }
};

const item = {
  hidden: { y: 20, opacity: 0 },
  show: { y: 0, opacity: 1 }
};

export default function Overview() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [recentFiles, setRecentFiles] = useState<FileItem[]>([]);
  const [recentTasks, setRecentTasks] = useState<BackgroundTask[]>([]);
  const [profile, setProfile] = useState(() => getSession()?.user ?? null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError('');
      try {
        const [nextProfile, files, tasksPage] = await Promise.all([getProfile(), listRecentFiles(), getTasks(0, 5)]);
        if (!cancelled) {
          setProfile(nextProfile);
          setRecentFiles(files);
          setRecentTasks(tasksPage.items.slice(0, 5));
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : '加载概览失败');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex flex-col h-full overflow-y-auto p-8 text-gray-900 dark:text-gray-100"
    >
        <div className="flex items-center gap-4 mb-4">
          <h1 className="text-4xl font-black tracking-tight animate-text-reveal">概览</h1>
        </div>
        <p className="mb-10 text-[11px] font-black uppercase tracking-[0.2em] opacity-40">
          {profile ? `${profile.displayName || profile.username} / 在线` : '正在初始化会话...'}
        </p>

        {error ? <div className="mb-8 rounded-lg bg-red-500/10 px-6 py-4 text-sm text-red-600 dark:text-red-400 font-bold border border-red-500/20 backdrop-blur-md">{error}</div> : null}

        <motion.div 
          variants={container}
          initial="hidden"
          animate="show"
          className="mb-10 grid grid-cols-1 md:grid-cols-3 gap-6"
        >
          <motion.div variants={item} className="glass-panel p-8 flex flex-col justify-center gap-3">
            <div className="text-[10px] font-black uppercase tracking-widest opacity-40">账号权限</div>
            <div className="text-2xl font-black tracking-tight">{getPortalRoleLabel(profile?.role)}</div>
          </motion.div>
          <motion.div variants={item} className="glass-panel p-8 flex flex-col justify-center gap-3">
            <div className="text-[10px] font-black uppercase tracking-widest opacity-40">存储配额</div>
            <div className="text-2xl font-black tracking-tight">{formatBytes(profile?.storageQuotaBytes ?? 0)}</div>
          </motion.div>
          <motion.div variants={item} className="glass-panel p-8 flex flex-col justify-center gap-3">
            <div className="text-[10px] font-black uppercase tracking-widest opacity-40">上传上限</div>
            <div className="text-2xl font-black tracking-tight">{formatBytes(profile?.maxUploadSizeBytes ?? 0)}</div>
          </motion.div>
        </motion.div>

        <h2 className="mb-6 text-sm font-black uppercase tracking-widest opacity-60">快捷入口</h2>
        <motion.div 
          variants={container}
          initial="hidden"
          animate="show"
          className="mb-12 grid grid-cols-2 md:grid-cols-4 gap-4"
        >
          <motion.div variants={item}>
            <Link to="/files" className="glass-panel p-6 flex flex-col items-center justify-center font-black group transition-all duration-300 border-white/10">
              <HardDrive className="mb-4 h-6 w-6 text-blue-500 group-hover:scale-110 transition-transform" />
              <div className="text-xs tracking-widest">网盘</div>
              <div className="mt-1 text-[9px] opacity-40 font-bold uppercase tracking-tighter">管理文件</div>
            </Link>
          </motion.div>
          <motion.div variants={item}>
            <Link to="/tasks" className="glass-panel p-6 flex flex-col items-center justify-center font-black group transition-all duration-300 border-white/10">
              <ListTodo className="mb-4 h-6 w-6 text-amber-500 group-hover:scale-110 transition-transform" />
              <div className="text-xs tracking-widest">任务</div>
              <div className="mt-1 text-[9px] opacity-40 font-bold uppercase tracking-tighter">任务队列</div>
            </Link>
          </motion.div>
          <motion.div variants={item}>
            <Link to="/shares" className="glass-panel p-6 flex flex-col items-center justify-center font-black group transition-all duration-300 border-white/10">
              <Share2 className="mb-4 h-6 w-6 text-rose-500 group-hover:scale-110 transition-transform" />
              <div className="text-xs tracking-widest">分享</div>
              <div className="mt-1 text-[9px] opacity-40 font-bold uppercase tracking-tighter">公开链接</div>
            </Link>
          </motion.div>
          <motion.div variants={item}>
            <Link to="/transfer" className="glass-panel p-6 flex flex-col items-center justify-center font-black group transition-all duration-300 border-white/10">
              <Send className="mb-4 h-6 w-6 text-green-500 group-hover:scale-110 transition-transform" />
              <div className="text-xs tracking-widest">快传</div>
              <div className="mt-1 text-[9px] opacity-40 font-bold uppercase tracking-tighter">点对点交换</div>
            </Link>
          </motion.div>
        </motion.div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-8">
          <motion.div 
            initial={{ x: -20, opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            transition={{ delay: 0.5 }}
            className="glass-panel-no-hover shadow-sm flex flex-col"
          >
            <div className="border-b border-white/10 px-8 py-6">
              <h2 className="text-xs font-black uppercase tracking-widest">最近文件</h2>
            </div>
            <div className="p-6">
              {loading ? (
                <div className="text-xs font-bold opacity-40 uppercase tracking-widest p-4">加载中...</div>
              ) : recentFiles.length === 0 ? (
                <div className="text-xs font-bold opacity-40 uppercase tracking-widest p-4 text-center">暂无数据</div>
              ) : (
                <div className="space-y-2">
                  {recentFiles.map((file) => (
                    <div key={file.id} className="flex items-center justify-between bg-white/5 dark:bg-black/20 rounded-lg px-5 py-4 hover:bg-white/10 dark:hover:bg-white/5 transition-all group">
                      <div className="min-w-0 pr-4">
                        <div className="truncate text-sm font-bold tracking-tight group-hover:text-blue-500 transition-colors uppercase">{file.filename}</div>
                        <div className="truncate text-[10px] font-bold opacity-30 mt-1 uppercase tracking-tighter">{file.path}</div>
                      </div>
                      <div className="text-right text-[10px] font-black opacity-40 flex-shrink-0 tracking-tighter">
                        <div>{formatBytes(file.size)}</div>
                        <div className="mt-1">{formatDateTime(file.createdAt)}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </motion.div>

          <motion.div 
            initial={{ x: 20, opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            transition={{ delay: 0.6 }}
            className="glass-panel-no-hover shadow-sm flex flex-col"
          >
            <div className="border-b border-white/10 px-8 py-6">
              <h2 className="text-xs font-black uppercase tracking-widest">任务状态</h2>
            </div>
            <div className="p-6">
              {loading ? (
                <div className="text-xs font-bold opacity-40 uppercase tracking-widest p-4">同步中...</div>
              ) : recentTasks.length === 0 ? (
                <div className="text-xs font-bold opacity-40 uppercase tracking-widest p-4 text-center">空闲</div>
              ) : (
                <div className="space-y-4">
                  {recentTasks.map((task) => (
                    <div key={task.id} className="border-b border-white/5 dark:border-white/5 pb-4 last:border-0 last:pb-0">
                      <div className="flex items-center justify-between mb-2">
                        <div className="text-xs font-black tracking-widest uppercase">{task.type}</div>
                        <div className="text-[9px] font-black px-2 py-0.5 bg-blue-500/10 text-blue-500 rounded-sm border border-blue-500/20 uppercase">{task.status}</div>
                      </div>
                      <div className="text-[10px] font-bold opacity-30 tracking-tighter">{formatDateTime(task.updatedAt)}</div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </motion.div>
        </div>
    </motion.div>
  );
}
