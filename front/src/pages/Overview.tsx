import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { useNavigate } from 'react-router-dom';
import {
  ChevronRight,
  Clock,
  Database,
  FileText,
  FolderPlus,
  Mail,
  Send,
  Smartphone,
  Upload,
  User,
  Zap,
} from 'lucide-react';

import { shouldLoadAvatarWithAuth } from '@/src/components/layout/account-utils';
import { Button } from '@/src/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/src/components/ui/card';
import { FileTypeIcon, getFileTypeTheme } from '@/src/components/ui/FileTypeIcon';
import { AppPageShell } from '@/src/components/ui/AppPageShell';
import { PageToolbar } from '@/src/components/ui/PageToolbar';
import { apiDownload, apiRequest } from '@/src/lib/api';
import { readCachedValue, writeCachedValue } from '@/src/lib/cache';
import { resolveStoredFileType } from '@/src/lib/file-type';
import { getOverviewCacheKey } from '@/src/lib/page-cache';
import { clearPostLoginPending, hasPostLoginPending, readStoredSession } from '@/src/lib/session';
import type { FileMetadata, PageResponse, UserProfile } from '@/src/lib/types';

import {
  APK_DOWNLOAD_PATH,
  getDesktopOverviewSectionColumns,
  getDesktopOverviewStretchSection,
  getOverviewLoadErrorMessage,
  getOverviewStorageQuotaLabel,
  shouldShowOverviewApkDownload,
} from './overview-state';

function formatFileSize(size: number) {
  if (size <= 0) {
    return '0 B';
  }

  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1);
  const value = size / 1024 ** index;
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatRecentTime(value: string) {
  const date = new Date(value);
  const diffHours = Math.floor((Date.now() - date.getTime()) / (1000 * 60 * 60));
  if (diffHours < 24) {
    return `${Math.max(diffHours, 0)}小时前`;
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export default function Overview() {
  const navigate = useNavigate();
  const cachedOverview = readCachedValue<{
    profile: UserProfile | null;
    recentFiles: FileMetadata[];
    rootFiles: FileMetadata[];
  }>(getOverviewCacheKey());
  const [profile, setProfile] = useState<UserProfile | null>(cachedOverview?.profile ?? readStoredSession()?.user ?? null);
  const [recentFiles, setRecentFiles] = useState<FileMetadata[]>(cachedOverview?.recentFiles ?? []);
  const [rootFiles, setRootFiles] = useState<FileMetadata[]>(cachedOverview?.rootFiles ?? []);
  const [loadingError, setLoadingError] = useState('');
  const [retryToken, setRetryToken] = useState(0);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);

  const currentHour = new Date().getHours();
  let greeting = '晚上好';
  if (currentHour < 6) greeting = '凌晨好';
  else if (currentHour < 12) greeting = '早上好';
  else if (currentHour < 18) greeting = '下午好';

  const currentTime = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  const recentWeekUploads = recentFiles.filter(
    (file) => Date.now() - new Date(file.createdAt).getTime() <= 7 * 24 * 60 * 60 * 1000,
  ).length;
  const usedBytes = useMemo(
    () => rootFiles.filter((file) => !file.directory).reduce((sum, file) => sum + file.size, 0),
    [rootFiles],
  );
  const storageQuotaBytes = profile?.storageQuotaBytes && profile.storageQuotaBytes > 0
    ? profile.storageQuotaBytes
    : 50 * 1024 * 1024 * 1024;
  const usedGb = usedBytes / 1024 / 1024 / 1024;
  const storagePercent = Math.min((usedBytes / storageQuotaBytes) * 100, 100);
  const latestFile = recentFiles[0] ?? null;
  const profileDisplayName = profile?.displayName || profile?.username || '未登录';
  const profileAvatarFallback = profileDisplayName.charAt(0).toUpperCase();
  const showApkDownload = shouldShowOverviewApkDownload();
  const desktopSections = getDesktopOverviewSectionColumns(showApkDownload);
  const desktopStretchSection = getDesktopOverviewStretchSection(showApkDownload);

  useEffect(() => {
    let cancelled = false;

    async function loadOverview() {
      const pendingAfterLogin = hasPostLoginPending();
      setLoadingError('');

      try {
        const [userResult, recentResult, rootResult] = await Promise.allSettled([
          apiRequest<UserProfile>('/user/profile'),
          apiRequest<FileMetadata[]>('/files/recent'),
          apiRequest<PageResponse<FileMetadata>>('/files/list?path=%2F&page=0&size=100'),
        ]);

        const failures = [userResult, recentResult, rootResult].filter((result) => result.status === 'rejected');

        if (cancelled) {
          return;
        }

        const nextProfile = userResult.status === 'fulfilled' ? userResult.value : profile;
        const nextRecentFiles = recentResult.status === 'fulfilled' ? recentResult.value : recentFiles;
        const nextRootFiles = rootResult.status === 'fulfilled' ? rootResult.value.items : rootFiles;

        setProfile(nextProfile);
        setRecentFiles(nextRecentFiles);
        setRootFiles(nextRootFiles);
        writeCachedValue(getOverviewCacheKey(), {
          profile: nextProfile,
          recentFiles: nextRecentFiles,
          rootFiles: nextRootFiles,
        });

        if (failures.length > 0) {
          setLoadingError(getOverviewLoadErrorMessage(pendingAfterLogin));
        } else {
          clearPostLoginPending();
        }
      } catch {
        if (!cancelled) {
          setLoadingError(getOverviewLoadErrorMessage(pendingAfterLogin));
        }
      }
    }

    void loadOverview();

    return () => {
      cancelled = true;
    };
  }, [retryToken]);

  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;

    async function loadAvatar() {
      if (!profile?.avatarUrl) {
        if (active) {
          setAvatarUrl(null);
        }
        return;
      }

      if (!shouldLoadAvatarWithAuth(profile.avatarUrl)) {
        if (active) {
          setAvatarUrl(profile.avatarUrl);
        }
        return;
      }

      try {
        const response = await apiDownload(profile.avatarUrl);
        const blob = await response.blob();
        objectUrl = URL.createObjectURL(blob);
        if (active) {
          setAvatarUrl(objectUrl);
        }
      } catch {
        if (active) {
          setAvatarUrl(null);
        }
      }
    }

    void loadAvatar();

    return () => {
      active = false;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [profile?.avatarUrl]);

  return (
    <AppPageShell toolbar={<PageToolbar title={`总览 · ${greeting}，${profile?.username ?? '访客'}`} />}>
      <div className="p-4 md:p-6 space-y-6 relative z-10">

      {loadingError ? (
        <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
          <Card className="border-amber-400/20 bg-amber-500/10">
            <CardContent className="flex flex-col gap-3 p-4 text-sm text-amber-100 md:flex-row md:items-center md:justify-between">
              <span>{loadingError}</span>
              <Button variant="outline" size="sm" onClick={() => setRetryToken((value) => value + 1)}>
                重新加载总览
              </Button>
            </CardContent>
          </Card>
        </motion.div>
      ) : null}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard title="网盘文件总数" value={`${rootFiles.length}`} desc="当前根目录统计" icon={FileText} delay={0.1} />
        <MetricCard
          title="最近 7 天上传"
          value={`${recentWeekUploads}`}
          desc={latestFile ? `最新更新于 ${formatRecentTime(latestFile.createdAt)}` : '暂无最近上传'}
          icon={Upload}
          delay={0.2}
        />
        <MetricCard
          title="快传入口"
          value={latestFile ? '就绪' : '待命'}
          desc="可随时生成临时取件码分享文件"
          icon={Send}
          delay={0.3}
        />
        <MetricCard
          title="存储占用"
          value={`${storagePercent.toFixed(1)}%`}
          desc={`${formatFileSize(usedBytes)} / ${formatFileSize(storageQuotaBytes)}`}
          icon={Database}
          delay={0.4}
        />
      </div>

      <div className="grid grid-cols-1 items-stretch lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 flex flex-col gap-6">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle>最近文件</CardTitle>
              <Button variant="ghost" size="sm" className="text-xs text-slate-400" onClick={() => navigate('/files')}>
                查看全部 <ChevronRight className="w-4 h-4 ml-1" />
              </Button>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                {recentFiles.slice(0, 3).map((file, index) => (
                  (() => {
                    const fileType = resolveStoredFileType({
                      filename: file.filename,
                      contentType: file.contentType,
                      directory: file.directory,
                    });

                    return (
                      <div
                        key={`${file.id}-${index}`}
                        className="flex items-center justify-between rounded-xl p-3 transition-colors cursor-pointer group hover:bg-white/5"
                        onClick={() => navigate('/files')}
                      >
                        <div className="flex items-center gap-4 overflow-hidden">
                          <FileTypeIcon type={fileType.kind} size="sm" className="group-hover:scale-[1.03] transition-transform duration-200" />
                          <div className="min-w-0 truncate">
                            <p className="truncate text-sm font-medium text-white">{file.filename}</p>
                            <div className="mt-1 flex flex-wrap items-center gap-2">
                              <span
                                className={`inline-flex rounded-full px-2 py-1 text-[11px] font-medium ${getFileTypeTheme(fileType.kind).badgeClassName}`}
                              >
                                {fileType.label}
                              </span>
                              <p className="text-xs text-slate-400">{formatRecentTime(file.createdAt)}</p>
                            </div>
                          </div>
                        </div>
                        <span className="ml-4 shrink-0 text-xs font-mono text-slate-500">
                          {file.directory ? '文件夹' : formatFileSize(file.size)}
                        </span>
                      </div>
                    );
                  })()
                ))}
                {recentFiles.length === 0 ? (
                  <div className="p-3 rounded-xl border border-dashed border-white/10 text-sm text-slate-500">
                    暂无最近文件
                  </div>
                ) : null}
              </div>
            </CardContent>
          </Card>

          <Card className={desktopStretchSection === 'transfer-workbench' ? 'flex-1 overflow-hidden' : 'overflow-hidden'}>
            <CardContent className="p-0">
              <div className="relative h-full overflow-hidden rounded-2xl bg-[radial-gradient(circle_at_top_left,rgba(51,110,255,0.22),transparent_45%),linear-gradient(135deg,rgba(15,23,42,0.94),rgba(15,23,42,0.8))] p-6">
                <div className="absolute -right-10 -top-10 h-32 w-32 rounded-full bg-cyan-400/10 blur-2xl" />
                <div className="relative z-10 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
                  <div className="space-y-3">
                    <div className="inline-flex items-center gap-2 rounded-full border border-cyan-400/20 bg-cyan-400/10 px-3 py-1 text-xs font-medium text-cyan-100">
                      <Zap className="h-3.5 w-3.5" />
                      新功能
                    </div>
                    <div>
                      <h3 className="text-2xl font-semibold text-white">P2P 快传工作台</h3>
                      <p className="mt-2 max-w-xl text-sm leading-6 text-slate-300">
                        现在可以直接从门户里生成取件码、复制分享链接，并在另一台设备上模拟接收流程。
                      </p>
                    </div>
                    <div className="flex flex-wrap gap-3 text-xs text-slate-400">
                      <span className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1">拖拽发送</span>
                      <span className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1">临时取件码</span>
                      <span className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1">浏览器接收</span>
                    </div>
                  </div>
                  <Button className="shrink-0" onClick={() => navigate('/transfer')}>
                    进入快传
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>

          {desktopSections.main.includes('apk-download') ? (
            <Card className={`${desktopStretchSection === 'apk-download' ? 'flex-1' : ''} overflow-hidden border-[#336EFF]/20 bg-[radial-gradient(circle_at_top_right,rgba(51,110,255,0.18),transparent_40%),linear-gradient(180deg,rgba(10,14,28,0.96),rgba(15,23,42,0.92))]`}>
              <CardContent className="h-full p-0">
                <div className="relative flex h-full flex-col overflow-hidden rounded-2xl p-6">
                  <div className="absolute -left-12 bottom-0 h-28 w-28 rounded-full bg-[#336EFF]/10 blur-3xl" />
                  <div className="relative z-10 flex h-full flex-col gap-5 md:flex-row md:items-end md:justify-between">
                    <div className="space-y-3">
                      <div className="inline-flex items-center gap-2 rounded-full border border-[#336EFF]/20 bg-[#336EFF]/10 px-3 py-1 text-xs font-medium text-[#b9ccff]">
                        <Smartphone className="h-3.5 w-3.5" />
                        Android 客户端
                      </div>
                      <div>
                        <h3 className="text-2xl font-semibold text-white">下载 APK 安装包</h3>
                        <p className="mt-2 max-w-xl text-sm leading-6 text-slate-300">
                          当前 Android 安装包通过独立发包链路维护，可直接从这里获取最新版本。
                        </p>
                      </div>
                      <div className="flex flex-wrap gap-3 text-xs text-slate-400">
                        <span className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1">后端分发</span>
                        <span className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1">独立发包</span>
                        <span className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1">一键下载</span>
                      </div>
                    </div>
                    <a
                      href={APK_DOWNLOAD_PATH}
                      className="inline-flex h-11 shrink-0 items-center justify-center rounded-xl bg-[#336EFF] px-6 text-sm font-medium text-white shadow-md shadow-[#336EFF]/20 transition-colors hover:bg-[#2958cc]"
                    >
                      下载 APK
                    </a>
                  </div>
                </div>
              </CardContent>
            </Card>
          ) : null}
        </div>

        <div className="flex flex-col gap-6">
          <Card>
            <CardHeader className="pb-4">
              <CardTitle>快捷操作</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-2 gap-3">
                <QuickAction icon={Upload} label="上传文件" onClick={() => navigate('/files')} />
                <QuickAction icon={FolderPlus} label="新建文件夹" onClick={() => navigate('/files')} />
                <QuickAction icon={Database} label="进入网盘" onClick={() => navigate('/files')} />
                <QuickAction icon={Send} label="打开快传" onClick={() => navigate('/transfer')} />
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-4">
              <CardTitle>存储空间</CardTitle>
            </CardHeader>
            <CardContent className="space-y-5">
              <div className="flex justify-between items-end">
                <div className="space-y-1">
                  <p className="text-3xl font-bold text-white tracking-tight">
                    {usedGb.toFixed(2)} <span className="text-sm text-slate-400 font-normal">GB</span>
                  </p>
                  <p className="text-xs text-slate-500 uppercase tracking-wider">{getOverviewStorageQuotaLabel(storageQuotaBytes)}</p>
                </div>
                <span className="text-xl font-mono text-[#336EFF] font-medium">{storagePercent.toFixed(1)}%</span>
              </div>
              <div className="h-2.5 w-full bg-black/40 rounded-full overflow-hidden shadow-inner">
                <div className="h-full bg-gradient-to-r from-[#336EFF] to-blue-400 rounded-full" style={{ width: `${storagePercent}%` }} />
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-4">
              <CardTitle>账号信息</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex items-center gap-4 p-4 rounded-xl bg-white/[0.02] border border-white/5">
                <div className="w-12 h-12 rounded-full bg-gradient-to-br from-indigo-500 to-cyan-500 flex items-center justify-center text-white font-bold text-xl shadow-lg overflow-hidden">
                  {avatarUrl ? <img src={avatarUrl} alt="Avatar" className="w-full h-full object-cover" /> : profileAvatarFallback}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-white truncate">{profileDisplayName}</p>
                  <p className="text-xs text-slate-400 truncate mt-0.5">{profile?.email ?? '暂无邮箱'}</p>
                </div>
              </div>
              <div className="mt-4 space-y-2 text-sm text-slate-400">
                <div className="flex items-center gap-2">
                  <User className="h-4 w-4 text-slate-500" />
                  <span>{profile?.username ?? '未登录'}</span>
                </div>
                <div className="flex items-center gap-2">
                  <Mail className="h-4 w-4 text-slate-500" />
                  <span>{profile?.email ?? '暂无邮箱'}</span>
                </div>
                <div className="flex items-center gap-2">
                  <Clock className="h-4 w-4 text-slate-500" />
                  <span>{latestFile ? `最近一次文件更新：${formatRecentTime(latestFile.createdAt)}` : '最近还没有文件变动'}</span>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
      </div>
    </AppPageShell>
  );
}

function MetricCard({
  title,
  value,
  desc,
  icon: Icon,
  delay,
}: {
  title: string;
  value: string;
  desc: string;
  icon: React.ComponentType<{ className?: string }>;
  delay: number;
}) {
  return (
    <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay }}>
      <Card className="h-full hover:bg-white/[0.04] transition-colors">
        <CardContent className="p-6 flex flex-col gap-4">
          <div className="flex justify-between items-start">
            <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-[#336EFF]/20 to-blue-500/10 flex items-center justify-center border border-[#336EFF]/20">
              <Icon className="w-6 h-6 text-[#336EFF]" />
            </div>
            <span className="text-3xl font-bold text-white tracking-tight">{value}</span>
          </div>
          <div className="mt-2">
            <p className="text-sm font-medium text-slate-300">{title}</p>
            <p className="text-xs text-slate-500 mt-1">{desc}</p>
          </div>
        </CardContent>
      </Card>
    </motion.div>
  );
}

function QuickAction({
  icon: Icon,
  label,
  onClick,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className="flex flex-col items-center justify-center gap-3 p-4 rounded-xl border border-white/5 bg-white/[0.02] hover:bg-white/[0.06] hover:border-white/10 transition-all group"
    >
      <Icon className="w-6 h-6 text-slate-400 group-hover:text-[#336EFF] transition-colors" />
      <span className="text-xs font-medium text-slate-300 group-hover:text-white transition-colors">{label}</span>
    </button>
  );
}
