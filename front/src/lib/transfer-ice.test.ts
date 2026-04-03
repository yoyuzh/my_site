import assert from 'node:assert/strict';
import test from 'node:test';

import {
  DEFAULT_TRANSFER_ICE_SERVERS,
  hasRelayTransferIceServer,
  resolveTransferIceServers,
} from './transfer-ice';

test('resolveTransferIceServers falls back to the default STUN list when no custom config is provided', () => {
  assert.deepEqual(resolveTransferIceServers(), DEFAULT_TRANSFER_ICE_SERVERS);
  assert.deepEqual(resolveTransferIceServers(''), DEFAULT_TRANSFER_ICE_SERVERS);
  assert.deepEqual(resolveTransferIceServers('not-json'), DEFAULT_TRANSFER_ICE_SERVERS);
});

test('resolveTransferIceServers appends custom TURN servers after the default STUN list', () => {
  const iceServers = resolveTransferIceServers(JSON.stringify([
    {
      urls: ['turn:turn.yoyuzh.xyz:3478?transport=udp', 'turns:turn.yoyuzh.xyz:5349'],
      username: 'portal-user',
      credential: 'portal-secret',
    },
  ]));

  assert.deepEqual(iceServers, [
    ...DEFAULT_TRANSFER_ICE_SERVERS,
    {
      urls: ['turn:turn.yoyuzh.xyz:3478?transport=udp', 'turns:turn.yoyuzh.xyz:5349'],
      username: 'portal-user',
      credential: 'portal-secret',
    },
  ]);
});

test('hasRelayTransferIceServer detects whether TURN relay servers are configured', () => {
  assert.equal(hasRelayTransferIceServer(DEFAULT_TRANSFER_ICE_SERVERS), false);
  assert.equal(hasRelayTransferIceServer(resolveTransferIceServers(JSON.stringify([
    {
      urls: 'turn:turn.yoyuzh.xyz:3478?transport=udp',
      username: 'portal-user',
      credential: 'portal-secret',
    },
  ]))), true);
});
