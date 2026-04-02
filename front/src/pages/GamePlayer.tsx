import React from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ExternalLink } from 'lucide-react';

import { Button } from '@/src/components/ui/button';

import { GAME_EXIT_PATH, isGameId, resolveGameHref } from './games-links';

export default function GamePlayer() {
  const navigate = useNavigate();
  const { gameId } = useParams<{ gameId: string }>();

  if (!gameId || !isGameId(gameId)) {
    return <Navigate to={GAME_EXIT_PATH} replace />;
  }

  const gameHref = resolveGameHref(gameId);

  return (
    <div className="space-y-6">
      <div className="glass-panel relative overflow-hidden rounded-3xl p-4 shadow-xl md:p-6">
        <div className="pointer-events-none absolute inset-x-0 top-0 h-20 bg-gradient-to-b from-white/10 to-transparent" />
        <div className="relative z-10 flex items-center justify-between gap-3">
          <div>
            <div className="text-xs uppercase tracking-[0.32em] text-slate-400">Game Session</div>
            <h1 className="mt-2 text-2xl font-bold text-white">{gameId.toUpperCase()}</h1>
          </div>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="glass"
              className="gap-2"
              onClick={() => navigate(GAME_EXIT_PATH)}
            >
              <ArrowLeft className="h-4 w-4" />
              退出游戏
            </Button>
            <Button
              type="button"
              variant="glass"
              className="gap-2"
              onClick={() => window.open(gameHref, '_blank', 'noopener,noreferrer')}
            >
              <ExternalLink className="h-4 w-4" />
              新窗口打开
            </Button>
          </div>
        </div>

        <div className="glass-panel relative mt-5 overflow-hidden rounded-2xl p-1 shadow-[0_24px_80px_rgba(0,0,0,0.5)]">
          <div className="relative overflow-hidden rounded-xl bg-black">
            <Button
              type="button"
              variant="glass"
              className="absolute left-4 top-4 z-10 gap-2 shadow-lg"
              onClick={() => navigate(GAME_EXIT_PATH)}
            >
              <ArrowLeft className="h-4 w-4" />
              退出
            </Button>
            <iframe
              title={`${gameId} game`}
              src={gameHref}
              className="h-[70vh] min-h-[560px] w-full border-0"
            />
          </div>
        </div>
      </div>
    </div>
  );
}
