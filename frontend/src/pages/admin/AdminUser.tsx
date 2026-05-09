import React, { useState } from 'react';
import AdminLayout from '../../components/AdminLayout';
import { UserPlus, Search, Edit2, Lock, Trash2, Filter } from 'lucide-react';
import { useAdminUsers } from '../../api/queries';
import type { AdminUser as AdminUserRecord } from '../../api/types';
import {
  resetAdminUserPassword,
  updateAdminUserBanned,
  updateAdminUserMaxUploadSize,
  updateAdminUserPassword,
  updateAdminUserRole,
  updateAdminUserStorageQuota,
} from '../../api/mutations';

const roleLabels: Record<AdminUserRecord['role'], string> = {
  ADMIN: '管理员',
  MODERATOR: '协管员',
  USER: '普通用户',
};

const roleOptions: AdminUserRecord['role'][] = ['USER', 'MODERATOR', 'ADMIN'];

function parseRoleInput(value: string | null | undefined) {
  const normalized = value?.trim();
  if (!normalized) {
    return null;
  }
  const byLabel = Object.entries(roleLabels).find(([, label]) => label === normalized);
  if (byLabel) {
    return byLabel[0] as AdminUserRecord['role'];
  }
  return normalized.toUpperCase() as AdminUserRecord['role'];
}

function toPositiveNumber(value: string | null) {
  if (value == null || value.trim() === '') {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

const AdminUser: React.FC = () => {
  const [showFilters, setShowFilters] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [searchDraft, setSearchDraft] = useState('');
  const [query, setQuery] = useState('');
  const [statusMessage, setStatusMessage] = useState('');
  const { data, isLoading, isError, refetch } = useAdminUsers({ page, page_size: pageSize, query });

  async function runAction(action: () => Promise<unknown>, successMessage: string) {
    setStatusMessage('');
    try {
      await action();
      setStatusMessage(successMessage);
      await refetch();
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '操作失败');
    }
  }

  function applySearch() {
    setPage(1);
    setQuery(searchDraft.trim());
  }

  function resetSearch() {
    setSearchDraft('');
    setQuery('');
    setPage(1);
  }

  return (
    <AdminLayout title="用户管理">
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-6 gap-4">
        <div className="flex items-center gap-2">
          <button className="btn-primary flex items-center gap-2 px-4 py-2 text-sm h-10 disabled:opacity-50 disabled:cursor-not-allowed" disabled title="后端暂未提供创建用户接口">
            <UserPlus size={16} /> 添加用户
          </button>
          <button className="bg-red-500/10 text-red-500 px-4 py-2 rounded-lg text-sm h-10 transition-colors font-medium disabled:opacity-50 disabled:cursor-not-allowed" disabled title="后端暂未提供批量删除用户接口">
            批量删除
          </button>
        </div>
        
        <div className="flex items-center gap-2 w-full md:w-auto">
          <div className="relative flex-1 md:w-64">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted-light dark:text-text-muted-dark" size={16} />
            <input 
              type="text" 
              placeholder="搜索用户名或邮箱..." 
              className="input-field h-10 w-full text-sm pl-9"
              value={searchDraft}
              onChange={(event) => setSearchDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  applySearch();
                }
              }}
            />
          </div>
          <button 
            onClick={() => setShowFilters(!showFilters)}
            className={`border border-[#D9E3F2] dark:border-[#222233] h-10 px-3 rounded-lg transition-colors flex items-center justify-center gap-2 text-sm ${showFilters ? 'bg-brand-light/10 text-brand-light border-brand-light/30' : 'bg-card-light dark:bg-[#0A0A0A] text-text-secondary-light dark:text-text-secondary-dark'}`}
          >
            <Filter size={16} />
            <span className="hidden sm:inline">高级筛选</span>
          </button>
        </div>
      </div>

      {showFilters && (
        <div className="card-container p-4 mb-6 animate-fade-in-up flex flex-wrap gap-4 items-end admin-filter-panel">
          <div className="flex-1 min-w-[200px]">
            <label className="block text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark mb-1 ml-1">用户组</label>
            <select
              className="input-field h-10 text-sm appearance-none py-0 disabled:opacity-60"
              disabled
              title="当前用户列表接口暂未提供用户组筛选"
            >
              <option value="">全部用户组</option>
            </select>
          </div>
          <div className="flex-1 min-w-[200px]">
            <label className="block text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark mb-1 ml-1">账号状态</label>
            <select
              className="input-field h-10 text-sm appearance-none py-0 disabled:opacity-60"
              disabled
              title="当前用户列表接口暂未提供账号状态筛选"
            >
              <option value="">全部状态</option>
            </select>
          </div>
          <div className="flex gap-2">
            <button
              className="h-10 px-4 text-sm admin-secondary-button rounded-lg hover:bg-black/5 dark:hover:bg-white/5 transition-colors"
              onClick={resetSearch}
            >
              重置
            </button>
            <button className="h-10 px-4 text-sm bg-brand-light text-white rounded-lg hover:opacity-90 transition-opacity" onClick={applySearch}>
              应用筛选
            </button>
          </div>
        </div>
      )}

      {statusMessage ? (
        <div className="mb-4 rounded-lg border border-[#D9E3F2] dark:border-[#222233] bg-card-light dark:bg-[#111117] px-4 py-3 text-sm text-text-secondary-light dark:text-text-secondary-dark">
          {statusMessage}
        </div>
      ) : null}

      <div className="card-container animate-fade-in-up" style={{ animationDelay: '100ms' }}>
        {isLoading ? (
          <div className="p-8 text-center text-text-muted-light">加载中...</div>
        ) : isError ? (
          <div className="p-8 text-center text-red-500">加载失败</div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-[#D9E3F2] dark:border-[#222233]">
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">
                      <input type="checkbox" className="rounded border-gray-300 text-brand-light focus:ring-brand-light cursor-pointer" />
                    </th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">用户名</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">邮箱</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">用户组</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">状态</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark text-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {(data?.items || []).map((user) => (
                    <tr key={user.id} className="border-b border-[#D9E3F2] dark:border-[#222233] hover:bg-black/5 dark:hover:bg-white/5 transition-colors">
                      <td className="px-6 py-4 text-sm">
                        <input type="checkbox" className="rounded border-gray-300 text-brand-light focus:ring-brand-light cursor-pointer" />
                      </td>
                      <td className="px-6 py-4 text-sm font-medium text-text-primary-light dark:text-white">
                        {user.username}
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark font-geist">{user.email}</td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                        <span className="bg-black/5 dark:bg-white/5 px-2 py-1 rounded font-funnel">{roleLabels[user.role]}</span>
                      </td>
                      <td className="px-6 py-4 text-sm">
                        <span className={`px-2 py-1 rounded-full text-xs font-semibold ${user.banned ? 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400' : 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'}`}>
                          {user.banned ? '已封禁' : '正常'}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-right flex justify-end gap-2">
                        <button
                          className="text-brand-light hover:text-brand-dark transition-colors p-1"
                          title="修改角色"
                          onClick={() => {
                            const nextRole = parseRoleInput(window.prompt('输入新角色：普通用户 / 协管员 / 管理员', roleLabels[user.role]));
                            if (!nextRole) {
                              return;
                            }
                            if (!roleOptions.includes(nextRole as AdminUserRecord['role'])) {
                              setStatusMessage('角色只能是普通用户、协管员或管理员');
                              return;
                            }
                            void runAction(() => updateAdminUserRole(user.id, nextRole as AdminUserRecord['role']), '角色已更新');
                          }}
                        >
                          <Edit2 size={16} />
                        </button>
                        <button
                          className="text-orange-500 hover:text-orange-600 transition-colors p-1"
                          title="修改密码"
                          onClick={() => {
                            const newPassword = window.prompt(`输入 ${user.username} 的新密码`);
                            if (!newPassword) {
                              return;
                            }
                            void runAction(() => updateAdminUserPassword(user.id, newPassword), '密码已更新');
                          }}
                        >
                          <Lock size={16} />
                        </button>
                        <button
                          className="text-text-secondary-light hover:text-brand-light transition-colors p-1"
                          title={user.banned ? '解封用户' : '封禁用户'}
                          onClick={() => {
                            if (window.confirm(`确认${user.banned ? '解封' : '封禁'}用户 ${user.username}？`)) {
                              void runAction(() => updateAdminUserBanned(user.id, !user.banned), user.banned ? '用户已解封' : '用户已封禁');
                            }
                          }}
                        >
                          {user.banned ? '解封' : '封禁'}
                        </button>
                        <button
                          className="text-text-secondary-light hover:text-brand-light transition-colors p-1"
                          title="重置密码"
                          onClick={() => {
                            if (window.confirm(`确认重置 ${user.username} 的密码？`)) {
                              void runAction(async () => {
                                const result = await resetAdminUserPassword(user.id);
                                window.alert(`新密码：${result.newPassword}`);
                              }, '密码已重置');
                            }
                          }}
                        >
                          重置
                        </button>
                        <button
                          className="text-text-secondary-light hover:text-brand-light transition-colors p-1"
                          title="设置容量"
                          onClick={() => {
                            const quota = toPositiveNumber(window.prompt('输入存储容量字节数', String(user.storageQuotaBytes)));
                            if (quota == null) {
                              setStatusMessage('容量必须是正数');
                              return;
                            }
                            void runAction(() => updateAdminUserStorageQuota(user.id, quota), '存储容量已更新');
                          }}
                        >
                          容量
                        </button>
                        <button
                          className="text-text-secondary-light hover:text-brand-light transition-colors p-1"
                          title="设置最大上传"
                          onClick={() => {
                            const maxUpload = toPositiveNumber(window.prompt('输入最大上传字节数', String(user.maxUploadSizeBytes)));
                            if (maxUpload == null) {
                              setStatusMessage('最大上传大小必须是正数');
                              return;
                            }
                            void runAction(() => updateAdminUserMaxUploadSize(user.id, maxUpload), '最大上传大小已更新');
                          }}
                        >
                          上传
                        </button>
                        <button className="text-red-500 p-1 opacity-40 cursor-not-allowed" title="后端暂未提供删除用户接口" disabled>
                          <Trash2 size={16} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            
            {/* Pagination */}
            <div className="p-4 border-t border-[#D9E3F2] dark:border-[#222233] flex flex-col sm:flex-row justify-between items-center text-sm text-text-secondary-light dark:text-text-secondary-dark gap-4">
              <div className="flex items-center gap-4">
                <span>共 {data?.pagination?.total_items || 0} 条记录</span>
                <select 
                  className="bg-transparent border-none text-brand-light font-medium cursor-pointer outline-none hidden sm:block"
                  value={pageSize}
                  onChange={(e) => { setPageSize(Number(e.target.value)); setPage(1); }}
                >
                  <option value={10}>10 条/页</option>
                  <option value={20}>20 条/页</option>
                  <option value={50}>50 条/页</option>
                </select>
              </div>
              <div className="flex gap-2">
                <button 
                  className="px-3 py-1 border border-[#D9E3F2] dark:border-[#222233] rounded hover:bg-black/5 dark:hover:bg-white/5 transition-colors disabled:opacity-50 disabled:cursor-not-allowed" 
                  disabled={page <= 1}
                  onClick={() => setPage(page - 1)}
                >上一页</button>
                <button className="px-3 py-1 border border-[#D9E3F2] dark:border-[#222233] rounded hover:bg-black/5 dark:hover:bg-white/5 transition-colors bg-brand-light text-white border-brand-light">{page}</button>
                <button 
                  className="px-3 py-1 border border-[#D9E3F2] dark:border-[#222233] rounded hover:bg-black/5 dark:hover:bg-white/5 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                  disabled={!data?.pagination?.total_pages || page >= data.pagination.total_pages}
                  onClick={() => setPage(page + 1)}
                >下一页</button>
              </div>
            </div>
          </>
        )}
      </div>
    </AdminLayout>
  );
};

export default AdminUser;
