import React, { useEffect, useMemo, useState } from 'react';
import DashboardLayout from '../components/DashboardLayout';
import { FolderPlus, MoreVertical, Share2, Star, Trash2, UploadCloud, X } from 'lucide-react';
import { useMutation } from '@tanstack/react-query';
import { useFavoriteFiles, useFiles } from '../api/queries';
import { formatBytes, formatDateTime } from '../lib/format';
import { batchDeleteFiles, createDirectory, createLegacyShareLink, getFileDetail, setFileFavorite } from '../lib/files';
import type { FileDetail } from '../api/types';
import FileThumbnail from '../components/media/FileThumbnail';

const Files: React.FC = () => {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [directoryName, setDirectoryName] = useState('');
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [detailFileId, setDetailFileId] = useState<number | null>(null);
  const [detail, setDetail] = useState<FileDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const { data, isLoading, isError, refetch } = useFiles('/', page, 20, search);
  const { data: favoriteFiles, refetch: refetchFavorites } = useFavoriteFiles();

  const createDirectoryMutation = useMutation({
    mutationFn: createDirectory,
    onSuccess: () => {
      setDirectoryName('');
      void refetch();
    },
  });

  const shareMutation = useMutation({
    mutationFn: createLegacyShareLink,
    onSuccess: (result) => {
      window.prompt('已创建分享链接 Token', result.token);
    },
  });

  const batchDeleteMutation = useMutation({
    mutationFn: batchDeleteFiles,
    onSuccess: () => {
      setSelectedIds([]);
      setDetailFileId(null);
      setDetail(null);
      void refetch();
      void refetchFavorites();
    },
  });

  const favoriteMutation = useMutation({
    mutationFn: ({ fileId, favorite }: { fileId: number; favorite: boolean }) => setFileFavorite(fileId, favorite),
    onSuccess: (_, variables) => {
      if (detailFileId === variables.fileId && detail) {
        setDetail({
          ...detail,
          favorite: variables.favorite,
        });
      }
      void refetchFavorites();
    },
  });

  const rows = useMemo(() => data?.items ?? [], [data]);
  const favoriteIds = useMemo(
    () => new Set((favoriteFiles ?? []).filter((item) => item.favorite).map((item) => item.fileId)),
    [favoriteFiles],
  );
  const allSelected = rows.length > 0 && rows.every((file) => selectedIds.includes(file.id));

  useEffect(() => {
    setSelectedIds((current) => current.filter((id) => rows.some((file) => file.id === id)));
  }, [rows]);

  useEffect(() => {
    if (detailFileId == null) {
      return;
    }

    let disposed = false;
    setDetailLoading(true);
    setDetailError(null);

    void getFileDetail(detailFileId)
      .then((result) => {
        if (!disposed) {
          setDetail(result);
        }
      })
      .catch((error: unknown) => {
        if (!disposed) {
          setDetailError(error instanceof Error ? error.message : '详情加载失败');
          setDetail(null);
        }
      })
      .finally(() => {
        if (!disposed) {
          setDetailLoading(false);
        }
      });

    return () => {
      disposed = true;
    };
  }, [detailFileId]);

  const toggleSelected = (fileId: number) => {
    setSelectedIds((current) =>
      current.includes(fileId) ? current.filter((id) => id !== fileId) : [...current, fileId],
    );
  };

  return (
    <DashboardLayout title="文件 Files">
      <div className="flex justify-between items-center mb-6">
        <div className="flex gap-2">
          <button className="btn-primary flex items-center gap-2 px-4 py-2 text-sm h-10">
            <UploadCloud size={16} /> 上传文件
          </button>
          <button
            className="bg-white dark:bg-transparent border border-[#BFD2F7] dark:border-[#222233] text-brand-light dark:text-white font-semibold py-2 px-4 rounded-lg transition-all duration-300 hover:bg-brand-light/5 text-sm h-10 flex items-center gap-2"
            onClick={() => {
              const nextName = window.prompt('请输入新文件夹名称', directoryName || '新建文件夹');
              if (nextName && nextName.trim()) {
                createDirectoryMutation.mutate(`/${nextName.trim()}`);
              }
            }}
          >
            <FolderPlus size={16} /> 新建文件夹
          </button>
          <button
            className="bg-white dark:bg-transparent border border-[#BFD2F7] dark:border-[#222233] text-brand-light dark:text-white font-semibold py-2 px-4 rounded-lg transition-all duration-300 hover:bg-brand-light/5 text-sm h-10 flex items-center gap-2 disabled:opacity-50"
            disabled={selectedIds.length === 0 || batchDeleteMutation.isPending}
            onClick={() => batchDeleteMutation.mutate(selectedIds)}
          >
            <Trash2 size={16} /> 删除所选 {selectedIds.length > 0 ? `(${selectedIds.length})` : ''}
          </button>
        </div>
        <div className="flex items-center gap-2">
          <input 
            type="text" 
            placeholder="搜索文件..." 
            className="input-field h-10 w-64 text-sm"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(1);
            }}
          />
        </div>
      </div>

      <div className="card-container">
        {isLoading ? (
          <div className="p-10 text-center text-text-muted-light">加载中...</div>
        ) : isError ? (
          <div className="p-10 text-center text-red-500">文件列表加载失败</div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-[#D9E3F2] dark:border-[#222233]">
                    <th className="px-4 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark w-12">
                      <input
                        type="checkbox"
                        className="rounded border-gray-300 cursor-pointer"
                        checked={allSelected}
                        onChange={() => setSelectedIds(allSelected ? [] : rows.map((file) => file.id))}
                      />
                    </th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">名称</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">大小</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark">创建时间</th>
                    <th className="px-6 py-4 text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark text-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((file) => (
                    <tr key={file.id} className="border-b border-[#D9E3F2] dark:border-[#222233] hover:bg-[#F8FBFF] dark:hover:bg-[#1A1A24] transition-colors">
                      <td className="px-4 py-4">
                        <input
                          type="checkbox"
                          className="rounded border-gray-300 cursor-pointer"
                          checked={selectedIds.includes(file.id)}
                          onChange={() => toggleSelected(file.id)}
                        />
                      </td>
                      <td className="px-6 py-4 text-sm font-medium text-text-primary-light dark:text-white">
                        <div className="flex items-center gap-3">
                          <FileThumbnail file={file} />
                          <div className="min-w-0">
                            <p className="truncate">{file.filename}</p>
                            <p className="truncate text-xs text-text-muted-light dark:text-text-muted-dark">{file.path}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark font-funnel">
                        {file.directory ? '-' : formatBytes(file.size)}
                      </td>
                      <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark font-funnel">
                        {formatDateTime(file.createdAt)}
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex justify-end gap-2">
                          <button
                            className={`w-9 h-9 flex items-center justify-center rounded-lg transition-colors ${
                              favoriteIds.has(file.id)
                                ? 'text-amber-500 bg-amber-500/10'
                                : 'text-text-muted-light hover:text-amber-500 hover:bg-amber-500/10'
                            }`}
                            onClick={() =>
                              favoriteMutation.mutate({
                                fileId: file.id,
                                favorite: !favoriteIds.has(file.id),
                              })
                            }
                            title={favoriteIds.has(file.id) ? '取消收藏' : '收藏'}
                          >
                            <Star size={18} fill={favoriteIds.has(file.id) ? 'currentColor' : 'none'} />
                          </button>
                          {!file.directory ? (
                            <button
                              className="w-9 h-9 flex items-center justify-center rounded-lg text-brand-light hover:text-brand-dark transition-colors"
                              onClick={() => shareMutation.mutate(file.id)}
                              title="创建分享链接"
                            >
                              <Share2 size={18} />
                            </button>
                          ) : null}
                          <button
                            className="w-9 h-9 flex items-center justify-center rounded-lg text-text-muted-light hover:text-brand-light transition-colors"
                            title="查看详情"
                            onClick={() => setDetailFileId(file.id)}
                          >
                            <MoreVertical size={18} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="p-4 border-t border-[#D9E3F2] dark:border-[#222233] flex items-center justify-between text-sm text-text-secondary-light dark:text-text-secondary-dark">
              <span>共 {data?.pagination.total_items ?? 0} 条记录</span>
              <div className="flex gap-2">
                <button
                  className="px-3 py-1 border border-[#D9E3F2] dark:border-[#222233] rounded disabled:opacity-50"
                  disabled={page <= 1}
                  onClick={() => setPage((current) => current - 1)}
                >
                  上一页
                </button>
                <button className="px-3 py-1 border border-brand-light rounded bg-brand-light text-white">
                  {page}
                </button>
                <button
                  className="px-3 py-1 border border-[#D9E3F2] dark:border-[#222233] rounded disabled:opacity-50"
                  disabled={page >= (data?.pagination.total_pages ?? 1)}
                  onClick={() => setPage((current) => current + 1)}
                >
                  下一页
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      {detailFileId != null ? (
        <aside className="mt-6 card-container p-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-sm text-text-muted-light dark:text-text-muted-dark">文件详情</p>
              <h3 className="mt-1 text-xl font-bold text-text-primary-light dark:text-white">
                {detail?.filename ?? '正在加载'}
              </h3>
            </div>
            <button
              className="w-9 h-9 flex items-center justify-center rounded-lg text-text-muted-light hover:text-brand-light hover:bg-black/5 dark:hover:bg-white/5 transition-colors"
              onClick={() => {
                setDetailFileId(null);
                setDetail(null);
                setDetailError(null);
              }}
              title="关闭详情"
            >
              <X size={18} />
            </button>
          </div>

          {detailLoading ? (
            <p className="mt-4 text-sm text-text-muted-light dark:text-text-muted-dark">正在加载文件详情...</p>
          ) : detailError ? (
            <p className="mt-4 text-sm text-red-500">{detailError}</p>
          ) : detail ? (
            <div className="mt-5 grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
              <div className="rounded-xl border border-[#D9E3F2] dark:border-[#222233] p-4">
                <p className="text-text-muted-light dark:text-text-muted-dark">路径</p>
                <p className="mt-1 font-medium text-text-primary-light dark:text-white break-all">{detail.path}</p>
              </div>
              <div className="rounded-xl border border-[#D9E3F2] dark:border-[#222233] p-4">
                <p className="text-text-muted-light dark:text-text-muted-dark">大小</p>
                <p className="mt-1 font-medium text-text-primary-light dark:text-white">
                  {detail.directory ? '-' : formatBytes(detail.size)}
                </p>
              </div>
              <div className="rounded-xl border border-[#D9E3F2] dark:border-[#222233] p-4">
                <p className="text-text-muted-light dark:text-text-muted-dark">创建时间</p>
                <p className="mt-1 font-medium text-text-primary-light dark:text-white">{formatDateTime(detail.createdAt)}</p>
              </div>
              <div className="rounded-xl border border-[#D9E3F2] dark:border-[#222233] p-4">
                <p className="text-text-muted-light dark:text-text-muted-dark">更新时间</p>
                <p className="mt-1 font-medium text-text-primary-light dark:text-white">{formatDateTime(detail.updatedAt)}</p>
              </div>
              <div className="rounded-xl border border-[#D9E3F2] dark:border-[#222233] p-4">
                <p className="text-text-muted-light dark:text-text-muted-dark">内容类型</p>
                <p className="mt-1 font-medium text-text-primary-light dark:text-white break-all">{detail.contentType || '-'}</p>
              </div>
              <div className="rounded-xl border border-[#D9E3F2] dark:border-[#222233] p-4">
                <p className="text-text-muted-light dark:text-text-muted-dark">状态</p>
                <p className="mt-1 font-medium text-text-primary-light dark:text-white">
                  {detail.favorite ? '已收藏' : '未收藏'} / {detail.shared ? '已共享' : '未共享'}
                </p>
              </div>
            </div>
          ) : null}
        </aside>
      ) : null}
    </DashboardLayout>
  );
};

export default Files;
