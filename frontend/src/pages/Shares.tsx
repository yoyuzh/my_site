import React, { useEffect, useMemo, useState } from 'react';
import DashboardLayout from '../components/DashboardLayout';
import { useMutation } from '@tanstack/react-query';
import { useMyShares } from '../api/queries';
import { formatBytes, formatDateTime } from '../lib/format';
import { getShareStats, updateSharePolicy } from '../lib/share-stats';
import type { ShareStats } from '../api/types';

const Shares: React.FC = () => {
  const [page, setPage] = useState(1);
  const [statsByToken, setStatsByToken] = useState<Record<string, ShareStats>>({});
  const [statsError, setStatsError] = useState<string | null>(null);
  const [loadingTokens, setLoadingTokens] = useState<string[]>([]);
  const { data, isLoading, isError, refetch } = useMyShares(page, 20);

  const updatePolicyMutation = useMutation({
    mutationFn: ({ id, maxDownloads }: { id: number; maxDownloads: number | null }) =>
      updateSharePolicy(id, maxDownloads),
    onSuccess: async (share) => {
      setStatsByToken((current) => ({
        ...current,
        [share.token]: {
          token: share.token,
          visits: share.viewCount,
          downloads: share.downloadCount,
          maxDownloads: share.maxDownloads,
          downloadLimitReached: share.maxDownloads != null && share.downloadCount >= share.maxDownloads,
        },
      }));
      void refetch();
      try {
        const refreshedStats = await getShareStats(share.token);
        setStatsByToken((current) => ({
          ...current,
          [share.token]: refreshedStats,
        }));
      } catch (error) {
        setStatsError(error instanceof Error ? error.message : '分享统计刷新失败');
      }
    },
  });

  const shares = useMemo(() => data?.items ?? [], [data]);

  useEffect(() => {
    const missingTokens = shares
      .map((share) => share.token)
      .filter((token) => statsByToken[token] == null);

    if (missingTokens.length === 0) {
      return;
    }

    let cancelled = false;
    setLoadingTokens((current) => Array.from(new Set([...current, ...missingTokens])));
    setStatsError(null);

    void Promise.allSettled(
      missingTokens.map(async (token) => ({
        token,
        stats: await getShareStats(token),
      })),
    ).then((results) => {
      if (cancelled) {
        return;
      }

      const loadedEntries: Record<string, ShareStats> = {};
      let hasFailure = false;
      for (const result of results) {
        if (result.status === 'fulfilled') {
          loadedEntries[result.value.token] = result.value.stats;
        } else {
          hasFailure = true;
        }
      }

      if (Object.keys(loadedEntries).length > 0) {
        setStatsByToken((current) => ({
          ...current,
          ...loadedEntries,
        }));
      }
      setLoadingTokens((current) => current.filter((token) => !missingTokens.includes(token)));
      if (hasFailure) {
        setStatsError('部分分享统计加载失败');
      }
    });

    return () => {
      cancelled = true;
    };
  }, [shares, statsByToken]);

  const openLimitPrompt = (shareId: number, currentLimit: number | null) => {
    const nextValue = window.prompt(
      '设置最大下载次数，留空表示不限次数',
      currentLimit == null ? '' : String(currentLimit),
    );
    if (nextValue == null) {
      return;
    }

    const normalized = nextValue.trim();
    if (normalized.length === 0) {
      updatePolicyMutation.mutate({ id: shareId, maxDownloads: null });
      return;
    }

    const parsed = Number.parseInt(normalized, 10);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      window.alert('请输入大于 0 的整数，或留空清除限制');
      return;
    }

    updatePolicyMutation.mutate({ id: shareId, maxDownloads: parsed });
  };

  return (
    <DashboardLayout title="分享 Shares">
      {isLoading ? (
        <div className="card-container p-10 text-center">加载中...</div>
      ) : isError ? (
        <div className="card-container p-10 text-center text-red-500">分享列表加载失败</div>
      ) : data && data.items.length > 0 ? (
        <div className="card-container divide-y divide-[#D9E3F2] dark:divide-[#222233]">
          {statsError ? (
            <div className="px-6 py-4 text-sm text-amber-600 dark:text-amber-400">{statsError}</div>
          ) : null}
          {data.items.map((share) => {
            const stats = statsByToken[share.token];
            const visits = stats?.visits ?? share.viewCount;
            const downloads = stats?.downloads ?? share.downloadCount;
            const maxDownloads = stats?.maxDownloads ?? share.maxDownloads;
            const limitReached = stats?.downloadLimitReached ?? (maxDownloads != null && downloads >= maxDownloads);
            const remainingDownloads = maxDownloads == null ? '不限次数' : `${Math.max(0, maxDownloads - downloads)} 次`;
            const loadingStats = loadingTokens.includes(share.token);

            return (
              <div key={share.id} className="px-6 py-5 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <div className="min-w-0">
                  <h3 className="truncate text-lg font-bold text-text-primary-light dark:text-white">
                    {share.shareName || share.file?.filename || share.token}
                  </h3>
                  <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark truncate">
                    分享者 {share.ownerUsername} · 下载 {downloads} 次 · 浏览 {visits} 次
                  </p>
                  <p className="mt-1 text-xs text-text-muted-light dark:text-text-muted-dark truncate">
                    {loadingStats ? '统计加载中...' : `剩余下载 ${remainingDownloads}`}
                    {limitReached ? ' · 已达上限' : ''}
                  </p>
                </div>
                <div className="flex items-center gap-3 lg:gap-4">
                  <div className="text-sm text-text-secondary-light dark:text-text-secondary-dark lg:text-right">
                    <p>{share.file ? formatBytes(share.file.size) : '-'}</p>
                    <p>{share.expiresAt ? `过期于 ${formatDateTime(share.expiresAt)}` : '永久有效'}</p>
                    <p>{maxDownloads == null ? '下载上限：不限' : `下载上限：${maxDownloads} 次`}</p>
                  </div>
                  <button
                    className="px-3 py-2 border rounded-lg text-sm font-medium border-[#BFD2F7] dark:border-[#222233] text-brand-light dark:text-white disabled:opacity-50"
                    disabled={updatePolicyMutation.isPending}
                    onClick={() => openLimitPrompt(share.id, maxDownloads)}
                  >
                    设置上限
                  </button>
                </div>
              </div>
            );
          })}
          <div className="px-6 py-4 flex items-center justify-between text-sm text-text-secondary-light dark:text-text-secondary-dark">
            <span>共 {data.pagination.total_items} 条分享</span>
            <div className="flex gap-2">
              <button className="px-3 py-1 border rounded disabled:opacity-50" disabled={page <= 1} onClick={() => setPage((current) => current - 1)}>上一页</button>
              <button className="px-3 py-1 border rounded bg-brand-light text-white border-brand-light">{page}</button>
              <button className="px-3 py-1 border rounded disabled:opacity-50" disabled={page >= data.pagination.total_pages} onClick={() => setPage((current) => current + 1)}>下一页</button>
            </div>
          </div>
        </div>
      ) : (
        <div className="card-container p-10 text-center flex flex-col items-center justify-center min-h-[400px]">
          <div className="w-16 h-16 rounded-full bg-brand-light/10 text-brand-light flex items-center justify-center mb-4">
            <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/><polyline points="16 6 12 2 8 6"/><line x1="12" x2="12" y1="2" y2="15"/></svg>
          </div>
          <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2">暂无分享链接</h3>
          <p className="text-text-secondary-light dark:text-text-secondary-dark font-geist max-w-md">
            你可以在文件页为文件创建分享链接，它们会显示在这里。
          </p>
        </div>
      )}
    </DashboardLayout>
  );
};

export default Shares;
