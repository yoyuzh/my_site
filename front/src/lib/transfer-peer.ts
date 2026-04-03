import Peer from 'simple-peer/simplepeer.min.js';
import type { Instance as SimplePeerInstance, Options as SimplePeerOptions, SignalData } from 'simple-peer';

export type TransferPeerPayload = string | Uint8Array | ArrayBuffer | Blob;

const TRANSFER_PEER_BUFFER_POLL_INTERVAL_MS = 16;

interface TransferPeerLike {
  bufferSize?: number;
  connected?: boolean;
  destroyed?: boolean;
  on(event: 'signal', listener: (signal: SignalData) => void): this;
  on(event: 'connect', listener: () => void): this;
  on(event: 'data', listener: (data: TransferPeerPayload) => void): this;
  on(event: 'close', listener: () => void): this;
  on(event: 'error', listener: (error: Error) => void): this;
  once?(event: 'drain', listener: () => void): this;
  removeListener?(event: 'drain', listener: () => void): this;
  signal(signal: SignalData): void;
  send(payload: TransferPeerPayload): void;
  write?(payload: TransferPeerPayload): boolean;
  destroy(): void;
}

export interface TransferPeerAdapter {
  readonly connected: boolean;
  readonly destroyed: boolean;
  applyRemoteSignal(payload: string): void;
  send(payload: TransferPeerPayload): void;
  write(payload: TransferPeerPayload): Promise<void>;
  destroy(): void;
}

export interface CreateTransferPeerOptions {
  initiator: boolean;
  trickle?: boolean;
  peerOptions?: Omit<SimplePeerOptions, 'initiator' | 'trickle'>;
  onSignal?: (payload: string) => void;
  onConnect?: () => void;
  onData?: (payload: TransferPeerPayload) => void;
  onClose?: () => void;
  onError?: (error: Error) => void;
  createPeer?: (options: SimplePeerOptions) => TransferPeerLike;
}

export function serializeTransferPeerSignal(signal: SignalData) {
  return JSON.stringify(signal);
}

export function parseTransferPeerSignal(payload: string) {
  return JSON.parse(payload) as SignalData;
}

function waitForPeerBufferToClear(peer: TransferPeerLike) {
  if (!peer.bufferSize || peer.bufferSize <= 0) {
    return Promise.resolve();
  }

  return new Promise<void>((resolve) => {
    let settled = false;
    let pollTimer: ReturnType<typeof setInterval> | null = null;

    const finish = () => {
      if (settled) {
        return;
      }
      settled = true;
      if (pollTimer) {
        clearInterval(pollTimer);
      }
      if (peer.removeListener) {
        peer.removeListener('drain', finish);
      }
      resolve();
    };

    peer.once?.('drain', finish);
    pollTimer = setInterval(() => {
      if (peer.destroyed || !peer.bufferSize || peer.bufferSize <= 0) {
        finish();
      }
    }, TRANSFER_PEER_BUFFER_POLL_INTERVAL_MS);
  });
}

export function createTransferPeer(options: CreateTransferPeerOptions): TransferPeerAdapter {
  const peerFactory = options.createPeer ?? ((peerOptions: SimplePeerOptions) => new Peer(peerOptions) as SimplePeerInstance);
  const peer = peerFactory({
    initiator: options.initiator,
    objectMode: true,
    trickle: options.trickle ?? true,
    ...options.peerOptions,
  });

  peer.on('signal', (signal) => {
    options.onSignal?.(serializeTransferPeerSignal(signal));
  });
  peer.on('connect', () => {
    options.onConnect?.();
  });
  peer.on('data', (payload) => {
    options.onData?.(payload);
  });
  peer.on('close', () => {
    options.onClose?.();
  });
  peer.on('error', (error) => {
    options.onError?.(error instanceof Error ? error : new Error(String(error)));
  });

  return {
    get connected() {
      return Boolean(peer.connected);
    },
    get destroyed() {
      return Boolean(peer.destroyed);
    },
    applyRemoteSignal(payload: string) {
      peer.signal(parseTransferPeerSignal(payload));
    },
    send(payload: TransferPeerPayload) {
      peer.send(payload);
    },
    async write(payload: TransferPeerPayload) {
      if (!peer.write) {
        peer.send(payload);
        await waitForPeerBufferToClear(peer);
        return;
      }

      peer.write(payload);
      await waitForPeerBufferToClear(peer);
    },
    destroy() {
      peer.destroy();
    },
  };
}
