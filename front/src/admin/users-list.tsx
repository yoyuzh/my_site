import { useEffect, useMemo, useState } from 'react';
import { Ban, Check, Clipboard, KeyRound, PencilLine, RefreshCw, Search, Shield, Mail, Phone, X } from 'lucide-react';
import { motion } from 'motion/react';
import { useForm } from 'react-hook-form';
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
} from '@tanstack/react-table';
import { cn } from '@/src/lib/utils';
import {
  getAdminUsers,
  resetUserPassword,
  updateUserMaxUploadSize,
  updateUserPassword,
  updateUserRole,
  updateUserStatus,
  updateUserStorageQuota,
  type AdminUser,
} from '@/src/lib/admin-users';
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

const columnHelper = createColumnHelper<AdminUser>();

type UserEditorFormValues = {
  role: AdminUser['role'];
  storageQuotaBytes: string;
  maxUploadSizeBytes: string;
  manualPassword: string;
};

const EMPTY_EDITOR_FORM_VALUES: UserEditorFormValues = {
  role: 'USER',
  storageQuotaBytes: '',
  maxUploadSizeBytes: '',
  manualPassword: '',
};

function validateNonNegativeBytes(rawValue: string, label: string) {
  const trimmedValue = rawValue.trim();
  if (!trimmedValue) {
    return `${label}不能为空`;
  }

  const value = Number(trimmedValue);
  if (!Number.isFinite(value) || !Number.isInteger(value) || value < 0) {
    return `${label}必须是非负整数`;
  }

  return true;
}

function parseNonNegativeBytes(rawValue: string, label: string) {
  const validation = validateNonNegativeBytes(rawValue, label);
  if (validation !== true) {
    throw new Error(validation);
  }

  return Number(rawValue.trim());
}

export default function AdminUsersList() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [editingUser, setEditingUser] = useState<AdminUser | null>(null);
  const [temporaryPasswords, setTemporaryPasswords] = useState<Record<number, string>>({});
  const [copiedTemporaryPasswordUserId, setCopiedTemporaryPasswordUserId] = useState<number | null>(null);
  const {
    register,
    trigger,
    getValues,
    reset,
    resetField,
    watch,
    formState: { errors },
  } = useForm<UserEditorFormValues>({
    defaultValues: EMPTY_EDITOR_FORM_VALUES,
    mode: 'onSubmit',
    reValidateMode: 'onChange',
  });
  const watchedRole = watch('role');
  const columns = useMemo<ColumnDef<AdminUser, unknown>[]>(() => [
    columnHelper.display({
      id: 'userInfo',
      header: '用户信息',
      cell: ({ row }) => {
        const user = row.original;
        return (
          <div className="flex items-center gap-4">
            <div className="h-10 w-10 rounded-lg bg-blue-500/10 flex items-center justify-center font-black text-blue-500 border border-blue-500/20 shadow-inner">
              {user.username.charAt(0).toUpperCase()}
            </div>
            <div>
              <div className="text-[12px] font-black tracking-tight uppercase">{user.username}</div>
              <div className="text-[10px] opacity-40 font-bold flex items-center gap-1.5 mt-0.5"><Mail className="h-3 w-3" /> {user.email}</div>
              {user.phoneNumber ? <div className="mt-1 text-[9px] font-black opacity-20 tracking-widest flex items-center gap-1.5"><Phone className="h-3 w-3" />{user.phoneNumber}</div> : null}
            </div>
          </div>
        );
      },
    }),
    columnHelper.accessor('role', {
      header: '角色',
      cell: ({ row, getValue }) => {
        const user = row.original;
        return (
          <span className={cn(
            "inline-flex items-center gap-2 rounded-sm px-2 py-0.5 text-[9px] font-black uppercase tracking-widest border shadow-inner",
            getValue() === 'ADMIN'
              ? "bg-purple-500/10 text-purple-500 border-purple-500/20"
              : "bg-blue-500/10 text-blue-500 border-blue-500/20"
          )}>
            <Shield className="h-3 w-3" />
            {user.role}
          </span>
        );
      },
    }),
    columnHelper.accessor('banned', {
      header: '状态',
      cell: ({ getValue }) => (
        <span className={cn(
          "inline-flex items-center gap-2 rounded-sm px-2 py-0.5 text-[9px] font-black uppercase tracking-widest border",
          getValue()
            ? "bg-red-500/10 text-red-500 border-red-500/20"
            : "bg-green-500/10 text-green-500 border-green-500/20"
        )}>
          {getValue() ? '已禁用' : '正常'}
        </span>
      ),
    }),
    columnHelper.display({
      id: 'resources',
      header: '资源配额',
      cell: ({ row }) => {
        const user = row.original;
        return (
          <>
            <div className="text-[10px] font-black uppercase tracking-tight">
              {formatBytes(user.usedStorageBytes)} / <span className="opacity-30">{formatBytes(user.storageQuotaBytes)}</span>
            </div>
            <div className="mt-2 h-1 w-full max-w-[120px] rounded-full bg-white/10 overflow-hidden">
              <motion.div
                initial={{ width: 0 }}
                animate={{ width: `${Math.min(100, (user.usedStorageBytes / user.storageQuotaBytes) * 100)}%` }}
                className="h-full bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.5)]"
              />
            </div>
            <div className="mt-2 text-[9px] font-bold opacity-30 uppercase tracking-widest">
              上传上限：{formatBytes(user.maxUploadSizeBytes)}
            </div>
          </>
        );
      },
    }),
    columnHelper.accessor('createdAt', {
      header: '注册时间',
      cell: ({ getValue }) => (
        <div className="text-[10px] font-bold opacity-30 tracking-tighter uppercase">
          {formatDateTime(getValue())}
        </div>
      ),
    }),
    columnHelper.display({
      id: 'actions',
      header: '操作',
      cell: ({ row }) => {
        const user = row.original;
        return (
          <>
            <div className="flex justify-end gap-2 opacity-30 group-hover:opacity-100 transition-opacity">
              <button
                type="button"
                onClick={() => openEditor(user)}
                className="p-2.5 rounded-lg glass-panel hover:bg-blue-600 hover:text-white text-blue-500 transition-all border-white/10"
                title="打开编辑面板"
              >
                <PencilLine className="h-4 w-4" />
              </button>
              <button
                type="button"
                onClick={() => void generateTemporaryPassword(user.id)}
                className="p-2.5 rounded-lg glass-panel hover:bg-violet-500 hover:text-white text-violet-500 transition-all border-white/10"
                title="生成临时密码"
              >
                <RefreshCw className="h-4 w-4" />
              </button>
              <button
                type="button"
                onClick={() => void mutate(() => updateUserStatus(user.id, !user.banned))}
                className={cn(
                  "p-2.5 rounded-lg glass-panel border border-white/10 transition-all",
                  user.banned ? "hover:bg-green-500 hover:text-white text-green-500" : "hover:bg-red-500 hover:text-white text-red-500"
                )}
                title={user.banned ? '恢复账号' : '禁用账号'}
              >
                <Ban className="h-4 w-4" />
              </button>
            </div>
            {temporaryPasswords[user.id] ? (
              <div className="mt-4 rounded-lg border border-violet-500/20 bg-violet-500/10 px-4 py-3 text-left shadow-inner">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <div className="text-[9px] font-black uppercase tracking-[0.2em] text-violet-500">
                      临时密码已生成
                    </div>
                    <div className="mt-1 text-[10px] font-bold opacity-50">
                      请复制后立即告知用户，随后可关闭此提示
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() =>
                      setTemporaryPasswords((current) => {
                        const next = { ...current };
                        delete next[user.id];
                        return next;
                      })
                    }
                    className="rounded-full border border-white/10 p-1.5 text-violet-500 transition-colors hover:bg-violet-500 hover:text-white"
                    title="关闭临时密码提示"
                  >
                    <X className="h-3.5 w-3.5" />
                  </button>
                </div>
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <code className="rounded-md border border-white/10 bg-black/20 px-3 py-2 text-[11px] font-black tracking-[0.15em] text-white">
                    {temporaryPasswords[user.id]}
                  </code>
                  <button
                    type="button"
                    onClick={() => void copyTemporaryPassword(user.id, temporaryPasswords[user.id])}
                    className="inline-flex items-center gap-2 rounded-md border border-white/10 px-3 py-2 text-[10px] font-black uppercase tracking-widest text-violet-500 transition-colors hover:bg-violet-500 hover:text-white"
                  >
                    {copiedTemporaryPasswordUserId === user.id ? (
                      <>
                        <Check className="h-3.5 w-3.5" />
                        已复制
                      </>
                    ) : (
                      <>
                        <Clipboard className="h-3.5 w-3.5" />
                        复制
                      </>
                    )}
                  </button>
                </div>
              </div>
            ) : null}
          </>
        );
      },
    }),
  ], [copiedTemporaryPasswordUserId, copyTemporaryPassword, generateTemporaryPassword, mutate, openEditor, temporaryPasswords]);
  const table = useReactTable<AdminUser>({
    data: users,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getRowId: (row) => String(row.id),
  });

  async function loadUsers(nextQuery = query) {
    setError('');
    try {
      const result = await getAdminUsers(0, 100, nextQuery);
      setUsers(result.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载用户失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (!editingUser) {
      return;
    }
    const latestUser = users.find((user) => user.id === editingUser.id);
    if (!latestUser) {
      return;
    }
    setEditingUser(latestUser);
    reset(
      {
        role: latestUser.role,
        storageQuotaBytes: String(latestUser.storageQuotaBytes),
        maxUploadSizeBytes: String(latestUser.maxUploadSizeBytes),
        manualPassword: getValues('manualPassword'),
      }
    );
  }, [editingUser, users]);

  useEffect(() => {
    void loadUsers();
  }, []);

  function openEditor(user: AdminUser) {
    setError('');
    setEditingUser(user);
    reset({
      role: user.role,
      storageQuotaBytes: String(user.storageQuotaBytes),
      maxUploadSizeBytes: String(user.maxUploadSizeBytes),
      manualPassword: '',
    });
  }

  function closeEditor() {
    setEditingUser(null);
    reset(EMPTY_EDITOR_FORM_VALUES);
  }

  async function saveEditorProfile() {
    if (!editingUser) {
      return;
    }
    try {
      const isValid = await trigger(['role', 'storageQuotaBytes', 'maxUploadSizeBytes']);
      if (!isValid) {
        return;
      }

      const currentValues = getValues();
      const nextStorageQuotaBytes = parseNonNegativeBytes(currentValues.storageQuotaBytes, '存储配额');
      const nextMaxUploadSizeBytes = parseNonNegativeBytes(currentValues.maxUploadSizeBytes, '最大上传限制');

      await mutate(async () => {
        if (currentValues.role !== editingUser.role) {
          await updateUserRole(editingUser.id, currentValues.role);
        }
        if (nextStorageQuotaBytes !== editingUser.storageQuotaBytes) {
          await updateUserStorageQuota(editingUser.id, nextStorageQuotaBytes);
        }
        if (nextMaxUploadSizeBytes !== editingUser.maxUploadSizeBytes) {
          await updateUserMaxUploadSize(editingUser.id, nextMaxUploadSizeBytes);
        }
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存基础配置失败');
    }
  }

  async function submitManualPassword() {
    if (!editingUser) {
      return;
    }
    const isValid = await trigger('manualPassword');
    if (!isValid) {
      return;
    }
    const nextPassword = getValues('manualPassword').trim();
    await mutate(async () => {
      await updateUserPassword(editingUser.id, nextPassword);
      resetField('manualPassword');
      setTemporaryPasswords((current) => {
        const next = { ...current };
        delete next[editingUser.id];
        return next;
      });
    });
  }

  async function generateTemporaryPassword(userId: number) {
    await mutate(async () => {
      const result = await resetUserPassword(userId);
      setTemporaryPasswords((current) => ({
        ...current,
        [userId]: result.temporaryPassword,
      }));
      setCopiedTemporaryPasswordUserId(null);
    });
  }

  async function mutate(action: () => Promise<unknown>) {
    try {
      await action();
      await loadUsers();
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败');
    }
  }

  async function copyTemporaryPassword(userId: number, password: string) {
    try {
      await navigator.clipboard.writeText(password);
      setCopiedTemporaryPasswordUserId(userId);
      window.setTimeout(() => {
        setCopiedTemporaryPasswordUserId((current) => (current === userId ? null : current));
      }, 1500);
    } catch (err) {
      setError(err instanceof Error ? err.message : '复制临时密码失败');
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
          <h1 className="text-4xl font-black tracking-tight animate-text-reveal text-gray-900 dark:text-white">用户策略</h1>
          <p className="mt-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">角色 / 配额 / 上传限制 / 密码策略</p>
        </div>
        <button
          type="button"
          onClick={() => {
            setLoading(true);
            void loadUsers();
          }}
          className="flex items-center gap-3 px-6 py-3 rounded-lg glass-panel hover:bg-white/40 transition-all font-black text-[11px] uppercase tracking-widest"
        >
          <RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} />
          刷新列表
        </button>
      </div>

      <div className="mb-10 group">
        <div className="relative">
          <Search className="absolute left-5 top-1/2 h-4 w-4 -translate-y-1/2 opacity-30 group-focus-within:text-blue-500 transition-colors" />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                setLoading(true);
                void loadUsers(event.currentTarget.value);
              }
            }}
            placeholder="搜索用户名、邮箱或手机号...（回车）"
            className="w-full rounded-lg glass-panel bg-white/10 py-5 pl-14 pr-6 outline-none border border-white/10 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10 transition-all font-black text-[11px] uppercase tracking-widest placeholder:opacity-20"
          />
        </div>
      </div>

      {error ? <div className="mb-8 rounded-lg bg-red-500/10 border border-red-500/20 px-6 py-4 text-xs text-red-600 font-bold backdrop-blur-md uppercase tracking-widest">{error}</div> : null}

      <div className="grid flex-1 min-h-0 gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        {loading && users.length === 0 ? (
          <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">正在查询用户数据...</div>
        ) : (
          <>
            <div className="glass-panel-no-hover rounded-lg overflow-hidden shadow-3xl border border-white/10">
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-white/10">
                  <thead className="bg-white/10 dark:bg-black/40">
                    {table.getHeaderGroups().map((headerGroup) => (
                      <tr key={headerGroup.id}>
                        {headerGroup.headers.map((header) => (
                          <th
                            key={header.id}
                            className={cn(
                              "px-8 py-5 text-[9px] font-black uppercase tracking-[0.2em] opacity-40",
                              header.column.id === 'actions' ? 'text-right' : 'text-left'
                            )}
                          >
                            {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                          </th>
                        ))}
                      </tr>
                    ))}
                  </thead>
                  <motion.tbody 
                    variants={container}
                    initial="hidden"
                    animate="show"
                    className="divide-y divide-white/10 dark:divide-white/5"
                  >
                    {table.getRowModel().rows.map((row) => {
                      const user = row.original;
                      const isEditing = editingUser?.id === user.id;
                      return (
                        <motion.tr
                          key={row.id}
                          variants={itemVariants}
                          className={cn(
                            "group transition-colors",
                            isEditing ? "bg-blue-500/10 dark:bg-blue-500/5" : "hover:bg-white/10 dark:hover:bg-white/5"
                          )}
                        >
                          {row.getVisibleCells().map((cell) => (
                            <td
                              key={cell.id}
                              className={cn(
                                "px-8 py-5 align-top",
                                cell.column.id === 'actions' ? 'text-right' : 'text-left'
                              )}
                            >
                              {flexRender(cell.column.columnDef.cell, cell.getContext())}
                            </td>
                          ))}
                        </motion.tr>
                      );
                    })}
                    {table.getRowModel().rows.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="px-8 py-20 text-center text-[10px] font-black uppercase tracking-widest opacity-30">
                          暂无用户记录
                        </td>
                      </tr>
                    ) : null}
                  </motion.tbody>
                </table>
              </div>
            </div>

            <aside className="glass-panel-no-hover rounded-lg border border-white/10 p-6 shadow-3xl xl:sticky xl:top-6 xl:self-start">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="text-[9px] font-black uppercase tracking-[0.2em] opacity-30">用户策略编辑</div>
                  <h2 className="mt-2 text-lg font-black tracking-tight uppercase">
                    {editingUser ? editingUser.username : '请选择用户'}
                  </h2>
                  <p className="mt-2 text-[10px] font-bold opacity-40 leading-relaxed">
                    {editingUser
                      ? '这里负责角色、存储配额、最大上传限制和手动改密。临时密码生成仍保留在表格快捷操作里，避免和手动改密混在一起。'
                      : '从左侧表格点击“编辑”打开该用户的策略面板。'}
                  </p>
                </div>
                {editingUser ? (
                  <button
                    type="button"
                    onClick={closeEditor}
                    className="rounded-full border border-white/10 px-3 py-1.5 text-[10px] font-black uppercase tracking-widest opacity-50 transition-colors hover:bg-white/10 hover:opacity-100"
                  >
                    关闭
                  </button>
                ) : null}
              </div>

              {editingUser ? (
                <div className="mt-6 space-y-6">
                  <div className="rounded-lg border border-white/10 bg-white/5 px-4 py-4">
                    <div className="flex items-center justify-between gap-3">
                      <div>
                        <div className="text-[9px] font-black uppercase tracking-[0.2em] opacity-30">当前账号</div>
                        <div className="mt-2 text-[12px] font-black uppercase tracking-tight">{editingUser.email}</div>
                      </div>
                      <span
                        className={cn(
                          "inline-flex items-center gap-2 rounded-sm px-2 py-0.5 text-[9px] font-black uppercase tracking-widest border",
                          editingUser.banned
                            ? "bg-red-500/10 text-red-500 border-red-500/20"
                            : "bg-green-500/10 text-green-500 border-green-500/20"
                        )}
                      >
                        {editingUser.banned ? '已禁用' : '正常'}
                      </span>
                    </div>
                    <div className="mt-4 text-[10px] font-black uppercase tracking-tight">
                      {formatBytes(editingUser.usedStorageBytes)} / <span className="opacity-30">{formatBytes(editingUser.storageQuotaBytes)}</span>
                    </div>
                    <div className="mt-2 h-1 w-full rounded-full bg-white/10 overflow-hidden">
                      <div
                        className="h-full bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.5)]"
                        style={{ width: `${Math.min(100, (editingUser.usedStorageBytes / editingUser.storageQuotaBytes) * 100)}%` }}
                      />
                    </div>
                  </div>

                  <div className="space-y-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <div className="text-[9px] font-black uppercase tracking-[0.2em] opacity-30">基础配置</div>
                        <div className="mt-1 text-[11px] font-bold opacity-50">修改角色、存储配额和最大上传限制后，点击保存即可生效。</div>
                      </div>
                      <span className="inline-flex items-center gap-2 rounded-sm px-2 py-0.5 text-[9px] font-black uppercase tracking-widest border bg-blue-500/10 text-blue-500 border-blue-500/20">
                        <Shield className="h-3 w-3" />
                        {watchedRole}
                      </span>
                    </div>

                    <label className="block">
                      <span className="mb-2 block text-[9px] font-black uppercase tracking-[0.2em] opacity-30">角色</span>
                      <select
                        {...register('role', {
                          validate: (value) => (value === 'USER' || value === 'ADMIN' ? true : '请选择有效角色'),
                        })}
                        className="w-full rounded-lg border border-white/10 bg-white/10 px-4 py-3 text-[11px] font-black uppercase tracking-widest outline-none transition-colors focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
                      >
                        <option value="USER">USER - 普通用户</option>
                        <option value="ADMIN">ADMIN - 管理员</option>
                      </select>
                      {errors.role ? (
                        <p className="mt-2 text-[10px] font-bold uppercase tracking-widest text-red-500">
                          {errors.role.message}
                        </p>
                      ) : null}
                    </label>

                    <div className="grid gap-4 sm:grid-cols-2">
                      <label className="block">
                        <span className="mb-2 block text-[9px] font-black uppercase tracking-[0.2em] opacity-30">存储配额（字节）</span>
                        <input
                          {...register('storageQuotaBytes', {
                            validate: (value) => validateNonNegativeBytes(value, '存储配额'),
                          })}
                          inputMode="numeric"
                          className="w-full rounded-lg border border-white/10 bg-white/10 px-4 py-3 text-[11px] font-black tracking-widest outline-none transition-colors focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
                        />
                        {errors.storageQuotaBytes ? (
                          <p className="mt-2 text-[10px] font-bold uppercase tracking-widest text-red-500">
                            {errors.storageQuotaBytes.message}
                          </p>
                        ) : null}
                      </label>
                      <label className="block">
                        <span className="mb-2 block text-[9px] font-black uppercase tracking-[0.2em] opacity-30">最大上传限制（字节）</span>
                        <input
                          {...register('maxUploadSizeBytes', {
                            validate: (value) => validateNonNegativeBytes(value, '最大上传限制'),
                          })}
                          inputMode="numeric"
                          className="w-full rounded-lg border border-white/10 bg-white/10 px-4 py-3 text-[11px] font-black tracking-widest outline-none transition-colors focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10"
                        />
                        {errors.maxUploadSizeBytes ? (
                          <p className="mt-2 text-[10px] font-bold uppercase tracking-widest text-red-500">
                            {errors.maxUploadSizeBytes.message}
                          </p>
                        ) : null}
                      </label>
                    </div>

                    <button
                      type="button"
                      onClick={() => void saveEditorProfile()}
                      className="inline-flex w-full items-center justify-center gap-3 rounded-lg bg-blue-600 px-4 py-3 text-[10px] font-black uppercase tracking-[0.2em] text-white transition-colors hover:bg-blue-500"
                    >
                      <Check className="h-4 w-4" />
                      保存基础配置
                    </button>
                  </div>

                  <div className="space-y-4 rounded-lg border border-amber-500/20 bg-amber-500/10 px-4 py-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <div className="text-[9px] font-black uppercase tracking-[0.2em] text-amber-500">手动设置密码</div>
                        <div className="mt-1 text-[10px] font-bold opacity-60 leading-relaxed">
                          这里是人工指定一个新密码，会直接覆盖当前密码。它和“生成临时密码”是两条不同的管理路径。
                        </div>
                      </div>
                      <KeyRound className="h-4 w-4 text-amber-500" />
                    </div>
                    <label className="block">
                      <span className="mb-2 block text-[9px] font-black uppercase tracking-[0.2em] opacity-30">新密码</span>
                      <input
                        {...register('manualPassword', {
                          validate: (value) => (value.trim() ? true : '请输入要手动设置的新密码'),
                        })}
                        type="password"
                        autoComplete="new-password"
                        className="w-full rounded-lg border border-white/10 bg-black/20 px-4 py-3 text-[11px] font-black tracking-widest outline-none transition-colors focus:border-amber-500/50 focus:ring-4 focus:ring-amber-500/10"
                        placeholder="输入后点击“手动设置密码”"
                      />
                      {errors.manualPassword ? (
                        <p className="mt-2 text-[10px] font-bold uppercase tracking-widest text-amber-500">
                          {errors.manualPassword.message}
                        </p>
                      ) : null}
                    </label>
                    <button
                      type="button"
                      onClick={() => void submitManualPassword()}
                      className="inline-flex w-full items-center justify-center gap-3 rounded-lg border border-amber-500/20 bg-amber-500/15 px-4 py-3 text-[10px] font-black uppercase tracking-[0.2em] text-amber-500 transition-colors hover:bg-amber-500 hover:text-white"
                    >
                      <KeyRound className="h-4 w-4" />
                      手动设置密码
                    </button>
                    <p className="text-[10px] font-bold opacity-50 leading-relaxed">
                      适合人工恢复账号、统一初始化密码或和用户同步已知密码。若要发放一次性密码，请继续使用表格里的“生成临时密码”。
                    </p>
                  </div>

                  <div className="rounded-lg border border-white/10 bg-white/5 px-4 py-4">
                    <div className="text-[9px] font-black uppercase tracking-[0.2em] opacity-30">账号状态</div>
                    <div className="mt-2 text-[10px] font-bold opacity-50 leading-relaxed">
                      可直接切换禁用 / 恢复，不影响上面的基础配置或手动改密表单。
                    </div>
                    <button
                      type="button"
                      onClick={() => void mutate(() => updateUserStatus(editingUser.id, !editingUser.banned))}
                      className={cn(
                        "mt-4 inline-flex w-full items-center justify-center gap-3 rounded-lg border px-4 py-3 text-[10px] font-black uppercase tracking-[0.2em] transition-colors",
                        editingUser.banned
                          ? "border-green-500/20 bg-green-500/10 text-green-500 hover:bg-green-500 hover:text-white"
                          : "border-red-500/20 bg-red-500/10 text-red-500 hover:bg-red-500 hover:text-white"
                      )}
                    >
                      <Ban className="h-4 w-4" />
                      {editingUser.banned ? '恢复账号' : '禁用账号'}
                    </button>
                  </div>
                </div>
              ) : (
                <div className="mt-10 rounded-lg border border-dashed border-white/10 px-6 py-12 text-center">
                  <div className="text-[10px] font-black uppercase tracking-[0.2em] opacity-25">编辑面板为空</div>
                  <p className="mt-3 text-[11px] font-bold opacity-40 leading-relaxed">
                    点击左侧任意用户行的“编辑”按钮，右侧会自动展开该用户的角色、配额和密码管理表单。
                  </p>
                </div>
              )}
            </aside>
          </>
        )}
      </div>
    </motion.div>
  );
}
