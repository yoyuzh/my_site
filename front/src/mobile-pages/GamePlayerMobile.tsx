import React from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ExternalLink } from 'lucide-react';

import { Button } from '@/src/components/ui/button';
import { GAME_EXIT_PATH, isGameId, resolveGameHref } from '@/src/pages/games-links';

export default function GamePlayerMobile() {
  const navigate = useNavigate();
  const { gameId } = useParams<{ gameId: string }>();

  if (!gameId || !isGameId(gameId)) {
    return <Navigate to={GAME_EXIT_PATH} replace />;
  }

  const gameHref = resolveGameHref(gameId);

  return (
    <div className="flex flex-col h-[100dvh] bg-black">
      {/* 沉浸式顶部返回栏 */}
      <div className="flex items-center justify-between px-4 py-3 bg-gradient-to-b from-black/80 to-transparent fixed top-0 left-0 right-0 z-20">
        <Button
          type="button"
          onClick={() => navigate(GAME_EXIT_PATH)}
          className="bg-black/40 hover:bg-black/60 text-white rounded-full p-2 h-10 w-10 backdrop-blur-md border border-white/10"
        >
          <ArrowLeft className="w-5 h-5" />
        </Button>
        <div className="text-white font-bold tracking-widest uppercase text-sm drop-shadow-md">
          {gameId}
        </div>
        <Button
          type="button"
          onClick={() => window.open(gameHref, '_blank', 'noopener,noreferrer')}
          className="bg-black/40 hover:bg-black/60 text-white rounded-full p-2 h-10 w-10 backdrop-blur-md border border-white/10"
        >
          <ExternalLink className="w-5 h-5" />
        </Button>
      </div>

      {/* 沉浸式全屏播放器 */}
      <div className="flex-1 w-full h-full relative z-10 pt-16">
        <iframe
          title={`${gameId} game`}
          src={gameHref}
          className="w-full h-full border-0 rounded-t-3xl shadow-[0_0_40px_rgba(51,110,255,0.15)] bg-black"
        />
      </div>
    </div>
  );
}
