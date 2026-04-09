import { resolveStoredFileType, type FileTypeKind } from '@/src/lib/file-type';
import type { FileMetadata } from '@/src/lib/types';

export interface UiFile {
  id: FileMetadata['id'];
  modified: string;
  name: string;
  size: string;
  type: FileTypeKind;
  typeLabel: string;
}

export function formatFileSize(size: number) {
  if (size <= 0) return '—';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1);
  const value = size / 1024 ** index;
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

export function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

export function toUiFile(file: FileMetadata): UiFile {
  const resolvedType = resolveStoredFileType({
    filename: file.filename,
    contentType: file.contentType,
    directory: file.directory,
  });

  return {
    id: file.id,
    name: file.filename,
    type: resolvedType.kind,
    typeLabel: resolvedType.label,
    size: file.directory ? '—' : formatFileSize(file.size),
    modified: formatDateTime(file.createdAt),
  };
}
