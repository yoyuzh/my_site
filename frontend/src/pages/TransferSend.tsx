import React, { useRef, useState } from 'react';
import DashboardLayout from '../components/DashboardLayout';
import { useMutation, useQuery } from '@tanstack/react-query';
import { createTransferSession, listMyOfflineTransferSessions } from '../lib/transfer';
import { formatBytes, formatDateTime } from '../lib/format';
import type { TransferMode } from '../api/types';

const TransferSend: React.FC = () => {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [mode, setMode] = useState<TransferMode>('ONLINE');
  const [createdSession, setCreatedSession] = useState<Awaited<ReturnType<typeof createTransferSession>> | null>(null);
  const offlineSessionsQuery = useQuery({
    queryKey: ['offlineTransferSessions'],
    queryFn: listMyOfflineTransferSessions,
  });
  const createSessionMutation = useMutation({
    mutationFn: (files: File[]) => createTransferSession(files, mode),
    onSuccess: (result) => {
      setCreatedSession(result);
    },
  });

  return (
    <DashboardLayout title="快传 Transfer">
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div className="card-container p-10 text-center flex flex-col items-center justify-center min-h-[400px]">
          <div className="w-16 h-16 rounded-full bg-brand-light/10 text-brand-light flex items-center justify-center mb-4">
            <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z"/><path d="m21.854 2.147-10.94 10.939"/></svg>
          </div>
          <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2">点对点极速传输</h3>
          <p className="text-text-secondary-light dark:text-text-secondary-dark font-geist max-w-md mb-8">
            当前页面已接入 `/api/transfer/sessions`。选择文件后会创建真实快传会话并返回取件码。
          </p>

          <div className="mb-6 flex gap-2">
            <button className={`px-4 py-2 rounded-lg border ${mode === 'ONLINE' ? 'bg-brand-light text-white border-brand-light' : 'border-[#D9E3F2] dark:border-[#222233]'}`} onClick={() => setMode('ONLINE')}>
              在线快传
            </button>
            <button className={`px-4 py-2 rounded-lg border ${mode === 'OFFLINE' ? 'bg-brand-light text-white border-brand-light' : 'border-[#D9E3F2] dark:border-[#222233]'}`} onClick={() => setMode('OFFLINE')}>
              离线快传
            </button>
          </div>

          <input
            ref={fileInputRef}
            type="file"
            multiple
            className="hidden"
            onChange={(event) => {
              const files = Array.from(event.target.files ?? []);
              if (files.length > 0) {
                createSessionMutation.mutate(files);
              }
            }}
          />

          <button className="btn-primary w-48" onClick={() => fileInputRef.current?.click()}>
            {createSessionMutation.isPending ? '创建中...' : '选择文件发送'}
          </button>

          {createdSession ? (
            <div className="mt-8 w-full max-w-xl rounded-2xl border border-[#D9E3F2] dark:border-[#222233] p-6 text-left">
              <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark">取件码</p>
              <h4 className="text-3xl font-bold text-brand-light dark:text-brand-dark tracking-[0.2em] mt-2">
                {createdSession.pickupCode}
              </h4>
              <p className="mt-3 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                {createdSession.mode} · 过期时间 {formatDateTime(createdSession.expiresAt)}
              </p>
              <div className="mt-4 space-y-2">
                {createdSession.files.map((file, index) => (
                  <div key={`${file.relativePath}-${index}`} className="flex items-center justify-between text-sm">
                    <span className="truncate">{file.relativePath}</span>
                    <span className="ml-3 shrink-0 text-text-muted-light dark:text-text-muted-dark">{formatBytes(file.size)}</span>
                  </div>
                ))}
              </div>
            </div>
          ) : null}
        </div>

        <div className="card-container p-6">
          <h3 className="text-lg font-bold text-text-primary-light dark:text-white mb-4">我的离线快传</h3>
          {offlineSessionsQuery.isLoading ? (
            <p className="text-sm text-text-muted-light dark:text-text-muted-dark">加载中...</p>
          ) : offlineSessionsQuery.data && offlineSessionsQuery.data.length > 0 ? (
            <div className="space-y-4">
              {offlineSessionsQuery.data.map((session) => (
                <div key={session.sessionId} className="rounded-xl border border-[#D9E3F2] dark:border-[#222233] p-4">
                  <p className="font-semibold text-text-primary-light dark:text-white">{session.pickupCode}</p>
                  <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark">
                    {session.mode} · {session.files.length} 个文件
                  </p>
                  <p className="text-xs text-text-muted-light dark:text-text-muted-dark mt-1">
                    过期于 {formatDateTime(session.expiresAt)}
                  </p>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-text-muted-light dark:text-text-muted-dark">暂无离线快传会话。</p>
          )}
        </div>
      </div>
    </DashboardLayout>
  );
};

export default TransferSend;
