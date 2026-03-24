import React, { useEffect, useRef, useState } from 'react';
import {
  Archive,
  CheckCircle,
  CheckSquare,
  DownloadCloud,
  File as FileIcon,
  Loader2,
  RefreshCcw,
  Shield,
  Square,
} from 'lucide-react';
import { useSearchParams } from 'react-router-dom';

import { useAuth } from '@/src/auth/AuthProvider';
import { NetdiskPathPickerModal } from '@/src/components/ui/NetdiskPathPickerModal';
import { Button } from '@/src/components/ui/button';
import { Input } from '@/src/components/ui/input';
import { buildTransferArchiveFileName, createTransferZipArchive } from '@/src/lib/transfer-archive';
import { resolveNetdiskSaveDirectory, saveFileToNetdisk } from '@/src/lib/netdisk-upload';
import {
  createTransferReceiveRequestMessage,
  parseTransferControlMessage,
  SIGNAL_POLL_INTERVAL_MS,
  toTransferChunk,
  type TransferFileDescriptor,
} from '@/src/lib/transfer-protocol';
import { flushPendingRemoteIceCandidates, handleRemoteIceCandidate } from '@/src/lib/transfer-signaling';
import {
  buildOfflineTransferDownloadUrl,
  DEFAULT_TRANSFER_ICE_SERVERS,
  importOfflineTransferFile,
  joinTransferSession,
  lookupTransferSession,
  pollTransferSignals,
  postTransferSignal,
} from '@/src/lib/transfer';
import type { TransferSessionResponse } from '@/src/lib/types';

import { canArchiveTransferSelection, formatTransferSize, sanitizeReceiveCode } from './transfer-state';

type ReceivePhase = 'idle' | 'joining' | 'waiting' | 'connecting' | 'receiving' | 'completed' | 'error';

interface DownloadableFile extends TransferFileDescriptor {
  progress: number;
  selected: boolean;
  requested: boolean;
  downloadUrl?: string;
  savedToNetdisk?: boolean;
}

interface IncomingTransferFile extends TransferFileDescriptor {
  chunks: Uint8Array[];
  receivedBytes: number;
}

function parseJsonPayload<T>(payload: string): T | null {
  try {
    return JSON.parse(payload) as T;
  } catch {
    return null;
  }
}

interface TransferReceiveProps {
  embedded?: boolean;
}

export default function TransferReceive({ embedded = false }: TransferReceiveProps) {
  const { session: authSession } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [receiveCode, setReceiveCode] = useState(searchParams.get('code') ?? '');
  const [transferSession, setTransferSession] = useState<TransferSessionResponse | null>(null);
  const [files, setFiles] = useState<DownloadableFile[]>([]);
  const [phase, setPhase] = useState<ReceivePhase>('idle');
  const [errorMessage, setErrorMessage] = useState('');
  const [overallProgress, setOverallProgress] = useState(0);
  const [lookupBusy, setLookupBusy] = useState(false);
  const [requestSubmitted, setRequestSubmitted] = useState(false);
  const [archiveRequested, setArchiveRequested] = useState(false);
  const [archiveName, setArchiveName] = useState(buildTransferArchiveFileName('快传文件'));
  const [archiveUrl, setArchiveUrl] = useState<string | null>(null);
  const [savingFileId, setSavingFileId] = useState<string | null>(null);
  const [saveMessage, setSaveMessage] = useState('');
  const [savePathPickerFileId, setSavePathPickerFileId] = useState<string | null>(null);
  const [saveRootPath, setSaveRootPath] = useState('/下载');

  const peerConnectionRef = useRef<RTCPeerConnection | null>(null);
  const dataChannelRef = useRef<RTCDataChannel | null>(null);
  const pollTimerRef = useRef<number | null>(null);
  const cursorRef = useRef(0);
  const lifecycleIdRef = useRef(0);
  const currentFileIdRef = useRef<string | null>(null);
  const totalBytesRef = useRef(0);
  const receivedBytesRef = useRef(0);
  const downloadUrlsRef = useRef<string[]>([]);
  const requestedFileIdsRef = useRef<string[]>([]);
  const pendingRemoteCandidatesRef = useRef<RTCIceCandidateInit[]>([]);
  const archiveBuiltRef = useRef(false);
  const completedFilesRef = useRef(new Map<string, {
    name: string;
    relativePath: string;
    blob: Blob;
    contentType: string;
  }>());
  const incomingFilesRef = useRef(new Map<string, IncomingTransferFile>());

  useEffect(() => {
    return () => {
      cleanupReceiver();
    };
  }, []);

  useEffect(() => {
    const sessionId = searchParams.get('session');
    if (!sessionId) {
      setTransferSession(null);
      setFiles([]);
      setPhase('idle');
      setOverallProgress(0);
      setRequestSubmitted(false);
      setArchiveRequested(false);
      setArchiveUrl(null);
      return;
    }

    void startReceivingSession(sessionId);
  }, [searchParams]);

  function cleanupReceiver() {
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

    for (const url of downloadUrlsRef.current) {
      URL.revokeObjectURL(url);
    }
    downloadUrlsRef.current = [];
    completedFilesRef.current.clear();
    incomingFilesRef.current.clear();
    currentFileIdRef.current = null;
    cursorRef.current = 0;
    receivedBytesRef.current = 0;
    totalBytesRef.current = 0;
    requestedFileIdsRef.current = [];
    pendingRemoteCandidatesRef.current = [];
    archiveBuiltRef.current = false;
  }

  async function startReceivingSession(sessionId: string) {
    const lifecycleId = lifecycleIdRef.current + 1;
    lifecycleIdRef.current = lifecycleId;

    cleanupReceiver();
    setPhase('joining');
    setErrorMessage('');
    setFiles([]);
    setOverallProgress(0);
    setRequestSubmitted(false);
    setArchiveRequested(false);
    setArchiveName(buildTransferArchiveFileName('快传文件'));
    setArchiveUrl(null);
    setSavingFileId(null);
      setSaveMessage('');

    try {
      const joinedSession = await joinTransferSession(sessionId);
      if (lifecycleIdRef.current !== lifecycleId) {
        return;
      }

      setTransferSession(joinedSession);
      setArchiveName(buildTransferArchiveFileName(`快传-${joinedSession.pickupCode}`));

      if (joinedSession.mode === 'OFFLINE') {
        const offlineFiles = joinedSession.files.map((file) => ({
          id: file.id ?? file.relativePath,
          name: file.name,
          size: file.size,
          contentType: file.contentType,
          relativePath: file.relativePath,
          progress: file.uploaded ? 100 : 0,
          selected: true,
          requested: true,
          downloadUrl: file.id ? buildOfflineTransferDownloadUrl(joinedSession.sessionId, file.id) : undefined,
          savedToNetdisk: false,
        }));

        setFiles(offlineFiles);
        setRequestSubmitted(true);
        setOverallProgress(offlineFiles.length > 0 ? 100 : 0);
        setPhase('completed');
        return;
      }

      const connection = new RTCPeerConnection({
        iceServers: DEFAULT_TRANSFER_ICE_SERVERS,
      });
      peerConnectionRef.current = connection;

      connection.onicecandidate = (event) => {
        if (!event.candidate) {
          return;
        }

        void postTransferSignal(
          joinedSession.sessionId,
          'receiver',
          'ice-candidate',
          JSON.stringify(event.candidate.toJSON()),
        );
      };

      connection.onconnectionstatechange = () => {
        if (connection.connectionState === 'connected') {
          setPhase((current) => (current === 'completed' ? current : 'connecting'));
        }

        if (connection.connectionState === 'failed' || connection.connectionState === 'disconnected') {
          setPhase('error');
          setErrorMessage('浏览器之间的直连失败，请重新打开分享链接。');
        }
      };

      connection.ondatachannel = (event) => {
        const channel = event.channel;
        dataChannelRef.current = channel;
        channel.binaryType = 'arraybuffer';
        channel.onopen = () => {
          setPhase((current) => (current === 'completed' ? current : 'connecting'));
        };
        channel.onmessage = (messageEvent) => {
          void handleIncomingMessage(messageEvent.data);
        };
      };

      startReceiverPolling(joinedSession.sessionId, connection, lifecycleId);
      setPhase('waiting');
    } catch (error) {
      if (lifecycleIdRef.current !== lifecycleId) {
        return;
      }

      setPhase('error');
      setErrorMessage(error instanceof Error ? error.message : '快传会话打开失败');
    }
  }

  function startReceiverPolling(sessionId: string, connection: RTCPeerConnection, lifecycleId: number) {
    let polling = false;

    pollTimerRef.current = window.setInterval(() => {
      if (polling || lifecycleIdRef.current !== lifecycleId) {
        return;
      }

      polling = true;

      void pollTransferSignals(sessionId, 'receiver', cursorRef.current)
        .then(async (response) => {
          if (lifecycleIdRef.current !== lifecycleId) {
            return;
          }

          cursorRef.current = response.nextCursor;

          for (const item of response.items) {
            if (item.type === 'offer') {
              const offer = parseJsonPayload<RTCSessionDescriptionInit>(item.payload);
              if (!offer) {
                continue;
              }

              setPhase('connecting');
              await connection.setRemoteDescription(offer);
              pendingRemoteCandidatesRef.current = await flushPendingRemoteIceCandidates(
                connection,
                pendingRemoteCandidatesRef.current,
              );
              const answer = await connection.createAnswer();
              await connection.setLocalDescription(answer);
              await postTransferSignal(sessionId, 'receiver', 'answer', JSON.stringify(answer));
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
          if (lifecycleIdRef.current !== lifecycleId) {
            return;
          }

          setPhase('error');
          setErrorMessage(error instanceof Error ? error.message : '轮询传输信令失败');
        })
        .finally(() => {
          polling = false;
        });
    }, SIGNAL_POLL_INTERVAL_MS);
  }

  async function finalizeArchiveDownload() {
    if (!archiveRequested || archiveBuiltRef.current || requestedFileIdsRef.current.length === 0) {
      return;
    }

    const archiveEntries = requestedFileIdsRef.current.map((fileId) => completedFilesRef.current.get(fileId)).filter(Boolean);
    if (archiveEntries.length !== requestedFileIdsRef.current.length) {
      return;
    }

    const archive = await createTransferZipArchive(
      archiveEntries.map((entry) => ({
        name: entry.name,
        relativePath: entry.relativePath,
        data: entry.blob,
      })),
    );

    const nextArchiveUrl = URL.createObjectURL(archive);
    downloadUrlsRef.current.push(nextArchiveUrl);
    archiveBuiltRef.current = true;
    setArchiveUrl(nextArchiveUrl);
  }

  async function handleIncomingMessage(data: string | ArrayBuffer | Blob) {
    if (typeof data === 'string') {
      const message = parseTransferControlMessage(data);

      if (!message) {
        return;
      }

      if (message.type === 'manifest') {
        setFiles(message.files.map((file) => ({
          ...file,
          progress: 0,
          selected: true,
          requested: false,
          savedToNetdisk: false,
        })));
        setPhase((current) => (current === 'receiving' || current === 'completed' ? current : 'waiting'));
        return;
      }

      if (message.type === 'file-meta') {
        currentFileIdRef.current = message.id;
        incomingFilesRef.current.set(message.id, {
          ...message,
          chunks: [],
          receivedBytes: 0,
        });
        setFiles((current) =>
          current.map((file) =>
            file.id === message.id
              ? {
                  ...file,
                  requested: true,
                  progress: 0,
                }
              : file,
          ),
        );
        return;
      }

      if (message.type === 'file-complete' && message.id) {
        finalizeDownloadableFile(message.id);
        currentFileIdRef.current = null;
        await finalizeArchiveDownload();
        return;
      }

      if (message.type === 'transfer-complete') {
        await finalizeArchiveDownload();
        setOverallProgress(100);
        setPhase('completed');
      }

      return;
    }

    const activeFileId = currentFileIdRef.current;
    if (!activeFileId) {
      return;
    }

    const targetFile = incomingFilesRef.current.get(activeFileId);
    if (!targetFile) {
      return;
    }

    const chunk = await toTransferChunk(data);
    targetFile.chunks.push(chunk);
    targetFile.receivedBytes += chunk.byteLength;
    receivedBytesRef.current += chunk.byteLength;

    setPhase('receiving');
    if (totalBytesRef.current > 0) {
      setOverallProgress(Math.min(99, Math.round((receivedBytesRef.current / totalBytesRef.current) * 100)));
    }

    setFiles((current) =>
      current.map((file) =>
        file.id === activeFileId
          ? {
              ...file,
              progress: Math.min(99, Math.round((targetFile.receivedBytes / Math.max(targetFile.size, 1)) * 100)),
            }
          : file,
      ),
    );
  }

  function finalizeDownloadableFile(fileId: string) {
    const targetFile = incomingFilesRef.current.get(fileId);
    if (!targetFile) {
      return;
    }

    const blob = new Blob(targetFile.chunks, {
      type: targetFile.contentType,
    });
    const downloadUrl = URL.createObjectURL(blob);
    downloadUrlsRef.current.push(downloadUrl);
    completedFilesRef.current.set(fileId, {
      name: targetFile.name,
      relativePath: targetFile.relativePath,
      blob,
      contentType: targetFile.contentType,
    });

    setFiles((current) =>
      current.map((file) =>
        file.id === fileId
          ? {
              ...file,
              progress: 100,
              requested: true,
              downloadUrl,
              savedToNetdisk: false,
            }
          : file,
      ),
    );
  }

  async function saveCompletedFile(fileId: string, rootPath: string) {
    const completedFile = completedFilesRef.current.get(fileId);
    if (!completedFile) {
      return;
    }

    setSavingFileId(fileId);
    setSaveMessage('');

    try {
      const netdiskFile = new File([completedFile.blob], completedFile.name, {
        type: completedFile.contentType || completedFile.blob.type || 'application/octet-stream',
      });
      const targetPath = resolveNetdiskSaveDirectory(completedFile.relativePath, rootPath);
      const savedFile = await saveFileToNetdisk(netdiskFile, targetPath);
      setFiles((current) =>
        current.map((file) =>
          file.id === fileId
            ? {
                ...file,
                savedToNetdisk: true,
          }
          : file,
        ),
      );
      setSaveMessage(`${savedFile.filename} 已存入网盘 ${savedFile.path}`);
    } catch (requestError) {
      setErrorMessage(requestError instanceof Error ? requestError.message : '存入网盘失败');
      throw requestError;
    } finally {
      setSavingFileId(null);
    }
  }

  function toggleFileSelection(fileId: string) {
    if (requestSubmitted) {
      return;
    }

    setFiles((current) =>
      current.map((file) =>
        file.id === fileId
          ? {
              ...file,
              selected: !file.selected,
            }
          : file,
      ),
    );
  }

  function toggleSelectAll(nextSelected: boolean) {
    if (requestSubmitted) {
      return;
    }

    setFiles((current) =>
      current.map((file) => ({
        ...file,
        selected: nextSelected,
      })),
    );
  }

  async function submitReceiveRequest(archive: boolean, fileIds?: string[]) {
    const channel = dataChannelRef.current;
    if (!channel || channel.readyState !== 'open') {
      setPhase('error');
      setErrorMessage('P2P 通道尚未准备好，请稍后再试。');
      return;
    }

    const requestedIds = fileIds ?? files.filter((file) => file.selected).map((file) => file.id);
    if (requestedIds.length === 0) {
      setErrorMessage('请先选择至少一个文件。');
      return;
    }

    const requestedSet = new Set(requestedIds);
    const requestedBytes = files
      .filter((file) => requestedSet.has(file.id))
      .reduce((sum, file) => sum + file.size, 0);

    requestedFileIdsRef.current = requestedIds;
    totalBytesRef.current = requestedBytes;
    receivedBytesRef.current = 0;
    archiveBuiltRef.current = false;
    setOverallProgress(0);
    setArchiveRequested(archive);
    setArchiveUrl(null);
    setRequestSubmitted(true);
    setErrorMessage('');

    setFiles((current) =>
      current.map((file) => ({
        ...file,
        selected: requestedSet.has(file.id),
        requested: requestedSet.has(file.id),
        progress: requestedSet.has(file.id) ? 0 : file.progress,
      })),
    );

    channel.send(createTransferReceiveRequestMessage(requestedIds, archive));
    setPhase('waiting');
  }

  async function handleLookupByCode() {
    setLookupBusy(true);
    setErrorMessage('');

    try {
      const result = await lookupTransferSession(receiveCode);
      setSearchParams({
        session: result.sessionId,
      });
    } catch (error) {
      setPhase('error');
      setErrorMessage(error instanceof Error ? error.message : '取件码无效或会话已过期');
    } finally {
      setLookupBusy(false);
    }
  }

  const sessionId = searchParams.get('session');
  const selectedFiles = files.filter((file) => file.selected);
  const requestedFiles = files.filter((file) => file.requested);
  const selectedSize = selectedFiles.reduce((sum, file) => sum + file.size, 0);
  const canZipAllFiles = canArchiveTransferSelection(files);
  const hasSelectableFiles = selectedFiles.length > 0;
  const canSubmitSelection = Boolean(dataChannelRef.current && dataChannelRef.current.readyState === 'open' && hasSelectableFiles);
  const isOfflineSession = transferSession?.mode === 'OFFLINE';

  const panelContent = (
    <>
      {!embedded ? (
        <div className="text-center mb-10">
          <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-500 via-teal-500 to-cyan-400 shadow-lg shadow-emerald-500/20">
            <DownloadCloud className="h-8 w-8 text-white" />
          </div>
          <h1 className="text-3xl font-bold mb-3">网页接收页</h1>
          <p className="text-slate-400">你现在打开的是公开接收链接。在线快传会走浏览器 P2P，离线快传会直接显示 7 天内可重复接收的文件。</p>
        </div>
      ) : null}

      <div className={embedded ? '' : 'glass-panel rounded-3xl border border-white/10 bg-[#0f172a]/80 shadow-2xl overflow-hidden'}>
        <div className={embedded ? '' : 'p-8'}>
          {!sessionId ? (
            <div className="mx-auto flex max-w-sm flex-col items-center">
              <div className="mb-8 flex h-20 w-20 items-center justify-center rounded-full bg-emerald-500/10">
                <DownloadCloud className="h-10 w-10 text-emerald-400" />
              </div>
              <h2 className="mb-6 text-xl font-medium">输入取件码打开接收页</h2>
              <div className="w-full mb-6">
                <Input
                  value={receiveCode}
                  onChange={(event) => setReceiveCode(sanitizeReceiveCode(event.target.value))}
                  inputMode="numeric"
                  aria-label="六位取件码"
                  placeholder="请输入 6 位取件码"
                  className="h-14 rounded-2xl border-white/10 bg-white/[0.03] px-4 text-center text-xl font-semibold tracking-[0.28em] text-slate-100 placeholder:text-slate-500 focus-visible:ring-emerald-400/60"
                />
              </div>
              <Button
                className="w-full h-12 text-lg bg-emerald-500 hover:bg-emerald-600 text-white"
                disabled={receiveCode.length !== 6 || lookupBusy}
                onClick={() => void handleLookupByCode()}
              >
                {lookupBusy ? '正在查找...' : '进入接收会话'}
              </Button>
              {errorMessage ? (
                <div className="mt-4 w-full rounded-xl border border-rose-500/20 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
                  {errorMessage}
                </div>
              ) : null}
            </div>
          ) : (
            <div className="grid gap-8 md:grid-cols-[1.08fr_0.92fr]">
              <div className="rounded-2xl border border-white/5 bg-black/20 p-6">
                <div className="flex items-center justify-between gap-4 mb-6">
                  <div>
                    <p className="text-xs uppercase tracking-[0.24em] text-slate-500">当前会话</p>
                    <h2 className="text-2xl font-semibold mt-2">{transferSession?.pickupCode ?? '连接中...'}</h2>
                  </div>
                  <Button
                    variant="outline"
                    className="border-white/10 text-slate-200 hover:bg-white/10"
                    onClick={() => {
                      if (sessionId) {
                        void startReceivingSession(sessionId);
                      }
                    }}
                  >
                    <RefreshCcw className="mr-2 h-4 w-4" />
                    重新连接
                  </Button>
                </div>

                <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4">
                  <div className="mb-4 flex items-center gap-3">
                    {phase === 'completed' ? (
                      <CheckCircle className="h-6 w-6 text-emerald-400" />
                    ) : (
                      <Loader2 className="h-6 w-6 animate-spin text-emerald-400" />
                    )}
                    <div>
                      <p className="text-sm font-medium text-white">
                        {phase === 'joining' && '正在加入快传会话...'}
                        {phase === 'waiting' && (files.length === 0
                          ? 'P2P 已连通，正在同步文件清单...'
                          : requestSubmitted
                            ? '已提交接收请求，等待发送端开始推送...'
                            : '文件清单已同步，请勾选要接收的文件。')}
                        {phase === 'connecting' && 'P2P 通道协商中...'}
                        {phase === 'receiving' && '文件正在接收...'}
                        {phase === 'completed' && (isOfflineSession
                          ? '离线文件已就绪，7 天内可以重复下载或存入网盘'
                          : archiveUrl
                            ? '接收完成，ZIP 已准备好下载'
                            : '接收完成，下面可以下载文件')}
                        {phase === 'error' && '接收失败'}
                      </p>
                      <p className="text-xs text-slate-400 mt-1">
                        {errorMessage || (isOfflineSession && transferSession
                          ? `离线有效期至 ${new Date(transferSession.expiresAt).toLocaleString('zh-CN', {month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'})}`
                          : `总进度 ${overallProgress}%`)}
                      </p>
                    </div>
                  </div>

                  <div className="h-2.5 w-full overflow-hidden rounded-full bg-black/40">
                    <div className="h-full rounded-full bg-gradient-to-r from-emerald-400 to-cyan-400" style={{width: `${overallProgress}%`}} />
                  </div>
                </div>

                {archiveUrl ? (
                  <div className="mt-5 rounded-2xl border border-cyan-400/20 bg-cyan-500/10 p-4">
                    <div className="flex items-start gap-3">
                      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-cyan-500/15">
                        <Archive className="h-5 w-5 text-cyan-300" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium text-white">全部文件 ZIP 已生成</p>
                        <p className="mt-1 text-xs text-slate-300">{archiveName}</p>
                      </div>
                      <a
                        href={archiveUrl}
                        download={archiveName}
                        className="rounded-lg border border-white/10 px-3 py-2 text-xs text-slate-100 transition-colors hover:bg-white/10"
                      >
                        下载 ZIP
                      </a>
                    </div>
                  </div>
                ) : null}
                {saveMessage ? (
                  <div className="mt-5 rounded-2xl border border-emerald-500/20 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-100">
                    {saveMessage}
                  </div>
                ) : null}
              </div>

              <div className="rounded-2xl border border-white/5 bg-black/20 p-6">
                <div className="mb-4 flex items-start justify-between gap-4">
                  <div>
                    <h3 className="text-lg font-medium">可接收文件</h3>
                    <p className="mt-1 text-xs text-slate-500">
                      {isOfflineSession
                        ? `离线模式 · ${files.length} 项`
                        : requestSubmitted
                        ? `已请求 ${requestedFiles.length} 项`
                        : `已选择 ${selectedFiles.length} 项 · ${formatTransferSize(selectedSize)}`}
                    </p>
                  </div>
                  {!requestSubmitted && files.length > 0 ? (
                    <div className="flex flex-wrap items-center justify-end gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        className="border-white/10 text-slate-200 hover:bg-white/10"
                        onClick={() => toggleSelectAll(true)}
                      >
                        全选
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        className="border-white/10 text-slate-200 hover:bg-white/10"
                        onClick={() => toggleSelectAll(false)}
                      >
                        清空
                      </Button>
                    </div>
                  ) : null}
                </div>

                {!requestSubmitted && files.length > 0 ? (
                  <div className="mb-4 flex flex-wrap gap-2">
                    <Button
                      className="bg-emerald-500 hover:bg-emerald-600 text-white"
                      disabled={!canSubmitSelection}
                      onClick={() => void submitReceiveRequest(false)}
                    >
                      接收选中项
                    </Button>
                    {canZipAllFiles ? (
                      <Button
                        variant="outline"
                        className="border-cyan-400/20 bg-cyan-500/10 text-cyan-100 hover:bg-cyan-500/15"
                        disabled={!dataChannelRef.current || dataChannelRef.current.readyState !== 'open'}
                        onClick={() => void submitReceiveRequest(true, files.map((file) => file.id))}
                      >
                        <Archive className="mr-2 h-4 w-4" />
                        全部下载 ZIP
                      </Button>
                    ) : null}
                  </div>
                ) : null}

                <div className="space-y-3">
                  {files.length === 0 ? (
                    <div className="rounded-xl border border-dashed border-white/10 bg-white/[0.02] px-4 py-5 text-sm text-slate-500">
                      {isOfflineSession ? '离线文件上传完成后，会直接在这里显示可下载清单。' : '连接建立后会先同步文件清单，你可以在这里先勾选想接收的内容。'}
                    </div>
                  ) : (
                    files.map((file) => (
                      <div key={file.id} className="rounded-xl border border-white/5 bg-white/[0.03] p-4">
                        <div className="flex items-start gap-3">
                          {!requestSubmitted ? (
                            <button
                              type="button"
                              className="mt-0.5 text-slate-300 hover:text-white"
                              onClick={() => toggleFileSelection(file.id)}
                              aria-label={file.selected ? '取消选择文件' : '选择文件'}
                            >
                              {file.selected ? <CheckSquare className="h-5 w-5 text-emerald-400" /> : <Square className="h-5 w-5" />}
                            </button>
                          ) : null}

                          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-500/10 shrink-0">
                            <FileIcon className="h-5 w-5 text-emerald-400" />
                          </div>

                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-medium text-slate-100">{file.name}</p>
                            <p className="truncate text-xs text-slate-500 mt-1">
                              {file.relativePath !== file.name ? `${file.relativePath} · ` : ''}
                              {formatTransferSize(file.size)}
                            </p>
                          </div>

                          {requestSubmitted ? (
                            file.requested ? (
                              file.downloadUrl ? (
                                <div className="flex items-center gap-2">
                                  <a
                                    href={file.downloadUrl}
                                    download={file.name}
                                    className="rounded-lg border border-white/10 px-3 py-2 text-xs text-slate-200 transition-colors hover:bg-white/10"
                                  >
                                    下载
                                  </a>
                                  {authSession?.token ? (
                                    <Button
                                      size="sm"
                                      variant="outline"
                                      className="border-white/10 text-slate-200 hover:bg-white/10"
                                      disabled={savingFileId === file.id || file.savedToNetdisk}
                                      onClick={() => setSavePathPickerFileId(file.id)}
                                    >
                                      {file.savedToNetdisk ? '已存入网盘' : savingFileId === file.id ? '存入中...' : '存入网盘'}
                                    </Button>
                                  ) : null}
                                </div>
                              ) : (
                                <span className="text-xs text-emerald-300">{file.progress}%</span>
                              )
                            ) : (
                              <span className="text-xs text-slate-500">未接收</span>
                            )
                          ) : null}
                        </div>

                        {requestSubmitted && file.requested ? (
                          <div className="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-black/40">
                            <div className="h-full rounded-full bg-emerald-400" style={{width: `${file.progress}%`}} />
                          </div>
                        ) : null}
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {!embedded ? (
        <div className="mt-10 grid gap-6 md:grid-cols-2">
          <div className="rounded-2xl border border-white/5 bg-white/[0.02] p-5">
            <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-emerald-500/10">
              <Shield className="h-5 w-5 text-emerald-400" />
            </div>
            <h4 className="text-sm font-medium text-slate-100 mb-1">在线走 P2P，离线走存储</h4>
            <p className="text-xs leading-6 text-slate-500">在线快传继续通过信令交换建立浏览器直连；离线快传会直接从站点存储里下载。</p>
          </div>
          <div className="rounded-2xl border border-white/5 bg-white/[0.02] p-5">
            <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-cyan-500/10">
              <Archive className="h-5 w-5 text-cyan-400" />
            </div>
            <h4 className="text-sm font-medium text-slate-100 mb-1">离线文件保留 7 天</h4>
            <p className="text-xs leading-6 text-slate-500">离线快传接收之后文件也不会立刻消失，在有效期内还能再次打开链接重复接收。</p>
          </div>
        </div>
      ) : null}

      <NetdiskPathPickerModal
        isOpen={Boolean(savePathPickerFileId)}
        title="选择存入位置"
        description="选择保存到网盘的根目录，快传里的相对目录结构会继续保留。"
        initialPath={saveRootPath}
        confirmLabel="存入这里"
        confirmPathPreview={(path) => {
          const offlineFile = savePathPickerFileId ? files.find((file) => file.id === savePathPickerFileId) : null;
          const completedFile = savePathPickerFileId ? completedFilesRef.current.get(savePathPickerFileId) : null;
          if (offlineFile) {
            return resolveNetdiskSaveDirectory(offlineFile.relativePath, path);
          }
          return completedFile ? resolveNetdiskSaveDirectory(completedFile.relativePath, path) : path;
        }}
        onClose={() => setSavePathPickerFileId(null)}
        onConfirm={async (path) => {
          if (!savePathPickerFileId) {
            return;
          }
          setSaveRootPath(path);
          if (isOfflineSession && transferSession) {
            const savedFile = await importOfflineTransferFile(transferSession.sessionId, savePathPickerFileId, path);
            setFiles((current) =>
              current.map((file) =>
                file.id === savePathPickerFileId
                  ? {
                      ...file,
                      savedToNetdisk: true,
                    }
                  : file,
              ),
            );
            setSaveMessage(`${savedFile.filename} 已存入网盘 ${savedFile.path}`);
          } else {
            await saveCompletedFile(savePathPickerFileId, path);
          }
          setSavePathPickerFileId(null);
        }}
      />
    </>
  );

  if (embedded) {
    return panelContent;
  }

  return (
    <div className="min-h-screen bg-[#07101D] px-4 py-8 text-white">
      <div className="mx-auto w-full max-w-4xl">
        {panelContent}
      </div>
    </div>
  );
}
