import assert from 'node:assert/strict';
import test from 'node:test';

import {
  GAME_EXIT_PATH,
  MORE_GAMES_LABEL,
  MORE_GAMES_URL,
  isGameId,
  resolveGameHref,
  resolveGamePlayerPath,
} from './games-links';

test('resolveGameHref maps the cat game to the t_race OSS page', () => {
  assert.equal(resolveGameHref('cat'), '/t_race/');
});

test('resolveGameHref maps the race game to the race OSS page', () => {
  assert.equal(resolveGameHref('race'), '/race/');
});

test('resolveGamePlayerPath maps the cat game to the in-app player route', () => {
  assert.equal(resolveGamePlayerPath('cat'), '/games/cat');
});

test('resolveGamePlayerPath maps the race game to the in-app player route', () => {
  assert.equal(resolveGamePlayerPath('race'), '/games/race');
});

test('isGameId only accepts supported game ids', () => {
  assert.equal(isGameId('cat'), true);
  assert.equal(isGameId('race'), true);
  assert.equal(isGameId('t_race'), false);
});

test('GAME_EXIT_PATH points back to the games lobby', () => {
  assert.equal(GAME_EXIT_PATH, '/games');
});

test('MORE_GAMES_URL points to the requested external games site', () => {
  assert.equal(MORE_GAMES_URL, 'https://quruifps.xyz');
});

test('MORE_GAMES_LABEL keeps the friendly-link copy', () => {
  assert.equal(MORE_GAMES_LABEL, '更多游戏请访问quruifps.xyz');
});
