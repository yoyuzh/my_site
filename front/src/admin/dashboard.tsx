import { useEffect, useState } from 'react';
import { Copy, Database, HardDrive, RefreshCw, Send, Users, ChevronRight, Activity } from 'lucide-react';
import { cn } from '@/src/lib/utils';
import { Link } from 'react-router-dom';
import { motion } from 'motion/react';
import { getAdminSummary, type AdminSummary } from '@/src/lib/admin';
import { formatBytes } from '@/src/lib/format';

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
  hidden: { y: 20, opacity: 0 },
  show: { y: 0, opacity: 1 }
};

export default function AdminDashboard() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [summary, setSummary] = useState<AdminSummary | null>(null);

  async function loadSummary() {
    setError('');
    try {
      setSummary(await getAdminSummary());
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载后台总览失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadSummary();
  }, []);

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col p-8 text-gray-900 dark:text-gray-100 overflow-y-auto"
    >
      <div className="mb-10 flex items-center justify-between">
        <div>
          <h1 className="text-4xl font-black tracking-tight animate-text-reveal text-gray-900 dark:text-white">后台指挥中心</h1>
          <p className="mt-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">全局基础设施 / 系统遥测</p>
        </div>
        <button
          type="button"
          onClick={() => {
            setLoading(true);
            void loadSummary();
          }}
          className="flex items-center gap-3 px-6 py-3 rounded-lg glass-panel hover:bg-white/40 transition-all font-black text-[11px] uppercase tracking-widest"
        >
          <RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} />
          刷新状态
        </button>
      </div>

      {error ? <div className="mb-8 rounded-lg bg-red-500/10 border border-red-500/20 px-6 py-4 text-xs text-red-600 font-bold backdrop-blur-md uppercase tracking-widest">{error}</div> : null}

      {loading && !summary ? (
        <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">正在查询核心服务...</div>
      ) : summary ? (
        <motion.div 
          variants={container}
          initial="hidden"
          animate="show"
          className="space-y-10"
        >
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
            <motion.div variants={itemVariants} className="glass-panel-no-hover rounded-lg p-8 shadow-2xl border border-white/10 group hover:border-blue-500/30 transition-all">
              <div className="mb-6 flex h-14 w-14 items-center justify-center rounded-lg bg-blue-500/10 border border-blue-500/20 shadow-[0_0_15px_rgba(59,130,246,0.1)]">
                <Users className="h-7 w-7 text-blue-500" />
              </div>
              <h3 className="text-4xl font-black tracking-tight group-hover:text-blue-500 transition-colors">{summary.totalUsers}</h3>
              <p className="mt-2 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">用户总数</p>
            </motion.div>

            <motion.div variants={itemVariants} className="glass-panel-no-hover rounded-lg p-8 shadow-2xl border border-white/10 group hover:border-green-500/30 transition-all">
              <div className="mb-6 flex h-14 w-14 items-center justify-center rounded-lg bg-green-500/10 border border-green-500/20 shadow-[0_0_15px_rgba(34,197,94,0.1)]">
                <HardDrive className="h-7 w-7 text-green-500" />
              </div>
              <h3 className="text-4xl font-black tracking-tight group-hover:text-green-500 transition-colors">{summary.totalFiles}</h3>
              <p className="mt-2 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">文件总数</p>
            </motion.div>

            <motion.div variants={itemVariants} className="glass-panel-no-hover rounded-lg p-8 shadow-2xl border border-white/10 group hover:border-purple-500/30 transition-all">
              <div className="mb-6 flex h-14 w-14 items-center justify-center rounded-lg bg-purple-500/10 border border-purple-500/20 shadow-[0_0_15px_rgba(168,85,247,0.1)]">
                <Database className="h-7 w-7 text-purple-500" />
              </div>
              <h3 className="text-4xl font-black tracking-tight group-hover:text-purple-500 transition-colors">{formatBytes(summary.totalStorageBytes).split(' ')[0]}<span className="text-xl ml-1 opacity-40">{formatBytes(summary.totalStorageBytes).split(' ')[1]}</span></h3>
              <p className="mt-2 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">存储容量</p>
            </motion.div>

            <motion.div variants={itemVariants} className="glass-panel-no-hover rounded-lg p-8 shadow-2xl border border-white/10 group hover:border-amber-500/30 transition-all">
              <div className="mb-6 flex h-14 w-14 items-center justify-center rounded-lg bg-amber-500/10 border border-amber-500/20 shadow-[0_0_15px_rgba(245,158,11,0.1)]">
                <Send className="h-7 w-7 text-amber-500" />
              </div>
              <h3 className="text-4xl font-black tracking-tight group-hover:text-amber-500 transition-colors">{formatBytes(summary.offlineTransferStorageBytes).split(' ')[0]}<span className="text-xl ml-1 opacity-40">{formatBytes(summary.offlineTransferStorageBytes).split(' ')[1]}</span></h3>
              <p className="mt-2 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">快传占用</p>
            </motion.div>
          </div>

          <div className="grid grid-cols-1 gap-10 lg:grid-cols-2">
            <motion.section variants={itemVariants} className="glass-panel-no-hover rounded-lg p-10 shadow-3xl border border-white/10">
              <div className="mb-8">
                <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">快捷入口</h2>
              </div>
              <div className="grid grid-cols-1 gap-4">
                <Link to="/admin/users" className="flex items-center justify-between p-6 rounded-lg bg-white/5 border border-white/5 hover:bg-white/10 hover:border-blue-500/30 transition-all group">
                  <div className="flex items-center gap-5">
                    <div className="p-3 rounded-lg bg-blue-500/10 group-hover:bg-blue-600 text-blue-500 group-hover:text-white transition-all shadow-inner">
                      <Users className="h-6 w-6" />
                    </div>
                    <div>
                      <span className="text-[11px] font-black uppercase tracking-widest block">用户管理</span>
                      <span className="text-[9px] font-bold opacity-30 uppercase tracking-widest mt-1 block group-hover:opacity-60 transition-opacity">统一账号控制</span>
                    </div>
                  </div>
                  <ChevronRight className="h-5 w-5 opacity-20 group-hover:opacity-100 group-hover:translate-x-1 transition-all" />
                </Link>
                <Link to="/admin/files" className="flex items-center justify-between p-6 rounded-lg bg-white/5 border border-white/5 hover:bg-white/10 hover:border-green-500/30 transition-all group">
                  <div className="flex items-center gap-5">
                    <div className="p-3 rounded-lg bg-green-500/10 group-hover:bg-green-600 text-green-500 group-hover:text-white transition-all shadow-inner">
                      <HardDrive className="h-6 w-6" />
                    </div>
                    <div>
                      <span className="text-[11px] font-black uppercase tracking-widest block">文件审计</span>
                      <span className="text-[9px] font-bold opacity-30 uppercase tracking-widest mt-1 block group-hover:opacity-60 transition-opacity">全站文件巡检</span>
                    </div>
                  </div>
                  <ChevronRight className="h-5 w-5 opacity-20 group-hover:opacity-100 group-hover:translate-x-1 transition-all" />
                </Link>
                <Link to="/admin/storage-policies" className="flex items-center justify-between p-6 rounded-lg bg-white/5 border border-white/5 hover:bg-white/10 hover:border-purple-500/30 transition-all group">
                  <div className="flex items-center gap-5">
                    <div className="p-3 rounded-lg bg-purple-500/10 group-hover:bg-purple-600 text-purple-500 group-hover:text-white transition-all shadow-inner">
                      <Database className="h-6 w-6" />
                    </div>
                    <div>
                      <span className="text-[11px] font-black uppercase tracking-widest block">存储策略</span>
                      <span className="text-[9px] font-bold opacity-30 uppercase tracking-widest mt-1 block group-hover:opacity-60 transition-opacity">按策略分发</span>
                    </div>
                  </div>
                  <ChevronRight className="h-5 w-5 opacity-20 group-hover:opacity-100 group-hover:translate-x-1 transition-all" />
                </Link>
              </div>
            </motion.section>

            <motion.section variants={itemVariants} className="glass-panel-no-hover rounded-lg p-10 shadow-3xl border border-white/10">
              <div className="mb-8 flex items-center justify-between">
                <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">运行概览</h2>
                <div className="flex items-center gap-3 text-[9px] font-black uppercase tracking-widest px-3 py-1.5 rounded-lg bg-green-500/10 text-green-500 border border-green-500/20 shadow-inner">
                  <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse shadow-[0_0_8px_rgba(34,197,94,0.5)]"></span>
                  服务健康
                </div>
              </div>
              <div className="space-y-8">
                <div className="p-8 rounded-lg bg-black/40 border border-white/5">
                  <div className="flex items-center justify-between mb-6">
                    <span className="text-[9px] font-black uppercase tracking-[0.3em] opacity-30">邀请码</span>
                    <button
                      type="button"
                      onClick={() => { navigator.clipboard.writeText(summary.inviteCode); window.alert('邀请码已复制'); }}
                      className="p-2 rounded-lg hover:bg-white/10 transition-all opacity-40 hover:opacity-100"
                    >
                      <Copy className="h-4 w-4" />
                    </button>
                  </div>
                  <div className="text-4xl font-black tracking-[0.4em] text-center p-8 bg-blue-500/5 rounded-lg border border-white/5 text-blue-500/80 drop-shadow-2xl">
                    {summary.inviteCode}
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-6">
                  <div className="p-6 rounded-lg bg-white/5 border border-white/5 group hover:border-white/20 transition-all">
                    <div className="text-[9px] font-black uppercase tracking-[0.2em] opacity-30 mb-3 flex items-center gap-2">
                       <Activity className="h-3 w-3" /> 下载流量
                    </div>
                    <div className="text-2xl font-black tracking-tight">{formatBytes(summary.downloadTrafficBytes)}</div>
                  </div>
                  <div className="p-6 rounded-lg bg-white/5 border border-white/5 group hover:border-white/20 transition-all">
                    <div className="text-[9px] font-black uppercase tracking-[0.2em] opacity-30 mb-3 flex items-center gap-2">
                       <Activity className="h-3 w-3" /> 请求量
                    </div>
                    <div className="text-2xl font-black tracking-tight group-hover:text-blue-500 transition-colors font-black">{summary.requestCount} <span className="text-xs opacity-40 ml-1">次</span></div>
                  </div>
                </div>
              </div>
            </motion.section>
          </div>
        </motion.div>
      ) : null}
    </motion.div>
  );
}
