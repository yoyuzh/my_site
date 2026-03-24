import React, { useRef, useState } from 'react';
import { motion } from 'motion/react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/src/components/ui/card';
import { Button } from '@/src/components/ui/button';
import { Gamepad2, Cat, Car, Play } from 'lucide-react';
import { cn } from '@/src/lib/utils';
import { calculateCardTilt } from './games-card-tilt';

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

function applyCardTilt(card: HTMLDivElement, rotateX: number, rotateY: number, glareX: number, glareY: number, scale: number) {
  card.style.setProperty('--card-rotate-x', `${rotateX}deg`);
  card.style.setProperty('--card-rotate-y', `${rotateY}deg`);
  card.style.setProperty('--card-glare-x', `${glareX}%`);
  card.style.setProperty('--card-glare-y', `${glareY}%`);
  card.style.setProperty('--card-scale', String(scale));
}

function GameCard({ game, index }: { game: (typeof GAMES)[number]; index: number }) {
  const cardRef = useRef<HTMLDivElement | null>(null);

  const handleMouseMove = (event: React.MouseEvent<HTMLDivElement>) => {
    const card = cardRef.current;
    if (!card) {
      return;
    }

    const rect = card.getBoundingClientRect();
    const tilt = calculateCardTilt(
      {
        clientX: event.clientX,
        clientY: event.clientY,
      },
      rect,
    );

    applyCardTilt(card, tilt.rotateX, tilt.rotateY, tilt.glareX, tilt.glareY, tilt.scale);
  };

  const handleMouseLeave = (event: React.MouseEvent<HTMLDivElement>) => {
    const card = cardRef.current;
    if (!card) {
      return;
    }

    const rect = card.getBoundingClientRect();
    const edgeTilt = calculateCardTilt(
      {
        clientX: event.clientX,
        clientY: event.clientY,
      },
      rect,
    );

    // Keep the highlight near the edge where the pointer exits and only flatten the card.
    applyCardTilt(card, 0, 0, edgeTilt.glareX, edgeTilt.glareY, 1);
  };

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ delay: index * 0.1 }}
      className="h-full [perspective:1400px]"
    >
      <Card
        ref={cardRef}
        onMouseMove={handleMouseMove}
        onMouseLeave={handleMouseLeave}
        className="group relative flex h-full flex-col overflow-hidden border-white/10 bg-white/[0.03] transition-[background-color,border-color,box-shadow,transform] duration-200 ease-out will-change-transform [transform:rotateX(var(--card-rotate-x,0deg))_rotateY(var(--card-rotate-y,0deg))_scale(var(--card-scale,1))] hover:border-white/20 hover:bg-white/[0.06] hover:shadow-[0_28px_80px_rgba(15,23,42,0.45)]"
      >
        <div
          className="pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-200 group-hover:opacity-100"
          style={{
            background:
              'radial-gradient(circle at var(--card-glare-x,50%) var(--card-glare-y,50%), rgba(255,255,255,0.24), rgba(255,255,255,0.08) 18%, rgba(255,255,255,0) 52%)',
          }}
        />
        <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(135deg,rgba(255,255,255,0.12),rgba(255,255,255,0)_38%,rgba(51,110,255,0.18)_100%)] opacity-70" />
        <div className={cn("absolute top-0 left-0 h-1 w-full bg-gradient-to-r", game.color)} />
        <CardHeader className="relative z-10 pb-4">
          <div className="flex items-start justify-between">
            <div className={cn("flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br shadow-lg", game.color)}>
              <game.icon className="h-6 w-6 text-white" />
            </div>
            <span className="rounded-md bg-white/5 px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-slate-500">
              {game.category}
            </span>
          </div>
          <CardTitle className="mt-4 text-xl">{game.name}</CardTitle>
          <CardDescription className="mt-2 line-clamp-2">
            {game.description}
          </CardDescription>
        </CardHeader>
        <CardContent className="relative z-10 mt-auto pt-4">
          <Button className="w-full gap-2 transition-all group-hover:bg-white group-hover:text-black">
            <Play className="h-4 w-4" fill="currentColor" /> Launch
          </Button>
        </CardContent>
      </Card>
    </motion.div>
  );
}

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
          <GameCard key={game.id} game={game} index={index} />
        ))}
      </div>
    </div>
  );
}
