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
  Upload,
  User,
  Zap,
} from 'lucide-react';

import { shouldLoadAvatarWithAuth } from '@/src/components/layout/account-utils';
import { Button } from '@/src/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/src/components/ui/card';
import { FileTypeIcon, getFileTypeTheme } from '@/src/components/ui/FileTypeIcon';
import { apiDownload, apiRequest } from '@/src/lib/api';
import { readCachedValue, writeCachedValue } from '@/src/lib/cache';
import { resolveStoredFileType } from '@/src/lib/file-type';
import { getOverviewCacheKey } from '@/src/lib/page-cache';
import { clearPostLoginPending, hasPostLoginPending, readStoredSession } from '@/src/lib/session';
import type { FileMetadata, PageResponse, UserProfile } from '@/src/lib/types';

import { getOverviewLoadErrorMessage } from '@/src/pages/overview-state';

function formatFileSize(size: number) {
  if (size <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1);
  const value = size / 1024 ** index;
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatRecentTime(value: string) {
  const date = new Date(value);
  const diffHours = Math.floor((Date.now() - date.getTime()) / (1000 * 60 * 60));
  if (diffHours < 24) return `${Math.max(diffHours, 0)}小时前`;
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(date);
}

export default function MobileOverview() {
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
  const recentWeekUploads = recentFiles.filter(f => Date.now() - new Date(f.createdAt).getTime() <= 7 * 24 * 60 * 60 * 1000).length;
  const usedBytes = useMemo(() => rootFiles.filter(f => !f.directory).reduce((sum, f) => sum + f.size, 0), [rootFiles]);
  const storageQuotaBytes = profile?.storageQuotaBytes && profile.storageQuotaBytes > 0 ? profile.storageQuotaBytes : 50 * 1024 * 1024 * 1024;
  const usedGb = usedBytes / 1024 / 1024 / 1024;
  const storagePercent = Math.min((usedBytes / storageQuotaBytes) * 100, 100);
  const latestFile = recentFiles[0] ?? null;
  const profileDisplayName = profile?.displayName || profile?.username || '未登录';
  const profileAvatarFallback = profileDisplayName.charAt(0).toUpperCase();

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
        const failures = [userResult, recentResult, rootResult].filter(r => r.status === 'rejected');
        if (cancelled) return;

        const nextProfile = userResult.status === 'fulfilled' ? userResult.value : profile;
        const nextRecentFiles = recentResult.status === 'fulfilled' ? recentResult.value : recentFiles;
        const nextRootFiles = rootResult.status === 'fulfilled' ? rootResult.value.items : rootFiles;

        setProfile(nextProfile); setRecentFiles(nextRecentFiles); setRootFiles(nextRootFiles);
        writeCachedValue(getOverviewCacheKey(), { profile: nextProfile, recentFiles: nextRecentFiles, rootFiles: nextRootFiles });

        if (failures.length > 0) setLoadingError(getOverviewLoadErrorMessage(pendingAfterLogin));
        else clearPostLoginPending();
      } catch {
        if (!cancelled) setLoadingError(getOverviewLoadErrorMessage(pendingAfterLogin));
      }
    }
    void loadOverview();
    return () => { cancelled = true; };
  }, [retryToken]);

  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;
    async function loadAvatar() {
      if (!profile?.avatarUrl) {
        if (active) setAvatarUrl(null);
        return;
      }
      if (!shouldLoadAvatarWithAuth(profile.avatarUrl)) {
        if (active) setAvatarUrl(profile.avatarUrl);
        return;
      }
      try {
        const response = await apiDownload(profile.avatarUrl);
        const blob = await response.blob();
        objectUrl = URL.createObjectURL(blob);
        if (active) setAvatarUrl(objectUrl);
      } catch {
        if (active) setAvatarUrl(null);
      }
    }
    void loadAvatar();
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [profile?.avatarUrl]);

  return (
    <div className="flex flex-col gap-4 px-4 pb-4">
      {/* 头部欢迎区域 */}
      <motion.div
        initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
        className="glass-panel p-5 rounded-2xl relative overflow-hidden"
      >
        <div className="absolute top-0 right-0 w-32 h-32 bg-[#336EFF] rounded-full mix-blend-screen filter blur-[60px] opacity-20" />
        <div className="relative z-10">
          <h1 className="text-xl font-bold text-white tracking-tight">
            欢迎，{profile?.username ?? '访客'}
          </h1>
          <p className="text-[#336EFF] font-medium text-xs mt-1">{currentTime} · {greeting}</p>
        </div>
      </motion.div>

      {loadingError ? (
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
          <Card className="border-amber-400/20 bg-amber-500/10 mb-2">
            <CardContent className="flex flex-col gap-3 p-4 text-xs text-amber-100">
              <span className="leading-tight">{loadingError}</span>
              <Button variant="outline" size="sm" className="w-full text-amber-200 border-amber-400/30 hover:bg-amber-400/20" onClick={() => setRetryToken((v) => v + 1)}>
                重新加载
              </Button>
            </CardContent>
          </Card>
        </motion.div>
      ) : null}

      {/* 核心指标网格：移动端改为 2x2 等宽 */}
      <div className="grid grid-cols-2 gap-3">
        <MobileMetricCard title="文件总数" value={`${rootFiles.length}`} icon={FileText} delay={0.1} color="text-amber-400" bg="bg-amber-500/20" />
        <MobileMetricCard title="近期上传" value={`${recentWeekUploads}`} icon={Upload} delay={0.15} color="text-emerald-400" bg="bg-emerald-500/20" />
        <MobileMetricCard title="快传就绪" value={latestFile ? '使用中' : '待命'} icon={Send} delay={0.2} color="text-[#336EFF]" bg="bg-[#336EFF]/20" />
        <MobileMetricCard title="存储占用" value={`${storagePercent.toFixed(1)}%`} icon={Database} delay={0.25} color="text-purple-400" bg="bg-purple-500/20" subtitle={`${formatFileSize(usedBytes)}`} />
      </div>

      {/* 快捷操作区 */}
      <Card className="glass-panel mt-2">
        <CardHeader className="py-3 px-4 pb-2 border-b border-white/5">
          <CardTitle className="text-sm font-medium">快捷操作</CardTitle>
        </CardHeader>
        <CardContent className="p-4 grid grid-cols-4 gap-2">
          <QuickAction icon={Upload} label="上传" onClick={() => navigate('/files')} />
          <QuickAction icon={FolderPlus} label="建目录" onClick={() => navigate('/files')} />
          <QuickAction icon={Database} label="网盘" onClick={() => navigate('/files')} />
          <QuickAction icon={Send} label="快传" onClick={() => navigate('/transfer')} />
        </CardContent>
      </Card>

      {/* 近期文件 (精简版) */}
      <Card className="glass-panel">
        <CardHeader className="flex flex-row items-center justify-between py-3 px-4 pb-2 border-b border-white/5">
          <CardTitle className="text-sm font-medium">最近文件</CardTitle>
          <button className="text-xs text-slate-400 flex items-center" onClick={() => navigate('/files')}>
            全部 <ChevronRight className="w-3 h-3" />
          </button>
        </CardHeader>
        <CardContent className="p-2">
          <div className="space-y-1">
            {recentFiles.slice(0, 3).map((file, index) => {
              const fileType = resolveStoredFileType({ filename: file.filename, contentType: file.contentType, directory: file.directory });
              return (
                <div key={`${file.id}-${index}`} className="flex items-center justify-between rounded-lg p-2 hover:bg-white/5" onClick={() => navigate('/files')}>
                  <div className="flex items-center gap-3 overflow-hidden">
                    <FileTypeIcon type={fileType.kind} size="sm" />
                    <div className="min-w-0 truncate">
                      <p className="truncate text-xs font-medium text-white">{file.filename}</p>
                      <p className="text-[10px] text-slate-400 mt-0.5">{formatRecentTime(file.createdAt)}</p>
                    </div>
                  </div>
                  <span className="ml-2 shrink-0 text-[10px] font-mono text-slate-500">{(file.directory ? '目录' : formatFileSize(file.size))}</span>
                </div>
              );
            })}
            {recentFiles.length === 0 && <div className="p-3 text-center text-xs text-slate-500">暂无动态</div>}
          </div>
        </CardContent>
      </Card>

      {/* 快传推荐横幅 */}
      <Card className="glass-panel overflow-hidden border-cyan-500/20 relative" onClick={() => navigate('/transfer')}>
        <div className="absolute inset-0 bg-gradient-to-r from-cyan-900/40 to-blue-900/40" />
        <CardContent className="relative z-10 p-4 flex items-center justify-between">
          <div className="space-y-1">
            <div className="flex items-center gap-1.5 pt-1">
              <Zap className="h-4 w-4 text-cyan-400" />
              <h3 className="text-sm font-semibold text-white">跨设备局域网快传</h3>
            </div>
            <p className="text-[10px] text-slate-300">生成取件码或直接扫码，免压缩快传。</p>
          </div>
          <ChevronRight className="h-5 w-5 text-cyan-400 opacity-70" />
        </CardContent>
      </Card>
      
      {/* 留出底部边距给导航栏 */}
      <div className="h-6" />
    </div>
  );
}

function MobileMetricCard({ title, value, icon: Icon, delay, color, bg, subtitle }: any) {
  return (
    <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay }}>
      <div className="glass-panel p-4 rounded-xl flex flex-col justify-between h-full hover:bg-white/5 active:scale-95 transition-all">
        <div className="flex items-center gap-2 mb-2">
          <div className={`p-1.5 rounded-lg ${bg}`}><Icon className={`w-4 h-4 ${color}`} /></div>
          <span className="text-xs text-slate-400">{title}</span>
        </div>
        <div>
          <div className="text-xl font-bold text-white leading-tight">{value}</div>
          {subtitle && <div className="text-[10px] text-slate-500 mt-0.5">{subtitle}</div>}
        </div>
      </div>
    </motion.div>
  );
}

function QuickAction({ icon: Icon, label, onClick }: any) {
  return (
    <button onClick={onClick} className="flex flex-col items-center justify-center gap-2 py-3 px-1 rounded-xl bg-white/[0.02] hover:bg-white/[0.06] active:scale-95 transition-all">
      <Icon className="w-5 h-5 text-slate-300" />
      <span className="text-[10px] tracking-wider text-slate-300">{label}</span>
    </button>
  );
}
