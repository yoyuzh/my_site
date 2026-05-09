import React, { useEffect, useState } from 'react';
import AdminLayout from '../../components/AdminLayout';
import {
  Save,
  Globe,
  KeyRound,
  Bot,
  Image as ImageIcon,
  CreditCard,
  Mail,
  RefreshCw,
  Palette,
  Bell,
  Server,
  Settings,
} from 'lucide-react';
import { useAdminSettings } from '../../api/queries';
import {
  rotateAdminInviteCode,
  updateAdminSettings,
  updateOfflineTransferStorageLimit,
} from '../../api/mutations';
import { formatBytes } from '../../lib/format';

const tabs = [
  { id: 'siteInfo', label: '基础设置', icon: <Globe size={18} /> },
  { id: 'userSession', label: '用户与会话', icon: <KeyRound size={18} /> },
  { id: 'captcha', label: '验证码', icon: <Bot size={18} /> },
  { id: 'media', label: '媒体处理', icon: <ImageIcon size={18} /> },
  { id: 'vas', label: '增值服务', icon: <CreditCard size={18} /> },
  { id: 'email', label: '邮件', icon: <Mail size={18} /> },
  { id: 'queue', label: '队列', icon: <RefreshCw size={18} /> },
  { id: 'appearance', label: '外观', icon: <Palette size={18} /> },
  { id: 'events', label: '事件', icon: <Bell size={18} /> },
  { id: 'server', label: '服务', icon: <Server size={18} /> },
];

const managementRoleOptions = [
  { value: 'ADMIN', label: '管理员' },
  { value: 'MODERATOR', label: '协管员' },
  { value: 'USER', label: '普通用户' },
];

function parsePositiveBytes(value: string) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : null;
}

function formatRuntimeValue(value: string) {
  const labels: Record<string, string> = {
    LOCAL: '本地存储',
    S3_COMPATIBLE: 'S3 兼容存储',
    OSS_SDK: 'OSS SDK',
    MEMORY: '内存队列',
    REDIS: 'Redis',
  };
  return labels[value] ?? value;
}

const AdminSetting: React.FC = () => {
  const [activeTab, setActiveTab] = useState('siteInfo');
  const [inviteCodeRequired, setInviteCodeRequired] = useState(false);
  const [inviteCode, setInviteCode] = useState('');
  const [managementRoles, setManagementRoles] = useState<string[]>(['ADMIN']);
  const [offlineLimit, setOfflineLimit] = useState('');
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const { data: settings, isLoading, isError, refetch } = useAdminSettings();

  useEffect(() => {
    if (!settings) {
      return;
    }
    setInviteCodeRequired(settings.registration.inviteCodeRequired);
    setInviteCode(settings.registration.currentInviteCode);
    setManagementRoles(settings.registration.managementRoles.length > 0 ? settings.registration.managementRoles : ['ADMIN']);
    setOfflineLimit(String(settings.transfer.offlineTransferStorageLimitBytes));
  }, [settings]);

  async function saveRegistration() {
    if (managementRoles.length === 0) {
      setStatusMessage('管理角色不能为空');
      return;
    }

    try {
      await updateAdminSettings({
        registration: {
          inviteCodeRequired,
          currentInviteCode: inviteCode.trim(),
          managementRoles,
        },
      });
      setStatusMessage('注册设置已保存');
      await refetch();
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '保存注册设置失败');
    }
  }

  function toggleManagementRole(role: string) {
    setManagementRoles((current) => (
      current.includes(role)
        ? current.filter((item) => item !== role)
        : [...current, role]
    ));
  }

  async function rotateInviteCodeNow() {
    try {
      const result = await rotateAdminInviteCode();
      setInviteCode(result.inviteCode);
      setStatusMessage(`已生成新邀请码：${result.inviteCode}`);
      await refetch();
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
      await refetch();
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '保存离线下载容量限制失败');
    }
  }

  function renderReadOnlyTab(title: string, description: string, rows: { label: string; value: string }[]) {
    return (
      <div>
        <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2">{title}</h3>
        <p className="text-sm text-text-muted-light dark:text-text-muted-dark mb-6">{description}</p>
        <div className="space-y-3">
          {rows.map((row) => (
            <div key={row.label} className="grid grid-cols-1 md:grid-cols-3 gap-3 border-b border-[#D9E3F2] dark:border-[#222233] pb-3">
              <span className="text-sm font-semibold text-text-primary-light dark:text-white">{row.label}</span>
              <span className="md:col-span-2 text-sm text-text-secondary-light dark:text-text-secondary-dark font-geist">{row.value}</span>
            </div>
          ))}
        </div>
      </div>
    );
  }

  function renderContent() {
    if (isLoading) {
      return <div className="p-8 text-center text-text-muted-light">加载中...</div>;
    }

    if (isError || !settings) {
      return <div className="p-8 text-center text-red-500">加载失败</div>;
    }

    if (activeTab === 'siteInfo') {
      return renderReadOnlyTab('站点信息', '当前后端只暴露站点配置能力状态，没有提供站点名称、描述和 URL 的写入接口。', [
        { label: '站点配置支持', value: settings.site.supported ? '已支持' : '未支持' },
        { label: '可写状态', value: settings.site.writeSupported ? '可写' : '只读' },
      ]);
    }

    if (activeTab === 'userSession') {
      return (
        <div>
          <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-6">用户与会话</h3>
          <form className="space-y-6" onSubmit={(event) => event.preventDefault()}>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="md:col-span-1">
                <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">邀请码注册</label>
                <p className="text-[13px] text-text-muted-light dark:text-text-muted-dark leading-relaxed font-geist">控制新用户注册是否必须填写当前邀请码。</p>
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
                  <span className="ml-3 text-sm font-medium text-text-secondary-light dark:text-text-secondary-dark">{inviteCodeRequired ? '开启' : '关闭'}</span>
                </label>
              </div>
            </div>

            <hr className="border-[#D9E3F2] dark:border-[#222233]" />

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="md:col-span-1">
                <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">邀请码</label>
                <p className="text-[13px] text-text-muted-light dark:text-text-muted-dark leading-relaxed font-geist">只保留生成入口，生成后会自动替换当前邀请码。</p>
              </div>
              <div className="md:col-span-2">
                <button type="button" className="bg-brand-light/10 text-brand-light px-4 py-3 rounded-lg text-sm font-semibold hover:bg-brand-light/20 transition-colors" onClick={() => void rotateInviteCodeNow()}>
                  生成邀请码
                </button>
              </div>
            </div>

            <hr className="border-[#D9E3F2] dark:border-[#222233]" />

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="md:col-span-1">
                <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">管理角色</label>
                <p className="text-[13px] text-text-muted-light dark:text-text-muted-dark leading-relaxed font-geist">选择哪些角色可以进入管理面板。</p>
              </div>
              <div className="md:col-span-2 flex flex-wrap gap-2">
                {managementRoleOptions.map((role) => {
                  const selected = managementRoles.includes(role.value);
                  return (
                    <button
                      key={role.value}
                      type="button"
                      className={`rounded-lg px-4 py-2 text-sm font-semibold transition-colors ${
                        selected
                          ? 'bg-brand-light text-white dark:bg-brand-dark'
                          : 'admin-secondary-button'
                      }`}
                      onClick={() => toggleManagementRole(role.value)}
                    >
                      {role.label}
                    </button>
                  );
                })}
              </div>
            </div>

            <hr className="border-[#D9E3F2] dark:border-[#222233]" />

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="md:col-span-1">
                <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">离线下载容量限制</label>
                <p className="text-[13px] text-text-muted-light dark:text-text-muted-dark leading-relaxed font-geist">当前值：{formatBytes(settings.transfer.offlineTransferStorageLimitBytes)}</p>
              </div>
              <div className="md:col-span-2 flex gap-2">
                <input type="number" min={1} className="input-field flex-1" value={offlineLimit} onChange={(event) => setOfflineLimit(event.target.value)} />
                <button type="button" className="admin-secondary-button px-4 rounded-lg text-sm font-semibold" onClick={() => void saveOfflineLimit()}>
                  保存限制
                </button>
              </div>
            </div>

            <div className="pt-6 flex justify-end">
              <button type="button" className="btn-primary flex items-center gap-2" onClick={() => void saveRegistration()}>
                <Save size={18} /> 保存注册设置
              </button>
            </div>
          </form>
        </div>
      );
    }

    if (activeTab === 'media') {
      return renderReadOnlyTab('媒体处理', '当前后端只暴露媒体处理运行状态，暂未提供写入端点。', [
        { label: '元数据提取', value: settings.mediaProcessing.metadataExtractionEnabled ? '开启' : '关闭' },
        { label: '缩略图生成', value: settings.mediaProcessing.thumbnailGenerationEnabled ? '开启' : '关闭' },
        { label: '视频封面', value: settings.mediaProcessing.videoPosterEnabled ? '开启' : '关闭' },
      ]);
    }

    if (activeTab === 'queue') {
      return renderReadOnlyTab('队列', '队列配置当前为只读快照。', [
        { label: '队列后端', value: formatRuntimeValue(settings.queue.backend) },
        { label: '媒体任务间隔', value: `${settings.queue.mediaMetadataFixedDelayMs} ms` },
        { label: '媒体任务初始延迟', value: `${settings.queue.mediaMetadataInitialDelayMs} ms` },
      ]);
    }

    if (activeTab === 'server') {
      return renderReadOnlyTab('服务', '服务配置当前为只读快照。', [
        { label: '存储提供方', value: formatRuntimeValue(settings.server.storageProvider) },
        { label: 'Redis', value: settings.server.redisEnabled ? '已启用' : '未启用' },
      ]);
    }

    return (
      <div className="flex flex-col items-center justify-center py-20 text-text-muted-light dark:text-text-muted-dark">
        <Settings size={48} className="mb-4 opacity-50" />
        <p className="text-lg font-medium">{tabs.find((tab) => tab.id === activeTab)?.label} 暂无可写后端接口</p>
        <p className="text-sm mt-2">该按钮已从假表单切换为明确占位，避免误以为已保存。</p>
      </div>
    );
  }

  return (
    <AdminLayout title="参数设置">
      <div className="flex flex-col lg:flex-row gap-8">
        <aside className="w-full lg:w-64 flex-shrink-0">
          <div className="card-container p-2">
            <nav className="flex flex-row lg:flex-col gap-1 overflow-x-auto lg:overflow-x-visible pb-2 lg:pb-0 scrollbar-hide">
              {tabs.map((tab) => (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-all duration-200 whitespace-nowrap text-sm font-medium ${
                    activeTab === tab.id
                      ? 'bg-brand-light text-white dark:bg-brand-dark shadow-md'
                      : 'text-text-secondary-light dark:text-text-secondary-dark hover:bg-black/5 dark:hover:bg-white/5 hover:text-text-primary-light dark:hover:text-text-primary-dark'
                  }`}
                >
                  {tab.icon}
                  {tab.label}
                </button>
              ))}
            </nav>
          </div>
        </aside>

        <div className="flex-1 max-w-4xl">
          {statusMessage && (
            <div className="mb-4 rounded-lg border border-[#D9E3F2] dark:border-[#222233] bg-card-light dark:bg-[#111117] px-4 py-3 text-sm text-text-secondary-light dark:text-text-secondary-dark">
              {statusMessage}
            </div>
          )}
          <div className="card-container p-8 animate-fade-in-up">{renderContent()}</div>
        </div>
      </div>
    </AdminLayout>
  );
};

export default AdminSetting;
