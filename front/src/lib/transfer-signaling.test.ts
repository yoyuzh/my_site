import assert from 'node:assert/strict';
import test from 'node:test';

import {
  flushPendingRemoteIceCandidates,
  handleRemoteIceCandidate,
} from './transfer-signaling';

test('handleRemoteIceCandidate defers candidates until the remote description exists', async () => {
  const appliedCandidates: RTCIceCandidateInit[] = [];
  const connection = {
    remoteDescription: null,
    addIceCandidate: async (candidate: RTCIceCandidateInit) => {
      appliedCandidates.push(candidate);
    },
  };
  const candidate: RTCIceCandidateInit = {
    candidate: 'candidate:1 1 udp 2122260223 10.0.0.2 54321 typ host',
    sdpMid: '0',
    sdpMLineIndex: 0,
  };

  const pendingCandidates = await handleRemoteIceCandidate(connection, [], candidate);

  assert.deepEqual(appliedCandidates, []);
  assert.deepEqual(pendingCandidates, [candidate]);
});

test('flushPendingRemoteIceCandidates applies queued candidates after the remote description is set', async () => {
  const appliedCandidates: RTCIceCandidateInit[] = [];
  const connection = {
    remoteDescription: { type: 'answer' } as RTCSessionDescription,
    addIceCandidate: async (candidate: RTCIceCandidateInit) => {
      appliedCandidates.push(candidate);
    },
  };
  const pendingCandidates: RTCIceCandidateInit[] = [
    {
      candidate: 'candidate:1 1 udp 2122260223 10.0.0.2 54321 typ host',
      sdpMid: '0',
      sdpMLineIndex: 0,
    },
    {
      candidate: 'candidate:2 1 udp 2122260223 10.0.0.3 54322 typ host',
      sdpMid: '0',
      sdpMLineIndex: 0,
    },
  ];

  const remainingCandidates = await flushPendingRemoteIceCandidates(connection, pendingCandidates);

  assert.deepEqual(appliedCandidates, pendingCandidates);
  assert.deepEqual(remainingCandidates, []);
});
