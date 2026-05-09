import React, { useEffect, useMemo, useState } from 'react';
import { History, Image as ImageIcon, KeyRound, RefreshCw, Server, Settings } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';
import AdminConfirmDialog from '../../components/admin/AdminConfirmDialog';
import AdminSchemaForm from '../../components/admin/AdminSchemaForm';
import type { AdminConfigDefinition } from '../../api/types';
import { useAdminConfigHistory, useAdminConfigSnapshot } from '../../api/queries';
import {
  rollbackAdminConfigValue,
  rotateAdminInviteCode,
  updateAdminConfigValue,
  updateAdminInviteCode,
} from '../../api/mutations';

type GroupPresentation = {
  label: string;
  description: string;
  icon: React.ReactNode;
};

type ConfigGroup = {
  id: string;
  fields: AdminConfigDefinition[];
} & GroupPresentation;

type TargetedSettingDirtyState = {
  inviteCode: boolean;
};

const groupPresentationById: Record<string, GroupPresentation> = {
  registration: {
    label: '注册',
    description: '邀请码、注册准入与管理角色。',
    icon: <KeyRound size={18} />,
  },
  transfer: {
    label: '离线传输',
    description: '离线传输容量与相关配额快照。',
    icon: <Settings size={18} />,
  },
  media: {
    label: '媒体处理',
    description: '媒体元数据与缩略图运行配置。',
    icon: <ImageIcon size={18} />,
  },
  queue: {
    label: '队列',
    description: '队列后端与媒体任务调度快照。',
    icon: <RefreshCw size={18} />,
  },
  server: {
    label: '服务',
    description: '服务端基础运行时快照。',
    icon: <Server size={18} />,
  },
};

function asString(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback;
}

function isGenericWritableField(field: AdminConfigDefinition) {
  return field.source === 'database' && field.editable;
}

function formatConfigValue(value: unknown) {
  if (Array.isArray(value)) {
    return value.join(', ');
  }
  if (typeof value === 'boolean') {
    return value ? '开启' : '关闭';
  }
  if (value == null) {
    return '-';
  }
  return String(value);
}

function groupFields(fields: AdminConfigDefinition[]): ConfigGroup[] {
  const grouped = new Map<string, AdminConfigDefinition[]>();

  for (const field of fields) {
    const existing = grouped.get(field.group);
    if (existing) {
      existing.push(field);
      continue;
    }
    grouped.set(field.group, [field]);
  }

  return [...grouped.entries()].map(([id, groupFields]) => ({
    id,
    fields: groupFields,
    ...(groupPresentationById[id] ?? {
      label: id,
      description: '后端返回的配置分组。',
      icon: <Settings size={18} />,
    }),
  }));
}

function findField(fields: AdminConfigDefinition[], key: string) {
  return fields.find((field) => field.key === key);
}

const cleanTargetedSettings: TargetedSettingDirtyState = {
  inviteCode: false,
};

const AdminSetting: React.FC = () => {
  const [activeTab, setActiveTab] = useState<string>('');
  const [inviteCode, setInviteCode] = useState('');
  const [targetedDirty, setTargetedDirty] = useState<TargetedSettingDirtyState>(cleanTargetedSettings);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [selectedHistoryKey, setSelectedHistoryKey] = useState<string | null>(null);
  const [rollbackTarget, setRollbackTarget] = useState<{ key: string; version: number } | null>(null);
  const [isRollingBack, setIsRollingBack] = useState(false);
  const {
    data: snapshot,
    isLoading,
    isError,
    refetch,
  } = useAdminConfigSnapshot();
  const {
    data: historyPage,
    isLoading: isHistoryLoading,
    refetch: refetchHistory,
  } = useAdminConfigHistory(selectedHistoryKey, 1, 10);

  const groups = useMemo(() => groupFields(snapshot?.fields ?? []), [snapshot?.fields]);
  const activeGroup = useMemo(
    () => groups.find((group) => group.id === activeTab) ?? groups[0] ?? null,
    [activeTab, groups],
  );

  useEffect(() => {
    if (!groups.length) {
      setActiveTab('');
      return;
    }

    setActiveTab((current) => (groups.some((group) => group.id === current) ? current : groups[0].id));
  }, [groups]);

  useEffect(() => {
    if (!activeGroup) {
      setSelectedHistoryKey(null);
      return;
    }
    const writableKeys = activeGroup.fields.filter(isGenericWritableField).map((field) => field.key);
    if (writableKeys.length === 0) {
      setSelectedHistoryKey(null);
      return;
    }
    setSelectedHistoryKey((current) => (current && writableKeys.includes(current) ? current : writableKeys[0]));
  }, [activeGroup]);

  useEffect(() => {
    const fields = snapshot?.fields;
    if (!fields) {
      return;
    }

    if (!targetedDirty.inviteCode) {
      setInviteCode(asString(findField(fields, 'registration.currentInviteCode')?.value));
    }
  }, [snapshot?.fields, targetedDirty]);

  async function refreshSnapshot() {
    await refetch();
  }

  async function saveGenericConfigValues(values: Record<string, unknown>) {
    if (!activeGroup) {
      return;
    }
    const writableKeys = new Set(activeGroup.fields.filter(isGenericWritableField).map((field) => field.key));
    const updates = Object.entries(values).filter(([key]) => writableKeys.has(key));
    if (updates.length === 0) {
      setStatusMessage('当前分组没有可保存的通用配置');
      return;
    }

    try {
      for (const [key, value] of updates) {
        await updateAdminConfigValue(key, value, 'Updated from admin settings');
      }
      setStatusMessage('配置已保存');
      await refreshSnapshot();
      if (selectedHistoryKey) {
        await refetchHistory();
      }
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '保存配置失败');
    }
  }

  async function confirmRollback() {
    if (!rollbackTarget) {
      return;
    }
    setIsRollingBack(true);
    try {
      await rollbackAdminConfigValue(rollbackTarget.key, rollbackTarget.version);
      setStatusMessage(`已回滚到版本 ${rollbackTarget.version}`);
      setRollbackTarget(null);
      await refreshSnapshot();
      await refetchHistory();
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '回滚配置失败');
    } finally {
      setIsRollingBack(false);
    }
  }

  async function saveInviteCodeOnly() {
    try {
      await updateAdminInviteCode(inviteCode.trim());
      setStatusMessage('邀请码已保存');
      await refreshSnapshot();
      setTargetedDirty((current) => ({
        ...current,
        inviteCode: false,
      }));
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '保存邀请码失败');
    }
  }

  async function rotateInviteCodeNow() {
    try {
      const result = await rotateAdminInviteCode();
      setInviteCode(result.inviteCode);
      setStatusMessage(`已生成新邀请码：${result.inviteCode}`);
      await refreshSnapshot();
      setTargetedDirty((current) => ({
        ...current,
        inviteCode: false,
      }));
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '生成邀请码失败');
    }
  }

  function renderRegistrationControls() {
    return (
      <div className="mt-6 rounded-2xl border border-[#D9E3F2] dark:border-[#222233] bg-white/70 dark:bg-[#0F1017] p-6">
        <div className="mb-6">
          <h3 className="text-base font-bold text-text-primary-light dark:text-white">邀请码</h3>
          <p className="mt-1 text-sm text-text-muted-light dark:text-text-muted-dark">
            保存当前邀请码，或直接生成新的邀请码。
          </p>
        </div>

        <form onSubmit={(event) => event.preventDefault()}>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="md:col-span-1">
              <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">
                当前邀请码
              </label>
              <p className="text-[13px] text-text-muted-light dark:text-text-muted-dark leading-relaxed font-geist">
                可单独保存，也可以生成新的邀请码。
              </p>
            </div>
            <div className="md:col-span-2 flex gap-2">
              <input
                type="text"
                className="input-field flex-1"
                value={inviteCode}
                onChange={(event) => {
                  setInviteCode(event.target.value);
                  setTargetedDirty((current) => ({ ...current, inviteCode: true }));
                }}
              />
              <button
                type="button"
                className="bg-white dark:bg-transparent border border-[#D9E3F2] dark:border-[#222233] px-4 rounded-lg text-sm font-semibold"
                onClick={() => void saveInviteCodeOnly()}
              >
                保存邀请码
              </button>
              <button
                type="button"
                className="bg-brand-light/10 text-brand-light px-4 rounded-lg text-sm font-semibold"
                onClick={() => void rotateInviteCodeNow()}
              >
                生成
              </button>
            </div>
          </div>
        </form>
      </div>
    );
  }

  function renderHistoryPanel(fields: AdminConfigDefinition[]) {
    const writableFields = fields.filter(isGenericWritableField);
    if (writableFields.length === 0) {
      return null;
    }
    const activeHistoryField = writableFields.find((field) => field.key === selectedHistoryKey) ?? writableFields[0];

    return (
      <div className="rounded-2xl border border-[#D9E3F2] dark:border-[#222233] bg-white/70 dark:bg-[#0F1017] p-6">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3 mb-4">
          <div>
            <h3 className="text-base font-bold text-text-primary-light dark:text-white flex items-center gap-2">
              <History size={18} /> 变更历史
            </h3>
            <p className="mt-1 text-sm text-text-muted-light dark:text-text-muted-dark">
              查看可写配置的最近版本，并按需恢复。
            </p>
          </div>
          <select
            className="input-field md:w-72"
            value={activeHistoryField.key}
            onChange={(event) => setSelectedHistoryKey(event.target.value)}
          >
            {writableFields.map((field) => (
              <option key={field.key} value={field.key}>
                {field.title}
              </option>
            ))}
          </select>
        </div>

        {isHistoryLoading ? (
          <div className="py-6 text-sm text-text-muted-light dark:text-text-muted-dark">加载中...</div>
        ) : historyPage?.items.length ? (
          <div className="space-y-3">
            {historyPage.items.map((item) => (
              <div
                key={item.id}
                className="rounded-xl border border-[#D9E3F2] dark:border-[#222233] bg-white dark:bg-[#111117] px-4 py-3"
              >
                <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-sm font-semibold text-text-primary-light dark:text-white">
                      版本 {item.version} · {item.actorUsername}
                    </div>
                    <div className="mt-1 text-xs text-text-muted-light dark:text-text-muted-dark">
                      {new Date(item.createdAt).toLocaleString()}
                    </div>
                    <div className="mt-2 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                      {formatConfigValue(item.beforeValue)} → {formatConfigValue(item.afterValue)}
                    </div>
                  </div>
                  <button
                    type="button"
                    className="bg-white dark:bg-transparent border border-[#D9E3F2] dark:border-[#222233] px-3 py-2 rounded-lg text-sm font-semibold"
                    onClick={() => setRollbackTarget({ key: item.key, version: item.version })}
                  >
                    回滚
                  </button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="py-6 text-sm text-text-muted-light dark:text-text-muted-dark">暂无变更历史</div>
        )}
      </div>
    );
  }

  function renderActiveGroup() {
    if (isLoading) {
      return <div className="p-8 text-center text-text-muted-light">加载中...</div>;
    }

    if (isError || !snapshot) {
      return <div className="p-8 text-center text-red-500">加载失败</div>;
    }

    if (!activeGroup) {
      return (
        <div className="flex flex-col items-center justify-center py-20 text-text-muted-light dark:text-text-muted-dark">
          <Settings size={48} className="mb-4 opacity-50" />
          <p className="text-lg font-medium">后端暂未返回可展示的配置分组</p>
        </div>
      );
    }

    return (
      <div className="space-y-6">
        <div>
          <h2 className="text-xl font-bold text-text-primary-light dark:text-white">{activeGroup.label}</h2>
          <p className="mt-2 text-sm text-text-muted-light dark:text-text-muted-dark">{activeGroup.description}</p>
        </div>

        <AdminSchemaForm
          fields={activeGroup.fields}
          readOnly={!activeGroup.fields.some(isGenericWritableField)}
          onSubmit={activeGroup.fields.some(isGenericWritableField) ? (values) => void saveGenericConfigValues(values) : undefined}
        />

        {renderHistoryPanel(activeGroup.fields)}

        {activeGroup.id === 'registration' ? renderRegistrationControls() : null}
      </div>
    );
  }

  return (
    <AdminLayout title="参数设置">
      <div className="flex flex-col lg:flex-row gap-8">
        <aside className="w-full lg:w-64 flex-shrink-0">
          <div className="card-container p-2">
            <nav className="flex flex-row lg:flex-col gap-1 overflow-x-auto lg:overflow-x-visible pb-2 lg:pb-0 scrollbar-hide">
              {groups.map((group) => (
                <button
                  key={group.id}
                  onClick={() => setActiveTab(group.id)}
                  className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-all duration-200 whitespace-nowrap text-sm font-medium ${
                    activeGroup?.id === group.id
                      ? 'bg-brand-light text-white dark:bg-brand-dark shadow-md'
                      : 'text-text-secondary-light dark:text-text-secondary-dark hover:bg-black/5 dark:hover:bg-white/5 hover:text-text-primary-light dark:hover:text-text-primary-dark'
                  }`}
                >
                  {group.icon}
                  {group.label}
                </button>
              ))}
            </nav>
          </div>
        </aside>

        <div className="flex-1 max-w-5xl">
          {statusMessage ? (
            <div className="mb-4 rounded-lg border border-[#D9E3F2] dark:border-[#222233] bg-white dark:bg-[#111117] px-4 py-3 text-sm text-text-secondary-light dark:text-text-secondary-dark">
              {statusMessage}
            </div>
          ) : null}
          <div className="card-container p-8 animate-fade-in-up">{renderActiveGroup()}</div>
        </div>
      </div>

      <AdminConfirmDialog
        open={rollbackTarget != null}
        title="回滚配置"
        description={rollbackTarget ? `确认恢复到版本 ${rollbackTarget.version}？` : ''}
        confirmLabel="确认回滚"
        isSubmitting={isRollingBack}
        onConfirm={() => void confirmRollback()}
        onClose={() => setRollbackTarget(null)}
      />
    </AdminLayout>
  );
};

export default AdminSetting;
