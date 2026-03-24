import React, { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  CheckCircle,
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
} from 'lucide-react';
import { useSearchParams } from 'react-router-dom';

import { useAuth } from '@/src/auth/AuthProvider';
import { Button } from '@/src/components/ui/button';
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
  TRANSFER_CHUNK_SIZE,
} from '@/src/lib/transfer-protocol';
import { waitForTransferChannelDrain } from '@/src/lib/transfer-runtime';
import { flushPendingRemoteIceCandidates, handleRemoteIceCandidate } from '@/src/lib/transfer-signaling';
import {
  DEFAULT_TRANSFER_ICE_SERVERS,
  createTransferSession,
  pollTransferSignals,
  postTransferSignal,
  uploadOfflineTransferFile,
} from '@/src/lib/transfer';
import type { TransferMode, TransferSessionResponse } from '@/src/lib/types';
import { cn } from '@/src/lib/utils';

import {
  buildQrImageUrl,
  canSendTransferFiles,
  formatTransferSize,
  getTransferModeSummary,
  resolveInitialTransferTab,
} from './transfer-state';
import TransferReceive from './TransferReceive';

type SendPhase = 'idle' | 'creating' | 'waiting' | 'connecting' | 'uploading' | 'transferring' | 'completed' | 'error';

function parseJsonPayload<T>(payload: string): T | null {
  try {
    return JSON.parse(payload) as T;
  } catch {
    return null;
  }
}

function getPhaseMessage(mode: TransferMode, phase: SendPhase, errorMessage: string) {
  if (mode === 'OFFLINE') {
    switch (phase) {
      case 'creating':
        return '正在创建离线快传会话并生成取件链接...';
      case 'uploading':
        return '文件正在上传到站点存储，上传完成后 7 天内都可以反复接收。';
      case 'completed':
        return '离线文件已上传完成，接收方现在可以多次下载或存入网盘。';
      case 'error':
        return errorMessage || '离线快传初始化失败，请重试。';
      default:
        return '拖拽文件后会生成离线取件码，并把文件上传到站点存储保留 7 天。';
    }
  }

  switch (phase) {
    case 'creating':
      return '正在创建快传会话并准备 P2P 连接...';
    case 'waiting':
      return '分享链接和二维码已经生成，等待接收端打开页面并选择要接收的文件。';
    case 'connecting':
      return '接收端已进入页面，正在交换浏览器连接信息并同步文件清单...';
    case 'transferring':
      return 'P2P 直连已建立，文件正在发送到对方浏览器。';
    case 'completed':
      return '本次文件已发送完成，对方页面现在可以下载。';
    case 'error':
      return errorMessage || '快传会话初始化失败，请重试。';
    default:
      return '拖拽文件后会自动生成会话、二维码和公开接收页链接。';
  }
}

export default function Transfer() {
  const { session: authSession } = useAuth();
  const [searchParams] = useSearchParams();
  const sessionId = searchParams.get('session');
  const allowSend = canSendTransferFiles(Boolean(authSession?.token));
  const [activeTab, setActiveTab] = useState(() => resolveInitialTransferTab(allowSend, sessionId));

  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [transferMode, setTransferMode] = useState<TransferMode>('ONLINE');
  const [session, setSession] = useState<TransferSessionResponse | null>(null);
  const [sendPhase, setSendPhase] = useState<SendPhase>('idle');
  const [sendProgress, setSendProgress] = useState(0);
  const [sendError, setSendError] = useState('');
  const [copied, setCopied] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const folderInputRef = useRef<HTMLInputElement>(null);
  const copiedTimerRef = useRef<number | null>(null);
  const pollTimerRef = useRef<number | null>(null);
  const peerConnectionRef = useRef<RTCPeerConnection | null>(null);
  const dataChannelRef = useRef<RTCDataChannel | null>(null);
  const cursorRef = useRef(0);
  const bootstrapIdRef = useRef(0);
  const totalBytesRef = useRef(0);
  const sentBytesRef = useRef(0);
  const sendingStartedRef = useRef(false);
  const pendingRemoteCandidatesRef = useRef<RTCIceCandidateInit[]>([]);
  const manifestRef = useRef<TransferFileDescriptor[]>([]);

  useEffect(() => {
    if (!folderInputRef.current) {
      return;
    }

    folderInputRef.current.setAttribute('webkitdirectory', '');
    folderInputRef.current.setAttribute('directory', '');
  }, []);

  useEffect(() => {
    return () => {
      cleanupCurrentTransfer();
      if (copiedTimerRef.current) {
        window.clearTimeout(copiedTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (!allowSend || sessionId) {
      setActiveTab('receive');
    }
  }, [allowSend, sessionId]);

  useEffect(() => {
    if (selectedFiles.length === 0) {
      return;
    }

    void bootstrapTransfer(selectedFiles);
  }, [transferMode]);

  const totalSize = selectedFiles.reduce((sum, file) => sum + file.size, 0);
  const shareLink = session
    ? buildTransferShareUrl(window.location.origin, session.sessionId, getTransferRouterMode())
    : '';
  const qrImageUrl = shareLink ? buildQrImageUrl(shareLink) : '';
  const transferModeSummary = getTransferModeSummary(transferMode);

  function cleanupCurrentTransfer() {
    if (pollTimerRef.current) {
      window.clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }

    if (dataChannelRef.current) {
      dataChannelRef.current.close();
      dataChannelRef.current = null;
    }

    if (peerConnectionRef.current) {
      peerConnectionRef.current.close();
      peerConnectionRef.current = null;
    }

    cursorRef.current = 0;
    sendingStartedRef.current = false;
    pendingRemoteCandidatesRef.current = [];
  }

  function resetSenderState() {
    cleanupCurrentTransfer();
    setSession(null);
    setSelectedFiles([]);
    setSendPhase('idle');
    setSendProgress(0);
    setSendError('');
  }

  async function copyToClipboard(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      if (copiedTimerRef.current) {
        window.clearTimeout(copiedTimerRef.current);
      }
      copiedTimerRef.current = window.setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  }

  function ensureReadyState(nextFiles: File[]) {
    setSelectedFiles(nextFiles);

    if (nextFiles.length === 0) {
      resetSenderState();
      return;
    }

    void bootstrapTransfer(nextFiles);
  }

  function appendFiles(files: FileList | File[]) {
    const nextFiles = [...selectedFiles, ...Array.from(files)];
    ensureReadyState(nextFiles);
  }

  function handleFileSelect(event: React.ChangeEvent<HTMLInputElement>) {
    if (event.target.files?.length) {
      appendFiles(event.target.files);
    }
    event.target.value = '';
  }

  function handleDragOver(event: React.DragEvent) {
    event.preventDefault();
  }

  function handleDrop(event: React.DragEvent) {
    event.preventDefault();
    if (event.dataTransfer.files?.length) {
      appendFiles(event.dataTransfer.files);
    }
  }

  function removeFile(indexToRemove: number) {
    ensureReadyState(selectedFiles.filter((_, index) => index !== indexToRemove));
  }

  async function bootstrapTransfer(files: File[]) {
    const bootstrapId = bootstrapIdRef.current + 1;
    bootstrapIdRef.current = bootstrapId;

    cleanupCurrentTransfer();
    setSendError('');
    setSendPhase('creating');
    setSendProgress(0);
    manifestRef.current = createTransferFileManifest(files);
    totalBytesRef.current = 0;
    sentBytesRef.current = 0;

    try {
      const createdSession = await createTransferSession(files, transferMode);
      if (bootstrapIdRef.current !== bootstrapId) {
        return;
      }

      setSession(createdSession);
      if (createdSession.mode === 'OFFLINE') {
        await uploadOfflineFiles(createdSession, files, bootstrapId);
        return;
      }

      setSendPhase('waiting');
      await setupSenderPeer(createdSession, files, bootstrapId);
    } catch (error) {
      if (bootstrapIdRef.current !== bootstrapId) {
        return;
      }
      setSendPhase('error');
      setSendError(error instanceof Error ? error.message : '快传会话创建失败');
    }
  }

  async function uploadOfflineFiles(createdSession: TransferSessionResponse, files: File[], bootstrapId: number) {
    setSendPhase('uploading');
    totalBytesRef.current = files.reduce((sum, file) => sum + file.size, 0);
    sentBytesRef.current = 0;
    setSendProgress(0);

    for (const [index, file] of files.entries()) {
      if (bootstrapIdRef.current !== bootstrapId) {
        return;
      }

      const sessionFile = createdSession.files[index];
      if (!sessionFile?.id) {
        throw new Error('离线快传文件清单不完整，请重新开始本次发送。');
      }

      let lastLoaded = 0;
      await uploadOfflineTransferFile(createdSession.sessionId, sessionFile.id, file, ({ loaded, total }) => {
        const delta = loaded - lastLoaded;
        lastLoaded = loaded;
        sentBytesRef.current += delta;

        if (loaded >= total) {
          sentBytesRef.current = Math.min(totalBytesRef.current, sentBytesRef.current);
        }

        if (totalBytesRef.current > 0) {
          setSendProgress(Math.min(99, Math.round((sentBytesRef.current / totalBytesRef.current) * 100)));
        }
      });
    }

    setSendProgress(100);
    setSendPhase('completed');
  }

  async function setupSenderPeer(createdSession: TransferSessionResponse, files: File[], bootstrapId: number) {
    const connection = new RTCPeerConnection({
      iceServers: DEFAULT_TRANSFER_ICE_SERVERS,
    });
    const channel = connection.createDataChannel('portal-transfer', {
      ordered: true,
    });

    peerConnectionRef.current = connection;
    dataChannelRef.current = channel;
    channel.binaryType = 'arraybuffer';

    connection.onicecandidate = (event) => {
      if (!event.candidate) {
        return;
      }

      void postTransferSignal(
        createdSession.sessionId,
        'sender',
        'ice-candidate',
        JSON.stringify(event.candidate.toJSON()),
      );
    };

    connection.onconnectionstatechange = () => {
      if (connection.connectionState === 'connected') {
        setSendPhase((current) => (current === 'transferring' || current === 'completed' ? current : 'connecting'));
      }

      if (connection.connectionState === 'failed' || connection.connectionState === 'disconnected') {
        setSendPhase('error');
        setSendError('浏览器直连失败，请重新生成分享链接再试一次。');
      }
    };

    channel.onopen = () => {
      channel.send(createTransferFileManifestMessage(manifestRef.current));
    };

    channel.onmessage = (event) => {
      if (typeof event.data !== 'string') {
        return;
      }

      const message = parseJsonPayload<{type?: string; fileIds?: string[];}>(event.data);
      if (!message || message.type !== 'receive-request' || !Array.isArray(message.fileIds)) {
        return;
      }

      if (sendingStartedRef.current) {
        return;
      }

      const requestedFiles = manifestRef.current.filter((item) => message.fileIds?.includes(item.id));
      if (requestedFiles.length === 0) {
        return;
      }

      sendingStartedRef.current = true;
      totalBytesRef.current = requestedFiles.reduce((sum, file) => sum + file.size, 0);
      sentBytesRef.current = 0;
      setSendProgress(0);
      void sendSelectedFiles(channel, files, requestedFiles, bootstrapId);
    };

    channel.onerror = () => {
      setSendPhase('error');
      setSendError('数据通道建立失败，请重新开始本次快传。');
    };

    startSenderPolling(createdSession.sessionId, connection, bootstrapId);

    const offer = await connection.createOffer();
    await connection.setLocalDescription(offer);
    await postTransferSignal(createdSession.sessionId, 'sender', 'offer', JSON.stringify(offer));
  }

  function startSenderPolling(sessionId: string, connection: RTCPeerConnection, bootstrapId: number) {
    let polling = false;

    pollTimerRef.current = window.setInterval(() => {
      if (polling || bootstrapIdRef.current !== bootstrapId) {
        return;
      }

      polling = true;

      void pollTransferSignals(sessionId, 'sender', cursorRef.current)
        .then(async (response) => {
          if (bootstrapIdRef.current !== bootstrapId) {
            return;
          }

          cursorRef.current = response.nextCursor;

          for (const item of response.items) {
            if (item.type === 'peer-joined') {
              setSendPhase((current) => (current === 'waiting' ? 'connecting' : current));
              continue;
            }

            if (item.type === 'answer' && !connection.currentRemoteDescription) {
              const answer = parseJsonPayload<RTCSessionDescriptionInit>(item.payload);
              if (answer) {
                await connection.setRemoteDescription(answer);
                pendingRemoteCandidatesRef.current = await flushPendingRemoteIceCandidates(
                  connection,
                  pendingRemoteCandidatesRef.current,
                );
              }
              continue;
            }

            if (item.type === 'ice-candidate') {
              const candidate = parseJsonPayload<RTCIceCandidateInit>(item.payload);
              if (candidate) {
                pendingRemoteCandidatesRef.current = await handleRemoteIceCandidate(
                  connection,
                  pendingRemoteCandidatesRef.current,
                  candidate,
                );
              }
            }
          }
        })
        .catch((error) => {
          if (bootstrapIdRef.current !== bootstrapId) {
            return;
          }
          setSendPhase('error');
          setSendError(error instanceof Error ? error.message : '轮询连接状态失败');
        })
        .finally(() => {
          polling = false;
        });
    }, SIGNAL_POLL_INTERVAL_MS);
  }

  async function sendSelectedFiles(
    channel: RTCDataChannel,
    files: File[],
    requestedFiles: TransferFileDescriptor[],
    bootstrapId: number,
  ) {
    setSendPhase('transferring');
    const filesById = new Map(files.map((file) => [createTransferFileId(file), file]));

    for (const descriptor of requestedFiles) {
      if (bootstrapIdRef.current !== bootstrapId || channel.readyState !== 'open') {
        return;
      }

      const file = filesById.get(descriptor.id);
      if (!file) {
        continue;
      }

      channel.send(createTransferFileMetaMessage(descriptor));

      for (let offset = 0; offset < file.size; offset += TRANSFER_CHUNK_SIZE) {
        if (bootstrapIdRef.current !== bootstrapId || channel.readyState !== 'open') {
          return;
        }

        const chunk = await file.slice(offset, offset + TRANSFER_CHUNK_SIZE).arrayBuffer();
        await waitForTransferChannelDrain(channel);
        channel.send(chunk);
        sentBytesRef.current += chunk.byteLength;

        if (totalBytesRef.current > 0) {
          setSendProgress(Math.min(
            99,
            Math.round((sentBytesRef.current / totalBytesRef.current) * 100),
          ));
        }
      }

      channel.send(createTransferFileCompleteMessage(descriptor.id));
    }

    channel.send(createTransferCompleteMessage());
    setSendProgress(100);
    setSendPhase('completed');
  }

  return (
    <div className="flex-1 flex flex-col items-center py-6 md:py-10">
      <div className="w-full max-w-4xl">
        <div className="text-center mb-10">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-br from-[#336EFF] via-blue-500 to-cyan-400 shadow-lg shadow-[#336EFF]/20 mb-6">
            <Send className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-3xl font-bold text-white mb-3">快传</h1>
          <p className="text-slate-400">在线快传走浏览器 P2P 一次性传输，离线快传会把文件存到站点存储里保留 7 天，可被反复接收。</p>
        </div>

        <div className="glass-panel border border-white/10 rounded-3xl overflow-hidden bg-[#0f172a]/80 backdrop-blur-xl shadow-2xl">
          {allowSend ? (
            <div className="flex border-b border-white/10">
              <button
                onClick={() => setActiveTab('send')}
                className={cn(
                  'flex-1 py-5 text-center font-medium transition-colors relative',
                  activeTab === 'send' ? 'text-white' : 'text-slate-400 hover:text-slate-200 hover:bg-white/5',
                )}
              >
                <div className="flex items-center justify-center gap-2">
                  <UploadCloud className="w-5 h-5" />
                  发送文件
                </div>
                {activeTab === 'send' ? (
                  <motion.div layoutId="activeTransferTab" className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#336EFF]" />
                ) : null}
              </button>
              <button
                onClick={() => setActiveTab('receive')}
                className={cn(
                  'flex-1 py-5 text-center font-medium transition-colors relative',
                  activeTab === 'receive' ? 'text-white' : 'text-slate-400 hover:text-slate-200 hover:bg-white/5',
                )}
              >
                <div className="flex items-center justify-center gap-2">
                  <DownloadCloud className="w-5 h-5" />
                  接收文件
                </div>
                {activeTab === 'receive' ? (
                  <motion.div layoutId="activeTransferTab" className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#336EFF]" />
                ) : null}
              </button>
            </div>
          ) : null}

          <div className="p-8 min-h-[420px] flex flex-col relative min-w-0">
            <AnimatePresence mode="wait">
              {activeTab === 'send' ? (
                <motion.div
                  key="send"
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: 20 }}
                  transition={{ duration: 0.2 }}
                  className="flex-1 flex flex-col h-full min-w-0"
                >
                  <div className="mb-6 grid gap-3 md:grid-cols-2">
                    {(['ONLINE', 'OFFLINE'] as TransferMode[]).map((mode) => {
                      const summary = getTransferModeSummary(mode);
                      const active = transferMode === mode;

                      return (
                        <button
                          key={mode}
                          type="button"
                          onClick={() => setTransferMode(mode)}
                          className={cn(
                            'rounded-2xl border p-4 text-left transition-colors',
                            active
                              ? 'border-blue-400/40 bg-blue-500/10'
                              : 'border-white/10 bg-white/[0.03] hover:bg-white/[0.05]',
                          )}
                        >
                          <div className="flex items-center justify-between gap-3">
                            <p className="text-sm font-semibold text-white">{summary.title}</p>
                            <span className={cn(
                              'rounded-full px-2.5 py-1 text-[11px] font-medium',
                              active ? 'bg-blue-400/15 text-blue-100' : 'bg-white/10 text-slate-300',
                            )}>
                              {mode === 'ONLINE' ? '一次接收' : '7 天多次'}
                            </span>
                          </div>
                          <p className="mt-2 text-sm leading-6 text-slate-400">{summary.description}</p>
                        </button>
                      );
                    })}
                  </div>

                  {selectedFiles.length === 0 ? (
                    <div
                      className="flex-1 border-2 border-dashed border-white/10 rounded-2xl flex flex-col items-center justify-center p-10 transition-colors hover:border-[#336EFF]/50 hover:bg-[#336EFF]/5"
                      onDragOver={handleDragOver}
                      onDrop={handleDrop}
                    >
                      <div className="w-20 h-20 rounded-full bg-blue-500/10 flex items-center justify-center mb-6">
                        <UploadCloud className="w-10 h-10 text-[#336EFF]" />
                      </div>
                      <h3 className="text-xl font-medium text-white mb-2">拖拽文件或文件夹到此处</h3>
                      <p className="text-slate-400 mb-8 text-center max-w-md">
                        {transferModeSummary.description}
                      </p>
                      <div className="flex flex-col sm:flex-row items-center gap-4">
                        <Button onClick={() => fileInputRef.current?.click()} className="bg-[#336EFF] hover:bg-blue-600 text-white px-8">
                          <FileIcon className="w-4 h-4 mr-2" />
                          选择文件
                        </Button>
                        <Button
                          onClick={() => folderInputRef.current?.click()}
                          variant="outline"
                          className="border-white/10 hover:bg-white/10 text-slate-300 px-8"
                        >
                          <Folder className="w-4 h-4 mr-2" />
                          选择文件夹
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <div className="flex-1 flex flex-col md:flex-row gap-8">
                      <div className="flex-1 flex flex-col items-center justify-center bg-black/20 rounded-2xl p-8 border border-white/5 relative min-w-0">
                        <button onClick={resetSenderState} className="absolute top-4 right-4 text-slate-500 hover:text-white transition-colors" aria-label="取消发送">
                          <X className="w-5 h-5" />
                        </button>

                        <h3 className="text-slate-400 text-sm font-medium mb-2 uppercase tracking-widest">取件码</h3>
                        <div className="text-5xl md:text-6xl font-bold text-white tracking-[0.2em] mb-8 font-mono">
                          {session?.pickupCode ?? '......'}
                        </div>

                        {qrImageUrl ? (
                          <div className="bg-white p-4 rounded-2xl mb-6 shadow-[0_18px_48px_rgba(15,23,42,0.18)]">
                            <img src={qrImageUrl} alt="快传分享二维码" className="w-44 h-44 rounded-xl" />
                          </div>
                        ) : null}

                        <div className="w-full max-w-xs rounded-2xl border border-white/10 bg-black/30 p-3 mb-4">
                          <div className="flex items-center gap-2 text-xs uppercase tracking-[0.24em] text-slate-500 mb-2">
                            <LinkIcon className="w-3.5 h-3.5" />
                            分享链接
                          </div>
                          <div className="text-sm text-slate-200 font-mono truncate">{shareLink || '会话创建中...'}</div>
                        </div>

                        <Button
                          variant="outline"
                          className="w-full max-w-xs border-white/10 hover:bg-white/10 text-slate-200"
                          onClick={() => void copyToClipboard(shareLink)}
                          disabled={!shareLink}
                        >
                          {copied ? <CheckCircle className="w-4 h-4 mr-2 text-emerald-400" /> : <Copy className="w-4 h-4 mr-2" />}
                          {copied ? '已复制' : '复制链接'}
                        </Button>
                      </div>

                      <div className="flex-1 flex flex-col min-w-0">
                        <div className="flex items-center justify-between mb-4 gap-4">
                          <div>
                            <h3 className="text-lg font-medium text-white">待发送文件</h3>
                            <span className="text-sm text-slate-400">{selectedFiles.length} 个项目 • {formatTransferSize(totalSize)}</span>
                          </div>
                          <Button
                            size="sm"
                            variant="outline"
                            className="h-8 border-white/10 hover:bg-white/10 text-slate-300 px-2 shrink-0"
                            onClick={() => folderInputRef.current?.click()}
                          >
                            <FolderPlus className="w-4 h-4 mr-1" />
                            添加文件夹
                          </Button>
                        </div>

                        <div className="flex-1 overflow-y-auto pr-2 space-y-3 max-h-[300px] mb-4">
                          {selectedFiles.map((file, index) => (
                            <div key={`${file.name}-${index}`} className="flex items-center gap-3 bg-white/5 border border-white/5 rounded-xl p-3 group transition-colors hover:bg-white/10">
                              <div className="w-10 h-10 rounded-lg bg-blue-500/10 flex items-center justify-center shrink-0">
                                <FileIcon className="w-5 h-5 text-blue-400" />
                              </div>
                              <div className="flex-1 min-w-0">
                                <p className="text-sm font-medium text-slate-200 truncate">{file.name}</p>
                                <p className="text-xs text-slate-500">{formatTransferSize(file.size)}</p>
                              </div>
                              <button
                                onClick={() => removeFile(index)}
                                className="p-2 text-slate-500 hover:text-red-400 opacity-100 md:opacity-0 group-hover:opacity-100 transition-opacity"
                                title="移除文件"
                              >
                                <Trash2 className="w-4 h-4" />
                              </button>
                            </div>
                          ))}
                        </div>

                        <button
                          onClick={() => fileInputRef.current?.click()}
                          className="w-full flex items-center justify-center gap-2 py-4 border-2 border-dashed border-white/10 rounded-xl text-slate-400 hover:text-white hover:border-white/30 hover:bg-white/5 transition-colors mb-6 shrink-0"
                        >
                          <Plus className="w-5 h-5" />
                          <span className="font-medium">添加更多文件</span>
                        </button>

                        <div className={cn(
                          'mt-auto rounded-xl p-4 flex items-start gap-4 border',
                          sendPhase === 'error'
                            ? 'bg-rose-500/10 border-rose-500/20'
                            : sendPhase === 'completed'
                              ? 'bg-emerald-500/10 border-emerald-500/20'
                              : 'bg-blue-500/10 border-blue-500/20',
                        )}>
                          {sendPhase === 'completed' ? (
                            <CheckCircle className="w-6 h-6 text-emerald-400 shrink-0" />
                          ) : (
                            <Loader2 className={cn(
                              'w-6 h-6 shrink-0',
                              sendPhase === 'error' ? 'text-rose-400' : 'text-blue-400 animate-spin',
                            )} />
                          )}
                          <div className="min-w-0">
                            <p className={cn(
                              'text-sm font-medium',
                              sendPhase === 'error'
                                ? 'text-rose-300'
                                : sendPhase === 'completed'
                                  ? 'text-emerald-300'
                                  : 'text-blue-300',
                            )}>
                              {getPhaseMessage(transferMode, sendPhase, sendError)}
                            </p>
                            <p className="text-xs text-slate-400 mt-1">
                              发送进度 {sendProgress}%{session ? ` · 会话有效期至 ${new Date(session.expiresAt).toLocaleString('zh-CN', {month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'})}` : ''}
                            </p>
                          </div>
                        </div>
                      </div>
                    </div>
                  )}

                  <input type="file" multiple className="hidden" ref={fileInputRef} onChange={handleFileSelect} />
                  <input type="file" multiple className="hidden" ref={folderInputRef} onChange={handleFileSelect} />
                </motion.div>
              ) : (
                <motion.div
                  key="receive"
                  initial={{ opacity: 0, x: 20 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -20 }}
                  transition={{ duration: 0.2 }}
                  className="flex-1 flex flex-col h-full min-w-0 w-full"
                >
                  <TransferReceive embedded />
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-12">
          <div className="flex items-start gap-4 p-4 rounded-2xl bg-white/[0.02] border border-white/5">
            <div className="w-10 h-10 rounded-full bg-blue-500/10 flex items-center justify-center shrink-0">
              <Smartphone className="w-5 h-5 text-blue-400" />
            </div>
            <div>
              <h4 className="text-sm font-medium text-slate-200 mb-1">扫码直达网页</h4>
              <p className="text-xs text-slate-500 leading-relaxed">二维码不承载文件本身，只负责把另一台设备带到公开接收页。</p>
            </div>
          </div>
          <div className="flex items-start gap-4 p-4 rounded-2xl bg-white/[0.02] border border-white/5">
            <div className="w-10 h-10 rounded-full bg-emerald-500/10 flex items-center justify-center shrink-0">
              <Shield className="w-5 h-5 text-emerald-400" />
            </div>
            <div>
              <h4 className="text-sm font-medium text-slate-200 mb-1">浏览器 P2P 传输</h4>
              <p className="text-xs text-slate-500 leading-relaxed">网页之间通过 WebRTC DataChannel 交换文件字节，后端只做信令和会话协调。</p>
            </div>
          </div>
          <div className="flex items-start gap-4 p-4 rounded-2xl bg-white/[0.02] border border-white/5">
            <div className="w-10 h-10 rounded-full bg-cyan-500/10 flex items-center justify-center shrink-0">
              <Monitor className="w-5 h-5 text-cyan-400" />
            </div>
            <div>
              <h4 className="text-sm font-medium text-slate-200 mb-1">在线一次性，离线可重复</h4>
              <p className="text-xs text-slate-500 leading-relaxed">在线模式适合临时快传，离线模式会保留 7 天，接收后文件也不会立刻消失。</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
