import React, { useState } from 'react';
import AdminLayout from '../../components/AdminLayout';
import { Database, Plus, Edit2, Trash2, ArrowRight } from 'lucide-react';
import { useAdminPolicies } from '../../api/queries';

const AdminPolicy: React.FC = () => {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const { data, isLoading, isError } = useAdminPolicies({ page, page_size: pageSize });

  return (
    <AdminLayout title="存储策略">
      <div className="flex justify-between items-center mb-6">
        <button className="btn-primary flex items-center gap-2 px-4 py-2 text-sm h-10">
          <Plus size={16} /> 添加存储策略
        </button>
      </div>

      {isLoading ? (
        <div className="p-8 text-center text-text-muted-light">加载中...</div>
      ) : isError ? (
        <div className="p-8 text-center text-red-500">加载失败</div>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 animate-fade-in-up">
            {(data?.items || []).map((policy: any) => (
              <div key={policy.id} className="card-container flex flex-col h-full">
                <div className="p-6 flex-1">
                  <div className="flex justify-between items-start mb-4">
                    <div className="bg-brand-light/10 text-brand-light p-3 rounded-lg">
                      <Database size={24} />
                    </div>
                    <div className="flex gap-2">
                      <button className="text-text-muted-light hover:text-brand-light transition-colors p-1" title="编辑">
                        <Edit2 size={18} />
                      </button>
                      <button className="text-text-muted-light hover:text-red-500 transition-colors p-1" title="删除">
                        <Trash2 size={18} />
                      </button>
                    </div>
                  </div>
                  <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2">{policy.name}</h3>
                  <div className="flex items-center gap-2 mb-4">
                    <span className="bg-black/5 dark:bg-white/5 px-2 py-1 rounded text-xs font-bold text-text-secondary-light dark:text-text-secondary-dark">{policy.type}</span>
                    <span className="text-xs text-text-muted-light dark:text-text-muted-dark">
                      {policy.type === 'Local' ? '本地服务器磁盘' : policy.type === 'OSS' ? '对象存储' : policy.type === 'S3' ? '海外对象存储节点' : '存储策略'}
                    </span>
                  </div>
                  <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark font-geist break-all bg-gray-50 dark:bg-[#1A1A24] p-2 rounded">
                    {policy.dir_name_rule || policy.path || '未指定路径'}
                  </p>
                </div>
                <div className="p-4 border-t border-[#D9E3F2] dark:border-[#222233] bg-[#F8FBFF] dark:bg-[#1A1A24]/50 flex justify-between items-center group cursor-pointer hover:bg-brand-light/5 transition-colors">
                   <span className="text-sm font-semibold text-brand-light dark:text-brand-dark">修改策略向导</span>
                   <ArrowRight size={16} className="text-brand-light dark:text-brand-dark transform group-hover:translate-x-1 transition-transform" />
                </div>
              </div>
            ))}
          </div>

          {/* Pagination */}
          <div className="p-4 mt-6 border-t border-[#D9E3F2] dark:border-[#222233] flex flex-col sm:flex-row justify-between items-center text-sm text-text-secondary-light dark:text-text-secondary-dark gap-4">
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
    </AdminLayout>
  );
};

export default AdminPolicy;