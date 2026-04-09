import { joinNetdiskPath, resolveTransferSaveDirectory, splitNetdiskPath } from './netdisk-paths';
import { uploadFileToNetdiskViaSession } from './upload-session';

export function normalizeNetdiskTargetPath(path: string | null | undefined, fallback = '/下载') {
  const rawPath = path?.trim();
  if (!rawPath) {
    return fallback;
  }

  return joinNetdiskPath(splitNetdiskPath(rawPath === '/' ? '/' : rawPath)) || fallback;
}

export function resolveNetdiskSaveDirectory(relativePath: string | null | undefined, rootPath = '/下载') {
  return normalizeNetdiskTargetPath(resolveTransferSaveDirectory(relativePath, rootPath));
}

export async function saveFileToNetdisk(file: File, path: string) {
  const normalizedPath = normalizeNetdiskTargetPath(path);
  return uploadFileToNetdiskViaSession(file, normalizedPath);
}
