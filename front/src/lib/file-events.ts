import { apiDownload } from './api';

export type FileEventType = 'CREATED' | 'UPDATED' | 'RENAMED' | 'MOVED' | 'DELETED' | 'RESTORED';

export interface FileEventMessage {
  eventType: FileEventType;
  fileId?: number | null;
  fromPath?: string | null;
  toPath?: string | null;
  clientId?: string | null;
  createdAt?: string;
  payload?: unknown;
}

export interface FileEventsSubscription {
  close: () => void;
}

interface SubscribeFileEventsOptions {
  onError?: (error: unknown) => void;
  onFileEvent: (event: FileEventMessage) => void;
  path: string;
}

const READY_EVENT_TYPE = 'READY';
const FILE_EVENT_TYPES = new Set<FileEventType>([
  'CREATED',
  'UPDATED',
  'RENAMED',
  'MOVED',
  'DELETED',
  'RESTORED',
]);
const FILE_EVENTS_RECONNECT_INITIAL_DELAY_MS = 1000;
const FILE_EVENTS_RECONNECT_MAX_DELAY_MS = 5000;
const FILE_EVENTS_RECONNECT_MULTIPLIER = 1.5;

export function getFileEventsReconnectDelayMs(attempt: number) {
  return Math.min(
    Math.round(FILE_EVENTS_RECONNECT_INITIAL_DELAY_MS * FILE_EVENTS_RECONNECT_MULTIPLIER ** Math.max(0, attempt)),
    FILE_EVENTS_RECONNECT_MAX_DELAY_MS,
  );
}

function sleep(ms: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal.aborted) {
      reject(signal.reason);
      return;
    }

    const timeoutId = setTimeout(() => {
      signal.removeEventListener('abort', handleAbort);
      resolve();
    }, ms);

    const handleAbort = () => {
      clearTimeout(timeoutId);
      signal.removeEventListener('abort', handleAbort);
      reject(signal.reason);
    };

    signal.addEventListener('abort', handleAbort, { once: true });
  });
}

export function buildFileEventsPath(path: string) {
  const normalizedPath = path.trim() || '/';
  const searchParams = new URLSearchParams({
    path: normalizedPath.startsWith('/') ? normalizedPath : `/${normalizedPath}`,
  });
  return `/files/events?${searchParams.toString()}`;
}

function parseSseBlock(block: string): FileEventMessage | null {
  const dataLines: string[] = [];
  let eventName = '';

  for (const line of block.split('\n')) {
    if (!line || line.startsWith(':')) {
      continue;
    }

    const separatorIndex = line.indexOf(':');
    const field = separatorIndex >= 0 ? line.slice(0, separatorIndex) : line;
    const rawValue = separatorIndex >= 0 ? line.slice(separatorIndex + 1) : '';
    const value = rawValue.startsWith(' ') ? rawValue.slice(1) : rawValue;

    if (field === 'event') {
      eventName = value;
    } else if (field === 'data') {
      dataLines.push(value);
    }
  }

  if (eventName === READY_EVENT_TYPE || dataLines.length === 0) {
    return null;
  }

  const payload = JSON.parse(dataLines.join('\n')) as {
    clientId?: string | null;
    createdAt?: string;
    eventType?: string;
    fileId?: number | null;
    fromPath?: string | null;
    payload?: unknown;
    toPath?: string | null;
  };
  if (payload.eventType === READY_EVENT_TYPE) {
    return null;
  }

  const eventType = payload.eventType || eventName;
  if (!FILE_EVENT_TYPES.has(eventType as FileEventType)) {
    return null;
  }

  return {
    ...payload,
    eventType: eventType as FileEventType,
  };
}

export function createSseEventParser() {
  let buffer = '';

  return {
    push(chunk: string) {
      buffer += chunk.replace(/\r\n/g, '\n');
      const events: FileEventMessage[] = [];

      while (true) {
        const eventBoundary = buffer.indexOf('\n\n');
        if (eventBoundary < 0) {
          break;
        }

        const block = buffer.slice(0, eventBoundary);
        buffer = buffer.slice(eventBoundary + 2);
        const event = parseSseBlock(block);
        if (event) {
          events.push(event);
        }
      }

      return events;
    },
  };
}

export function subscribeFileEvents({
  onError,
  onFileEvent,
  path,
}: SubscribeFileEventsOptions): FileEventsSubscription {
  const abortController = new AbortController();
  let closed = false;
  let reader: ReadableStreamDefaultReader<Uint8Array> | null = null;

  const readStream = async () => {
    let reconnectAttempt = 0;

    while (!closed) {
      let streamHadData = false;
      try {
        const parser = createSseEventParser();
        const response = await apiDownload(`/v2${buildFileEventsPath(path)}`, {
          signal: abortController.signal,
        });

        if (!response.body) {
          throw new Error('文件事件流不可用');
        }

        reader = response.body.getReader();
        const decoder = new TextDecoder();

        while (!closed) {
          const { done, value } = await reader.read();
          if (done) {
            break;
          }

          streamHadData = true;
          for (const event of parser.push(decoder.decode(value, { stream: true }))) {
            onFileEvent(event);
          }
        }
      } catch (error) {
        if (!closed) {
          onError?.(error);
        }
      } finally {
        reader?.releaseLock();
        reader = null;
      }

      if (closed) {
        break;
      }

      const nextAttempt = streamHadData ? 0 : reconnectAttempt;
      await sleep(getFileEventsReconnectDelayMs(nextAttempt), abortController.signal).catch(() => undefined);
      reconnectAttempt = streamHadData ? 0 : reconnectAttempt + 1;
      if (abortController.signal.aborted) {
        break;
      }
    }
  };

  void readStream();

  return {
    close() {
      closed = true;
      abortController.abort();
      reader?.cancel().catch(() => undefined);
    },
  };
}
