import React, { useState } from 'react';
import { motion } from 'motion/react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/src/components/ui/card';
import { Button } from '@/src/components/ui/button';
import { Input } from '@/src/components/ui/input';
import { GraduationCap, Calendar, User, Lock, Search, BookOpen, ChevronRight, Award } from 'lucide-react';
import { cn } from '@/src/lib/utils';

export default function School() {
  const [activeTab, setActiveTab] = useState<'schedule' | 'grades'>('schedule');
  const [loading, setLoading] = useState(false);
  const [queried, setQueried] = useState(false);

  const handleQuery = (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      setQueried(true);
    }, 1500);
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
                  <Input defaultValue="2023123456" className="pl-9 bg-black/20" required />
                </div>
              </div>
              <div className="space-y-2">
                <label className="text-xs font-medium text-slate-400 ml-1">密码</label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                  <Input type="password" defaultValue="password123" className="pl-9 bg-black/20" required />
                </div>
              </div>
              <div className="space-y-2">
                <label className="text-xs font-medium text-slate-400 ml-1">学期</label>
                <select className="flex h-11 w-full rounded-xl border border-white/10 bg-black/20 px-3 py-2 text-sm text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#336EFF]">
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
                <SummaryItem label="当前缓存账号" value="2023123456" icon={User} />
                <SummaryItem label="已保存课表学期" value="2025 春" icon={Calendar} />
                <SummaryItem label="已保存成绩" value="3 个学期" icon={Award} />
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
            "px-6 py-2 text-sm font-medium rounded-lg transition-all",
            activeTab === 'schedule' ? "bg-[#336EFF] text-white shadow-md" : "text-slate-400 hover:text-white"
          )}
        >
          课表抽屉
        </button>
        <button
          onClick={() => setActiveTab('grades')}
          className={cn(
            "px-6 py-2 text-sm font-medium rounded-lg transition-all",
            activeTab === 'grades' ? "bg-[#336EFF] text-white shadow-md" : "text-slate-400 hover:text-white"
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
        {activeTab === 'schedule' ? <ScheduleView queried={queried} /> : <GradesView queried={queried} />}
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

function ScheduleView({ queried }: { queried: boolean }) {
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
  const mockSchedule = [
    { day: 0, time: '08:00 - 09:35', name: '高等数学 (下)', room: '教1-204' },
    { day: 0, time: '10:00 - 11:35', name: '大学物理', room: '教2-101' },
    { day: 1, time: '14:00 - 15:35', name: '软件工程', room: '计科楼 302' },
    { day: 2, time: '08:00 - 09:35', name: '数据结构', room: '教1-105' },
    { day: 3, time: '16:00 - 17:35', name: '计算机网络', room: '计科楼 401' },
    { day: 4, time: '10:00 - 11:35', name: '操作系统', room: '教3-202' },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle>本周课表</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
          {days.map((day, index) => (
            <div key={day} className="space-y-3">
              <div className="text-center py-2 bg-white/5 rounded-lg text-sm font-medium text-slate-300">
                {day}
              </div>
              <div className="space-y-2">
                {mockSchedule.filter(s => s.day === index).map((course, i) => (
                  <div key={i} className="p-3 rounded-xl bg-[#336EFF]/10 border border-[#336EFF]/20 hover:bg-[#336EFF]/20 transition-colors">
                    <p className="text-xs font-mono text-[#336EFF] mb-1">{course.time}</p>
                    <p className="text-sm font-medium text-white leading-tight mb-2">{course.name}</p>
                    <p className="text-xs text-slate-400 flex items-center gap-1">
                      <ChevronRight className="w-3 h-3" /> {course.room}
                    </p>
                  </div>
                ))}
                {mockSchedule.filter(s => s.day === index).length === 0 && (
                  <div className="h-24 rounded-xl border border-dashed border-white/10 flex items-center justify-center text-xs text-slate-500">
                    无课程
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

function GradesView({ queried }: { queried: boolean }) {
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

  const terms = [
    {
      name: '2024 秋',
      grades: [75, 78, 80, 83, 85, 88, 89, 96]
    },
    {
      name: '2025 春',
      grades: [70, 78, 82, 84, 85, 85, 86, 88, 93]
    },
    {
      name: '2025 秋',
      grades: [68, 70, 76, 80, 85, 86, 90, 94, 97]
    }
  ];

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
          {terms.map((term, i) => (
            <div key={i} className="flex flex-col">
              <h3 className="text-sm font-bold text-white border-b border-white/5 pb-3 mb-4">{term.name}</h3>
              <div className="flex flex-col gap-2">
                {term.grades.map((score, j) => (
                  <div 
                    key={j} 
                    className={cn(
                      "w-full py-1.5 rounded-full text-xs font-mono font-medium text-center transition-colors",
                      getScoreStyle(score)
                    )}
                  >
                    {score}
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
