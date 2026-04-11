import { useEffect, useMemo, useState } from 'react';
import { cn } from '@/src/lib/utils';
import { Clock3, Copy, Download, ExternalLink, FolderDown, Link as LinkIcon, RefreshCw, Send, Upload, ChevronRight } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { motion, AnimatePresence } from 'motion/react';
import { formatBytes, formatDateTime } from '@/src/lib/format';
import { getSession } from '@/src/lib/session';
import {
  buildOfflineTransferDownloadUrl,
  createTransferSession,
  importOfflineTransferFile,
  joinTransferSession,
  listMyOfflineTransferSessions,
  lookupTransferSession,
  sanitizePickupCode,
  uploadOfflineTransferFile,
  type LookupTransferSessionResponse,
  type TransferFileItem,
  type TransferMode,
  type TransferSessionResponse,
} from '@/src/lib/transfer';

type TransferTab = 'send' | 'receive' | 'history';

function getTransferShareUrl(pickupCode: string) {
  const url = new URL('/transfer', window.location.origin);
  url.searchParams.set('code', pickupCode);
  return url.toString();
}

function findSessionFile(sourceFile: File, sessionFiles: TransferFileItem[]) {
  return sessionFiles.find(
    (item) =>
      item.name === sourceFile.name &&
      item.relativePath.replaceAll('\\', '/') === (('webkitRelativePath' in sourceFile && sourceFile.webkitRelativePath) || sourceFile.name).replaceAll('\\', '/') &&
      item.size === sourceFile.size,
  );
}

const container = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: {
      staggerChildren: 0.05
    }
  }
};

const itemVariants = {
  hidden: { y: 10, opacity: 0 },
  show: { y: 0, opacity: 1 }
};

export default function Transfer() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [activeTab, setActiveTab] = useState<TransferTab>('send');
  const [sendMode, setSendMode] = useState<TransferMode>('OFFLINE');
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [sendLoading, setSendLoading] = useState(false);
  const [sendError, setSendError] = useState('');
  const [sendMessage, setSendMessage] = useState('');
  const [createdSession, setCreatedSession] = useState<TransferSessionResponse | null>(null);
  const [uploadedCount, setUploadedCount] = useState(0);
  const [receiveCode, setReceiveCode] = useState(() => sanitizePickupCode(searchParams.get('code') ?? ''));
  const [lookupLoading, setLookupLoading] = useState(false);
  const [receiveError, setReceiveError] = useState('');
  const [receiveMessage, setReceiveMessage] = useState('');
  const [lookupResult, setLookupResult] = useState<LookupTransferSessionResponse | null>(null);
  const [joinedSession, setJoinedSession] = useState<TransferSessionResponse | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const [historyMessage, setHistoryMessage] = useState('');
  const [historySessions, setHistorySessions] = useState<TransferSessionResponse[]>([]);

  const loggedIn = Boolean(getSession());

  useEffect(() => {
    const codeFromQuery = sanitizePickupCode(searchParams.get('code') ?? '');
    if (codeFromQuery && codeFromQuery !== receiveCode) {
      setReceiveCode(codeFromQuery);
    }
  }, [searchParams]);

  useEffect(() => {
    if (activeTab !== 'history' || !loggedIn) {
      return;
    }
    void loadHistory();
  }, [activeTab, loggedIn]);

  useEffect(() => {
    const codeFromQuery = sanitizePickupCode(searchParams.get('code') ?? '');
    if (!codeFromQuery) {
      return;
    }
    setActiveTab('receive');
    void handleLookup(codeFromQuery);
  }, []);

  const transferShareUrl = useMemo(
    () => (createdSession ? getTransferShareUrl(createdSession.pickupCode) : ''),
    [createdSession],
  );

  async function loadHistory() {
    setHistoryLoading(true);
    setHistoryError('');
    try {
      setHistorySessions(await listMyOfflineTransferSessions());
    } catch (err) {
      setHistoryError(err instanceof Error ? err.message : '加载记录失败');
    } finally {
      setHistoryLoading(false);
    }
  }

  async function handleCreateSession() {
    if (selectedFiles.length === 0) {
      setSendError('请先选择至少一个文件。');
      return;
    }

    setSendLoading(true);
    setSendError('');
    setSendMessage('');
    setCreatedSession(null);
    setUploadedCount(0);

    try {
      const session = await createTransferSession(selectedFiles, sendMode);
      setCreatedSession(session);

      if (session.mode === 'ONLINE') {
        setSendMessage('在线快传会话已创建。目前暂未接入浏览器直连发送。');
        return;
      }

      let completed = 0;
      for (const file of selectedFiles) {
        const matched = findSessionFile(file, session.files);
        if (!matched?.id) {
          throw new Error(`无法验证文件：${file.name}`);
        }
        await uploadOfflineTransferFile(session.sessionId, matched.id, file);
        completed += 1;
        setUploadedCount(completed);
      }

      setSendMessage('同步完成。');
    } catch (err) {
      setSendError(err instanceof Error ? err.message : '创建失败');
    } finally {
      setSendLoading(false);
    }
  }

  async function handleLookup(code = receiveCode) {
    const normalized = sanitizePickupCode(code);
    if (normalized.length !== 6) {
      setReceiveError('请输入 6 位取件码。');
      return;
    }

    setLookupLoading(true);
    setReceiveError('');
    setReceiveMessage('');
    setLookupResult(null);
    setJoinedSession(null);

    try {
      const result = await lookupTransferSession(normalized);
      setReceiveCode(result.pickupCode);
      setLookupResult(result);
      setSearchParams({ code: result.pickupCode });
    } catch (err) {
      setReceiveError(err instanceof Error ? err.message : '查找失败');
    } finally {
      setLookupLoading(false);
    }
  }

  async function handleJoinSession() {
    if (!lookupResult) {
      return;
    }

    setLookupLoading(true);
    setReceiveError('');
    setReceiveMessage('');
    try {
      const session = await joinTransferSession(lookupResult.sessionId);
      setJoinedSession(session);
      if (session.mode === 'ONLINE') {
        setReceiveMessage('在线会话已打开，等待发送方响应。');
      } else {
        setReceiveMessage('对象已就绪，可执行下载或导入。');
      }
    } catch (err) {
      setReceiveError(err instanceof Error ? err.message : '打开失败');
    } finally {
      setLookupLoading(false);
    }
  }

  async function handleImport(sessionId: string, fileId: string) {
    const targetPath = window.prompt('导入到路径', '/') || '/';
    try {
      const saved = await importOfflineTransferFile(sessionId, fileId, targetPath);
      setReceiveMessage(`${saved.filename} -> ${saved.path}`);
    } catch (err) {
      setReceiveError(err instanceof Error ? err.message : '导入失败');
    }
  }

  async function handleHistoryImport(sessionId: string, fileId: string) {
    const targetPath = window.prompt('导入到路径', '/') || '/';
    try {
      const saved = await importOfflineTransferFile(sessionId, fileId, targetPath);
      setHistoryMessage(`${saved.filename} -> ${saved.path}`);
    } catch (err) {
      setHistoryError(err instanceof Error ? err.message : '导入失败');
    }
  }

  const uploadProgressText =
    createdSession?.mode === 'OFFLINE' && selectedFiles.length > 0
      ? `${uploadedCount} / ${selectedFiles.length}`
      : '-';

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col p-8 text-gray-900 dark:text-gray-100 overflow-y-auto"
    >
      <div className="mb-10">
        <h1 className="text-4xl font-black tracking-tight animate-text-reveal">即时快传</h1>
        <p className="mt-3 text-sm font-black uppercase tracking-[0.2em] opacity-70">即时数据中转 / 安全取件通道</p>
      </div>

      <div className="mb-10 flex gap-2 p-1.5 rounded-lg glass-panel-no-hover w-fit shadow-2xl border border-white/10">
        {([
          ['send', '发送'],
          ['receive', '接收'],
          ['history', '记录'],
        ] as const).map(([tab, label]) => (
          <button
            key={tab}
            type="button"
            onClick={() => setActiveTab(tab)}
            className={cn(
               "rounded-md px-8 py-3 text-[10px] font-black uppercase tracking-widest transition-all duration-300",
               activeTab === tab 
                ? "bg-blue-600 text-white shadow-xl scale-[1.02]" 
                : "opacity-40 hover:opacity-100 hover:bg-white/10"
            )}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="flex-1 min-h-0">
        <AnimatePresence mode="wait">
          {activeTab === 'send' && (
            <motion.div 
               key="send"
               initial={{ y: 20, opacity: 0 }}
               animate={{ y: 0, opacity: 1 }}
               exit={{ y: -20, opacity: 0 }}
               className="grid gap-8 lg:grid-cols-[1.2fr_0.8fr]"
            >
              <section className="glass-panel-no-hover rounded-lg p-10 shadow-3xl border border-white/10">
                <div className="mb-8">
                  <h2 className="text-sm font-black uppercase tracking-[0.3em] opacity-70">传输配置</h2>
                </div>

                <div className="mb-10 flex gap-4">
                  {([
                    ['OFFLINE', '离线快传'],
                    ['ONLINE', '在线快传'],
                  ] as const).map(([mode, label]) => (
                    <button
                      key={mode}
                      type="button"
                      onClick={() => setSendMode(mode)}
                      className={cn(
                        "rounded-lg px-6 py-2.5 text-[10px] font-black uppercase tracking-widest transition-all border",
                        sendMode === mode 
                          ? "bg-blue-600/10 border-blue-500/40 text-blue-500 shadow-inner" 
                          : "border-white/10 opacity-30 hover:opacity-100 hover:bg-white/5"
                      )}
                    >
                      {label}
                    </button>
                  ))}
                </div>

                <label className="mb-10 flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed border-white/10 bg-white/5 px-10 py-20 text-center transition-all hover:border-blue-500/40 hover:bg-blue-500/5 group border-white/10">
                  <Upload className="mb-6 h-12 w-12 text-blue-500 opacity-40 group-hover:opacity-100 group-hover:scale-110 transition-all" />
                  <div className="text-[11px] font-black uppercase tracking-[0.2em]">选择要发送的文件</div>
                  <div className="mt-3 text-xs font-bold opacity-80 dark:opacity-90 uppercase tracking-widest">支持多文件，大小受系统限制</div>
                  <input
                    type="file"
                    multiple
                    className="hidden"
                    onChange={(event) => {
                      setSelectedFiles(Array.from(event.target.files ?? []));
                    }}
                  />
                </label>

                {selectedFiles.length > 0 && (
                  <div className="mb-10 rounded-lg bg-black/20 p-6 border border-white/10">
                    <div className="mb-4 text-xs font-black uppercase tracking-[0.3em] opacity-70">已选择文件（{selectedFiles.length}）</div>
                    <div className="space-y-3 max-h-64 overflow-y-auto pr-2 custom-scrollbar">
                      {selectedFiles.map((file) => (
                        <div key={`${file.name}-${file.size}`} className="flex items-center justify-between gap-4 p-3 rounded bg-white/5 border border-white/5">
                          <span className="truncate text-[11px] font-black uppercase tracking-tight">{file.name}</span>
                          <span className="shrink-0 text-sm font-bold opacity-80 dark:opacity-90">{formatBytes(file.size)}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {sendError && <div className="mb-8 rounded-lg bg-red-500/10 border border-red-500/20 px-6 py-4 text-[10px] text-red-600 font-black uppercase tracking-widest backdrop-blur-md">{sendError}</div>}
                {sendMessage && <div className="mb-8 rounded-lg bg-green-500/10 border border-green-500/20 px-6 py-4 text-[10px] text-green-600 font-black uppercase tracking-widest backdrop-blur-md">{sendMessage}</div>}

                <button
                  type="button"
                  onClick={() => void handleCreateSession()}
                  disabled={sendLoading || selectedFiles.length === 0}
                  className="w-full inline-flex items-center justify-center gap-4 rounded-lg bg-blue-600 px-8 py-5 text-[11px] font-black uppercase tracking-[0.3em] text-white shadow-2xl hover:bg-blue-500 hover:scale-[1.01] transition-all disabled:opacity-30 disabled:hover:scale-100"
                >
                  {sendLoading ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
                  {sendLoading ? '处理中...' : '创建会话'}
                </button>
              </section>

              <aside className="glass-panel-no-hover rounded-lg p-10 shadow-3xl border border-white/10 h-fit">
                <div className="mb-8">
                  <h2 className="text-sm font-black uppercase tracking-[0.3em] opacity-70">会话信息</h2>
                </div>
                {createdSession ? (
                  <div className="space-y-10">
                    <div className="rounded-lg bg-blue-600/5 dark:bg-blue-600/10 border border-blue-500/20 p-10 text-center">
                      <div className="text-[9px] font-black text-blue-500 uppercase tracking-[0.4em] mb-4">取件码</div>
                      <div className="text-5xl font-black tracking-[0.3em] text-blue-500 ml-[0.3em] drop-shadow-xl">{createdSession.pickupCode}</div>
                    </div>
                    
                    <div className="space-y-5 p-6 rounded-lg bg-white/5 border border-white/10 text-[10px] font-black uppercase tracking-widest">
                      <div className="flex justify-between border-b border-white/5 pb-3">
                        <span className="opacity-80 dark:opacity-90">模式</span>
                        <span>{createdSession.mode}</span>
                      </div>
                      <div className="flex justify-between border-b border-white/5 pb-3">
                        <span className="opacity-80 dark:opacity-90">过期</span>
                        <span className="text-amber-500">{formatDateTime(createdSession.expiresAt).split(' ')[0]}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="opacity-80 dark:opacity-90">进度</span>
                        <span className="text-blue-500">{uploadProgressText}</span>
                      </div>
                    </div>

                    <div className="grid grid-cols-1 gap-3">
                      <button
                        type="button"
                        onClick={() => { navigator.clipboard.writeText(createdSession.pickupCode); window.alert('取件码已复制'); }}
                        className="flex items-center justify-center gap-3 rounded-lg glass-panel border-white/10 p-4 text-[9px] font-black uppercase tracking-[0.2em] hover:bg-white/40 transition-all"
                      >
                        <Copy className="h-4 w-4" /> 复制取件码
                      </button>
                      <button
                        type="button"
                        onClick={() => { navigator.clipboard.writeText(transferShareUrl); window.alert('链接已复制'); }}
                        className="flex items-center justify-center gap-3 rounded-lg glass-panel border-white/10 p-4 text-[9px] font-black uppercase tracking-[0.2em] hover:bg-white/40 transition-all"
                      >
                        <LinkIcon className="h-4 w-4" /> 复制链接
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="py-20 text-center">
                    <div className="mb-6 inline-flex p-6 rounded-lg bg-white/5 opacity-10">
                      <Copy className="h-10 w-10" />
                    </div>
                    <p className="text-sm font-black uppercase tracking-widest opacity-70">等待会话创建<br/>创建后可查看</p>
                  </div>
                )}
              </aside>
            </motion.div>
          )}

          {activeTab === 'receive' && (
            <motion.div 
               key="receive"
               initial={{ y: 20, opacity: 0 }}
               animate={{ y: 0, opacity: 1 }}
               exit={{ y: -20, opacity: 0 }}
               className="grid gap-8 lg:grid-cols-[0.8fr_1.2fr]"
            >
              <section className="glass-panel-no-hover rounded-lg p-10 shadow-3xl border border-white/10 h-fit">
                <div className="mb-8">
                  <h2 className="text-sm font-black uppercase tracking-[0.3em] opacity-70">接收会话</h2>
                </div>

                <div className="relative mb-8">
                  <input
                    value={receiveCode}
                    onChange={(event) => setReceiveCode(sanitizePickupCode(event.target.value))}
                    onKeyDown={(event) => { if (event.key === 'Enter') void handleLookup(); }}
                    placeholder="000000"
                    className="w-full rounded-lg glass-panel bg-black/40 p-8 text-center text-5xl font-black tracking-[0.5em] outline-none border border-white/10 focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10 placeholder:opacity-10 transition-all duration-500 text-blue-500"
                  />
                </div>
                
                <button
                  type="button"
                  onClick={() => void handleLookup()}
                  disabled={lookupLoading}
                  className="w-full rounded-lg bg-blue-600 p-5 text-[11px] font-black uppercase tracking-[0.3em] text-white shadow-2xl hover:bg-blue-500 transition-all disabled:opacity-30"
                >
                  {lookupLoading ? <RefreshCw className="h-4 w-4 animate-spin inline mr-3" /> : null}
                  查询取件码
                </button>

                {receiveError && <div className="mt-8 rounded-lg bg-red-500/10 border border-red-500/20 px-6 py-4 text-[10px] text-red-600 font-black uppercase tracking-widest backdrop-blur-md">{receiveError}</div>}
                {receiveMessage && <div className="mt-8 rounded-lg bg-green-500/10 border border-green-500/20 px-6 py-4 text-[10px] text-green-600 font-black uppercase tracking-widest backdrop-blur-md">{receiveMessage}</div>}

                {lookupResult && (
                  <div className="mt-10 p-8 rounded-lg bg-blue-600/5 border border-blue-500/20">
                    <div className="flex items-center gap-5 mb-6">
                      <div className="p-4 rounded-lg bg-blue-600 text-white font-black text-2xl tracking-[0.2em]">
                        {lookupResult.pickupCode}
                      </div>
                      <div>
                        <div className="text-[10px] font-black uppercase tracking-widest text-blue-500">已找到会话</div>
                        <div className="text-xs font-bold opacity-80 dark:opacity-90 uppercase tracking-widest">{lookupResult.mode} MODE</div>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => void handleJoinSession()}
                      disabled={lookupLoading}
                      className="w-full rounded-lg glass-panel border-white/10 p-4 text-[10px] font-black uppercase tracking-widest hover:bg-blue-600 hover:text-white transition-all"
                    >
                      加入会话
                    </button>
                  </div>
                )}
              </section>

              <section className="glass-panel-no-hover rounded-lg p-10 shadow-3xl border border-white/10">
                <div className="mb-8">
                  <h2 className="text-sm font-black uppercase tracking-[0.3em] opacity-70">文件列表</h2>
                </div>
                {joinedSession ? (
                  joinedSession.mode === 'OFFLINE' ? (
                    <div className="space-y-4">
                      {joinedSession.files.map((file) => (
                        <div key={`${file.id}-${file.size}`} className="p-6 rounded-lg bg-white/5 border border-white/10 hover:bg-white/10 transition-all group">
                          <div className="flex flex-wrap items-center justify-between gap-6">
                            <div className="min-w-0 flex-1">
                              <div className="truncate text-lg font-black uppercase tracking-tight group-hover:text-blue-500 transition-colors">{file.name}</div>
                              <div className="mt-1 truncate text-xs font-bold opacity-80 dark:opacity-90 uppercase tracking-widest">{file.relativePath}</div>
                              <div className="mt-2 text-[10px] font-black text-blue-500 flex items-center gap-1">
                                <span className="opacity-40 font-black">大小：</span>{formatBytes(file.size)}
                              </div>
                            </div>
                            <div className="flex gap-2">
                              {file.id && (
                                <a
                                  href={buildOfflineTransferDownloadUrl(joinedSession.sessionId, file.id)}
                                  className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-5 py-3 text-[10px] font-black uppercase tracking-widest text-white hover:bg-blue-500 shadow-xl transition-all"
                                >
                                  <Download className="h-4 w-4" /> 下载
                                </a>
                              )}
                              {loggedIn && file.id && (
                                <button
                                  type="button"
                                  onClick={() => void handleImport(joinedSession.sessionId, file.id!)}
                                  className="inline-flex items-center gap-2 rounded-lg glass-panel border-white/10 px-5 py-3 text-[10px] font-black uppercase tracking-widest hover:bg-white/40 transition-all"
                                >
                                  <FolderDown className="h-4 w-4" /> 导入网盘
                                </button>
                              )}
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="py-32 text-center rounded-lg bg-amber-500/5 border border-amber-500/10 px-10">
                      <RefreshCw className="h-12 w-12 text-amber-500 mx-auto mb-6 opacity-30 animate-spin-slow" />
                      <h3 className="text-[11px] font-black uppercase tracking-[0.3em] text-amber-500">等待发送方在线</h3>
                      <p className="mt-4 text-[10px] font-bold opacity-40 uppercase tracking-widest leading-relaxed">在线快传需要发送方保持在线</p>
                    </div>
                  )
                ) : (
                  <div className="py-40 text-center">
                    <div className="mb-8 inline-flex p-6 rounded-lg bg-white/5 opacity-10">
                      <FolderDown className="h-10 w-10" />
                    </div>
                    <p className="text-sm font-black uppercase tracking-widest opacity-70">暂无会话<br/>输入取件码后查询</p>
                  </div>
                )}
              </section>
            </motion.div>
          )}

          {activeTab === 'history' && (
            <motion.div 
               key="history"
               initial={{ y: 20, opacity: 0 }}
               animate={{ y: 0, opacity: 1 }}
               exit={{ y: -20, opacity: 0 }}
               className="glass-panel-no-hover rounded-lg p-10 shadow-3xl border border-white/10"
            >
              <div className="mb-10 flex items-center justify-between">
                <div>
                  <h2 className="text-sm font-black uppercase tracking-[0.3em] opacity-70">离线快传记录</h2>
                </div>
                <button
                  type="button"
                  onClick={() => void loadHistory()}
                  disabled={!loggedIn || historyLoading}
                  className="flex items-center gap-3 rounded-lg glass-panel border-white/10 px-6 py-3 text-[10px] font-black uppercase tracking-widest hover:bg-white/40 transition-all border-white/10 disabled:opacity-20"
                >
                  <RefreshCw className={cn("h-4 w-4", historyLoading && "animate-spin")} />
                  刷新记录
                </button>
              </div>

              {!loggedIn ? (
                <div className="py-32 text-center">
                  <p className="text-sm font-black uppercase tracking-[0.3em] opacity-70">登录后可查看记录</p>
                </div>
              ) : historyLoading && historySessions.length === 0 ? (
                <div className="py-32 text-center text-sm font-black uppercase tracking-widest opacity-70">加载中...</div>
              ) : historySessions.length === 0 ? (
                <div className="py-32 text-center text-sm font-black uppercase tracking-widest opacity-70">暂无记录</div>
              ) : (
                <motion.div 
                  variants={container}
                  initial="hidden"
                  animate="show"
                  className="grid gap-8"
                >
                  {historyError && <div className="rounded-lg bg-red-500/10 border border-red-500/20 px-6 py-4 text-xs text-red-600 font-bold">{historyError}</div>}
                  {historyMessage && <div className="rounded-lg bg-green-500/10 border border-green-500/30 px-6 py-4 text-xs text-green-600 font-bold">{historyMessage}</div>}
                  {historySessions.map((session) => (
                    <motion.div key={session.sessionId} variants={itemVariants} className="p-8 rounded-lg bg-white/5 border border-white/10 hover:border-blue-500/30 transition-all group">
                      <div className="mb-8 flex flex-wrap items-center justify-between gap-6">
                        <div className="flex items-center gap-6">
                          <div className="p-5 rounded-lg bg-blue-600 text-white font-black text-3xl tracking-[0.3em] shadow-xl">
                            {session.pickupCode}
                          </div>
                          <div>
                            <div className="flex items-center gap-2 text-[10px] font-black text-amber-500 mb-2 uppercase tracking-widest">
                              <Clock3 className="h-4 w-4" />
                              过期时间：{formatDateTime(session.expiresAt).split(' ')[0]}
                            </div>
                            <div className="text-xs font-bold opacity-80 dark:opacity-90 uppercase tracking-[0.2em]">文件数：{session.files.length}</div>
                          </div>
                        </div>
                        <div className="flex gap-2">
                          <button
                            type="button"
                            onClick={() => { navigator.clipboard.writeText(session.pickupCode); window.alert('取件码已复制'); }}
                            className="p-3.5 rounded-lg glass-panel hover:bg-blue-600 hover:text-white transition-all border border-white/10 shadow-sm"
                            title="复制取件码"
                          >
                            <Copy className="h-4 w-4" />
                          </button>
                          <button
                            type="button"
                            onClick={() => { navigator.clipboard.writeText(getTransferShareUrl(session.pickupCode)); window.alert('链接已复制'); }}
                            className="p-3.5 rounded-lg glass-panel hover:bg-blue-600 hover:text-white transition-all border border-white/10 shadow-sm"
                            title="复制链接"
                          >
                            <LinkIcon className="h-4 w-4" />
                          </button>
                        </div>
                      </div>
                      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                        {session.files.map((file) => (
                          <div key={`${file.id}-${file.size}`} className="p-5 rounded-lg bg-black/40 border border-white/5 group/file">
                            <div className="truncate text-[11px] font-black uppercase tracking-tight mb-4 group-hover/file:text-blue-500 transition-colors">{file.name}</div>
                            <div className="flex items-center justify-between gap-3 border-t border-white/5 pt-3">
                              <span className="text-xs font-bold opacity-80 dark:opacity-90 uppercase">{formatBytes(file.size)}</span>
                              <div className="flex gap-2">
                                {file.id && (
                                  <a
                                    href={buildOfflineTransferDownloadUrl(session.sessionId, file.id)}
                                    className="p-2 rounded-lg hover:bg-blue-600/20 text-blue-500 transition-colors"
                                    title="下载"
                                  >
                                    <Download className="h-4 w-4" />
                                  </a>
                                )}
                                {file.id && (
                                  <button
                                    type="button"
                                    onClick={() => void handleHistoryImport(session.sessionId, file.id!)}
                                    className="p-2 rounded-lg hover:bg-white/10 text-gray-700 dark:text-gray-100 opacity-80 hover:opacity-100 transition-all"
                                    title="导入网盘"
                                  >
                                    <FolderDown className="h-4 w-4" />
                                  </button>
                                )}
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </motion.div>
                  ))}
                </motion.div>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </motion.div>
  );
}
