import React, { useState } from 'react';
import { motion } from 'motion/react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/src/components/ui/card';
import { Button } from '@/src/components/ui/button';
import { Gamepad2, Cat, Car, ExternalLink, Play } from 'lucide-react';
import { cn } from '@/src/lib/utils';
import { MORE_GAMES_LABEL, MORE_GAMES_URL, resolveGamePlayerPath, type GameId } from '@/src/pages/games-links';

const GAMES: Array<{
  id: GameId;
  name: string;
  description: string;
  icon: typeof Cat;
  color: string;
  category: 'featured';
}> = [
  {
    id: 'cat',
    name: 'CAT',
    description: '简单的小猫升级游戏，通过点击获取经验，解锁不同形态的猫咪。',
    icon: Cat,
    color: 'from-orange-400 to-red-500',
    category: 'featured'
  },
  {
    id: 'race',
    name: 'RACE',
    description: '赛车休闲小游戏，躲避障碍物，挑战最高分记录。',
    icon: Car,
    color: 'from-blue-400 to-indigo-500',
    category: 'featured'
  }
];

function MobileGameCard({ game, index }: { game: (typeof GAMES)[number]; index: number }) {
  const navigate = useNavigate();

  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.1 }}>
      <Card className="glass-panel overflow-hidden border-white/10 bg-white/[0.03] active:bg-white/[0.06] transition-colors relative">
        <div className={cn("absolute top-0 left-0 h-1.5 w-full bg-gradient-to-r", game.color)} />
        <CardHeader className="pb-3 px-4 pt-5">
          <div className="flex items-start justify-between">
            <div className={cn("flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br shadow-lg", game.color)}>
              <game.icon className="h-6 w-6 text-white" />
            </div>
            <span className="rounded-md bg-white/5 border border-white/5 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-slate-400">
              {game.category}
            </span>
          </div>
          <CardTitle className="mt-3 text-lg font-bold text-white">{game.name}</CardTitle>
          <CardDescription className="mt-1 text-xs text-slate-400 line-clamp-2 leading-relaxed tracking-wide">
            {game.description}
          </CardDescription>
        </CardHeader>
        <CardContent className="px-4 pb-4">
          <Button type="button" onClick={() => navigate(resolveGamePlayerPath(game.id))} className="w-full h-11 bg-white/10 hover:bg-white/20 text-white rounded-xl font-medium tracking-wide border border-white/5 active:scale-95 transition-transform" >
            <Play className="h-4 w-4 mr-2" fill="currentColor" /> 开始游玩
          </Button>
        </CardContent>
      </Card>
    </motion.div>
  );
}

export default function MobileGames() {
  const [activeTab, setActiveTab] = useState<'featured' | 'all'>('featured');

  return (
    <div className="flex flex-col min-h-full bg-[#07101D] text-white">
      {/* 沉浸式头部 */}
      <div className="relative px-5 pt-12 pb-8 overflow-hidden bg-[url('/noise.png')]">
        <div className="absolute top-[-20%] right-[-20%] w-[120%] h-[150%] bg-purple-600 rounded-full mix-blend-screen filter blur-[80px] opacity-20" />
        <div className="relative z-10 space-y-3">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/5 border border-white/10 font-medium w-fit">
            <Gamepad2 className="w-3.5 h-3.5 text-purple-400" />
            <span className="text-[10px] text-slate-300 tracking-wider uppercase">Entertainment</span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight">游戏大厅</h1>
          <p className="text-xs text-slate-400 leading-relaxed max-w-[280px]">
            随时随地体验轻量级小游戏，即点即玩。
          </p>
          <a href={MORE_GAMES_URL} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-[11px] text-slate-300 active:text-white mt-1 underline underline-offset-2 opacity-70">
            <ExternalLink className="h-3 w-3" /> {MORE_GAMES_LABEL}
          </a>
        </div>
      </div>

      <div className="flex-1 px-4 pb-10">
        {/* 分类切换栏 */}
        <div className="flex p-1 rounded-xl bg-white/5 border border-white/5 w-full max-w-[200px] mb-6 shadow-inner">
          <button onClick={() => setActiveTab('featured')} className={cn("flex-1 py-1.5 text-xs font-medium rounded-lg transition-all", activeTab === 'featured' ? "bg-white/10 text-white shadow" : "text-slate-400")}>
            精选好玩
          </button>
          <button onClick={() => setActiveTab('all')} className={cn("flex-1 py-1.5 text-xs font-medium rounded-lg transition-all", activeTab === 'all' ? "bg-white/10 text-white shadow" : "text-slate-400")}>
            全部内容
          </button>
        </div>

        {/* 游戏卡片网格 */}
        <div className="grid grid-cols-1 gap-4">
          {GAMES.map((game, index) => (
            <MobileGameCard key={game.id} game={game} index={index} />
          ))}
        </div>
      </div>
      
      {/* 留出底部边距给导航栏 */}
      <div className="h-6" />
    </div>
  );
}
