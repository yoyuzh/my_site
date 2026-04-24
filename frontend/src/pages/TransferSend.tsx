import React, { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import DashboardLayout from '../components/DashboardLayout';
import { useMutation, useQuery } from '@tanstack/react-query';
import { createTransferSession, listMyOfflineTransferSessions } from '../lib/transfer';
import { formatBytes, formatDateTime } from '../lib/format';
import { P2pSender, type P2pTransferProgress } from '../lib/p2p-transfer';
import type { TransferMode, TransferSessionResponse } from '../api/types';
import { TransferReceivePanel } from './TransferReceive';

function formatPercent(progress: P2pTransferProgress | null) {
  if (!progress || progress.totalBytes <= 0) {
    return 0;
  }
  return Math.min(100, Math.round((progress.sentBytes / progress.totalBytes) * 100));
}

const TransferSend: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get('tab') === 'receive' ? 'receive' : 'send';
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const senderRef = useRef<P2pSender | null>(null);
  const [mode, setMode] = useState<TransferMode>('ONLINE');
  const [createdSession, setCreatedSession] = useState<TransferSessionResponse | null>(null);
  const [onlineStatus, setOnlineStatus] = useState('');
  const [onlineError, setOnlineError] = useState('');
  const [onlineProgress, setOnlineProgress] = useState<P2pTransferProgress | null>(null);
  const [onlineComplete, setOnlineComplete] = useState(false);
  const [isStartingOnline, setIsStartingOnline] = useState(false);

  const offlineSessionsQuery = useQuery({
    queryKey: ['offlineTransferSessions'],
    queryFn: listMyOfflineTransferSessions,
  });
  const createOfflineSessionMutation = useMutation({
    mutationFn: (files: File[]) => createTransferSession(files, 'OFFLINE'),
    onSuccess: (result) => {
      setCreatedSession(result);
      void offlineSessionsQuery.refetch();
    },
  });

  useEffect(() => {
    return () => {
      senderRef.current?.stop();
      senderRef.current = null;
    };
  }, []);

  async function copyText(value: string) {
    try {
      await window.navigator.clipboard.writeText(value);
      setOnlineStatus('已复制到剪贴板');
    } catch {
      window.prompt('复制内容', value);
    }
  }

  async function startOnlineTransfer(files: File[]) {
    senderRef.current?.stop();
    setCreatedSession(null);
    setOnlineStatus('正在创建在线 P2P 会话...');
    setOnlineError('');
    setOnlineProgress(null);
    setOnlineComplete(false);
    setIsStartingOnline(true);

    try {
      const session = await createTransferSession(files, 'ONLINE');
      setCreatedSession(session);
      const sender = new P2pSender(session, files, {
        onStatus: setOnlineStatus,
        onError: setOnlineError,
        onProgress: setOnlineProgress,
        onComplete: () => setOnlineComplete(true),
      });
      senderRef.current = sender;
      await sender.start();
    } catch (error) {
      setOnlineError(error instanceof Error ? error.message : '在线 P2P 快传启动失败');
    } finally {
      setIsStartingOnline(false);
    }
  }

  function handleSelectedFiles(files: File[]) {
    if (files.length === 0) {
      return;
    }
    if (mode === 'ONLINE') {
      void startOnlineTransfer(files);
      return;
    }
    setOnlineStatus('');
    setOnlineError('');
    setOnlineProgress(null);
    setOnlineComplete(false);
    senderRef.current?.stop();
    createOfflineSessionMutation.mutate(files);
  }

  const receiveUrl = createdSession
    ? `${window.location.origin}/dashboard/transfer-send?tab=receive&code=${createdSession.pickupCode}`
    : '';
  const percent = formatPercent(onlineProgress);

  function switchTab(tab: 'send' | 'receive') {
    const nextParams = new URLSearchParams(searchParams);
    if (tab === 'receive') {
      nextParams.set('tab', 'receive');
    } else {
      nextParams.delete('tab');
      nextParams.delete('code');
    }
    setSearchParams(nextParams);
  }

  return (
    <DashboardLayout title="快传 Transfer">
      <div className="mb-6 flex flex-wrap gap-2">
        <button
          className={`px-4 py-2 rounded-lg border text-sm font-semibold transition-colors ${activeTab === 'send' ? 'bg-brand-light text-white border-brand-light' : 'bg-white dark:bg-transparent border-[#D9E3F2] dark:border-[#222233] text-text-secondary-light dark:text-text-secondary-dark'}`}
          onClick={() => switchTab('send')}
        >
          发送
        </button>
        <button
          className={`px-4 py-2 rounded-lg border text-sm font-semibold transition-colors ${activeTab === 'receive' ? 'bg-brand-light text-white border-brand-light' : 'bg-white dark:bg-transparent border-[#D9E3F2] dark:border-[#222233] text-text-secondary-light dark:text-text-secondary-dark'}`}
          onClick={() => switchTab('receive')}
        >
          接收
        </button>
      </div>

      {activeTab === 'receive' ? (
        <div className="card-container p-8 md:p-10">
          <TransferReceivePanel embedded />
        </div>
      ) : (
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div className="card-container p-10 text-center flex flex-col items-center justify-center min-h-[400px]">
          <div className="w-16 h-16 rounded-full bg-brand-light/10 text-brand-light flex items-center justify-center mb-4">
            <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z"/><path d="m21.854 2.147-10.94 10.939"/></svg>
          </div>
          <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2">点对点极速传输</h3>
          <p className="text-text-secondary-light dark:text-text-secondary-dark font-geist max-w-md mb-8">
            在线模式使用 WebRTC DataChannel 直连传输，后端只负责取件码和信令中转；离线模式仍会上传到服务器临时存储。
          </p>

          <div className="mb-6 flex gap-2">
            <button className={`px-4 py-2 rounded-lg border ${mode === 'ONLINE' ? 'bg-brand-light text-white border-brand-light' : 'border-[#D9E3F2] dark:border-[#222233]'}`} onClick={() => setMode('ONLINE')}>
              在线 P2P
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
              handleSelectedFiles(files);
              event.target.value = '';
            }}
          />

          <button
            className="btn-primary w-48 disabled:opacity-60 disabled:cursor-not-allowed"
            disabled={isStartingOnline || createOfflineSessionMutation.isPending}
            onClick={() => fileInputRef.current?.click()}
          >
            {isStartingOnline || createOfflineSessionMutation.isPending ? '创建中...' : '选择文件发送'}
          </button>

          {createdSession ? (
            <div className="mt-8 w-full max-w-xl rounded-2xl border border-[#D9E3F2] dark:border-[#222233] p-6 text-left">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark">取件码</p>
                  <h4 className="text-3xl font-bold text-brand-light dark:text-brand-dark tracking-[0.2em] mt-2">
                    {createdSession.pickupCode}
                  </h4>
                </div>
                <button
                  className="text-sm font-semibold text-brand-light dark:text-brand-dark"
                  onClick={() => void copyText(createdSession.pickupCode)}
                >
                  复制取件码
                </button>
              </div>
              <p className="mt-3 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                {createdSession.mode} · 过期时间 {formatDateTime(createdSession.expiresAt)}
              </p>

              {createdSession.mode === 'ONLINE' ? (
                <div className="mt-4 space-y-3">
                  <div className="rounded-xl bg-[#F8FBFF] dark:bg-black/20 p-4">
                    <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark break-all">
                      接收链接：{receiveUrl}
                    </p>
                    <button className="mt-2 text-sm font-semibold text-brand-light dark:text-brand-dark" onClick={() => void copyText(receiveUrl)}>
                      复制接收链接
                    </button>
                  </div>
                  <div>
                    <div className="flex justify-between text-xs text-text-muted-light dark:text-text-muted-dark mb-1">
                      <span>{onlineProgress?.fileName || onlineStatus || '等待接收端连接'}</span>
                      <span>{percent}%</span>
                    </div>
                    <div className="h-2 rounded-full bg-black/10 dark:bg-white/10 overflow-hidden">
                      <div className="h-full bg-brand-light dark:bg-brand-dark transition-all" style={{ width: `${percent}%` }} />
                    </div>
                  </div>
                  {onlineComplete && <p className="text-sm text-emerald-600 dark:text-emerald-400">P2P 文件发送完成</p>}
                  {onlineStatus && <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark">{onlineStatus}</p>}
                  {onlineError && <p className="text-sm text-red-500">{onlineError}</p>}
                </div>
              ) : (
                <div className="mt-4 space-y-2">
                  {createdSession.files.map((file, index) => (
                    <div key={`${file.relativePath}-${index}`} className="flex items-center justify-between text-sm">
                      <span className="truncate">{file.relativePath}</span>
                      <span className="ml-3 shrink-0 text-text-muted-light dark:text-text-muted-dark">{formatBytes(file.size)}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : null}
        </div>

        <div className="card-container p-6">
          <div className="mb-6 rounded-xl border border-[#D9E3F2] dark:border-[#222233] p-4">
            <h3 className="text-lg font-bold text-text-primary-light dark:text-white mb-2">接收在线快传</h3>
            <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark mb-3">对方给你取件码后，在当前快传页切到“接收”标签建立 P2P 连接。</p>
            <button className="text-sm font-semibold text-brand-light dark:text-brand-dark" onClick={() => switchTab('receive')}>
              切到接收标签
            </button>
          </div>

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
      )}
    </DashboardLayout>
  );
};

export default TransferSend;
