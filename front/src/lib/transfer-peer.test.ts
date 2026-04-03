import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import test from 'node:test';

import {
  createTransferPeer,
  parseTransferPeerSignal,
  serializeTransferPeerSignal,
  type TransferPeerPayload,
} from './transfer-peer';

class FakePeer extends EventEmitter {
  destroyed = false;
  sent: Array<string | Uint8Array | ArrayBuffer> = [];
  signaled: unknown[] = [];
  writeReturnValue = true;
  bufferSize = 0;

  send(payload: string | Uint8Array | ArrayBuffer) {
    this.sent.push(payload);
  }

  write(payload: string | Uint8Array | ArrayBuffer) {
    this.sent.push(payload);
    return this.writeReturnValue;
  }

  signal(payload: unknown) {
    this.signaled.push(payload);
  }

  destroy() {
    this.destroyed = true;
    this.emit('close');
  }
}

test('serializeTransferPeerSignal and parseTransferPeerSignal preserve signal payloads', () => {
  const payload = {
    type: 'offer' as const,
    sdp: 'v=0',
  };

  assert.deepEqual(parseTransferPeerSignal(serializeTransferPeerSignal(payload)), payload);
});

test('createTransferPeer forwards local simple-peer signals to the app layer', () => {
  const fakePeer = new FakePeer();
  const seenSignals: string[] = [];
  let receivedOptions: Record<string, unknown> | null = null;

  createTransferPeer({
    initiator: true,
    onSignal: (payload) => {
      seenSignals.push(payload);
    },
    createPeer: (options) => {
      receivedOptions = options as Record<string, unknown>;
      return fakePeer as never;
    },
  });

  fakePeer.emit('signal', {
    type: 'answer' as const,
    sdp: 'v=0',
  });

  assert.deepEqual(seenSignals, [JSON.stringify({ type: 'answer', sdp: 'v=0' })]);
  assert.equal(receivedOptions?.objectMode, true);
});

test('createTransferPeer routes remote signals, data, connect, close, and error events through the adapter', () => {
  const fakePeer = new FakePeer();
  let connected = 0;
  let closed = 0;
  const dataPayloads: TransferPeerPayload[] = [];
  const errors: string[] = [];

  const peer = createTransferPeer({
    initiator: false,
    onConnect: () => {
      connected += 1;
    },
    onData: (payload) => {
      dataPayloads.push(payload);
    },
    onClose: () => {
      closed += 1;
    },
    onError: (error) => {
      errors.push(error.message);
    },
    createPeer: () => fakePeer as never,
  });

  peer.applyRemoteSignal(JSON.stringify({ candidate: 'candidate:1' }));
  peer.send('hello');
  fakePeer.emit('connect');
  fakePeer.emit('data', 'payload');
  fakePeer.emit('error', new Error('boom'));
  peer.destroy();

  assert.deepEqual(fakePeer.signaled, [{ candidate: 'candidate:1' }]);
  assert.deepEqual(fakePeer.sent, ['hello']);
  assert.equal(connected, 1);
  assert.deepEqual(dataPayloads, ['payload']);
  assert.deepEqual(errors, ['boom']);
  assert.equal(closed, 1);
  assert.equal(fakePeer.destroyed, true);
});

test('createTransferPeer waits for drain when the wrapped peer applies backpressure', async () => {
  const fakePeer = new FakePeer();
  fakePeer.bufferSize = 2048;
  const peer = createTransferPeer({
    initiator: true,
    createPeer: () => fakePeer as never,
  });

  let completed = false;
  const writePromise = peer.write('chunk').then(() => {
    completed = true;
  });

  await new Promise((resolve) => setTimeout(resolve, 5));
  assert.equal(completed, false);

  fakePeer.emit('drain');
  await writePromise;
  assert.equal(completed, true);
});

test('createTransferPeer falls back to bufferSize polling when drain is not emitted', async () => {
  const fakePeer = new FakePeer();
  fakePeer.bufferSize = 2048;
  const peer = createTransferPeer({
    initiator: true,
    createPeer: () => fakePeer as never,
  });

  let completed = false;
  const writePromise = peer.write('chunk').then(() => {
    completed = true;
  });

  await new Promise((resolve) => setTimeout(resolve, 5));
  assert.equal(completed, false);

  fakePeer.bufferSize = 0;
  await writePromise;
  assert.equal(completed, true);
  assert.deepEqual(fakePeer.sent, ['chunk']);
});
