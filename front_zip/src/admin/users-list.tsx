import { useEffect, useState } from 'react';
import { Ban, KeyRound, RefreshCw, Search, Shield, Upload, Mail, Phone, ChevronRight } from 'lucide-react';
import { motion } from 'motion/react';
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

export default function AdminUsersList() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [users, setUsers] = useState<AdminUser[]>([]);

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
    void loadUsers();
  }, []);

  async function mutate(action: () => Promise<unknown>) {
    try {
      await action();
      await loadUsers();
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败');
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
          <h1 className="text-4xl font-black tracking-tight animate-text-reveal text-gray-900 dark:text-white">身份管理</h1>
          <p className="mt-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">用户权限 / 身份档案</p>
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

      <div className="flex-1 min-h-0">
        {loading && users.length === 0 ? (
          <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">正在查询用户数据...</div>
        ) : (
          <div className="glass-panel-no-hover rounded-lg overflow-hidden shadow-3xl border border-white/10">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-white/10">
                <thead className="bg-white/10 dark:bg-black/40">
                  <tr>
                    <th className="px-8 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">用户信息</th>
                    <th className="px-8 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">角色</th>
                    <th className="px-8 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">状态</th>
                    <th className="px-8 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">资源配额</th>
                    <th className="px-8 py-5 text-left text-[9px] font-black uppercase tracking-[0.2em] opacity-40">注册时间</th>
                    <th className="px-8 py-5 text-right text-[9px] font-black uppercase tracking-[0.2em] opacity-40">操作</th>
                  </tr>
                </thead>
                <motion.tbody 
                  variants={container}
                  initial="hidden"
                  animate="show"
                  className="divide-y divide-white/10 dark:divide-white/5"
                >
                  {users.map((user) => (
                    <motion.tr key={user.id} variants={itemVariants} className="hover:bg-white/10 dark:hover:bg-white/5 transition-colors group">
                      <td className="px-8 py-5">
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
                      </td>
                      <td className="px-8 py-5">
                        <span className={cn(
                          "inline-flex items-center gap-2 rounded-sm px-2 py-0.5 text-[9px] font-black uppercase tracking-widest border shadow-inner",
                          user.role === 'ADMIN' 
                            ? "bg-purple-500/10 text-purple-500 border-purple-500/20" 
                            : "bg-blue-500/10 text-blue-500 border-blue-500/20"
                        )}>
                          <Shield className="h-3 w-3" />
                          {user.role}
                        </span>
                      </td>
                      <td className="px-8 py-5">
                        <span className={cn(
                          "inline-flex items-center gap-2 rounded-sm px-2 py-0.5 text-[9px] font-black uppercase tracking-widest border",
                          user.banned 
                            ? "bg-red-500/10 text-red-500 border-red-500/20" 
                            : "bg-green-500/10 text-green-500 border-green-500/20"
                        )}>
                          {user.banned ? '已禁用' : '正常'}
                        </span>
                      </td>
                      <td className="px-8 py-5">
                        <div className="text-[10px] font-black uppercase tracking-tight">
                          {formatBytes(user.usedStorageBytes)} / <span className="opacity-30">{formatBytes(user.storageQuotaBytes)}</span>
                        </div>
                        <div className="mt-2 h-1 w-full max-w-[120px] rounded-full bg-white/10 overflow-hidden">
                          <motion.div 
                            initial={{ width: 0 }}
                            animate={{ width: `${Math.min(100, (user.usedStorageBytes / user.storageQuotaBytes) * 100)}%` }}
                            className="h-full bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.5)]" 
                          ></motion.div>
                        </div>
                        <div className="mt-2 text-[9px] font-bold opacity-30 uppercase tracking-widest">
                          上传上限：{formatBytes(user.maxUploadSizeBytes)}
                        </div>
                      </td>
                      <td className="px-8 py-5 text-[10px] font-bold opacity-30 tracking-tighter uppercase">
                        {formatDateTime(user.createdAt)}
                      </td>
                      <td className="px-8 py-5 text-right">
                        <div className="flex justify-end gap-2 opacity-30 group-hover:opacity-100 transition-opacity">
                          <button
                            type="button"
                            onClick={() =>
                              void mutate(async () => {
                                const nextRole = window.prompt('设置角色：USER 或 ADMIN', user.role);
                                if (!nextRole || (nextRole !== 'USER' && nextRole !== 'ADMIN')) {
                                  return;
                                }
                                await updateUserRole(user.id, nextRole);
                              })
                            }
                            className="p-2.5 rounded-lg glass-panel hover:bg-blue-600 hover:text-white text-blue-500 transition-all border-white/10"
                            title="修改角色"
                          >
                            <Shield className="h-4 w-4" />
                          </button>
                          <button
                            type="button"
                            onClick={() =>
                              void mutate(async () => {
                                const nextQuota = window.prompt('设置存储配额（字节）', String(user.storageQuotaBytes));
                                if (!nextQuota) return;
                                await updateUserStorageQuota(user.id, Number(nextQuota));
                              })
                            }
                            className="p-2.5 rounded-lg glass-panel hover:bg-blue-600 hover:text-white text-blue-500 transition-all border-white/10"
                            title="修改配额"
                          >
                            <Upload className="h-4 w-4" />
                          </button>
                          <button
                            type="button"
                            onClick={() =>
                              void mutate(async () => {
                                const newPassword = window.prompt('设置新密码');
                                if (!newPassword) return;
                                await updateUserPassword(user.id, newPassword);
                              })
                            }
                            className="p-2.5 rounded-lg glass-panel hover:bg-amber-500 hover:text-white text-amber-500 transition-all border-white/10"
                            title="重置密码"
                          >
                            <KeyRound className="h-4 w-4" />
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
                      </td>
                    </motion.tr>
                  ))}
                  {users.length === 0 ? (
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
        )}
      </div>
    </motion.div>
  );
}
