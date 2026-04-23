import React, { useMemo, useState } from 'react';
import AdminLayout from '../../components/AdminLayout';
import { useMutation } from '@tanstack/react-query';
import { Filter, ListChecks } from 'lucide-react';
import { useAdminTasks } from '../../api/queries';
import type { AdminTask as AdminTaskItem } from '../../api/types';
import { formatDateTime } from '../../lib/format';
import { readTaskProgressSnapshot, rebuildSearchIndex } from '../../lib/tasks';

const AdminTask: React.FC = () => {
  const [showFilters, setShowFilters] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);
  const { data, isLoading, isError, refetch } = useAdminTasks({ page, page_size: pageSize });
  const selectedTask = useMemo(
    () => data?.items.find((task) => task.id === selectedTaskId) ?? null,
    [data, selectedTaskId],
  );
  const selectedTaskProgress = selectedTask ? readTaskProgressSnapshot(selectedTask.publicStateJson) : null;
  const rebuildMutation = useMutation({
    mutationFn: rebuildSearchIndex,
    onSuccess: () => void refetch(),
  });

  return (
    <AdminLayout title="离线下载与系统任务">
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-6 gap-4">
        <div className="flex items-center gap-2">
          <button
            className="bg-brand-light/10 text-brand-light hover:bg-brand-light/20 px-4 py-2 rounded-lg text-sm h-10 transition-colors font-medium disabled:opacity-50"
            disabled={rebuildMutation.isPending}
            onClick={() => rebuildMutation.mutate()}
          >
            重建搜索索引
          </button>
        </div>
        
        <div className="flex items-center gap-2 w-full md:w-auto">
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
            <label className="block text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark mb-1 ml-1">任务状态</label>
            <select className="input-field h-10 text-sm appearance-none py-0">
              <option value="">全部状态</option>
              <option value="0">排队中</option>
              <option value="1">处理中</option>
              <option value="2">失败</option>
              <option value="3">取消</option>
              <option value="4">完成</option>
            </select>
          </div>
          <div className="flex-1 min-w-[200px]">
            <label className="block text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark mb-1 ml-1">任务类型</label>
            <select className="input-field h-10 text-sm appearance-none py-0">
              <option value="">全部</option>
              <option value="1">压缩</option>
              <option value="2">解压</option>
              <option value="3">上传</option>
              <option value="4">下载</option>
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
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">任务类型</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">状态与进度</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">创建者</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">创建时间</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark text-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {(data?.items || []).map((task: AdminTaskItem) => {
                    const progress = readTaskProgressSnapshot(task.publicStateJson);
                    return (
                    <tr
                      key={task.id}
                      className={`border-b border-[#D9E3F2] dark:border-[#222233] hover:bg-[#F8FBFF] dark:hover:bg-[#1A1A24] transition-colors cursor-pointer ${
                        selectedTaskId === task.id ? 'bg-brand-light/5 dark:bg-brand-dark/10' : ''
                      }`}
                      onClick={() => setSelectedTaskId(task.id)}
                    >
                      <td className="px-6 py-4 text-sm">
                        <input type="checkbox" className="rounded border-gray-300 text-brand-light focus:ring-brand-light cursor-pointer" />
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark font-funnel">{task.id}</td>
                      <td className="px-6 py-4 text-sm font-medium text-text-primary-light dark:text-white flex items-center gap-3">
                        <ListChecks size={16} className="text-brand-light" />
                        {task.type}
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                        <div className="flex items-center gap-2">
                           <span className="bg-black/5 dark:bg-white/5 px-2 py-1 rounded font-funnel">{task.status}</span>
                           {(progress?.progressPercent || 0) > 0 && (
                              <div className="w-16 bg-gray-200 dark:bg-[#222233] rounded-full h-1.5 hidden lg:block">
                                <div className="bg-brand-light dark:bg-brand-dark h-1.5 rounded-full" style={{ width: `${progress?.progressPercent ?? 0}%` }}></div>
                              </div>
                           )}
                        </div>
                        <p className="mt-1 text-xs text-text-muted-light dark:text-text-muted-dark">
                          已处理 {progress?.processedItems ?? 0} / {progress?.totalItems ?? 0}
                        </p>
                      </td>
                      <td className="px-6 py-4 text-sm text-brand-light dark:text-brand-dark">
                        {task.ownerUsername || task.ownerEmail || 'Unknown'}
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark font-geist">{formatDateTime(task.createdAt)}</td>
                      <td className="px-6 py-4 text-right">
                        <button className="text-sm text-brand-light hover:text-brand-dark transition-colors" type="button">
                          详情
                        </button>
                      </td>
                    </tr>
                  )})}
                </tbody>
              </table>
            </div>

            {selectedTask ? (
              <div className="border-t border-[#D9E3F2] dark:border-[#222233] p-6">
                <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
                  <div>
                    <h3 className="text-lg font-semibold text-text-primary-light dark:text-white">任务详情 #{selectedTask.id}</h3>
                    <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark">
                      {selectedTask.type} · {selectedTask.status}
                    </p>
                  </div>
                  <div className="text-sm text-text-secondary-light dark:text-text-secondary-dark lg:text-right">
                    <p>进度 {selectedTaskProgress?.progressPercent ?? 0}%</p>
                    <p>已处理 {selectedTaskProgress?.processedItems ?? 0} / {selectedTaskProgress?.totalItems ?? 0}</p>
                  </div>
                </div>
                <div className="mt-4 h-2 w-full rounded-full bg-[#E8EEF8] dark:bg-[#1D2330]">
                  <div
                    className="h-2 rounded-full bg-brand-light transition-all dark:bg-brand-dark"
                    style={{ width: `${selectedTaskProgress?.progressPercent ?? 0}%` }}
                  />
                </div>
                <p className="mt-4 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                  {selectedTaskProgress?.message || selectedTask.errorMessage || selectedTask.correlationId || '暂无附加信息'}
                </p>
              </div>
            ) : null}

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

export default AdminTask;
