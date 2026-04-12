import { useEffect, useState } from 'react';
import { CheckCircle2, Copy, Database, Globe, HardDrive, Layers3, RefreshCw, Server, ShieldCheck, XCircle } from 'lucide-react';
import { motion } from 'motion/react';
import { cn } from '@/src/lib/utils';
import { formatBytes, formatDateTime } from '@/src/lib/format';
import { getAdminFilesystem, type AdminFilesystemResponse } from '@/src/lib/admin-filesystem';

const container = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: {
      staggerChildren: 0.05,
    },
  },
};

const itemVariants = {
  hidden: { y: 14, opacity: 0 },
  show: { y: 0, opacity: 1 },
};

function statusClass(active: boolean) {
  return active
    ? 'bg-green-500/10 text-green-600 dark:text-green-400 border-green-500/20'
    : 'bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/20';
}

function statusIcon(active: boolean) {
  return active ? <CheckCircle2 className="h-3.5 w-3.5" /> : <XCircle className="h-3.5 w-3.5" />;
}

function booleanLabel(active: boolean) {
  return active ? '启用' : '停用';
}

function infoRow(label: string, value: string) {
  return (
    <div className="rounded-lg border border-white/10 bg-white/5 p-4">
      <div className="text-[9px] font-black uppercase tracking-[0.25em] opacity-30">{label}</div>
      <div className="mt-2 break-words text-[11px] font-black uppercase tracking-tight opacity-80">{value || '-'}</div>
    </div>
  );
}

function capabilityRow(label: string, active: boolean) {
  return (
    <div className="flex items-center justify-between rounded-lg border border-white/10 bg-white/5 px-4 py-3">
      <span className="text-[10px] font-black uppercase tracking-[0.18em] opacity-50">{label}</span>
      <span className={cn('inline-flex items-center gap-1.5 rounded-sm border px-2 py-1 text-[9px] font-black uppercase tracking-widest', statusClass(active))}>
        {statusIcon(active)}
        {booleanLabel(active)}
      </span>
    </div>
  );
}

function sectionTitle(title: string, subtitle: string) {
  return (
    <div className="mb-6 flex items-start justify-between gap-4">
      <div>
        <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">{title}</h2>
        <p className="mt-2 text-[9px] font-black uppercase tracking-[0.22em] opacity-25">{subtitle}</p>
      </div>
    </div>
  );
}

export default function AdminFilesystem() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [filesystem, setFilesystem] = useState<AdminFilesystemResponse | null>(null);

  async function loadFilesystem() {
    setError('');
    setNotice('');
    try {
      setFilesystem(await getAdminFilesystem());
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载文件系统信息失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadFilesystem();
  }, []);

  async function copyText(value: string) {
    if (!value) {
      return;
    }
    try {
      await navigator.clipboard.writeText(value);
      setNotice('标识已复制');
      setError('');
    } catch {
      setError('复制失败，请手动复制。');
      setNotice('');
    }
  }

  const overviewCards = filesystem
    ? [
        {
          label: '存储提供者',
          value: filesystem.overview.storageProvider,
          icon: <Server className="h-7 w-7 text-blue-500" />,
          tone: 'blue',
        },
        {
          label: '文件总数',
          value: String(filesystem.overview.totalFiles),
          icon: <HardDrive className="h-7 w-7 text-green-500" />,
          tone: 'green',
        },
        {
          label: '对象总数',
          value: String(filesystem.overview.totalBlobs),
          icon: <Database className="h-7 w-7 text-purple-500" />,
          tone: 'purple',
        },
        {
          label: '实体总数',
          value: String(filesystem.overview.totalEntities),
          icon: <Layers3 className="h-7 w-7 text-amber-500" />,
          tone: 'amber',
        },
      ]
    : [];

  const capabilityEntries = filesystem
    ? [
        ['直传', filesystem.defaultPolicy.capabilities.directUpload],
        ['分片上传', filesystem.defaultPolicy.capabilities.multipartUpload],
        ['签名下载', filesystem.defaultPolicy.capabilities.signedDownloadUrl],
        ['服务端代理下载', filesystem.defaultPolicy.capabilities.serverProxyDownload],
        ['原生缩略图', filesystem.defaultPolicy.capabilities.thumbnailNative],
        ['友好下载名', filesystem.defaultPolicy.capabilities.friendlyDownloadName],
        ['需要 CORS', filesystem.defaultPolicy.capabilities.requiresCors],
        ['内部端点', filesystem.defaultPolicy.capabilities.supportsInternalEndpoint],
      ] as const
    : [];

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col overflow-y-auto p-8 text-gray-900 dark:text-gray-100"
    >
      <div className="mb-10 flex items-center justify-between gap-4">
        <div>
          <h1 className="animate-text-reveal text-4xl font-black tracking-tight text-gray-900 dark:text-white">文件系统</h1>
          <p className="mt-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">存储概览 / 上传模式 / 媒体处理 / 缓存 / WebDAV</p>
        </div>
        <button
          type="button"
          onClick={() => {
            setLoading(true);
            void loadFilesystem();
          }}
          className="flex items-center gap-3 rounded-lg glass-panel px-6 py-3 font-black text-[11px] uppercase tracking-widest transition-all hover:bg-white/40"
        >
          <RefreshCw className={cn('h-4 w-4', loading && 'animate-spin')} />
          刷新状态
        </button>
      </div>

      {error ? <div className="mb-8 rounded-lg border border-red-500/20 bg-red-500/10 px-6 py-4 text-xs font-bold uppercase tracking-widest text-red-600 dark:text-red-400">{error}</div> : null}
      {notice ? <div className="mb-8 rounded-lg border border-blue-500/20 bg-blue-500/10 px-6 py-4 text-xs font-bold uppercase tracking-widest text-blue-600 dark:text-blue-300">{notice}</div> : null}

      {loading && !filesystem ? (
        <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">正在读取文件系统快照...</div>
      ) : filesystem ? (
        <motion.div variants={container} initial="hidden" animate="show" className="space-y-10">
          <motion.section variants={itemVariants} className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
            {overviewCards.map((card) => (
              <div key={card.label} className="glass-panel-no-hover rounded-lg border border-white/10 p-8 shadow-2xl transition-all group hover:border-white/20">
                <div className={cn('mb-6 flex h-14 w-14 items-center justify-center rounded-lg border shadow-[0_0_15px_rgba(59,130,246,0.08)]', card.tone === 'green' && 'bg-green-500/10 border-green-500/20', card.tone === 'purple' && 'bg-purple-500/10 border-purple-500/20', card.tone === 'amber' && 'bg-amber-500/10 border-amber-500/20', card.tone === 'blue' && 'bg-blue-500/10 border-blue-500/20')}>
                  {card.icon}
                </div>
                <h3 className="text-4xl font-black tracking-tight">{card.value}</h3>
                <p className="mt-2 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">{card.label}</p>
              </div>
            ))}
          </motion.section>

          <div className="grid grid-cols-1 gap-8 xl:grid-cols-[1.35fr_0.65fr]">
            <motion.section variants={itemVariants} className="glass-panel-no-hover rounded-lg border border-white/10 p-10 shadow-3xl">
              {sectionTitle('默认存储策略', '当前系统选择的默认分发与对象存储规则')}
              <div className="mb-6 flex flex-wrap items-center gap-3">
                <span className={cn('inline-flex items-center gap-2 rounded-sm border px-2.5 py-1 text-[9px] font-black uppercase tracking-widest', filesystem.defaultPolicy.enabled ? 'bg-green-500/10 text-green-600 dark:text-green-400 border-green-500/20' : 'bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/20')}>
                  {statusIcon(filesystem.defaultPolicy.enabled)}
                  {filesystem.defaultPolicy.enabled ? '默认策略启用' : '默认策略停用'}
                </span>
                <span className="rounded-sm border border-blue-500/20 bg-blue-500/10 px-2.5 py-1 text-[9px] font-black uppercase tracking-widest text-blue-600 dark:text-blue-400">{filesystem.defaultPolicy.defaultPolicy ? 'DEFAULT' : 'NON-DEFAULT'}</span>
                <button
                  type="button"
                  onClick={() => void copyText(filesystem.defaultPolicy.endpoint || filesystem.defaultPolicy.bucketName || filesystem.defaultPolicy.name)}
                  className="inline-flex items-center gap-1.5 rounded-sm border border-white/10 bg-white/5 px-2.5 py-1 text-[9px] font-black uppercase tracking-widest opacity-70 transition-all hover:bg-white/10 hover:opacity-100"
                >
                  <Copy className="h-3.5 w-3.5" />
                  复制标识
                </button>
              </div>

              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                {infoRow('ID', String(filesystem.defaultPolicy.id))}
                {infoRow('名称', filesystem.defaultPolicy.name)}
                {infoRow('类型', filesystem.defaultPolicy.type)}
                {infoRow('访问模式', filesystem.defaultPolicy.privateBucket ? '私有桶' : '公开桶')}
                {infoRow('Bucket', filesystem.defaultPolicy.bucketName || '-')}
                {infoRow('Endpoint', filesystem.defaultPolicy.endpoint || '-')}
                {infoRow('Region', filesystem.defaultPolicy.region || '-')}
                {infoRow('Prefix', filesystem.defaultPolicy.prefix || '-')}
                {infoRow('凭证模式', filesystem.defaultPolicy.credentialMode)}
                {infoRow('策略上限', formatBytes(filesystem.defaultPolicy.maxSizeBytes))}
                {infoRow('创建时间', formatDateTime(filesystem.defaultPolicy.createdAt))}
                {infoRow('更新时间', formatDateTime(filesystem.defaultPolicy.updatedAt))}
              </div>

              <div className="mt-8">
                <div className="mb-4 text-[10px] font-black uppercase tracking-[0.3em] opacity-30">能力矩阵</div>
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                  {capabilityEntries.map(([label, active]) => capabilityRow(label, active))}
                </div>
                <div className="mt-4 rounded-lg border border-white/10 bg-white/5 p-4">
                  <div className="flex items-center justify-between gap-4">
                    <span className="text-[10px] font-black uppercase tracking-[0.18em] opacity-50">单对象最大值</span>
                    <span className="text-[11px] font-black uppercase tracking-tight">{formatBytes(filesystem.defaultPolicy.capabilities.maxObjectSize)}</span>
                  </div>
                </div>
              </div>
            </motion.section>

            <div className="space-y-8">
              <motion.section variants={itemVariants} className="glass-panel-no-hover rounded-lg border border-white/10 p-8 shadow-3xl">
                {sectionTitle('上传模式矩阵', '前端只展示服务端暴露的实际可用上传路径')}
                <div className="space-y-3">
                  {[
                    { label: '代理上传', active: filesystem.upload.proxyUpload, note: '客户端经由后端转发，适合受控或兼容性场景。' },
                    { label: '直传单文件', active: filesystem.upload.directSingleUpload, note: '单文件直接命中存储端，适合小文件快速上传。' },
                    { label: '直传分片', active: filesystem.upload.directMultipartUpload, note: '大文件分片写入，适合稳定传输与断点续传。' },
                  ].map((item) => (
                    <div key={item.label} className="rounded-lg border border-white/10 bg-white/5 p-4">
                      <div className="flex items-center justify-between gap-3">
                        <div>
                          <div className="text-[10px] font-black uppercase tracking-[0.22em] opacity-50">{item.label}</div>
                          <div className="mt-2 text-[9px] font-black uppercase tracking-[0.18em] opacity-25">{item.note}</div>
                        </div>
                        <span className={cn('inline-flex items-center gap-1.5 rounded-sm border px-2 py-1 text-[9px] font-black uppercase tracking-widest', statusClass(item.active))}>
                          {statusIcon(item.active)}
                          {booleanLabel(item.active)}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
                <div className="mt-4 rounded-lg border border-white/10 bg-white/5 p-4">
                  <div className="flex items-center justify-between gap-4">
                    <span className="text-[10px] font-black uppercase tracking-[0.18em] opacity-50">有效最大文件大小</span>
                    <span className="text-[11px] font-black uppercase tracking-tight">{formatBytes(filesystem.upload.effectiveMaxFileSizeBytes)}</span>
                  </div>
                </div>
              </motion.section>

              <motion.section variants={itemVariants} className="glass-panel-no-hover rounded-lg border border-white/10 p-8 shadow-3xl">
                {sectionTitle('媒体处理', '缩略图与元数据采集能力快照')}
                <div className="space-y-3">
                  <div className="flex items-center justify-between rounded-lg border border-white/10 bg-white/5 px-4 py-3">
                    <div>
                      <div className="text-[10px] font-black uppercase tracking-[0.22em] opacity-50">元数据提取</div>
                      <div className="mt-2 text-[9px] font-black uppercase tracking-[0.18em] opacity-25">文件入库后是否自动提取媒体信息。</div>
                    </div>
                    <span className={cn('inline-flex items-center gap-1.5 rounded-sm border px-2 py-1 text-[9px] font-black uppercase tracking-widest', statusClass(filesystem.mediaProcessing.metadataExtractionEnabled))}>
                      {statusIcon(filesystem.mediaProcessing.metadataExtractionEnabled)}
                      {booleanLabel(filesystem.mediaProcessing.metadataExtractionEnabled)}
                    </span>
                  </div>
                  <div className="flex items-center justify-between rounded-lg border border-white/10 bg-white/5 px-4 py-3">
                    <div>
                      <div className="text-[10px] font-black uppercase tracking-[0.22em] opacity-50">原生缩略图</div>
                      <div className="mt-2 text-[9px] font-black uppercase tracking-[0.18em] opacity-25">是否直接由存储或后端生成缩略图结果。</div>
                    </div>
                    <span className={cn('inline-flex items-center gap-1.5 rounded-sm border px-2 py-1 text-[9px] font-black uppercase tracking-widest', statusClass(filesystem.mediaProcessing.nativeThumbnailSupport))}>
                      {statusIcon(filesystem.mediaProcessing.nativeThumbnailSupport)}
                      {booleanLabel(filesystem.mediaProcessing.nativeThumbnailSupport)}
                    </span>
                  </div>
                </div>
              </motion.section>

              <motion.section variants={itemVariants} className="glass-panel-no-hover rounded-lg border border-white/10 p-8 shadow-3xl">
                {sectionTitle('缓存状态', '文件列表与目录版本的缓存后端')}
                <div className="grid grid-cols-1 gap-4">
                  {infoRow('缓存后端', filesystem.cache.backend)}
                  {infoRow('文件列表 TTL', `${filesystem.cache.filesListTtlSeconds} 秒`)}
                  {infoRow('目录版本 TTL', `${filesystem.cache.directoryVersionTtlSeconds} 秒`)}
                </div>
              </motion.section>

              <motion.section variants={itemVariants} className="glass-panel-no-hover rounded-lg border border-white/10 p-8 shadow-3xl">
                {sectionTitle('WebDAV 状态', '只读挂载与外部客户端访问能力')}
                <div className="flex items-start justify-between gap-4 rounded-lg border border-white/10 bg-white/5 p-4">
                  <div>
                    <div className="text-[10px] font-black uppercase tracking-[0.22em] opacity-50">WebDAV 服务</div>
                    <div className="mt-2 text-[9px] font-black uppercase tracking-[0.18em] opacity-25">管理台仅展示后端当前是否暴露 WebDAV。</div>
                  </div>
                  <span className={cn('inline-flex items-center gap-1.5 rounded-sm border px-2 py-1 text-[9px] font-black uppercase tracking-widest', statusClass(filesystem.webdav.enabled))}>
                    {filesystem.webdav.enabled ? <Globe className="h-3.5 w-3.5" /> : <XCircle className="h-3.5 w-3.5" />}
                    {booleanLabel(filesystem.webdav.enabled)}
                  </span>
                </div>
                <div className="mt-4 rounded-lg border border-white/10 bg-white/5 p-4">
                  <div className="flex items-center gap-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-50">
                    <ShieldCheck className="h-4 w-4 text-blue-500" />
                    说明
                  </div>
                  <p className="mt-3 text-[11px] font-bold leading-6 opacity-60">
                    当前页面仅展示文件系统快照，不提供就地编辑。所有可变配置仍由系统设置与存储策略页面管理。
                  </p>
                </div>
              </motion.section>
            </div>
          </div>
        </motion.div>
      ) : null}
    </motion.div>
  );
}
