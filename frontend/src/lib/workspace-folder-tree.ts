export const FILES_PATH_SEARCH_PARAM = 'path';
export const WORKSPACE_FOLDER_TREE_STORAGE_KEY = 'workspace-folder-tree-expanded-paths';
export const WORKSPACE_FOLDER_TREE_SECTION_STORAGE_KEY = 'workspace-folder-tree-files-section-collapsed';
export const WORKSPACE_FOLDER_TREE_REFRESH_EVENT = 'workspace-folder-tree:refresh';

type WorkspaceFolderTreeRefreshDetail = {
  paths: string[];
};

export function normalizeWorkspaceFolderPath(path?: string | null) {
  const trimmed = (path ?? '').trim();
  if (!trimmed || trimmed === '/') {
    return '/';
  }

  const normalized = trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
  return normalized.replace(/\/{2,}/g, '/').replace(/\/+$/, '') || '/';
}

export function getWorkspaceFolderName(path: string) {
  const normalized = normalizeWorkspaceFolderPath(path);
  if (normalized === '/') {
    return '/';
  }

  const segments = normalized.split('/').filter(Boolean);
  return segments[segments.length - 1] ?? '/';
}

export function getWorkspaceFolderParentPath(path: string) {
  const normalized = normalizeWorkspaceFolderPath(path);
  if (normalized === '/') {
    return '/';
  }

  const index = normalized.lastIndexOf('/');
  return index <= 0 ? '/' : normalized.slice(0, index);
}

export function getWorkspaceFolderAncestorPaths(path: string) {
  const normalized = normalizeWorkspaceFolderPath(path);
  if (normalized === '/') {
    return ['/'];
  }

  const segments = normalized.split('/').filter(Boolean);
  const ancestors = ['/'];
  let current = '';

  for (let index = 0; index < segments.length - 1; index += 1) {
    current += `/${segments[index]}`;
    ancestors.push(current);
  }

  return ancestors;
}

export function getWorkspaceFolderPathFromSearchParams(searchParams: URLSearchParams) {
  return normalizeWorkspaceFolderPath(searchParams.get(FILES_PATH_SEARCH_PARAM));
}

export function buildWorkspaceFilesHref(path: string) {
  const normalized = normalizeWorkspaceFolderPath(path);
  if (normalized === '/') {
    return '/dashboard/files';
  }

  const params = new URLSearchParams();
  params.set(FILES_PATH_SEARCH_PARAM, normalized);
  return `/dashboard/files?${params.toString()}`;
}

export function emitWorkspaceFolderTreeRefresh(paths: string[]) {
  if (typeof window === 'undefined') {
    return;
  }

  const normalizedPaths = Array.from(
    new Set(
      paths
        .map((path) => normalizeWorkspaceFolderPath(path))
        .filter((path) => Boolean(path)),
    ),
  );

  window.dispatchEvent(
    new CustomEvent<WorkspaceFolderTreeRefreshDetail>(WORKSPACE_FOLDER_TREE_REFRESH_EVENT, {
      detail: {
        paths: normalizedPaths,
      },
    }),
  );
}
