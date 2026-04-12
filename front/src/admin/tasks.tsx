import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Clock3,
  FileCode2,
  ListTodo,
  PanelRightOpen,
  RefreshCw,
  Search,
  User,
} from 'lucide-react';
import { motion } from 'motion/react';
import { AdminSelect } from '@/src/components/admin/AdminSelect';
import { cn } from '@/src/lib/utils';
import { formatDateTime } from '@/src/lib/format';
import { getAdminTask, getAdminTasks, type AdminTask, type AdminTaskQuery } from '@/src/lib/admin-tasks';

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
  hidden: { y: 12, opacity: 0 },
  show: { y: 0, opacity: 1 },
};

const DEFAULT_FILTERS: AdminTaskQuery = {
  userQuery: '',
  type: '',
  status: '',
  failureCategory: '',
  leaseState: '',
};

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

function taskTypeLabel(type: string) {
  const labels: Record<string, string> = {
    ARCHIVE: '归档',
    EXTRACT: '解压',
    MEDIA_META: '媒体元数据',
    STORAGE_POLICY_MIGRATION: '存储迁移',
  };

  return labels[type] ?? type;
}

function taskStatusLabel(status: string) {
  const labels: Record<string, string> = {
    QUEUED: '排队中',
    RUNNING: '执行中',
    COMPLETED: '已完成',
    FAILED: '已失败',
    CANCELLED: '已取消',
  };

  return labels[status] ?? status;
}

function failureCategoryLabel(category: string | null) {
  if (!category) {
    return '-';
  }

  const labels: Record<string, string> = {
    UNSUPPORTED_INPUT: '不支持输入',
    DATA_STATE: '数据状态异常',
    TRANSIENT_INFRASTRUCTURE: '临时基础设施',
    RATE_LIMITED: '触发限流',
    UNKNOWN: '未知',
  };

  return labels[category] ?? category;
}

function leaseStateLabel(leaseState: string) {
  const labels: Record<string, string> = {
    ACTIVE: '活跃',
    LEASED: '已租约',
    EXPIRED: '已过期',
    FREE: '空闲',
    NONE: '空闲',
  };

  return labels[leaseState] ?? leaseState;
}

function statusTone(status: string) {
  switch (status) {
    case 'RUNNING':
      return 'blue';
    case 'COMPLETED':
      return 'green';
    case 'FAILED':
      return 'red';
    case 'CANCELLED':
      return 'amber';
    case 'QUEUED':
    default:
      return 'gray';
  }
}

function failureTone(category: string | null) {
  switch (category) {
    case 'TRANSIENT_INFRASTRUCTURE':
      return 'blue';
    case 'RATE_LIMITED':
      return 'amber';
    case 'UNSUPPORTED_INPUT':
    case 'DATA_STATE':
    case 'UNKNOWN':
      return 'red';
    default:
      return 'gray';
  }
}

function leaseTone(leaseState: string) {
  switch (leaseState) {
    case 'ACTIVE':
    case 'LEASED':
      return 'blue';
    case 'EXPIRED':
      return 'red';
    default:
      return 'gray';
  }
}

function pillClass(tone: 'blue' | 'green' | 'amber' | 'red' | 'gray') {
  switch (tone) {
    case 'green':
      return 'border-green-500/20 bg-green-500/10 text-green-600 dark:text-green-400';
    case 'blue':
      return 'border-blue-500/20 bg-blue-500/10 text-blue-600 dark:text-blue-400';
    case 'amber':
      return 'border-amber-500/20 bg-amber-500/10 text-amber-600 dark:text-amber-400';
    case 'red':
      return 'border-red-500/20 bg-red-500/10 text-red-600 dark:text-red-400';
    case 'gray':
    default:
      return 'border-white/10 bg-white/5 text-gray-500 dark:text-gray-300';
  }
}

function Badge({
  children,
  tone = 'gray',
}: {
  children: ReactNode;
  tone?: 'blue' | 'green' | 'amber' | 'red' | 'gray';
}) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[9px] font-black uppercase tracking-[0.18em]', pillClass(tone))}>
      {children}
    </span>
  );
}

function SectionTitle({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div className="mb-6">
      <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">{title}</h2>
      <p className="mt-2 text-[9px] font-black uppercase tracking-[0.22em] opacity-25">{subtitle}</p>
    </div>
  );
}

function MetricCard({
  icon,
  label,
  value,
  tone,
}: {
  icon: ReactNode;
  label: string;
  value: string;
  tone: 'blue' | 'green' | 'amber' | 'red' | 'gray';
}) {
  return (
    <div className="glass-panel-no-hover rounded-lg border border-white/10 p-6 shadow-2xl transition-all hover:border-white/20">
      <div className={cn('mb-5 flex h-12 w-12 items-center justify-center rounded-lg border shadow-[0_0_15px_rgba(59,130,246,0.08)]', pillClass(tone))}>
        {icon}
      </div>
      <h3 className="text-3xl font-black tracking-tight">{value}</h3>
      <p className="mt-2 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">{label}</p>
    </div>
  );
}

function DetailRow({
  label,
  value,
  valueClassName,
}: {
  label: string;
  value: ReactNode;
  valueClassName?: string;
}) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-white/10 py-3 last:border-0 last:pb-0">
      <span className="text-[9px] font-black uppercase tracking-[0.2em] opacity-30">{label}</span>
      <span className={cn('max-w-[66%] text-right text-[11px] font-bold leading-5 break-all', valueClassName)}>{value}</span>
    </div>
  );
}

function parseJson(value: string | null) {
  if (!value) {
    return null;
  }

  try {
    return JSON.parse(value) as unknown;
  } catch {
    return value;
  }
}

function formatJsonPreview(value: string | null) {
  const parsed = parseJson(value);
  if (parsed == null) {
    return '-';
  }

  if (typeof parsed === 'string') {
    return parsed;
  }

  return JSON.stringify(parsed, null, 2);
}

function isActiveTask(status: string) {
  return status === 'QUEUED' || status === 'RUNNING';
}

export default function AdminTasks() {
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [listError, setListError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [pageSize, setPageSize] = useState(20);
  const [pageData, setPageData] = useState<{
    items: AdminTask[];
    total: number;
    page: number;
    size: number;
  } | null>(null);
  const [selectedTask, setSelectedTask] = useState<AdminTask | null>(null);
  const requestSeqRef = useRef(0);

  async function loadTasks(nextPage = 0, nextFilters = filters, nextPageSize = pageSize, isRefresh = false) {
    if (isRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setListError('');

    try {
      const result = await getAdminTasks(nextPage, nextPageSize, nextFilters);
      setPageData(result);
    } catch (err) {
      setListError(err instanceof Error ? err.message : '加载任务监控列表失败');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useEffect(() => {
    void loadTasks();
  }, []);

  async function openTaskDetail(task: AdminTask) {
    setSelectedTask(task);
    setDetailLoading(true);
    setDetailError('');
    const seq = ++requestSeqRef.current;

    try {
      const detail = await getAdminTask(task.id);
      if (seq !== requestSeqRef.current) {
        return;
      }
      setSelectedTask(detail);
    } catch (err) {
      if (seq !== requestSeqRef.current) {
        return;
      }
      setDetailError(err instanceof Error ? err.message : '加载任务详情失败');
    } finally {
      if (seq === requestSeqRef.current) {
        setDetailLoading(false);
      }
    }
  }

  function handleResetFilters() {
    setFilters(DEFAULT_FILTERS);
    setSelectedTask(null);
    void loadTasks(0, DEFAULT_FILTERS);
  }

  const items = pageData?.items ?? [];
  const total = pageData?.total ?? 0;
  const currentPage = pageData?.page ?? 0;
  const currentSize = pageData?.size ?? pageSize;
  const pageCount = pageData ? Math.max(1, Math.ceil((pageData.total || 0) / pageData.size)) : 0;
  const activeCount = items.filter((item) => isActiveTask(item.status)).length;
  const failedCount = items.filter((item) => item.status === 'FAILED').length;
  const retryScheduledCount = items.filter((item) => item.retryScheduled).length;
  const activeFilterLabels = [
    filters.userQuery.trim() ? `用户: ${filters.userQuery.trim()}` : '',
    filters.type.trim() ? `类型: ${filters.type.trim()}` : '',
    filters.status.trim() ? `状态: ${filters.status.trim()}` : '',
    filters.failureCategory.trim() ? `失败分类: ${filters.failureCategory.trim()}` : '',
    filters.leaseState.trim() ? `租约状态: ${filters.leaseState.trim()}` : '',
  ].filter(Boolean);

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col overflow-y-auto p-8 text-gray-900 dark:text-gray-100"
    >
      <div className="mb-10 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="animate-text-reveal text-4xl font-black tracking-tight text-gray-900 dark:text-white">任务监控</h1>
          <p className="mt-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">
            `GET /api/admin/tasks` / `GET /api/admin/tasks/:id` / 租约与重试态监控
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <label className="rounded-lg glass-panel px-4 py-3 text-[11px] font-black uppercase tracking-widest">
            <span className="mr-3 opacity-40">每页</span>
            <AdminSelect
              value={pageSize}
              onChange={(event) => {
                const nextSize = Number(event.target.value);
                setPageSize(nextSize);
                void loadTasks(0, filters, nextSize);
              }}
              className="w-auto min-w-[5rem] bg-transparent border-0 rounded-none p-0 pr-8 shadow-none focus:ring-0 focus:border-transparent font-black text-[11px] uppercase tracking-widest"
            >
              {PAGE_SIZE_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </AdminSelect>
          </label>
          <button
            type="button"
            onClick={() => {
              void loadTasks(currentPage, filters, currentSize, true);
            }}
            disabled={loading || refreshing}
            className="flex items-center gap-3 rounded-lg glass-panel px-6 py-3 text-[11px] font-black uppercase tracking-widest transition-all hover:bg-white/40 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <RefreshCw className={cn('h-4 w-4', refreshing && 'animate-spin')} />
            刷新列表
          </button>
        </div>
      </div>

      <motion.section variants={container} initial="hidden" animate="show" className="mb-10 grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-5">
        <motion.div variants={itemVariants}>
          <MetricCard icon={<ListTodo className="h-6 w-6" />} label="任务总数" value={String(total)} tone="blue" />
        </motion.div>
        <motion.div variants={itemVariants}>
          <MetricCard icon={<Clock3 className="h-6 w-6" />} label="当前页数量" value={String(items.length)} tone="gray" />
        </motion.div>
        <motion.div variants={itemVariants}>
          <MetricCard icon={<RefreshCw className="h-6 w-6" />} label="当前页进行中" value={String(activeCount)} tone="green" />
        </motion.div>
        <motion.div variants={itemVariants}>
          <MetricCard icon={<AlertTriangle className="h-6 w-6" />} label="当前页失败" value={String(failedCount)} tone="red" />
        </motion.div>
        <motion.div variants={itemVariants}>
          <MetricCard icon={<PanelRightOpen className="h-6 w-6" />} label="已安排重试" value={String(retryScheduledCount)} tone="amber" />
        </motion.div>
      </motion.section>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          void loadTasks(0, filters, pageSize);
        }}
        className="mb-8 glass-panel-no-hover rounded-lg border border-white/10 p-6 shadow-3xl"
      >
        <SectionTitle title="筛选器" subtitle="只使用后端支持的任务查询参数，避免前端做额外猜测" />

        <div className="grid grid-cols-1 gap-4 xl:grid-cols-[1.1fr_0.9fr_0.8fr_1fr_0.9fr]">
          <label className="relative block group">
            <Search className="pointer-events-none absolute left-5 top-1/2 h-4 w-4 -translate-y-1/2 opacity-30 transition-colors group-focus-within:text-blue-500" />
            <input
              value={filters.userQuery ?? ''}
              onChange={(event) => setFilters((current) => ({ ...current, userQuery: event.target.value }))}
              placeholder="所有者"
              className="w-full rounded-lg border border-white/10 bg-white/10 py-4 pl-14 pr-5 outline-none transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
            />
          </label>
          <label className="relative block group">
            <input
              value={filters.type ?? ''}
              onChange={(event) => setFilters((current) => ({ ...current, type: event.target.value }))}
              placeholder="任务类型"
              className="w-full rounded-lg border border-white/10 bg-white/10 px-5 py-4 outline-none transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
            />
          </label>
          <label className="relative block group">
            <input
              value={filters.status ?? ''}
              onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}
              placeholder="状态"
              className="w-full rounded-lg border border-white/10 bg-white/10 px-5 py-4 outline-none transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
            />
          </label>
          <label className="relative block group">
            <input
              value={filters.failureCategory ?? ''}
              onChange={(event) => setFilters((current) => ({ ...current, failureCategory: event.target.value }))}
              placeholder="失败分类"
              className="w-full rounded-lg border border-white/10 bg-white/10 px-5 py-4 outline-none transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
            />
          </label>
          <label className="relative block group">
            <input
              value={filters.leaseState ?? ''}
              onChange={(event) => setFilters((current) => ({ ...current, leaseState: event.target.value }))}
              placeholder="租约状态"
              className="w-full rounded-lg border border-white/10 bg-white/10 px-5 py-4 outline-none transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
            />
          </label>
        </div>

        <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-2">
            {activeFilterLabels.length ? (
              activeFilterLabels.map((label) => (
                <span key={label} className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[9px] font-black uppercase tracking-[0.2em] opacity-70">
                  {label}
                </span>
              ))
            ) : (
              <span className="text-[9px] font-black uppercase tracking-[0.22em] opacity-25">当前没有启用筛选条件</span>
            )}
          </div>
          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              onClick={handleResetFilters}
              className="rounded-lg border border-white/10 bg-white/5 px-5 py-3 text-[11px] font-black uppercase tracking-widest transition-all hover:bg-white/10"
            >
              重置筛选
            </button>
            <button
              type="submit"
              className="rounded-lg bg-blue-600 px-5 py-3 text-[11px] font-black uppercase tracking-widest text-white transition-all hover:bg-blue-500"
            >
              应用筛选
            </button>
          </div>
        </div>
      </form>

      {listError ? (
        <div className="mb-8 rounded-lg border border-red-500/20 bg-red-500/10 px-6 py-4 text-xs font-bold uppercase tracking-widest text-red-600 dark:text-red-400">
          {listError}
        </div>
      ) : null}

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 text-[9px] font-black uppercase tracking-[0.22em] opacity-30">
        <span>
          共 {total} 条任务记录
          {pageData ? ` / 第 ${currentPage + 1} 页，共 ${pageCount} 页` : ''}
        </span>
        <span>当前页 {items.length} 条</span>
      </div>

      <div className="grid flex-1 min-h-0 grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_28rem]">
        <div className="min-h-0">
          {loading && !pageData ? (
            <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">
              正在读取任务监控列表...
            </div>
          ) : items.length === 0 ? (
            <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">
              当前没有任务
            </div>
          ) : (
            <div className="glass-panel-no-hover rounded-lg overflow-hidden shadow-3xl border border-white/10">
              <div className="overflow-x-auto">
                <table className="min-w-[1500px] divide-y divide-white/10">
                  <thead className="bg-white/10 dark:bg-black/40">
                    <tr>
                      <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">任务</th>
                      <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">所属用户</th>
                      <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">状态</th>
                      <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">租约 / 重试</th>
                      <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">时间</th>
                      <th className="px-6 py-5 text-right text-[9px] font-black uppercase tracking-[0.2em] opacity-40">操作</th>
                    </tr>
                  </thead>
                  <motion.tbody variants={container} initial="hidden" animate="show" className="divide-y divide-white/10 dark:divide-white/5">
                    {items.map((task) => {
                      const isSelected = selectedTask?.id === task.id;
                      return (
                        <motion.tr
                          key={task.id}
                          variants={itemVariants}
                          onClick={() => {
                            void openTaskDetail(task);
                          }}
                          className={cn(
                            'group cursor-pointer transition-colors hover:bg-white/10 dark:hover:bg-white/5',
                            isSelected && 'bg-blue-500/10 dark:bg-blue-500/10',
                          )}
                        >
                          <td className="px-6 py-5 align-top">
                            <div className="text-[12px] font-black tracking-tight uppercase">{taskTypeLabel(task.type)}</div>
                            <div className="mt-1 font-mono text-[9px] font-black tracking-[0.18em] opacity-30">#{task.id}</div>
                            <div className="mt-3 flex flex-wrap gap-2">
                              <Badge tone="gray">{task.type}</Badge>
                              {task.correlationId ? <Badge tone="blue">{task.correlationId}</Badge> : null}
                            </div>
                          </td>
                          <td className="px-6 py-5 align-top">
                            <div className="text-[11px] font-black tracking-tight uppercase">{task.ownerUsername || '-'}</div>
                            <div className="mt-1 text-[9px] font-mono font-black tracking-[0.16em] opacity-30">{task.ownerEmail || '-'}</div>
                            <div className="mt-3 text-[9px] font-black uppercase tracking-[0.18em] opacity-40">用户 ID #{task.userId}</div>
                          </td>
                          <td className="px-6 py-5 align-top">
                            <div className="flex flex-wrap gap-2">
                              <Badge tone={statusTone(task.status)}>{taskStatusLabel(task.status)}</Badge>
                              {task.retryScheduled ? <Badge tone="amber">已安排重试</Badge> : <Badge tone="gray">未安排重试</Badge>}
                            </div>
                            <div className="mt-3 flex flex-wrap gap-2">
                              <Badge tone={failureTone(task.failureCategory)}>{failureCategoryLabel(task.failureCategory)}</Badge>
                            </div>
                          </td>
                          <td className="px-6 py-5 align-top">
                            <div className="text-[11px] font-black uppercase tracking-tight">
                              {task.attemptCount}/{task.maxAttempts} 次
                            </div>
                            <div className="mt-3 flex flex-wrap gap-2">
                              <Badge tone={leaseTone(task.leaseState)}>{leaseStateLabel(task.leaseState)}</Badge>
                              <Badge tone="gray">{task.workerOwner || '无 worker'}</Badge>
                            </div>
                            <div className="mt-3 text-[9px] font-black uppercase tracking-[0.18em] opacity-40">下一次运行：{formatDateTime(task.nextRunAt)}</div>
                          </td>
                          <td className="px-6 py-5 align-top">
                            <div className="text-[9px] font-black uppercase tracking-[0.18em] opacity-40">创建：{formatDateTime(task.createdAt)}</div>
                            <div className="mt-2 text-[9px] font-black uppercase tracking-[0.18em] opacity-40">更新：{formatDateTime(task.updatedAt)}</div>
                            <div className="mt-2 text-[9px] font-black uppercase tracking-[0.18em] opacity-40">结束：{formatDateTime(task.finishedAt)}</div>
                          </td>
                          <td className="px-6 py-5 align-top text-right">
                            <div className="flex justify-end gap-2 opacity-60 transition-opacity group-hover:opacity-100">
                              <button
                                type="button"
                                onClick={(event) => {
                                  event.stopPropagation();
                                  void openTaskDetail(task);
                                }}
                                className="rounded-lg border border-white/10 bg-white/5 p-2.5 text-blue-500 shadow-sm transition-all hover:bg-blue-600 hover:text-white"
                                title="查看详情"
                              >
                                <PanelRightOpen className="h-4 w-4" />
                              </button>
                            </div>
                          </td>
                        </motion.tr>
                      );
                    })}
                  </motion.tbody>
                </table>
              </div>
            </div>
          )}
        </div>

        <aside className="xl:sticky xl:top-6">
          <div className="glass-panel-no-hover rounded-2xl border border-white/10 p-6 shadow-3xl">
            <div className="mb-6 flex items-start justify-between gap-4">
              <div>
                <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">任务详情</h2>
                <p className="mt-2 text-[9px] font-black uppercase tracking-[0.22em] opacity-25">点击左侧任意任务后查看完整监控信息</p>
              </div>
              {selectedTask ? (
                <button
                  type="button"
                  onClick={() => {
                    setSelectedTask(null);
                    setDetailError('');
                    setDetailLoading(false);
                    requestSeqRef.current += 1;
                  }}
                  className="rounded-lg border border-white/10 bg-white/5 px-4 py-2 text-[10px] font-black uppercase tracking-widest transition-all hover:bg-white/10"
                >
                  关闭
                </button>
              ) : null}
            </div>

            {detailError ? (
              <div className="mb-5 rounded-lg border border-red-500/20 bg-red-500/10 px-4 py-3 text-[11px] font-bold uppercase tracking-wide text-red-600 dark:text-red-400">
                {detailError}
              </div>
            ) : null}

            {selectedTask ? (
              <div className="space-y-6">
                <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge tone={statusTone(selectedTask.status)}>{taskStatusLabel(selectedTask.status)}</Badge>
                    <Badge tone="gray">{taskTypeLabel(selectedTask.type)}</Badge>
                    <Badge tone={selectedTask.retryScheduled ? 'amber' : 'gray'}>{selectedTask.retryScheduled ? '已安排重试' : '未安排重试'}</Badge>
                  </div>
                  <div className="mt-4 text-2xl font-black tracking-tight">任务 #{selectedTask.id}</div>
                  <div className="mt-2 font-mono text-[9px] font-black tracking-[0.18em] opacity-30">{selectedTask.correlationId || '无 correlationId'}</div>
                </div>

                {detailLoading ? (
                  <div className="rounded-2xl border border-white/10 bg-white/5 px-4 py-8 text-center text-[10px] font-black uppercase tracking-widest opacity-40">
                    正在加载详情...
                  </div>
                ) : null}

                <div>
                  <SectionTitle title="基本信息" subtitle="任务归属、类型、状态与失败分类" />
                  <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
                    <DetailRow label="任务 ID" value={`#${selectedTask.id}`} />
                    <DetailRow label="类型" value={taskTypeLabel(selectedTask.type)} />
                    <DetailRow label="状态" value={<Badge tone={statusTone(selectedTask.status)}>{taskStatusLabel(selectedTask.status)}</Badge>} />
                    <DetailRow label="失败分类" value={<Badge tone={failureTone(selectedTask.failureCategory)}>{failureCategoryLabel(selectedTask.failureCategory)}</Badge>} />
                    <DetailRow label="重试已安排" value={<Badge tone={selectedTask.retryScheduled ? 'amber' : 'gray'}>{selectedTask.retryScheduled ? '是' : '否'}</Badge>} />
                    <DetailRow label="所属用户" value={`${selectedTask.ownerUsername || '-'} / ${selectedTask.ownerEmail || '-'}`} />
                    <DetailRow label="用户 ID" value={`#${selectedTask.userId}`} />
                  </div>
                </div>

                <div>
                  <SectionTitle title="租约信息" subtitle="worker 与 lease 边界，便于排查多实例抢占" />
                  <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
                    <DetailRow label="租约状态" value={<Badge tone={leaseTone(selectedTask.leaseState)}>{leaseStateLabel(selectedTask.leaseState)}</Badge>} />
                    <DetailRow label="leaseOwner" value={selectedTask.leaseOwner || '-'} />
                    <DetailRow label="workerOwner" value={selectedTask.workerOwner || '-'} />
                    <DetailRow label="leaseExpiresAt" value={formatDateTime(selectedTask.leaseExpiresAt)} />
                    <DetailRow label="heartbeatAt" value={formatDateTime(selectedTask.heartbeatAt)} />
                    <DetailRow label="nextRunAt" value={formatDateTime(selectedTask.nextRunAt)} />
                    <DetailRow label="attemptCount" value={`${selectedTask.attemptCount}/${selectedTask.maxAttempts}`} />
                    <DetailRow label="correlationId" value={selectedTask.correlationId || '-'} />
                  </div>
                </div>

                <div>
                  <SectionTitle title="时间信息" subtitle="创建、更新与完成时间" />
                  <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
                    <DetailRow label="createdAt" value={formatDateTime(selectedTask.createdAt)} />
                    <DetailRow label="updatedAt" value={formatDateTime(selectedTask.updatedAt)} />
                    <DetailRow label="finishedAt" value={formatDateTime(selectedTask.finishedAt)} />
                  </div>
                </div>

                <div>
                  <SectionTitle title="公开状态" subtitle="publicStateJson / errorMessage / 原始调度状态" />
                  <div className="rounded-2xl border border-white/10 bg-black/30 p-4">
                    <div className="mb-4">
                      <div className="mb-2 text-[9px] font-black uppercase tracking-[0.2em] opacity-30">publicStateJson</div>
                      <pre className="max-h-72 overflow-auto whitespace-pre-wrap break-words rounded-xl border border-white/10 bg-white/5 p-4 text-[11px] leading-6 text-gray-100">
                        {formatJsonPreview(selectedTask.publicStateJson)}
                      </pre>
                    </div>
                    <div>
                      <div className="mb-2 text-[9px] font-black uppercase tracking-[0.2em] opacity-30">errorMessage</div>
                      <div className={cn('rounded-xl border p-4 text-[11px] leading-6', selectedTask.errorMessage ? 'border-red-500/20 bg-red-500/10 text-red-200' : 'border-white/10 bg-white/5 opacity-70')}>
                        {selectedTask.errorMessage || '无错误信息'}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              <div className="rounded-2xl border border-dashed border-white/15 bg-white/5 px-4 py-12 text-center">
                <FileCode2 className="mx-auto h-10 w-10 opacity-25" />
                <p className="mt-4 text-[11px] font-black uppercase tracking-[0.22em] opacity-40">尚未选择任务</p>
                <p className="mt-2 text-[9px] font-black uppercase tracking-[0.2em] opacity-25">从左侧列表打开任务详情面板</p>
              </div>
            )}
          </div>
        </aside>
      </div>

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
        <div className="text-[9px] font-black uppercase tracking-[0.22em] opacity-30">
          {pageData ? `第 ${currentPage + 1} 页 / 每页 ${currentSize}` : '尚未加载分页信息'}
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => {
              if (currentPage <= 0) {
                return;
              }
              void loadTasks(currentPage - 1, filters, currentSize);
            }}
            disabled={!pageData || currentPage <= 0 || loading}
            className="inline-flex items-center gap-2 rounded-lg border border-white/10 bg-white/5 px-4 py-2 text-[10px] font-black uppercase tracking-widest transition-all hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40"
          >
            <ChevronLeft className="h-4 w-4" />
            上一页
          </button>
          <button
            type="button"
            onClick={() => {
              if (!pageData || currentPage + 1 >= pageCount) {
                return;
              }
              void loadTasks(currentPage + 1, filters, currentSize);
            }}
            disabled={!pageData || currentPage + 1 >= pageCount || loading}
            className="inline-flex items-center gap-2 rounded-lg border border-white/10 bg-white/5 px-4 py-2 text-[10px] font-black uppercase tracking-widest transition-all hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40"
          >
            下一页
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>
    </motion.div>
  );
}
