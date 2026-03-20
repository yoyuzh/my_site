export const TRANSFER_CHUNK_SIZE = 64 * 1024;
export const SIGNAL_POLL_INTERVAL_MS = 1000;

interface TransferFileIdentity {
  name: string;
  lastModified: number;
  size: number;
}

export interface TransferFileDescriptor {
  id: string;
  name: string;
  size: number;
  contentType: string;
  relativePath: string;
}

export type TransferControlMessage =
  {
    type: 'manifest';
    files: TransferFileDescriptor[];
  }
  | {
      type: 'receive-request';
      fileIds: string[];
      archive: boolean;
    }
  | ({
      type: 'file-meta';
    } & TransferFileDescriptor)
  | {
      type: 'file-complete';
      id: string;
    }
  | {
      type: 'transfer-complete';
    };

export function createTransferFileId(file: TransferFileIdentity) {
  return `${file.name}-${file.lastModified}-${file.size}`;
}

export function getTransferFileRelativePath(file: File) {
  const rawRelativePath = ('webkitRelativePath' in file && typeof file.webkitRelativePath === 'string' && file.webkitRelativePath)
    ? file.webkitRelativePath
    : file.name;

  const normalizedPath = rawRelativePath
    .replaceAll('\\', '/')
    .split('/')
    .map((segment) => segment.trim())
    .filter(Boolean)
    .join('/');

  return normalizedPath || file.name;
}

export function createTransferFileManifest(files: File[]): TransferFileDescriptor[] {
  return files.map((file) => ({
    id: createTransferFileId(file),
    name: file.name,
    size: file.size,
    contentType: file.type || 'application/octet-stream',
    relativePath: getTransferFileRelativePath(file),
  }));
}

export function createTransferFileManifestMessage(files: TransferFileDescriptor[]) {
  return JSON.stringify({
    type: 'manifest',
    files,
  } satisfies TransferControlMessage);
}

export function createTransferReceiveRequestMessage(fileIds: string[], archive: boolean) {
  return JSON.stringify({
    type: 'receive-request',
    fileIds,
    archive,
  } satisfies TransferControlMessage);
}

export function createTransferFileMetaMessage(payload: TransferFileDescriptor) {
  return JSON.stringify({
    type: 'file-meta',
    ...payload,
  } satisfies TransferControlMessage);
}

export function createTransferFileCompleteMessage(id: string) {
  return JSON.stringify({
    type: 'file-complete',
    id,
  } satisfies TransferControlMessage);
}

export function createTransferCompleteMessage() {
  return JSON.stringify({
    type: 'transfer-complete',
  } satisfies TransferControlMessage);
}

export function parseTransferControlMessage(payload: string): TransferControlMessage | null {
  try {
    return JSON.parse(payload) as TransferControlMessage;
  } catch {
    return null;
  }
}

export async function toTransferChunk(data: ArrayBuffer | Blob) {
  if (data instanceof Blob) {
    return new Uint8Array(await data.arrayBuffer());
  }

  return new Uint8Array(data);
}
