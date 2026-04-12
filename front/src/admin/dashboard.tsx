import { useEffect, useState, type ReactNode } from 'react';
import {
  Activity,
  ArrowRight,
  Copy,
  Database,
  HardDrive,
  RefreshCw,
  Send,
  Settings,
  Share2,
  Shield,
  Users,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { motion } from 'motion/react';
import { cn } from '@/src/lib/utils';
import { getAdminSummary, type AdminSummary } from '@/src/lib/admin';
import { formatBytes } from '@/src/lib/format';

const container = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: {
      staggerChildren: 0.05,
    },
  },
};

const itemVariants = {
  hidden: { y: 16, opacity: 0 },
  show: { y: 0, opacity: 1 },
};

function ConfigCard({
  to,
  icon,
  title,
  description,
  highlights,
  tone,
}: {
  to: string;
  icon: ReactNode;
  title: string;
  description: string;
  highlights: string[];
  tone: 'blue' | 'emerald' | 'amber';
}) {
  const toneClass =
    tone === 'emerald'
      ? 'border-emerald-500/20 bg-emerald-500/10 text-emerald-500'
      : tone === 'amber'
        ? 'border-amber-500/20 bg-amber-500/10 text-amber-500'
        : 'border-blue-500/20 bg-blue-500/10 text-blue-500';

  return (
    <Link
      to={to}
      className="group glass-panel-no-hover rounded-2xl border border-white/10 p-7 shadow-3xl transition-all hover:border-white/20 hover:bg-white/10"
    >
      <div className="flex items-start justify-between gap-4">
        <div className={cn('flex h-12 w-12 items-center justify-center rounded-xl border', toneClass)}>{icon}</div>
        <ArrowRight className="h-4 w-4 opacity-20 transition-all group-hover:translate-x-1 group-hover:opacity-100" />
      </div>
      <h2 className="mt-6 text-xl font-black tracking-tight text-gray-900 dark:text-white">{title}</h2>
      <p className="mt-3 text-sm leading-7 text-gray-600 dark:text-gray-300">{description}</p>
      <div className="mt-6 flex flex-wrap gap-2">
        {highlights.map((item) => (
          <span
            key={item}
            className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[9px] font-black uppercase tracking-[0.18em] opacity-75"
          >
            {item}
          </span>
        ))}
      </div>
    </Link>
  );
}

function ToolCard({
  to,
  icon,
  title,
  description,
}: {
  to: string;
  icon: ReactNode;
  title: string;
  description: string;
}) {
  return (
    <Link
      to={to}
      className="group rounded-xl border border-white/10 bg-white/5 p-5 transition-all hover:border-white/20 hover:bg-white/10"
    >
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-white/10 bg-black/10 text-gray-700 dark:text-gray-100">
            {icon}
          </div>
          <div>
            <div className="text-[11px] font-black uppercase tracking-[0.18em]">{title}</div>
            <div className="mt-1 text-[10px] font-bold leading-5 opacity-45">{description}</div>
          </div>
        </div>
        <ArrowRight className="h-4 w-4 opacity-20 transition-all group-hover:translate-x-1 group-hover:opacity-100" />
      </div>
    </Link>
  );
}

export default function AdminDashboard() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [summary, setSummary] = useState<AdminSummary | null>(null);
  const [copiedInviteCode, setCopiedInviteCode] = useState(false);

  async function loadSummary() {
    setError('');
    try {
      setSummary(await getAdminSummary());
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载配置首页失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadSummary();
  }, []);

  async function copyInviteCode(inviteCode: string) {
    try {
      await navigator.clipboard.writeText(inviteCode);
      setCopiedInviteCode(true);
      window.setTimeout(() => setCopiedInviteCode(false), 1500);
    } catch {
      setError('复制邀请码失败，请手动复制。');
    }
  }

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col overflow-y-auto p-8 text-gray-900 dark:text-gray-100"
    >
      <div className="mb-10 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="animate-text-reveal text-4xl font-black tracking-tight text-gray-900 dark:text-white">配置首页</h1>
          <p className="mt-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">
            系统配置 / 存储配置 / 用户策略 / 治理工具
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            setLoading(true);
            void loadSummary();
          }}
          className="flex items-center gap-3 rounded-lg glass-panel px-6 py-3 text-[11px] font-black uppercase tracking-widest transition-all hover:bg-white/40"
        >
          <RefreshCw className={cn('h-4 w-4', loading && 'animate-spin')} />
          刷新配置视图
        </button>
      </div>

      {error ? (
        <div className="mb-8 rounded-lg border border-red-500/20 bg-red-500/10 px-6 py-4 text-xs font-bold uppercase tracking-widest text-red-600 dark:text-red-400">
          {error}
        </div>
      ) : null}

      {loading && !summary ? (
        <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">
          正在读取配置首页...
        </div>
      ) : summary ? (
        <motion.div variants={container} initial="hidden" animate="show" className="space-y-10">
          <motion.section variants={itemVariants} className="glass-panel-no-hover rounded-2xl border border-white/10 p-8 shadow-3xl">
            <div className="grid gap-8 xl:grid-cols-[1.1fr_0.9fr]">
              <div>
                <div className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">配置主入口</div>
                <h2 className="mt-4 text-3xl font-black tracking-tight text-gray-900 dark:text-white">
                  后台先改配置，再做治理
                </h2>
                <p className="mt-4 max-w-3xl text-sm leading-7 text-gray-600 dark:text-gray-300">
                  这里不再把后台定义成“看统计的地方”，而是把已经能影响系统行为的配置入口集中起来。你现在最应该先改的是系统级开关、存储策略和用户策略，治理工具放在第二层。
                </p>
                <div className="mt-6 flex flex-wrap gap-3">
                  <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[9px] font-black uppercase tracking-[0.2em] opacity-75">
                    邀请码: {summary.inviteCode}
                  </span>
                  <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[9px] font-black uppercase tracking-[0.2em] opacity-75">
                    离线快传上限: {formatBytes(summary.offlineTransferStorageLimitBytes)}
                  </span>
                  <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[9px] font-black uppercase tracking-[0.2em] opacity-75">
                    当前用户数: {summary.totalUsers}
                  </span>
                </div>
              </div>

              <div className="rounded-2xl border border-white/10 bg-black/20 p-6">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <div className="text-[9px] font-black uppercase tracking-[0.22em] opacity-30">当前生效值</div>
                    <div className="mt-2 text-xl font-black tracking-[0.25em] text-blue-500">{summary.inviteCode}</div>
                  </div>
                  <button
                    type="button"
                    onClick={() => void copyInviteCode(summary.inviteCode)}
                    className="rounded-lg border border-white/10 bg-white/5 p-2.5 transition-colors hover:bg-white/10"
                    title="复制邀请码"
                  >
                    {copiedInviteCode ? <Shield className="h-4 w-4 text-emerald-500" /> : <Copy className="h-4 w-4" />}
                  </button>
                </div>
                <div className="mt-6 grid grid-cols-2 gap-4">
                  <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                    <div className="text-[9px] font-black uppercase tracking-[0.18em] opacity-30">离线快传占用</div>
                    <div className="mt-2 text-lg font-black tracking-tight">{formatBytes(summary.offlineTransferStorageBytes)}</div>
                  </div>
                  <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                    <div className="text-[9px] font-black uppercase tracking-[0.18em] opacity-30">下载流量</div>
                    <div className="mt-2 text-lg font-black tracking-tight">{formatBytes(summary.downloadTrafficBytes)}</div>
                  </div>
                  <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                    <div className="text-[9px] font-black uppercase tracking-[0.18em] opacity-30">总文件数</div>
                    <div className="mt-2 text-lg font-black tracking-tight">{summary.totalFiles}</div>
                  </div>
                  <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                    <div className="text-[9px] font-black uppercase tracking-[0.18em] opacity-30">请求量</div>
                    <div className="mt-2 text-lg font-black tracking-tight">{summary.requestCount}</div>
                  </div>
                </div>
              </div>
            </div>
          </motion.section>

          <motion.section variants={itemVariants}>
            <div className="mb-6">
              <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">配置分组</h2>
              <p className="mt-2 text-[10px] font-bold uppercase tracking-[0.2em] opacity-25">先处理系统行为，再处理治理问题</p>
            </div>
            <div className="grid gap-6 xl:grid-cols-3">
              <ConfigCard
                to="/admin/settings"
                icon={<Settings className="h-6 w-6" />}
                title="系统配置"
                description="集中处理邀请码、离线快传总容量，以及当前运行环境里最直接影响注册和传输行为的系统项。"
                highlights={['邀请码', '离线快传上限', '运行快照']}
                tone="blue"
              />
              <ConfigCard
                to="/admin/storage-policies"
                icon={<Database className="h-6 w-6" />}
                title="存储配置"
                description="集中处理存储策略的新增、编辑、启停与迁移任务创建，不再把这块藏在资源表格里。"
                highlights={['策略编辑', '启停', '迁移任务']}
                tone="emerald"
              />
              <ConfigCard
                to="/admin/users"
                icon={<Users className="h-6 w-6" />}
                title="用户策略"
                description="集中处理用户角色、配额、上传上限、手动改密和临时密码重置，把用户页从“查人”改成“改规则”。"
                highlights={['角色', '配额', '上传上限', '密码策略']}
                tone="amber"
              />
            </div>
          </motion.section>

          <motion.section variants={itemVariants}>
            <div className="mb-6">
              <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">治理工具</h2>
              <p className="mt-2 text-[10px] font-bold uppercase tracking-[0.2em] opacity-25">这些页面更偏治理与排查，而不是直接改配置</p>
            </div>
            <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-4">
              <ToolCard to="/admin/files" icon={<HardDrive className="h-5 w-5" />} title="文件治理" description="查全站文件、执行高风险删除。" />
              <ToolCard to="/admin/file-blobs" icon={<Database className="h-5 w-5" />} title="对象治理" description="查 blob 关联、孤儿风险和对象异常。" />
              <ToolCard to="/admin/shares" icon={<Share2 className="h-5 w-5" />} title="分享治理" description="排查 Token、撤销分享和过期风险。" />
              <ToolCard to="/admin/tasks" icon={<Send className="h-5 w-5" />} title="任务监控" description="观察迁移和后台任务，不在这里改系统配置。" />
              <ToolCard to="/admin/audits" icon={<Activity className="h-5 w-5" />} title="审计日志" description="复盘谁改了什么，而不是直接改值。" />
              <ToolCard to="/admin/filesystem" icon={<HardDrive className="h-5 w-5" />} title="文件系统快照" description="查看当前文件与上传体系状态。" />
            </div>
          </motion.section>

          <motion.section variants={itemVariants} className="grid gap-6 lg:grid-cols-3">
            <div className="glass-panel-no-hover rounded-2xl border border-white/10 p-6 shadow-3xl">
              <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-xl border border-blue-500/20 bg-blue-500/10 text-blue-500">
                <Settings className="h-5 w-5" />
              </div>
              <div className="text-[10px] font-black uppercase tracking-[0.22em] opacity-30">系统配置负载</div>
              <div className="mt-3 text-2xl font-black tracking-tight">{formatBytes(summary.offlineTransferStorageLimitBytes)}</div>
              <p className="mt-3 text-[10px] font-bold leading-6 opacity-45">当前系统里最直接可调的资源上限是离线快传容量，总览展示它是为了方便你先去调参。</p>
            </div>
            <div className="glass-panel-no-hover rounded-2xl border border-white/10 p-6 shadow-3xl">
              <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-xl border border-emerald-500/20 bg-emerald-500/10 text-emerald-500">
                <Database className="h-5 w-5" />
              </div>
              <div className="text-[10px] font-black uppercase tracking-[0.22em] opacity-30">存储当前占用</div>
              <div className="mt-3 text-2xl font-black tracking-tight">{formatBytes(summary.totalStorageBytes)}</div>
              <p className="mt-3 text-[10px] font-bold leading-6 opacity-45">存储策略页会决定上传模式、对象大小上限和迁移方向，这里只给你一个当前量级参考。</p>
            </div>
            <div className="glass-panel-no-hover rounded-2xl border border-white/10 p-6 shadow-3xl">
              <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-xl border border-amber-500/20 bg-amber-500/10 text-amber-500">
                <Users className="h-5 w-5" />
              </div>
              <div className="text-[10px] font-black uppercase tracking-[0.22em] opacity-30">用户策略对象</div>
              <div className="mt-3 text-2xl font-black tracking-tight">{summary.totalUsers}</div>
              <p className="mt-3 text-[10px] font-bold leading-6 opacity-45">用户页现在应该被理解成“用户策略面板”，你在里面改的是规则和限制，不是只读名单。</p>
            </div>
          </motion.section>
        </motion.div>
      ) : null}
    </motion.div>
  );
}
