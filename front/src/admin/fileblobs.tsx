import { useEffect, useState, type ReactNode } from 'react';
import { AlertTriangle, CheckCircle2, Copy, FileBox, RefreshCw, Search, ShieldAlert, XCircle } from 'lucide-react';
import { motion } from 'motion/react';
import { AdminSelect } from '@/src/components/admin/AdminSelect';
import { cn } from '@/src/lib/utils';
import { formatBytes, formatDateTime } from '@/src/lib/format';
import {
  getAdminFileBlobs,
  type AdminFileBlobEntityType,
  type AdminFileBlobResponse,
} from '@/src/lib/admin-fileblobs';

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

const DEFAULT_FILTERS = {
  userQuery: '',
  storagePolicyId: '',
  objectKey: '',
  entityType: '' as AdminFileBlobEntityType | '',
};

const ENTITY_TYPE_LABELS: Record<AdminFileBlobEntityType, string> = {
  VERSION: '版本',
  THUMBNAIL: '缩略图',
  LIVE_PHOTO: '实况照片',
  TRANSCODE: '转码产物',
  AVATAR: '头像',
};

function statusPill(active: boolean, trueLabel: string, falseLabel: string, tone: 'red' | 'amber' | 'purple' = 'red') {
  const toneClass =
    tone === 'amber'
      ? active
        ? 'border-amber-500/20 bg-amber-500/10 text-amber-600 dark:text-amber-400'
        : 'border-white/10 bg-white/5 text-gray-500 dark:text-gray-300'
      : tone === 'purple'
        ? active
          ? 'border-purple-500/20 bg-purple-500/10 text-purple-600 dark:text-purple-400'
          : 'border-white/10 bg-white/5 text-gray-500 dark:text-gray-300'
        : active
          ? 'border-red-500/20 bg-red-500/10 text-red-600 dark:text-red-400'
          : 'border-white/10 bg-white/5 text-gray-500 dark:text-gray-300';

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[9px] font-black uppercase tracking-[0.18em]',
        toneClass,
      )}
    >
      {active ? <CheckCircle2 className="h-3.5 w-3.5" /> : <XCircle className="h-3.5 w-3.5" />}
      {active ? trueLabel : falseLabel}
    </span>
  );
}

function titleBlock(title: string, subtitle: string) {
  return (
    <div className="mb-6">
      <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">{title}</h2>
      <p className="mt-2 text-[9px] font-black uppercase tracking-[0.22em] opacity-25">{subtitle}</p>
    </div>
  );
}

function metricCard({
  label,
  value,
  icon,
  tone,
}: {
  label: string;
  value: string;
  icon: ReactNode;
  tone: 'blue' | 'green' | 'amber' | 'red' | 'purple';
}) {
  return (
    <div className="glass-panel-no-hover rounded-lg border border-white/10 p-6 shadow-2xl transition-all group hover:border-white/20">
      <div
        className={cn(
          'mb-5 flex h-12 w-12 items-center justify-center rounded-lg border shadow-[0_0_15px_rgba(59,130,246,0.08)]',
          tone === 'blue' && 'bg-blue-500/10 border-blue-500/20 text-blue-500',
          tone === 'green' && 'bg-green-500/10 border-green-500/20 text-green-500',
          tone === 'purple' && 'bg-purple-500/10 border-purple-500/20 text-purple-500',
          tone === 'amber' && 'bg-amber-500/10 border-amber-500/20 text-amber-500',
          tone === 'red' && 'bg-red-500/10 border-red-500/20 text-red-500',
        )}
      >
        {icon}
      </div>
      <h3 className="text-3xl font-black tracking-tight">{value}</h3>
      <p className="mt-2 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">{label}</p>
    </div>
  );
}

function riskRow(label: string, active: boolean, tone: 'red' | 'amber' | 'purple') {
  return statusPill(active, label, `无${label}`, tone);
}

export default function AdminFileBlobs() {
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [page, setPage] = useState<{
    items: AdminFileBlobResponse[];
    total: number;
    page: number;
    size: number;
  } | null>(null);

  async function loadFileBlobs(nextFilters = filters, isRefresh = false) {
    const trimmedStoragePolicyId = nextFilters.storagePolicyId.trim();
    if (trimmedStoragePolicyId) {
      const parsedStoragePolicyId = Number(trimmedStoragePolicyId);
      if (!Number.isInteger(parsedStoragePolicyId) || parsedStoragePolicyId <= 0) {
        setError('存储策略 ID 必须是正整数');
        return;
      }
    }

    if (isRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setError('');

    try {
      const result = await getAdminFileBlobs(0, 100, {
        userQuery: nextFilters.userQuery,
        storagePolicyId: trimmedStoragePolicyId ? Number(trimmedStoragePolicyId) : null,
        objectKey: nextFilters.objectKey,
        entityType: nextFilters.entityType,
      });
      setPage(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载对象实体失败');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useEffect(() => {
    void loadFileBlobs();
  }, []);

  async function copyText(value: string) {
    if (!value) {
      return;
    }

    try {
      await navigator.clipboard.writeText(value);
      setNotice('对象键已复制');
      setError('');
    } catch {
      setError('复制失败，请手动复制。');
      setNotice('');
    }
  }

  const items = page?.items ?? [];
  const blobMissingCount = items.filter((item) => item.blobMissing).length;
  const orphanRiskCount = items.filter((item) => item.orphanRisk).length;
  const referenceMismatchCount = items.filter((item) => item.referenceMismatch).length;
  const anyRiskCount = items.filter((item) => item.blobMissing || item.orphanRisk || item.referenceMismatch).length;
  const activeFilterLabels = [
    filters.userQuery.trim() ? `用户: ${filters.userQuery.trim()}` : '',
    filters.storagePolicyId.trim() ? `策略: #${filters.storagePolicyId.trim()}` : '',
    filters.objectKey.trim() ? `对象键: ${filters.objectKey.trim()}` : '',
    filters.entityType ? `实体类型: ${ENTITY_TYPE_LABELS[filters.entityType]}` : '',
  ].filter(Boolean);
  const isInitialLoading = loading && !page;
  const pageLabel = page ? `第 ${page.page + 1} 页 / 每页 ${page.size}` : '-';

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col overflow-y-auto p-8 text-gray-900 dark:text-gray-100"
    >
      <div className="mb-10 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="animate-text-reveal text-4xl font-black tracking-tight text-gray-900 dark:text-white">对象实体</h1>
          <p className="mt-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">
            `GET /api/admin/file-blobs` / 用户检索 / 策略过滤 / 风险巡检
          </p>
        </div>
        <div className="flex flex-wrap gap-3">
          <button
            type="button"
            onClick={() => {
              void loadFileBlobs(filters, true);
            }}
            disabled={loading || refreshing}
            className="flex items-center gap-3 rounded-lg glass-panel px-6 py-3 text-[11px] font-black uppercase tracking-widest transition-all hover:bg-white/40 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <RefreshCw className={cn('h-4 w-4', refreshing && 'animate-spin')} />
            刷新列表
          </button>
        </div>
      </div>

      {notice ? (
        <div className="mb-8 rounded-lg border border-blue-500/20 bg-blue-500/10 px-6 py-4 text-xs font-bold uppercase tracking-widest text-blue-600 dark:text-blue-300">
          {notice}
        </div>
      ) : null}

      <motion.section variants={container} initial="hidden" animate="show" className="mb-10 grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
        <motion.div variants={itemVariants}>
          {metricCard({
            label: '当前页对象数',
            value: page ? `${items.length}` : '-',
            icon: <FileBox className="h-6 w-6" />,
            tone: 'blue',
          })}
        </motion.div>
        <motion.div variants={itemVariants}>
          {metricCard({
            label: 'blobMissing',
            value: `${blobMissingCount}`,
            icon: <XCircle className="h-6 w-6" />,
            tone: 'red',
          })}
        </motion.div>
        <motion.div variants={itemVariants}>
          {metricCard({
            label: 'orphanRisk',
            value: `${orphanRiskCount}`,
            icon: <AlertTriangle className="h-6 w-6" />,
            tone: 'amber',
          })}
        </motion.div>
        <motion.div variants={itemVariants}>
          {metricCard({
            label: 'referenceMismatch',
            value: `${referenceMismatchCount}`,
            icon: <ShieldAlert className="h-6 w-6" />,
            tone: 'purple',
          })}
        </motion.div>
      </motion.section>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          void loadFileBlobs(filters);
        }}
        className="mb-8 glass-panel-no-hover rounded-lg border border-white/10 p-6 shadow-3xl"
      >
        {titleBlock('筛选器', '只保留服务端支持的查询参数，避免前端做额外猜测')}
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-[1.2fr_0.8fr_1fr_0.8fr]">
          <label className="group relative block">
            <Search className="pointer-events-none absolute left-5 top-1/2 h-4 w-4 -translate-y-1/2 opacity-30 transition-colors group-focus-within:text-blue-500" />
            <input
              value={filters.userQuery}
              onChange={(event) => setFilters((current) => ({ ...current, userQuery: event.target.value }))}
              placeholder="搜索用户名 / 邮箱 / 手机号"
              className="w-full rounded-lg border border-white/10 bg-white/10 py-4 pl-14 pr-5 outline-none transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
            />
          </label>
          <label className="group relative block">
            <input
              value={filters.storagePolicyId}
              onChange={(event) => setFilters((current) => ({ ...current, storagePolicyId: event.target.value }))}
              inputMode="numeric"
              placeholder="存储策略 ID"
              className="w-full rounded-lg border border-white/10 bg-white/10 px-5 py-4 outline-none transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
            />
          </label>
          <label className="group relative block">
            <input
              value={filters.objectKey}
              onChange={(event) => setFilters((current) => ({ ...current, objectKey: event.target.value }))}
              placeholder="对象键"
              className="w-full rounded-lg border border-white/10 bg-white/10 px-5 py-4 outline-none transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
            />
          </label>
          <label className="group relative block">
            <AdminSelect
              value={filters.entityType}
              onChange={(event) =>
                setFilters((current) => ({
                  ...current,
                  entityType: event.target.value as AdminFileBlobEntityType | '',
                }))
              }
              className="w-full font-black text-[11px] uppercase tracking-widest"
            >
              <option value="">全部实体类型</option>
              {Object.entries(ENTITY_TYPE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </AdminSelect>
          </label>
        </div>

        <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-2">
            {activeFilterLabels.length ? (
              activeFilterLabels.map((label) => (
                <span
                  key={label}
                  className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[9px] font-black uppercase tracking-[0.2em] opacity-70"
                >
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
              onClick={() => {
                setFilters(DEFAULT_FILTERS);
                void loadFileBlobs(DEFAULT_FILTERS);
              }}
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

      {error ? (
        <div className="mb-8 rounded-lg border border-red-500/20 bg-red-500/10 px-6 py-4 text-xs font-bold uppercase tracking-widest text-red-600 dark:text-red-400">
          {error}
        </div>
      ) : null}

      {page ? (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3 text-[9px] font-black uppercase tracking-[0.22em] opacity-30">
          <span>
            共 {page.total} 条记录 / {pageLabel}
          </span>
          <span>风险对象 {anyRiskCount} 条</span>
        </div>
      ) : null}

      <div className="flex-1 min-h-0">
        {isInitialLoading ? (
          <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">
            正在加载对象实体快照...
          </div>
        ) : page ? (
          <div className="glass-panel-no-hover rounded-lg overflow-hidden shadow-3xl border border-white/10">
            <div className="overflow-x-auto">
              <table className="min-w-[1600px] divide-y divide-white/10">
                <thead className="bg-white/10 dark:bg-black/40">
                  <tr>
                    <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">对象键</th>
                    <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">实体 / Blob</th>
                    <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">存储策略</th>
                    <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">大小 / 类型</th>
                    <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">关联信息</th>
                    <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">风险</th>
                    <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">时间</th>
                  </tr>
                </thead>
                <motion.tbody variants={container} initial="hidden" animate="show" className="divide-y divide-white/10 dark:divide-white/5">
                  {items.map((item) => {
                    const rowClassName = cn(
                      'group transition-colors',
                      item.blobMissing && 'bg-red-500/5 hover:bg-red-500/10',
                      !item.blobMissing && item.orphanRisk && 'bg-amber-500/5 hover:bg-amber-500/10',
                      !item.blobMissing && !item.orphanRisk && item.referenceMismatch && 'bg-purple-500/5 hover:bg-purple-500/10',
                    );

                    return (
                      <motion.tr key={`${item.entityId}-${item.blobId}`} variants={itemVariants} className={rowClassName}>
                        <td className="px-6 py-5 align-top">
                          <div className="flex items-start gap-3">
                            <div className="min-w-0 flex-1">
                              <div className="break-all font-mono text-[11px] font-black uppercase tracking-tight group-hover:text-blue-500">
                                {item.objectKey}
                              </div>
                              <div className="mt-1 text-[9px] font-black uppercase tracking-[0.18em] opacity-30">
                                创建者 {item.createdByUsername || `#${item.createdByUserId ?? '-'}`}
                              </div>
                              <div className="mt-1 text-[9px] font-black uppercase tracking-[0.18em] opacity-25">
                                样本所有者 {item.sampleOwnerUsername || '-'}
                                {item.sampleOwnerEmail ? ` / ${item.sampleOwnerEmail}` : ''}
                              </div>
                            </div>
                            <button
                              type="button"
                              onClick={() => void copyText(item.objectKey)}
                              className="rounded-lg border border-white/10 bg-white/5 p-2 text-blue-500 opacity-30 transition-all hover:bg-blue-600 hover:text-white group-hover:opacity-100"
                              title="复制对象键"
                            >
                              <Copy className="h-4 w-4" />
                            </button>
                          </div>
                        </td>

                        <td className="px-6 py-5 align-top">
                          <div className="text-[11px] font-black uppercase tracking-tight">实体 #{item.entityId}</div>
                          <div className="mt-1 text-[9px] font-black uppercase tracking-[0.18em] opacity-30">Blob #{item.blobId}</div>
                          <div className="mt-2 inline-flex rounded-full border border-blue-500/20 bg-blue-500/10 px-2.5 py-1 text-[9px] font-black uppercase tracking-[0.18em] text-blue-600 dark:text-blue-400">
                            {ENTITY_TYPE_LABELS[item.entityType]}
                          </div>
                        </td>

                        <td className="px-6 py-5 align-top">
                          <div className="text-[11px] font-black uppercase tracking-tight">策略 #{item.storagePolicyId}</div>
                          <div className="mt-1 text-[9px] font-black uppercase tracking-[0.18em] opacity-30">
                            {item.createdByUserId != null ? `创建者 ID ${item.createdByUserId}` : '创建者 ID -'}
                          </div>
                        </td>

                        <td className="px-6 py-5 align-top">
                          <div className="text-[11px] font-black uppercase tracking-tight">{formatBytes(item.size)}</div>
                          <div className="mt-1 break-words text-[9px] font-black uppercase tracking-[0.18em] opacity-30">
                            {item.contentType || '-'}
                          </div>
                        </td>

                        <td className="px-6 py-5 align-top">
                          <div className="space-y-1 text-[9px] font-black uppercase tracking-[0.18em] opacity-40">
                            <div>引用 {item.referenceCount ?? '-'}</div>
                            <div>关联文件 {item.linkedStoredFileCount}</div>
                            <div>关联所有者 {item.linkedOwnerCount}</div>
                          </div>
                        </td>

                        <td className="px-6 py-5 align-top">
                          <div className="flex flex-col gap-2">
                            {riskRow('blobMissing', item.blobMissing, 'red')}
                            {riskRow('orphanRisk', item.orphanRisk, 'amber')}
                            {riskRow('referenceMismatch', item.referenceMismatch, 'purple')}
                          </div>
                        </td>

                        <td className="px-6 py-5 align-top">
                          <div className="text-[10px] font-bold uppercase tracking-tighter opacity-30">{formatDateTime(item.createdAt)}</div>
                          <div className="mt-1 text-[9px] font-black uppercase tracking-[0.18em] opacity-25">
                            Blob {formatDateTime(item.blobCreatedAt)}
                          </div>
                        </td>
                      </motion.tr>
                    );
                  })}

                  {items.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="px-8 py-20 text-center text-[10px] font-black uppercase tracking-widest opacity-30">
                        没有匹配的对象实体
                      </td>
                    </tr>
                  ) : null}
                </motion.tbody>
              </table>
            </div>
          </div>
        ) : (
          <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">
            暂无对象实体数据
          </div>
        )}
      </div>
    </motion.div>
  );
}
