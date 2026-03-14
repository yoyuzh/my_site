import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { Award, BookOpen, Calendar, ChevronRight, GraduationCap, Lock, MapPin, Search, User } from 'lucide-react';

import { Button } from '@/src/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/src/components/ui/card';
import { Input } from '@/src/components/ui/input';
import { apiRequest } from '@/src/lib/api';
import { readCachedValue, writeCachedValue } from '@/src/lib/cache';
import { getSchoolResultsCacheKey, readStoredSchoolQuery, writeStoredSchoolQuery } from '@/src/lib/page-cache';
import { buildScheduleTable } from '@/src/lib/schedule-table';
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
    options: { background?: boolean } = {},
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

  const handleQuery = async (event: React.FormEvent) => {
    event.preventDefault();
    await loadSchoolData(studentId, semester);
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Search className="w-5 h-5 text-[#336EFF]" />
              教务查询
            </CardTitle>
            <CardDescription>输入学号、密码和学期后同步课表与成绩。</CardDescription>
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
                <select
                  value={semester}
                  onChange={(event) => setSemester(event.target.value)}
                  className="flex h-11 w-full rounded-xl border border-white/10 bg-black/20 px-3 py-2 text-sm text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#336EFF]"
                >
                  <option value="2025-spring">2025 春</option>
                  <option value="2024-fall">2024 秋</option>
                  <option value="2024-spring">2024 春</option>
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3 pt-2">
                <Button type="submit" disabled={loading} className="w-full">
                  {loading ? '查询中...' : '查询课表'}
                </Button>
                <Button
                  type="submit"
                  variant="outline"
                  disabled={loading}
                  className="w-full"
                  onClick={() => setActiveTab('grades')}
                >
                  {loading ? '查询中...' : '查询成绩'}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <DatabaseIcon className="w-5 h-5 text-[#336EFF]" />
              数据摘要
            </CardTitle>
            <CardDescription>展示当前缓存或最近一次查询结果。</CardDescription>
          </CardHeader>
          <CardContent>
            {queried ? (
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <SummaryItem label="当前账号" value={studentId} icon={User} />
                <SummaryItem label="当前学期" value={semester} icon={Calendar} />
                <SummaryItem label="平均成绩" value={`${averageGrade} 分`} icon={Award} />
              </div>
            ) : (
              <div className="h-40 flex flex-col items-center justify-center text-slate-500 space-y-3 border border-dashed border-white/10 rounded-xl bg-white/[0.01]">
                <Search className="w-8 h-8 opacity-50" />
                <p className="text-sm">暂无缓存数据，请先执行查询。</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="flex bg-black/20 p-1 rounded-xl w-fit">
        <button
          onClick={() => setActiveTab('schedule')}
          className={cn(
            'px-6 py-2 text-sm font-medium rounded-lg transition-all',
            activeTab === 'schedule' ? 'bg-[#336EFF] text-white shadow-md' : 'text-slate-400 hover:text-white',
          )}
        >
          课表视图
        </button>
        <button
          onClick={() => setActiveTab('grades')}
          className={cn(
            'px-6 py-2 text-sm font-medium rounded-lg transition-all',
            activeTab === 'grades' ? 'bg-[#336EFF] text-white shadow-md' : 'text-slate-400 hover:text-white',
          )}
        >
          成绩热力图
        </button>
      </div>

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

function DatabaseIcon(props: React.SVGProps<SVGSVGElement>) {
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

function SummaryItem({
  label,
  value,
  icon: Icon,
}: {
  label: string;
  value: string;
  icon: React.ComponentType<{ className?: string }>;
}) {
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
  const periodLabels: Record<'morning' | 'afternoon' | 'evening', string> = {
    morning: '上午',
    afternoon: '下午',
    evening: '晚上',
  };
  const rows = buildScheduleTable(schedule);

  return (
    <Card>
      <CardHeader>
        <CardTitle>本周课表</CardTitle>
        <CardDescription>上午 4 节、下午 4 节、晚上 4 节。没有课的格子保持为空。</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[920px] border-separate border-spacing-2 text-sm">
            <thead>
              <tr>
                <th className="w-24 rounded-xl bg-white/5 px-3 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">
                  节次
                </th>
                {days.map((day) => (
                  <th key={day} className="rounded-xl bg-white/5 px-3 py-3 text-center text-sm font-medium text-slate-200">
                    {day}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.section}>
                  <td className="align-top">
                    <div className="rounded-xl border border-white/5 bg-white/[0.02] px-3 py-3">
                      <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">{periodLabels[row.period]}</p>
                      <p className="mt-1 text-base font-semibold text-white">第 {row.section} 节</p>
                    </div>
                  </td>
                  {row.slots.map((slot, index) => {
                    if (slot.type === 'covered') {
                      return null;
                    }

                    if (slot.type === 'empty') {
                      return (
                        <td key={`${row.section}-${index}`} className="h-24 rounded-xl border border-dashed border-white/10 bg-white/[0.01]" />
                      );
                    }

                    return (
                      <td key={`${row.section}-${index}`} rowSpan={slot.rowSpan} className="min-w-[145px] align-top">
                        <div className="h-full min-h-24 rounded-xl border border-[#336EFF]/20 bg-[#336EFF]/10 p-3 transition-colors hover:bg-[#336EFF]/20">
                          <p className="text-xs font-mono text-[#336EFF] mb-1">
                            {formatSections(slot.course?.startTime, slot.course?.endTime)}
                          </p>
                          <p className="text-sm font-medium text-white leading-tight mb-2">
                            {slot.course?.courseName}
                          </p>
                          <p className="text-xs text-slate-400 flex items-center gap-1">
                            <ChevronRight className="w-3 h-3" /> {slot.course?.classroom ?? '教室待定'}
                          </p>
                          <p className="mt-2 text-[11px] text-slate-500">
                            {slot.course?.teacher ?? '任课教师待定'}
                          </p>
                        </div>
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
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
    const term = grade.semester ?? '未分类';
    if (!accumulator[term]) {
      accumulator[term] = [];
    }
    accumulator[term].push(grade.grade ?? 0);
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
          {Object.entries(terms).map(([term, scores]) => (
            <div key={term} className="flex flex-col">
              <h3 className="text-sm font-bold text-white border-b border-white/5 pb-3 mb-4">{term}</h3>
              <div className="flex flex-col gap-2">
                {scores.map((score, index) => (
                  <div
                    key={`${term}-${index}`}
                    className={cn(
                      'w-full py-1.5 rounded-full text-xs font-mono font-medium text-center transition-colors',
                      getScoreStyle(score),
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
