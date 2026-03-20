export function splitNetdiskPath(path: string | null | undefined) {
  const rawPath = path?.trim();
  if (!rawPath || rawPath === '/') {
    return [] as string[];
  }

  return rawPath
    .replaceAll('\\', '/')
    .split('/')
    .map((segment) => segment.trim())
    .filter((segment) => segment && segment !== '.' && segment !== '..');
}

export function joinNetdiskPath(segments: string[]) {
  return segments.length === 0 ? '/' : `/${segments.join('/')}`;
}

export function getParentNetdiskPath(path: string | null | undefined) {
  const segments = splitNetdiskPath(path);
  return joinNetdiskPath(segments.slice(0, -1));
}

export function resolveTransferSaveDirectory(relativePath: string | null | undefined, rootPath = '/下载') {
  const rootSegments = splitNetdiskPath(rootPath);
  const relativeSegments = splitNetdiskPath(relativePath);
  if (relativeSegments.length <= 1) {
    return joinNetdiskPath(rootSegments);
  }

  return joinNetdiskPath([...rootSegments, ...relativeSegments.slice(0, -1)]);
}
