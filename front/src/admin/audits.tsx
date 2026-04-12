import { Fragment, useEffect, useMemo, useState } from 'react';
import { ChevronDown, ChevronLeft, ChevronRight, ChevronUp, Copy, RefreshCw, Search } from 'lucide-react';
import { motion } from 'motion/react';
import { cn } from '@/src/lib/utils';
import { formatDateTime } from '@/src/lib/format';
import { getAdminAudits, type AdminAuditLog } from '@/src/lib/admin-audits';

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
  actorQuery: '',
  actionType: '',
  targetType: '',
  targetId: '',
};

function titleBlock(title: string, subtitle: string) {
  return (
    <div className="mb-6">
      <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">{title}</h2>
      <p className="mt-2 text-[9px] font-black uppercase tracking-[0.22em] opacity-25">{subtitle}</p>
    </div>
  );
}

function normalizeAuthorities(value: AdminAuditLog['actorAuthorities']) {
  if (Array.isArray(value)) {
    return value.filter(Boolean);
  }

  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (trimmed.startsWith('[')) {
      try {
        const parsed = JSON.parse(trimmed) as unknown;
        if (Array.isArray(parsed)) {
          return parsed.map((item) => String(item).trim()).filter(Boolean);
        }
      } catch {
        // Fall through to the plain-text splitter below.
      }
    }

    return value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  }

  return [];
}

function formatDetailsJson(detailsJson: string | null) {
  if (!detailsJson?.trim()) {
    return '无详细内容';
  }

  try {
    const parsed = JSON.parse(detailsJson);
    return typeof parsed === 'string' ? parsed : JSON.stringify(parsed, null, 2);
  } catch {
    return detailsJson;
  }
}

function actionPill(value: string) {
  return (
    <span className="inline-flex items-center rounded-full border border-blue-500/20 bg-blue-500/10 px-2.5 py-1 text-[9px] font-black uppercase tracking-[0.18em] text-blue-600 dark:text-blue-300">
      {value || '-'}
    </span>
  );
}

function targetPill(type: string, targetId: string | null) {
  return (
    <div className="inline-flex flex-col gap-1">
      <span className="inline-flex w-fit items-center rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[9px] font-black uppercase tracking-[0.18em] opacity-80">
        {type || '-'}
      </span>
      <span className="font-mono text-[9px] font-black uppercase tracking-[0.16em] opacity-35">{targetId || '-'}</span>
    </div>
  );
}

export default function AdminAuditsPage() {
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [page, setPage] = useState<{
    items: AdminAuditLog[];
    total: number;
    page: number;
    size: number;
  } | null>(null);
  const [expandedAuditIds, setExpandedAuditIds] = useState<Set<number>>(() => new Set());

  async function loadAudits(nextPage = 0, nextFilters = filters, isRefresh = false) {
    if (isRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setError('');

    try {
      const result = await getAdminAudits(nextPage, 100, nextFilters);
      setPage(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载审计日志失败');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useEffect(() => {
    void loadAudits();
  }, []);

  async function copyText(value: string) {
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      window.alert('复制失败，请手动复制。');
    }
  }

  const items = page?.items ?? [];
  const activeFilterLabels = useMemo(
    () =>
      [
        filters.actorQuery.trim() ? `操作者: ${filters.actorQuery.trim()}` : '',
        filters.actionType.trim() ? `动作: ${filters.actionType.trim()}` : '',
        filters.targetType.trim() ? `目标类型: ${filters.targetType.trim()}` : '',
        filters.targetId.trim() ? `目标 ID: ${filters.targetId.trim()}` : '',
      ].filter(Boolean),
    [filters],
  );
  const isInitialLoading = loading && !page;

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col overflow-y-auto p-8 text-gray-900 dark:text-gray-100"
    >
      <div className="mb-10 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="animate-text-reveal text-4xl font-black tracking-tight text-gray-900 dark:text-white">审计日志</h1>
          <p className="mt-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">
            `GET /api/admin/audits` / 操作者 / 动作 / 目标 / 详情展开
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            void loadAudits(page?.page ?? 0, filters, true);
          }}
          disabled={loading || refreshing}
          className="flex items-center gap-3 rounded-lg glass-panel px-6 py-3 text-[11px] font-black uppercase tracking-widest transition-all hover:bg-white/40 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <RefreshCw className={cn('h-4 w-4', refreshing && 'animate-spin')} />
          刷新列表
        </button>
      </div>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          void loadAudits(0, filters);
        }}
        className="mb-8 glass-panel-no-hover rounded-lg border border-white/10 p-6 shadow-3xl"
      >
        {titleBlock('筛选器', '只使用后端支持的查询参数，避免前端侧再做任何推断')}
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-[1fr_0.9fr_0.9fr_0.9fr]">
          <label className="group relative block">
            <Search className="pointer-events-none absolute left-5 top-1/2 h-4 w-4 -translate-y-1/2 opacity-30 transition-colors group-focus-within:text-blue-500" />
            <input
              value={filters.actorQuery}
              onChange={(event) => setFilters((current) => ({ ...current, actorQuery: event.target.value }))}
              placeholder="操作者关键词"
              className="w-full rounded-lg border border-white/10 bg-white/10 py-4 pl-14 pr-5 outline-none transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
            />
          </label>
          <label className="group relative block">
            <input
              value={filters.actionType}
              onChange={(event) => setFilters((current) => ({ ...current, actionType: event.target.value }))}
              placeholder="动作类型"
              className="w-full rounded-lg border border-white/10 bg-white/10 px-5 py-4 outline-none transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
            />
          </label>
          <label className="group relative block">
            <input
              value={filters.targetType}
              onChange={(event) => setFilters((current) => ({ ...current, targetType: event.target.value }))}
              placeholder="目标类型"
              className="w-full rounded-lg border border-white/10 bg-white/10 px-5 py-4 outline-none transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
            />
          </label>
          <label className="group relative block">
            <input
              value={filters.targetId}
              onChange={(event) => setFilters((current) => ({ ...current, targetId: event.target.value }))}
              placeholder="目标 ID"
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
              onClick={() => {
                setFilters(DEFAULT_FILTERS);
                void loadAudits(0, DEFAULT_FILTERS);
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

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 text-[9px] font-black uppercase tracking-[0.22em] opacity-30">
        <span>共 {page?.total ?? 0} 条审计记录</span>
        <span>当前页 {items.length} 条</span>
        <span>{page ? `第 ${page.page + 1} 页 / 每页 ${page.size}` : '第 - 页'}</span>
      </div>

      <div className="flex-1 min-h-0">
        {isInitialLoading ? (
          <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">
            正在读取审计日志...
          </div>
        ) : (
          <div className="glass-panel-no-hover rounded-lg overflow-hidden border border-white/10 shadow-3xl">
            <div className="overflow-x-auto">
              <table className="min-w-[1600px] divide-y divide-white/10">
                <thead className="bg-white/10 dark:bg-black/40">
                  <tr>
                    <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">时间</th>
                    <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">操作者</th>
                    <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">动作</th>
                    <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">目标</th>
                    <th className="px-6 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">摘要</th>
                    <th className="px-6 py-5 text-right text-[9px] font-black uppercase tracking-[0.2em] opacity-40">详情</th>
                  </tr>
                </thead>
                <motion.tbody variants={container} initial="hidden" animate="show" className="divide-y divide-white/10 dark:divide-white/5">
                  {items.map((audit) => {
                    const authorities = normalizeAuthorities(audit.actorAuthorities);
                    const expanded = expandedAuditIds.has(audit.id);

                    return (
                      <Fragment key={audit.id}>
                        <motion.tr variants={itemVariants} className="group transition-colors hover:bg-white/10 dark:hover:bg-white/5">
                          <td className="px-6 py-5 align-top">
                            <div className="text-[11px] font-black tracking-tight">{formatDateTime(audit.createdAt)}</div>
                            <div className="mt-1 font-mono text-[9px] font-black uppercase tracking-[0.18em] opacity-35">ID {audit.id}</div>
                          </td>
                          <td className="px-6 py-5 align-top">
                            <div className="text-[12px] font-black tracking-tight">{audit.actorUsername || '系统 / 未知'}</div>
                            <div className="mt-1 font-mono text-[9px] font-black uppercase tracking-[0.18em] opacity-35">
                              {audit.actorUserId != null ? `user #${audit.actorUserId}` : '无用户 ID'}
                            </div>
                            <div className="mt-3 flex flex-wrap gap-2">
                              {authorities.length ? (
                                authorities.map((authority) => (
                                  <span
                                    key={`${audit.id}-${authority}`}
                                    className="rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[9px] font-black uppercase tracking-[0.18em] opacity-70"
                                  >
                                    {authority}
                                  </span>
                                ))
                              ) : (
                                <span className="rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[9px] font-black uppercase tracking-[0.18em] opacity-40">
                                  无权限信息
                                </span>
                              )}
                            </div>
                          </td>
                          <td className="px-6 py-5 align-top">{actionPill(audit.actionType)}</td>
                          <td className="px-6 py-5 align-top">{targetPill(audit.targetType, audit.targetId)}</td>
                          <td className="px-6 py-5 align-top">
                            <div className="max-w-[560px] text-[11px] font-bold leading-6 opacity-90">{audit.summary || '-'}</div>
                          </td>
                          <td className="px-6 py-5 align-top text-right">
                            <button
                              type="button"
                              onClick={() => {
                                setExpandedAuditIds((current) => {
                                  const next = new Set(current);
                                  if (next.has(audit.id)) {
                                    next.delete(audit.id);
                                  } else {
                                    next.add(audit.id);
                                  }
                                  return next;
                                });
                              }}
                              className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[9px] font-black uppercase tracking-[0.18em] transition-all hover:bg-white/10"
                            >
                              {expanded ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
                              {expanded ? '收起详情' : '查看详情'}
                            </button>
                          </td>
                        </motion.tr>
                        {expanded ? (
                          <tr>
                            <td colSpan={6} className="px-6 pb-6">
                              <div className="rounded-lg border border-white/10 bg-white/5 p-5">
                                <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                                  <div>
                                    <h3 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">详情内容</h3>
                                    <p className="mt-2 text-[11px] font-bold opacity-45">`detailsJson` 原文与格式化预览</p>
                                  </div>
                                  <button
                                    type="button"
                                    onClick={() => void copyText(audit.detailsJson || '')}
                                    className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-[9px] font-black uppercase tracking-[0.18em] transition-all hover:bg-white/10"
                                  >
                                    <Copy className="h-3.5 w-3.5" />
                                    复制原文
                                  </button>
                                </div>
                                <div className="grid gap-4 xl:grid-cols-[0.9fr_1.1fr]">
                                  <div className="rounded-lg border border-white/10 bg-black/10 p-4">
                                    <div className="mb-3 text-[9px] font-black uppercase tracking-[0.2em] opacity-30">基础信息</div>
                                    <div className="space-y-3 text-[11px] font-bold leading-6">
                                      <div>
                                        <span className="mr-2 text-[9px] font-black uppercase tracking-[0.18em] opacity-30">Summary</span>
                                        {audit.summary || '-'}
                                      </div>
                                      <div>
                                        <span className="mr-2 text-[9px] font-black uppercase tracking-[0.18em] opacity-30">Action</span>
                                        {audit.actionType || '-'}
                                      </div>
                                      <div>
                                        <span className="mr-2 text-[9px] font-black uppercase tracking-[0.18em] opacity-30">Target</span>
                                        {audit.targetType || '-'} {audit.targetId ? `#${audit.targetId}` : ''}
                                      </div>
                                      <div>
                                        <span className="mr-2 text-[9px] font-black uppercase tracking-[0.18em] opacity-30">Created</span>
                                        {formatDateTime(audit.createdAt)}
                                      </div>
                                    </div>
                                  </div>
                                  <div>
                                    <div className="mb-3 text-[9px] font-black uppercase tracking-[0.2em] opacity-30">JSON 预览</div>
                                    <pre className="max-h-[420px] overflow-auto rounded-lg border border-white/10 bg-black/20 p-4 font-mono text-[11px] leading-6 text-gray-200 dark:text-gray-100">
                                      {formatDetailsJson(audit.detailsJson)}
                                    </pre>
                                  </div>
                                </div>
                              </div>
                            </td>
                          </tr>
                        ) : null}
                      </Fragment>
                    );
                  })}
                  {items.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="px-6 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-35">
                        当前筛选条件下没有审计记录
                      </td>
                    </tr>
                  ) : null}
                </motion.tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
        <div className="text-[9px] font-black uppercase tracking-[0.22em] opacity-30">
          {page ? `第 ${page.page + 1} 页 / 每页 ${page.size}` : '尚未加载分页信息'}
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => {
              if (!page || page.page <= 0) {
                return;
              }
              void loadAudits(page.page - 1, filters);
            }}
            disabled={!page || page.page <= 0 || loading}
            className="inline-flex items-center gap-2 rounded-lg border border-white/10 bg-white/5 px-4 py-2 text-[10px] font-black uppercase tracking-widest transition-all hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40"
          >
            <ChevronLeft className="h-4 w-4" />
            上一页
          </button>
          <button
            type="button"
            onClick={() => {
              if (!page || (page.page + 1) * page.size >= page.total) {
                return;
              }
              void loadAudits(page.page + 1, filters);
            }}
            disabled={!page || (page.page + 1) * page.size >= page.total || loading}
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
