import React, { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import DashboardLayout from '../components/DashboardLayout';
import { useMutation, useQuery } from '@tanstack/react-query';
import { createTransferSession, listMyOfflineTransferSessions, uploadOfflineTransferFile } from '../lib/transfer';
import { formatBytes, formatDateTime } from '../lib/format';
import { P2pSender, type P2pTransferProgress } from '../lib/p2p-transfer';
import type { TransferMode, TransferSessionResponse } from '../api/types';
import { TransferReceivePanel } from './TransferReceive';
import { 
  Send, 
  Clock, 
  Link as LinkIcon, 
  Copy, 
  FileText, 
  CheckCircle2, 
  X, 
  ArrowRight,
  Info,
  History,
  Check
} from 'lucide-react';
import { clsx } from 'clsx';

function formatPercent(progress: P2pTransferProgress | null) {
  if (!progress || progress.totalBytes <= 0) {
    return 0;
  }
  return Math.min(100, Math.round((progress.sentBytes / progress.totalBytes) * 100));
}

function getFriendlyStatus(status: string) {
  if (!status) return '';
  if (status.includes('创建在线 P2P 会话')) return '正在准备发送入口...';
  if (status.includes('已创建取件码')) return '等待对方进入';
  if (status.includes('接收端已加入')) return '对方已进入，正在建立连接';
  if (status.includes('接收端已连接')) return '正在发送';
  if (status.includes('建立 P2P 连接')) return '正在建立连接';
  if (status.includes('建立连接')) return '正在建立连接';
  if (status.includes('发送文件')) return '正在发送';
  if (status.includes('文件发送完成')) return '发送完成';
  if (status.includes('连接已建立')) return '连接成功';
  if (status.includes('连接已断开')) return '连接已断开';
  return status;
}

function getFriendlyError(error: string) {
  if (!error) return '';
  if (error.includes('TURN')) return '当前网络下在线发送失败，建议改用稍后接收。';
  if (error.includes('无效') || error.includes('过期')) return '发送入口已失效，请重新创建一次。';
  if (error.includes('接收端') || error.includes('receiver')) return '对方还没进入接收页，可以先把取件码或链接发给对方。';
  if (error.includes('连接')) return '这次连接没有建立成功，可以重试一次。';
  return error;
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
  const [offlineStatus, setOfflineStatus] = useState('');
  const [offlineError, setOfflineError] = useState('');
  const [copyFeedback, setCopyFeedback] = useState<string | null>(null);

  const offlineSessionsQuery = useQuery({
    queryKey: ['offlineTransferSessions'],
    queryFn: listMyOfflineTransferSessions,
  });
  const createOfflineSessionMutation = useMutation({
    mutationFn: async (files: File[]) => {
      setOfflineError('');
      setOfflineStatus('正在创建稍后接收会话...');
      const session = await createTransferSession(files, 'OFFLINE');
      const sessionFilesByRelativePath = new Map(
        session.files.map((file) => [file.relativePath, file]),
      );

      for (let index = 0; index < files.length; index += 1) {
        const file = files[index];
        const relativePath =
          'webkitRelativePath' in file &&
          typeof file.webkitRelativePath === 'string' &&
          file.webkitRelativePath.length > 0
            ? file.webkitRelativePath
            : file.name;
        const sessionFile = sessionFilesByRelativePath.get(relativePath);
        if (!sessionFile?.id) {
          throw new Error(`稍后接收文件映射失败：${relativePath}`);
        }
        setOfflineStatus(`正在上传文件 ${index + 1}/${files.length}：${relativePath}`);
        await uploadOfflineTransferFile(session.sessionId, sessionFile.id, file);
      }

      setOfflineStatus('文件上传完成，对方现在可以取件了');
      return {
        ...session,
        files: session.files.map((file) => ({ ...file, uploaded: true })),
      };
    },
    onSuccess: (result) => {
      setCreatedSession(result);
      void offlineSessionsQuery.refetch();
    },
    onError: (error) => {
      setOfflineError(error instanceof Error ? error.message : '稍后接收创建失败');
    },
  });

  useEffect(() => {
    return () => {
      senderRef.current?.stop();
      senderRef.current = null;
    };
  }, []);

  async function copyText(value: string, key: string) {
    try {
      await window.navigator.clipboard.writeText(value);
      setCopyFeedback(key);
      setTimeout(() => setCopyFeedback(null), 2000);
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
      setOnlineError(error instanceof Error ? error.message : '在线发送启动失败');
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
    setOfflineStatus('');
    setOfflineError('');
    senderRef.current?.stop();
    createOfflineSessionMutation.mutate(files);
  }

  function resetSession() {
    senderRef.current?.stop();
    senderRef.current = null;
    setCreatedSession(null);
    setOnlineStatus('');
    setOnlineError('');
    setOnlineProgress(null);
    setOnlineComplete(false);
    setOfflineStatus('');
    setOfflineError('');
  }

  const receiveUrl = createdSession
    ? `${window.location.origin}/transfer/receive?code=${createdSession.pickupCode}`
    : '';
  const percent = formatPercent(onlineProgress);
  const friendlyStatus = getFriendlyStatus(onlineStatus);
  const friendlyError = getFriendlyError(onlineError);

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
    <DashboardLayout title="快传 Transfer" hideHeader>
      <div className="mb-8 flex items-center justify-between">
        <div className="flex gap-1 p-1 bg-black/5 dark:bg-white/5 rounded-xl">
          <button
            className={clsx(
              "px-6 py-2 rounded-lg text-sm font-bold transition-all",
              activeTab === 'send' 
                ? "bg-white dark:bg-[#222233] text-brand-light shadow-sm" 
                : "text-text-secondary-light dark:text-text-secondary-dark hover:bg-black/5 dark:hover:bg-white/5"
            )}
            onClick={() => switchTab('send')}
          >
            发送
          </button>
          <button
            className={clsx(
              "px-6 py-2 rounded-lg text-sm font-bold transition-all",
              activeTab === 'receive' 
                ? "bg-white dark:bg-[#222233] text-brand-light shadow-sm" 
                : "text-text-secondary-light dark:text-text-secondary-dark hover:bg-black/5 dark:hover:bg-white/5"
            )}
            onClick={() => switchTab('receive')}
          >
            接收
          </button>
        </div>
      </div>

      {activeTab === 'receive' ? (
        <div className="card-container p-8 md:p-10">
          <TransferReceivePanel embedded />
        </div>
      ) : (
        <div className="max-w-6xl mx-auto">
          <div className="grid gap-8 lg:grid-cols-[1fr_320px]">
            <div className="space-y-6">
              {!createdSession ? (
                <div className="card-container overflow-hidden">
                  <div className="flex border-b border-[#D9E3F2] dark:border-[#222233]">
                    <button 
                      onClick={() => setMode('ONLINE')}
                      className={clsx(
                        "flex-1 py-5 px-6 text-left transition-colors relative",
                        mode === 'ONLINE' ? "bg-brand-light/5" : "hover:bg-black/[0.02] dark:hover:bg-white/[0.02]"
                      )}
                    >
                      <div className="flex items-center gap-3 mb-1">
                        <div className={clsx(
                          "w-10 h-10 rounded-full flex items-center justify-center transition-colors",
                          mode === 'ONLINE' ? "bg-brand-light text-white" : "bg-black/5 dark:bg-white/5 text-text-secondary-light dark:text-text-secondary-dark"
                        )}>
                          <Send size={20} />
                        </div>
                        <span className={clsx(
                          "font-bold text-lg",
                          mode === 'ONLINE' ? "text-brand-light" : "text-text-primary-light dark:text-white"
                        )}>在线发送</span>
                      </div>
                      <p className="pl-[52px] text-sm text-text-secondary-light dark:text-text-secondary-dark">
                        对方现在就能进入接收页，连接成功后直接开始。
                      </p>
                      {mode === 'ONLINE' && <div className="absolute bottom-0 left-0 right-0 h-1 bg-brand-light" />}
                    </button>
                    <button 
                      onClick={() => setMode('OFFLINE')}
                      className={clsx(
                        "flex-1 py-5 px-6 text-left transition-colors relative",
                        mode === 'OFFLINE' ? "bg-brand-light/5" : "hover:bg-black/[0.02] dark:hover:bg-white/[0.02]"
                      )}
                    >
                      <div className="flex items-center gap-3 mb-1">
                        <div className={clsx(
                          "w-10 h-10 rounded-full flex items-center justify-center transition-colors",
                          mode === 'OFFLINE' ? "bg-brand-light text-white" : "bg-black/5 dark:bg-white/5 text-text-secondary-light dark:text-text-secondary-dark"
                        )}>
                          <Clock size={20} />
                        </div>
                        <span className={clsx(
                          "font-bold text-lg",
                          mode === 'OFFLINE' ? "text-brand-light" : "text-text-primary-light dark:text-white"
                        )}>稍后接收</span>
                      </div>
                      <p className="pl-[52px] text-sm text-text-secondary-light dark:text-text-secondary-dark">
                        先生成取件码，对方稍后凭取件码来取。
                      </p>
                      {mode === 'OFFLINE' && <div className="absolute bottom-0 left-0 right-0 h-1 bg-brand-light" />}
                    </button>
                  </div>

                  <div className="p-12 text-center">
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
                      className="group relative inline-flex items-center justify-center w-64 h-64 rounded-full border-4 border-dashed border-[#D9E3F2] dark:border-[#222233] hover:border-brand-light dark:hover:border-brand-dark transition-all duration-300 bg-transparent hover:bg-brand-light/5"
                      onClick={() => fileInputRef.current?.click()}
                      disabled={isStartingOnline || createOfflineSessionMutation.isPending}
                    >
                      <div className="flex flex-col items-center">
                        <div className="w-20 h-20 rounded-2xl bg-brand-light/10 text-brand-light flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                          <Send size={40} />
                        </div>
                        <span className="text-xl font-bold text-text-primary-light dark:text-white mb-2">
                          {isStartingOnline || createOfflineSessionMutation.isPending ? '正在创建...' : '选择文件并创建'}
                        </span>
                        <span className="text-sm text-text-secondary-light dark:text-text-secondary-dark">
                          支持多选文件发送
                        </span>
                      </div>
                    </button>
                    {mode === 'OFFLINE' && (offlineStatus || offlineError) ? (
                      <div className="mx-auto mt-6 max-w-md space-y-2 text-left">
                        {offlineStatus ? (
                          <div className="rounded-xl border border-brand-light/10 bg-brand-light/5 px-4 py-3 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                            {offlineStatus}
                          </div>
                        ) : null}
                        {offlineError ? (
                          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600 dark:border-red-500/20 dark:bg-red-500/10 dark:text-red-400">
                            {offlineError}
                          </div>
                        ) : null}
                      </div>
                    ) : null}
                  </div>
                </div>
              ) : (
                <div className="card-container p-8 md:p-10 border-2 border-brand-light/20 shadow-xl shadow-brand-light/5">
                  <div className="flex items-center justify-between mb-8">
                    <h2 className="text-2xl font-bold text-text-primary-light dark:text-white flex items-center gap-2">
                      <CheckCircle2 className="text-emerald-500" />
                      发送入口已就绪
                    </h2>
                    <button 
                      onClick={resetSession}
                      className="text-sm font-semibold text-text-secondary-light dark:text-text-secondary-dark hover:text-text-primary-light flex items-center gap-1 transition-colors"
                    >
                      <X size={16} /> 取消并返回
                    </button>
                  </div>

                  <div className="grid md:grid-cols-2 gap-10">
                    <div className="space-y-6">
                      <div className="p-6 bg-brand-light/5 dark:bg-brand-light/10 rounded-2xl border border-brand-light/10">
                        <p className="text-sm font-bold text-brand-light mb-3 flex items-center gap-2">
                          <Info size={14} /> 把取件码告诉对方
                        </p>
                        <div className="flex items-center justify-between gap-4">
                          <div className="text-4xl font-black text-brand-light tracking-[0.3em] font-mono leading-none">
                            {createdSession.pickupCode}
                          </div>
                          <button
                            className={clsx(
                              "flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-bold transition-all",
                              copyFeedback === 'code' ? "bg-emerald-500 text-white" : "bg-brand-light text-white hover:bg-brand-dark shadow-lg shadow-brand-light/20"
                            )}
                            onClick={() => void copyText(createdSession.pickupCode, 'code')}
                          >
                            {copyFeedback === 'code' ? <Check size={16} /> : <Copy size={16} />}
                            {copyFeedback === 'code' ? '已复制' : '复制'}
                          </button>
                        </div>
                      </div>

                      <div className="p-6 bg-[#F8FBFF] dark:bg-white/5 rounded-2xl border border-[#D9E3F2] dark:border-[#222233]">
                        <p className="text-sm font-bold text-text-secondary-light dark:text-text-secondary-dark mb-3 flex items-center gap-2">
                          <LinkIcon size={14} /> 接收链接
                        </p>
                        <div className="flex items-center gap-2">
                          <div className="flex-1 px-4 py-2 bg-white dark:bg-black/20 border border-[#D9E3F2] dark:border-[#222233] rounded-xl text-xs text-text-secondary-light dark:text-text-secondary-dark truncate">
                            {receiveUrl}
                          </div>
                          <button
                            className={clsx(
                              "flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-bold transition-all shrink-0",
                              copyFeedback === 'link' ? "bg-emerald-500 text-white" : "bg-white dark:bg-[#222233] text-brand-light border border-brand-light/20 hover:border-brand-light"
                            )}
                            onClick={() => void copyText(receiveUrl, 'link')}
                          >
                            {copyFeedback === 'link' ? <Check size={16} /> : <Copy size={16} />}
                            {copyFeedback === 'link' ? '已复制' : '复制链接'}
                          </button>
                        </div>
                      </div>

                      <div className="flex items-center gap-4 text-sm text-text-secondary-light dark:text-text-secondary-dark px-2">
                        <span className="flex items-center gap-1.5">
                          <Clock size={14} /> 过期时间：{formatDateTime(createdSession.expiresAt)}
                        </span>
                        <span className="w-1.5 h-1.5 rounded-full bg-[#D9E3F2] dark:bg-[#222233]" />
                        <span className="flex items-center gap-1.5">
                          {createdSession.mode === 'ONLINE' ? '在线发送' : '稍后接收'}
                        </span>
                      </div>
                    </div>

                    <div className="space-y-6 flex flex-col justify-center">
                      {createdSession.mode === 'ONLINE' ? (
                        <div className="space-y-5">
                          <div className="flex items-center gap-3">
                            <div className={clsx(
                              "w-3 h-3 rounded-full animate-pulse",
                              onlineComplete ? "bg-emerald-500" : (onlineError ? "bg-red-500" : "bg-brand-light")
                            )} />
                            <span className="font-bold text-lg text-text-primary-light dark:text-white">
                              {onlineComplete ? '发送已完成' : (onlineError ? '连接出现问题' : (friendlyStatus || '正在等待对方进入'))}
                            </span>
                          </div>

                          {!onlineComplete && !onlineError && (
                            <div className="space-y-2">
                              <div className="flex justify-between text-xs text-text-muted-light dark:text-text-muted-dark font-medium">
                                <span className="truncate max-w-[200px]">{onlineProgress?.fileName || '等待传输...'}</span>
                                <span>{percent}%</span>
                              </div>
                              <div className="h-3 rounded-full bg-black/5 dark:bg-white/5 overflow-hidden">
                                <div 
                                  className="h-full bg-brand-light dark:bg-brand-dark transition-all duration-500" 
                                  style={{ width: `${percent}%` }} 
                                />
                              </div>
                              {onlineProgress && (
                                <p className="text-[10px] text-text-muted-light dark:text-text-muted-dark text-right">
                                  {formatBytes(onlineProgress.sentBytes)} / {formatBytes(onlineProgress.totalBytes)}
                                </p>
                              )}
                            </div>
                          )}

                          {onlineComplete && (
                            <div className="p-4 bg-emerald-50 dark:bg-emerald-500/10 rounded-xl border border-emerald-200 dark:border-emerald-500/20">
                              <p className="text-sm text-emerald-700 dark:text-emerald-400 font-medium">
                                所有文件已成功发送给对方。您可以继续发送其他文件或关闭此页面。
                              </p>
                            </div>
                          )}

                          {onlineError && (
                            <div className="p-4 bg-red-50 dark:bg-red-500/10 rounded-xl border border-red-200 dark:border-red-500/20">
                              <p className="text-sm text-red-700 dark:text-red-400 font-medium mb-2">
                                {friendlyError}
                              </p>
                              <button 
                                onClick={resetSession}
                                className="text-xs font-bold text-red-600 dark:text-red-400 hover:underline flex items-center gap-1"
                              >
                                建议改用“稍后接收”重试 <ArrowRight size={12} />
                              </button>
                            </div>
                          )}

                          {!onlineProgress && !onlineComplete && !onlineError && (
                            <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark">
                              请保持此页面打开。一旦对方进入并连接，文件将自动开始传输。
                            </p>
                          )}
                        </div>
                      ) : (
                        <div className="bg-black/5 dark:bg-white/5 rounded-2xl p-6">
                          <p className="text-sm font-bold text-text-primary-light dark:text-white mb-4 flex items-center gap-2">
                            <FileText size={16} /> 文件清单 ({createdSession.files.length})
                          </p>
                          <div className="space-y-3 max-h-[160px] overflow-y-auto pr-2 custom-scrollbar">
                            {createdSession.files.map((file, index) => (
                              <div key={`${file.relativePath}-${index}`} className="flex items-center justify-between text-sm">
                                <span className="truncate text-text-secondary-light dark:text-text-secondary-dark mr-4" title={file.relativePath}>
                                  {file.relativePath}
                                </span>
                                <span className="shrink-0 text-text-muted-light dark:text-text-muted-dark font-mono text-xs">
                                  {formatBytes(file.size)}
                                </span>
                              </div>
                            ))}
                          </div>
                          <div className="mt-6 pt-4 border-t border-black/5 dark:border-white/10">
                            <p className="text-xs text-text-secondary-light dark:text-text-secondary-dark leading-relaxed">
                              {offlineStatus || '文件已准备就绪。对方可以随时凭取件码或链接取走，无需您保持在线。'}
                            </p>
                            {offlineError ? (
                              <p className="mt-2 text-xs text-red-500">{offlineError}</p>
                            ) : null}
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              )}

              <div className="card-container p-6 flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl bg-brand-light/10 text-brand-light flex items-center justify-center shrink-0">
                  <Info size={20} />
                </div>
                <div>
                  <h3 className="font-bold text-text-primary-light dark:text-white mb-1">如何接收？</h3>
                  <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark leading-relaxed">
                    将取件码或链接发给接收方。对方只需在本站“快传”页面切到“接收”标签，输入取件码即可开始下载。
                  </p>
                </div>
              </div>
            </div>

            <aside className="space-y-6">
              <div className="card-container p-6">
                <div className="flex items-center gap-2 mb-4 text-text-primary-light dark:text-white">
                  <History size={18} />
                  <h3 className="font-bold">最近的“稍后接收”</h3>
                </div>
                
                {offlineSessionsQuery.isLoading ? (
                  <div className="space-y-3">
                    {[1, 2, 3].map(i => (
                      <div key={i} className="h-16 rounded-xl bg-black/5 dark:bg-white/5 animate-pulse" />
                    ))}
                  </div>
                ) : offlineSessionsQuery.data && offlineSessionsQuery.data.length > 0 ? (
                  <div className="space-y-3">
                    {offlineSessionsQuery.data.slice(0, 5).map((session) => (
                      <div 
                        key={session.sessionId} 
                        className="group p-4 rounded-xl border border-[#D9E3F2] dark:border-[#222233] hover:border-brand-light/30 transition-all cursor-pointer relative"
                        onClick={() => setCreatedSession(session)}
                      >
                        <div className="flex justify-between items-start mb-1">
                          <span className="font-mono font-bold text-brand-light">{session.pickupCode}</span>
                          <span className="text-[10px] px-1.5 py-0.5 rounded bg-black/5 dark:bg-white/5 text-text-muted-light dark:text-text-muted-dark">
                            {session.files.length} 文件
                          </span>
                        </div>
                        <p className="text-[10px] text-text-muted-light dark:text-text-muted-dark truncate">
                          过期于 {formatDateTime(session.expiresAt)}
                        </p>
                        <div className="absolute inset-0 bg-brand-light/5 opacity-0 group-hover:opacity-100 transition-opacity rounded-xl" />
                      </div>
                    ))}
                    {offlineSessionsQuery.data.length > 5 && (
                      <p className="text-center text-xs text-text-muted-light dark:text-text-muted-dark pt-2">
                        仅显示最近 5 条记录
                      </p>
                    )}
                  </div>
                ) : (
                  <div className="py-8 text-center">
                    <p className="text-sm text-text-muted-light dark:text-text-muted-dark">暂无记录</p>
                  </div>
                )}
              </div>

              <div className="p-6 rounded-2xl bg-brand-light/5 border border-brand-light/10">
                <h4 className="text-sm font-bold text-brand-light mb-2">安全提示</h4>
                <p className="text-xs text-text-secondary-light dark:text-text-secondary-dark leading-relaxed">
                  “在线发送”适合双方都在线时快速传输；“稍后接收”适合对方暂时不方便立刻接收的场景。
                </p>
              </div>
            </aside>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
};

export default TransferSend;
