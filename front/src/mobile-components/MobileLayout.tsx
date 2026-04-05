import React, { ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  FolderOpen,
  Key,
  LayoutDashboard,
  LogOut,
  Mail,
  Send,
  Settings,
  Shield,
  Smartphone,
  X,
  Menu,
} from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';

import { useAuth } from '@/src/auth/AuthProvider';
import { apiBinaryUploadRequest, apiDownload, apiRequest, apiUploadRequest } from '@/src/lib/api';
import { isNativeAppShellLocation } from '@/src/lib/app-shell';
import { createSession, readStoredSession, saveStoredSession } from '@/src/lib/session';
import type { AuthResponse, InitiateUploadResponse, UserProfile } from '@/src/lib/types';
import { cn } from '@/src/lib/utils';
import { Button } from '@/src/components/ui/button';
import { Input } from '@/src/components/ui/input';

import { buildAccountDraft, getRoleLabel, shouldLoadAvatarWithAuth } from '@/src/components/layout/account-utils';
import { UploadProgressPanel } from '@/src/components/layout/UploadProgressPanel';

const NAV_ITEMS = [
  { name: '总览', path: '/overview', icon: LayoutDashboard },
  { name: '网盘', path: '/files', icon: FolderOpen },
  { name: '快传', path: '/transfer', icon: Send },
] as const;

export function isNativeMobileShellLocation(location: Location | URL | null) {
  return isNativeAppShellLocation(location);
}

export function getMobileViewportOffsetClassNames(isNativeShell = false) {
  if (isNativeShell) {
    return {
      header: 'safe-area-pt pt-6',
      main: 'pt-[calc(3.5rem+1.5rem+var(--app-safe-area-top))]',
    };
  }

  return {
    header: 'safe-area-pt',
    main: 'pt-[calc(3.5rem+var(--app-safe-area-top))]',
  };
}

export function getMobileUploadPanelOffsetClassName(isNativeShell = false) {
  if (isNativeShell) {
    return 'top-[calc(3.5rem+1rem+var(--app-safe-area-top))]';
  }

  return 'top-[calc(3.5rem+0.75rem+var(--app-safe-area-top))]';
}

type ActiveModal = 'security' | 'settings' | null;

export function getVisibleNavItems(isAdmin: boolean) {
  // 底部导航栏容量有限，后台页面可通过顶部头像菜单或者折叠菜单进入
  return NAV_ITEMS;
}

interface LayoutProps {
  children?: ReactNode;
}

export function MobileLayout({ children }: LayoutProps = {}) {
  const navigate = useNavigate();
  const { isAdmin, logout, refreshProfile, user } = useAuth();
  const navItems = getVisibleNavItems(isAdmin);
  const isNativeShell = typeof window !== 'undefined' && isNativeMobileShellLocation(window.location);
  const viewportOffsets = getMobileViewportOffsetClassNames(
    isNativeShell,
  );
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [activeModal, setActiveModal] = useState<ActiveModal>(null);
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState<string | null>(null);
  const [selectedAvatarFile, setSelectedAvatarFile] = useState<File | null>(null);
  const [avatarSourceUrl, setAvatarSourceUrl] = useState<string | null>(user?.avatarUrl ?? null);
  const [profileDraft, setProfileDraft] = useState(() =>
    buildAccountDraft(
      user ?? {
        id: 0,
        username: '',
        email: '',
        createdAt: '',
      },
    ),
  );
  
  // States related to modales and profile editing (Same as original)
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [profileMessage, setProfileMessage] = useState('');
  const [passwordMessage, setPasswordMessage] = useState('');
  const [profileError, setProfileError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [profileSubmitting, setProfileSubmitting] = useState(false);
  const [passwordSubmitting, setPasswordSubmitting] = useState(false);

  useEffect(() => {
    if (!user) return;
    setProfileDraft(buildAccountDraft(user));
  }, [user]);

  useEffect(() => {
    if (!avatarPreviewUrl) return undefined;
    return () => URL.revokeObjectURL(avatarPreviewUrl);
  }, [avatarPreviewUrl]);

  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;
    async function syncAvatar() {
      if (!user?.avatarUrl) {
        if (active) setAvatarSourceUrl(null);
        return;
      }
      if (!shouldLoadAvatarWithAuth(user.avatarUrl)) {
        if (active) setAvatarSourceUrl(user.avatarUrl);
        return;
      }
      try {
        const response = await apiDownload(user.avatarUrl);
        const blob = await response.blob();
        objectUrl = URL.createObjectURL(blob);
        if (active) setAvatarSourceUrl(objectUrl);
      } catch {
        if (active) setAvatarSourceUrl(null);
      }
    }
    void syncAvatar();
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [user?.avatarUrl]);

  const displayName = useMemo(() => user?.displayName || user?.username || '账户', [user]);
  const email = user?.email || '暂无邮箱';
  const phoneNumber = user?.phoneNumber || '未设置手机号';
  const roleLabel = getRoleLabel(user?.role);
  const avatarFallback = (displayName || 'Y').charAt(0).toUpperCase();
  const displayedAvatarUrl = avatarPreviewUrl || avatarSourceUrl;

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const handleAvatarClick = () => fileInputRef.current?.click();

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setSelectedAvatarFile(file);
    setAvatarPreviewUrl((current) => {
      if (current) URL.revokeObjectURL(current);
      return URL.createObjectURL(file);
    });
  };

  const handleProfileDraftChange = (field: keyof typeof profileDraft, value: string) => {
    setProfileDraft((current) => ({ ...current, [field]: value }));
  };

  const closeModal = () => {
    setActiveModal(null);
    setProfileMessage(''); setProfileError('');
    setPasswordMessage(''); setPasswordError('');
  };

  const persistSessionUser = (nextProfile: UserProfile) => {
    const currentSession = readStoredSession();
    if (!currentSession) return;
    saveStoredSession({ ...currentSession, user: nextProfile });
  };

  const uploadAvatar = async (file: File) => {
    const initiated = await apiRequest<InitiateUploadResponse>('/user/avatar/upload/initiate', {
      method: 'POST',
      body: { filename: file.name, contentType: file.type || 'image/png', size: file.size },
    });
    if (initiated.direct) {
      try {
        await apiBinaryUploadRequest(initiated.uploadUrl, { method: initiated.method, headers: initiated.headers, body: file });
      } catch {
        const formData = new FormData();
        formData.append('file', file);
        await apiUploadRequest<void>(`/user/avatar/upload?storageName=${encodeURIComponent(initiated.storageName)}`, { body: formData });
      }
    } else {
      const formData = new FormData();
      formData.append('file', file);
      await apiUploadRequest<void>(initiated.uploadUrl, { body: formData, method: initiated.method === 'PUT' ? 'PUT' : 'POST', headers: initiated.headers });
    }
    const nextProfile = await apiRequest<UserProfile>('/user/avatar/upload/complete', {
      method: 'POST',
      body: { filename: file.name, contentType: file.type || 'image/png', size: file.size, storageName: initiated.storageName },
    });
    persistSessionUser(nextProfile);
    return nextProfile;
  };

  const handleSaveProfile = async () => {
    setProfileSubmitting(true); setProfileMessage(''); setProfileError('');
    try {
      if (selectedAvatarFile) await uploadAvatar(selectedAvatarFile);
      const nextProfile = await apiRequest<UserProfile>('/user/profile', {
        method: 'PUT',
        body: {
          displayName: profileDraft.displayName.trim(), email: profileDraft.email.trim(),
          phoneNumber: profileDraft.phoneNumber.trim(), bio: profileDraft.bio,
          preferredLanguage: profileDraft.preferredLanguage,
        },
      });
      persistSessionUser(nextProfile);
      await refreshProfile();
      setSelectedAvatarFile(null);
      setAvatarPreviewUrl((current) => { if (current) URL.revokeObjectURL(current); return null; });
      setProfileMessage('资料已保存');
    } catch (error) {
      setProfileError(error instanceof Error ? error.message : '保存失败');
    } finally {
      setProfileSubmitting(false);
    }
  };

  const handleChangePassword = async () => {
    setPasswordMessage(''); setPasswordError('');
    if (newPassword !== confirmPassword) { setPasswordError('密码不一致'); return; }
    setPasswordSubmitting(true);
    try {
      const auth = await apiRequest<AuthResponse>('/user/password', {
        method: 'POST', body: { currentPassword, newPassword },
      });
      const currentSession = readStoredSession();
      if (currentSession) {
        saveStoredSession({ ...currentSession, ...createSession(auth), user: auth.user });
      }
      setCurrentPassword(''); setNewPassword(''); setConfirmPassword('');
      setPasswordMessage('密码已更新');
    } catch (error) {
      setPasswordError(error instanceof Error ? error.message : '修改失败');
    } finally {
      setPasswordSubmitting(false);
    }
  };

  return (
    <div className="flex flex-col h-[100dvh] w-full bg-[#07101D] text-white relative overflow-hidden">
      {/* Background Animated Blobs */}
      <div className="fixed inset-0 z-0 pointer-events-none">
        <div className="absolute top-0 left-[-20%] w-[60%] h-[40%] rounded-full bg-[#336EFF] opacity-20 mix-blend-screen blur-[100px] animate-pulse" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[70%] h-[50%] rounded-full bg-purple-600 opacity-20 mix-blend-screen blur-[100px]" />
      </div>

      {/* Top App Bar */}
      <header className={cn("fixed top-0 left-0 right-0 z-40 w-full glass-panel border-b border-white/5 bg-[#07101D]/70 backdrop-blur-2xl", viewportOffsets.header)}>
        <div className="flex items-center justify-between px-4 h-14">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-[#336EFF] to-blue-400 flex items-center justify-center shadow-lg">
              <span className="text-white font-bold text-sm leading-none">Y</span>
            </div>
            <span className="text-white font-bold text-sm tracking-wider">优立云盘</span>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => setIsDropdownOpen(true)}
              className="w-8 h-8 rounded-full bg-slate-800 border border-white/10 flex items-center justify-center overflow-hidden"
            >
              {displayedAvatarUrl ? (
                <img src={displayedAvatarUrl} alt="Avatar" className="w-full h-full object-cover" />
              ) : (
                <span className="text-xs font-semibold">{avatarFallback}</span>
              )}
            </button>
          </div>
        </div>
      </header>

      <AnimatePresence>
        {isDropdownOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm"
              onClick={() => setIsDropdownOpen(false)}
            />
            <motion.div
              initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }}
              transition={{ type: 'spring', damping: 25, stiffness: 200 }}
              className="fixed bottom-0 left-0 right-0 z-50 glass-panel bg-[#0f172a]/95 rounded-t-3xl border-t border-white/10 pt-2 pb-8 px-4"
            >
              <div className="w-12 h-1 bg-white/20 rounded-full mx-auto mb-6" />
              
              <div className="flex items-center gap-4 mb-6 p-4 rounded-2xl bg-white/5 border border-white/10">
                <div className="w-14 h-14 rounded-full overflow-hidden bg-slate-800">
                  {displayedAvatarUrl ? <img src={displayedAvatarUrl} className="w-full h-full object-cover" /> : <div className="w-full h-full flex items-center justify-center text-xl">{avatarFallback}</div>}
                </div>
                <div>
                  <div className="font-semibold text-lg">{displayName}</div>
                  <div className="text-sm text-slate-400">{email}</div>
                  <div className="text-xs px-2 py-0.5 mt-1 rounded bg-[#336EFF]/20 text-[#336EFF] inline-block">{roleLabel}</div>
                </div>
              </div>

              <div className="space-y-2">
                <button onClick={() => { setIsDropdownOpen(false); setActiveModal('settings'); }} className="w-full flex items-center gap-3 p-4 rounded-xl hover:bg-white/5 active:bg-white/10 transition-colors">
                  <Settings className="w-5 h-5 text-slate-300" /> <span>账户设置</span>
                </button>
                <button onClick={() => { setIsDropdownOpen(false); setActiveModal('security'); }} className="w-full flex items-center gap-3 p-4 rounded-xl hover:bg-white/5 active:bg-white/10 transition-colors">
                  <Shield className="w-5 h-5 text-slate-300" /> <span>安全中心</span>
                </button>
                {isAdmin && (
                  <button onClick={() => { setIsDropdownOpen(false); navigate('/admin'); }} className="w-full flex items-center gap-3 p-4 rounded-xl hover:bg-white/5 active:bg-white/10 transition-colors">
                    <Shield className="w-5 h-5 text-purple-400" /> <span className="text-purple-400">管理后台</span>
                  </button>
                )}
                <button onClick={handleLogout} className="w-full flex items-center gap-3 p-4 rounded-xl hover:bg-red-500/10 active:bg-red-500/20 text-red-400 transition-colors mt-2">
                  <LogOut className="w-5 h-5" /> <span>退出登录</span>
                </button>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>

      {/* Main Content Area */}
      <main className={cn("flex-1 w-full overflow-y-auto overflow-x-hidden pb-16 z-10", viewportOffsets.main)}>
        {children ?? <Outlet />}
      </main>

      {/* Upload Panel (Floating above bottom bar) */}
      <div className={cn('fixed left-3 right-3 z-40 pointer-events-none', getMobileUploadPanelOffsetClassName(isNativeShell))}>
        <div className="pointer-events-auto">
          <UploadProgressPanel variant="mobile" />
        </div>
      </div>

      {/* Bottom Navigation Bar */}
      <nav className="fixed bottom-0 left-0 right-0 z-40 glass-panel border-t border-white/5 bg-[#0f172a]/90 backdrop-blur-2xl safe-area-pb">
        <div className="flex items-center justify-around h-16 px-2">
          {navItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                cn(
                  'flex flex-col items-center justify-center w-16 h-full gap-1 transition-colors',
                  isActive ? 'text-[#336EFF]' : 'text-slate-400 hover:text-slate-200'
                )
              }
            >
              {({ isActive }) => (
                <>
                  <item.icon className={cn('w-6 h-6', isActive && 'fill-current opacity-20')} />
                  <span className="text-[10px] font-medium">{item.name}</span>
                </>
              )}
            </NavLink>
          ))}
        </div>
      </nav>

      {/* Support Modals (Settings & Security) */}
      <AnimatePresence>
        {activeModal === 'security' && (
          <div className="fixed inset-0 z-[100] flex flex-col bg-[#07101D]">
            <div className="glass-panel border-b border-white/10 h-14 flex items-center justify-between px-4 shrink-0">
              <div className="flex items-center gap-2 text-white"><Shield className="w-5 h-5 text-emerald-400"/> 安全中心</div>
              <button onClick={closeModal} className="p-2"><X className="w-5 h-5"/></button>
            </div>
            <div className="flex-1 overflow-y-auto p-4 space-y-6">
              {/* Similar to original but vertically stacked without hover constraints */}
              <div className="p-4 rounded-xl glass-panel space-y-4">
                <div className="flex items-center gap-3">
                  <Key className="w-5 h-5 text-blue-400" /> <span className="font-medium text-white">修改密码</span>
                </div>
                <Input type="password" placeholder="当前密码" value={currentPassword} onChange={e=>setCurrentPassword(e.target.value)} className="bg-black/20" />
                <Input type="password" placeholder="新密码" value={newPassword} onChange={e=>setNewPassword(e.target.value)} className="bg-black/20" />
                <Input type="password" placeholder="确认新密码" value={confirmPassword} onChange={e=>setConfirmPassword(e.target.value)} className="bg-black/20" />
                <Button className="w-full" onClick={()=>void handleChangePassword()} disabled={passwordSubmitting}>{passwordSubmitting?'处理中':'更新密码'}</Button>
                {passwordError && <p className="text-sm text-red-400">{passwordError}</p>}
                {passwordMessage && <p className="text-sm text-emerald-400">{passwordMessage}</p>}
              </div>

              <div className="p-4 rounded-xl glass-panel flex flex-col gap-2">
                <div className="text-white text-sm font-medium">手机绑定</div>
                <div className="text-xs text-slate-400">{phoneNumber}</div>
              </div>
            </div>
          </div>
        )}

        {activeModal === 'settings' && (
          <div className="fixed inset-0 z-[100] flex flex-col bg-[#07101D]">
            <div className="glass-panel border-b border-white/10 h-14 flex items-center justify-between px-4 shrink-0">
              <div className="flex items-center gap-2 text-white"><Settings className="w-5 h-5 text-[#336EFF]"/> 账户设置</div>
              <button onClick={closeModal} className="p-2"><X className="w-5 h-5"/></button>
            </div>
            <div className="flex-1 overflow-y-auto p-4 space-y-6">
              <div className="flex flex-col items-center gap-4 py-4 glass-panel rounded-2xl">
                <div onClick={handleAvatarClick} className="w-24 h-24 rounded-full overflow-hidden bg-slate-800 relative">
                  {displayedAvatarUrl ? <img src={displayedAvatarUrl} className="w-full h-full object-cover"/> : <div className="w-full h-full flex items-center justify-center text-3xl">{avatarFallback}</div>}
                  <div className="absolute inset-0 bg-black/40 flex items-center justify-center"><span className="text-xs text-white">更换</span></div>
                  <input type="file" ref={fileInputRef} onChange={handleFileChange} accept="image/*" className="hidden" />
                </div>
                <div className="text-center">
                  <div className="font-semibold text-white">{displayName}</div>
                  <div className="text-sm text-slate-400">{roleLabel}</div>
                </div>
              </div>

              <div className="space-y-4">
                <div>
                  <label className="text-xs text-slate-400 pl-1">昵称</label>
                  <Input value={profileDraft.displayName} onChange={e=>handleProfileDraftChange('displayName', e.target.value)} className="bg-black/20 mt-1" />
                </div>
                <div>
                  <label className="text-xs text-slate-400 pl-1">邮箱</label>
                  <Input type="email" value={profileDraft.email} onChange={e=>handleProfileDraftChange('email', e.target.value)} className="bg-black/20 mt-1" />
                </div>
                <div>
                  <label className="text-xs text-slate-400 pl-1">手机号</label>
                  <Input type="tel" value={profileDraft.phoneNumber} onChange={e=>handleProfileDraftChange('phoneNumber', e.target.value)} className="bg-black/20 mt-1" />
                </div>
                <div>
                  <label className="text-xs text-slate-400 pl-1">个人简介</label>
                  <textarea value={profileDraft.bio} onChange={e=>handleProfileDraftChange('bio', e.target.value)} className="w-full min-h-[80px] rounded-md bg-black/20 border-white/10 text-white p-3 text-sm resize-none mt-1" />
                </div>
              </div>

              {profileError && <p className="text-sm text-red-400">{profileError}</p>}
              {profileMessage && <p className="text-sm text-emerald-400">{profileMessage}</p>}

              <Button className="w-full py-6" onClick={()=>void handleSaveProfile()} disabled={profileSubmitting}>{profileSubmitting?'保存中':'保存修改'}</Button>
              <div className="h-8" />
            </div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
