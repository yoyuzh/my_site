import { useEffect, useState, type ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { Copy, RefreshCw, RotateCcw, Save } from 'lucide-react';
import { motion } from 'motion/react';
import { cn } from '@/src/lib/utils';
import { formatBytes } from '@/src/lib/format';
import { AdminAlertDialog } from '@/src/components/admin/AdminAlertDialog';
import { AdminInput } from '@/src/components/admin/AdminInput';
import {
  getAdminSettings,
  rotateAdminRegistrationInviteCode,
  updateAdminSettings,
  type AdminSettings,
  type AdminSettingsUpdateRequest,
} from '@/src/operations-admin/api/settings/settings';

type SettingsFormValues = {
  siteSupported: boolean;
  inviteCodeRequired: boolean;
  currentInviteCode: string;
  managementRolesRaw: string;
  accessExpirationSeconds: number;
  refreshExpirationSeconds: number;
  tokenBlacklistEnabled: boolean;
  tokenBlacklistTtlBufferSeconds: number;
  offlineTransferStorageLimitBytes: number;
  metadataExtractionEnabled: boolean;
  thumbnailGenerationEnabled: boolean;
  videoPosterEnabled: boolean;
  queueBackend: string;
  queueMediaMetadataFixedDelayMs: number;
  queueMediaMetadataInitialDelayMs: number;
  appearanceSupported: boolean;
  serverStorageProvider: string;
  serverRedisEnabled: boolean;
};

type SettingsWriteSupport = {
  site: boolean;
  registration: boolean;
  userSession: boolean;
  transfer: boolean;
  mediaProcessing: boolean;
  queue: boolean;
  appearance: boolean;
  server: boolean;
};

type SectionWriteState = 'writable' | 'mixed' | 'readOnly';

function statusPill(value: boolean, trueLabel = 'Enabled', falseLabel = 'Disabled') {
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

function sectionWriteStatePill(state: SectionWriteState) {
  if (state === 'mixed') {
    return (
      <span className="inline-flex items-center rounded-full border border-amber-500/20 bg-amber-500/10 px-2 py-0.5 text-[9px] font-black uppercase tracking-[0.2em] text-amber-600 dark:text-amber-300">
        Mixed
      </span>
    );
  }

  return statusPill(state === 'writable', 'Writable', 'Read only');
}

function getSettingsWriteSupport(settings: AdminSettings | null): SettingsWriteSupport {
  return {
    site: settings?.site.writeSupported ?? false,
    registration: settings?.registration.writeSupported ?? false,
    userSession: settings?.userSession.writeSupported ?? false,
    transfer: settings?.transfer.writeSupported ?? false,
    mediaProcessing: settings?.mediaProcessing.writeSupported ?? false,
    queue: settings?.queue.writeSupported ?? false,
    appearance: settings?.appearance.writeSupported ?? false,
    server: settings?.server.writeSupported ?? false,
  };
}

function hasWritableSettings(writeSupport: SettingsWriteSupport) {
  return (
    writeSupport.site ||
    writeSupport.registration ||
    writeSupport.userSession ||
    writeSupport.transfer ||
    writeSupport.mediaProcessing ||
    writeSupport.queue ||
    writeSupport.appearance ||
    writeSupport.server
  );
}

function hasReadOnlySettings(writeSupport: SettingsWriteSupport) {
  return (
    !writeSupport.site ||
    !writeSupport.registration ||
    !writeSupport.userSession ||
    !writeSupport.transfer ||
    !writeSupport.mediaProcessing ||
    !writeSupport.queue ||
    !writeSupport.appearance ||
    !writeSupport.server
  );
}

function getSectionWriteState(...flags: boolean[]): SectionWriteState {
  const writableCount = flags.filter(Boolean).length;
  if (writableCount === 0) {
    return 'readOnly';
  }
  if (writableCount === flags.length) {
    return 'writable';
  }
  return 'mixed';
}

function resolveSectionValue<T>(writeSupported: boolean, nextValue: T, currentValue: T) {
  return writeSupported ? nextValue : currentValue;
}

function SectionCard({
  title,
  subtitle,
  children,
  writeState,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
  writeState?: SectionWriteState;
}) {
  return (
    <section className="glass-panel-no-hover rounded-2xl border border-white/10 p-6 shadow-3xl">
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[12px] font-black uppercase tracking-[0.2em]">{title}</h2>
          {subtitle ? <p className="mt-2 text-[11px] font-bold opacity-50">{subtitle}</p> : null}
        </div>
        {writeState ? sectionWriteStatePill(writeState) : null}
      </div>
      {children}
    </section>
  );
}

function toRoleText(roles: string[]) {
  return roles.join(', ');
}

function parseRoleText(raw: string) {
  return raw
    .split(/[,\n]/)
    .map((item) => item.trim())
    .filter((item, index, list) => item.length > 0 && list.indexOf(item) === index);
}

function toFormValues(settings: AdminSettings): SettingsFormValues {
  return {
    siteSupported: settings.site.supported,
    inviteCodeRequired: settings.registration.inviteCodeRequired,
    currentInviteCode: settings.registration.currentInviteCode,
    managementRolesRaw: toRoleText(settings.registration.managementRoles),
    accessExpirationSeconds: settings.userSession.accessExpirationSeconds,
    refreshExpirationSeconds: settings.userSession.refreshExpirationSeconds,
    tokenBlacklistEnabled: settings.userSession.tokenBlacklistEnabled,
    tokenBlacklistTtlBufferSeconds: settings.userSession.tokenBlacklistTtlBufferSeconds,
    offlineTransferStorageLimitBytes: settings.transfer.offlineTransferStorageLimitBytes,
    metadataExtractionEnabled: settings.mediaProcessing.metadataExtractionEnabled,
    thumbnailGenerationEnabled: settings.mediaProcessing.thumbnailGenerationEnabled,
    videoPosterEnabled: settings.mediaProcessing.videoPosterEnabled,
    queueBackend: settings.queue.backend,
    queueMediaMetadataFixedDelayMs: settings.queue.mediaMetadataFixedDelayMs,
    queueMediaMetadataInitialDelayMs: settings.queue.mediaMetadataInitialDelayMs,
    appearanceSupported: settings.appearance.supported,
    serverStorageProvider: settings.server.storageProvider,
    serverRedisEnabled: settings.server.redisEnabled,
  };
}

export default function AdminSettingsPage() {
  const [settings, setSettings] = useState<AdminSettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [rotating, setRotating] = useState(false);
  const [rotateDialogOpen, setRotateDialogOpen] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const form = useForm<SettingsFormValues>({
    mode: 'onSubmit',
    reValidateMode: 'onChange',
    defaultValues: {
      siteSupported: false,
      inviteCodeRequired: true,
      currentInviteCode: '',
      managementRolesRaw: 'MODERATOR, ADMIN',
      accessExpirationSeconds: 900,
      refreshExpirationSeconds: 1209600,
      tokenBlacklistEnabled: false,
      tokenBlacklistTtlBufferSeconds: 60,
      offlineTransferStorageLimitBytes: 1,
      metadataExtractionEnabled: true,
      thumbnailGenerationEnabled: false,
      videoPosterEnabled: false,
      queueBackend: 'in-memory',
      queueMediaMetadataFixedDelayMs: 3000,
      queueMediaMetadataInitialDelayMs: 15000,
      appearanceSupported: false,
      serverStorageProvider: 'local',
      serverRedisEnabled: false,
    },
  });
  const writeSupport = getSettingsWriteSupport(settings);
  const hasWritableSections = hasWritableSettings(writeSupport);
  const hasReadOnlySections = hasReadOnlySettings(writeSupport);
  const registrationWriteState = getSectionWriteState(writeSupport.registration);
  const userSessionWriteState = getSectionWriteState(writeSupport.userSession);
  const transferMediaWriteState = getSectionWriteState(writeSupport.transfer, writeSupport.mediaProcessing);
  const systemWriteState = getSectionWriteState(writeSupport.queue, writeSupport.site, writeSupport.appearance, writeSupport.server);

  async function loadSettings(isRefresh = false) {
    if (isRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setError('');
    try {
      const next = await getAdminSettings();
      setSettings(next);
      form.reset(toFormValues(next));
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

  async function handleSaveAll(values: SettingsFormValues) {
    if (!settings) {
      return;
    }

    const inviteCode = values.currentInviteCode.trim();
    const managementRoles = parseRoleText(values.managementRolesRaw);
    if (writeSupport.registration && !inviteCode) {
      setError('邀请码不能为空');
      return;
    }
    if (writeSupport.registration && managementRoles.length === 0) {
      setError('至少保留一个管理角色');
      return;
    }

    if (!hasWritableSections) {
      setNotice('No writable settings are currently exposed by the backend.');
      return;
    }

    const payload: AdminSettingsUpdateRequest = {
      site: resolveSectionValue(writeSupport.site, { supported: values.siteSupported }, { supported: settings.site.supported }),
      registration: resolveSectionValue(
        writeSupport.registration,
        {
          inviteCodeRequired: values.inviteCodeRequired,
          currentInviteCode: inviteCode,
          managementRoles,
        },
        {
          inviteCodeRequired: settings.registration.inviteCodeRequired,
          currentInviteCode: settings.registration.currentInviteCode,
          managementRoles: settings.registration.managementRoles,
        },
      ),
      userSession: resolveSectionValue(
        writeSupport.userSession,
        {
          accessExpirationSeconds: values.accessExpirationSeconds,
          refreshExpirationSeconds: values.refreshExpirationSeconds,
          tokenBlacklistEnabled: values.tokenBlacklistEnabled,
          tokenBlacklistTtlBufferSeconds: values.tokenBlacklistTtlBufferSeconds,
        },
        {
          accessExpirationSeconds: settings.userSession.accessExpirationSeconds,
          refreshExpirationSeconds: settings.userSession.refreshExpirationSeconds,
          tokenBlacklistEnabled: settings.userSession.tokenBlacklistEnabled,
          tokenBlacklistTtlBufferSeconds: settings.userSession.tokenBlacklistTtlBufferSeconds,
        },
      ),
      transfer: resolveSectionValue(
        writeSupport.transfer,
        { offlineTransferStorageLimitBytes: values.offlineTransferStorageLimitBytes },
        { offlineTransferStorageLimitBytes: settings.transfer.offlineTransferStorageLimitBytes },
      ),
      mediaProcessing: resolveSectionValue(
        writeSupport.mediaProcessing,
        {
          metadataExtractionEnabled: values.metadataExtractionEnabled,
          thumbnailGenerationEnabled: values.thumbnailGenerationEnabled,
          videoPosterEnabled: values.videoPosterEnabled,
        },
        {
          metadataExtractionEnabled: settings.mediaProcessing.metadataExtractionEnabled,
          thumbnailGenerationEnabled: settings.mediaProcessing.thumbnailGenerationEnabled,
          videoPosterEnabled: settings.mediaProcessing.videoPosterEnabled,
        },
      ),
      queue: resolveSectionValue(
        writeSupport.queue,
        {
          backend: values.queueBackend.trim(),
          mediaMetadataFixedDelayMs: values.queueMediaMetadataFixedDelayMs,
          mediaMetadataInitialDelayMs: values.queueMediaMetadataInitialDelayMs,
        },
        {
          backend: settings.queue.backend,
          mediaMetadataFixedDelayMs: settings.queue.mediaMetadataFixedDelayMs,
          mediaMetadataInitialDelayMs: settings.queue.mediaMetadataInitialDelayMs,
        },
      ),
      appearance: resolveSectionValue(
        writeSupport.appearance,
        { supported: values.appearanceSupported },
        { supported: settings.appearance.supported },
      ),
      server: resolveSectionValue(
        writeSupport.server,
        {
          storageProvider: values.serverStorageProvider.trim(),
          redisEnabled: values.serverRedisEnabled,
        },
        {
          storageProvider: settings.server.storageProvider,
          redisEnabled: settings.server.redisEnabled,
        },
      ),
    };

    setSaving(true);
    setError('');
    setNotice('');
    try {
      const next = await updateAdminSettings(payload);
      setSettings(next);
      form.reset(toFormValues(next));
      setNotice('系统设置已保存');
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存系统设置失败');
    } finally {
      setSaving(false);
    }
  }

  async function handleRotateInviteCode() {
    if (!writeSupport.registration) {
      return;
    }

    setRotating(true);
    setError('');
    setNotice('');
    try {
      const nextInvite = await rotateAdminRegistrationInviteCode();
      form.setValue('currentInviteCode', nextInvite.currentInviteCode, { shouldDirty: true });
      setNotice('邀请码已轮换，点击保存即可写入整页设置');
    } catch (err) {
      setError(err instanceof Error ? err.message : '轮换邀请码失败');
    } finally {
      setRotating(false);
    }
  }

  const watchInviteCode = form.watch('currentInviteCode');
  const watchOfflineLimit = form.watch('offlineTransferStorageLimitBytes');
  const rolesPreview = parseRoleText(form.watch('managementRolesRaw')).join(' / ');
  const busy = loading || refreshing || saving || rotating;

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col overflow-y-auto p-8 text-gray-900 dark:text-gray-100"
    >
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="animate-text-reveal text-4xl font-black tracking-tight text-gray-900 dark:text-white">系统设置</h1>
          <p className="mt-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">
            全量可编辑模式 / Frontend Writable
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            void loadSettings(true);
          }}
          disabled={busy}
          className="flex items-center gap-3 rounded-lg glass-panel px-6 py-3 text-[11px] font-black uppercase tracking-widest transition-all hover:bg-white/40 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <RefreshCw className={cn('h-4 w-4', refreshing && 'animate-spin')} />
          刷新
        </button>
      </div>

      {error ? (
        <div className="mb-6 rounded-lg border border-red-500/20 bg-red-500/10 px-6 py-4 text-xs font-bold uppercase tracking-widest text-red-600 backdrop-blur-md dark:text-red-400">
          {error}
        </div>
      ) : null}
      {notice ? (
        <div className="mb-6 rounded-lg border border-blue-500/20 bg-blue-500/10 px-6 py-4 text-xs font-bold uppercase tracking-widest text-blue-600 backdrop-blur-md dark:text-blue-300">
          {notice}
        </div>
      ) : null}
      {settings && hasReadOnlySections ? (
        <div className="mb-6 rounded-lg border border-amber-500/20 bg-amber-500/10 px-6 py-4 text-xs font-bold uppercase tracking-widest text-amber-600 backdrop-blur-md dark:text-amber-300">
          Read-only sections are locked by the backend and will be preserved on save.
        </div>
      ) : null}

      {loading && !settings ? (
        <div className="glass-panel-no-hover rounded-lg px-4 py-16 text-center text-[10px] font-black uppercase tracking-widest opacity-40">
          正在读取系统设置...
        </div>
      ) : (
        <form className="space-y-6" onSubmit={form.handleSubmit(handleSaveAll)}>
          <SectionCard title="注册设置" subtitle="邀请码、角色、是否要求邀请码" writeState={registrationWriteState}>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <label className="block">
                <span className="mb-2 block text-[10px] font-black uppercase tracking-[0.2em] opacity-40">当前邀请码</span>
                <div className="flex gap-2">
                  <AdminInput
                    {...form.register('currentInviteCode', {
                      required: '邀请码不能为空',
                      maxLength: {
                        value: 64,
                        message: '邀请码长度不能超过 64',
                      },
                      validate: (value) => value.trim().length > 0 || '邀请码不能为空',
                    })}
                    disabled={!writeSupport.registration}
                    maxLength={64}
                    placeholder="输入邀请码"
                  />
                  <button
                    type="button"
                    onClick={async () => {
                      try {
                        await navigator.clipboard.writeText(watchInviteCode || '');
                        setNotice('邀请码已复制');
                      } catch {
                        setError('复制邀请码失败');
                      }
                    }}
                    className="rounded-lg border border-white/10 bg-white/5 px-3 text-blue-500 transition-all hover:bg-white/10"
                    title="复制邀请码"
                  >
                    <Copy className="h-4 w-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setRotateDialogOpen(true)}
                    disabled={!writeSupport.registration || rotating}
                    className="inline-flex items-center gap-2 rounded-lg border border-white/10 bg-white/5 px-3 text-[10px] font-black uppercase tracking-[0.15em] transition-all hover:bg-white/10 disabled:opacity-60"
                  >
                    <RotateCcw className={cn('h-3.5 w-3.5', rotating && 'animate-spin')} />
                    轮换
                  </button>
                </div>
              </label>
              <label className="block">
                <span className="mb-2 block text-[10px] font-black uppercase tracking-[0.2em] opacity-40">管理角色</span>
                <AdminInput
                  {...form.register('managementRolesRaw', {
                    validate: (value) => parseRoleText(value).length > 0 || '至少保留一个管理角色',
                  })}
                  disabled={!writeSupport.registration}
                  placeholder="示例: MODERATOR, ADMIN"
                />
                <div className="mt-2 text-[10px] font-bold opacity-50">预览: {rolesPreview || '-'}</div>
              </label>
              <label className="inline-flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 px-4 py-3">
                <input type="checkbox" className="h-4 w-4" disabled={!writeSupport.registration} {...form.register('inviteCodeRequired')} />
                <span className="text-[11px] font-bold">注册必须邀请码</span>
                {statusPill(form.watch('inviteCodeRequired'), 'Required', 'Optional')}
              </label>
            </div>
          </SectionCard>

          <SectionCard title="会话设置" subtitle="Access/Refresh 过期时间、黑名单开关与缓冲时间" writeState={userSessionWriteState}>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
              <label>
                <span className="mb-2 block text-[10px] font-black uppercase tracking-[0.2em] opacity-40">Access TTL (秒)</span>
                <AdminInput type="number" min={1} step={1} disabled={!writeSupport.userSession} {...form.register('accessExpirationSeconds', { valueAsNumber: true, min: 1 })} />
              </label>
              <label>
                <span className="mb-2 block text-[10px] font-black uppercase tracking-[0.2em] opacity-40">Refresh TTL (秒)</span>
                <AdminInput type="number" min={1} step={1} disabled={!writeSupport.userSession} {...form.register('refreshExpirationSeconds', { valueAsNumber: true, min: 1 })} />
              </label>
              <label>
                <span className="mb-2 block text-[10px] font-black uppercase tracking-[0.2em] opacity-40">黑名单缓冲 (秒)</span>
                <AdminInput
                  type="number"
                  min={1}
                  step={1}
                  disabled={!writeSupport.userSession}
                  {...form.register('tokenBlacklistTtlBufferSeconds', { valueAsNumber: true, min: 1 })}
                />
              </label>
              <label className="inline-flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 px-4 py-3">
                <input type="checkbox" className="h-4 w-4" disabled={!writeSupport.userSession} {...form.register('tokenBlacklistEnabled')} />
                <span className="text-[11px] font-bold">启用 Token 黑名单</span>
              </label>
            </div>
          </SectionCard>

          <SectionCard title="传输与媒体" subtitle="离线快传容量与媒体处理开关" writeState={transferMediaWriteState}>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
              <label className="md:col-span-2">
                <span className="mb-2 block text-[10px] font-black uppercase tracking-[0.2em] opacity-40">离线快传上限 (字节)</span>
                <AdminInput
                  type="number"
                  min={1}
                  step={1}
                  disabled={!writeSupport.transfer}
                  {...form.register('offlineTransferStorageLimitBytes', { valueAsNumber: true, min: 1 })}
                />
                <div className="mt-2 text-[10px] font-bold opacity-60">容量预览: {formatBytes(Number.isFinite(watchOfflineLimit) ? watchOfflineLimit : 0)}</div>
              </label>
              <label className="inline-flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 px-4 py-3">
                <input type="checkbox" className="h-4 w-4" disabled={!writeSupport.mediaProcessing} {...form.register('metadataExtractionEnabled')} />
                <span className="text-[11px] font-bold">元数据提取</span>
              </label>
              <label className="inline-flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 px-4 py-3">
                <input type="checkbox" className="h-4 w-4" disabled={!writeSupport.mediaProcessing} {...form.register('thumbnailGenerationEnabled')} />
                <span className="text-[11px] font-bold">缩略图生成</span>
              </label>
              <label className="inline-flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 px-4 py-3">
                <input type="checkbox" className="h-4 w-4" disabled={!writeSupport.mediaProcessing} {...form.register('videoPosterEnabled')} />
                <span className="text-[11px] font-bold">视频封面</span>
              </label>
            </div>
          </SectionCard>

          <SectionCard title="队列、站点、外观、服务" subtitle="剩余系统级字段统一在此编辑" writeState={systemWriteState}>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
              <label>
                <span className="mb-2 block text-[10px] font-black uppercase tracking-[0.2em] opacity-40">Queue Backend</span>
                <AdminInput disabled={!writeSupport.queue} {...form.register('queueBackend', { required: true })} placeholder="in-memory / redis" />
              </label>
              <label>
                <span className="mb-2 block text-[10px] font-black uppercase tracking-[0.2em] opacity-40">Fixed Delay (ms)</span>
                <AdminInput
                  type="number"
                  min={1}
                  step={1}
                  disabled={!writeSupport.queue}
                  {...form.register('queueMediaMetadataFixedDelayMs', { valueAsNumber: true, min: 1 })}
                />
              </label>
              <label>
                <span className="mb-2 block text-[10px] font-black uppercase tracking-[0.2em] opacity-40">Initial Delay (ms)</span>
                <AdminInput
                  type="number"
                  min={1}
                  step={1}
                  disabled={!writeSupport.queue}
                  {...form.register('queueMediaMetadataInitialDelayMs', { valueAsNumber: true, min: 1 })}
                />
              </label>
              <label>
                <span className="mb-2 block text-[10px] font-black uppercase tracking-[0.2em] opacity-40">Storage Provider</span>
                <AdminInput disabled={!writeSupport.server} {...form.register('serverStorageProvider', { required: true })} placeholder="local / s3" />
              </label>
              <label className="inline-flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 px-4 py-3">
                <input type="checkbox" className="h-4 w-4" disabled={!writeSupport.site} {...form.register('siteSupported')} />
                <span className="text-[11px] font-bold">站点支持</span>
              </label>
              <label className="inline-flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 px-4 py-3">
                <input type="checkbox" className="h-4 w-4" disabled={!writeSupport.appearance} {...form.register('appearanceSupported')} />
                <span className="text-[11px] font-bold">外观支持</span>
              </label>
              <label className="inline-flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 px-4 py-3">
                <input type="checkbox" className="h-4 w-4" disabled={!writeSupport.server} {...form.register('serverRedisEnabled')} />
                <span className="text-[11px] font-bold">服务端 Redis 开关</span>
              </label>
            </div>
          </SectionCard>

          <div className="sticky bottom-0 z-10 mt-4 border border-white/10 bg-black/30 p-4 backdrop-blur-xl">
            <button
              type="submit"
              disabled={busy || !hasWritableSections}
              className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-6 py-3 text-[11px] font-black uppercase tracking-[0.15em] text-white shadow-lg transition-all hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <Save className="h-4 w-4" />
              {saving ? '保存中...' : '保存全部设置'}
            </button>
          </div>
        </form>
      )}

      <AdminAlertDialog
        open={rotateDialogOpen}
        title="轮换邀请码"
        description="将生成一个全新邀请码。轮换后请记得点击“保存全部设置”。"
        confirmLabel="确认轮换"
        cancelLabel="取消"
        confirmTone="warning"
        busy={rotating}
        onConfirm={async () => {
          setRotateDialogOpen(false);
          await handleRotateInviteCode();
        }}
        onCancel={() => setRotateDialogOpen(false)}
      />
    </motion.div>
  );
}
