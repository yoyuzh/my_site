import { useEffect, useState, type ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { Copy, Database, RefreshCw, RotateCcw, Save, Server, Settings, Shield, Clock3, Layers3 } from 'lucide-react';
import { motion } from 'motion/react';
import { cn } from '@/src/lib/utils';
import { AdminAlertDialog } from '@/src/components/admin/AdminAlertDialog';
import { AdminInput } from '@/src/components/admin/AdminInput';
import {
  getAdminSettings,
  rotateAdminRegistrationInviteCode,
  updateAdminOfflineTransferStorageLimit,
  updateAdminRegistrationInviteCode,
  type AdminSettings,
} from '@/src/operations-admin/api/settings/settings';
import { formatBytes } from '@/src/lib/format';

const container = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: {
      staggerChildren: 0.06,
    },
  },
};

const itemVariants = {
  hidden: { y: 18, opacity: 0 },
  show: { y: 0, opacity: 1 },
};

function formatDurationSeconds(seconds: number) {
  if (!Number.isFinite(seconds) || seconds < 0) {
    return '-';
  }

  if (seconds < 60) {
    return `${seconds} 秒`;
  }

  const minutes = seconds / 60;
  if (minutes < 60) {
    return `${minutes % 1 === 0 ? minutes : minutes.toFixed(1)} 分钟`;
  }

  const hours = minutes / 60;
  if (hours < 24) {
    return `${hours % 1 === 0 ? hours : hours.toFixed(1)} 小时`;
  }

  const days = hours / 24;
  return `${days % 1 === 0 ? days : days.toFixed(1)} 天`;
}

function formatDurationMs(milliseconds: number) {
  if (!Number.isFinite(milliseconds) || milliseconds < 0) {
    return '-';
  }

  if (milliseconds < 1000) {
    return `${milliseconds} 毫秒`;
  }

  return formatDurationSeconds(milliseconds / 1000);
}

function statusPill(value: boolean, trueLabel = '已启用', falseLabel = '未启用') {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2 py-0.5 text-[9px] font-black uppercase tracking-[0.2em]',
        value
          ? 'border-green-500/20 bg-green-500/10 text-green-600 dark:text-green-400'
          : 'border-white/10 bg-white/5 text-gray-500 dark:text-gray-300',
      )}
    >
      {value ? trueLabel : falseLabel}
    </span>
  );
}

function SnapshotRow({
  label,
  value,
  valueClassName,
}: {
  label: string;
  value: ReactNode;
  valueClassName?: string;
}) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-white/10 py-3 last:border-0 last:pb-0">
      <span className="text-[9px] font-black uppercase tracking-[0.2em] opacity-30">{label}</span>
      <span className={cn('text-right text-[11px] font-bold leading-5', valueClassName)}>{value}</span>
    </div>
  );
}

function SnapshotCard({
  title,
  badge,
  icon,
  children,
}: {
  title: string;
  badge: string;
  icon: ReactNode;
  children: ReactNode;
}) {
  return (
    <motion.section
      variants={itemVariants}
      className="glass-panel-no-hover rounded-2xl border border-white/10 p-6 shadow-3xl"
    >
      <div className="mb-6 flex items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-white/10 bg-white/5 text-blue-500 shadow-inner">
            {icon}
          </div>
          <div>
            <h3 className="text-[13px] font-black uppercase tracking-[0.18em]">{title}</h3>
            <p className="mt-1 text-[9px] font-black uppercase tracking-[0.3em] opacity-30">{badge}</p>
          </div>
        </div>
      </div>
      <div>{children}</div>
    </motion.section>
  );
}

type InviteCodeFormValues = {
  inviteCode: string;
};

type OfflineTransferLimitFormValues = {
  offlineTransferStorageLimitBytes: number;
};

export default function AdminSettingsPage() {
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [savingInviteCode, setSavingInviteCode] = useState(false);
  const [rotatingInviteCode, setRotatingInviteCode] = useState(false);
  const [rotateInviteDialogOpen, setRotateInviteDialogOpen] = useState(false);
  const [savingTransferLimit, setSavingTransferLimit] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [settings, setSettings] = useState<AdminSettings | null>(null);
  const inviteCodeForm = useForm<InviteCodeFormValues>({
    defaultValues: {
      inviteCode: '',
    },
    mode: 'onSubmit',
    reValidateMode: 'onChange',
  });
  const offlineTransferLimitForm = useForm<OfflineTransferLimitFormValues>({
    defaultValues: {
      offlineTransferStorageLimitBytes: 1,
    },
    mode: 'onSubmit',
    reValidateMode: 'onChange',
  });

  async function loadSettings(isRefresh = false) {
    if (isRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setError('');
    try {
      const nextSettings = await getAdminSettings();
      setSettings(nextSettings);
      inviteCodeForm.reset({
        inviteCode: nextSettings.registration.currentInviteCode,
      });
      offlineTransferLimitForm.reset({
        offlineTransferStorageLimitBytes: nextSettings.transfer.offlineTransferStorageLimitBytes,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载系统设置失败');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useEffect(() => {
    void loadSettings();
  }, []);

  async function handleSaveInviteCode(values: InviteCodeFormValues) {
    const nextInviteCode = values.inviteCode.trim();
    if (!nextInviteCode) {
      setError('邀请码不能为空');
      return;
    }

    setSavingInviteCode(true);
    setError('');
    setNotice('');
    try {
      await updateAdminRegistrationInviteCode(nextInviteCode);
      await loadSettings(true);
      setNotice('邀请码已更新');
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新邀请码失败');
    } finally {
      setSavingInviteCode(false);
    }
  }

  async function handleRotateInviteCode() {
    setRotatingInviteCode(true);
    setError('');
    setNotice('');
    try {
      await rotateAdminRegistrationInviteCode();
      await loadSettings(true);
      setNotice('邀请码已轮换');
    } catch (err) {
      setError(err instanceof Error ? err.message : '轮换邀请码失败');
    } finally {
      setRotatingInviteCode(false);
    }
  }

  async function handleConfirmRotateInviteCode() {
    setRotateInviteDialogOpen(false);
    await handleRotateInviteCode();
  }

  async function handleSaveTransferLimit(values: OfflineTransferLimitFormValues) {
    const nextLimit = values.offlineTransferStorageLimitBytes;
    if (!Number.isInteger(nextLimit) || nextLimit <= 0) {
      setError('离线快传存储上限必须是大于 0 的整数');
      return;
    }

    setSavingTransferLimit(true);
    setError('');
    setNotice('');
    try {
      await updateAdminOfflineTransferStorageLimit(nextLimit);
      await loadSettings(true);
      setNotice('离线快传存储上限已更新');
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新离线快传存储上限失败');
    } finally {
      setSavingTransferLimit(false);
    }
  }

  const isBusy = loading || refreshing;
  const watchedOfflineLimit = offlineTransferLimitForm.watch('offlineTransferStorageLimitBytes');
  const offlineLimitPreview = Number.isFinite(watchedOfflineLimit) ? watchedOfflineLimit : 0;

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col overflow-y-auto p-8 text-gray-900 dark:text-gray-100"
    >
      <div className="mb-10 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="animate-text-reveal text-4xl font-black tracking-tight text-gray-900 dark:text-white">系统设置</h1>
          <p className="mt-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">
            可编辑设置 / 只读快照 / 后端能力边界
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            void loadSettings(true);
          }}
          disabled={isBusy}
          className="flex items-center gap-3 rounded-lg glass-panel px-6 py-3 text-[11px] font-black uppercase tracking-widest transition-all hover:bg-white/40 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <RefreshCw className={cn('h-4 w-4', refreshing && 'animate-spin')} />
          刷新设置
        </button>
      </div>

      {error ? (
        <div className="mb-8 rounded-lg border border-red-500/20 bg-red-500/10 px-6 py-4 text-xs font-bold uppercase tracking-widest text-red-600 backdrop-blur-md dark:text-red-400">
          {error}
        </div>
      ) : null}

      {notice ? (
        <div className="mb-8 rounded-lg border border-blue-500/20 bg-blue-500/10 px-6 py-4 text-xs font-bold uppercase tracking-widest text-blue-600 backdrop-blur-md dark:text-blue-300">
          {notice}
        </div>
      ) : null}

      {loading && !settings ? (
        <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">
          正在读取系统设置快照...
        </div>
      ) : settings ? (
        <motion.div variants={container} initial="hidden" animate="show" className="space-y-10">
          <section>
            <div className="mb-5 flex items-center justify-between gap-4">
              <div>
                <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">可编辑设置</h2>
                <p className="mt-2 text-[11px] font-bold opacity-40">
                  这里是当前后端明确支持写入的设置项，仅包含邀请码与离线快传容量上限。
                </p>
              </div>
              <span className="rounded-full border border-green-500/20 bg-green-500/10 px-3 py-1 text-[9px] font-black uppercase tracking-[0.2em] text-green-600 dark:text-green-400">
                PATCH / POST
              </span>
            </div>

            <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
              <motion.section
                variants={itemVariants}
                className="glass-panel-no-hover rounded-2xl border border-white/10 p-6 shadow-3xl"
              >
                <div className="mb-6 flex items-start justify-between gap-4">
                  <div className="flex items-center gap-3">
                    <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-blue-500/20 bg-blue-500/10 text-blue-500 shadow-inner">
                      <Shield className="h-5 w-5" />
                    </div>
                    <div>
                      <h3 className="text-[13px] font-black uppercase tracking-[0.18em]">注册邀请码</h3>
                      <p className="mt-1 text-[9px] font-black uppercase tracking-[0.25em] opacity-30">
                        当前为可写设置，变更后立即生效
                      </p>
                    </div>
                  </div>
                  {settings.registration.writeSupported ? statusPill(true, '可编辑', '只读') : statusPill(false, '可编辑', '只读')}
                </div>

                <div className="space-y-5">
                  <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                    <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                      <div className="mb-2 text-[9px] font-black uppercase tracking-[0.25em] opacity-30">是否强制邀请码</div>
                      <div className="text-sm font-black">{settings.registration.inviteCodeRequired ? '是' : '否'}</div>
                    </div>
                    <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                      <div className="mb-2 text-[9px] font-black uppercase tracking-[0.25em] opacity-30">管理角色</div>
                      <div className="flex flex-wrap gap-2">
                        {settings.registration.managementRoles.map((role) => (
                          <span
                            key={role}
                            className="rounded-full border border-white/10 bg-white/5 px-2 py-0.5 text-[9px] font-black uppercase tracking-[0.2em]"
                          >
                            {role}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>

                  <div className="rounded-xl border border-white/10 bg-black/20 p-4">
                    <div className="mb-2 flex items-center justify-between gap-3">
                      <span className="text-[9px] font-black uppercase tracking-[0.25em] opacity-30">当前邀请码</span>
                      <button
                        type="button"
                        onClick={async () => {
                          try {
                            await navigator.clipboard.writeText(settings.registration.currentInviteCode);
                            setNotice('邀请码已复制');
                          } catch {
                            setError('复制邀请码失败');
                          }
                        }}
                        className="rounded-lg p-2 text-blue-500 transition-all hover:bg-white/10 hover:text-blue-400"
                        title="复制邀请码"
                      >
                        <Copy className="h-4 w-4" />
                      </button>
                    </div>
                    <div className="break-all rounded-xl border border-white/10 bg-blue-500/5 px-4 py-4 font-mono text-lg font-black tracking-[0.3em] text-blue-500">
                      {settings.registration.currentInviteCode}
                    </div>
                  </div>

                  <form
                    className="grid grid-cols-1 gap-4 lg:grid-cols-[1fr_auto]"
                    onSubmit={inviteCodeForm.handleSubmit(handleSaveInviteCode, () => {
                      setError('');
                    })}
                  >
                    <label className="block">
                      <span className="mb-2 block text-[9px] font-black uppercase tracking-[0.25em] opacity-30">
                        编辑邀请码
                      </span>
                      <AdminInput
                        {...inviteCodeForm.register('inviteCode', {
                          required: '邀请码不能为空',
                          maxLength: {
                            value: 64,
                            message: '邀请码不能超过 64 个字符',
                          },
                          validate: (value) => value.trim().length > 0 || '邀请码不能为空',
                        })}
                        maxLength={64}
                        placeholder="输入新的邀请码"
                        aria-invalid={inviteCodeForm.formState.errors.inviteCode ? 'true' : 'false'}
                      />
                      {inviteCodeForm.formState.errors.inviteCode ? (
                        <div className="mt-2 text-[10px] font-bold uppercase tracking-[0.15em] text-red-500 dark:text-red-400">
                          {inviteCodeForm.formState.errors.inviteCode.message}
                        </div>
                      ) : null}
                    </label>
                    <div className="flex items-end gap-3">
                      <button
                        type="submit"
                        disabled={savingInviteCode || rotatingInviteCode}
                        className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-5 py-4 text-[11px] font-black uppercase tracking-[0.15em] text-white shadow-lg transition-all hover:bg-blue-500 hover:scale-[1.02] active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        <Save className="h-4 w-4" />
                        {savingInviteCode ? '保存中' : '保存'}
                      </button>
                      <button
                        type="button"
                        onClick={() => setRotateInviteDialogOpen(true)}
                        disabled={savingInviteCode || rotatingInviteCode}
                        className="inline-flex items-center justify-center gap-2 rounded-lg border border-white/10 bg-white/5 px-5 py-4 text-[11px] font-black uppercase tracking-[0.15em] transition-all hover:bg-white/15 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        <RotateCcw className={cn('h-4 w-4', rotatingInviteCode && 'animate-spin')} />
                        {rotatingInviteCode ? '轮换中' : '轮换'}
                      </button>
                    </div>
                  </form>
                </div>
              </motion.section>

              <motion.section
                variants={itemVariants}
                className="glass-panel-no-hover rounded-2xl border border-white/10 p-6 shadow-3xl"
              >
                <div className="mb-6 flex items-start justify-between gap-4">
                  <div className="flex items-center gap-3">
                    <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-amber-500/20 bg-amber-500/10 text-amber-500 shadow-inner">
                      <Database className="h-5 w-5" />
                    </div>
                    <div>
                      <h3 className="text-[13px] font-black uppercase tracking-[0.18em]">离线快传存储上限</h3>
                      <p className="mt-1 text-[9px] font-black uppercase tracking-[0.25em] opacity-30">
                        控制离线快传在站点内可占用的总容量
                      </p>
                    </div>
                  </div>
                  {settings.transfer.writeSupported ? statusPill(true, '可编辑', '只读') : statusPill(false, '可编辑', '只读')}
                </div>

                <div className="space-y-5">
                  <div className="rounded-xl border border-white/10 bg-black/20 p-4">
                    <div className="mb-2 text-[9px] font-black uppercase tracking-[0.25em] opacity-30">当前上限</div>
                    <div className="text-3xl font-black tracking-tight text-amber-500">
                      {formatBytes(settings.transfer.offlineTransferStorageLimitBytes)}
                    </div>
                    <div className="mt-2 text-[9px] font-black uppercase tracking-[0.25em] opacity-30">
                      {settings.transfer.offlineTransferStorageLimitBytes} 字节
                    </div>
                  </div>

                  <form
                    className="space-y-5"
                    onSubmit={offlineTransferLimitForm.handleSubmit(handleSaveTransferLimit, () => {
                      setError('');
                    })}
                  >
                    <label className="block">
                      <span className="mb-2 block text-[9px] font-black uppercase tracking-[0.25em] opacity-30">
                        输入新的字节数
                      </span>
                      <AdminInput
                        type="number"
                        min={1}
                        step={1}
                        {...offlineTransferLimitForm.register('offlineTransferStorageLimitBytes', {
                          valueAsNumber: true,
                          required: '离线快传存储上限不能为空',
                          validate: (value) =>
                            Number.isInteger(value) && value > 0 ? true : '离线快传存储上限必须是大于 0 的整数',
                        })}
                        aria-invalid={offlineTransferLimitForm.formState.errors.offlineTransferStorageLimitBytes ? 'true' : 'false'}
                      />
                      {offlineTransferLimitForm.formState.errors.offlineTransferStorageLimitBytes ? (
                        <div className="mt-2 text-[10px] font-bold uppercase tracking-[0.15em] text-red-500 dark:text-red-400">
                          {offlineTransferLimitForm.formState.errors.offlineTransferStorageLimitBytes.message}
                        </div>
                      ) : null}
                    </label>

                    <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-amber-500/10 bg-amber-500/5 px-4 py-4">
                      <div>
                        <div className="text-[9px] font-black uppercase tracking-[0.25em] opacity-30">容量预览</div>
                        <div className="mt-1 text-sm font-black">{formatBytes(offlineLimitPreview)}</div>
                      </div>
                      <button
                        type="submit"
                        disabled={savingTransferLimit || savingInviteCode || rotatingInviteCode}
                        className="inline-flex items-center justify-center gap-2 rounded-lg bg-amber-500 px-5 py-4 text-[11px] font-black uppercase tracking-[0.15em] text-white shadow-lg transition-all hover:bg-amber-400 hover:scale-[1.02] active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        <Save className="h-4 w-4" />
                        {savingTransferLimit ? '保存中' : '保存上限'}
                      </button>
                    </div>
                  </form>

                  <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                    <div className="mb-2 flex items-center gap-2 text-[9px] font-black uppercase tracking-[0.25em] opacity-30">
                      <Clock3 className="h-3.5 w-3.5" />
                      仅供运营参考
                    </div>
                    <div className="text-[11px] font-bold leading-6 opacity-70">
                      该设置只影响离线快传的总存储配额，不会改变文件列表、分享或普通上传的容量规则。
                    </div>
                  </div>
                </div>
              </motion.section>
            </div>
          </section>

          <section>
            <div className="mb-5 flex items-center justify-between gap-4">
              <div>
                <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">只读快照</h2>
                <p className="mt-2 text-[11px] font-bold opacity-40">
                  下列内容全部来自 <span className="font-mono">GET /api/admin/settings</span>，当前不提供前端编辑入口。
                </p>
              </div>
              <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[9px] font-black uppercase tracking-[0.2em] opacity-70">
                Snapshot
              </span>
            </div>

            <div className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
              <SnapshotCard
                title="站点设置"
                badge={settings.site.writeSupported ? '可写快照' : '只读快照'}
                icon={<Settings className="h-5 w-5" />}
              >
                <SnapshotRow label="是否支持" value={statusPill(settings.site.supported, '已接入', '未接入')} />
                <SnapshotRow label="写入支持" value={statusPill(settings.site.writeSupported, '可写', '只读')} />
              </SnapshotCard>

              <SnapshotCard
                title="用户会话"
                badge={settings.userSession.writeSupported ? '可写快照' : '只读快照'}
                icon={<Shield className="h-5 w-5" />}
              >
                <SnapshotRow label="Access TTL" value={formatDurationSeconds(settings.userSession.accessExpirationSeconds)} />
                <SnapshotRow label="Refresh TTL" value={formatDurationSeconds(settings.userSession.refreshExpirationSeconds)} />
                <SnapshotRow
                  label="Token 黑名单"
                  value={statusPill(settings.userSession.tokenBlacklistEnabled, '已启用', '未启用')}
                />
                <SnapshotRow
                  label="黑名单缓冲"
                  value={formatDurationSeconds(settings.userSession.tokenBlacklistTtlBufferSeconds)}
                />
                <SnapshotRow label="写入支持" value={statusPill(settings.userSession.writeSupported, '可写', '只读')} />
              </SnapshotCard>

              <SnapshotCard
                title="媒体处理"
                badge={settings.mediaProcessing.writeSupported ? '可写快照' : '只读快照'}
                icon={<Server className="h-5 w-5" />}
              >
                <SnapshotRow
                  label="元数据提取"
                  value={statusPill(settings.mediaProcessing.metadataExtractionEnabled, '已启用', '未启用')}
                />
                <SnapshotRow
                  label="缩略图生成"
                  value={statusPill(settings.mediaProcessing.thumbnailGenerationEnabled, '已启用', '未启用')}
                />
                <SnapshotRow
                  label="视频封面"
                  value={statusPill(settings.mediaProcessing.videoPosterEnabled, '已启用', '未启用')}
                />
                <SnapshotRow label="写入支持" value={statusPill(settings.mediaProcessing.writeSupported, '可写', '只读')} />
              </SnapshotCard>

              <SnapshotCard
                title="任务队列"
                badge={settings.queue.writeSupported ? '可写快照' : '只读快照'}
                icon={<Clock3 className="h-5 w-5" />}
              >
                <SnapshotRow label="后端" value={settings.queue.backend} valueClassName="font-mono uppercase tracking-[0.2em]" />
                <SnapshotRow label="固定延迟" value={formatDurationMs(settings.queue.mediaMetadataFixedDelayMs)} />
                <SnapshotRow label="初始延迟" value={formatDurationMs(settings.queue.mediaMetadataInitialDelayMs)} />
                <SnapshotRow label="写入支持" value={statusPill(settings.queue.writeSupported, '可写', '只读')} />
              </SnapshotCard>

              <SnapshotCard
                title="外观"
                badge={settings.appearance.writeSupported ? '可写快照' : '只读快照'}
                icon={<Layers3 className="h-5 w-5" />}
              >
                <SnapshotRow label="是否支持" value={statusPill(settings.appearance.supported, '已接入', '未接入')} />
                <SnapshotRow label="写入支持" value={statusPill(settings.appearance.writeSupported, '可写', '只读')} />
              </SnapshotCard>

              <SnapshotCard
                title="服务器"
                badge={settings.server.writeSupported ? '可写快照' : '只读快照'}
                icon={<Server className="h-5 w-5" />}
              >
                <SnapshotRow
                  label="存储提供者"
                  value={settings.server.storageProvider}
                  valueClassName="font-mono uppercase tracking-[0.2em]"
                />
                <SnapshotRow label="Redis" value={statusPill(settings.server.redisEnabled, '已启用', '未启用')} />
                <SnapshotRow label="写入支持" value={statusPill(settings.server.writeSupported, '可写', '只读')} />
              </SnapshotCard>
            </div>
          </section>
        </motion.div>
      ) : null}

      <AdminAlertDialog
        open={rotateInviteDialogOpen}
        title="轮换邀请码"
        description="旧邀请码会立即失效，新的邀请码将覆盖当前注册入口。"
        confirmLabel="确认轮换"
        cancelLabel="取消"
        confirmTone="warning"
        busy={rotatingInviteCode}
        onConfirm={handleConfirmRotateInviteCode}
        onCancel={() => setRotateInviteDialogOpen(false)}
      />
    </motion.div>
  );
}
