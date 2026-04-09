import { useEffect, useRef, useState } from 'react';
import { apiRequest } from '@/src/lib/api';
import { readCachedValue, removeCachedValue, writeCachedValue } from '@/src/lib/cache';
import { subscribeFileEvents } from '@/src/lib/file-events';
import { getFilesLastPathCacheKey, getFilesListCacheKey } from '@/src/lib/page-cache';
import type { FileMetadata, PageResponse } from '@/src/lib/types';
import {
  createExpandedDirectorySet,
  getMissingDirectoryListingPaths,
  hasLoadedDirectoryListing,
  mergeDirectoryChildren,
  toDirectoryPath,
  type DirectoryChildrenMap,
} from '../files-tree';
import { toUiFile, type UiFile } from './file-types';

export function toBackendPath(pathParts: string[]) {
  return toDirectoryPath(pathParts);
}

export function splitBackendPath(path: string) {
  return path.split('/').filter(Boolean);
}

export function useFilesDirectoryState() {
  const initialPath = readCachedValue<string[]>(getFilesLastPathCacheKey()) ?? [];
  const initialCachedFiles = readCachedValue<FileMetadata[]>(getFilesListCacheKey(toBackendPath(initialPath))) ?? [];

  const [currentPath, setCurrentPath] = useState<string[]>(initialPath);
  const currentPathRef = useRef(currentPath);
  
  const [directoryChildren, setDirectoryChildren] = useState<DirectoryChildrenMap>(() => {
    if (initialCachedFiles.length === 0) return {};
    return mergeDirectoryChildren(
      {},
      toBackendPath(initialPath),
      initialCachedFiles.filter((file) => file.directory).map((file) => file.filename),
    );
  });
  
  const [loadedDirectoryPaths, setLoadedDirectoryPaths] = useState<Set<string>>(
    () => new Set(initialCachedFiles.length === 0 ? [] : [toBackendPath(initialPath)]),
  );
  
  const [expandedDirectories, setExpandedDirectories] = useState(() => createExpandedDirectorySet(initialPath));
  const [currentFiles, setCurrentFiles] = useState<UiFile[]>(initialCachedFiles.map(toUiFile));

  const recordDirectoryChildren = (pathParts: string[], items: FileMetadata[]) => {
    setDirectoryChildren((previous) => {
      let next = mergeDirectoryChildren(
        previous,
        toBackendPath(pathParts),
        items.filter((file) => file.directory).map((file) => file.filename),
      );
      for (let index = 0; index < pathParts.length; index += 1) {
        next = mergeDirectoryChildren(
          next,
          toBackendPath(pathParts.slice(0, index)),
          [pathParts[index]],
        );
      }
      return next;
    });
  };

  const markDirectoryLoaded = (pathParts: string[]) => {
    const path = toBackendPath(pathParts);
    setLoadedDirectoryPaths((previous) => {
      if (previous.has(path)) return previous;
      const next = new Set(previous);
      next.add(path);
      return next;
    });
  };

  const loadCurrentPath = async (pathParts: string[]) => {
    const response = await apiRequest<PageResponse<FileMetadata>>(
      `/files/list?path=${encodeURIComponent(toBackendPath(pathParts))}&page=0&size=100`
    );
    writeCachedValue(getFilesListCacheKey(toBackendPath(pathParts)), response.items);
    writeCachedValue(getFilesLastPathCacheKey(), pathParts);
    recordDirectoryChildren(pathParts, response.items);
    markDirectoryLoaded(pathParts);
    setCurrentFiles(response.items.map(toUiFile));
  };

  useEffect(() => {
    currentPathRef.current = currentPath;
    setExpandedDirectories((previous) => {
      const next = new Set(previous);
      for (const path of createExpandedDirectorySet(currentPath)) {
        next.add(path);
      }
      return next;
    });

    const cachedFiles = readCachedValue<FileMetadata[]>(getFilesListCacheKey(toBackendPath(currentPath)));
    writeCachedValue(getFilesLastPathCacheKey(), currentPath);

    if (cachedFiles) {
      recordDirectoryChildren(currentPath, cachedFiles);
      setCurrentFiles(cachedFiles.map(toUiFile));
    }

    loadCurrentPath(currentPath).catch(() => {
      if (!cachedFiles) {
        setCurrentFiles([]);
      }
    });
  }, [currentPath]);

  useEffect(() => {
    const missingAncestors = getMissingDirectoryListingPaths(currentPath, loadedDirectoryPaths);
    if (missingAncestors.length === 0) return;

    let cancelled = false;
    Promise.all(
      missingAncestors.map(async (pathParts) => {
        const path = toBackendPath(pathParts);
        const response = await apiRequest<PageResponse<FileMetadata>>(
          `/files/list?path=${encodeURIComponent(path)}&page=0&size=100`
        );
        writeCachedValue(getFilesListCacheKey(path), response.items);
        return { pathParts, items: response.items };
      }),
    ).then((responses) => {
      if (cancelled) return;
      for (const response of responses) {
        recordDirectoryChildren(response.pathParts, response.items);
        markDirectoryLoaded(response.pathParts);
      }
    }).catch(() => {});

    return () => { cancelled = true; };
  }, [currentPath, loadedDirectoryPaths]);

  useEffect(() => {
    const subscription = subscribeFileEvents({
      path: toBackendPath(currentPath),
      onFileEvent: () => {
        const activePath = currentPathRef.current;
        removeCachedValue(getFilesListCacheKey(toBackendPath(activePath)));
        loadCurrentPath(activePath).catch(() => undefined);
      },
      onError: () => undefined,
    });
    return () => { subscription.close(); };
  }, [currentPath]);

  const handleDirectoryToggle = async (pathParts: string[]) => {
    const path = toBackendPath(pathParts);
    let shouldLoadChildren = false;

    setExpandedDirectories((previous) => {
      const next = new Set(previous);
      if (next.has(path)) {
        next.delete(path);
        return next;
      }
      next.add(path);
      shouldLoadChildren = !hasLoadedDirectoryListing(pathParts, loadedDirectoryPaths);
      return next;
    });

    if (!shouldLoadChildren) return;

    try {
      const response = await apiRequest<PageResponse<FileMetadata>>(
        `/files/list?path=${encodeURIComponent(path)}&page=0&size=100`
      );
      writeCachedValue(getFilesListCacheKey(path), response.items);
      recordDirectoryChildren(pathParts, response.items);
      markDirectoryLoaded(pathParts);
    } catch {}
  };

  return {
    currentPath,
    setCurrentPath,
    directoryChildren,
    expandedDirectories,
    currentFiles,
    setCurrentFiles,
    handleDirectoryToggle,
    loadCurrentPath,
  };
}
