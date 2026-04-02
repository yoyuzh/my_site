const GAME_HREFS = {
  cat: '/t_race/',
  race: '/race/',
} as const;

export type GameId = keyof typeof GAME_HREFS;
export const GAME_EXIT_PATH = '/games';
export const MORE_GAMES_URL = 'https://quruifps.xyz';
export const MORE_GAMES_LABEL = '更多游戏请访问quruifps.xyz';

export function resolveGameHref(gameId: GameId) {
  return GAME_HREFS[gameId];
}

export function resolveGamePlayerPath(gameId: GameId) {
  return `${GAME_EXIT_PATH}/${gameId}`;
}

export function isGameId(value: string): value is GameId {
  return value in GAME_HREFS;
}
