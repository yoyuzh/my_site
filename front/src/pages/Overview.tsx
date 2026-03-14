import React from 'react';
import { motion } from 'motion/react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/src/components/ui/card';
import { Button } from '@/src/components/ui/button';
import { 
  FileText, Upload, FolderPlus, Database, 
  GraduationCap, BookOpen, Clock, HardDrive,
  User, Mail, ChevronRight
} from 'lucide-react';

export default function Overview() {
  const navigate = useNavigate();
  const currentHour = new Date().getHours();
  let greeting = '晚上好';
  if (currentHour < 6) greeting = '凌晨好';
  else if (currentHour < 12) greeting = '早上好';
  else if (currentHour < 18) greeting = '下午好';

  const currentTime = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });

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
          <h1 className="text-3xl md:text-4xl font-bold text-white tracking-tight">欢迎回来，tester5595</h1>
          <p className="text-[#336EFF] font-medium">现在时间 {currentTime} · {greeting}</p>
          <p className="text-sm text-slate-400 mt-4 max-w-xl leading-relaxed">
            这是您的个人门户总览。在这里您可以快速查看网盘文件状态、近期课程安排以及教务成绩摘要。
          </p>
        </div>
      </motion.div>

      {/* Metrics Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard title="网盘文件总数" value="128" desc="包含 4 个分类" icon={FileText} delay={0.1} />
        <MetricCard title="最近 7 天上传" value="6" desc="最新更新于 2 小时前" icon={Upload} delay={0.2} />
        <MetricCard title="本周课程" value="18" desc="今日还有 2 节课" icon={BookOpen} delay={0.3} />
        <MetricCard title="已录入成绩" value="42" desc="最近学期：2025 秋" icon={GraduationCap} delay={0.4} />
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
                {[
                  { name: '软件工程期末复习资料.pdf', size: '2.4 MB', time: '2小时前' },
                  { name: '2025春季学期课表.xlsx', size: '156 KB', time: '昨天 14:30' },
                  { name: '项目架构设计图.png', size: '4.1 MB', time: '3天前' },
                ].map((file, i) => (
                  <div key={i} className="flex items-center justify-between p-3 rounded-xl hover:bg-white/5 transition-colors cursor-pointer group" onClick={() => navigate('/files')}>
                    <div className="flex items-center gap-4 overflow-hidden">
                      <div className="w-10 h-10 rounded-xl bg-[#336EFF]/10 flex items-center justify-center shrink-0 group-hover:bg-[#336EFF]/20 transition-colors">
                        <FileText className="w-5 h-5 text-[#336EFF]" />
                      </div>
                      <div className="truncate">
                        <p className="text-sm font-medium text-white truncate">{file.name}</p>
                        <p className="text-xs text-slate-400 mt-0.5">{file.time}</p>
                      </div>
                    </div>
                    <span className="text-xs text-slate-500 font-mono shrink-0 ml-4">{file.size}</span>
                  </div>
                ))}
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
                {[
                  { time: '08:00 - 09:35', name: '高等数学 (下)', room: '教1-204' },
                  { time: '10:00 - 11:35', name: '大学物理', room: '教2-101' },
                  { time: '14:00 - 15:35', name: '软件工程', room: '计科楼 302' },
                ].map((course, i) => (
                  <div key={i} className="flex items-center gap-4 p-4 rounded-xl border border-white/5 bg-white/[0.02] hover:bg-white/[0.04] transition-colors">
                    <div className="w-28 shrink-0 text-sm font-mono text-[#336EFF] bg-[#336EFF]/10 px-2 py-1 rounded-md text-center">{course.time}</div>
                    <div className="flex-1 truncate">
                      <p className="text-sm font-medium text-white truncate">{course.name}</p>
                      <p className="text-xs text-slate-400 flex items-center gap-1.5 mt-1">
                        <Clock className="w-3.5 h-3.5" /> {course.room}
                      </p>
                    </div>
                  </div>
                ))}
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
                  <p className="text-3xl font-bold text-white tracking-tight">12.6 <span className="text-sm text-slate-400 font-normal">GB</span></p>
                  <p className="text-xs text-slate-500 uppercase tracking-wider">已使用 / 共 50 GB</p>
                </div>
                <span className="text-xl font-mono text-[#336EFF] font-medium">25%</span>
              </div>
              <div className="h-2.5 w-full bg-black/40 rounded-full overflow-hidden shadow-inner">
                <div className="h-full bg-gradient-to-r from-[#336EFF] to-blue-400 rounded-full" style={{ width: '25%' }} />
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
                <div className="w-12 h-12 rounded-full bg-gradient-to-br from-indigo-500 to-purple-500 flex items-center justify-center text-white font-bold text-xl shadow-lg">
                  T
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-white truncate">tester5595</p>
                  <p className="text-xs text-slate-400 truncate mt-0.5">tester5595@example.com</p>
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
