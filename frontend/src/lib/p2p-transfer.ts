import type { TransferSessionResponse, TransferSignalEnvelope } from '../api/types';
import { pollTransferSignals, postTransferSignal } from './transfer';

const SIGNAL_POLL_INTERVAL_MS = 900;
const CHUNK_SIZE = 64 * 1024;
const MAX_BUFFERED_AMOUNT = 4 * 1024 * 1024;

const ICE_SERVERS: RTCIceServer[] = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:global.stun.twilio.com:3478' },
];

type TransferRole = 'sender' | 'receiver';
type SignalType = 'webrtc-offer' | 'webrtc-answer' | 'webrtc-ice-candidate';

export interface P2pTransferProgress {
  fileName: string;
  fileBytes: number;
  fileSentBytes: number;
  totalBytes: number;
  sentBytes: number;
}

export interface ReceivedP2pFile {
  id: string;
  name: string;
  relativePath: string;
  size: number;
  contentType: string;
  url: string;
}

interface P2pCallbacks {
  onStatus?: (message: string) => void;
  onError?: (message: string) => void;
  onProgress?: (progress: P2pTransferProgress) => void;
  onFileReceived?: (file: ReceivedP2pFile) => void;
  onComplete?: () => void;
}

interface P2pFileManifest {
  id: string;
  name: string;
  relativePath: string;
  size: number;
  contentType: string;
}

type TransferControlMessage =
  | { kind: 'manifest'; files: P2pFileManifest[] }
  | { kind: 'file-start'; file: P2pFileManifest }
  | { kind: 'file-end'; id: string }
  | { kind: 'transfer-complete' };

function assertWebRtcSupported() {
  if (!window.RTCPeerConnection) {
    throw new Error('当前浏览器不支持 WebRTC，无法使用在线 P2P 快传');
  }
}

function createPeerConnection() {
  return new RTCPeerConnection({ iceServers: ICE_SERVERS });
}

function safeJsonParse<T>(value: string): T | null {
  try {
    return JSON.parse(value) as T;
  } catch {
    return null;
  }
}

function createId() {
  return typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

function getRelativePath(file: File) {
  return 'webkitRelativePath' in file &&
    typeof file.webkitRelativePath === 'string' &&
    file.webkitRelativePath.length > 0
    ? file.webkitRelativePath
    : file.name;
}

function toManifest(files: File[]): P2pFileManifest[] {
  return files.map((file) => ({
    id: createId(),
    name: file.name,
    relativePath: getRelativePath(file),
    size: file.size,
    contentType: file.type || 'application/octet-stream',
  }));
}

function sleep(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

async function waitForBufferedAmount(channel: RTCDataChannel) {
  if (channel.bufferedAmount <= MAX_BUFFERED_AMOUNT) {
    return;
  }

  await new Promise<void>((resolve, reject) => {
    const previousLowHandler = channel.onbufferedamountlow;
    const previousCloseHandler = channel.onclose;
    const previousErrorHandler = channel.onerror;

    channel.bufferedAmountLowThreshold = MAX_BUFFERED_AMOUNT / 2;
    channel.onbufferedamountlow = (event) => {
      previousLowHandler?.call(channel, event);
      channel.onbufferedamountlow = previousLowHandler;
      channel.onclose = previousCloseHandler;
      channel.onerror = previousErrorHandler;
      resolve();
    };
    channel.onclose = (event) => {
      previousCloseHandler?.call(channel, event);
      reject(new Error('P2P 连接已关闭'));
    };
    channel.onerror = (event) => {
      previousErrorHandler?.call(channel, event);
      reject(new Error('P2P 连接异常'));
    };
  });
}

async function addCandidateOrQueue(
  peer: RTCPeerConnection,
  candidate: RTCIceCandidateInit,
  pendingCandidates: RTCIceCandidateInit[],
) {
  if (!peer.remoteDescription) {
    pendingCandidates.push(candidate);
    return;
  }
  await peer.addIceCandidate(candidate);
}

async function flushCandidates(peer: RTCPeerConnection, pendingCandidates: RTCIceCandidateInit[]) {
  while (peer.remoteDescription && pendingCandidates.length > 0) {
    const candidate = pendingCandidates.shift();
    if (candidate) {
      await peer.addIceCandidate(candidate);
    }
  }
}

async function pollSignals(
  sessionId: string,
  role: TransferRole,
  isStopped: () => boolean,
  handleSignal: (signal: TransferSignalEnvelope) => Promise<void>,
) {
  let cursor = 0;
  while (!isStopped()) {
    const response = await pollTransferSignals(sessionId, role, cursor);
    cursor = response.nextCursor;
    for (const signal of response.items) {
      await handleSignal(signal);
    }
    await sleep(SIGNAL_POLL_INTERVAL_MS);
  }
}

class P2pTransferBase {
  protected stopped = false;
  protected peer: RTCPeerConnection | null = null;
  protected pendingCandidates: RTCIceCandidateInit[] = [];

  constructor(
    protected readonly sessionId: string,
    protected readonly role: TransferRole,
    protected readonly callbacks: P2pCallbacks,
  ) {}

  stop() {
    this.stopped = true;
    this.peer?.close();
    this.peer = null;
  }

  protected isStopped = () => this.stopped;

  protected reportError(error: unknown) {
    this.callbacks.onError?.(error instanceof Error ? error.message : 'P2P 快传失败');
  }

  protected async postSignal(type: SignalType, payload: unknown) {
    await postTransferSignal(this.sessionId, this.role, type, JSON.stringify(payload));
  }

  protected configurePeer(peer: RTCPeerConnection) {
    peer.onicecandidate = (event) => {
      if (event.candidate) {
        void this.postSignal('webrtc-ice-candidate', event.candidate.toJSON()).catch((error) => this.reportError(error));
      }
    };
    peer.onconnectionstatechange = () => {
      if (peer.connectionState === 'connected') {
        this.callbacks.onStatus?.('P2P 连接已建立');
      } else if (peer.connectionState === 'failed') {
        this.callbacks.onError?.('P2P 连接失败，当前网络可能需要 TURN 中继');
      } else if (peer.connectionState === 'disconnected') {
        this.callbacks.onStatus?.('P2P 连接已断开');
      }
    };
  }
}

export class P2pSender extends P2pTransferBase {
  private channel: RTCDataChannel | null = null;
  private manifest: P2pFileManifest[];

  constructor(
    session: TransferSessionResponse,
    private readonly files: File[],
    callbacks: P2pCallbacks,
  ) {
    super(session.sessionId, 'sender', callbacks);
    this.manifest = toManifest(files);
  }

  async start() {
    assertWebRtcSupported();
    const peer = createPeerConnection();
    this.peer = peer;
    this.configurePeer(peer);

    const channel = peer.createDataChannel('yoyuzh-online-transfer', { ordered: true });
    this.channel = channel;
    channel.binaryType = 'arraybuffer';
    channel.onopen = () => {
      this.callbacks.onStatus?.('接收端已连接，开始发送文件');
      void this.sendFiles().catch((error) => this.reportError(error));
    };
    channel.onclose = () => this.callbacks.onStatus?.('发送通道已关闭');
    channel.onerror = () => this.callbacks.onError?.('发送通道异常');

    const offer = await peer.createOffer();
    await peer.setLocalDescription(offer);
    await this.postSignal('webrtc-offer', offer);
    this.callbacks.onStatus?.('已创建取件码，等待接收端加入');

    void pollSignals(this.sessionId, 'sender', this.isStopped, (signal) => this.handleSignal(signal)).catch((error) => {
      if (!this.stopped) {
        this.reportError(error);
      }
    });
  }

  private async handleSignal(signal: TransferSignalEnvelope) {
    if (!this.peer) {
      return;
    }
    if (signal.type === 'peer-joined') {
      this.callbacks.onStatus?.('接收端已加入，正在建立 P2P 连接');
      return;
    }
    if (signal.type === 'webrtc-answer') {
      const answer = safeJsonParse<RTCSessionDescriptionInit>(signal.payload);
      if (answer) {
        await this.peer.setRemoteDescription(answer);
        await flushCandidates(this.peer, this.pendingCandidates);
      }
      return;
    }
    if (signal.type === 'webrtc-ice-candidate') {
      const candidate = safeJsonParse<RTCIceCandidateInit>(signal.payload);
      if (candidate) {
        await addCandidateOrQueue(this.peer, candidate, this.pendingCandidates);
      }
    }
  }

  private async sendFiles() {
    const channel = this.channel;
    if (!channel || channel.readyState !== 'open') {
      throw new Error('P2P 发送通道未打开');
    }

    const totalBytes = this.files.reduce((sum, file) => sum + file.size, 0);
    let sentBytes = 0;
    channel.send(JSON.stringify({ kind: 'manifest', files: this.manifest } satisfies TransferControlMessage));

    for (let fileIndex = 0; fileIndex < this.files.length; fileIndex++) {
      const file = this.files[fileIndex];
      const manifest = this.manifest[fileIndex];
      let fileSentBytes = 0;
      channel.send(JSON.stringify({ kind: 'file-start', file: manifest } satisfies TransferControlMessage));

      for (let offset = 0; offset < file.size; offset += CHUNK_SIZE) {
        const chunk = await file.slice(offset, offset + CHUNK_SIZE).arrayBuffer();
        channel.send(chunk);
        fileSentBytes += chunk.byteLength;
        sentBytes += chunk.byteLength;
        this.callbacks.onProgress?.({
          fileName: manifest.relativePath,
          fileBytes: manifest.size,
          fileSentBytes,
          totalBytes,
          sentBytes,
        });
        await waitForBufferedAmount(channel);
      }

      channel.send(JSON.stringify({ kind: 'file-end', id: manifest.id } satisfies TransferControlMessage));
    }

    channel.send(JSON.stringify({ kind: 'transfer-complete' } satisfies TransferControlMessage));
    this.callbacks.onStatus?.('文件发送完成');
    this.callbacks.onComplete?.();
  }
}

export class P2pReceiver extends P2pTransferBase {
  private currentFile: P2pFileManifest | null = null;
  private currentChunks: ArrayBuffer[] = [];
  private currentReceivedBytes = 0;
  private totalBytes = 0;
  private receivedBytes = 0;

  constructor(sessionId: string, callbacks: P2pCallbacks) {
    super(sessionId, 'receiver', callbacks);
  }

  async start() {
    assertWebRtcSupported();
    const peer = createPeerConnection();
    this.peer = peer;
    this.configurePeer(peer);

    peer.ondatachannel = (event) => {
      const channel = event.channel;
      channel.binaryType = 'arraybuffer';
      channel.onopen = () => this.callbacks.onStatus?.('P2P 接收通道已打开');
      channel.onmessage = (messageEvent) => void this.handleChannelMessage(messageEvent.data).catch((error) => this.reportError(error));
      channel.onclose = () => this.callbacks.onStatus?.('P2P 接收通道已关闭');
      channel.onerror = () => this.callbacks.onError?.('P2P 接收通道异常');
    };

    this.callbacks.onStatus?.('已加入会话，等待发送端信令');
    void pollSignals(this.sessionId, 'receiver', this.isStopped, (signal) => this.handleSignal(signal)).catch((error) => {
      if (!this.stopped) {
        this.reportError(error);
      }
    });
  }

  private async handleSignal(signal: TransferSignalEnvelope) {
    if (!this.peer) {
      return;
    }
    if (signal.type === 'webrtc-offer') {
      const offer = safeJsonParse<RTCSessionDescriptionInit>(signal.payload);
      if (!offer) {
        return;
      }
      await this.peer.setRemoteDescription(offer);
      await flushCandidates(this.peer, this.pendingCandidates);
      const answer = await this.peer.createAnswer();
      await this.peer.setLocalDescription(answer);
      await this.postSignal('webrtc-answer', answer);
      this.callbacks.onStatus?.('已回应发送端，正在建立 P2P 连接');
      return;
    }
    if (signal.type === 'webrtc-ice-candidate') {
      const candidate = safeJsonParse<RTCIceCandidateInit>(signal.payload);
      if (candidate) {
        await addCandidateOrQueue(this.peer, candidate, this.pendingCandidates);
      }
    }
  }

  private async handleChannelMessage(data: string | ArrayBuffer | Blob) {
    if (typeof data === 'string') {
      this.handleControlMessage(data);
      return;
    }

    const chunk = data instanceof Blob ? await data.arrayBuffer() : data;
    if (!this.currentFile) {
      throw new Error('收到文件分片前缺少文件元数据');
    }
    this.currentChunks.push(chunk);
    this.currentReceivedBytes += chunk.byteLength;
    this.receivedBytes += chunk.byteLength;
    this.callbacks.onProgress?.({
      fileName: this.currentFile.relativePath,
      fileBytes: this.currentFile.size,
      fileSentBytes: this.currentReceivedBytes,
      totalBytes: this.totalBytes,
      sentBytes: this.receivedBytes,
    });
  }

  private handleControlMessage(raw: string) {
    const message = safeJsonParse<TransferControlMessage>(raw);
    if (!message) {
      return;
    }

    if (message.kind === 'manifest') {
      this.totalBytes = message.files.reduce((sum, file) => sum + file.size, 0);
      this.callbacks.onStatus?.(`准备接收 ${message.files.length} 个文件`);
      return;
    }

    if (message.kind === 'file-start') {
      this.currentFile = message.file;
      this.currentChunks = [];
      this.currentReceivedBytes = 0;
      this.callbacks.onStatus?.(`正在接收：${message.file.relativePath}`);
      return;
    }

    if (message.kind === 'file-end') {
      if (!this.currentFile || this.currentFile.id !== message.id) {
        this.callbacks.onError?.('文件结束标记不匹配');
        return;
      }
      const blob = new Blob(this.currentChunks, { type: this.currentFile.contentType });
      this.callbacks.onFileReceived?.({
        ...this.currentFile,
        url: URL.createObjectURL(blob),
      });
      this.currentFile = null;
      this.currentChunks = [];
      this.currentReceivedBytes = 0;
      return;
    }

    if (message.kind === 'transfer-complete') {
      this.callbacks.onStatus?.('文件接收完成');
      this.callbacks.onComplete?.();
    }
  }
}
