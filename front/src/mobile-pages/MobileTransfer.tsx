import React, { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  CheckCircle,
  ChevronRight,
  Clock3,
  Copy,
  DownloadCloud,
  File as FileIcon,
  Folder,
  FolderPlus,
  Link as LinkIcon,
  Loader2,
  Monitor,
  Plus,
  Send,
  Shield,
  Smartphone,
  Trash2,
  UploadCloud,
  X,
  LogIn,
} from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import { useAuth } from '@/src/auth/AuthProvider';
import { Button } from '@/src/components/ui/button';
import { appendTransferRelayHint } from '@/src/lib/transfer-ice';
import { buildTransferShareUrl, getTransferRouterMode } from '@/src/lib/transfer-links';
import {
  createTransferFileManifest,
  createTransferFileManifestMessage,
  createTransferCompleteMessage,
  createTransferFileCompleteMessage,
  createTransferFileId,
  createTransferFileMetaMessage,
  type TransferFileDescriptor,
  SIGNAL_POLL_INTERVAL_MS,
} from '@/src/lib/transfer-protocol';
import {
  shouldPublishTransferProgress,
  resolveTransferChunkSize,
} from '@/src/lib/transfer-runtime';
import { createTransferPeer, type TransferPeerAdapter } from '@/src/lib/transfer-peer';
import {
  DEFAULT_TRANSFER_ICE_SERVERS,
  TRANSFER_HAS_RELAY_SUPPORT,
  createTransferSession,
  listMyOfflineTransferSessions,
  pollTransferSignals,
  postTransferSignal,
  uploadOfflineTransferFile,
} from '@/src/lib/transfer';
import type { TransferMode, TransferSessionResponse } from '@/src/lib/types';
import { cn } from '@/src/lib/utils';

// We reuse the state and sub-component
import {
  buildQrImageUrl,
  canSendTransferFiles,
  getAvailableTransferModes,
  formatTransferSize,
  getOfflineTransferSessionLabel,
  getOfflineTransferSessionSize,
  getTransferModeSummary,
  resolveInitialTransferTab,
} from '@/src/pages/transfer-state';
import TransferReceive from '@/src/pages/TransferReceive';

type SendPhase = 'idle' | 'creating' | 'waiting' | 'connecting' | 'uploading' | 'transferring' | 'completed' | 'error';

function parseJsonPayload<T>(payload: string): T | null {
  try { return JSON.parse(payload) as T; } catch { return null; }
}

function getPhaseMessage(mode: TransferMode, phase: SendPhase, errorMessage: string) {
  if (mode === 'OFFLINE') {
    switch (phase) {
      case 'creating': return '正在创建离线会话...';
      case 'uploading': return '文件上传中，完成后可保留7天。';
      case 'completed': return '离线文件已上传完成，可以多次下载。';
      case 'error': return errorMessage || '初始化失败，请重试。';
      default: return '离线模式会将文件存至服务端保留7天。';
    }
  }
  switch (phase) {
    case 'creating': return '正在准备 P2P 连接...';
    case 'waiting': return '已生成二维码，等待接收端扫码或访问链接。';
    case 'connecting': return '接收端已进入，正在建立连接...';
    case 'transferring': return 'P2P 直连已建立，文件发送中...';
    case 'completed': return '本次文件发送完成。';
    case 'error': return errorMessage || '快传初始化失败，请重试。';
    default: return '文件不经过服务器，浏览器直连互传。';
  }
}

export function getMobileTransferLayoutClassNames() {
  return {
    root: 'relative flex min-h-full flex-col bg-transparent',
    header: 'sticky top-0 z-30 px-4 py-2',
    headerPanel: 'glass-panel relative overflow-hidden rounded-[24px] border border-white/12 bg-[#0b1528]/82 px-3.5 py-3 shadow-[0_14px_36px_rgba(8,15,30,0.32)] backdrop-blur-2xl',
    titlePanel: 'relative overflow-hidden rounded-[18px] px-3.5 pt-3 pb-3',
    content: 'relative z-10 flex-1 flex flex-col min-w-0 px-4 pt-3 pb-6',
    sendFileList: 'glass-panel rounded-2xl p-2.5',
  };
}

export default function MobileTransfer() {
  const navigate = useNavigate();
  const { ready: authReady, session: authSession } = useAuth();
  const [searchParams] = useSearchParams();
  const sessionId = searchParams.get('session');
  const isAuthenticated = Boolean(authSession?.token);
  const allowSend = canSendTransferFiles(isAuthenticated);
  const availableTransferModes = getAvailableTransferModes(isAuthenticated);
  const [activeTab, setActiveTab] = useState(() => resolveInitialTransferTab(allowSend, sessionId));

  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [transferMode, setTransferMode] = useState<TransferMode>('ONLINE');
  const [session, setSession] = useState<TransferSessionResponse | null>(null);
  const [sendPhase, setSendPhase] = useState<SendPhase>('idle');
  const [sendProgress, setSendProgress] = useState(0);
  const [sendError, setSendError] = useState('');
  const [copied, setCopied] = useState(false);
  const [offlineHistory, setOfflineHistory] = useState<TransferSessionResponse[]>([]);
  const [offlineHistoryLoading, setOfflineHistoryLoading] = useState(false);
  const [offlineHistoryError, setOfflineHistoryError] = useState('');
  const [selectedOfflineSession, setSelectedOfflineSession] = useState<TransferSessionResponse | null>(null);
  const [historyCopiedSessionId, setHistoryCopiedSessionId] = useState<string | null>(null);
  const layoutClassNames = getMobileTransferLayoutClassNames();

  const fileInputRef = useRef<HTMLInputElement>(null);
  const folderInputRef = useRef<HTMLInputElement>(null);
  const copiedTimerRef = useRef<number | null>(null);
  const historyCopiedTimerRef = useRef<number | null>(null);
  const pollTimerRef = useRef<number | null>(null);
  const peerRef = useRef<TransferPeerAdapter | null>(null);
  const cursorRef = useRef(0);
  const bootstrapIdRef = useRef(0);
  const totalBytesRef = useRef(0);
  const sentBytesRef = useRef(0);
  const lastSendProgressPublishAtRef = useRef(0);
  const lastPublishedSendProgressRef = useRef(0);
  const sendingStartedRef = useRef(false);
  const manifestRef = useRef<TransferFileDescriptor[]>([]);

  useEffect(() => {
    if (folderInputRef.current) {
      folderInputRef.current.setAttribute('webkitdirectory', '');
      folderInputRef.current.setAttribute('directory', '');
    }
    return () => {
      cleanupCurrentTransfer();
      if (copiedTimerRef.current) window.clearTimeout(copiedTimerRef.current);
      if (historyCopiedTimerRef.current) window.clearTimeout(historyCopiedTimerRef.current);
    };
  }, []);

  useEffect(() => {
    if (!allowSend || sessionId) setActiveTab('receive');
  }, [allowSend, sessionId]);

  useEffect(() => {
    if (!availableTransferModes.includes(transferMode)) setTransferMode('ONLINE');
  }, [availableTransferModes, transferMode]);

  useEffect(() => {
    if (selectedFiles.length > 0) void bootstrapTransfer(selectedFiles);
  }, [transferMode]);

  useEffect(() => {
    if (!isAuthenticated) {
      setOfflineHistory([]); setOfflineHistoryError(''); setSelectedOfflineSession(null);
      return;
    }
    void loadOfflineHistory();
  }, [isAuthenticated]);

  const totalSize = selectedFiles.reduce((sum, file) => sum + file.size, 0);
  const shareLink = session ? buildTransferShareUrl(window.location.origin, session.sessionId, getTransferRouterMode()) : '';
  const qrImageUrl = shareLink ? buildQrImageUrl(shareLink) : '';
  const transferModeSummary = getTransferModeSummary(transferMode);
  const selectedOfflineSessionShareLink = selectedOfflineSession ? buildTransferShareUrl(window.location.origin, selectedOfflineSession.sessionId, getTransferRouterMode()) : '';
  const selectedOfflineSessionQrImageUrl = selectedOfflineSessionShareLink ? buildQrImageUrl(selectedOfflineSessionShareLink) : '';

  function navigateBackToLogin() {
    const nextPath = `${window.location.pathname}${window.location.search}`;
    navigate(`/login?next=${encodeURIComponent(nextPath)}`);
  }

  function isOfflineSessionReady(sessionToCheck: TransferSessionResponse) {
    return sessionToCheck.files.every((f) => f.uploaded);
  }

  async function loadOfflineHistory(options?: {silent?: boolean}) {
    if (!isAuthenticated) return;
    if (!options?.silent) setOfflineHistoryLoading(true);
    setOfflineHistoryError('');
    try {
      const sessions = await listMyOfflineTransferSessions();
      setOfflineHistory(sessions);
      setSelectedOfflineSession((curr) => curr ? (sessions.find((item) => item.sessionId === curr.sessionId) ?? null) : null);
    } catch (e) {
      setOfflineHistoryError(e instanceof Error ? e.message : '离线快传记录加载失败');
    } finally {
      if (!options?.silent) setOfflineHistoryLoading(false);
    }
  }

  function cleanupCurrentTransfer() {
    if (pollTimerRef.current) { window.clearInterval(pollTimerRef.current); pollTimerRef.current = null; }
    const peer = peerRef.current;
    peerRef.current = null;
    peer?.destroy();
    cursorRef.current = 0; lastSendProgressPublishAtRef.current = 0; lastPublishedSendProgressRef.current = 0; sendingStartedRef.current = false;
  }

  function publishSendProgress(nextProgress: number, options?: {force?: boolean}) {
    const normalizedProgress = Math.max(0, Math.min(100, nextProgress));
    const now = globalThis.performance?.now?.() ?? Date.now();
    if (!options?.force && !shouldPublishTransferProgress({
      nextProgress: normalizedProgress,
      previousProgress: lastPublishedSendProgressRef.current,
      now,
      lastPublishedAt: lastSendProgressPublishAtRef.current,
    })) return;

    lastSendProgressPublishAtRef.current = now;
    lastPublishedSendProgressRef.current = normalizedProgress;
    setSendProgress(normalizedProgress);
  }

  function resetSenderState() {
    cleanupCurrentTransfer();
    setSession(null); setSelectedFiles([]); setSendPhase('idle'); publishSendProgress(0, {force: true}); setSendError('');
  }

  async function copyToClipboard(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      if (copiedTimerRef.current) window.clearTimeout(copiedTimerRef.current);
      copiedTimerRef.current = window.setTimeout(() => setCopied(false), 1800);
    } catch { setCopied(false); }
  }

  function ensureReadyState(nextFiles: File[]) {
    setSelectedFiles(nextFiles);
    if (nextFiles.length === 0) resetSenderState();
    else void bootstrapTransfer(nextFiles);
  }

  function appendFiles(files: FileList | File[]) {
    ensureReadyState([...selectedFiles, ...Array.from(files)]);
  }

  function handleFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    if (e.target.files?.length) appendFiles(e.target.files);
    e.target.value = '';
  }

  function removeFile(idx: number) {
    ensureReadyState(selectedFiles.filter((_, i) => i !== idx));
  }

  async function bootstrapTransfer(files: File[]) {
    const bootstrapId = bootstrapIdRef.current + 1;
    bootstrapIdRef.current = bootstrapId;

    cleanupCurrentTransfer();
    setSendError(''); setSendPhase('creating'); publishSendProgress(0, {force: true});
    manifestRef.current = createTransferFileManifest(files);
    totalBytesRef.current = 0; sentBytesRef.current = 0;

    try {
      const createdSession = await createTransferSession(files, transferMode);
      if (bootstrapIdRef.current !== bootstrapId) return;

      setSession(createdSession);
      if (createdSession.mode === 'OFFLINE') {
        void loadOfflineHistory({silent: true});
        await uploadOfflineFiles(createdSession, files, bootstrapId);
        return;
      }
      setSendPhase('waiting');
      await setupSenderPeer(createdSession, files, bootstrapId);
    } catch (e) {
      if (bootstrapIdRef.current !== bootstrapId) return;
      setSendPhase('error');
      setSendError(e instanceof Error ? e.message : '快传会话创建失败');
    }
  }

  async function uploadOfflineFiles(createdSession: TransferSessionResponse, files: File[], bootstrapId: number) {
    setSendPhase('uploading');
    totalBytesRef.current = files.reduce((sum, f) => sum + f.size, 0); sentBytesRef.current = 0; publishSendProgress(0, {force: true});
    for (const [idx, file] of files.entries()) {
      if (bootstrapIdRef.current !== bootstrapId) return;
      const sessionFile = createdSession.files[idx];
      if (!sessionFile?.id) throw new Error('单次传送配置异常，请重新传输。');

      let lastLoaded = 0;
      await uploadOfflineTransferFile(createdSession.sessionId, sessionFile.id, file, ({ loaded, total }) => {
        sentBytesRef.current += (loaded - lastLoaded); lastLoaded = loaded;
        if (loaded >= total) sentBytesRef.current = Math.min(totalBytesRef.current, sentBytesRef.current);
        if (totalBytesRef.current > 0) publishSendProgress(Math.min(99, Math.round((sentBytesRef.current / totalBytesRef.current) * 100)));
      });
    }
    publishSendProgress(100, {force: true}); setSendPhase('completed');
    void loadOfflineHistory({silent: true});
  }

  async function setupSenderPeer(createdSession: TransferSessionResponse, files: File[], bootstrapId: number) {
    const peer = createTransferPeer({
      initiator: true,
      peerOptions: {
        config: {
          iceServers: DEFAULT_TRANSFER_ICE_SERVERS,
        },
      },
      onSignal: (payload) => {
        void postTransferSignal(createdSession.sessionId, 'sender', 'signal', payload);
      },
      onConnect: () => {
        if (bootstrapIdRef.current !== bootstrapId) return;
        setSendPhase(cur => (cur === 'transferring' || cur === 'completed' ? cur : 'connecting'));
        peer.send(createTransferFileManifestMessage(manifestRef.current));
      },
      onData: (payload) => {
        if (typeof payload !== 'string') return;
        const msg = parseJsonPayload<{type?: string; fileIds?: string[]}>(payload);
        if (!msg || msg.type !== 'receive-request' || !Array.isArray(msg.fileIds) || sendingStartedRef.current) return;

        const requestedFiles = manifestRef.current.filter((item) => msg.fileIds?.includes(item.id));
        if (requestedFiles.length === 0) return;

        sendingStartedRef.current = true;
        totalBytesRef.current = requestedFiles.reduce((sum, f) => sum + f.size, 0); sentBytesRef.current = 0; publishSendProgress(0, {force: true});
        void sendSelectedFiles(peer, files, requestedFiles, bootstrapId);
      },
      onError: (error) => {
        if (bootstrapIdRef.current !== bootstrapId) return;
        setSendPhase('error');
        setSendError(appendTransferRelayHint(
          error.message || '数据通道建立失败',
          TRANSFER_HAS_RELAY_SUPPORT,
        ));
      },
    });
    peerRef.current = peer;
    startSenderPolling(createdSession.sessionId, bootstrapId);
  }

  function startSenderPolling(sessionId: string, bootstrapId: number) {
    let polling = false;
    pollTimerRef.current = window.setInterval(() => {
      if (polling || bootstrapIdRef.current !== bootstrapId) return;
      polling = true;
      void pollTransferSignals(sessionId, 'sender', cursorRef.current)
        .then((res) => {
          if (bootstrapIdRef.current !== bootstrapId) return;
          cursorRef.current = res.nextCursor;
          for (const item of res.items) {
            if (item.type === 'peer-joined') {
               setSendPhase(cur => (cur === 'waiting' ? 'connecting' : cur));
               continue;
            }
            if (item.type === 'signal') {
               peerRef.current?.applyRemoteSignal(item.payload);
            }
          }
        })
        .catch(e => {
          if (bootstrapIdRef.current !== bootstrapId) return;
          setSendPhase('error'); setSendError(e instanceof Error ? e.message : '状态轮询失败');
        })
        .finally(() => polling = false);
    }, SIGNAL_POLL_INTERVAL_MS);
  }

  async function sendSelectedFiles(
    peer: TransferPeerAdapter,
    files: File[],
    requestedFiles: TransferFileDescriptor[],
    bootstrapId: number,
  ) {
    setSendPhase('transferring');
    const filesById = new Map(files.map((f) => [createTransferFileId(f), f]));
    const chunkSize = resolveTransferChunkSize();

    for (const desc of requestedFiles) {
      if (bootstrapIdRef.current !== bootstrapId || !peer.connected) return;
      const file = filesById.get(desc.id);
      if (!file) continue;

      peer.send(createTransferFileMetaMessage(desc));
      for (let offset = 0; offset < file.size; offset += chunkSize) {
        if (bootstrapIdRef.current !== bootstrapId || !peer.connected) return;
        const chunk = await file.slice(offset, offset + chunkSize).arrayBuffer();
        await peer.write(chunk);
        sentBytesRef.current += chunk.byteLength;
        if (totalBytesRef.current > 0) publishSendProgress(Math.min(99, Math.round((sentBytesRef.current / totalBytesRef.current) * 100)));
      }
      peer.send(createTransferFileCompleteMessage(desc.id));
    }
    peer.send(createTransferCompleteMessage());
    publishSendProgress(100, {force: true}); setSendPhase('completed');
  }

  async function copyOfflineSessionLink(s: TransferSessionResponse) {
    const sl = buildTransferShareUrl(window.location.origin, s.sessionId, getTransferRouterMode());
    await navigator.clipboard.writeText(sl);
    setHistoryCopiedSessionId(s.sessionId);
    if (historyCopiedTimerRef.current) window.clearTimeout(historyCopiedTimerRef.current);
    historyCopiedTimerRef.current = window.setTimeout(() => {
      setHistoryCopiedSessionId((curr) => (curr === s.sessionId ? null : curr));
    }, 1800);
  }

  return (
    <div className={layoutClassNames.root}>
      <div className="pointer-events-none absolute inset-0 z-0">
        <div className="absolute top-[-18%] left-[-22%] h-72 w-72 rounded-full bg-[#336EFF] opacity-20 mix-blend-screen blur-[100px] animate-blob" />
        <div className="absolute top-[10%] right-[-18%] h-80 w-80 rounded-full bg-cyan-500 opacity-16 mix-blend-screen blur-[96px] animate-blob animation-delay-2000" />
        <div className="absolute bottom-[-18%] left-[14%] h-80 w-80 rounded-full bg-purple-600 opacity-18 mix-blend-screen blur-[100px] animate-blob animation-delay-4000" />
      </div>

      <input type="file" multiple className="hidden" ref={fileInputRef} onChange={handleFileSelect} />
      <input type="file" multiple className="hidden" ref={folderInputRef} onChange={handleFileSelect} />

      <div className={layoutClassNames.header}>
        <div className={layoutClassNames.headerPanel}>
          <div className="absolute inset-0 bg-[#0b1528]/64 backdrop-blur-2xl" />
          <div className="absolute inset-0 bg-[url('/noise.png')] opacity-30" />
          <div className="absolute inset-x-0 top-0 h-px bg-white/18" />
          <div className="absolute top-[-40%] right-[-8%] h-[140%] w-[95%] rounded-full bg-[#336EFF] opacity-14 mix-blend-screen blur-[80px]" />
          <div className="absolute bottom-[-65%] left-[-8%] h-[120%] w-[55%] rounded-full bg-cyan-400/10 mix-blend-screen blur-[72px]" />

          <div className={layoutClassNames.titlePanel}>
            <div className="relative z-10 flex items-center text-[1.375rem] font-bold tracking-wide">
              <Send className="mr-2.5 h-5.5 w-5.5 text-cyan-400" />
              快传
            </div>
          </div>

          {allowSend && (
            <div className="relative z-10 mt-2.5 flex overflow-hidden rounded-2xl border border-white/8 bg-black/18">
              <button
                onClick={() => setActiveTab('send')}
                className={cn('flex-1 py-3.5 text-sm font-medium transition-colors relative flex items-center justify-center gap-2',
                  activeTab === 'send' ? 'text-white bg-blue-500/10' : 'text-slate-400')}
              >
                <UploadCloud className="w-4 h-4" /> 发送
              </button>
              <button
                onClick={() => setActiveTab('receive')}
                className={cn('flex-1 py-3.5 text-sm font-medium transition-colors relative flex items-center justify-center gap-2',
                  activeTab === 'receive' ? 'text-white bg-blue-500/10' : 'text-slate-400')}
              >
                <DownloadCloud className="w-4 h-4" /> 接收
              </button>
            </div>
          )}
          <div className="pointer-events-none absolute inset-x-3 bottom-0 h-px bg-white/8" />
        </div>
      </div>

      <div className={layoutClassNames.content}>
        {authReady && !isAuthenticated && (
           <div className="mb-4 flex flex-col gap-2 rounded-xl bg-blue-500/10 px-4 py-3 text-xs text-blue-100/90 border border-blue-400/10">
             <p className="leading-relaxed">无需登录即可在线发送、在线接收和离线接收。只有发离线和把离线文件存入网盘时才需要登录。</p>
             <Button variant="outline" size="sm" onClick={navigateBackToLogin} className="w-full bg-white/5 border-white/10 text-white mt-1">
               <LogIn className="mr-2 h-3.5 w-3.5" /> 去登录
             </Button>
           </div>
        )}

        <AnimatePresence mode="wait">
          {activeTab === 'send' ? (
            <motion.div key="send" initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 10 }} className="flex-1 flex flex-col min-w-0">
              
              {selectedFiles.length === 0 ? (
                <div className="flex-1 flex flex-col items-center justify-center mt-2 glass-panel rounded-3xl p-6 border border-white/5 overflow-hidden">
                  <div className="w-20 h-20 rounded-full bg-blue-500/10 border-4 border-blue-400/10 shadow-xl flex items-center justify-center mb-6">
                    <Plus className="w-10 h-10 text-blue-400" />
                  </div>
                  <h3 className="text-xl font-bold text-white mb-2">选择要发送的文件</h3>
                  <p className="text-sm text-slate-400 text-center mb-8 px-4 leading-relaxed">
                    选择在线模式建立一次性P2P链接<br/>离线模式可将文件上传至服务端保存7天
                  </p>
                  
                  {availableTransferModes.length > 1 && (
                     <div className="w-full max-w-sm mb-8 bg-black/20 rounded-xl p-1 flex">
                        {availableTransferModes.map(mode => (
                           <button key={mode} onClick={() => setTransferMode(mode)} className={cn("flex-1 text-xs py-2 rounded-lg transition-all", transferMode === mode ? "bg-blue-500 text-white font-medium" : "text-slate-400")}>
                              {mode === 'ONLINE' ? '在线(一次性)' : '离线(存7天)'}
                           </button>
                        ))}
                     </div>
                  )}

                  <div className="flex gap-4 w-full max-w-sm">
                     <Button className="flex-1 rounded-xl bg-[#336EFF] hover:bg-blue-600 h-14" onClick={() => fileInputRef.current?.click()}>
                        <FileIcon className="mr-2 h-5 w-5" /> 文件
                     </Button>
                     <Button className="flex-1 rounded-xl bg-white/5 border border-white/10 hover:bg-white/10 text-white h-14" onClick={() => folderInputRef.current?.click()}>
                        <Folder className="mr-2 h-5 w-5" /> 文件夹
                     </Button>
                  </div>
                </div>
              ) : (
                <div className="flex flex-col gap-4">
                   <div className="glass-panel p-5 rounded-2xl flex flex-col items-center text-center">
                     <button onClick={resetSenderState} className="absolute right-6 top-6 p-2 rounded-full bg-black/40 text-slate-400"><X className="w-4 h-4"/></button>
                     <p className="text-xs text-slate-400 uppercase tracking-widest mb-1.5 flex items-center gap-1">
                        <LinkIcon className="w-3 h-3"/> 取件码
                     </p>
                     <div className="text-4xl md:text-5xl font-mono tracking-widest text-[#336EFF] font-bold mb-4 bg-blue-500/10 px-4 py-2 rounded-2xl border border-blue-400/20">{session?.pickupCode ?? '...'}</div>
                     
                     {qrImageUrl && <img src={qrImageUrl} className="w-32 h-32 rounded-xl border-4 border-white/10 shadow-xl mb-4" />}
                     
                     <div className="w-full">
                       <Button size="sm" variant="outline" className="w-full border-white/10 bg-black/40 text-slate-300" onClick={() => copyToClipboard(shareLink)} disabled={!shareLink}>
                         {copied ? <CheckCircle className="w-4 h-4 mr-2 text-emerald-400"/> : <Copy className="w-4 h-4 mr-2"/> }
                         {copied ? '链接已复制' : '复制长取件链接'}
                       </Button>
                     </div>
                   </div>

                   {/* 状态区 */}
                   <div className={cn("rounded-2xl p-4 flex gap-3 items-center border", 
                     sendPhase === 'error' ? 'bg-rose-500/10 border-rose-500/20 text-rose-300' : 
                     sendPhase === 'completed' ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-300' : 
                     'bg-[#336EFF]/10 border-blue-500/20 text-blue-300'
                   )}>
                     {sendPhase === 'completed' ? <CheckCircle className="w-6 h-6 shrink-0"/> : <Loader2 className={cn("w-6 h-6 shrink-0", sendPhase === 'error' ? 'text-rose-400' : 'animate-spin')}/>}
                     <div className="flex-1 min-w-0">
                        <p className="text-xs font-medium leading-relaxed">{getPhaseMessage(transferMode, sendPhase, sendError)}</p>
                        {sendPhase !== 'error' && sendPhase !== 'completed' && <div className="mt-2 h-1.5 bg-black/40 rounded-full overflow-hidden"><div className="h-full bg-blue-500" style={{ width: `${sendProgress}%` }}/></div>}
                     </div>
                   </div>

                   {/* 文件列表 */}
                   <div className={layoutClassNames.sendFileList}>
                     <p className="text-xs text-slate-500 mb-2 px-2.5 pt-2">共 {selectedFiles.length} 项 / {formatTransferSize(totalSize)}</p>
                     {selectedFiles.map((f, i) => (
                       <div key={i} className="flex px-2.5 py-2 items-center gap-3 bg-white/[0.03] rounded-xl mb-1 hover:bg-white/5 active:bg-white/10 transition-colors">
                         <div className="p-1.5 rounded-lg bg-black/20"><FileIcon className="w-4 h-4 text-[#336EFF]" /></div>
                         <div className="flex-1 min-w-0">
                           <p className="text-xs font-medium text-slate-200 truncate">{f.name}</p>
                           <p className="text-[10px] text-slate-500">{formatTransferSize(f.size)}</p>
                         </div>
                         <button onClick={() => removeFile(i)} className="p-2 -mr-2 text-slate-500"><Trash2 className="w-3.5 h-3.5"/></button>
                       </div>
                     ))}
                   </div>
                </div>
              )}

              {/* 离线区简略版 */}
              {isAuthenticated && activeTab === 'send' && selectedFiles.length === 0 && (
                <div className="mt-8">
                   <h3 className="text-sm font-semibold text-slate-200 ml-2 mb-3">最近的离线快传</h3>
                   <div className="space-y-2">
                     {offlineHistoryLoading && offlineHistory.length === 0 ? <p className="text-xs text-center text-slate-500 py-4">加载中...</p> : 
                      offlineHistory.length === 0 ? <p className="text-xs text-center text-slate-500 py-4">暂无离线快传记录</p> : 
                      offlineHistory.map(session => (
                        <div key={session.sessionId} onClick={() => setSelectedOfflineSession(session)} className="glass-panel p-3 rounded-xl flex items-center justify-between hover:bg-white/5 active:bg-white/10">
                          <div className="flex-1 min-w-0">
                            <p className="text-sm font-medium text-slate-200">{session.pickupCode} <span className="text-xs ml-2 text-slate-500">{getOfflineTransferSessionLabel(session)}</span></p>
                            <p className="text-[10px] text-slate-500 mt-1">{isOfflineSessionReady(session) ? '可接收' : '处理中'} • {session.files.length}个文件 • {getOfflineTransferSessionSize(session)}</p>
                          </div>
                          <ChevronRight className="w-4 h-4 text-slate-500"/>
                        </div>
                      ))}
                   </div>
                </div>
              )}
            </motion.div>
          ) : (
            <motion.div key="receive" initial={{ opacity: 0, x: 10 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -10 }} className="flex-1 flex flex-col bg-transparent">
              <TransferReceive embedded />
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Offline History Modal Mobile */}
      <AnimatePresence>
        {selectedOfflineSession && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-50 bg-[#0f172a]/95 backdrop-blur flex flex-col p-6 items-center justify-center">
             <button onClick={() => setSelectedOfflineSession(null)} className="absolute top-6 right-6 p-2 bg-white/10 rounded-full"><X className="w-5 h-5 text-white"/></button>
             <h3 className="text-xs text-slate-400 tracking-[0.3em] font-medium uppercase mb-4">取件码</h3>
             <div className="text-6xl font-mono text-cyan-400 font-bold tracking-[0.1em] bg-cyan-900/20 px-6 py-4 rounded-3xl border border-cyan-500/20 mb-8 shadow-[0_0_40px_rgba(34,211,238,0.2)]">
                {selectedOfflineSession.pickupCode}
             </div>
             {selectedOfflineSessionQrImageUrl && <img src={selectedOfflineSessionQrImageUrl} className="w-48 h-48 rounded-2xl mb-8 border-4 border-white shadow-2xl" />}
             
             <Button className="w-full max-w-sm rounded-xl h-14 bg-[#336EFF] text-base" onClick={() => copyOfflineSessionLink(selectedOfflineSession)}>
                {historyCopiedSessionId === selectedOfflineSession.sessionId ? '链接已复制' : '复制提取链接'}
             </Button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
