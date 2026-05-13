import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { 
  User, 
  Mail, 
  Phone, 
  BookOpen, 
  Languages, 
  Lock, 
  LogOut, 
  Camera,
  Loader2,
  CheckCircle2,
  AlertCircle,
} from 'lucide-react';
import DashboardLayout from '../components/DashboardLayout';
import WebDavAccessPanel from '../components/webdav/WebDavAccessPanel';
import { getSession } from '../lib/session';
import { changePassword, getProfile, logout, updateProfile, uploadAvatar, type UpdateProfilePayload, type ChangePasswordPayload } from '../lib/auth';
import { getFileViewerConfig } from '../lib/files';
import { getUserSettings, updateUserSettings } from '../lib/user-settings';
import { clearDefaultViewerPreference, setDefaultViewerPreference } from '../lib/file-open-preferences';
import { getViewersForExtension } from '../lib/file-viewers';
import { useUploadQueue } from '../hooks/useUploadQueue';
import { useNavigate } from 'react-router-dom';
import clsx from 'clsx';

const AccountSettings: React.FC = () => {
  const session = getSession();
  const user = session?.user;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { setUploadConcurrency } = useUploadQueue();

  const [profileData, setProfileData] = useState<UpdateProfilePayload>({
    displayName: user?.displayName || '',
    email: user?.email || '',
    phoneNumber: user?.phoneNumber || '',
    bio: user?.bio || '',
    preferredLanguage: user?.preferredLanguage || 'zh-CN',
  });

  const [passwordData, setPasswordData] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  const [message, setMessage] = useState<{ type: 'success' | 'error', text: string } | null>(null);
  const [selectedOpenWithExtension, setSelectedOpenWithExtension] = useState('md');
  const [uploadConcurrencyDraft, setUploadConcurrencyDraft] = useState(2);

  const { data: accountSettings } = useQuery({
    queryKey: ['userSettings'],
    queryFn: () => getUserSettings(),
  });

  const { data: viewerConfig } = useQuery({
    queryKey: ['fileViewerConfig'],
    queryFn: getFileViewerConfig,
  });

  const supportedOpenWithExtensions = useMemo(() => {
    return Object.keys(viewerConfig?.defaultViewerMapping ?? {}).sort((a, b) => a.localeCompare(b));
  }, [viewerConfig]);

  const selectedExtensionViewers = useMemo(() => {
    if (!viewerConfig) {
      return [];
    }
    return getViewersForExtension(viewerConfig, selectedOpenWithExtension);
  }, [selectedOpenWithExtension, viewerConfig]);

  const fileDefaultRows = useMemo(() => {
    const defaults = accountSettings?.defaultOpenWithByExt ?? {};
    return Object.entries(defaults)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([extension, viewerId]) => ({
        extension,
        viewerId,
        viewerName: viewerConfig?.fileViewers.find((viewer) => viewer.id === viewerId)?.displayName ?? viewerId,
      }));
  }, [accountSettings, viewerConfig]);

  useEffect(() => {
    getProfile()
      .then((profile) => {
        setProfileData({
          displayName: profile.displayName || '',
          email: profile.email || '',
          phoneNumber: profile.phoneNumber || '',
          bio: profile.bio || '',
          preferredLanguage: profile.preferredLanguage || 'zh-CN',
        });
      })
      .catch(() => {
        // keep session snapshot as fallback
      });
  }, []);

  useEffect(() => {
    if (accountSettings?.uploadConcurrency != null) {
      setUploadConcurrencyDraft(accountSettings.uploadConcurrency);
    }
  }, [accountSettings?.uploadConcurrency]);

  const profileMutation = useMutation({
    mutationFn: updateProfile,
    onSuccess: () => {
      setMessage({ type: 'success', text: '个人资料已更新' });
      setTimeout(() => setMessage(null), 3000);
    },
    onError: (error: any) => {
      setMessage({ type: 'error', text: error.message || '更新失败' });
    }
  });

  const passwordMutation = useMutation({
    mutationFn: (data: ChangePasswordPayload) => changePassword(data),
    onSuccess: () => {
      setMessage({ type: 'success', text: '密码已修改' });
      setPasswordData({ currentPassword: '', newPassword: '', confirmPassword: '' });
      setTimeout(() => setMessage(null), 3000);
    },
    onError: (error: any) => {
      setMessage({ type: 'error', text: error.message || '修改失败' });
    }
  });

  const avatarMutation = useMutation({
    mutationFn: uploadAvatar,
    onSuccess: () => {
      setMessage({ type: 'success', text: '头像已上传' });
      setTimeout(() => setMessage(null), 3000);
    },
    onError: (error: any) => {
      setMessage({ type: 'error', text: error.message || '上传失败' });
    }
  });

  const fileSettingsMutation = useMutation({
    mutationFn: updateUserSettings,
    onSuccess: (settings) => {
      queryClient.setQueryData(['userSettings'], settings);
      setUploadConcurrency(settings.uploadConcurrency ?? 2);
      setUploadConcurrencyDraft(settings.uploadConcurrency ?? 2);
      setMessage({ type: 'success', text: '文件设置已更新' });
      setTimeout(() => setMessage(null), 3000);
    },
    onError: (error: any) => {
      setMessage({ type: 'error', text: error.message || '更新文件设置失败' });
    }
  });

  const handleProfileSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    profileMutation.mutate(profileData);
  };

  const handlePasswordSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (passwordData.newPassword !== passwordData.confirmPassword) {
      setMessage({ type: 'error', text: '两次输入的新密码不一致' });
      return;
    }
    passwordMutation.mutate({
      currentPassword: passwordData.currentPassword,
      newPassword: passwordData.newPassword,
    });
  };

  const handleAvatarChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      if (!file.type.startsWith('image/')) {
        setMessage({ type: 'error', text: '只能上传图片文件' });
        e.target.value = '';
        return;
      }
      if (file.size > 5 * 1024 * 1024) {
        setMessage({ type: 'error', text: '图片大小不能超过 5MB' });
        e.target.value = '';
        return;
      }
      avatarMutation.mutate(file);
      e.target.value = '';
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const handleSetOpenWithDefault = (viewerId: string) => {
    fileSettingsMutation.mutate({
      defaultOpenWithByExt: setDefaultViewerPreference(
        accountSettings?.defaultOpenWithByExt,
        selectedOpenWithExtension,
        viewerId,
      ),
    });
  };

  const handleClearOpenWithDefault = (extension: string) => {
    fileSettingsMutation.mutate({
      defaultOpenWithByExt: clearDefaultViewerPreference(accountSettings?.defaultOpenWithByExt, extension),
    });
  };

  const handleClearAllOpenWithDefaults = () => {
    fileSettingsMutation.mutate({ defaultOpenWithByExt: {} });
  };

  const handleUploadConcurrencySave = () => {
    fileSettingsMutation.mutate({ uploadConcurrency: uploadConcurrencyDraft });
  };

  if (!user) return null;

  return (
    <DashboardLayout title="个人设置">
      <div className="h-full overflow-y-auto pr-2 custom-scrollbar">
        <div className="max-w-4xl space-y-6 pb-12">
          
          {message && (
            <div className={clsx(
              "flex items-center gap-3 rounded-2xl border p-4 transition-all",
              message.type === 'success' 
                ? "border-emerald-500/20 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400" 
                : "border-red-500/20 bg-red-500/10 text-red-600 dark:text-red-400"
            )}>
              {message.type === 'success' ? <CheckCircle2 size={20} /> : <AlertCircle size={20} />}
              <span className="text-sm font-medium">{message.text}</span>
            </div>
          )}

          {/* User Summary */}
          <section className="rounded-3xl border border-white/50 bg-white/80 p-6 shadow-sm dark:border-white/5 dark:bg-[#161922]/80">
            <div className="flex flex-col items-center gap-6 sm:flex-row">
              <div className="relative group">
                <div className="h-24 w-24 overflow-hidden rounded-full border-4 border-white shadow-md dark:border-slate-800">
                  {user.avatarUrl ? (
                    <img src={user.avatarUrl} alt={user.displayName || user.username} className="h-full w-full object-cover" />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center bg-slate-100 text-slate-400 dark:bg-slate-800">
                      <User size={40} />
                    </div>
                  )}
                </div>
                <button 
                  onClick={() => fileInputRef.current?.click()}
                  disabled={avatarMutation.isPending}
                  className="absolute bottom-0 right-0 flex h-8 w-8 items-center justify-center rounded-full bg-blue-600 text-white shadow-lg transition-transform hover:scale-110 disabled:opacity-50"
                >
                  {avatarMutation.isPending ? <Loader2 size={16} className="animate-spin" /> : <Camera size={16} />}
                </button>
                <input 
                  type="file" 
                  ref={fileInputRef} 
                  onChange={handleAvatarChange} 
                  accept="image/*" 
                  className="hidden" 
                />
              </div>
              <div className="flex-1 text-center sm:text-left">
                <h2 className="text-2xl font-bold text-slate-900 dark:text-white">
                  {user.displayName || user.username}
                </h2>
                <p className="text-slate-500 dark:text-slate-400">@{user.username} · {user.role}</p>
                <div className="mt-2 flex flex-wrap justify-center gap-4 sm:justify-start">
                  <span className="text-xs font-medium text-slate-400">ID: {user.id}</span>
                  <span className="text-xs font-medium text-slate-400">注册于: {new Date(user.createdAt).toLocaleDateString()}</span>
                </div>
              </div>
            </div>
          </section>

          {/* Profile Edit */}
          <section className="rounded-3xl border border-white/50 bg-white/80 p-8 shadow-sm dark:border-white/5 dark:bg-[#161922]/80">
            <div className="mb-6 flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-500/10 text-blue-600">
                <User size={20} />
              </div>
              <h3 className="text-lg font-bold text-slate-900 dark:text-white">基本资料</h3>
            </div>
            
            <form onSubmit={handleProfileSubmit} className="space-y-6">
              <div className="grid gap-6 md:grid-cols-2">
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-700 dark:text-slate-300">用户名 (不可更改)</label>
                  <input 
                    type="text" 
                    value={user.username} 
                    disabled 
                    className="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-slate-500 dark:border-slate-800 dark:bg-slate-900/50"
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-700 dark:text-slate-300">显示名称</label>
                  <input 
                    type="text" 
                    value={profileData.displayName || ''} 
                    onChange={e => setProfileData({...profileData, displayName: e.target.value})}
                    className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                    placeholder="你的昵称"
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-700 dark:text-slate-300">电子邮箱</label>
                  <div className="relative">
                    <Mail size={18} className="absolute left-4 top-3.5 text-slate-400" />
                    <input 
                      type="email" 
                      value={profileData.email || ''} 
                      onChange={e => setProfileData({...profileData, email: e.target.value})}
                      className="w-full rounded-2xl border border-slate-200 bg-white pl-11 pr-4 py-3 text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                      placeholder="email@example.com"
                    />
                  </div>
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-700 dark:text-slate-300">手机号码</label>
                  <div className="relative">
                    <Phone size={18} className="absolute left-4 top-3.5 text-slate-400" />
                    <input 
                      type="tel" 
                      value={profileData.phoneNumber || ''} 
                      onChange={e => setProfileData({...profileData, phoneNumber: e.target.value})}
                      className="w-full rounded-2xl border border-slate-200 bg-white pl-11 pr-4 py-3 text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                      placeholder="13800138000"
                    />
                  </div>
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-700 dark:text-slate-300">首选语言</label>
                  <div className="relative">
                    <Languages size={18} className="absolute left-4 top-3.5 text-slate-400" />
                    <select 
                      value={profileData.preferredLanguage || 'zh-CN'} 
                      onChange={e => setProfileData({...profileData, preferredLanguage: e.target.value})}
                      className="w-full appearance-none rounded-2xl border border-slate-200 bg-white pl-11 pr-4 py-3 text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                    >
                      <option value="zh-CN">简体中文</option>
                      <option value="en-US">English</option>
                    </select>
                  </div>
                </div>
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">个人简介</label>
                <div className="relative">
                  <BookOpen size={18} className="absolute left-4 top-3.5 text-slate-400" />
                  <textarea 
                    value={profileData.bio || ''} 
                    onChange={e => setProfileData({...profileData, bio: e.target.value})}
                    rows={3}
                    className="w-full rounded-2xl border border-slate-200 bg-white pl-11 pr-4 py-3 text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                    placeholder="介绍一下你自己..."
                  />
                </div>
              </div>
              <div className="flex justify-end">
                <button 
                  type="submit" 
                  disabled={profileMutation.isPending}
                  className="flex items-center gap-2 rounded-2xl bg-blue-600 px-8 py-3 font-semibold text-white transition-all hover:bg-blue-700 active:scale-95 disabled:opacity-50"
                >
                  {profileMutation.isPending && <Loader2 size={18} className="animate-spin" />}
                  保存修改
                </button>
              </div>
            </form>
          </section>

          {/* Password Change */}
          <section className="rounded-3xl border border-white/50 bg-white/80 p-8 shadow-sm dark:border-white/5 dark:bg-[#161922]/80">
            <div className="mb-6 flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-500/10 text-amber-600">
                <Lock size={20} />
              </div>
              <h3 className="text-lg font-bold text-slate-900 dark:text-white">修改密码</h3>
            </div>
            
            <form onSubmit={handlePasswordSubmit} className="space-y-6">
              <div className="grid gap-6 md:grid-cols-3">
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-700 dark:text-slate-300">当前密码</label>
                  <input 
                    type="password" 
                    required
                    value={passwordData.currentPassword}
                    onChange={e => setPasswordData({...passwordData, currentPassword: e.target.value})}
                    className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-700 dark:text-slate-300">新密码</label>
                  <input 
                    type="password" 
                    required
                    value={passwordData.newPassword}
                    onChange={e => setPasswordData({...passwordData, newPassword: e.target.value})}
                    className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-700 dark:text-slate-300">确认新密码</label>
                  <input 
                    type="password" 
                    required
                    value={passwordData.confirmPassword}
                    onChange={e => setPasswordData({...passwordData, confirmPassword: e.target.value})}
                    className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                  />
                </div>
              </div>
              <div className="flex justify-end">
                <button 
                  type="submit" 
                  disabled={passwordMutation.isPending}
                  className="flex items-center gap-2 rounded-2xl bg-amber-600 px-8 py-3 font-semibold text-white transition-all hover:bg-amber-700 active:scale-95 disabled:opacity-50"
                >
                  {passwordMutation.isPending && <Loader2 size={18} className="animate-spin" />}
                  重置密码
                </button>
              </div>
            </form>
          </section>

          <WebDavAccessPanel username={user.username} onMessage={setMessage} />

          {/* File Defaults */}
          <section className="rounded-3xl border border-white/50 bg-white/80 p-8 shadow-sm dark:border-white/5 dark:bg-[#161922]/80">
            <div className="mb-6 flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-600">
                <BookOpen size={20} />
              </div>
              <h3 className="text-lg font-bold text-slate-900 dark:text-white">文件</h3>
            </div>

            <div className="grid gap-4 md:grid-cols-[1fr_1fr_auto]">
              <div className="space-y-2">
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">文件扩展名</label>
                <select
                  value={selectedOpenWithExtension}
                  onChange={e => setSelectedOpenWithExtension(e.target.value)}
                  className="w-full appearance-none rounded-2xl border border-slate-200 bg-white px-4 py-3 text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                >
                  {(supportedOpenWithExtensions.length > 0 ? supportedOpenWithExtensions : ['md', 'txt', 'drawio', 'excalidraw']).map((extension) => (
                    <option key={extension} value={extension}>.{extension}</option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">默认打开方式</label>
                <select
                  value={accountSettings?.defaultOpenWithByExt?.[selectedOpenWithExtension] ?? ''}
                  onChange={e => handleSetOpenWithDefault(e.target.value)}
                  disabled={selectedExtensionViewers.length === 0 || fileSettingsMutation.isPending}
                  className="w-full appearance-none rounded-2xl border border-slate-200 bg-white px-4 py-3 text-slate-900 focus:border-blue-500 focus:outline-none disabled:opacity-50 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                >
                  <option value="" disabled>选择打开方式</option>
                  {selectedExtensionViewers.map((viewer) => (
                    <option key={viewer.id} value={viewer.id}>{viewer.displayName}</option>
                  ))}
                </select>
              </div>
              <div className="flex items-end">
                <button
                  type="button"
                  disabled={fileDefaultRows.length === 0 || fileSettingsMutation.isPending}
                  onClick={handleClearAllOpenWithDefaults}
                  className="w-full rounded-2xl border border-slate-200 px-5 py-3 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900"
                >
                  清除全部
                </button>
              </div>
            </div>

            <div className="mt-6 grid gap-4 md:grid-cols-[1fr_auto]">
              <div className="space-y-2">
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">上传线程数</label>
                <select
                  value={uploadConcurrencyDraft}
                  onChange={e => setUploadConcurrencyDraft(Number(e.target.value))}
                  className="w-full appearance-none rounded-2xl border border-slate-200 bg-white px-4 py-3 text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                >
                  {Array.from({ length: 8 }, (_, index) => index + 1).map((value) => (
                    <option key={value} value={value}>{value} 线程</option>
                  ))}
                </select>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  默认双线程上传，修改后会立即用于新的上传任务。
                </p>
              </div>
              <div className="flex items-end">
                <button
                  type="button"
                  disabled={fileSettingsMutation.isPending || uploadConcurrencyDraft === (accountSettings?.uploadConcurrency ?? 2)}
                  onClick={handleUploadConcurrencySave}
                  className="w-full rounded-2xl bg-blue-600 px-5 py-3 text-sm font-semibold text-white transition-colors hover:bg-blue-700 disabled:opacity-50"
                >
                  保存线程数
                </button>
              </div>
            </div>

            <div className="mt-5 overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-800">
              {fileDefaultRows.length === 0 ? (
                <div className="px-4 py-5 text-sm text-slate-500 dark:text-slate-400">暂无已保存的默认打开方式。</div>
              ) : (
                fileDefaultRows.map((row) => (
                  <div
                    key={row.extension}
                    className="flex items-center justify-between gap-4 border-b border-slate-200 px-4 py-3 last:border-b-0 dark:border-slate-800"
                  >
                    <div>
                      <p className="text-sm font-semibold text-slate-900 dark:text-white">.{row.extension}</p>
                      <p className="text-xs text-slate-500 dark:text-slate-400">{row.viewerName}</p>
                    </div>
                    <button
                      type="button"
                      disabled={fileSettingsMutation.isPending}
                      onClick={() => handleClearOpenWithDefault(row.extension)}
                      className="rounded-xl px-3 py-2 text-sm font-semibold text-red-600 transition-colors hover:bg-red-500/10 disabled:opacity-50 dark:text-red-400"
                    >
                      清除
                    </button>
                  </div>
                ))
              )}
            </div>
          </section>

          {/* Dangerous Zone */}
          <section className="rounded-3xl border border-red-500/20 bg-red-500/5 p-8 dark:bg-red-500/10">
            <div className="flex flex-col items-center justify-between gap-4 sm:flex-row">
              <div>
                <h3 className="text-lg font-bold text-red-600 dark:text-red-400">退出登录</h3>
                <p className="text-sm text-red-500/80">退出当前账号并清除浏览器本地缓存。</p>
              </div>
              <button 
                onClick={handleLogout}
                className="flex items-center gap-2 rounded-2xl bg-red-600 px-8 py-3 font-semibold text-white transition-all hover:bg-red-700 active:scale-95"
              >
                <LogOut size={18} />
                安全退出
              </button>
            </div>
          </section>
        </div>
      </div>
    </DashboardLayout>
  );
};

export default AccountSettings;
