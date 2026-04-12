import { useEffect, useState } from 'react';
import { Copy, ExternalLink, RefreshCw, Search, Trash2 } from 'lucide-react';
import { motion } from 'motion/react';
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type RowData,
} from '@tanstack/react-table';
import { AdminSelect } from '@/src/components/admin/AdminSelect';
import { cn } from '@/src/lib/utils';
import { AdminAlertDialog } from '@/src/components/admin/AdminAlertDialog';
import { formatBytes, formatDateTime } from '@/src/lib/format';
import { deleteAdminShare, getAdminShares, type AdminShare } from '@/src/operations-admin/api/governance/shares';

declare module '@tanstack/react-table' {
  interface ColumnMeta<TData extends RowData, TValue> {
    thClassName?: string;
    tdClassName?: string;
  }
}

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
  hidden: { y: 10, opacity: 0 },
  show: { y: 0, opacity: 1 },
};

const DEFAULT_FILTERS = {
  userQuery: '',
  fileName: '',
  token: '',
  passwordProtected: '' as 'true' | 'false' | '',
  expired: '' as 'true' | 'false' | '',
};

function boolBadge(active: boolean, activeLabel: string, inactiveLabel: string, tone: 'blue' | 'amber' | 'purple' | 'red' = 'blue') {
  const toneClass =
    tone === 'amber'
      ? active
        ? 'border-amber-500/20 bg-amber-500/10 text-amber-600 dark:text-amber-400'
        : 'border-white/10 bg-white/5 text-gray-500 dark:text-gray-300'
      : tone === 'purple'
        ? active
          ? 'border-purple-500/20 bg-purple-500/10 text-purple-600 dark:text-purple-400'
          : 'border-white/10 bg-white/5 text-gray-500 dark:text-gray-300'
        : tone === 'red'
          ? active
            ? 'border-red-500/20 bg-red-500/10 text-red-600 dark:text-red-400'
            : 'border-white/10 bg-white/5 text-gray-500 dark:text-gray-300'
          : active
            ? 'border-blue-500/20 bg-blue-500/10 text-blue-600 dark:text-blue-400'
            : 'border-white/10 bg-white/5 text-gray-500 dark:text-gray-300';

  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-sm border px-2 py-0.5 text-[8px] font-black uppercase tracking-widest', toneClass)}>
      {active ? activeLabel : inactiveLabel}
    </span>
  );
}

export default function AdminShares() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [shares, setShares] = useState<AdminShare[]>([]);
  const [total, setTotal] = useState(0);
  const [pendingDeleteShare, setPendingDeleteShare] = useState<AdminShare | null>(null);

  async function loadShares(nextFilters = filters) {
    setError('');
    try {
      const result = await getAdminShares(0, 100, nextFilters);
      setShares(result.items);
      setTotal(result.total);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载分享治理列表失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadShares();
  }, []);

  async function copyText(value: string, successMessage: string) {
    try {
      await navigator.clipboard.writeText(value);
      setNotice(successMessage);
      setError('');
    } catch {
      setError('复制失败，请手动复制。');
      setNotice('');
    }
  }

  async function handleConfirmDeleteShare() {
    if (!pendingDeleteShare) {
      return;
    }

    const target = pendingDeleteShare;
    setPendingDeleteShare(null);
    try {
      await deleteAdminShare(target.id);
      setLoading(true);
      await loadShares();
    } catch (err) {
      setLoading(false);
      setError(err instanceof Error ? err.message : '删除分享失败');
    }
  }

  const activeFilterLabels = [
    filters.userQuery.trim() ? `用户: ${filters.userQuery.trim()}` : '',
    filters.fileName.trim() ? `文件: ${filters.fileName.trim()}` : '',
    filters.token.trim() ? `Token: ${filters.token.trim()}` : '',
    filters.passwordProtected ? `密码保护: ${filters.passwordProtected === 'true' ? '是' : '否'}` : '',
    filters.expired ? `已过期: ${filters.expired === 'true' ? '是' : '否'}` : '',
  ].filter(Boolean);

  const columns: ColumnDef<AdminShare>[] = [
    {
      accessorKey: 'token',
      header: '分享',
      meta: {
        thClassName: 'px-6 py-5 text-left',
        tdClassName: 'px-6 py-5 align-top',
      },
      cell: ({ row }) => (
        <div>
          <div className="text-[12px] font-black tracking-tight uppercase">{row.original.shareName || row.original.fileName}</div>
          <div className="mt-1 break-all font-mono text-[9px] font-black tracking-[0.18em] opacity-30">{row.original.token}</div>
          <div className="mt-3 flex flex-wrap gap-2">
            {boolBadge(row.original.passwordProtected, '需密码', '无密码', 'amber')}
            {boolBadge(row.original.expired, '已过期', '未过期', 'red')}
          </div>
        </div>
      ),
    },
    {
      id: 'permissions',
      header: '权限',
      meta: {
        thClassName: 'px-6 py-5 text-left',
        tdClassName: 'px-6 py-5 align-top',
      },
      cell: ({ row }) => (
        <div>
          <div className="flex flex-wrap gap-2">
            {boolBadge(row.original.allowDownload, '可下载', '仅查看', 'blue')}
            {boolBadge(row.original.allowImport, '可导入', '受保护', 'purple')}
          </div>
          <div className="mt-3 text-[9px] font-black uppercase tracking-[0.18em] opacity-30">
            Max DL {row.original.maxDownloads ?? '∞'}
          </div>
        </div>
      ),
    },
    {
      id: 'owner',
      header: '所属用户',
      meta: {
        thClassName: 'px-6 py-5 text-left',
        tdClassName: 'px-6 py-5 align-top',
      },
      cell: ({ row }) => (
        <div>
          <div className="text-[11px] font-black uppercase tracking-tight text-blue-500">{row.original.ownerUsername}</div>
          <div className="mt-1 text-[9px] font-black uppercase tracking-[0.18em] opacity-30">{row.original.ownerEmail}</div>
          <div className="mt-1 text-[9px] font-black uppercase tracking-[0.18em] opacity-25">UID #{row.original.ownerId}</div>
        </div>
      ),
    },
    {
      id: 'fileInfo',
      header: '文件信息',
      meta: {
        thClassName: 'px-6 py-5 text-left',
        tdClassName: 'px-6 py-5 align-top',
      },
      cell: ({ row }) => (
        <div>
          <div className="text-[11px] font-black uppercase tracking-tight">{row.original.fileName}</div>
          <div className="mt-1 truncate max-w-[260px] text-[9px] font-black uppercase tracking-[0.18em] opacity-30">
            {row.original.filePath}
          </div>
          <div className="mt-2 text-[9px] font-black uppercase tracking-[0.18em] opacity-25">
            {row.original.directory ? '目录' : `${formatBytes(row.original.fileSize)} / ${row.original.fileContentType || '-'}`}
          </div>
        </div>
      ),
    },
    {
      id: 'stats',
      header: '统计',
      meta: {
        thClassName: 'px-6 py-5 text-left',
        tdClassName: 'px-6 py-5 align-top',
      },
      cell: ({ row }) => (
        <div className="space-y-1 text-[9px] font-black uppercase tracking-[0.18em] opacity-40">
          <div>下载 {row.original.downloadCount}</div>
          <div>查看 {row.original.viewCount}</div>
        </div>
      ),
    },
    {
      id: 'time',
      header: '时间',
      meta: {
        thClassName: 'px-6 py-5 text-left',
        tdClassName: 'px-6 py-5 align-top',
      },
      cell: ({ row }) => (
        <div>
          <div className="text-[10px] font-bold uppercase tracking-tighter opacity-30">{formatDateTime(row.original.createdAt)}</div>
          <div className="mt-1 text-[9px] font-black uppercase tracking-[0.18em] opacity-25">
            过期 {row.original.expiresAt ? formatDateTime(row.original.expiresAt) : '永久有效'}
          </div>
        </div>
      ),
    },
    {
      id: 'actions',
      header: '操作',
      meta: {
        thClassName: 'px-6 py-5 text-right',
        tdClassName: 'px-6 py-5 align-top text-right',
      },
      cell: ({ row }) => {
        const share = row.original;

        return (
          <div className="flex justify-end gap-2 opacity-30 transition-opacity group-hover:opacity-100">
            <button
              type="button"
              onClick={() => void copyText(share.token, '分享 Token 已复制')}
              className="rounded-lg border border-white/10 bg-white/5 p-2.5 text-blue-500 transition-all hover:bg-blue-600 hover:text-white"
              title="复制 Token"
            >
              <Copy className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={() => window.open(`${window.location.origin}/share/${share.token}`, '_blank', 'noopener,noreferrer')}
              className="rounded-lg border border-white/10 bg-white/5 p-2.5 text-blue-500 transition-all hover:bg-blue-600 hover:text-white"
              title="打开分享"
            >
              <ExternalLink className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={() => setPendingDeleteShare(share)}
              className="rounded-lg border border-white/10 bg-white/5 p-2.5 text-red-500 transition-all hover:bg-red-500 hover:text-white"
              title="删除分享"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        );
      },
    },
  ];

  const table = useReactTable({
    data: shares,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getRowId: (row) => String(row.id),
  });

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col overflow-y-auto p-8 text-gray-900 dark:text-gray-100"
    >
      <div className="mb-10 flex items-center justify-between gap-4">
        <div>
          <h1 className="animate-text-reveal text-4xl font-black tracking-tight text-gray-900 dark:text-white">分享管理</h1>
          <p className="mt-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">分享治理 / Token 检索 / 过期与密码保护筛选</p>
        </div>
        <button
          type="button"
          onClick={() => {
            setLoading(true);
            void loadShares();
          }}
          className="flex items-center gap-3 rounded-lg glass-panel px-6 py-3 font-black text-[11px] uppercase tracking-widest transition-all hover:bg-white/40"
        >
          <RefreshCw className={cn('h-4 w-4', loading && 'animate-spin')} />
          刷新列表
        </button>
      </div>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          setLoading(true);
          void loadShares(filters);
        }}
        className="mb-8 glass-panel-no-hover rounded-lg border border-white/10 p-6 shadow-3xl"
      >
        <div className="mb-6">
          <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">筛选器</h2>
          <p className="mt-2 text-[9px] font-black uppercase tracking-[0.22em] opacity-25">严格对应后端 `GET /api/admin/shares` 支持的查询参数</p>
        </div>

        <div className="grid grid-cols-1 gap-4 xl:grid-cols-[1fr_1fr_1.2fr_0.8fr_0.8fr]">
          <label className="relative block group">
            <Search className="pointer-events-none absolute left-5 top-1/2 h-4 w-4 -translate-y-1/2 opacity-30 transition-colors group-focus-within:text-blue-500" />
            <input
              value={filters.userQuery}
              onChange={(event) => setFilters((current) => ({ ...current, userQuery: event.target.value }))}
              placeholder="所有者"
              className="w-full rounded-lg border border-white/10 bg-white/10 py-4 pl-14 pr-5 outline-none transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
            />
          </label>
          <label className="relative block group">
            <input
              value={filters.fileName}
              onChange={(event) => setFilters((current) => ({ ...current, fileName: event.target.value }))}
              placeholder="文件名"
              className="w-full rounded-lg border border-white/10 bg-white/10 px-5 py-4 outline-none transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
            />
          </label>
          <label className="relative block group">
            <input
              value={filters.token}
              onChange={(event) => setFilters((current) => ({ ...current, token: event.target.value }))}
              placeholder="分享 Token"
              className="w-full rounded-lg border border-white/10 bg-white/10 px-5 py-4 outline-none transition-all font-black text-[11px] tracking-widest placeholder:opacity-20 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
            />
          </label>
          <label className="relative block group">
            <AdminSelect
              value={filters.passwordProtected}
              onChange={(event) => setFilters((current) => ({ ...current, passwordProtected: event.target.value as 'true' | 'false' | '' }))}
              className="w-full font-black text-[11px] uppercase tracking-widest"
            >
              <option value="">密码保护</option>
              <option value="true">需要密码</option>
              <option value="false">无需密码</option>
            </AdminSelect>
          </label>
          <label className="relative block group">
            <AdminSelect
              value={filters.expired}
              onChange={(event) => setFilters((current) => ({ ...current, expired: event.target.value as 'true' | 'false' | '' }))}
              className="w-full font-black text-[11px] uppercase tracking-widest"
            >
              <option value="">过期状态</option>
              <option value="true">已过期</option>
              <option value="false">未过期</option>
            </AdminSelect>
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
                setLoading(true);
                void loadShares(DEFAULT_FILTERS);
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

      {notice ? (
        <div className="mb-8 rounded-lg border border-blue-500/20 bg-blue-500/10 px-6 py-4 text-xs font-bold uppercase tracking-widest text-blue-600 dark:text-blue-300">
          {notice}
        </div>
      ) : null}

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 text-[9px] font-black uppercase tracking-[0.22em] opacity-30">
        <span>共 {total} 条分享记录</span>
        <span>当前页 {shares.length} 条</span>
      </div>

      <div className="flex-1 min-h-0">
        {loading && shares.length === 0 ? (
          <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">
            正在读取分享治理列表...
          </div>
        ) : (
          <div className="glass-panel-no-hover rounded-lg overflow-hidden shadow-3xl border border-white/10">
            <div className="overflow-x-auto">
              <table className="min-w-[1500px] divide-y divide-white/10">
                <thead className="bg-white/10 dark:bg-black/40">
                  {table.getHeaderGroups().map((headerGroup) => (
                    <tr key={headerGroup.id}>
                      {headerGroup.headers.map((header) => {
                        const meta = header.column.columnDef.meta;

                        return (
                          <th
                            key={header.id}
                            colSpan={header.colSpan}
                            className={cn(
                              'text-[9px] font-black uppercase tracking-[0.2em] opacity-40',
                              meta?.thClassName
                            )}
                          >
                            {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                          </th>
                        );
                      })}
                    </tr>
                  ))}
                </thead>
                <motion.tbody variants={container} initial="hidden" animate="show" className="divide-y divide-white/10 dark:divide-white/5">
                  {table.getRowModel().rows.map((row) => (
                    <motion.tr key={row.id} variants={itemVariants} className="group transition-colors hover:bg-white/10 dark:hover:bg-white/5">
                      {row.getVisibleCells().map((cell) => {
                        const meta = cell.column.columnDef.meta;

                        return (
                          <td key={cell.id} className={cn(meta?.tdClassName)}>
                            {flexRender(cell.column.columnDef.cell, cell.getContext())}
                          </td>
                        );
                      })}
                    </motion.tr>
                  ))}

                  {table.getRowModel().rows.length === 0 ? (
                    <tr>
                      <td colSpan={table.getAllColumns().length} className="px-8 py-20 text-center text-[10px] font-black uppercase tracking-widest opacity-30">
                        没有匹配的分享记录
                      </td>
                    </tr>
                  ) : null}
                </motion.tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      <AdminAlertDialog
        open={pendingDeleteShare !== null}
        title="删除分享"
        description={
          pendingDeleteShare
            ? `确认删除分享 ${pendingDeleteShare.shareName || pendingDeleteShare.fileName} 吗？删除后该分享将立即失效。`
            : ''
        }
        confirmLabel="确认删除"
        cancelLabel="取消"
        confirmTone="danger"
        busy={loading && pendingDeleteShare !== null}
        onConfirm={handleConfirmDeleteShare}
        onCancel={() => setPendingDeleteShare(null)}
      />
    </motion.div>
  );
}
