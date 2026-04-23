import React from 'react';
import AdminLayout from '../../components/AdminLayout';
import { Users, HardDrive, AlertTriangle, FileText, Share2, Activity } from 'lucide-react';
import { useAdminSummary } from '../../api/queries';
import { formatBytes, formatPercent } from '../../lib/format';

const AdminHome: React.FC = () => {
  const { data, isLoading, isError } = useAdminSummary();

  return (
    <AdminLayout title="管理面板">
      {isLoading ? (
        <div className="p-8 text-center text-text-muted-light">加载中...</div>
      ) : isError ? (
        <div className="p-8 text-center text-red-500">加载失败</div>
      ) : (
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        
        {/* Trend Summary Area */}
        <div className="lg:col-span-3 flex flex-col gap-6">
          <div className="card-container p-8 animate-fade-in-up">
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-[16px] font-semibold text-text-primary-light dark:text-white">趋势概览</h3>
              <span className="text-xs text-text-muted-light dark:text-text-muted-dark bg-black/5 dark:bg-white/5 px-3 py-1 rounded-full font-geist">生成于 10 分钟前</span>
            </div>
            <hr className="border-[#D9E3F2] dark:border-[#222233] mb-6" />
            
            {/* Chart Placeholder */}
            <div className="h-[350px] w-full flex flex-col items-center justify-center border border-dashed border-[#D9E3F2] dark:border-[#222233] rounded-lg bg-[#F8FBFF] dark:bg-[#111117]/50">
              <Activity size={48} className="text-brand-light/30 dark:text-brand-dark/30 mb-4" />
              <p className="text-text-secondary-light dark:text-text-secondary-dark font-medium">图表数据加载中...</p>
              <p className="text-xs text-text-muted-light dark:text-text-muted-dark mt-2 font-geist">此处将渲染按日期统计的用户、文件和分享趋势图 (Recharts)</p>
            </div>
          </div>
        </div>

        {/* Summary Quick Stats Area */}
        <div className="lg:col-span-1 flex flex-col gap-6">
          <div className="card-container p-6 animate-fade-in-up" style={{ animationDelay: '100ms' }}>
            <h3 className="text-[16px] font-semibold text-text-primary-light dark:text-white mb-4">系统状态汇总</h3>
            <hr className="border-[#D9E3F2] dark:border-[#222233] mb-6" />
            
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 p-2 rounded-lg">
                    <Users size={20} />
                  </div>
                  <div>
                    <p className="text-xs text-text-muted-light dark:text-text-muted-dark font-geist">总用户数</p>
                    <p className="text-[15px] font-bold text-text-primary-light dark:text-white font-funnel">{data?.totalUsers ?? 0}</p>
                  </div>
                </div>
              </div>

              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="bg-yellow-100 dark:bg-yellow-900/30 text-yellow-600 dark:text-yellow-500 p-2 rounded-lg">
                    <FileText size={20} />
                  </div>
                  <div>
                    <p className="text-xs text-text-muted-light dark:text-text-muted-dark font-geist">文件总数</p>
                    <p className="text-[15px] font-bold text-text-primary-light dark:text-white font-funnel">{data?.totalFiles ?? 0}</p>
                  </div>
                </div>
              </div>

              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400 p-2 rounded-lg">
                    <Share2 size={20} />
                  </div>
                  <div>
                    <p className="text-xs text-text-muted-light dark:text-text-muted-dark font-geist">分享下载总量</p>
                    <p className="text-[15px] font-bold text-text-primary-light dark:text-white font-funnel">{data?.shareDownloadCount ?? 0}</p>
                  </div>
                </div>
              </div>

              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="bg-pink-100 dark:bg-pink-900/30 text-pink-600 dark:text-pink-400 p-2 rounded-lg">
                    <FileText size={20} />
                  </div>
                  <div>
                    <p className="text-xs text-text-muted-light dark:text-text-muted-dark font-geist">收藏文件</p>
                    <p className="text-[15px] font-bold text-text-primary-light dark:text-white font-funnel">{data?.favoriteFileCount ?? 0}</p>
                  </div>
                </div>
              </div>

              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-400 p-2 rounded-lg">
                    <Activity size={20} />
                  </div>
                  <div>
                    <p className="text-xs text-text-muted-light dark:text-text-muted-dark font-geist">活跃任务</p>
                    <p className="text-[15px] font-bold text-text-primary-light dark:text-white font-funnel">{data?.activeTaskCount ?? 0}</p>
                  </div>
                </div>
              </div>

              <div className="flex items-center justify-between pt-4 border-t border-[#D9E3F2] dark:border-[#222233]">
                <div className="flex items-center gap-3 w-full">
                  <div className="bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 p-2 rounded-lg">
                    <HardDrive size={20} />
                  </div>
                  <div className="w-full">
                    <div className="flex justify-between items-center w-full">
                       <p className="text-xs text-text-muted-light dark:text-text-muted-dark font-geist">离线快传占用</p>
                       <p className="text-xs font-bold text-text-primary-light dark:text-white font-funnel">
                         {data ? formatPercent(data.offlineTransferStorageBytes, data.offlineTransferStorageLimitBytes) : '0%'}
                       </p>
                    </div>
                    <div className="w-full bg-gray-200 dark:bg-[#222233] rounded-full h-1.5 mt-1">
                      <div className="bg-purple-500 h-1.5 rounded-full" style={{ width: `${data ? formatPercent(data.offlineTransferStorageBytes, data.offlineTransferStorageLimitBytes) : '0%'}` }}></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          
          {/* Site URL Warning Component Placeholder */}
          <div className="card-container p-6 border-orange-200 dark:border-orange-900/50 bg-orange-50/50 dark:bg-orange-900/10 animate-fade-in-up" style={{ animationDelay: '200ms' }}>
             <div className="flex gap-3">
                <AlertTriangle className="text-orange-500 flex-shrink-0" size={20} />
                <div>
                   <h4 className="text-sm font-bold text-orange-700 dark:text-orange-400 mb-1">当前邀请码</h4>
                   <p className="text-xs text-orange-600/80 dark:text-orange-300/80 font-geist leading-relaxed">
                     {data?.inviteCode ? `注册邀请码：${data.inviteCode}` : '后端未返回邀请码。'}
                   </p>
                   <p className="mt-3 text-xs font-semibold text-orange-700 dark:text-orange-400">
                     当前累计存储：{data ? formatBytes(data.totalStorageBytes) : '-'}
                   </p>
                </div>
             </div>
          </div>
        </div>

      </div>
      )}
    </AdminLayout>
  );
};

export default AdminHome;
