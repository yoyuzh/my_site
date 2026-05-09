import React, { useEffect, useMemo, useState } from 'react';
import { Image as ImageIcon, KeyRound, RefreshCw, Save, Server, Settings } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';
import AdminSchemaForm from '../../components/admin/AdminSchemaForm';
import type { AdminConfigDefinition } from '../../api/types';
import { useAdminConfigSnapshot } from '../../api/queries';
import {
  rotateAdminInviteCode,
  updateAdminInviteCode,
  updateAdminSettings,
  updateOfflineTransferStorageLimit,
} from '../../api/mutations';
import { formatBytes } from '../../lib/format';

type GroupPresentation = {
  label: string;
  description: string;
  icon: React.ReactNode;
};

type ConfigGroup = {
  id: string;
  fields: AdminConfigDefinition[];
} & GroupPresentation;

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

function parsePositiveBytes(value: string) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : null;
}

function asBoolean(value: unknown, fallback = false) {
  return typeof value === 'boolean' ? value : fallback;
}

function asString(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback;
}

function asStringArray(value: unknown) {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}

function asNumberString(value: unknown, fallback = '') {
  return typeof value === 'number' && Number.isFinite(value) ? String(value) : fallback;
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

const AdminSetting: React.FC = () => {
  const [activeTab, setActiveTab] = useState<string>('');
  const [inviteCodeRequired, setInviteCodeRequired] = useState(false);
  const [inviteCode, setInviteCode] = useState('');
  const [managementRoles, setManagementRoles] = useState('ADMIN');
  const [offlineLimit, setOfflineLimit] = useState('');
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const {
    data: snapshot,
    isLoading,
    isError,
    refetch,
  } = useAdminConfigSnapshot();

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
    const fields = snapshot?.fields;
    if (!fields) {
      return;
    }

    setInviteCodeRequired(asBoolean(findField(fields, 'registration.inviteCodeRequired')?.value));
    setInviteCode(asString(findField(fields, 'registration.currentInviteCode')?.value));
    setManagementRoles(asStringArray(findField(fields, 'registration.managementRoles')?.value).join(', '));
    setOfflineLimit(asNumberString(findField(fields, 'transfer.offlineTransferStorageLimitBytes')?.value));
  }, [snapshot?.fields]);

  async function refreshSnapshot() {
    await refetch();
  }

  async function saveRegistration() {
    const roles = managementRoles
      .split(',')
      .map((role) => role.trim())
      .filter(Boolean);

    if (roles.length === 0) {
      setStatusMessage('管理角色不能为空');
      return;
    }

    try {
      await updateAdminSettings({
        registration: {
          inviteCodeRequired,
          currentInviteCode: inviteCode.trim(),
          managementRoles: roles,
        },
      });
      setStatusMessage('注册设置已保存');
      await refreshSnapshot();
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '保存注册设置失败');
    }
  }

  async function saveInviteCodeOnly() {
    try {
      await updateAdminInviteCode(inviteCode.trim());
      setStatusMessage('邀请码已保存');
      await refreshSnapshot();
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
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '生成邀请码失败');
    }
  }

  async function saveOfflineLimit() {
    const nextLimit = parsePositiveBytes(offlineLimit);
    if (nextLimit == null) {
      setStatusMessage('离线下载容量限制必须是正数字节数');
      return;
    }

    try {
      await updateOfflineTransferStorageLimit(nextLimit);
      setStatusMessage(`离线下载容量限制已更新为 ${formatBytes(nextLimit)}`);
      await refreshSnapshot();
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '保存离线下载容量限制失败');
    }
  }

  function renderRegistrationControls() {
    return (
      <div className="mt-6 rounded-2xl border border-[#D9E3F2] dark:border-[#222233] bg-white/70 dark:bg-[#0F1017] p-6">
        <div className="mb-6">
          <h3 className="text-base font-bold text-text-primary-light dark:text-white">定向写入控制</h3>
          <p className="mt-1 text-sm text-text-muted-light dark:text-text-muted-dark">
            通用配置写入尚未接通，当前继续使用现有注册设置接口。
          </p>
        </div>

        <form className="space-y-6" onSubmit={(event) => event.preventDefault()}>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="md:col-span-1">
              <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">邀请码注册</label>
              <p className="text-[13px] text-text-muted-light dark:text-text-muted-dark leading-relaxed font-geist">
                控制新用户注册是否必须填写当前邀请码。
              </p>
            </div>
            <div className="md:col-span-2 flex items-center">
              <label className="relative inline-flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  className="sr-only peer"
                  checked={inviteCodeRequired}
                  onChange={(event) => setInviteCodeRequired(event.target.checked)}
                />
                <div className="w-11 h-6 bg-[#D9E3F2] dark:bg-[#222233] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-brand-light dark:peer-checked:bg-brand-dark"></div>
                <span className="ml-3 text-sm font-medium text-text-secondary-light dark:text-text-secondary-dark">
                  {inviteCodeRequired ? '开启' : '关闭'}
                </span>
              </label>
            </div>
          </div>

          <hr className="border-[#D9E3F2] dark:border-[#222233]" />

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="md:col-span-1">
              <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">当前邀请码</label>
              <p className="text-[13px] text-text-muted-light dark:text-text-muted-dark leading-relaxed font-geist">
                Schema 中该字段只读，这里继续走现有单独保存与轮换接口。
              </p>
            </div>
            <div className="md:col-span-2 flex gap-2">
              <input
                type="text"
                className="input-field flex-1"
                value={inviteCode}
                onChange={(event) => setInviteCode(event.target.value)}
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

          <hr className="border-[#D9E3F2] dark:border-[#222233]" />

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="md:col-span-1">
              <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">管理角色</label>
              <p className="text-[13px] text-text-muted-light dark:text-text-muted-dark leading-relaxed font-geist">
                逗号分隔，保存时会写回现有注册治理配置。
              </p>
            </div>
            <div className="md:col-span-2">
              <input
                type="text"
                className="input-field"
                value={managementRoles}
                onChange={(event) => setManagementRoles(event.target.value)}
              />
            </div>
          </div>

          <div className="pt-2 flex justify-end">
            <button type="button" className="btn-primary flex items-center gap-2" onClick={() => void saveRegistration()}>
              <Save size={18} /> 保存注册设置
            </button>
          </div>
        </form>
      </div>
    );
  }

  function renderTransferControls(fields: AdminConfigDefinition[]) {
    const snapshotLimit = findField(fields, 'transfer.offlineTransferStorageLimitBytes')?.value;

    return (
      <div className="mt-6 rounded-2xl border border-[#D9E3F2] dark:border-[#222233] bg-white/70 dark:bg-[#0F1017] p-6">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="md:col-span-1">
            <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">离线下载容量限制</label>
            <p className="text-[13px] text-text-muted-light dark:text-text-muted-dark leading-relaxed font-geist">
              当前快照值：
              {typeof snapshotLimit === 'number' ? ` ${formatBytes(snapshotLimit)}` : ' 未知'}
            </p>
          </div>
          <div className="md:col-span-2 flex gap-2">
            <input
              type="number"
              min={1}
              className="input-field flex-1"
              value={offlineLimit}
              onChange={(event) => setOfflineLimit(event.target.value)}
            />
            <button
              type="button"
              className="bg-white dark:bg-transparent border border-[#D9E3F2] dark:border-[#222233] px-4 rounded-lg text-sm font-semibold"
              onClick={() => void saveOfflineLimit()}
            >
              保存限制
            </button>
          </div>
        </div>
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

        <AdminSchemaForm fields={activeGroup.fields} readOnly />

        {activeGroup.id === 'registration' ? renderRegistrationControls() : null}
        {activeGroup.id === 'transfer' ? renderTransferControls(activeGroup.fields) : null}
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
    </AdminLayout>
  );
};

export default AdminSetting;
