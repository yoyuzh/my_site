import React, { useEffect, useState } from 'react';
import { RefreshCw, RotateCcw, Trash2, Folder, Clock3 } from 'lucide-react';
import { apiRequest } from '@/src/lib/api';
import type { PageResponse, RecycleBinItem } from '@/src/lib/types';
import { formatRecycleBinExpiresLabel, RECYCLE_BIN_RETENTION_DAYS } from '@/src/pages/recycle-bin-state';
import { Button } from '@/src/components/ui/button';

function formatFileSize(size: number) {
  if (size <= 0) return '—';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1);
  const value = size / 1024 ** index;
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

export default function MobileRecycleBin() {
  const [items, setItems] = useState<RecycleBinItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [restoringId, setRestoringId] = useState<number | null>(null);

  const loadRecycleBin = async () => {
    setLoading(true);
    setError('');
    try {
      const response = await apiRequest<PageResponse<RecycleBinItem>>('/files/recycle-bin?page=0&size=100');
      setItems(response.items);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '回收站加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadRecycleBin();
  }, []);

  const handleRestore = async (itemId: number) => {
    setRestoringId(itemId);
    setError('');
    try {
      await apiRequest(`/files/recycle-bin/${itemId}/restore`, { method: 'POST' });
      setItems((previous) => previous.filter((item) => item.id !== itemId));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '恢复失败');
    } finally {
      setRestoringId(null);
    }
  };

  return (
    <div className="flex flex-col h-full bg-[#07101D] text-slate-300 min-h-[100dvh] pb-24">
      {/* Mobile Top Header */}
      <header className="sticky top-0 z-40 flex items-center justify-between px-4 py-3 bg-[#07101D]/80 backdrop-blur-xl border-b border-white/5">
        <h1 className="text-lg font-bold text-white tracking-tight flex items-center gap-2">
          <Trash2 className="w-5 h-5 text-slate-400" />
          回收站
        </h1>
        <button
          onClick={() => void loadRecycleBin()}
          className="p-2 -mr-2 rounded-full hover:bg-white/10 active:bg-white/20 transition-colors"
          disabled={loading}
        >
          <RefreshCw className={`w-5 h-5 text-white ${loading ? 'animate-spin' : ''}`} />
        </button>
      </header>

      <div className="px-4 py-4 space-y-4">
        <div className="rounded-xl border border-white/5 bg-white/[0.02] p-4 text-xs text-slate-400 leading-relaxed shadow-sm">
          删除的文件会在此保留 {RECYCLE_BIN_RETENTION_DAYS} 天，到期后自动清理。
        </div>

        {error && (
          <div className="rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-200">
            {error}
          </div>
        )}

        {loading ? (
          <div className="py-20 text-center text-sm text-slate-500">正在加载回收站...</div>
        ) : items.length === 0 ? (
          <div className="py-20 flex flex-col items-center justify-center gap-4 opacity-60">
            <div className="w-16 h-16 rounded-full bg-white/5 flex items-center justify-center">
               <Trash2 className="w-8 h-8 text-slate-400" />
            </div>
            <p className="text-sm">回收站为空</p>
          </div>
        ) : (
          <div className="space-y-3">
            {items.map((item) => (
              <div key={item.id} className="rounded-2xl border border-white/5 bg-black/20 p-4 transition-colors active:bg-white/5 relative">
                <div className="flex items-start gap-3">
                  <div className="mt-0.5 rounded-xl border border-white/5 bg-white/[0.03] p-2 text-slate-300 shrink-0">
                    <Folder className="h-4 w-4" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-base font-medium text-white mb-1">{item.filename}</p>
                    <p className="truncate text-xs text-slate-500 mb-2">{item.path}</p>
                    <div className="flex flex-wrap items-center gap-2 text-[10px] text-slate-400">
                      <span>{item.directory ? '文件夹' : formatFileSize(item.size)}</span>
                      <span className="flex items-center gap-1 rounded bg-amber-500/10 px-1.5 py-0.5 text-amber-200/80">
                         <Clock3 className="h-3 w-3" />
                         {formatRecycleBinExpiresLabel(item.expiresAt)}
                      </span>
                    </div>
                  </div>
                </div>
                <div className="mt-4 pt-3 border-t border-white/5 flex justify-end">
                   <Button
                     size="sm"
                     variant="outline"
                     className="h-8 border-white/10 text-slate-300 shrink-0"
                     onClick={() => void handleRestore(item.id)}
                     disabled={restoringId === item.id}
                   >
                     <RotateCcw className="mr-1.5 h-3.5 w-3.5" />
                     {restoringId === item.id ? '恢复中' : '恢复'}
                   </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
