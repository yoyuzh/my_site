import type { TransferMode } from '../lib/types';
import type { TransferFileDescriptor } from '../lib/transfer-protocol';

export type TransferTab = 'send' | 'receive';

export function createMockTransferCode() {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

export function sanitizeReceiveCode(value: string) {
  return value.replace(/\D/g, '').slice(0, 6);
}

export function formatTransferSize(bytes: number) {
  if (bytes <= 0) {
    return '0 B';
  }

  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unitIndex = 0;

  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }

  const displayValue = value >= 10 || unitIndex === 0 ? value.toFixed(0) : value.toFixed(1);
  return `${displayValue.replace(/\.0$/, '')} ${units[unitIndex]}`;
}

export function buildQrImageUrl(shareUrl: string) {
  return `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(shareUrl)}`;
}

export function canSendTransferFiles(isAuthenticated: boolean) {
  return isAuthenticated;
}

export function getTransferModeSummary(mode: TransferMode) {
  if (mode === 'OFFLINE') {
    return {
      title: '发离线',
      description: '文件先上传到站点存储，保留 7 天，到期自动销毁，可被多次接收。',
    };
  }

  return {
    title: '发在线',
    description: '文件通过浏览器 P2P 直连发送，只能被接收一次，适合双方都在线时快速传输。',
  };
}

export function resolveInitialTransferTab(
  isAuthenticated: boolean,
  sessionId: string | null,
): TransferTab {
  if (!canSendTransferFiles(isAuthenticated) || sessionId) {
    return 'receive';
  }

  return 'send';
}

export function canArchiveTransferSelection(files: Pick<TransferFileDescriptor, 'relativePath'>[]) {
  return files.length > 1 || files.some((file) => file.relativePath.includes('/'));
}
