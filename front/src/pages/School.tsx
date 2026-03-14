import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { GraduationCap, Calendar, User, Lock, Search, BookOpen, ChevronRight, Award } from 'lucide-react';

import { Button } from '@/src/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/src/components/ui/card';
import { Input } from '@/src/components/ui/input';
import { apiRequest } from '@/src/lib/api';
import { readCachedValue, writeCachedValue } from '@/src/lib/cache';
import { getSchoolResultsCacheKey, readStoredSchoolQuery, writeStoredSchoolQuery } from '@/src/lib/page-cache';
import type { CourseResponse, GradeResponse } from '@/src/lib/types';
import { cn } from '@/src/lib/utils';

function formatSections(startTime?: number | null, endTime?: number | null) {
  if (!startTime || !endTime) {
    return '节次待定';
  }

  return `第 ${startTime}-${endTime} 节`;
}

export default function School() {
  const storedQuery = readStoredSchoolQuery();
  const initialStudentId = storedQuery?.studentId ?? '2023123456';
  const initialSemester = storedQuery?.semester ?? '2025-spring';
  const initialCachedResults = readCachedValue<{
    queried: boolean;
    schedule: CourseResponse[];
    grades: GradeResponse[];
  }>(getSchoolResultsCacheKey(initialStudentId, initialSemester));
  const [activeTab, setActiveTab] = useState<'schedule' | 'grades'>('schedule');
  const [studentId, setStudentId] = useState(initialStudentId);
  const [password, setPassword] = useState('password123');
  const [semester, setSemester] = useState(initialSemester);
  const [loading, setLoading] = useState(false);
  const [queried, setQueried] = useState(initialCachedResults?.queried ?? false);
  const [schedule, setSchedule] = useState<CourseResponse[]>(initialCachedResults?.schedule ?? []);
  const [grades, setGrades] = useState<GradeResponse[]>(initialCachedResults?.grades ?? []);

  const averageGrade = useMemo(() => {
    if (grades.length === 0) {
      return '0.0';
    }

    const sum = grades.reduce((total, item) => total + (item.grade ?? 0), 0);
    return (sum / grades.length).toFixed(1);
  }, [grades]);

  const loadSchoolData = async (
    nextStudentId: string,
    nextSemester: string,
    options: { background?: boolean } = {}
  ) => {
    const cacheKey = getSchoolResultsCacheKey(nextStudentId, nextSemester);
    const cachedResults = readCachedValue<{
      queried: boolean;
      schedule: CourseResponse[];
      grades: GradeResponse[];
    }>(cacheKey);

    if (!options.background) {
      setLoading(true);
    }

    writeStoredSchoolQuery({
      studentId: nextStudentId,
      semester: nextSemester,
    });

    try {
      const queryString = new URLSearchParams({
        studentId: nextStudentId,
        semester: nextSemester,
      }).toString();

      const [scheduleData, gradeData] = await Promise.all([
        apiRequest<CourseResponse[]>(`/cqu/schedule?${queryString}`),
        apiRequest<GradeResponse[]>(`/cqu/grades?${queryString}`),
      ]);

      setQueried(true);
      setSchedule(scheduleData);
      setGrades(gradeData);
      writeCachedValue(cacheKey, {
        queried: true,
        studentId: nextStudentId,
        semester: nextSemester,
        schedule: scheduleData,
        grades: gradeData,
      });
    } catch {
      if (!cachedResults) {
        setQueried(false);
        setSchedule([]);
        setGrades([]);
      }
    } finally {
      if (!options.background) {
        setLoading(false);
      }
    }
  };

  useEffect(() => {
    if (!storedQuery) {
      return;
    }

    loadSchoolData(storedQuery.studentId, storedQuery.semester, {
      background: true,
    }).catch(() => undefined);
  }, []);

  const handleQuery = async (e: React.FormEvent) => {
    e.preventDefault();
    await loadSchoolData(studentId, semester);
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Query Form */}
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Search className="w-5 h-5 text-[#336EFF]" />
              教务查询
            </CardTitle>
            <CardDescription>输入教务系统账号密码以同步数据</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleQuery} className="space-y-4">
              <div className="space-y-2">
                <label className="text-xs font-medium text-slate-400 ml-1">学号</label>
                <div className="relative">
                  <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                  <Input value={studentId} onChange={(event) => setStudentId(event.target.value)} className="pl-9 bg-black/20" required />
                </div>
              </div>
              <div className="space-y-2">
                <label className="text-xs font-medium text-slate-400 ml-1">密码</label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                  <Input type="password" value={password} onChange={(event) => setPassword(event.target.value)} className="pl-9 bg-black/20" required />
                </div>
              </div>
              <div className="space-y-2">
                <label className="text-xs font-medium text-slate-400 ml-1">学期</label>
                <select value={semester} onChange={(event) => setSemester(event.target.value)} className="flex h-11 w-full rounded-xl border border-white/10 bg-black/20 px-3 py-2 text-sm text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#336EFF]">
                  <option value="2025-spring">2025 春</option>
                  <option value="2024-fall">2024 秋</option>
                  <option value="2024-spring">2024 春</option>
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3 pt-2">
                <Button type="submit" disabled={loading} className="w-full">
                  {loading ? '查询中...' : '查询课表'}
                </Button>
                <Button type="submit" variant="outline" disabled={loading} className="w-full" onClick={() => setActiveTab('grades')}>
                  {loading ? '查询中...' : '查询成绩'}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>

        {/* Data Summary */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <DatabaseIcon className="w-5 h-5 text-[#336EFF]" />
              数据摘要
            </CardTitle>
            <CardDescription>当前缓存或最近一次查询结果</CardDescription>
          </CardHeader>
          <CardContent>
            {queried ? (
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <SummaryItem label="当前缓存账号" value={studentId} icon={User} />
                <SummaryItem label="已保存课表学期" value={semester} icon={Calendar} />
                <SummaryItem label="已保存成绩" value={`${averageGrade} 分`} icon={Award} />
              </div>
            ) : (
              <div className="h-40 flex flex-col items-center justify-center text-slate-500 space-y-3 border border-dashed border-white/10 rounded-xl bg-white/[0.01]">
                <Search className="w-8 h-8 opacity-50" />
                <p className="text-sm">暂无缓存数据，请先执行查询</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* View Toggle */}
      <div className="flex bg-black/20 p-1 rounded-xl w-fit">
        <button
          onClick={() => setActiveTab('schedule')}
          className={cn(
            'px-6 py-2 text-sm font-medium rounded-lg transition-all',
            activeTab === 'schedule' ? 'bg-[#336EFF] text-white shadow-md' : 'text-slate-400 hover:text-white'
          )}
        >
          课表抽屉
        </button>
        <button
          onClick={() => setActiveTab('grades')}
          className={cn(
            'px-6 py-2 text-sm font-medium rounded-lg transition-all',
            activeTab === 'grades' ? 'bg-[#336EFF] text-white shadow-md' : 'text-slate-400 hover:text-white'
          )}
        >
          成绩热力图
        </button>
      </div>

      {/* Content Area */}
      <motion.div
        key={activeTab}
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        {activeTab === 'schedule' ? <ScheduleView queried={queried} schedule={schedule} /> : <GradesView queried={queried} grades={grades} />}
      </motion.div>
    </div>
  );
}

function DatabaseIcon(props: any) {
  return (
    <svg
      {...props}
      xmlns="http://www.w3.org/2000/svg"
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <ellipse cx="12" cy="5" rx="9" ry="3" />
      <path d="M3 5V19A9 3 0 0 0 21 19V5" />
      <path d="M3 12A9 3 0 0 0 21 12" />
    </svg>
  );
}

function SummaryItem({ label, value, icon: Icon }: any) {
  return (
    <div className="p-4 rounded-xl bg-white/[0.02] border border-white/5 flex items-center gap-4">
      <div className="w-10 h-10 rounded-lg bg-[#336EFF]/10 flex items-center justify-center shrink-0">
        <Icon className="w-5 h-5 text-[#336EFF]" />
      </div>
      <div>
        <p className="text-xs text-slate-400 mb-0.5">{label}</p>
        <p className="text-sm font-medium text-white">{value}</p>
      </div>
    </div>
  );
}

function ScheduleView({ queried, schedule }: { queried: boolean; schedule: CourseResponse[] }) {
  if (!queried) {
    return (
      <Card>
        <CardContent className="h-64 flex flex-col items-center justify-center text-slate-500">
          <BookOpen className="w-12 h-12 mb-4 opacity-20" />
          <p>请先查询课表</p>
        </CardContent>
      </Card>
    );
  }

  const days = ['周一', '周二', '周三', '周四', '周五'];

  return (
    <Card>
      <CardHeader>
        <CardTitle>本周课表</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
          {days.map((day, index) => {
            const dayCourses = schedule.filter((item) => (item.dayOfWeek ?? 0) - 1 === index);
            return (
              <div key={day} className="space-y-3">
                <div className="text-center py-2 bg-white/5 rounded-lg text-sm font-medium text-slate-300">
                  {day}
                </div>
                <div className="space-y-2">
                  {dayCourses.map((course, i) => (
                    <div key={i} className="p-3 rounded-xl bg-[#336EFF]/10 border border-[#336EFF]/20 hover:bg-[#336EFF]/20 transition-colors">
                      <p className="text-xs font-mono text-[#336EFF] mb-1">{formatSections(course.startTime, course.endTime)}</p>
                      <p className="text-sm font-medium text-white leading-tight mb-2">{course.courseName}</p>
                      <p className="text-xs text-slate-400 flex items-center gap-1">
                        <ChevronRight className="w-3 h-3" /> {course.classroom ?? '教室待定'}
                      </p>
                    </div>
                  ))}
                  {dayCourses.length === 0 && (
                    <div className="h-24 rounded-xl border border-dashed border-white/10 flex items-center justify-center text-xs text-slate-500">
                      无课程
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}

function GradesView({ queried, grades }: { queried: boolean; grades: GradeResponse[] }) {
  if (!queried) {
    return (
      <Card>
        <CardContent className="h-64 flex flex-col items-center justify-center text-slate-500">
          <Award className="w-12 h-12 mb-4 opacity-20" />
          <p>请先查询成绩</p>
        </CardContent>
      </Card>
    );
  }

  const terms = grades.reduce<Record<string, number[]>>((accumulator, grade) => {
    const semester = grade.semester ?? '未分类';
    if (!accumulator[semester]) {
      accumulator[semester] = [];
    }
    accumulator[semester].push(grade.grade ?? 0);
    return accumulator;
  }, {});

  const getScoreStyle = (score: number) => {
    if (score >= 95) return 'bg-[#336EFF]/50 text-white';
    if (score >= 90) return 'bg-[#336EFF]/40 text-white/90';
    if (score >= 85) return 'bg-[#336EFF]/30 text-white/80';
    if (score >= 80) return 'bg-slate-700/60 text-white/70';
    if (score >= 75) return 'bg-slate-700/40 text-white/60';
    return 'bg-slate-800/60 text-white/50';
  };

  return (
    <Card className="bg-[#0f172a]/80 backdrop-blur-sm border-slate-800/50">
      <CardHeader className="pb-2">
        <CardTitle className="text-lg font-medium text-white">成绩热力图</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {Object.entries(terms).map(([term, scores], i) => (
            <div key={i} className="flex flex-col">
              <h3 className="text-sm font-bold text-white border-b border-white/5 pb-3 mb-4">{term}</h3>
              <div className="flex flex-col gap-2">
                {scores.map((score, j) => (
                  <div
                    key={j}
                    className={cn(
                      'w-full py-1.5 rounded-full text-xs font-mono font-medium text-center transition-colors',
                      getScoreStyle(score)
                    )}
                  >
                    {score}
                  </div>
                ))}
              </div>
            </div>
          ))}
          {Object.keys(terms).length === 0 && <div className="text-sm text-slate-500">暂无成绩数据</div>}
        </div>
      </CardContent>
    </Card>
  );
}
