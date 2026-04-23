import React, { useEffect, useMemo, useState } from 'react';
import DashboardLayout from '../components/DashboardLayout';
import { useTasks } from '../api/queries';
import { formatDateTime } from '../lib/format';
import { getTaskProgress, readTaskProgressSnapshot } from '../lib/tasks';
import type { BackgroundTask, TaskProgress } from '../api/types';

const Tasks: React.FC = () => {
  const [page, setPage] = useState(1);
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);
  const [progress, setProgress] = useState<TaskProgress | null>(null);
  const [progressLoading, setProgressLoading] = useState(false);
  const [progressError, setProgressError] = useState<string | null>(null);
  const { data, isLoading, isError } = useTasks(page, 20);
  const selectedTask = useMemo(
    () => data?.items.find((task) => task.id === selectedTaskId) ?? null,
    [data, selectedTaskId],
  );
  const selectedTaskSnapshot = selectedTask ? readTaskProgressSnapshot(selectedTask.publicStateJson) : null;

  useEffect(() => {
    if (!data?.items.length) {
      setSelectedTaskId(null);
      return;
    }
    if (selectedTaskId == null || !data.items.some((task) => task.id === selectedTaskId)) {
      setSelectedTaskId(data.items[0].id);
    }
  }, [data, selectedTaskId]);

  useEffect(() => {
    if (selectedTaskId == null) {
      setProgress(null);
      setProgressError(null);
      return;
    }

    let cancelled = false;
    setProgressLoading(true);
    setProgressError(null);

    void getTaskProgress(selectedTaskId)
      .then((result) => {
        if (!cancelled) {
          setProgress(result);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setProgress(null);
          setProgressError(error instanceof Error ? error.message : '任务进度加载失败');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setProgressLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [selectedTaskId]);

  return (
    <DashboardLayout title="任务 Tasks">
      {isLoading ? (
        <div className="card-container p-10 text-center">加载中...</div>
      ) : isError ? (
        <div className="card-container p-10 text-center text-red-500">任务列表加载失败</div>
      ) : data && data.items.length > 0 ? (
        <div className="space-y-6">
          <div className="card-container divide-y divide-[#D9E3F2] dark:divide-[#222233]">
          {data.items.map((task: BackgroundTask) => (
            <div
              key={task.id}
              className={`px-6 py-5 flex cursor-pointer flex-col gap-2 transition-colors lg:flex-row lg:items-center lg:justify-between ${
                selectedTaskId === task.id ? 'bg-brand-light/5 dark:bg-brand-dark/10' : ''
              }`}
              onClick={() => setSelectedTaskId(task.id)}
            >
              <div>
                <h3 className="font-bold text-text-primary-light dark:text-white">{task.type}</h3>
                <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark">
                  状态 {task.status} · 创建于 {formatDateTime(task.createdAt)}
                </p>
              </div>
              <div className="text-sm text-text-muted-light dark:text-text-muted-dark lg:text-right">
                <p>ID #{task.id}</p>
                <p>{task.errorMessage || task.correlationId || '无附加信息'}</p>
              </div>
            </div>
          ))}
          <div className="px-6 py-4 flex items-center justify-between text-sm text-text-secondary-light dark:text-text-secondary-dark">
            <span>共 {data.pagination.total_items} 条任务</span>
            <div className="flex gap-2">
              <button className="px-3 py-1 border rounded disabled:opacity-50" disabled={page <= 1} onClick={() => setPage((current) => current - 1)}>上一页</button>
              <button className="px-3 py-1 border rounded bg-brand-light text-white border-brand-light">{page}</button>
              <button className="px-3 py-1 border rounded disabled:opacity-50" disabled={page >= data.pagination.total_pages} onClick={() => setPage((current) => current + 1)}>下一页</button>
            </div>
          </div>
        </div>
          {selectedTask ? (
            <div className="card-container p-6">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <h3 className="text-lg font-bold text-text-primary-light dark:text-white">
                    任务详情 #{selectedTask.id}
                  </h3>
                  <p className="mt-1 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                    {selectedTask.type} · 当前状态 {progress?.status || selectedTask.status}
                  </p>
                </div>
                <div className="text-right text-sm text-text-secondary-light dark:text-text-secondary-dark">
                  <p>{progressLoading ? '进度加载中...' : `进度 ${progress?.progressPercent ?? selectedTaskSnapshot?.progressPercent ?? 0}%`}</p>
                  <p>
                    已处理 {progress?.processedItems ?? selectedTaskSnapshot?.processedItems ?? 0} /{' '}
                    {progress?.totalItems ?? selectedTaskSnapshot?.totalItems ?? 0}
                  </p>
                </div>
              </div>
              <div className="mt-4 h-2 w-full rounded-full bg-[#E8EEF8] dark:bg-[#1D2330]">
                <div
                  className="h-2 rounded-full bg-brand-light transition-all dark:bg-brand-dark"
                  style={{ width: `${progress?.progressPercent ?? selectedTaskSnapshot?.progressPercent ?? 0}%` }}
                />
              </div>
              <p className="mt-4 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                {progressError || progress?.message || selectedTaskSnapshot?.message || selectedTask.errorMessage || '暂无详细进度信息'}
              </p>
            </div>
          ) : null}
        </div>
      ) : (
        <div className="card-container p-10 text-center flex flex-col items-center justify-center min-h-[400px]">
          <div className="w-16 h-16 rounded-full bg-brand-light/10 text-brand-light flex items-center justify-center mb-4">
            <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 11 3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
          </div>
          <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2">当前没有进行中的任务</h3>
          <p className="text-text-secondary-light dark:text-text-secondary-dark font-geist max-w-md">
            所有的上传、下载、离线下载或解压任务都会显示在这里。
          </p>
        </div>
      )}
    </DashboardLayout>
  );
};

export default Tasks;
