import React, { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import Topbar from '../components/Topbar';
import BackgroundEffects from '../components/BackgroundEffects';
import { buildOfflineTransferDownloadUrl, joinTransferSession, lookupTransferSession } from '../lib/transfer';
import { P2pReceiver, type P2pTransferProgress, type ReceivedP2pFile } from '../lib/p2p-transfer';
import { formatBytes, formatDateTime } from '../lib/format';
import type { TransferSessionResponse } from '../api/types';

function formatPercent(progress: P2pTransferProgress | null) {
  if (!progress || progress.totalBytes <= 0) {
    return 0;
  }
  return Math.min(100, Math.round((progress.sentBytes / progress.totalBytes) * 100));
}

export const TransferReceivePanel: React.FC<{ embedded?: boolean }> = ({ embedded = false }) => {
  const [searchParams] = useSearchParams();
  const initialCode = searchParams.get('code') ?? '';
  const receiverRef = useRef<P2pReceiver | null>(null);
  const receivedFilesRef = useRef<ReceivedP2pFile[]>([]);
  const [pickupCode, setPickupCode] = useState(initialCode);
  const [sessionInfo, setSessionInfo] = useState<{ pickupCode: string; expiresAt: string } | null>(null);
  const [offlineSession, setOfflineSession] = useState<TransferSessionResponse | null>(null);
  const [status, setStatus] = useState('');
  const [error, setError] = useState('');
  const [progress, setProgress] = useState<P2pTransferProgress | null>(null);
  const [receivedFiles, setReceivedFiles] = useState<ReceivedP2pFile[]>([]);
  const [isReceiving, setIsReceiving] = useState(false);
  const percent = formatPercent(progress);

  useEffect(() => {
    return () => {
      receiverRef.current?.stop();
      receivedFilesRef.current.forEach((file) => URL.revokeObjectURL(file.url));
    };
  }, []);

  function addReceivedFile(file: ReceivedP2pFile) {
    receivedFilesRef.current = [...receivedFilesRef.current, file];
    setReceivedFiles(receivedFilesRef.current);
  }

  async function startReceive() {
    const code = pickupCode.replace(/\D/g, '');
    if (code.length !== 6) {
      setError('请输入 6 位数字取件码');
      return;
    }

    receiverRef.current?.stop();
    receivedFilesRef.current.forEach((file) => URL.revokeObjectURL(file.url));
    receivedFilesRef.current = [];
    setReceivedFiles([]);
    setError('');
    setStatus('正在查找快传会话...');
    setProgress(null);
    setOfflineSession(null);
    setIsReceiving(true);

    try {
      const lookup = await lookupTransferSession(code);
      if (!lookup) {
        throw new Error('取件码无效或会话已过期');
      }
      const joined = await joinTransferSession(lookup.sessionId);
      setSessionInfo({ pickupCode: joined.pickupCode, expiresAt: joined.expiresAt });

      if (lookup.mode === 'OFFLINE') {
        setOfflineSession(joined);
        setStatus('文件已准备完成，可以直接下载');
        setIsReceiving(false);
        return;
      }

      const receiver = new P2pReceiver(joined.sessionId, {
        onStatus: setStatus,
        onError: setError,
        onProgress: setProgress,
        onFileReceived: addReceivedFile,
        onComplete: () => setStatus('所有文件已接收完成'),
      });
      receiverRef.current = receiver;
      await receiver.start();
    } catch (err) {
      setError(err instanceof Error ? err.message : '接收 P2P 快传失败');
      setIsReceiving(false);
    }
  }

  const content = (
    <div className={embedded ? '' : 'mx-auto max-w-3xl'}>
      <div className={embedded ? '' : 'card-container p-8 md:p-10'}>
            <div className="mb-8">
              <p className="text-sm font-semibold text-brand-light dark:text-brand-dark mb-2">在线快传</p>
              <h1 className="text-3xl font-bold text-text-primary-light dark:text-white">接收 P2P 文件</h1>
              <p className="mt-3 text-text-secondary-light dark:text-text-secondary-dark">
                输入发送方给你的 6 位取件码，浏览器会通过 WebRTC DataChannel 直连接收文件。文件内容不会经过服务器。
              </p>
            </div>

            <div className="flex flex-col sm:flex-row gap-3">
              <input
                type="text"
                inputMode="numeric"
                maxLength={6}
                className="input-field flex-1 tracking-[0.25em] text-xl font-bold"
                placeholder="取件码"
                value={pickupCode}
                onChange={(event) => setPickupCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    void startReceive();
                  }
                }}
              />
              <button
                className="btn-primary px-6 disabled:opacity-60 disabled:cursor-not-allowed"
                disabled={isReceiving && receivedFiles.length === 0}
                onClick={() => void startReceive()}
              >
                {isReceiving && receivedFiles.length === 0 ? '连接中...' : '开始接收'}
              </button>
            </div>

            {sessionInfo && (
              <div className="mt-6 rounded-xl bg-[#F8FBFF] dark:bg-black/20 p-4 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                <p>取件码：<span className="font-bold text-brand-light dark:text-brand-dark tracking-[0.2em]">{sessionInfo.pickupCode}</span></p>
                <p className="mt-1">过期时间：{formatDateTime(sessionInfo.expiresAt)}</p>
              </div>
            )}

            {offlineSession && (
              <div className="mt-6 rounded-2xl border border-[#D9E3F2] bg-[#F8FBFF] p-5 dark:border-[#222233] dark:bg-black/20">
                <h2 className="text-lg font-bold text-text-primary-light dark:text-white">离线快传文件</h2>
                <p className="mt-2 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                  这是“稍后接收”快传，文件已经在服务器准备好了，可以直接下载。
                </p>
                <div className="mt-4 space-y-3">
                  {offlineSession.files.map((file) => (
                    <div key={file.id ?? file.relativePath} className="flex flex-col gap-3 rounded-xl border border-[#D9E3F2] bg-white p-4 dark:border-[#222233] dark:bg-black/20 sm:flex-row sm:items-center sm:justify-between">
                      <div className="min-w-0">
                        <p className="truncate font-semibold text-text-primary-light dark:text-white">{file.relativePath}</p>
                        <p className="text-sm text-text-muted-light dark:text-text-muted-dark">
                          {formatBytes(file.size)} · {file.contentType}
                        </p>
                      </div>
                      <a
                        className="btn-primary px-4 py-2 text-center text-sm"
                        href={buildOfflineTransferDownloadUrl(offlineSession.sessionId, file.id ?? '')}
                        download={file.name}
                      >
                        下载
                      </a>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {(status || error || progress) && !offlineSession && (
              <div className="mt-6 space-y-3">
                <div>
                  <div className="flex justify-between text-xs text-text-muted-light dark:text-text-muted-dark mb-1">
                    <span>{progress?.fileName || status || '等待连接'}</span>
                    <span>{percent}%</span>
                  </div>
                  <div className="h-2 rounded-full bg-black/10 dark:bg-white/10 overflow-hidden">
                    <div className="h-full bg-brand-light dark:bg-brand-dark transition-all" style={{ width: `${percent}%` }} />
                  </div>
                  {progress && (
                    <p className="mt-1 text-xs text-text-muted-light dark:text-text-muted-dark">
                      {formatBytes(progress.sentBytes)} / {formatBytes(progress.totalBytes)}
                    </p>
                  )}
                </div>
                {status && <p className="text-sm text-text-secondary-light dark:text-text-secondary-dark">{status}</p>}
                {error && <p className="text-sm text-red-500">{error}</p>}
              </div>
            )}

            {receivedFiles.length > 0 && !offlineSession && (
              <div className="mt-8">
                <h2 className="text-lg font-bold text-text-primary-light dark:text-white mb-4">已接收文件</h2>
                <div className="space-y-3">
                  {receivedFiles.map((file) => (
                    <div key={file.id} className="rounded-xl border border-[#D9E3F2] dark:border-[#222233] p-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                      <div className="min-w-0">
                        <p className="font-semibold text-text-primary-light dark:text-white truncate">{file.relativePath}</p>
                        <p className="text-sm text-text-muted-light dark:text-text-muted-dark">{formatBytes(file.size)} · {file.contentType}</p>
                      </div>
                      <a className="btn-primary text-center px-4 py-2 text-sm" href={file.url} download={file.name}>
                        下载
                      </a>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="mt-8 border-t border-[#D9E3F2] dark:border-[#222233] pt-6">
              <p className="text-xs text-text-muted-light dark:text-text-muted-dark">
                {offlineSession ? '离线快传无需发送端保持在线，过期前都可以重复下载。' : '接收完成前请保持发送端和接收端页面打开。'}
              </p>
            </div>
          </div>
    </div>
  );

  if (embedded) {
    return content;
  }

  return (
    <div className="min-h-screen bg-bg-light dark:bg-bg-dark transition-colors duration-300">
      <Topbar meta="P2P Transfer Receive" />
      <BackgroundEffects />
      <main className="relative z-10 min-h-screen px-6 pt-28 pb-12">{content}</main>
    </div>
  );
};

const TransferReceive: React.FC = () => {
  return <TransferReceivePanel />;
};

export default TransferReceive;
