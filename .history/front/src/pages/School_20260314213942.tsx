import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { Award, BookOpen, Calendar, Lock, MapPin, Search, User } from 'lucide-react';

import { Button } from '@/src/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/src/components/ui/card';
import { Input } from '@/src/components/ui/input';
import { apiRequest } from '@/src/lib/api';
import { readCachedValue, writeCachedValue } from '@/src/lib/cache';
import { getSchoolResultsCacheKey, readStoredSchoolQuery, writeStoredSchoolQuery } from '@/src/lib/page-cache';
import { buildScheduleTable } from '@/src/lib/schedule-table';
import type { CourseResponse, GradeResponse } from '@/src/lib/types';
import { cn } from '@/src/lib/utils';

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

function getCourseTheme(courseName?: string) {
  if (!courseName) {
    return {
      bg: 'bg-slate-500/10',
      border: 'border-slate-500/20',
      text: 'text-slate-300',
    };
  }

  const themes = [
    {
      bg: 'bg-blue-500/20 hover:bg-blue-500/30',
      border: 'border-blue-400/30',
      text: 'text-blue-100',
    },
    {
      bg: 'bg-indigo-500/20 hover:bg-indigo-500/30',
      border: 'border-indigo-400/30',
      text: 'text-indigo-100',
    },
    {
      bg: 'bg-cyan-500/20 hover:bg-cyan-500/30',
      border: 'border-cyan-400/30',
      text: 'text-cyan-100',
    },
    {
      bg: 'bg-sky-500/20 hover:bg-sky-500/30',
      border: 'border-sky-400/30',
      text: 'text-sky-100',
    },
    {
      bg: 'bg-violet-500/20 hover:bg-violet-500/30',
      border: 'border-violet-400/30',
      text: 'text-violet-100',
    },
    {
      bg: 'bg-teal-500/20 hover:bg-teal-500/30',
      border: 'border-teal-400/30',
      text: 'text-teal-100',
    },
  ];

  let hash = 0;
  for (let i = 0; i < courseName.length; i += 1) {
    hash = courseName.charCodeAt(i) + ((hash << 5) - hash);
  }
  return themes[Math.abs(hash) % themes.length];
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
  const rows = buildScheduleTable(schedule);

  return (
    <Card className="border-none bg-transparent shadow-none">
      <CardHeader className="px-0 pt-0">
        <div className="flex items-center justify-between mb-2">
          <div>
            <CardTitle className="text-xl">我的课表</CardTitle>
            <CardDescription>2025 春季学期</CardDescription>
          </div>
          <div className="flex gap-2 text-xs text-slate-400">
            <span className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full bg-blue-500" />
              {' '}
              必修
            </span>
            <span className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full bg-cyan-500" />
              {' '}
              选修
            </span>
          </div>
        </div>
      </CardHeader>
      <CardContent className="p-0">
        <div className="overflow-x-auto pb-2 -mx-1 px-1">
          <div className="min-w-[800px]">
            <table className="w-full border-separate border-spacing-2">
              <thead>
                <tr>
                  <th className="w-10" aria-label="时段" />
                  <th className="w-8" aria-label="节次" />
                  {days.map((d) => (
                    <th key={d} className="pb-2 text-center text-sm font-medium text-slate-400">
                      {d}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map((row, index) => (
                  <tr key={row.section}>
                    {/* Morning/Afternoon label */}
                    {index % 4 === 0 && (
                      <td rowSpan={4} className="align-top pt-2">
                        <div className="py-3 rounded-lg bg-white/5 text-center text-xs font-bold text-slate-400 [writing-mode:vertical-lr] h-full flex items-center justify-center tracking-widest border border-white/5">
                          {row.section <= 4 ? '上午' : row.section <= 8 ? '下午' : '晚上'}
                        </div>
                      </td>
                    )}

                    {/* Section Number */}
                    <td className="align-middle text-center">
                      <div className="text-sm font-mono text-slate-600 font-bold">{row.section}</div>
                    </td>

                    {/* Cells */}
                    {row.slots.map((slot, colIdx) => {
                      if (slot.type === 'covered') return null;
                      if (slot.type === 'empty') {
                        return (
                          <td key={`${row.section}-${colIdx}`} className="h-20 bg-white/[0.02] rounded-xl border border-white/5" />
                        );
                      }

                      const theme = getCourseTheme(slot.course?.courseName ?? '');
                      return (
                        <td key={`${row.section}-${colIdx}`} rowSpan={slot.rowSpan} className="h-20 align-top">
                          <div
                            className={cn(
                              'w-full h-full p-3 rounded-xl border backdrop-blur-md transition-all hover:scale-[1.02] hover:shadow-lg flex flex-col justify-between group cursor-pointer',
                              theme.bg,
                              theme.border,
                            )}
                          >
                            <div>
                              <h3 className={cn('font-bold text-sm line-clamp-2 leading-tight mb-2', theme.text)}>
                                {slot.course?.courseName}
                              </h3>
                              <p className={cn('text-xs flex items-center gap-1 opacity-80', theme.text)}>
                                <MapPin className="w-3 h-3 opacity-70" />
                                {' '}
                                {slot.course?.classroom ?? '未知'}
                              </p>
                            </div>
                            <div className="mt-2 flex items-center justify-between">
                              <span
                                className={cn(
                                  'text-[10px] px-1.5 py-0.5 rounded opacity-80 font-medium bg-black/10',
                                  theme.text,
                                )}
                              >
                                {slot.course?.teacher ?? '老师'}
                              </span>
                            </div>
                          </div>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
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
