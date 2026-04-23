import React, { useState } from 'react';
import AdminLayout from '../../components/AdminLayout';
import { FileKey, Search, Trash2, Filter, Import } from 'lucide-react';
import { useAdminFiles } from '../../api/queries';
import { formatBytes, formatDateTime } from '../../lib/format';
import type { AdminFile as AdminFileItem } from '../../api/types';

function renderFileKind(file: AdminFileItem) {
  if (file.directory) {
    return '目录';
  }
  if (file.contentType) {
    return file.contentType;
  }
  return '未知类型';
}

const AdminFile: React.FC = () => {
  const [showFilters, setShowFilters] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const { data, isLoading, isError } = useAdminFiles({ page, page_size: pageSize });

  return (
    <AdminLayout title="物理文件">
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-6 gap-4">
        <div className="flex items-center gap-2">
          <button className="bg-white dark:bg-transparent border border-[#BFD2F7] dark:border-[#222233] text-brand-light dark:text-white font-semibold py-2 px-4 rounded-lg transition-all duration-300 hover:bg-brand-light/5 text-sm h-10 flex items-center gap-2">
            <Import size={16} /> 导入外部目录
          </button>
          <button className="bg-red-500/10 text-red-500 hover:bg-red-500/20 px-4 py-2 rounded-lg text-sm h-10 transition-colors font-medium">
            批量删除
          </button>
        </div>
        
        <div className="flex items-center gap-2 w-full md:w-auto">
          <div className="relative flex-1 md:w-64">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted-light dark:text-text-muted-dark" size={16} />
            <input 
              type="text" 
              placeholder="搜索文件名..." 
              className="input-field h-10 w-full text-sm pl-9"
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
          <div className="flex-1 min-w-[150px]">
            <label className="block text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark mb-1 ml-1">存储策略</label>
            <select className="input-field h-10 text-sm appearance-none py-0">
              <option value="">全部策略</option>
              <option value="1">默认本地存储</option>
              <option value="2">阿里云 OSS</option>
            </select>
          </div>
          <div className="flex-1 min-w-[150px]">
            <label className="block text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark mb-1 ml-1">所属用户</label>
            <input type="text" placeholder="输入用户ID" className="input-field h-10 text-sm py-0" />
          </div>
          <div className="flex-1 min-w-[150px]">
            <label className="block text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark mb-1 ml-1">包含直链</label>
            <select className="input-field h-10 text-sm appearance-none py-0">
              <option value="">全部</option>
              <option value="true">是</option>
              <option value="false">否</option>
            </select>
          </div>
          <div className="flex-1 min-w-[150px]">
            <label className="block text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark mb-1 ml-1">已分享</label>
            <select className="input-field h-10 text-sm appearance-none py-0">
              <option value="">全部</option>
              <option value="true">是</option>
              <option value="false">否</option>
            </select>
          </div>
          <div className="flex gap-2">
            <button className="h-10 px-4 text-sm bg-white dark:bg-black border border-[#D9E3F2] dark:border-[#222233] rounded-lg hover:bg-black/5 dark:hover:bg-white/5 transition-colors">
              重置
            </button>
            <button className="h-10 px-4 text-sm bg-brand-light text-white rounded-lg hover:opacity-90 transition-opacity">
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
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">文件</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">路径 / 类型</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">大小</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">所属用户</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">状态</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">创建时间</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark text-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {(data?.items || []).map((file: AdminFileItem) => (
                    <tr key={file.id} className="border-b border-[#D9E3F2] dark:border-[#222233] hover:bg-[#F8FBFF] dark:hover:bg-[#1A1A24] transition-colors">
                      <td className="px-6 py-4 text-sm">
                        <input type="checkbox" className="rounded border-gray-300 text-brand-light focus:ring-brand-light cursor-pointer" />
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark font-funnel">{file.id}</td>
                      <td className="px-6 py-4 text-sm font-medium text-text-primary-light dark:text-white flex items-center gap-3">
                        <FileKey size={16} className="text-brand-light" />
                        <div className="min-w-0">
                          <span className="truncate max-w-xs font-geist block">{file.filename}</span>
                          <span className="truncate max-w-xs text-xs text-text-muted-light dark:text-text-muted-dark block">
                            {file.directory ? '目录条目' : '文件条目'}
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                        <div className="truncate max-w-[260px]">{file.path}</div>
                        <div className="mt-1 text-xs text-text-muted-light dark:text-text-muted-dark">
                          {renderFileKind(file)}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark font-funnel">
                        {file.directory ? '-' : formatBytes(file.size)}
                      </td>
                      <td className="px-6 py-4 text-sm text-brand-light dark:text-brand-dark">
                        <div>{file.ownerUsername || 'Unknown'}</div>
                        <div className="mt-1 text-xs text-text-muted-light dark:text-text-muted-dark">
                          {file.ownerEmail || '无邮箱'}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                        <div className="flex flex-wrap gap-2">
                          <span className={`px-2 py-1 rounded-full text-xs font-medium ${file.favorite ? 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300' : 'bg-black/5 text-text-secondary-light dark:bg-white/5 dark:text-text-secondary-dark'}`}>
                            {file.favorite ? '已收藏' : '未收藏'}
                          </span>
                          <span className={`px-2 py-1 rounded-full text-xs font-medium ${file.thumbnailAvailable ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300' : 'bg-black/5 text-text-secondary-light dark:bg-white/5 dark:text-text-secondary-dark'}`}>
                            {file.thumbnailAvailable ? '有缩略图' : '无缩略图'}
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark font-geist">
                        {formatDateTime(file.createdAt)}
                      </td>
                      <td className="px-6 py-4 text-right flex justify-end gap-2">
                        <button className="text-red-500 hover:text-red-600 transition-colors p-1" title="删除">
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

export default AdminFile;
