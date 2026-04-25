import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Box, Divider, Menu, MenuItem, Stack, Typography, ListItemIcon, ListItemText, alpha } from '@mui/material';
import {
  Check,
  Close,
  ContentCopy,
  CreateNewFolder,
  DeleteOutline,
  Description,
  Download,
  DriveFileMove,
  Edit,
  InfoOutlined,
  OpenInFull,
  Refresh,
  Share,
  Star,
  StarBorder,
  UploadFile,
  Visibility,
  FolderZip,
  ContentPaste,
  CloudDownload,
  Link as LinkIcon,
  Label,
  ChevronRight,
  MoreVert,
  InsertDriveFile,
  Circle,
} from '@mui/icons-material';
import { ThemeProvider as MuiThemeProvider, createTheme } from '@mui/material/styles';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import DashboardLayout from '../components/DashboardLayout';
import FileDetailsRail from '../components/files/FileDetailsRail';
import { FilesExplorerSurface } from '../components/files/FilesExplorerSurface';
import { FilesPreviewDialog } from '../components/files/FilesPreviewDialog';
import { FileTagsManagerDialog } from '../components/files/FileTagsManagerDialog';
import { FilesTopBar } from '../components/files/FilesTopBar';
import { useFavoriteFiles, useFiles } from '../api/queries';
import type { FileDetail, FileItem, FileTag } from '../api/types';
import {
  addFileTag,
  batchDeleteFiles,
  copyFile,
  createDirectory,
  createLegacyShareLink,
  listTags,
  downloadFileBlob,
  getFileDetail,
  getFileDownloadUrl,
  listFileTags,
  moveFile,
  renameFile,
  removeFileTag,
  setFileFavorite,
  uploadFile,
} from '../lib/files';
import { useTheme as useAppTheme } from '../hooks/useTheme';

type ViewMode = 'grid' | 'list';

export type SortBy = 'name' | 'tags' | 'createdAt' | 'updatedAt';
export type SortOrder = 'asc' | 'desc';

type ContextMenuState = {
  mouseX: number;
  mouseY: number;
  file?: FileItem;
};

type SelectedFileMap = Record<string, FileItem>;

const FILES_PAGE_SIZE = 30;
const VIEW_MODE_STORAGE_KEY = 'cloudreve-files-view-mode';
const SORT_BY_STORAGE_KEY = 'cloudreve-files-sort-by';
const SORT_ORDER_STORAGE_KEY = 'cloudreve-files-sort-order';

function joinDirectoryPath(parentPath: string, filename: string) {
  return parentPath === '/' ? `/${filename}` : `${parentPath}/${filename}`;
}

function normalizeDirectoryPath(path: string) {
  const trimmed = path.trim();
  if (!trimmed || trimmed === '/') {
    return '/';
  }

  const normalized = trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
  return normalized.replace(/\/{2,}/g, '/').replace(/\/+$/, '') || '/';
}

function getLogicalPath(file: Pick<FileItem, 'directory' | 'filename' | 'path'>) {
  if (!file.path) {
    return joinDirectoryPath('/', file.filename);
  }
  if (file.path === file.filename || file.path.endsWith(`/${file.filename}`)) {
    return file.path;
  }
  return joinDirectoryPath(file.path, file.filename);
}

function getSelectionKey(file: Pick<FileItem, 'id'>) {
  return String(file.id);
}

function isExternalUrl(url: string) {
  return /^https?:\/\//i.test(url) || url.startsWith('//');
}

function triggerBlobDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function triggerUrlDownload(url: string, filename: string) {
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.target = '_blank';
  link.rel = 'noopener noreferrer';
  document.body.appendChild(link);
  link.click();
  link.remove();
}

const Files: React.FC = () => {
  const queryClient = useQueryClient();
  const { theme } = useAppTheme();
  const muiTheme = useMemo(
    () => {
      const isDark = theme === 'dark';
      const menuPaperBackground = isDark ? '#1E293B' : '#FFFFFF';
      const menuBorderColor = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(15,23,42,0.08)';
      const menuHoverBackground = isDark ? 'rgba(255,255,255,0.05)' : 'rgba(79,124,255,0.08)';
      const menuShadow = isDark ? '0 18px 40px rgba(2,6,23,0.36)' : '0 18px 40px rgba(15,23,42,0.12)';

      return createTheme({
        palette: {
          mode: theme,
          primary: {
            main: '#4F7CFF',
          },
          background: {
            default: isDark ? '#0F1117' : '#F6F8FC',
            paper: isDark ? '#171923' : '#FFFFFF',
          },
        },
        shape: {
          borderRadius: 8,
        },
        components: {
          MuiMenu: {
            styleOverrides: {
              paper: {
                backgroundColor: menuPaperBackground,
                backgroundImage: 'none',
                minWidth: 252,
                border: `1px solid ${menuBorderColor}`,
                boxShadow: menuShadow,
                color: isDark ? '#F8FAFC' : '#0F172A',
              },
            },
          },
          MuiMenuItem: {
            styleOverrides: {
              root: {
                fontSize: '0.875rem',
                minHeight: 34,
                padding: '4px 16px',
                '&:hover': {
                  backgroundColor: menuHoverBackground,
                },
              },
            },
          },
          MuiDivider: {
            styleOverrides: {
              root: {
                borderColor: menuBorderColor,
              },
            },
          },
          MuiListItemIcon: {
            styleOverrides: {
              root: {
                minWidth: 34,
                color: 'inherit',
              },
            },
          },
        },
        typography: {
          fontFamily: 'Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        },
      });
    },
    [theme],
  );

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const folderInputRef = useRef<HTMLInputElement | null>(null);
  const lastSelectedIndexRef = useRef(0);
  const [search, setSearch] = useState('');
  const [currentPath, setCurrentPath] = useState('/');
  const [page, setPage] = useState(1);
  const [allRows, setAllRows] = useState<FileItem[]>([]);
  const [sortBy, setSortBy] = useState<SortBy>(() => {
    const stored = window.localStorage.getItem(SORT_BY_STORAGE_KEY);
    return (stored as SortBy) || 'createdAt';
  });
  const [sortOrder, setSortOrder] = useState<SortOrder>(() => {
    const stored = window.localStorage.getItem(SORT_ORDER_STORAGE_KEY);
    return (stored as SortOrder) || 'desc';
  });
  const [viewMode, setViewMode] = useState<ViewMode>(() => {
    const stored = window.localStorage.getItem(VIEW_MODE_STORAGE_KEY);
    return stored === 'list' || stored === 'grid' ? stored : 'grid';
  });
  const [uploadStatus, setUploadStatus] = useState<string | null>(null);
  const [selectedById, setSelectedById] = useState<SelectedFileMap>({});
  const [contextMenu, setContextMenu] = useState<ContextMenuState | null>(null);
  const [previewFile, setPreviewFile] = useState<FileItem | null>(null);
  const [detailFileId, setDetailFileId] = useState<number | null>(null);
  const [detail, setDetail] = useState<FileDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [tagManagerOpen, setTagManagerOpen] = useState(false);
  const [tagFile, setTagFile] = useState<FileItem | null>(null);
  const [tagSubmenuAnchor, setTagSubmenuAnchor] = useState<HTMLElement | null>(null);
  const activeMenuFile = contextMenu?.file;

  const { data: activeFileTags = [] } = useQuery({
    queryKey: ['file-tags', activeMenuFile?.id],
    queryFn: () => (activeMenuFile ? listFileTags(activeMenuFile.id) : Promise.resolve([])),
    enabled: !!activeMenuFile && activeMenuFile.directory,
  });

  const { data: allTags = [] } = useQuery({
    queryKey: ['tags'],
    queryFn: listTags,
    enabled: !!activeMenuFile && !!activeMenuFile.directory,
  });

  const { data, isLoading, isError, refetch, isFetching } = useFiles(currentPath, page, FILES_PAGE_SIZE, search);
  const { data: favoriteFiles, refetch: refetchFavorites } = useFavoriteFiles();
  const browsingScopeKey = useMemo(() => `${currentPath}::${search.trim()}::${sortBy}::${sortOrder}`, [currentPath, search, sortBy, sortOrder]);
  const visibleFolders = useMemo(() => allRows.filter((file) => file.directory), [allRows]);

  const folderTagQueries = useQueries({
    queries: visibleFolders.map((folder) => ({
      queryKey: ['file-tags', folder.id],
      queryFn: () => listFileTags(folder.id),
      staleTime: 30_000,
    })),
  });

  const folderTagsMap = useMemo(
    () =>
      visibleFolders.reduce<Record<number, Awaited<ReturnType<typeof listFileTags>>>>((acc, folder, index) => {
        acc[folder.id] = folderTagQueries[index]?.data ?? [];
        return acc;
      }, {}),
    [folderTagQueries, visibleFolders],
  );

  const sortedRows = useMemo(() => {
    const sorted = [...allRows].sort((a, b) => {
      // 1. Directories stay ahead of files
      if (a.directory !== b.directory) {
        return a.directory ? -1 : 1;
      }

      // 2. Sort within each group
      let comparison = 0;
      switch (sortBy) {
        case 'name':
          comparison = a.filename.localeCompare(b.filename);
          break;
        case 'tags': {
          const aTags = (folderTagsMap[a.id] || []).map((t) => t.name).sort().join(', ');
          const bTags = (folderTagsMap[b.id] || []).map((t) => t.name).sort().join(', ');
          if (!aTags && !bTags) comparison = 0;
          else if (!aTags) comparison = 1;
          else if (!bTags) comparison = -1;
          else comparison = aTags.localeCompare(bTags);
          break;
        }
        case 'createdAt':
          comparison = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
          break;
        case 'updatedAt': {
          const timeA = new Date(a.updatedAt || a.createdAt).getTime();
          const timeB = new Date(b.updatedAt || b.createdAt).getTime();
          comparison = timeA - timeB;
          break;
        }
      }

      if (comparison === 0) {
        comparison = a.filename.localeCompare(b.filename) || a.id - b.id;
      }

      return sortOrder === 'asc' ? comparison : -comparison;
    });
    return sorted;
  }, [allRows, sortBy, sortOrder, folderTagsMap]);

  const favoriteIds = useMemo(
    () => new Set((favoriteFiles ?? []).filter((item) => item.favorite).map((item) => item.fileId)),
    [favoriteFiles],
  );
  const selectedFiles = useMemo(() => Object.values(selectedById), [selectedById]);
  const selectedCount = selectedFiles.length;
  const selectedFolderCount = useMemo(() => selectedFiles.filter((file) => file.directory).length, [selectedFiles]);
  const selectedFileCount = selectedCount - selectedFolderCount;
  const allSelected = allRows.length > 0 && allRows.every((file) => selectedById[getSelectionKey(file)]);

  useEffect(() => {
    if (data?.items) {
      if (page === 1) {
        setAllRows(data.items);
      } else {
        setAllRows((prev) => {
          // Avoid duplicates
          const existingIds = new Set(prev.map((item) => item.id));
          const newItems = data.items.filter((item) => !existingIds.has(item.id));
          return [...prev, ...newItems];
        });
      }
    }
  }, [data, page]);

  const createDirectoryMutation = useMutation({
    mutationFn: createDirectory,
    onSuccess: () => {
      setPage(1);
      void refetch();
    },
  });

  const renameMutation = useMutation({
    mutationFn: ({ fileId, filename }: { fileId: number; filename: string }) => renameFile(fileId, filename),
    onSuccess: () => {
      setPage(1);
      void refetch();
    },
  });

  const moveMutation = useMutation({
    mutationFn: ({ fileId, path }: { fileId: number; path: string }) => moveFile(fileId, path),
    onSuccess: () => {
      setPage(1);
      void refetch();
    },
  });

  const copyMutation = useMutation({
    mutationFn: ({ fileId, path }: { fileId: number; path: string }) => copyFile(fileId, path),
    onSuccess: () => {
      setPage(1);
      void refetch();
    },
  });

  const uploadMutation = useMutation({
    mutationFn: async ({ files, folderPath }: { files: File[]; folderPath?: string }) => {
      const uploaded = [];
      for (const file of files) {
        setUploadStatus(`正在上传：${file.name}`);
        const targetPath = folderPath ? joinDirectoryPath(currentPath, folderPath) : currentPath;
        uploaded.push(await uploadFile(targetPath, file));
      }
      return uploaded;
    },
    onSuccess: (result) => {
      setUploadStatus(`已上传 ${result.length} 个文件`);
      setPage(1);
      void refetch();
    },
    onError: (error) => {
      setUploadStatus(error instanceof Error ? error.message : '上传失败');
    },
  });

  const shareMutation = useMutation({
    mutationFn: createLegacyShareLink,
    onSuccess: (result) => {
      window.prompt('已创建分享链接 Token', result.token);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: batchDeleteFiles,
    onSuccess: () => {
      setSelectedById({});
      setContextMenu(null);
      setDetailFileId(null);
      setDetail(null);
      setDetailError(null);
      setPage(1);
      void refetch();
      void refetchFavorites();
    },
  });

  const favoriteMutation = useMutation({
    mutationFn: ({ fileId, favorite }: { fileId: number; favorite: boolean }) => setFileFavorite(fileId, favorite),
    onSuccess: (_, variables) => {
      if (detailFileId === variables.fileId && detail) {
        setDetail({ ...detail, favorite: variables.favorite });
      }
      void refetchFavorites();
    },
  });

  const downloadMutation = useMutation({
    mutationFn: async (file: FileItem) => {
      const result = await getFileDownloadUrl(file.id);
      if (isExternalUrl(result.url)) {
        return { file, url: result.url, blob: null };
      }
      return { file, url: null, blob: await downloadFileBlob(file.id) };
    },
    onSuccess: ({ file, url, blob }) => {
      if (url) {
        triggerUrlDownload(url, file.filename);
        return;
      }
      if (blob) {
        triggerBlobDownload(blob, file.filename);
      }
    },
  });

  const toggleFolderTagMutation = useMutation({
    mutationFn: async ({ fileId, tagId, assigned }: { fileId: number; tagId: number; assigned: boolean }) => {
      if (assigned) {
        await removeFileTag(fileId, tagId);
      } else {
        await addFileTag(fileId, tagId);
      }
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['file-tags', variables.fileId] });
      queryClient.invalidateQueries({ queryKey: ['file-detail', variables.fileId] });
    },
  });

  useEffect(() => {
    window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, viewMode);
  }, [viewMode]);

  useEffect(() => {
    window.localStorage.setItem(SORT_BY_STORAGE_KEY, sortBy);
  }, [sortBy]);

  useEffect(() => {
    window.localStorage.setItem(SORT_ORDER_STORAGE_KEY, sortOrder);
  }, [sortOrder]);

  useEffect(() => {
    // Sync selectedById with allRows if rows change (e.g. deletion or accumulation)
    setSelectedById((current) => {
      const next: SelectedFileMap = {};
      allRows.forEach((file) => {
        const key = getSelectionKey(file);
        if (current[key]) {
          next[key] = file;
        }
      });
      return next;
    });
  }, [allRows]);

  useEffect(() => {
    // Ensure data is fresh on mount
    void refetch();
  }, []);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setSelectedById({});
        setContextMenu(null);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  useEffect(() => {
    if (detailFileId == null) {
      return;
    }

    let disposed = false;
    setDetailLoading(true);
    setDetailError(null);

    void getFileDetail(detailFileId)
      .then((result) => {
        if (!disposed) {
          setDetail({
            ...result,
            path: getLogicalPath(result),
          });
        }
      })
      .catch((error: unknown) => {
        if (!disposed) {
          setDetailError(error instanceof Error ? error.message : '详情加载失败');
          setDetail(null);
        }
      })
      .finally(() => {
        if (!disposed) {
          setDetailLoading(false);
        }
      });

    return () => {
      disposed = true;
    };
  }, [detailFileId]);

  function handlePathChange(path: string) {
    const normalizedPath = normalizeDirectoryPath(path);
    setSelectedById({});
    setContextMenu(null);
    setPreviewFile(null);
    setDetailFileId(null);
    setDetail(null);
    setDetailError(null);
    lastSelectedIndexRef.current = 0;
    setAllRows([]);
    setPage(1);
    setCurrentPath(normalizedPath);
  }

  function handleSearchChange(nextSearch: string) {
    setSelectedById({});
    setContextMenu(null);
    setPreviewFile(null);
    setDetailFileId(null);
    setDetail(null);
    setDetailError(null);
    lastSelectedIndexRef.current = 0;
    setAllRows([]);
    setPage(1);
    setSearch(nextSearch);
  }

  function openDirectory(file: FileItem) {
    if (!file.directory) {
      return;
    }
    handlePathChange(getLogicalPath(file));
  }

  function closeContextMenus() {
    setContextMenu(null);
    setTagSubmenuAnchor(null);
  }

  function openFile(file: FileItem) {
    closeContextMenus();
    if (file.directory) {
      openDirectory(file);
      return;
    }
    setPreviewFile(file);
  }

  function selectFile(file: FileItem, index: number, event?: React.MouseEvent<HTMLElement>) {
    lastSelectedIndexRef.current = index;
    setSelectedById({ [getSelectionKey(file)]: file });
  }

  function toggleFileSelection(file: FileItem, index: number) {
    lastSelectedIndexRef.current = index;
    setSelectedById((current) => {
      const next = { ...current };
      const key = getSelectionKey(file);
      if (next[key]) {
        delete next[key];
      } else {
        next[key] = file;
      }
      return next;
    });
  }

  function openFileContextMenu(file: FileItem, index: number, event: React.MouseEvent<HTMLElement>) {
    event.preventDefault();
    event.stopPropagation();
    if (!selectedById[getSelectionKey(file)]) {
      lastSelectedIndexRef.current = index;
      setSelectedById({ [getSelectionKey(file)]: file });
    }
    setTagSubmenuAnchor(null);
    setContextMenu({ mouseX: event.clientX + 2, mouseY: event.clientY - 6, file });
  }

  function openEmptyContextMenu(event: React.MouseEvent<HTMLElement>) {
    event.preventDefault();
    setTagSubmenuAnchor(null);
    setContextMenu({ mouseX: event.clientX + 2, mouseY: event.clientY - 6 });
  }

  function openDetail(file: FileItem) {
    setDetailFileId(file.id);
    closeContextMenus();
  }

  function toggleFavorite(file: FileItem) {
    favoriteMutation.mutate({
      fileId: file.id,
      favorite: !favoriteIds.has(file.id),
    });
    closeContextMenus();
  }

  function shareFile(file: FileItem) {
    shareMutation.mutate(file.id);
    closeContextMenus();
  }

  function deleteFiles(files: FileItem[]) {
    if (files.length === 0) {
      return;
    }
    closeContextMenus();
    if (window.confirm(`确认删除 ${files.length} 个项目？`)) {
      deleteMutation.mutate(files.map((file) => file.id));
    }
  }

  function downloadFile(file: FileItem) {
    if (file.directory) {
      alert('暂未接入文件夹下载');
      closeContextMenus();
      return;
    }
    closeContextMenus();
    downloadMutation.mutate(file);
  }

  function createFolder() {
    const nextName = window.prompt('请输入新文件夹名称', '新建文件夹');
    if (nextName && nextName.trim()) {
      createDirectoryMutation.mutate(joinDirectoryPath(currentPath, nextName.trim()));
    }
    closeContextMenus();
  }

  function renameFileAction(file: FileItem) {
    const nextName = window.prompt('请输入新名称', file.filename);
    if (nextName && nextName.trim() && nextName !== file.filename) {
      renameMutation.mutate({ fileId: file.id, filename: nextName.trim() });
    }
    closeContextMenus();
  }

  function moveFileAction(file: FileItem) {
    const nextPath = window.prompt('请输入目标路径', currentPath);
    if (nextPath && nextPath.trim()) {
      moveMutation.mutate({ fileId: file.id, path: nextPath.trim() });
    }
    closeContextMenus();
  }

  function copyFileAction(file: FileItem) {
    const nextPath = window.prompt('请输入目标路径', currentPath);
    if (nextPath && nextPath.trim()) {
      copyMutation.mutate({ fileId: file.id, path: nextPath.trim() });
    }
    closeContextMenus();
  }

  async function getDirectLink(file: FileItem) {
    try {
      const result = await getFileDownloadUrl(file.id);
      await navigator.clipboard.writeText(result.url);
      alert('直链已复制到剪贴板');
    } catch (e) {
      alert('获取直链失败');
    }
    closeContextMenus();
  }

  async function uploadFromClipboard() {
    try {
      const items = await navigator.clipboard.read();
      const files: File[] = [];
      for (const item of items) {
        for (const type of item.types) {
          if (type.startsWith('image/') || type.startsWith('text/')) {
            const blob = await item.getType(type);
            const ext = type.split('/')[1] === 'plain' ? 'txt' : type.split('/')[1];
            files.push(new File([blob], `clipboard_${Date.now()}.${ext}`, { type }));
          }
        }
      }
      if (files.length > 0) {
        uploadMutation.mutate({ files });
      } else {
        alert('剪贴板中没有可上传的文件');
      }
    } catch (e) {
      alert('无法访问剪贴板或剪贴板为空');
    }
    closeContextMenus();
  }

  function createNewDocument(type: 'md' | 'drawio' | 'txt' | 'excalidraw') {
    let defaultName = 'Untitled.txt';
    let content = '';
    let mimeType = 'text/plain';

    switch (type) {
      case 'md':
        defaultName = 'Untitled.md';
        content = '# ' + defaultName + '\n\n开始编写内容...';
        mimeType = 'text/markdown';
        break;
      case 'drawio':
        defaultName = 'Untitled.drawio';
        content = '<?xml version="1.0" encoding="UTF-8"?><mxfile host="Electron" agent="5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) draw.io/21.3.7 Chrome/112.0.5615.204 Electron/24.5.0 Safari/537.36"><diagram id="R2lEEUBd9i9sh9G3x2me" name="Page-1"><mxGraphModel dx="1026" dy="662" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="850" pageHeight="1100" math="0" shadow="0"><root><mxCell id="0"/><mxCell id="1" parent="0"/></root></mxGraphModel></diagram></mxfile>';
        mimeType = 'application/x-drawio';
        break;
      case 'excalidraw':
        defaultName = 'Untitled.excalidraw';
        content = JSON.stringify({ type: "excalidraw", version: 2, source: "https://excalidraw.com", elements: [], appState: { viewBackgroundColor: "#ffffff" }, files: {} });
        mimeType = 'application/json';
        break;
      case 'txt':
      default:
        defaultName = 'Untitled.txt';
        content = '';
        mimeType = 'text/plain';
        break;
    }

    const filename = window.prompt('请输入文件名称', defaultName);
    if (!filename || !filename.trim()) {
      closeContextMenus();
      return;
    }

    const file = new File([content], filename.trim(), { type: mimeType });
    uploadMutation.mutate({ files: [file] });
    closeContextMenus();
  }

  function handleSelectAll() {
    if (allSelected) {
      setSelectedById({});
      return;
    }
    const next: SelectedFileMap = {};
    allRows.forEach((file) => {
      next[getSelectionKey(file)] = file;
    });
    setSelectedById(next);
  }

  const menuMode = useMemo(() => {
    if (!contextMenu) return 'none';
    if (!contextMenu.file) return 'empty';
    if (selectedCount > 1 && selectedById[getSelectionKey(contextMenu.file)]) {
      return 'batch';
    }
    return contextMenu.file.directory ? 'folder' : 'file';
  }, [contextMenu, selectedCount, selectedById]);

  function isFolderTagAssigned(tagId: number) {
    return activeFileTags.some((tag) => tag.id === tagId);
  }

  return (
    <DashboardLayout title="文件 Files" hideHeader={true}>
      <MuiThemeProvider theme={muiTheme}>
        <input
          ref={fileInputRef}
          type="file"
          multiple
          className="hidden"
          onChange={(event) => {
            const files = Array.from(event.target.files ?? []);
            if (files.length > 0) {
              uploadMutation.mutate({ files });
            }
            event.target.value = '';
          }}
        />
        <input
          ref={folderInputRef}
          type="file"
          // @ts-ignore
          webkitdirectory=""
          directory=""
          multiple
          className="hidden"
          onChange={(event) => {
            const files = Array.from(event.target.files ?? []);
            if (files.length > 0) {
              uploadMutation.mutate({ files });
            }
            event.target.value = '';
          }}
        />

        <Stack
          spacing={2}
          sx={{
            height: '100%',
            minHeight: 0,
            overflow: { md: 'hidden', xs: 'visible' },
          }}
        >
          <FilesTopBar
            currentPath={currentPath}
            onPathChange={handlePathChange}
            search={search}
            onSearchChange={handleSearchChange}
            onRefresh={() => {
              setPage(1);
              void refetch();
            }}
            onUploadClick={() => fileInputRef.current?.click()}
            onUploadFolderClick={() => folderInputRef.current?.click()}
            onCreateFolderClick={createFolder}
            onCreateFileClick={() => createNewDocument('txt')}
            viewMode={viewMode}
            onViewModeChange={setViewMode}
            sortBy={sortBy}
            sortOrder={sortOrder}
            onSortChange={(nextBy, nextOrder) => {
              setSortBy(nextBy);
              setSortOrder(nextOrder);
            }}
            page={page}
            totalPages={data?.pagination.total_pages ?? 1}
            totalItems={data?.pagination.total_items ?? 0}
            // Selection props
            selectedCount={selectedCount}
            selectedFiles={selectedFiles}
            onClearSelection={() => setSelectedById({})}
            onOpen={openFile}
            onDetail={openDetail}
            onDownload={downloadFile}
            onShare={shareFile}
            onDelete={deleteFiles}
            onRename={renameFileAction}
            onMove={moveFileAction}
            onCopy={copyFileAction}
          />

          <Box
            sx={{
              display: 'flex',
              alignItems: 'stretch',
              gap: detailFileId != null ? 2 : 0,
              flex: 1,
              minHeight: 0,
              transition: viewMode === 'list' ? 'gap 300ms cubic-bezier(0.4, 0, 0.2, 1)' : 'none',
              animation: 'filesWorkspaceEnter 240ms ease-out',
              '@keyframes filesWorkspaceEnter': {
                from: { opacity: 0, transform: 'translateY(6px)' },
                to: { opacity: 1, transform: 'translateY(0)' },
              },
            }}
          >
            <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
              <FilesExplorerSurface
                key={browsingScopeKey}
                isLoading={isLoading}
                isError={isError}
                rows={sortedRows}
                viewMode={viewMode}
                selectedById={selectedById}
                favoriteIds={favoriteIds}
                allSelected={allSelected}
                selectedCount={selectedCount}
                showSelectionControls={selectedCount > 0}
                onSelectFile={selectFile}
                onToggleSelection={toggleFileSelection}
                onSelectAll={handleSelectAll}
                onOpenFile={openFile}
                onContextMenu={openFileContextMenu}
                onEmptyContextMenu={openEmptyContextMenu}
                onEmptyClick={() => {
                  setSelectedById({});
                  setContextMenu(null);
                }}
                getLogicalPath={getLogicalPath}
                getSelectionKey={getSelectionKey}
                folderTagsMap={folderTagsMap}
                sortBy={sortBy}
                // Infinite Scroll
                hasNextPage={data ? page < data.pagination.total_pages : false}
                isFetchingNextPage={isFetching && page > 1}
                onLoadMore={() => {
                  if (data && page < data.pagination.total_pages && !isFetching) {
                    setPage((p) => p + 1);
                  }
                }}
              />
            </Box>

            <Box
              sx={{
                width: {
                  xs: detailFileId != null ? '100%' : 0,
                  md: detailFileId != null ? '320px' : 0,
                  xl: detailFileId != null ? '340px' : 0,
                },
                flexShrink: 0,
                transition: viewMode === 'list'
                  ? 'width 300ms cubic-bezier(0.4, 0, 0.2, 1), opacity 300ms ease, transform 300ms ease'
                  : 'opacity 220ms ease-out, transform 220ms ease-out',
                overflow: 'hidden',
                position: { xs: 'fixed', md: 'relative' },
                top: { xs: 'auto', md: 0 },
                bottom: { xs: 0, md: 'auto' },
                right: { xs: 0, md: 'auto' },
                zIndex: { xs: 1200, md: 'auto' },
                bgcolor: 'background.default',
                visibility: detailFileId != null ? 'visible' : 'hidden',
                opacity: detailFileId != null ? 1 : 0,
                transform: detailFileId != null ? 'translateX(0)' : 'translateX(16px)',
                height: '100%',
              }}
            >
              <FileDetailsRail
                detail={detail}
                loading={detailLoading}
                error={detailError}
                onClose={() => {
                  setDetailFileId(null);
                  setDetail(null);
                  setDetailError(null);
                }}
              />
            </Box>
          </Box>
        </Stack>

        <Menu
          open={contextMenu != null}
          onClose={closeContextMenus}
          anchorReference="anchorPosition"
          anchorPosition={contextMenu ? { top: contextMenu.mouseY, left: contextMenu.mouseX } : undefined}
        >
          {menuMode === 'empty' && (
            <Box>
              <MenuItem onClick={() => { setContextMenu(null); fileInputRef.current?.click(); }}>
                <ListItemIcon><UploadFile fontSize="small" /></ListItemIcon>
                <ListItemText>上传文件</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => { setContextMenu(null); folderInputRef.current?.click(); }}>
                <ListItemIcon><FolderZip fontSize="small" /></ListItemIcon>
                <ListItemText>上传文件夹</ListItemText>
              </MenuItem>
              <MenuItem onClick={uploadFromClipboard}>
                <ListItemIcon><ContentPaste fontSize="small" /></ListItemIcon>
                <ListItemText>从剪贴板上传</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => { alert('暂未接入离线下载'); setContextMenu(null); }}>
                <ListItemIcon><CloudDownload fontSize="small" /></ListItemIcon>
                <ListItemText>离线下载</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem onClick={createFolder}>
                <ListItemIcon><CreateNewFolder fontSize="small" /></ListItemIcon>
                <ListItemText>创建文件夹</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => createNewDocument('txt')}>
                <ListItemIcon><InsertDriveFile fontSize="small" /></ListItemIcon>
                <ListItemText>创建文件</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem onClick={() => createNewDocument('md')}>
                <ListItemIcon><Description fontSize="small" /></ListItemIcon>
                <ListItemText>Markdown (.md)</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => createNewDocument('drawio')}>
                <ListItemIcon><Description fontSize="small" /></ListItemIcon>
                <ListItemText>draw.io</ListItemText>
                <ChevronRight fontSize="small" sx={{ ml: 'auto', opacity: 0.5 }} />
              </MenuItem>
              <MenuItem onClick={() => createNewDocument('txt')}>
                <ListItemIcon><Description fontSize="small" /></ListItemIcon>
                <ListItemText>Text (.txt)</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => createNewDocument('excalidraw')}>
                <ListItemIcon><Description fontSize="small" /></ListItemIcon>
                <ListItemText>Excalidraw (.excalidraw)</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem onClick={() => { setPage(1); void refetch(); setContextMenu(null); }}>
                <ListItemIcon><Refresh fontSize="small" /></ListItemIcon>
                <ListItemText>刷新</ListItemText>
              </MenuItem>
            </Box>
          )}

          {menuMode === 'batch' && (
            <Box>
              <Box sx={{ px: 2, pt: 1.25, pb: 0.75 }}>
                <Typography variant="body2" fontWeight={700}>
                  已选择 {selectedCount} 个项目
                </Typography>
              </Box>
              <Divider sx={{ my: 0.5 }} />
              <MenuItem
                onClick={() => {
                  setSelectedById({});
                  setContextMenu(null);
                }}
              >
                <ListItemIcon><Close fontSize="small" /></ListItemIcon>
                <ListItemText>取消选择</ListItemText>
              </MenuItem>
              <MenuItem sx={{ color: 'error.main' }} onClick={() => deleteFiles(selectedFiles)}>
                <ListItemIcon><DeleteOutline fontSize="small" color="inherit" /></ListItemIcon>
                <ListItemText>批量删除</ListItemText>
              </MenuItem>
            </Box>
          )}

          {menuMode === 'folder' && activeMenuFile && (
            <Box>
              <MenuItem onClick={() => openFile(activeMenuFile)}>
                <ListItemIcon><OpenInFull fontSize="small" /></ListItemIcon>
                <ListItemText>进入</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => downloadFile(activeMenuFile)}>
                <ListItemIcon><Download fontSize="small" /></ListItemIcon>
                <ListItemText>下载</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem onClick={() => shareFile(activeMenuFile)}>
                <ListItemIcon><Share fontSize="small" /></ListItemIcon>
                <ListItemText>分享</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => renameFileAction(activeMenuFile)}>
                <ListItemIcon><Edit fontSize="small" /></ListItemIcon>
                <ListItemText>重命名</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => copyFileAction(activeMenuFile)}>
                <ListItemIcon><ContentCopy fontSize="small" /></ListItemIcon>
                <ListItemText>复制</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => getDirectLink(activeMenuFile)}>
                <ListItemIcon><LinkIcon fontSize="small" /></ListItemIcon>
                <ListItemText>获取直链</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem
                onClick={(event) => setTagSubmenuAnchor(event.currentTarget)}
                onMouseEnter={(event) => setTagSubmenuAnchor(event.currentTarget)}
                selected={Boolean(tagSubmenuAnchor)}
                sx={{
                  bgcolor: tagSubmenuAnchor ? alpha(muiTheme.palette.primary.main, 0.08) : undefined,
                }}
              >
                <ListItemIcon><Label fontSize="small" /></ListItemIcon>
                <ListItemText>标签</ListItemText>
                <ChevronRight fontSize="small" sx={{ ml: 'auto', opacity: 0.5 }} />
              </MenuItem>
              <MenuItem onClick={() => setContextMenu(null)}>
                <ListItemIcon><DriveFileMove fontSize="small" /></ListItemIcon>
                <ListItemText>整理</ListItemText>
                <ChevronRight fontSize="small" sx={{ ml: 'auto', opacity: 0.5 }} />
              </MenuItem>
              <MenuItem onClick={() => setContextMenu(null)}>
                <ListItemIcon><MoreVert fontSize="small" /></ListItemIcon>
                <ListItemText>更多操作</ListItemText>
                <ChevronRight fontSize="small" sx={{ ml: 'auto', opacity: 0.5 }} />
              </MenuItem>
              <Divider />
              <MenuItem onClick={() => openDetail(activeMenuFile)}>
                <ListItemIcon><InfoOutlined fontSize="small" /></ListItemIcon>
                <ListItemText>详情</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem sx={{ color: 'error.main' }} onClick={() => deleteFiles([activeMenuFile])}>
                <ListItemIcon><DeleteOutline fontSize="small" color="inherit" /></ListItemIcon>
                <ListItemText>删除</ListItemText>
              </MenuItem>
            </Box>
          )}

          {menuMode === 'file' && activeMenuFile && (
            <Box>
              <MenuItem onClick={() => openFile(activeMenuFile)}>
                <ListItemIcon><OpenInFull fontSize="small" /></ListItemIcon>
                <ListItemText>打开</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => { setContextMenu(null); setPreviewFile(activeMenuFile); }}>
                <ListItemIcon><Visibility fontSize="small" /></ListItemIcon>
                <ListItemText>打开方式</ListItemText>
                <ChevronRight fontSize="small" sx={{ ml: 'auto', opacity: 0.5 }} />
              </MenuItem>
              <MenuItem onClick={() => downloadFile(activeMenuFile)}>
                <ListItemIcon><Download fontSize="small" /></ListItemIcon>
                <ListItemText>下载</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem onClick={() => shareFile(activeMenuFile)}>
                <ListItemIcon><Share fontSize="small" /></ListItemIcon>
                <ListItemText>分享</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => renameFileAction(activeMenuFile)}>
                <ListItemIcon><Edit fontSize="small" /></ListItemIcon>
                <ListItemText>重命名</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => copyFileAction(activeMenuFile)}>
                <ListItemIcon><ContentCopy fontSize="small" /></ListItemIcon>
                <ListItemText>复制</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => getDirectLink(activeMenuFile)}>
                <ListItemIcon><LinkIcon fontSize="small" /></ListItemIcon>
                <ListItemText>获取直链</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem onClick={() => setContextMenu(null)}>
                <ListItemIcon><DriveFileMove fontSize="small" /></ListItemIcon>
                <ListItemText>整理</ListItemText>
                <ChevronRight fontSize="small" sx={{ ml: 'auto', opacity: 0.5 }} />
              </MenuItem>
              <MenuItem onClick={() => setContextMenu(null)}>
                <ListItemIcon><MoreVert fontSize="small" /></ListItemIcon>
                <ListItemText>更多操作</ListItemText>
                <ChevronRight fontSize="small" sx={{ ml: 'auto', opacity: 0.5 }} />
              </MenuItem>
              <Divider />
              <MenuItem onClick={() => openDetail(activeMenuFile)}>
                <ListItemIcon><InfoOutlined fontSize="small" /></ListItemIcon>
                <ListItemText>详情</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem sx={{ color: 'error.main' }} onClick={() => deleteFiles([activeMenuFile])}>
                <ListItemIcon><DeleteOutline fontSize="small" color="inherit" /></ListItemIcon>
                <ListItemText>删除</ListItemText>
              </MenuItem>
            </Box>
          )}
        </Menu>

        <Menu
          open={menuMode === 'folder' && Boolean(tagSubmenuAnchor)}
          anchorEl={tagSubmenuAnchor}
          onClose={() => setTagSubmenuAnchor(null)}
          anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
          transformOrigin={{ vertical: 'top', horizontal: 'left' }}
          MenuListProps={{
            onMouseLeave: () => setTagSubmenuAnchor(null),
            sx: { py: 0.5, minWidth: 220 },
          }}
        >
          <MenuItem
            onClick={() => {
              setTagFile(activeMenuFile || null);
              closeContextMenus();
              setTagManagerOpen(true);
            }}
          >
            <ListItemIcon><Label fontSize="small" /></ListItemIcon>
            <ListItemText>管理标签</ListItemText>
          </MenuItem>
          <Divider />
          {allTags.map((tag) => {
            const assigned = isFolderTagAssigned(tag.id);
            return (
              <MenuItem
                key={tag.id}
                onClick={() => {
                  if (!activeMenuFile) return;
                  toggleFolderTagMutation.mutate({
                    fileId: activeMenuFile.id,
                    tagId: tag.id,
                    assigned,
                  });
                }}
              >
                <ListItemIcon sx={{ minWidth: 26 }}>
                  {assigned ? <Check fontSize="small" /> : null}
                </ListItemIcon>
                <Box
                  component="span"
                  sx={{
                    px: 1.25,
                    py: 0.5,
                    borderRadius: 999,
                    bgcolor: tag.color,
                    color: '#fff',
                    fontSize: 13,
                    fontWeight: 700,
                    lineHeight: 1,
                    mr: 1.25,
                    minWidth: 24,
                    textAlign: 'center',
                    boxShadow: `inset 0 0 0 1px ${alpha('#000000', 0.08)}`,
                  }}
                >
                  {tag.name}
                </Box>
              </MenuItem>
            );
          })}
        </Menu>

        <FilesPreviewDialog file={previewFile} onClose={() => setPreviewFile(null)} />
        <FileTagsManagerDialog
          open={tagManagerOpen}
          onClose={() => {
            setTagManagerOpen(false);
            setTagFile(null);
          }}
          file={tagFile}
        />
      </MuiThemeProvider>
    </DashboardLayout>
  );
};

export default Files;
