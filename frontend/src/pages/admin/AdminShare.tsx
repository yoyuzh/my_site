import React, { useState } from 'react';
import AdminLayout from '../../components/AdminLayout';
import { Share2, Search, Link as LinkIcon, Trash2, Filter } from 'lucide-react';
import { useAdminShares } from '../../api/queries';
import { deleteAdminShare } from '../../api/mutations';
import { formatDateTime } from '../../lib/format';
import { buildFullShareUrl } from '../../lib/shares';
import type { AdminShare as AdminShareItem } from '../../api/types';

function formatShareLimit(value: number | null) {
  return value == null ? '不限' : String(value);
}

const AdminShare: React.FC = () => {
  const [showFilters, setShowFilters] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [searchDraft, setSearchDraft] = useState('');
  const [fileName, setFileName] = useState('');
  const [ownerDraft, setOwnerDraft] = useState('');
  const [userQuery, setUserQuery] = useState('');
  const [expiredDraft, setExpiredDraft] = useState('');
  const [expired, setExpired] = useState<boolean | undefined>(undefined);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const { data, isLoading, isError, refetch } = useAdminShares({
    page,
    page_size: pageSize,
    fileName,
    userQuery,
    expired,
  });

  function applyFilters() {
    setFileName(searchDraft.trim());
    setUserQuery(ownerDraft.trim());
    setExpired(expiredDraft === '' ? undefined : expiredDraft === 'true');
    setPage(1);
  }

  function resetFilters() {
    setSearchDraft('');
    setFileName('');
    setOwnerDraft('');
    setUserQuery('');
    setExpiredDraft('');
    setExpired(undefined);
    setPage(1);
  }

  async function copyShareLink(share: AdminShareItem) {
    const url = buildFullShareUrl(share.token);
    try {
      await window.navigator.clipboard.writeText(url);
      setStatusMessage(`已复制分享链接：${url}`);
    } catch {
      window.prompt('复制分享链接', url);
    }
  }

  async function handleDelete(share: AdminShareItem) {
    const label = share.shareName || share.fileName || share.token;
    if (!window.confirm(`确认取消分享「${label}」？`)) {
      return;
    }

    try {
      await deleteAdminShare(share.id);
      setStatusMessage(`已取消分享：${label}`);
      await refetch();
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '取消分享失败');
    }
  }

  return (
    <AdminLayout title="分享管理">
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-6 gap-4">
        <div className="flex items-center gap-2">
           <button
             className="bg-red-500/10 text-red-500 hover:bg-red-500/20 px-4 py-2 rounded-lg text-sm h-10 transition-colors font-medium disabled:opacity-50 disabled:cursor-not-allowed"
             disabled
             title="后端暂未提供批量删除分享接口"
           >
            批量删除
          </button>
        </div>
        
        <div className="flex items-center gap-2 w-full md:w-auto">
          <div className="relative flex-1 md:w-64">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted-light dark:text-text-muted-dark" size={16} />
            <input 
              type="text" 
              placeholder="搜索分享文件名..."
              className="input-field h-10 w-full text-sm pl-9"
              value={searchDraft}
              onChange={(event) => setSearchDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  applyFilters();
                }
              }}
            />
          </div>
          <button 
            onClick={() => setShowFilters(!showFilters)}
            className={`border border-[#D9E3F2] dark:border-[#222233] h-10 px-3 rounded-lg transition-colors flex items-center justify-center gap-2 text-sm ${showFilters ? 'bg-brand-light/10 text-brand-light border-brand-light/30' : 'bg-white dark:bg-[#0A0A0A] text-text-secondary-light dark:text-text-secondary-dark'}`}
          >
            <Filter size={16} />
            <span className="hidden sm:inline">筛选</span>
          </button>
        </div>
      </div>

      {showFilters && (
        <div className="card-container p-4 mb-6 animate-fade-in-up flex flex-wrap gap-4 items-end bg-[#F8FBFF] dark:bg-[#111117]/80">
          <div className="flex-1 min-w-[200px]">
            <label className="block text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark mb-1 ml-1">创建者</label>
            <input
              type="text"
              placeholder="输入创建者用户名或邮箱"
              className="input-field h-10 text-sm py-0"
              value={ownerDraft}
              onChange={(event) => setOwnerDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  applyFilters();
                }
              }}
            />
          </div>
          <div className="flex-1 min-w-[200px]">
            <label className="block text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark mb-1 ml-1">状态</label>
            <select
              className="input-field h-10 text-sm appearance-none py-0"
              value={expiredDraft}
              onChange={(event) => setExpiredDraft(event.target.value)}
            >
              <option value="">全部状态</option>
              <option value="false">正常</option>
              <option value="true">已过期</option>
            </select>
          </div>
          <div className="flex gap-2">
            <button
              className="h-10 px-4 text-sm bg-white dark:bg-black border border-[#D9E3F2] dark:border-[#222233] rounded-lg hover:bg-black/5 dark:hover:bg-white/5 transition-colors"
              onClick={resetFilters}
            >
              重置
            </button>
            <button
              className="h-10 px-4 text-sm bg-brand-light text-white rounded-lg hover:opacity-90 transition-opacity"
              onClick={applyFilters}
            >
              应用
            </button>
          </div>
        </div>
      )}

      {statusMessage && (
        <div className="mb-4 rounded-lg border border-[#D9E3F2] dark:border-[#222233] bg-white dark:bg-[#111117] px-4 py-3 text-sm text-text-secondary-light dark:text-text-secondary-dark">
          {statusMessage}
        </div>
      )}

      <div className="card-container animate-fade-in-up">
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
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">#</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">分享</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">所属用户</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">浏览/下载</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">状态</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">过期时间</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark text-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {(data?.items || []).map((share: AdminShareItem) => (
                    <tr key={share.id} className="border-b border-[#D9E3F2] dark:border-[#222233] hover:bg-[#F8FBFF] dark:hover:bg-[#1A1A24] transition-colors">
                      <td className="px-6 py-4 text-sm">
                        <input type="checkbox" className="rounded border-gray-300 text-brand-light focus:ring-brand-light cursor-pointer" />
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark font-funnel">{share.id}</td>
                      <td className="px-6 py-4 text-sm font-medium text-text-primary-light dark:text-white flex items-center gap-3">
                        <Share2 size={16} className="text-brand-light" />
                        <div className="min-w-0">
                          <div className="truncate max-w-xs">{share.shareName || share.fileName || share.token}</div>
                          <div className="mt-1 truncate max-w-xs text-xs text-text-muted-light dark:text-text-muted-dark">
                            {share.filePath || share.token}
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-brand-light dark:text-brand-dark">
                        <div>{share.ownerUsername || 'Unknown'}</div>
                        <div className="mt-1 text-xs text-text-muted-light dark:text-text-muted-dark">
                          {share.ownerEmail || '无邮箱'}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark font-funnel">
                        {share.viewCount} / {share.downloadCount}
                        <div className="mt-1 text-xs text-text-muted-light dark:text-text-muted-dark">
                          上限 {formatShareLimit(share.maxDownloads)}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                        <div className="flex flex-wrap gap-2">
                          <span className={`px-2 py-1 rounded-full text-xs font-medium ${share.passwordProtected ? 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300' : 'bg-black/5 text-text-secondary-light dark:bg-white/5 dark:text-text-secondary-dark'}`}>
                            {share.passwordProtected ? '受密码保护' : '公开访问'}
                          </span>
                          <span className={`px-2 py-1 rounded-full text-xs font-medium ${share.expired ? 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300' : 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300'}`}>
                            {share.expired ? '已过期' : '有效'}
                          </span>
                          <span className="px-2 py-1 rounded-full text-xs font-medium bg-black/5 text-text-secondary-light dark:bg-white/5 dark:text-text-secondary-dark">
                            {share.allowDownload ? '可下载' : '禁下载'}
                          </span>
                          <span className="px-2 py-1 rounded-full text-xs font-medium bg-black/5 text-text-secondary-light dark:bg-white/5 dark:text-text-secondary-dark">
                            {share.allowImport ? '可导入' : '禁导入'}
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark font-geist">{share.expiresAt ? formatDateTime(share.expiresAt) : '永久有效'}</td>
                      <td className="px-6 py-4 text-right flex justify-end gap-2">
                        <button
                          className="text-brand-light hover:text-brand-dark transition-colors p-1"
                          title="复制链接"
                          onClick={() => void copyShareLink(share)}
                        >
                          <LinkIcon size={16} />
                        </button>
                        <button
                          className="text-red-500 hover:text-red-600 transition-colors p-1"
                          title="取消分享"
                          onClick={() => void handleDelete(share)}
                        >
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

export default AdminShare;
