import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Clock3, Folder, RefreshCw, RotateCcw, Trash2 } from 'lucide-react';

import { Button } from '@/src/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/src/components/ui/card';
import { apiRequest } from '@/src/lib/api';
import type { PageResponse, RecycleBinItem } from '@/src/lib/types';
import { AppPageShell } from '@/src/components/ui/AppPageShell';
import { PageToolbar } from '@/src/components/ui/PageToolbar';

import { formatRecycleBinExpiresLabel, RECYCLE_BIN_RETENTION_DAYS } from './recycle-bin-state';

function formatFileSize(size: number) {
  if (size <= 0) {
    return '—';
  }

  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1);
  const value = size / 1024 ** index;
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

export default function RecycleBin() {
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
      await apiRequest(`/files/recycle-bin/${itemId}/restore`, {
        method: 'POST',
      });
      setItems((previous) => previous.filter((item) => item.id !== itemId));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '恢复失败');
    } finally {
      setRestoringId(null);
    }
  };

  return (
    <AppPageShell
      toolbar={
        <PageToolbar
          title={
            <div className="flex items-center gap-3">
              <span>网盘回收站</span>
              <div className="hidden sm:flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs text-slate-300 font-normal">
                <Trash2 className="h-3.5 w-3.5" />
                回收站保留 {RECYCLE_BIN_RETENTION_DAYS} 天
              </div>
            </div>
          }
          actions={
            <>
              <Button variant="outline" className="h-9 border-white/10 bg-white/5 text-slate-200 hover:bg-white/10" onClick={() => void loadRecycleBin()} disabled={loading}>
                <RefreshCw className="mr-2 h-4 w-4" />
                刷新
              </Button>
              <Link to="/files" className="inline-flex h-9 items-center justify-center rounded-xl border border-white/10 bg-white/5 px-4 text-sm font-medium text-slate-200 transition-colors hover:bg-white/10">
                返回网盘
              </Link>
            </>
          }
        />
      }
    >
      <div className="p-4 md:p-6 mx-auto flex h-full w-full max-w-6xl flex-col gap-6">
        <Card className="overflow-hidden bg-transparent border-0 shadow-none">
          <CardContent className="p-0">
          {error ? (
            <div className="mb-4 rounded-2xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-200">
              {error}
            </div>
          ) : null}

          {loading ? (
            <div className="flex min-h-64 items-center justify-center text-sm text-slate-400">
              正在加载回收站...
            </div>
          ) : items.length === 0 ? (
            <div className="flex min-h-64 flex-col items-center justify-center gap-4 rounded-3xl border border-dashed border-white/10 bg-black/10 text-center">
              <div className="rounded-3xl border border-white/10 bg-white/5 p-4">
                <Trash2 className="h-8 w-8 text-slate-400" />
              </div>
              <div className="space-y-1">
                <p className="text-lg font-medium text-white">回收站为空</p>
                <p className="text-sm text-slate-400">删除后的文件会在这里保留 10 天。</p>
              </div>
            </div>
          ) : (
            <div className="space-y-4">
              {items.map((item) => (
                <div
                  key={item.id}
                  className="flex flex-col gap-4 rounded-3xl border border-white/10 bg-black/10 p-5 lg:flex-row lg:items-center lg:justify-between"
                >
                  <div className="min-w-0 space-y-3">
                    <div className="flex items-center gap-3">
                      <div className="rounded-2xl border border-white/10 bg-white/5 p-3 text-slate-200">
                        <Folder className="h-5 w-5" />
                      </div>
                      <div className="min-w-0">
                        <p className="truncate text-base font-semibold text-white">{item.filename}</p>
                        <p className="truncate text-sm text-slate-400">{item.path}</p>
                      </div>
                    </div>
                    <div className="flex flex-wrap items-center gap-3 text-xs text-slate-400">
                      <span>{item.directory ? '文件夹' : formatFileSize(item.size)}</span>
                      <span>删除于 {formatDateTime(item.deletedAt)}</span>
                      <span className="inline-flex items-center gap-1 rounded-full border border-amber-500/20 bg-amber-500/10 px-2.5 py-1 text-amber-200">
                        <Clock3 className="h-3.5 w-3.5" />
                        {formatRecycleBinExpiresLabel(item.expiresAt)}
                      </span>
                    </div>
                  </div>
                  <Button
                    className="min-w-28 self-start bg-[#336EFF] text-white hover:bg-[#2958cc] lg:self-center"
                    onClick={() => void handleRestore(item.id)}
                    disabled={restoringId === item.id}
                  >
                    <RotateCcw className="mr-2 h-4 w-4" />
                    {restoringId === item.id ? '恢复中' : '恢复'}
                  </Button>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
      </div>
    </AppPageShell>
  );
}
