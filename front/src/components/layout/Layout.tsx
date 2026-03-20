import React, { ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  Gamepad2,
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
} from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';

import { useAuth } from '@/src/auth/AuthProvider';
import { apiBinaryUploadRequest, apiDownload, apiRequest, apiUploadRequest } from '@/src/lib/api';
import { createSession, readStoredSession, saveStoredSession } from '@/src/lib/session';
import type { AuthResponse, InitiateUploadResponse, UserProfile } from '@/src/lib/types';
import { cn } from '@/src/lib/utils';
import { Button } from '@/src/components/ui/button';
import { Input } from '@/src/components/ui/input';

import { buildAccountDraft, getRoleLabel, shouldLoadAvatarWithAuth } from './account-utils';

const NAV_ITEMS = [
  { name: '总览', path: '/overview', icon: LayoutDashboard },
  { name: '网盘', path: '/files', icon: FolderOpen },
  { name: '快传', path: '/transfer', icon: Send },
  { name: '游戏', path: '/games', icon: Gamepad2 },
  { name: '后台', path: '/admin', icon: Shield },
] as const;

type ActiveModal = 'security' | 'settings' | null;

export function getVisibleNavItems(isAdmin: boolean) {
  return NAV_ITEMS.filter((item) => isAdmin || item.path !== '/admin');
}

interface LayoutProps {
  children?: ReactNode;
}

export function Layout({ children }: LayoutProps = {}) {
  const navigate = useNavigate();
  const { isAdmin, logout, refreshProfile, user } = useAuth();
  const navItems = getVisibleNavItems(isAdmin);
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
    if (!user) {
      return;
    }
    setProfileDraft(buildAccountDraft(user));
  }, [user]);

  useEffect(() => {
    if (!avatarPreviewUrl) {
      return undefined;
    }

    return () => {
      URL.revokeObjectURL(avatarPreviewUrl);
    };
  }, [avatarPreviewUrl]);

  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;

    async function syncAvatar() {
      if (!user?.avatarUrl) {
        if (active) {
          setAvatarSourceUrl(null);
        }
        return;
      }

      if (!shouldLoadAvatarWithAuth(user.avatarUrl)) {
        if (active) {
          setAvatarSourceUrl(user.avatarUrl);
        }
        return;
      }

      try {
        const response = await apiDownload(user.avatarUrl);
        const blob = await response.blob();
        objectUrl = URL.createObjectURL(blob);
        if (active) {
          setAvatarSourceUrl(objectUrl);
        }
      } catch {
        if (active) {
          setAvatarSourceUrl(null);
        }
      }
    }

    void syncAvatar();

    return () => {
      active = false;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [user?.avatarUrl]);

  const displayName = useMemo(() => {
    if (!user) {
      return '账户';
    }
    return user.displayName || user.username;
  }, [user]);

  const email = user?.email || '暂无邮箱';
  const phoneNumber = user?.phoneNumber || '未设置手机号';
  const roleLabel = getRoleLabel(user?.role);
  const avatarFallback = (displayName || 'Y').charAt(0).toUpperCase();
  const displayedAvatarUrl = avatarPreviewUrl || avatarSourceUrl;

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const handleAvatarClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    setSelectedAvatarFile(file);
    setAvatarPreviewUrl((current) => {
      if (current) {
        URL.revokeObjectURL(current);
      }
      return URL.createObjectURL(file);
    });
  };

  const handleProfileDraftChange = (field: keyof typeof profileDraft, value: string) => {
    setProfileDraft((current) => ({
      ...current,
      [field]: value,
    }));
  };

  const closeModal = () => {
    setActiveModal(null);
    setProfileMessage('');
    setProfileError('');
    setPasswordMessage('');
    setPasswordError('');
  };

  const persistSessionUser = (nextProfile: UserProfile) => {
    const currentSession = readStoredSession();
    if (!currentSession) {
      return;
    }

    saveStoredSession({
      ...currentSession,
      user: nextProfile,
    });
  };

  const uploadAvatar = async (file: File) => {
    const initiated = await apiRequest<InitiateUploadResponse>('/user/avatar/upload/initiate', {
      method: 'POST',
      body: {
        filename: file.name,
        contentType: file.type || 'image/png',
        size: file.size,
      },
    });

    if (initiated.direct) {
      try {
        await apiBinaryUploadRequest(initiated.uploadUrl, {
          method: initiated.method,
          headers: initiated.headers,
          body: file,
        });
      } catch {
        const formData = new FormData();
        formData.append('file', file);
        await apiUploadRequest<void>(`/user/avatar/upload?storageName=${encodeURIComponent(initiated.storageName)}`, {
          body: formData,
        });
      }
    } else {
      const formData = new FormData();
      formData.append('file', file);
      await apiUploadRequest<void>(initiated.uploadUrl, {
        body: formData,
        method: initiated.method === 'PUT' ? 'PUT' : 'POST',
        headers: initiated.headers,
      });
    }

    const nextProfile = await apiRequest<UserProfile>('/user/avatar/upload/complete', {
      method: 'POST',
      body: {
        filename: file.name,
        contentType: file.type || 'image/png',
        size: file.size,
        storageName: initiated.storageName,
      },
    });

    persistSessionUser(nextProfile);
    return nextProfile;
  };

  const handleSaveProfile = async () => {
    setProfileSubmitting(true);
    setProfileMessage('');
    setProfileError('');

    try {
      if (selectedAvatarFile) {
        await uploadAvatar(selectedAvatarFile);
      }

      const nextProfile = await apiRequest<UserProfile>('/user/profile', {
        method: 'PUT',
        body: {
          displayName: profileDraft.displayName.trim(),
          email: profileDraft.email.trim(),
          phoneNumber: profileDraft.phoneNumber.trim(),
          bio: profileDraft.bio,
          preferredLanguage: profileDraft.preferredLanguage,
        },
      });

      persistSessionUser(nextProfile);

      await refreshProfile();
      setSelectedAvatarFile(null);
      setAvatarPreviewUrl((current) => {
        if (current) {
          URL.revokeObjectURL(current);
        }
        return null;
      });
      setProfileMessage('账户资料已保存');
    } catch (error) {
      setProfileError(error instanceof Error ? error.message : '账户资料保存失败');
    } finally {
      setProfileSubmitting(false);
    }
  };

  const handleChangePassword = async () => {
    setPasswordMessage('');
    setPasswordError('');

    if (newPassword !== confirmPassword) {
      setPasswordError('两次输入的新密码不一致');
      return;
    }

    setPasswordSubmitting(true);
    try {
      const auth = await apiRequest<AuthResponse>('/user/password', {
        method: 'POST',
        body: {
          currentPassword,
          newPassword,
        },
      });

      const currentSession = readStoredSession();
      if (currentSession) {
        saveStoredSession({
          ...currentSession,
          ...createSession(auth),
          user: auth.user,
        });
      }

      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setPasswordMessage('密码已更新，当前登录态已同步刷新');
    } catch (error) {
      setPasswordError(error instanceof Error ? error.message : '密码修改失败');
    } finally {
      setPasswordSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-[#07101D] text-white relative overflow-hidden">
      <div className="fixed inset-0 z-0 pointer-events-none">
        <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] rounded-full bg-[#336EFF] opacity-20 mix-blend-screen blur-[120px] animate-blob" />
        <div className="absolute top-[20%] right-[-10%] w-[50%] h-[50%] rounded-full bg-purple-600 opacity-20 mix-blend-screen blur-[120px] animate-blob animation-delay-2000" />
        <div className="absolute bottom-[-20%] left-[20%] w-[60%] h-[60%] rounded-full bg-indigo-600 opacity-20 mix-blend-screen blur-[120px] animate-blob animation-delay-4000" />
      </div>

      <header className="fixed top-0 left-0 right-0 z-50 w-full glass-panel border-b border-white/10 bg-[#07101D]/60 backdrop-blur-xl">
        <div className="container mx-auto px-4 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-[#336EFF] to-blue-400 flex items-center justify-center shadow-lg shadow-[#336EFF]/20">
              <span className="text-white font-bold text-lg leading-none">Y</span>
            </div>
            <div className="flex flex-col">
              <span className="text-white font-bold text-sm tracking-wider">YOYUZH.XYZ</span>
              <span className="text-slate-400 text-[10px] uppercase tracking-widest">Personal Portal</span>
            </div>
          </div>

          <nav className="hidden md:flex items-center gap-2">
            {navItems.map((item) => (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition-all duration-200 relative overflow-hidden group',
                    isActive ? 'text-white shadow-md shadow-[#336EFF]/20' : 'text-slate-400 hover:text-white hover:bg-white/5',
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    {isActive && <div className="absolute inset-0 bg-[#336EFF] opacity-100 z-0" />}
                    <item.icon className="w-4 h-4 relative z-10" />
                    <span className="relative z-10">{item.name}</span>
                  </>
                )}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-4 relative">
            <button
              onClick={() => setIsDropdownOpen((current) => !current)}
              className="w-10 h-10 rounded-full bg-slate-800 border border-white/10 flex items-center justify-center text-slate-300 hover:text-white hover:border-white/20 transition-all relative z-10 overflow-hidden"
              aria-label="Account"
            >
              {displayedAvatarUrl ? (
                <img src={displayedAvatarUrl} alt="Avatar" className="w-full h-full object-cover" />
              ) : (
                <span className="text-sm font-semibold">{avatarFallback}</span>
              )}
            </button>

            <AnimatePresence>
              {isDropdownOpen && (
                <>
                  <div className="fixed inset-0 z-40" onClick={() => setIsDropdownOpen(false)} />
                  <motion.div
                    initial={{ opacity: 0, scale: 0.95, y: 10 }}
                    animate={{ opacity: 1, scale: 1, y: 0 }}
                    exit={{ opacity: 0, scale: 0.95, y: 10 }}
                    transition={{ duration: 0.15 }}
                    className="absolute right-0 top-full mt-2 w-56 bg-[#0f172a] border border-white/10 rounded-xl shadow-2xl z-50 py-2 overflow-hidden"
                  >
                    <div className="px-4 py-3 border-b border-white/10 mb-2">
                      <p className="text-sm font-medium text-white">{displayName}</p>
                      <p className="text-xs text-slate-400 truncate">{email}</p>
                    </div>

                    <button
                      onClick={() => {
                        setActiveModal('security');
                        setIsDropdownOpen(false);
                      }}
                      className="w-full text-left px-4 py-2 text-sm text-slate-300 hover:bg-white/10 hover:text-white flex items-center gap-3 transition-colors"
                    >
                      <Shield className="w-4 h-4" /> 安全中心
                    </button>
                    <button
                      onClick={() => {
                        setActiveModal('settings');
                        setIsDropdownOpen(false);
                      }}
                      className="w-full text-left px-4 py-2 text-sm text-slate-300 hover:bg-white/10 hover:text-white flex items-center gap-3 transition-colors"
                    >
                      <Settings className="w-4 h-4" /> 账户设置
                    </button>

                    <div className="h-px bg-white/10 my-2" />

                    <button
                      onClick={handleLogout}
                      className="w-full text-left px-4 py-2 text-sm text-red-400 hover:bg-red-500/10 hover:text-red-300 flex items-center gap-3 transition-colors"
                    >
                      <LogOut className="w-4 h-4" /> 退出登录
                    </button>
                  </motion.div>
                </>
              )}
            </AnimatePresence>
          </div>
        </div>
      </header>

      <main className="flex-1 container mx-auto px-4 pt-24 pb-8 relative z-10">
        {children ?? <Outlet />}
      </main>

      <AnimatePresence>
        {activeModal === 'security' && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              className="bg-[#0f172a] border border-white/10 rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden flex flex-col max-h-[80vh]"
            >
              <div className="p-5 border-b border-white/10 flex justify-between items-center bg-white/5">
                <h3 className="text-lg font-semibold text-white flex items-center gap-2">
                  <Shield className="w-5 h-5 text-emerald-400" />
                  安全中心
                </h3>
                <button onClick={closeModal} className="text-slate-400 hover:text-white transition-colors p-1 rounded-md hover:bg-white/10">
                  <X className="w-5 h-5" />
                </button>
              </div>
              <div className="p-6 overflow-y-auto space-y-6">
                <div className="space-y-4">
                  <div className="p-4 rounded-xl bg-white/5 border border-white/10 space-y-4">
                    <div className="flex items-center gap-4">
                      <div className="w-10 h-10 rounded-full bg-blue-500/20 flex items-center justify-center">
                        <Key className="w-5 h-5 text-blue-400" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-white">登录密码</p>
                        <p className="text-xs text-slate-400 mt-0.5">密码修改后会刷新当前登录凭据并使旧 refresh token 失效</p>
                      </div>
                    </div>

                    <div className="grid gap-3">
                      <Input
                        type="password"
                        placeholder="当前密码"
                        value={currentPassword}
                        onChange={(event) => setCurrentPassword(event.target.value)}
                        className="bg-black/20 border-white/10"
                      />
                      <Input
                        type="password"
                        placeholder="新密码"
                        value={newPassword}
                        onChange={(event) => setNewPassword(event.target.value)}
                        className="bg-black/20 border-white/10"
                      />
                      <Input
                        type="password"
                        placeholder="确认新密码"
                        value={confirmPassword}
                        onChange={(event) => setConfirmPassword(event.target.value)}
                        className="bg-black/20 border-white/10"
                      />
                      <div className="flex justify-end">
                        <Button variant="outline" disabled={passwordSubmitting} onClick={() => void handleChangePassword()}>
                          {passwordSubmitting ? '保存中...' : '修改'}
                        </Button>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center justify-between p-4 rounded-xl bg-white/5 border border-white/10">
                    <div className="flex items-center gap-4">
                      <div className="w-10 h-10 rounded-full bg-emerald-500/20 flex items-center justify-center">
                        <Smartphone className="w-5 h-5 text-emerald-400" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-white">手机绑定</p>
                        <p className="text-xs text-slate-400 mt-0.5">当前手机号：{phoneNumber}</p>
                      </div>
                    </div>
                    <Button
                      variant="outline"
                      className="border-white/10 hover:bg-white/10 text-slate-300"
                      onClick={() => setActiveModal('settings')}
                    >
                      更改
                    </Button>
                  </div>

                  <div className="flex items-center justify-between p-4 rounded-xl bg-white/5 border border-white/10">
                    <div className="flex items-center gap-4">
                      <div className="w-10 h-10 rounded-full bg-purple-500/20 flex items-center justify-center">
                        <Mail className="w-5 h-5 text-purple-400" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-white">邮箱绑定</p>
                        <p className="text-xs text-slate-400 mt-0.5">当前邮箱：{email}</p>
                      </div>
                    </div>
                    <Button
                      variant="outline"
                      className="border-white/10 hover:bg-white/10 text-slate-300"
                      onClick={() => setActiveModal('settings')}
                    >
                      更改
                    </Button>
                  </div>
                </div>

                {passwordError && <p className="text-sm text-rose-300">{passwordError}</p>}
                {passwordMessage && <p className="text-sm text-emerald-300">{passwordMessage}</p>}
              </div>
            </motion.div>
          </div>
        )}

        {activeModal === 'settings' && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              className="bg-[#0f172a] border border-white/10 rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden flex flex-col max-h-[80vh]"
            >
              <div className="p-5 border-b border-white/10 flex justify-between items-center bg-white/5">
                <h3 className="text-lg font-semibold text-white flex items-center gap-2">
                  <Settings className="w-5 h-5 text-[#336EFF]" />
                  账户设置
                </h3>
                <button onClick={closeModal} className="text-slate-400 hover:text-white transition-colors p-1 rounded-md hover:bg-white/10">
                  <X className="w-5 h-5" />
                </button>
              </div>
              <div className="p-6 overflow-y-auto space-y-6">
                <div className="flex items-center gap-6 pb-6 border-b border-white/10">
                  <div className="relative group cursor-pointer" onClick={handleAvatarClick}>
                    <div className="w-20 h-20 rounded-full bg-gradient-to-br from-[#336EFF] to-blue-400 flex items-center justify-center text-2xl font-bold text-white shadow-lg overflow-hidden">
                      {displayedAvatarUrl ? <img src={displayedAvatarUrl} alt="Avatar" className="w-full h-full object-cover" /> : avatarFallback}
                    </div>
                    <div className="absolute inset-0 bg-black/50 rounded-full opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                      <span className="text-xs text-white">{selectedAvatarFile ? '等待保存' : '更换头像'}</span>
                    </div>
                    <input type="file" ref={fileInputRef} onChange={handleFileChange} accept="image/*" className="hidden" />
                  </div>
                  <div className="flex-1 space-y-1">
                    <h4 className="text-lg font-medium text-white">{displayName}</h4>
                    <p className="text-sm text-slate-400">{roleLabel}</p>
                  </div>
                </div>

                <div className="space-y-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium text-slate-300">昵称</label>
                    <Input
                      value={profileDraft.displayName}
                      onChange={(event) => handleProfileDraftChange('displayName', event.target.value)}
                      className="bg-black/20 border-white/10 text-white focus-visible:ring-[#336EFF]"
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium text-slate-300">邮箱</label>
                    <Input
                      type="email"
                      value={profileDraft.email}
                      onChange={(event) => handleProfileDraftChange('email', event.target.value)}
                      className="bg-black/20 border-white/10 text-white focus-visible:ring-[#336EFF]"
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium text-slate-300">手机号</label>
                    <Input
                      type="tel"
                      value={profileDraft.phoneNumber}
                      onChange={(event) => handleProfileDraftChange('phoneNumber', event.target.value)}
                      className="bg-black/20 border-white/10 text-white focus-visible:ring-[#336EFF]"
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium text-slate-300">个人简介</label>
                    <textarea
                      className="w-full min-h-[100px] rounded-md bg-black/20 border border-white/10 text-white p-3 text-sm focus:outline-none focus:ring-2 focus:ring-[#336EFF] resize-none"
                      value={profileDraft.bio}
                      onChange={(event) => handleProfileDraftChange('bio', event.target.value)}
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium text-slate-300">语言偏好</label>
                    <select
                      className="w-full rounded-md bg-black/20 border border-white/10 text-white p-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#336EFF] appearance-none"
                      value={profileDraft.preferredLanguage}
                      onChange={(event) => handleProfileDraftChange('preferredLanguage', event.target.value)}
                    >
                      <option value="zh-CN">简体中文</option>
                      <option value="en-US">English</option>
                    </select>
                  </div>
                </div>

                {profileError && <p className="text-sm text-rose-300">{profileError}</p>}
                {profileMessage && <p className="text-sm text-emerald-300">{profileMessage}</p>}

                <div className="pt-4 flex justify-end gap-3">
                  <Button variant="outline" onClick={closeModal} className="border-white/10 hover:bg-white/10 text-slate-300">
                    取消
                  </Button>
                  <Button variant="default" disabled={profileSubmitting} onClick={() => void handleSaveProfile()}>
                    {profileSubmitting ? '保存中...' : '保存更改'}
                  </Button>
                </div>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
