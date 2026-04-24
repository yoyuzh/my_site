import React, { useState } from 'react';
import AdminLayout from '../../components/AdminLayout';
import { FolderKey, Search, Filter, Trash2 } from 'lucide-react';
import { useAdminBlobs } from '../../api/queries';
import { formatBytes, formatDateTime } from '../../lib/format';
import type { AdminFileBlob } from '../../api/types';

const AdminBlob: React.FC = () => {
  const [showFilters, setShowFilters] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [objectKeyDraft, setObjectKeyDraft] = useState('');
  const [objectKey, setObjectKey] = useState('');
  const [userDraft, setUserDraft] = useState('');
  const [userQuery, setUserQuery] = useState('');
  const [policyDraft, setPolicyDraft] = useState('');
  const [storagePolicyId, setStoragePolicyId] = useState<number | undefined>(undefined);
  const { data, isLoading, isError } = useAdminBlobs({
    page,
    page_size: pageSize,
    objectKey,
    userQuery,
    storagePolicyId,
  });

  function applyFilters() {
    const parsedPolicy = Number(policyDraft);
    setObjectKey(objectKeyDraft.trim());
    setUserQuery(userDraft.trim());
    setStoragePolicyId(policyDraft.trim() && Number.isFinite(parsedPolicy) ? parsedPolicy : undefined);
    setPage(1);
  }

  function resetFilters() {
    setObjectKeyDraft('');
    setObjectKey('');
    setUserDraft('');
    setUserQuery('');
    setPolicyDraft('');
    setStoragePolicyId(undefined);
    setPage(1);
  }

  return (
    <AdminLayout title="文件记录 (Entity)">
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-6 gap-4">
        <div className="flex items-center gap-2">
           <button
             className="bg-red-500/10 text-red-500 hover:bg-red-500/20 px-4 py-2 rounded-lg text-sm h-10 transition-colors font-medium disabled:opacity-50 disabled:cursor-not-allowed"
             disabled
             title="后端暂未提供批量删除文件实体接口"
           >
            批量删除
          </button>
        </div>

        <div className="flex items-center gap-2 w-full md:w-auto">
          <div className="relative flex-1 md:w-64">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted-light dark:text-text-muted-dark" size={16} />
            <input 
              type="text" 
              placeholder="搜索 object key..."
              className="input-field h-10 w-full text-sm pl-9"
              value={objectKeyDraft}
              onChange={(event) => setObjectKeyDraft(event.target.value)}
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
            <label className="block text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark mb-1 ml-1">存储策略</label>
            <input
              type="number"
              min={1}
              placeholder="输入策略 ID"
              className="input-field h-10 text-sm py-0"
              value={policyDraft}
              onChange={(event) => setPolicyDraft(event.target.value)}
            />
          </div>
          <div className="flex-1 min-w-[200px]">
             <label className="block text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark mb-1 ml-1">上传者 ID</label>
             <input
               type="text"
               placeholder="输入用户名或邮箱"
               className="input-field h-10 text-sm py-0"
               value={userDraft}
               onChange={(event) => setUserDraft(event.target.value)}
               onKeyDown={(event) => {
                 if (event.key === 'Enter') {
                   applyFilters();
                 }
               }}
             />
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
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">存储策略</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">物理文件路径/Hash</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">大小</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">引用计数</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark text-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {(data?.items || []).map((blob: AdminFileBlob) => (
                    <tr key={`${blob.entityType}-${blob.entityId}-${blob.blobId}`} className="border-b border-[#D9E3F2] dark:border-[#222233] hover:bg-[#F8FBFF] dark:hover:bg-[#1A1A24] transition-colors">
                      <td className="px-6 py-4 text-sm">
                        <input type="checkbox" className="rounded border-gray-300 text-brand-light focus:ring-brand-light cursor-pointer" />
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark font-funnel">{blob.entityId}</td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                         <span className="bg-black/5 dark:bg-white/5 px-2 py-1 rounded font-funnel">
                           {blob.storagePolicyId == null ? '未绑定策略' : `#${blob.storagePolicyId}`}
                         </span>
                      </td>
                      <td className="px-6 py-4 text-sm font-medium text-text-primary-light dark:text-white flex items-center gap-3">
                        <FolderKey size={16} className="text-brand-light flex-shrink-0" />
                        <span className="truncate max-w-xs font-geist">{blob.objectKey || '无 object key'}</span>
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark font-funnel">
                        {blob.size == null ? '-' : formatBytes(blob.size)}
                        <div className="mt-1 text-xs text-text-muted-light dark:text-text-muted-dark">{blob.contentType || blob.entityType}</div>
                      </td>
                      <td className="px-6 py-4 text-sm font-bold text-brand-light dark:text-brand-dark font-funnel">
                        {blob.referenceCount ?? blob.linkedStoredFileCount}
                        <div className="mt-1 text-xs text-text-muted-light dark:text-text-muted-dark font-normal">
                          {blob.createdAt ? formatDateTime(blob.createdAt) : '-'}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-right flex justify-end gap-2">
                        <button
                          className="text-red-500 hover:text-red-600 transition-colors p-1 disabled:opacity-50 disabled:cursor-not-allowed"
                          title="后端暂未提供删除文件实体接口"
                          disabled
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

export default AdminBlob;
