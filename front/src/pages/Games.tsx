import React, { useState } from 'react';
import { motion } from 'motion/react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/src/components/ui/card';
import { Button } from '@/src/components/ui/button';
import { Gamepad2, Rocket, Cat, Car, Play } from 'lucide-react';
import { cn } from '@/src/lib/utils';

const GAMES = [
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

export default function Games() {
  const [activeTab, setActiveTab] = useState<'featured' | 'all'>('featured');

  return (
    <div className="space-y-8">
      {/* Hero Section */}
      <motion.div 
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass-panel rounded-3xl p-8 relative overflow-hidden"
      >
        <div className="absolute top-0 right-0 w-64 h-64 bg-purple-500 rounded-full mix-blend-screen filter blur-[100px] opacity-20" />
        <div className="relative z-10 space-y-4 max-w-2xl">
          <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/5 border border-white/10 w-fit">
            <Gamepad2 className="w-4 h-4 text-purple-400" />
            <span className="text-xs text-slate-300 font-medium tracking-wide uppercase">Entertainment</span>
          </div>
          <h1 className="text-3xl md:text-4xl font-bold text-white tracking-tight">游戏入口</h1>
          <p className="text-sm text-slate-400 leading-relaxed">
            保留轻量试玩与静态资源检查入口，维持与整站一致的毛玻璃语言。在这里您可以快速启动站内集成的小游戏。
          </p>
        </div>
      </motion.div>

      {/* Category Tabs */}
      <div className="flex bg-black/20 p-1 rounded-xl w-fit">
        <button
          onClick={() => setActiveTab('featured')}
          className={cn(
            "px-6 py-2 text-sm font-medium rounded-lg transition-all",
            activeTab === 'featured' ? "bg-white/10 text-white shadow-md" : "text-slate-400 hover:text-white"
          )}
        >
          精选
        </button>
        <button
          onClick={() => setActiveTab('all')}
          className={cn(
            "px-6 py-2 text-sm font-medium rounded-lg transition-all",
            activeTab === 'all' ? "bg-white/10 text-white shadow-md" : "text-slate-400 hover:text-white"
          )}
        >
          全部
        </button>
      </div>

      {/* Game Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {GAMES.map((game, index) => (
          <motion.div
            key={game.id}
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: index * 0.1 }}
          >
            <Card className="h-full flex flex-col hover:bg-white/[0.04] transition-colors group overflow-hidden relative">
              <div className={cn("absolute top-0 left-0 w-full h-1 bg-gradient-to-r", game.color)} />
              <CardHeader className="pb-4">
                <div className="flex items-start justify-between">
                  <div className={cn("w-12 h-12 rounded-2xl flex items-center justify-center bg-gradient-to-br shadow-lg", game.color)}>
                    <game.icon className="w-6 h-6 text-white" />
                  </div>
                  <span className="text-[10px] font-bold uppercase tracking-wider text-slate-500 bg-white/5 px-2 py-1 rounded-md">
                    {game.category}
                  </span>
                </div>
                <CardTitle className="text-xl mt-4">{game.name}</CardTitle>
                <CardDescription className="line-clamp-2 mt-2">
                  {game.description}
                </CardDescription>
              </CardHeader>
              <CardContent className="mt-auto pt-4">
                <Button className="w-full gap-2 group-hover:bg-white group-hover:text-black transition-all">
                  <Play className="w-4 h-4" fill="currentColor" /> Launch
                </Button>
              </CardContent>
            </Card>
          </motion.div>
        ))}
      </div>
    </div>
  );
}
