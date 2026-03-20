import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { useNavigate } from 'react-router-dom';
import {
  FileText,
  Upload,
  FolderPlus,
  Database,
  GraduationCap,
  BookOpen,
  Clock,
  User,
  Mail,
  ChevronRight,
} from 'lucide-react';

import { Button } from '@/src/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/src/components/ui/card';
import { apiDownload, apiRequest } from '@/src/lib/api';
import { readCachedValue, writeCachedValue } from '@/src/lib/cache';
import { shouldLoadAvatarWithAuth } from '@/src/components/layout/account-utils';
import { getOverviewCacheKey, getSchoolResultsCacheKey, readStoredSchoolQuery, writeStoredSchoolQuery } from '@/src/lib/page-cache';
import { cacheLatestSchoolData, fetchLatestSchoolData } from '@/src/lib/school';
import { clearPostLoginPending, hasPostLoginPending, readStoredSession } from '@/src/lib/session';
import type { CourseResponse, FileMetadata, GradeResponse, PageResponse, UserProfile } from '@/src/lib/types';
import { getOverviewLoadErrorMessage } from './overview-state';

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
  const storedSchoolQuery = readStoredSchoolQuery();
  const cachedSchoolResults =
    storedSchoolQuery?.studentId && storedSchoolQuery?.semester
      ? readCachedValue<{
          schedule: CourseResponse[];
          grades: GradeResponse[];
        }>(getSchoolResultsCacheKey(storedSchoolQuery.studentId, storedSchoolQuery.semester))
      : null;
  const cachedOverview = readCachedValue<{
    profile: UserProfile | null;
    recentFiles: FileMetadata[];
    rootFiles: FileMetadata[];
    schedule: CourseResponse[];
    grades: GradeResponse[];
  }>(getOverviewCacheKey());
  const [profile, setProfile] = useState<UserProfile | null>(cachedOverview?.profile ?? readStoredSession()?.user ?? null);
  const [recentFiles, setRecentFiles] = useState<FileMetadata[]>(cachedOverview?.recentFiles ?? []);
  const [rootFiles, setRootFiles] = useState<FileMetadata[]>(cachedOverview?.rootFiles ?? []);
  const [schedule, setSchedule] = useState<CourseResponse[]>(cachedOverview?.schedule ?? cachedSchoolResults?.schedule ?? []);
  const [grades, setGrades] = useState<GradeResponse[]>(cachedOverview?.grades ?? cachedSchoolResults?.grades ?? []);
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
    (file) => Date.now() - new Date(file.createdAt).getTime() <= 7 * 24 * 60 * 60 * 1000
  ).length;
  const usedBytes = useMemo(
    () => rootFiles.filter((file) => !file.directory).reduce((sum, file) => sum + file.size, 0),
    [rootFiles]
  );
  const usedGb = usedBytes / 1024 / 1024 / 1024;
  const storagePercent = Math.min((usedGb / 50) * 100, 100);

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

        const primaryFailures = [userResult, recentResult, rootResult].filter(
          (result) => result.status === 'rejected'
        );

        if (cancelled) {
          return;
        }

        if (userResult.status === 'fulfilled') {
          setProfile(userResult.value);
        }
        if (recentResult.status === 'fulfilled') {
          setRecentFiles(recentResult.value);
        }
        if (rootResult.status === 'fulfilled') {
          setRootFiles(rootResult.value.items);
        }

        let scheduleData: CourseResponse[] = [];
        let gradesData: GradeResponse[] = [];
        const schoolQuery = readStoredSchoolQuery();
        let schoolFailed = false;

        if (schoolQuery?.studentId && schoolQuery?.semester) {
          const queryString = new URLSearchParams({
            studentId: schoolQuery.studentId,
            semester: schoolQuery.semester,
          }).toString();

          const [scheduleResult, gradesResult] = await Promise.allSettled([
            apiRequest<CourseResponse[]>(`/cqu/schedule?${queryString}`),
            apiRequest<GradeResponse[]>(`/cqu/grades?${queryString}`),
          ]);

          if (scheduleResult.status === 'fulfilled') {
            scheduleData = scheduleResult.value;
          } else {
            schoolFailed = true;
          }
          if (gradesResult.status === 'fulfilled') {
            gradesData = gradesResult.value;
          } else {
            schoolFailed = true;
          }
        } else {
          try {
            const latest = await fetchLatestSchoolData();
            if (latest) {
              cacheLatestSchoolData(latest);
              writeStoredSchoolQuery({
                studentId: latest.studentId,
                semester: latest.semester,
              });
              scheduleData = latest.schedule;
              gradesData = latest.grades;
            }
          } catch {
            schoolFailed = true;
          }
        }

        if (!cancelled) {
          setSchedule(scheduleData);
          setGrades(gradesData);
          writeCachedValue(getOverviewCacheKey(), {
            profile:
              userResult.status === 'fulfilled'
                ? userResult.value
                : profile,
            recentFiles:
              recentResult.status === 'fulfilled'
                ? recentResult.value
                : recentFiles,
            rootFiles:
              rootResult.status === 'fulfilled'
                ? rootResult.value.items
                : rootFiles,
            schedule: scheduleData,
            grades: gradesData,
          });

          if (primaryFailures.length > 0 || schoolFailed) {
            setLoadingError(getOverviewLoadErrorMessage(pendingAfterLogin));
          } else {
            clearPostLoginPending();
          }
        }
      } catch {
        const schoolQuery = readStoredSchoolQuery();
        if (!cancelled && schoolQuery?.studentId && schoolQuery?.semester) {
          const cachedSchoolResults = readCachedValue<{
            schedule: CourseResponse[];
            grades: GradeResponse[];
          }>(getSchoolResultsCacheKey(schoolQuery.studentId, schoolQuery.semester));

          if (cachedSchoolResults) {
            setSchedule(cachedSchoolResults.schedule);
            setGrades(cachedSchoolResults.grades);
          }
        }

        if (!cancelled) {
          setLoadingError(getOverviewLoadErrorMessage(pendingAfterLogin));
        }
      }
    }

    loadOverview();
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

  const latestSemester = grades[0]?.semester ?? '--';
  const previewCourses = schedule.slice(0, 3);
  const profileDisplayName = profile?.displayName || profile?.username || '未登录';
  const profileAvatarFallback = profileDisplayName.charAt(0).toUpperCase();

  return (
    <div className="space-y-6">
      {/* Hero Section */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass-panel rounded-3xl p-8 relative overflow-hidden"
      >
        <div className="absolute top-0 right-0 w-64 h-64 bg-[#336EFF] rounded-full mix-blend-screen filter blur-[100px] opacity-20" />
        <div className="relative z-10 space-y-2">
          <h1 className="text-3xl md:text-4xl font-bold text-white tracking-tight">
            欢迎回来，{profile?.username ?? '访客'}
          </h1>
          <p className="text-[#336EFF] font-medium">现在时间 {currentTime} · {greeting}</p>
          <p className="text-sm text-slate-400 mt-4 max-w-xl leading-relaxed">
            这是您的个人门户总览。在这里您可以快速查看网盘文件状态、近期课程安排以及教务成绩摘要。
          </p>
        </div>
      </motion.div>

      {loadingError && (
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
      )}

      {/* Metrics Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard title="网盘文件总数" value={`${rootFiles.length}`} desc="当前根目录统计" icon={FileText} delay={0.1} />
        <MetricCard
          title="最近 7 天上传"
          value={`${recentWeekUploads}`}
          desc={recentFiles[0] ? `最新更新于 ${formatRecentTime(recentFiles[0].createdAt)}` : '暂无最近上传'}
          icon={Upload}
          delay={0.2}
        />
        <MetricCard
          title="本周课程"
          value={`${schedule.length}`}
          desc={schedule.length > 0 ? `当前已同步 ${schedule.length} 节课` : '请先前往教务页查询'}
          icon={BookOpen}
          delay={0.3}
        />
        <MetricCard
          title="已录入成绩"
          value={`${grades.length}`}
          desc={`最近学期：${latestSemester}`}
          icon={GraduationCap}
          delay={0.4}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column */}
        <div className="lg:col-span-2 space-y-6">
          {/* Recent Files */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle>最近文件</CardTitle>
              <Button variant="ghost" size="sm" className="text-xs text-slate-400" onClick={() => navigate('/files')}>
                查看全部 <ChevronRight className="w-4 h-4 ml-1" />
              </Button>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                {recentFiles.slice(0, 3).map((file, i) => (
                  <div key={i} className="flex items-center justify-between p-3 rounded-xl hover:bg-white/5 transition-colors cursor-pointer group" onClick={() => navigate('/files')}>
                    <div className="flex items-center gap-4 overflow-hidden">
                      <div className="w-10 h-10 rounded-xl bg-[#336EFF]/10 flex items-center justify-center shrink-0 group-hover:bg-[#336EFF]/20 transition-colors">
                        <FileText className="w-5 h-5 text-[#336EFF]" />
                      </div>
                      <div className="truncate">
                        <p className="text-sm font-medium text-white truncate">{file.filename}</p>
                        <p className="text-xs text-slate-400 mt-0.5">{formatRecentTime(file.createdAt)}</p>
                      </div>
                    </div>
                    <span className="text-xs text-slate-500 font-mono shrink-0 ml-4">{formatFileSize(file.size)}</span>
                  </div>
                ))}
                {recentFiles.length === 0 && (
                  <div className="p-3 rounded-xl border border-dashed border-white/10 text-sm text-slate-500">
                    暂无最近文件
                  </div>
                )}
              </div>
            </CardContent>
          </Card>

          {/* Schedule */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle>今日 / 本周课程</CardTitle>
              <div className="flex bg-black/20 rounded-lg p-1">
                <button className="px-3 py-1 text-xs font-medium rounded-md bg-[#336EFF] text-white shadow-sm transition-colors">今日</button>
                <button className="px-3 py-1 text-xs font-medium rounded-md text-slate-400 hover:text-white transition-colors">本周</button>
              </div>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                {previewCourses.map((course, i) => (
                  <div key={i} className="flex items-center gap-4 p-4 rounded-xl border border-white/5 bg-white/[0.02] hover:bg-white/[0.04] transition-colors">
                    <div className="w-28 shrink-0 text-sm font-mono text-[#336EFF] bg-[#336EFF]/10 px-2 py-1 rounded-md text-center">
                      第 {course.startTime ?? '--'} - {course.endTime ?? '--'} 节
                    </div>
                    <div className="flex-1 truncate">
                      <p className="text-sm font-medium text-white truncate">{course.courseName}</p>
                      <p className="text-xs text-slate-400 flex items-center gap-1.5 mt-1">
                        <Clock className="w-3.5 h-3.5" /> {course.classroom ?? '教室待定'}
                      </p>
                    </div>
                  </div>
                ))}
                {previewCourses.length === 0 && (
                  <div className="p-4 rounded-xl border border-dashed border-white/10 text-sm text-slate-500">
                    暂无课程数据，请先前往教务页查询
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Right Column */}
        <div className="space-y-6">
          {/* Quick Actions */}
          <Card>
            <CardHeader className="pb-4">
              <CardTitle>快捷操作</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-2 gap-3">
                <QuickAction icon={Upload} label="上传文件" onClick={() => navigate('/files')} />
                <QuickAction icon={FolderPlus} label="新建文件夹" onClick={() => navigate('/files')} />
                <QuickAction icon={Database} label="进入网盘" onClick={() => navigate('/files')} />
                <QuickAction icon={GraduationCap} label="查询成绩" onClick={() => navigate('/school')} />
              </div>
            </CardContent>
          </Card>

          {/* Storage */}
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
                  <p className="text-xs text-slate-500 uppercase tracking-wider">已使用 / 共 50 GB</p>
                </div>
                <span className="text-xl font-mono text-[#336EFF] font-medium">{storagePercent.toFixed(1)}%</span>
              </div>
              <div className="h-2.5 w-full bg-black/40 rounded-full overflow-hidden shadow-inner">
                <div className="h-full bg-gradient-to-r from-[#336EFF] to-blue-400 rounded-full" style={{ width: `${storagePercent}%` }} />
              </div>
            </CardContent>
          </Card>

          {/* Account Info */}
          <Card>
            <CardHeader className="pb-4">
              <CardTitle>账号信息</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex items-center gap-4 p-4 rounded-xl bg-white/[0.02] border border-white/5">
                <div className="w-12 h-12 rounded-full bg-gradient-to-br from-indigo-500 to-purple-500 flex items-center justify-center text-white font-bold text-xl shadow-lg overflow-hidden">
                  {avatarUrl ? (
                    <img src={avatarUrl} alt="Avatar" className="w-full h-full object-cover" />
                  ) : (
                    profileAvatarFallback
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-white truncate">{profileDisplayName}</p>
                  <p className="text-xs text-slate-400 truncate mt-0.5">{profile?.email ?? '暂无邮箱'}</p>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}

function MetricCard({ title, value, desc, icon: Icon, delay }: any) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay }}
    >
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

function QuickAction({ icon: Icon, label, onClick }: any) {
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
