import { useEffect, useState } from 'react';
import { ArrowRightLeft, Edit2, Play, Plus, RefreshCw, Square } from 'lucide-react';
import { motion } from 'motion/react';
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type RowData,
} from '@tanstack/react-table';
import {
  createStorageMigration,
  createStoragePolicy,
  getStoragePolicies,
  updateStoragePolicy,
  updateStoragePolicyStatus,
  type AdminStoragePolicy,
  type StoragePolicyCapabilities,
  type StoragePolicyUpsertPayload,
} from '@/src/operations-admin/api/settings/storage-policies';
import { AdminDialog } from '@/src/components/admin/AdminDialog';
import { AdminInput } from '@/src/components/admin/AdminInput';
import { AdminSelect } from '@/src/components/admin/AdminSelect';
import { formatBytes } from '@/src/lib/format';
import { cn } from '@/src/lib/utils';

declare module '@tanstack/react-table' {
  interface ColumnMeta<TData extends RowData, TValue> {
    thClassName?: string;
    tdClassName?: string;
  }
}

function createDefaultCapabilities(maxObjectSize = 1024 * 1024 * 1024): StoragePolicyCapabilities {
  return {
    directUpload: false,
    multipartUpload: false,
    signedDownloadUrl: false,
    serverProxyDownload: true,
    thumbnailNative: false,
    friendlyDownloadName: false,
    requiresCors: false,
    supportsInternalEndpoint: false,
    maxObjectSize,
  };
}

function buildInitialForm(policy?: AdminStoragePolicy): StoragePolicyUpsertPayload {
  if (policy) {
    return {
      name: policy.name,
      type: policy.type,
      bucketName: policy.bucketName ?? '',
      endpoint: policy.endpoint ?? '',
      region: policy.region ?? '',
      privateBucket: policy.privateBucket,
      prefix: policy.prefix ?? '',
      credentialMode: policy.credentialMode,
      maxSizeBytes: policy.maxSizeBytes,
      capabilities: policy.capabilities,
      enabled: policy.enabled,
    };
  }

  return {
    name: '',
    type: 'LOCAL',
    bucketName: '',
    endpoint: '',
    region: '',
    privateBucket: false,
    prefix: '',
    credentialMode: 'NONE',
    maxSizeBytes: 1024 * 1024 * 1024,
    capabilities: createDefaultCapabilities(),
    enabled: true,
  };
}

export default function AdminStoragePoliciesList() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [policies, setPolicies] = useState<AdminStoragePolicy[]>([]);
  const [editingPolicy, setEditingPolicy] = useState<AdminStoragePolicy | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<StoragePolicyUpsertPayload>(buildInitialForm());
  const [migratingPolicy, setMigratingPolicy] = useState<AdminStoragePolicy | null>(null);
  const [migrationTargetPolicyId, setMigrationTargetPolicyId] = useState('');
  const [migrationSubmitting, setMigrationSubmitting] = useState(false);
  const [migrationNotice, setMigrationNotice] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const columns: ColumnDef<AdminStoragePolicy>[] = [
    {
      accessorKey: 'name',
      header: '名称',
      meta: {
        thClassName: 'px-8 py-5 text-left',
        tdClassName: 'px-8 py-5',
      },
      cell: ({ row }) => {
        const policy = row.original;
        return (
          <div>
            <div className="flex items-center gap-2 font-black text-[13px] tracking-tight">
              {policy.name}
              {policy.defaultPolicy ? (
                <span className="rounded-sm bg-blue-500/20 text-blue-600 dark:text-blue-400 px-1.5 py-0.5 text-[8px] border border-blue-500/20 uppercase tracking-widest font-black">
                  默认
                </span>
              ) : null}
            </div>
            <div className="text-[10px] font-bold opacity-30 mt-1 tracking-tighter">PID::{policy.id}</div>
          </div>
        );
      },
    },
    {
      accessorKey: 'type',
      header: '后端类型',
      meta: {
        thClassName: 'px-8 py-5 text-left',
        tdClassName: 'px-8 py-5',
      },
      cell: ({ getValue }) => (
        <span className="font-black text-[10px] uppercase tracking-widest opacity-60 bg-white/10 px-2 py-0.5 rounded-sm">
          {String(getValue())}
        </span>
      ),
    },
    {
      id: 'endpoint',
      header: '访问端点',
      meta: {
        thClassName: 'px-8 py-5 text-left',
        tdClassName: 'px-8 py-5',
      },
      cell: ({ row }) => {
        const policy = row.original;
        return (
          <div>
            <div className="truncate max-w-[180px] font-bold opacity-60 text-[11px] tracking-tight">
              {policy.endpoint || '-'}
            </div>
            <div className="text-[9px] font-black text-blue-500 uppercase tracking-tighter mt-0.5">
              {policy.bucketName || '私有根路径'}
            </div>
          </div>
        );
      },
    },
    {
      id: 'status',
      header: '状态',
      meta: {
        thClassName: 'px-8 py-5 text-left',
        tdClassName: 'px-8 py-5',
      },
      cell: ({ row }) => {
        const policy = row.original;
        return (
          <span
            className={cn(
              'inline-flex items-center gap-1.5 rounded-sm px-2 py-1 text-[9px] font-black uppercase tracking-widest border',
              policy.enabled
                ? 'bg-green-500/10 text-green-600 dark:text-green-400 border-green-500/20'
                : 'bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/20'
            )}
          >
            <span className={cn('w-1.5 h-1.5 rounded-full', policy.enabled ? 'bg-green-500 animate-pulse' : 'bg-red-500')} />
            {policy.enabled ? '启用' : '停用'}
          </span>
        );
      },
    },
    {
      accessorKey: 'maxSizeBytes',
      header: '对象上限',
      meta: {
        thClassName: 'px-8 py-5 text-left',
        tdClassName: 'px-8 py-5 font-black opacity-60 text-xs tracking-tighter',
      },
      cell: ({ getValue }) => formatBytes(Number(getValue())),
    },
    {
      id: 'actions',
      header: '操作',
      meta: {
        thClassName: 'px-8 py-5 text-right',
        tdClassName: 'px-8 py-5 text-right',
      },
      cell: ({ row }) => {
        const policy = row.original;
        return (
          <div className="flex justify-end gap-2.5 opacity-40 group-hover:opacity-100 transition-opacity">
            <button
              type="button"
              onClick={() => {
                setEditingPolicy(policy);
                setForm(buildInitialForm(policy));
                setShowForm(true);
              }}
              className="p-2 rounded-lg glass-panel hover:bg-white/40 text-gray-500 border-white/20 transition-all"
              title="编辑策略"
            >
              <Edit2 className="h-4 w-4" />
            </button>
            {!policy.defaultPolicy ? (
              <button
                type="button"
                onClick={async () => {
                  await updateStoragePolicyStatus(policy.id, !policy.enabled);
                  await loadPolicies();
                }}
                className={cn(
                  'p-2 rounded-lg glass-panel border-white/20 transition-all',
                  policy.enabled ? 'text-amber-500 hover:bg-amber-500/10' : 'text-green-500 hover:bg-green-500/10'
                )}
                title={policy.enabled ? '停用' : '启用'}
              >
                {policy.enabled ? <Square className="h-4 w-4" /> : <Play className="h-4 w-4" />}
              </button>
            ) : null}
            <button
              type="button"
              onClick={() => openMigrationDialog(policy)}
              className="p-2 rounded-lg glass-panel hover:bg-blue-500/10 text-blue-500 border-white/20 transition-all"
              title="发起迁移"
            >
              <ArrowRightLeft className="h-4 w-4" />
            </button>
          </div>
        );
      },
    },
  ];

  const table = useReactTable({
    data: policies,
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  async function loadPolicies() {
    setError('');
    try {
      setPolicies(await getStoragePolicies());
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载存储策略失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadPolicies();
  }, []);

  async function savePolicy() {
    try {
      if (editingPolicy) {
        await updateStoragePolicy(editingPolicy.id, form);
      } else {
        await createStoragePolicy(form);
      }
      setShowForm(false);
      setEditingPolicy(null);
      setForm(buildInitialForm());
      await loadPolicies();
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存策略失败');
    }
  }

  function openMigrationDialog(policy: AdminStoragePolicy) {
    const firstTargetPolicy = policies.find((item) => item.id !== policy.id);
    setMigratingPolicy(policy);
    setMigrationTargetPolicyId(firstTargetPolicy ? String(firstTargetPolicy.id) : '');
    setMigrationNotice(null);
  }

  function closeMigrationDialog() {
    setMigratingPolicy(null);
    setMigrationTargetPolicyId('');
    setMigrationSubmitting(false);
  }

  async function submitMigration() {
    if (!migratingPolicy) {
      return;
    }

    const targetPolicyId = Number(migrationTargetPolicyId);
    if (!Number.isInteger(targetPolicyId) || targetPolicyId <= 0) {
      setMigrationNotice({
        type: 'error',
        message: '请输入有效的目标策略 ID',
      });
      return;
    }

    if (targetPolicyId === migratingPolicy.id) {
      setMigrationNotice({
        type: 'error',
        message: '目标策略不能与源策略相同',
      });
      return;
    }

    setMigrationSubmitting(true);
    setMigrationNotice(null);

    try {
      await createStorageMigration(migratingPolicy.id, targetPolicyId);
      setMigrationNotice({
        type: 'success',
        message: `已创建从 PID::${migratingPolicy.id} 到 PID::${targetPolicyId} 的迁移任务`,
      });
      closeMigrationDialog();
      await loadPolicies();
    } catch (err) {
      setMigrationNotice({
        type: 'error',
        message: err instanceof Error ? err.message : '创建迁移任务失败',
      });
      setMigrationSubmitting(false);
    }
  }

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col p-8 text-gray-900 dark:text-gray-100 overflow-y-auto"
    >
      <div className="mb-10 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-4xl font-black tracking-tight animate-text-reveal">存储策略</h1>
          <p className="mt-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">资源分发与存储节点映射</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => {
              setLoading(true);
              void loadPolicies();
            }}
            className="flex items-center gap-2 px-5 py-3 rounded-lg glass-panel hover:bg-white/40 transition-all font-black text-[11px] uppercase tracking-widest"
          >
            <RefreshCw className={cn("h-3.5 w-3.5", loading && "animate-spin")} />
            刷新
          </button>
          <button
            type="button"
            onClick={() => {
              setEditingPolicy(null);
              setForm(buildInitialForm());
              setShowForm(true);
            }}
            className="flex items-center gap-2 px-6 py-3 rounded-lg bg-blue-600 text-white font-black text-[11px] uppercase tracking-[0.15em] shadow-lg hover:bg-blue-500 hover:scale-[1.02] active:scale-[0.98] transition-all"
          >
            <Plus className="h-4 w-4" />
            新建策略
          </button>
        </div>
      </div>

      {migrationNotice ? (
        <div
          className={cn(
            'mb-8 rounded-lg border px-6 py-4 text-xs font-bold backdrop-blur-md',
            migrationNotice.type === 'success'
              ? 'border-emerald-500/20 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
              : 'border-red-500/20 bg-red-500/10 text-red-600 dark:text-red-400'
          )}
        >
          {migrationNotice.message}
        </div>
      ) : null}

      {error ? <div className="mb-8 rounded-lg bg-red-500/10 border border-red-500/20 px-6 py-4 text-xs text-red-600 dark:text-red-400 font-bold backdrop-blur-md">{error}</div> : null}

      <div className="flex-1 min-h-0">
        {loading ? (
          <div className="glass-panel rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">正在读取存储策略...</div>
        ) : (
          <div className="glass-panel rounded-lg overflow-hidden shadow-xl border-white/20">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-white/10 text-sm">
                <thead className="bg-white/10 dark:bg-black/40 font-black uppercase tracking-[0.15em] text-[9px] opacity-40">
                  {table.getHeaderGroups().map((headerGroup) => (
                    <tr key={headerGroup.id}>
                      {headerGroup.headers.map((header) => (
                        <th
                          key={header.id}
                          className={cn(header.column.columnDef.meta?.thClassName)}
                        >
                          {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                        </th>
                      ))}
                    </tr>
                  ))}
                </thead>
                <tbody className="divide-y divide-white/10 dark:divide-white/5">
                  {table.getRowModel().rows.map((row) => (
                    <tr key={row.id} className="hover:bg-white/10 dark:hover:bg-white/5 transition-colors group">
                      {row.getVisibleCells().map((cell) => (
                        <td
                          key={cell.id}
                          className={cn(
                            cell.column.columnDef.meta?.thClassName,
                            cell.column.columnDef.meta?.tdClassName
                          )}
                        >
                          {flexRender(cell.column.columnDef.cell, cell.getContext())}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      <AdminDialog
        open={showForm}
        title={editingPolicy ? '编辑策略' : '新建策略'}
        onOpenChange={(nextOpen) => {
          if (!nextOpen) {
            setShowForm(false);
            setEditingPolicy(null);
          }
        }}
        footer={
          <>
            <button
              type="button"
              onClick={() => {
                setShowForm(false);
                setEditingPolicy(null);
              }}
              className="px-8 py-4 rounded-lg glass-panel hover:bg-white/40 text-[11px] font-black uppercase tracking-widest transition-all"
            >
              取消
            </button>
            <button
              type="button"
              onClick={() => void savePolicy()}
              className="px-10 py-4 rounded-lg bg-blue-600 text-white text-[11px] font-black uppercase tracking-widest shadow-xl hover:bg-blue-500 hover:scale-[1.02] active:scale-[0.98] transition-all"
            >
              保存
            </button>
          </>
        }
      >
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
          <div className="space-y-2">
            <label className="text-[10px] font-black uppercase tracking-[0.2em] opacity-40 ml-1">策略名称</label>
            <AdminInput
              value={form.name}
              onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
            />
          </div>
          <div className="space-y-2">
            <label className="text-[10px] font-black uppercase tracking-[0.2em] opacity-40 ml-1">驱动协议</label>
            <AdminSelect
              value={form.type}
              onChange={(event) => setForm((current) => ({ ...current, type: event.target.value as StoragePolicyUpsertPayload['type'] }))}
            >
              <option value="LOCAL">本地文件系统</option>
              <option value="S3_COMPATIBLE">S3 兼容接口</option>
            </AdminSelect>
          </div>
          <div className="space-y-2 md:col-span-2">
            <label className="text-[10px] font-black uppercase tracking-[0.2em] opacity-40 ml-1">端点地址</label>
            <AdminInput
              value={form.endpoint || ''}
              onChange={(event) => setForm((current) => ({ ...current, endpoint: event.target.value }))}
            />
          </div>
          <div className="space-y-2">
            <label className="text-[10px] font-black uppercase tracking-[0.2em] opacity-40 ml-1">桶名称</label>
            <AdminInput
              value={form.bucketName || ''}
              onChange={(event) => setForm((current) => ({ ...current, bucketName: event.target.value }))}
            />
          </div>
          <div className="space-y-2">
            <label className="text-[10px] font-black uppercase tracking-[0.2em] opacity-40 ml-1">对象大小上限（字节）</label>
            <AdminInput
              type="number"
              value={form.maxSizeBytes}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  maxSizeBytes: Number(event.target.value),
                  capabilities: { ...current.capabilities, maxObjectSize: Number(event.target.value) },
                }))
              }
            />
          </div>
        </div>

        <div className="mt-10 grid grid-cols-2 gap-4 text-[9px] font-black uppercase tracking-widest md:grid-cols-4">
          {(
            [
              ['privateBucket', '私有桶'],
              ['enabled', '启用'],
              ['capabilities.directUpload', '直传'],
              ['capabilities.multipartUpload', '分片上传'],
              ['capabilities.signedDownloadUrl', '签名下载'],
              ['capabilities.serverProxyDownload', '代理下载'],
              ['capabilities.requiresCors', '需要 CORS'],
              ['capabilities.supportsInternalEndpoint', '内网端点'],
            ] as const
          ).map(([key, label]) => {
            const checked =
              key === 'privateBucket'
                ? form.privateBucket
                : key === 'enabled'
                  ? form.enabled
                  : form.capabilities[key.replace('capabilities.', '') as keyof StoragePolicyCapabilities];
            const checkedBoolean = Boolean(checked);
            return (
              <label
                key={key}
                className={cn(
                  'flex items-center gap-3 p-3 rounded-lg hover:bg-white/10 transition-all cursor-pointer border border-transparent group',
                  checkedBoolean ? 'bg-white/5 border-white/10' : 'opacity-30'
                )}
              >
                <input
                  type="checkbox"
                  checked={checkedBoolean}
                  onChange={(event) => {
                    const nextValue = event.target.checked;
                    if (key === 'privateBucket') {
                      setForm((current) => ({ ...current, privateBucket: nextValue }));
                      return;
                    }
                    if (key === 'enabled') {
                      setForm((current) => ({ ...current, enabled: nextValue }));
                      return;
                    }
                    const capabilityKey = key.replace('capabilities.', '') as keyof StoragePolicyCapabilities;
                    setForm((current) => ({
                      ...current,
                      capabilities: {
                        ...current.capabilities,
                        [capabilityKey]: nextValue,
                      },
                    }));
                  }}
                  className="w-4 h-4 rounded-sm border-white/20 bg-white/10 text-blue-600 focus:ring-0"
                />
                <span className={cn('transition-colors', checked ? 'text-blue-500' : '')}>{label}</span>
              </label>
            );
          })}
        </div>
      </AdminDialog>

      <AdminDialog
        open={Boolean(migratingPolicy)}
        title="发起迁移"
        description="仅创建迁移任务，不会立即执行对象复制"
        onOpenChange={(nextOpen) => {
          if (!nextOpen) {
            closeMigrationDialog();
          }
        }}
        footer={
          <>
            <button
              type="button"
              onClick={closeMigrationDialog}
              className="px-8 py-4 rounded-lg glass-panel hover:bg-white/40 text-[11px] font-black uppercase tracking-widest transition-all"
            >
              取消
            </button>
            <button
              type="button"
              onClick={() => void submitMigration()}
              disabled={migrationSubmitting}
              className="px-10 py-4 rounded-lg bg-blue-600 text-white text-[11px] font-black uppercase tracking-widest shadow-xl hover:bg-blue-500 hover:scale-[1.02] active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 transition-all"
            >
              {migrationSubmitting ? '创建中...' : '创建迁移任务'}
            </button>
          </>
        }
      >
        {migratingPolicy ? (
          <div className="grid gap-4 rounded-lg border border-white/10 bg-white/5 p-5">
            <div>
              <div className="text-[10px] font-black uppercase tracking-[0.2em] opacity-40">源策略</div>
              <div className="mt-2 text-sm font-black tracking-tight">{migratingPolicy.name}</div>
              <div className="mt-1 text-[10px] font-bold opacity-40">PID::{migratingPolicy.id}</div>
            </div>
            <div className="h-px bg-white/10" />
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <label className="text-[10px] font-black uppercase tracking-[0.2em] opacity-40 ml-1">选择已有目标策略</label>
                <AdminSelect
                  value={migrationTargetPolicyId}
                  onChange={(event) => setMigrationTargetPolicyId(event.target.value)}
                >
                  <option value="">请选择目标策略</option>
                  {policies
                    .filter((item) => item.id !== migratingPolicy.id)
                    .map((policy) => (
                      <option key={policy.id} value={policy.id}>
                        {policy.name} / PID::{policy.id} / {policy.type}
                      </option>
                    ))}
                </AdminSelect>
              </div>
              <div className="space-y-2">
                <label className="text-[10px] font-black uppercase tracking-[0.2em] opacity-40 ml-1">或手动输入目标策略 ID</label>
                <AdminInput
                  type="number"
                  min="1"
                  value={migrationTargetPolicyId}
                  onChange={(event) => setMigrationTargetPolicyId(event.target.value)}
                  placeholder="例如 12"
                />
              </div>
            </div>
            <p className="text-[10px] font-bold leading-5 opacity-50">
              如果目标策略不在下拉框里，可以直接输入它的策略 ID。当前页面只负责创建迁移任务，不负责迁移进度展示。
            </p>
          </div>
        ) : null}
      </AdminDialog>
    </motion.div>
  );
}
