import React, { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { Box, Divider, Menu, MenuItem, Stack, Typography, ListItemIcon, ListItemText, alpha } from '@mui/material';
import type { MenuProps } from '@mui/material';
import {
  Check,
  ChevronRight,
  Circle,
  ClipboardPaste,
  CloudDownload,
  Copy,
  Eye,
  FileText,
  FolderInput,
  FolderOpen,
  FolderPlus,
  Info,
  Link2,
  PanelsTopLeft,
  Pencil,
  RefreshCw,
  Share2,
  Tags,
  Trash2,
  Download,
  Upload,
  Archive,
  X,
} from 'lucide-react';
import { ThemeProvider as MuiThemeProvider, createTheme } from '@mui/material/styles';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { bindFocus, bindHover } from 'material-ui-popup-state';
import { bindMenu, usePopupState } from 'material-ui-popup-state/hooks';
import type { PopupState } from 'material-ui-popup-state/hooks';
import { useSearchParams } from 'react-router-dom';
import DashboardLayout from '../components/DashboardLayout';
import FileDetailsRail from '../components/files/FileDetailsRail';
import { FilesExplorerSurface } from '../components/files/FilesExplorerSurface';
import { FileViewerHost } from '../components/files/FileViewerHost';
import { OpenWithDialog } from '../components/files/OpenWithDialog';
import { FileTagsManagerDialog } from '../components/files/FileTagsManagerDialog';
import { FilesTopBar } from '../components/files/FilesTopBar';
import AppearanceDialog from '../components/files/AppearanceDialog';
import { FileDeleteDialog } from '../components/files/FileDeleteDialog';
import MoveItemsDialog from '../components/files/MoveItemsDialog';
import WorkspaceDragOverlay from '../components/files/WorkspaceDragOverlay';
import CreateShareDialog from '../components/files/CreateShareDialog';
import CreateRemoteDownloadDialog from '../components/files/CreateRemoteDownloadDialog';
import { useFavoriteFiles, useFiles } from '../api/queries';
import type { FileDeleteMode, FileDetail, FileItem, FileTag, FileViewerDefinition, MediaCategory, MoveResponse } from '../api/types';
import {
  addFileTag,
  batchDeleteFiles,
  batchMoveFiles,
  copyFile,
  createDirectory,
  listTags,
  downloadFileBlob,
  getFileDetail,
  getFileDownloadUrl,
  getFileViewerConfig,
  listFileTags,
  moveFile,
  renameFile,
  removeFileTag,
  setFileFavorite,
  uploadFile,
} from '../lib/files';
import { getUserSettings, updateUserSettings } from '../lib/user-settings';
import {
  buildBrowserFolderArchive,
  collectFolderDownloadEntries,
  type FolderDownloadMode,
} from '../lib/folder-downloads';
import { useUploadQueue } from '../hooks/useUploadQueue';
import { useUploadPanelStore } from '../hooks/useUploadPanelStore';
import { showToast, updateToast, removeToast } from '../components/files/WorkspaceActionToastHost';
import {
  getAllFileViewers,
  getAvailableViewersForFile,
  getFileExtension,
  getRecommendedViewersForFile,
} from '../lib/file-viewers';
import { setDefaultViewerPreference } from '../lib/file-open-preferences';
import { useTheme as useAppTheme } from '../hooks/useTheme';
import { useWorkspaceDragMove } from '../hooks/useWorkspaceDragMove';
import {
  emitWorkspaceFolderTreeRefresh,
  FILES_PATH_SEARCH_PARAM,
  getWorkspaceItemLogicalPath,
  getWorkspaceFolderParentPath,
  getWorkspaceFolderPathFromSearchParams,
  normalizeWorkspaceFolderPath,
} from '../lib/workspace-folder-tree';

type ViewMode = 'grid' | 'list';

export type SortBy = 'name' | 'tags' | 'createdAt' | 'updatedAt';
export type SortOrder = 'asc' | 'desc';

type ContextMenuState = {
  file?: FileItem;
};

type SelectedFileMap = Record<string, FileItem>;

const FILES_PAGE_SIZE = 30;
const VIEW_MODE_STORAGE_KEY = 'cloudreve-files-view-mode';
const SORT_BY_STORAGE_KEY = 'cloudreve-files-sort-by';
const SORT_ORDER_STORAGE_KEY = 'cloudreve-files-sort-order';
const FOLDER_DOWNLOAD_MODE_LABELS: Record<FolderDownloadMode, string> = {
  'server-archive': '服务器端打包',
  'browser-archive': '浏览器打包',
  'individual-files': '逐一文件下载',
};

const HoverMenu = React.forwardRef<HTMLDivElement, MenuProps>(function HoverMenu(props, ref) {
  const { PaperProps, style, ...rest } = props;
  return (
    <Menu
      {...rest}
      ref={ref}
      style={{ pointerEvents: 'none', ...style }}
      PaperProps={{
        ...PaperProps,
        style: {
          pointerEvents: 'auto',
          ...(PaperProps?.style ?? {}),
        },
      }}
    />
  );
});

const CascadingContext = createContext<{
  parentPopupState?: PopupState;
  rootPopupState?: PopupState;
}>({});

function CascadingMenu({ popupState, ...props }: Omit<MenuProps, 'open'> & { popupState: PopupState }) {
  const { rootPopupState } = useContext(CascadingContext);
  const contextValue = useMemo(
    () => ({
      rootPopupState: rootPopupState || popupState,
      parentPopupState: popupState,
    }),
    [rootPopupState, popupState],
  );

  return (
    <CascadingContext.Provider value={contextValue}>
      <HoverMenu {...props} {...bindMenu(popupState)} />
    </CascadingContext.Provider>
  );
}

function CascadingMenuItem({
  onClick,
  closeRootOnClick = true,
  ...props
}: React.ComponentProps<typeof MenuItem> & { closeRootOnClick?: boolean }) {
  const { rootPopupState } = useContext(CascadingContext);

  return (
    <MenuItem
      {...props}
      onClick={(event) => {
        if (closeRootOnClick) {
          rootPopupState?.close();
        }
        onClick?.(event);
      }}
    />
  );
}

function CascadingSubmenu({
  title,
  icon,
  popupId,
  children,
}: {
  title: string;
  icon: React.ReactNode;
  popupId: string;
  children: React.ReactNode;
}) {
  const { parentPopupState } = useContext(CascadingContext);
  const popupState = usePopupState({
    popupId,
    variant: 'popover',
    parentPopupState,
  });

  return (
    <>
      <MenuItem
        {...bindHover(popupState)}
        {...bindFocus(popupState)}
        selected={popupState.isOpen}
        sx={{
          bgcolor: popupState.isOpen ? 'action.hover' : undefined,
        }}
      >
        <ListItemIcon>{icon}</ListItemIcon>
        <ListItemText>{title}</ListItemText>
        <ChevronRight size={16} style={{ marginLeft: 'auto', opacity: 0.5 }} />
      </MenuItem>
      <CascadingMenu
        popupState={popupState}
        keepMounted
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'left' }}
        MenuListProps={{
          sx: { py: 0.5, minWidth: 220 },
        }}
      >
        {children}
      </CascadingMenu>
    </>
  );
}

const MEDIA_CATEGORY_META: Record<MediaCategory, { title: string; rootLabel: string }> = {
  image: { title: '图片', rootLabel: '图片' },
  video: { title: '视频', rootLabel: '视频' },
  audio: { title: '音乐', rootLabel: '音乐' },
  document: { title: '文档', rootLabel: '文档' },
};

const CLIPBOARD_EXTENSION_BY_MIME_TYPE: Record<string, string> = {
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/jpg': 'jpg',
  'image/webp': 'webp',
  'image/gif': 'gif',
  'image/svg+xml': 'svg',
  'image/bmp': 'bmp',
  'image/heic': 'heic',
  'image/heif': 'heif',
  'text/plain': 'txt',
  'text/html': 'html',
  'text/markdown': 'md',
  'application/pdf': 'pdf',
  'application/zip': 'zip',
  'application/msword': 'doc',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'docx',
  'application/vnd.ms-excel': 'xls',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'xlsx',
  'application/vnd.ms-powerpoint': 'ppt',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'pptx',
};

function joinDirectoryPath(parentPath: string, filename: string) {
  return parentPath === '/' ? `/${filename}` : `${parentPath}/${filename}`;
}

function getParentDirectoryPath(path: string) {
  const normalizedPath = normalizeWorkspaceFolderPath(path);
  if (normalizedPath === '/') {
    return '/';
  }
  const segments = normalizedPath.split('/').filter(Boolean);
  segments.pop();
  return segments.length === 0 ? '/' : `/${segments.join('/')}`;
}

function isTimeoutError(error: unknown) {
  return error instanceof Error && /timeout/i.test(error.message);
}

function getLogicalPath(file: Pick<FileItem, 'directory' | 'filename' | 'path'>) {
  return getWorkspaceItemLogicalPath(file);
}

function getSelectionKey(file: Pick<FileItem, 'id'>) {
  return String(file.id);
}

function replaceLogicalPathPrefix(currentPath: string, sourcePath: string, targetPath: string) {
  const normalizedCurrentPath = normalizeWorkspaceFolderPath(currentPath);
  const normalizedSourcePath = normalizeWorkspaceFolderPath(sourcePath);
  const normalizedTargetPath = normalizeWorkspaceFolderPath(targetPath);

  if (normalizedCurrentPath === normalizedSourcePath) {
    return normalizedTargetPath;
  }

  if (!normalizedCurrentPath.startsWith(`${normalizedSourcePath}/`)) {
    return normalizedCurrentPath;
  }

  return normalizeWorkspaceFolderPath(
    `${normalizedTargetPath}${normalizedCurrentPath.slice(normalizedSourcePath.length)}`,
  );
}

function isExternalUrl(url: string) {
  return /^https?:\/\//i.test(url) || url.startsWith('//');
}

function getClipboardTimestamp() {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  return [
    now.getFullYear(),
    pad(now.getMonth() + 1),
    pad(now.getDate()),
    '-',
    pad(now.getHours()),
    pad(now.getMinutes()),
    pad(now.getSeconds()),
  ].join('');
}

function inferClipboardExtension(type: string) {
  const normalizedType = type.trim().toLowerCase();
  if (CLIPBOARD_EXTENSION_BY_MIME_TYPE[normalizedType]) {
    return CLIPBOARD_EXTENSION_BY_MIME_TYPE[normalizedType];
  }

  const [, subtype = ''] = normalizedType.split('/');
  if (!subtype) {
    return 'bin';
  }

  const cleanedSubtype = subtype.split(';')[0]?.trim() ?? '';
  if (!cleanedSubtype) {
    return 'bin';
  }

  if (cleanedSubtype === 'plain') {
    return 'txt';
  }

  return cleanedSubtype.split('+')[0] || 'bin';
}

function sanitizeClipboardFilenameCandidate(rawValue: string) {
  const trimmed = rawValue.trim();
  if (!trimmed || trimmed.includes('\n') || trimmed.includes('\r')) {
    return null;
  }

  const withoutQuery = trimmed.split('?')[0]?.split('#')[0] ?? trimmed;
  const normalized = withoutQuery.replace(/\\/g, '/');
  const leafName = normalized.split('/').filter(Boolean).pop() ?? normalized;
  const cleaned = leafName.trim();

  if (!cleaned || cleaned === '.' || cleaned === '..') {
    return null;
  }

  return cleaned.replace(/[<>:"/\\|?*\u0000-\u001F]/g, '-');
}

function ensureFilenameHasExpectedExtension(filename: string, extension: string) {
  const normalizedExtension = extension.trim().toLowerCase();
  if (!normalizedExtension) {
    return filename;
  }

  const lowerFilename = filename.toLowerCase();
  const expectedSuffix = `.${normalizedExtension}`;
  if (lowerFilename.endsWith(expectedSuffix)) {
    return filename;
  }

  const dotIndex = filename.lastIndexOf('.');
  if (dotIndex > 0) {
    return `${filename.slice(0, dotIndex)}${expectedSuffix}`;
  }

  return `${filename}${expectedSuffix}`;
}

function isInputTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  const tagName = target.tagName;
  return target.isContentEditable || tagName === 'INPUT' || tagName === 'TEXTAREA' || tagName === 'SELECT';
}

async function buildClipboardFilename(
  item: ClipboardItem,
  preferredType: string,
  extension: string,
  timestamp: string,
  index: number,
) {
  const normalizedType = preferredType.trim().toLowerCase();

  if (normalizedType !== 'text/plain' && item.types.some((type) => type.trim().toLowerCase() === 'text/plain')) {
    try {
      const textBlob = await item.getType('text/plain');
      const textValue = await textBlob.text();
      const candidate = sanitizeClipboardFilenameCandidate(textValue);
      if (candidate) {
        return ensureFilenameHasExpectedExtension(candidate, extension);
      }
    } catch {
      // Ignore and fall back to generated naming below.
    }
  }

  const baseName = normalizedType.startsWith('image/')
    ? 'pasted-image'
    : normalizedType.startsWith('text/')
      ? 'pasted-text'
      : 'clipboard-file';
  const suffix = index > 0 ? `-${index + 1}` : '';
  return `${baseName}-${timestamp}${suffix}.${extension}`;
}

function pickPreferredClipboardType(types: readonly string[]) {
  const normalizedTypes = types.map((type) => type.trim()).filter(Boolean);
  return normalizedTypes.find((type) => type.toLowerCase().startsWith('image/'))
    ?? normalizedTypes.find((type) => {
      const normalizedType = type.toLowerCase();
      return !normalizedType.startsWith('text/');
    })
    ?? normalizedTypes.find((type) => type.toLowerCase().startsWith('text/'))
    ?? null;
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

type FilesProps = {
  mediaCategory?: MediaCategory;
};

const Files: React.FC<FilesProps> = ({ mediaCategory }) => {
  const [searchParams, setSearchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const { theme } = useAppTheme();
  const { addTasks: addUploadTasks } = useUploadQueue();
  const { set: setUploadPanelOpen } = useUploadPanelStore();
  const requestedPath = mediaCategory ? '/' : getWorkspaceFolderPathFromSearchParams(searchParams);
  const categoryMeta = mediaCategory ? MEDIA_CATEGORY_META[mediaCategory] : null;
  const isCategoryMode = mediaCategory != null;
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
  const keyboardSelectionAnchorRef = useRef<number | null>(null);
  const [search, setSearch] = useState('');
  const [currentPath, setCurrentPath] = useState(requestedPath);
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
  const [activeViewer, setActiveViewer] = useState<FileViewerDefinition | null>(null);
  const [openWithState, setOpenWithState] = useState<{
    file: FileItem;
    extension: string;
    recommendedViewers: FileViewerDefinition[];
    allViewers: FileViewerDefinition[];
    availableViewerIds: string[];
  } | null>(null);
  const [detailFileId, setDetailFileId] = useState<number | null>(null);
  const [detail, setDetail] = useState<FileDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [tagManagerOpen, setTagManagerOpen] = useState(false);
  const [tagFile, setTagFile] = useState<FileItem | null>(null);
  const [shareDialogOpen, setShareDialogOpen] = useState(false);
  const [shareFileItem, setShareFileItem] = useState<FileItem | null>(null);
  const [remoteDownloadDialogOpen, setRemoteDownloadDialogOpen] = useState(false);
  const [deleteDialogState, setDeleteDialogState] = useState<{
    open: boolean;
    files: FileItem[];
  }>({
    open: false,
    files: [],
  });
  const [moveDialogState, setMoveDialogState] = useState<{
    open: boolean;
    items: FileItem[];
    targetPath: string;
    initialConflictResult: MoveResponse | null;
  }>({
    open: false,
    items: [],
    targetPath: requestedPath,
    initialConflictResult: null,
  });
  const [appearanceFile, setAppearanceFile] = useState<FileItem | null>(null);
  const activeMenuFile = contextMenu?.file;
  const isMacPlatform = useMemo(() => /Mac|iPhone|iPad|iPod/i.test(window.navigator.platform), []);
  const contextMenuPopupState = usePopupState({
    popupId: 'files-context-menu',
    variant: 'popover',
  });

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

  const { data, isLoading, isError, refetch, isFetching } = useFiles(
    currentPath,
    page,
    FILES_PAGE_SIZE,
    search,
    { category: mediaCategory },
  );
  const { data: favoriteFiles, refetch: refetchFavorites } = useFavoriteFiles();
  const { data: viewerConfig } = useQuery({
    queryKey: ['fileViewerConfig'],
    queryFn: getFileViewerConfig,
  });
  const { data: userSettings } = useQuery({
    queryKey: ['userSettings'],
    queryFn: () => getUserSettings(),
  });
  const browsingScopeKey = useMemo(
    () => `${mediaCategory ?? 'directory'}::${currentPath}::${search.trim()}::${sortBy}::${sortOrder}`,
    [currentPath, mediaCategory, search, sortBy, sortOrder],
  );
  const activeMenuFileExtension =
    activeMenuFile && !activeMenuFile.directory ? getFileExtension(activeMenuFile) : '';
  const activeMenuFileAllViewers =
    activeMenuFile && !activeMenuFile.directory && viewerConfig
      ? getAllFileViewers(viewerConfig)
      : [];
  const activeMenuFileAvailableViewers =
    activeMenuFile && !activeMenuFile.directory && viewerConfig
      ? getAvailableViewersForFile(viewerConfig, activeMenuFile, activeMenuFileExtension)
      : [];
  const activeMenuFileRecommendedViewers =
    activeMenuFile && !activeMenuFile.directory && viewerConfig
      ? getRecommendedViewersForFile(viewerConfig, activeMenuFile, activeMenuFileExtension)
      : [];
  const visibleFolders = useMemo(() => allRows.filter((file) => file.directory), [allRows]);

  function closeSubmenus() {
    contextMenuPopupState._childPopupState?.close();
  }

  function refreshCurrentListing() {
    if (page === 1) {
      void refetch().then((result) => {
        if (result.data?.items) {
          setAllRows(result.data.items);
        }
      });
      return;
    }

    setAllRows([]);
    setPage(1);
    void queryClient.invalidateQueries({ queryKey: ['files'] });
  }

  function summarizeMoveResult(result: MoveResponse) {
    const successCount = result.items.filter((item) => !item.skipped).length;
    const renamedCount = result.items.filter((item) => item.renamed).length;
    const skippedCount = result.items.filter((item) => item.skipped).length;
    const summary = [`成功 ${successCount} 个`];
    if (renamedCount > 0) {
      summary.push(`自动重命名 ${renamedCount} 个`);
    }
    if (skippedCount > 0) {
      summary.push(`跳过 ${skippedCount} 个`);
    }
    return `移动完成：${summary.join('，')}`;
  }

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
  const keyboardNavigationBlocked =
    contextMenuPopupState.isOpen ||
    previewFile != null ||
    openWithState != null ||
    tagManagerOpen ||
    shareDialogOpen ||
    remoteDownloadDialogOpen ||
    moveDialogState.open ||
    appearanceFile != null ||
    deleteDialogState.open;

  function clampRowIndex(index: number) {
    return Math.max(0, Math.min(index, sortedRows.length - 1));
  }

  function getSelectedKeyboardIndex() {
    if (sortedRows.length === 0) {
      return -1;
    }

    if (selectedFiles.length === 0) {
      return -1;
    }

    if (selectedFiles.length === 1) {
      const selectedKey = getSelectionKey(selectedFiles[0]);
      const selectedIndex = sortedRows.findIndex((file) => getSelectionKey(file) === selectedKey);
      if (selectedIndex >= 0) {
        return selectedIndex;
      }
    }

    return clampRowIndex(lastSelectedIndexRef.current);
  }

  function selectKeyboardRow(index: number) {
    if (sortedRows.length === 0) {
      return;
    }
    const nextIndex = clampRowIndex(index);
    const nextFile = sortedRows[nextIndex];
    lastSelectedIndexRef.current = nextIndex;
    keyboardSelectionAnchorRef.current = nextIndex;
    setSelectedById({ [getSelectionKey(nextFile)]: nextFile });
  }

  function selectAllRows() {
    if (allRows.length === 0) {
      return;
    }

    const next: SelectedFileMap = {};
    allRows.forEach((file) => {
      next[getSelectionKey(file)] = file;
    });
    lastSelectedIndexRef.current = clampRowIndex(lastSelectedIndexRef.current);
    keyboardSelectionAnchorRef.current = lastSelectedIndexRef.current;
    setSelectedById(next);
  }

  function openMoveDialog(items: FileItem[]) {
    if (items.length === 0) {
      return;
    }

    setMoveDialogState({
      open: true,
      items,
      targetPath: currentPath,
      initialConflictResult: null,
    });
    closeContextMenus();
  }

  function getGridSectionInfo(index: number) {
    const clampedIndex = clampRowIndex(index);
    const file = sortedRows[clampedIndex];
    if (!file) {
      return null;
    }

    const startIndex = sortedRows.findIndex((candidate) => candidate.directory === file.directory);
    let endIndex = startIndex;
    while (endIndex + 1 < sortedRows.length && sortedRows[endIndex + 1].directory === file.directory) {
      endIndex += 1;
    }

    return {
      startIndex,
      endIndex,
      localIndex: clampedIndex - startIndex,
      totalItems: endIndex - startIndex + 1,
    };
  }

  function getSiblingGridSection(section: NonNullable<ReturnType<typeof getGridSectionInfo>>, direction: -1 | 1) {
    const siblingIndex = direction < 0 ? section.startIndex - 1 : section.endIndex + 1;
    if (siblingIndex < 0 || siblingIndex >= sortedRows.length) {
      return null;
    }
    return getGridSectionInfo(siblingIndex);
  }

  function getGridNavigationTargetIndex(currentIndex: number, direction: 'up' | 'down' | 'left' | 'right', gridColumnCount: number) {
    const section = getGridSectionInfo(currentIndex);
    if (!section) {
      return -1;
    }

    const columnCount = Math.max(gridColumnCount, 1);
    const row = Math.floor(section.localIndex / columnCount);
    const column = section.localIndex % columnCount;
    const lastRow = Math.floor((section.totalItems - 1) / columnCount);
    const lastColumnInLastRow = (section.totalItems - 1) % columnCount;

    if (direction === 'left') {
      if (column === 0) {
        return section.startIndex + section.localIndex;
      }
      return section.startIndex + section.localIndex - 1;
    }

    if (direction === 'right') {
      const isLastRealItem = section.localIndex === section.totalItems - 1;
      const isAtVisualRowEnd = column === columnCount - 1;
      if (isLastRealItem || isAtVisualRowEnd) {
        return section.startIndex + section.localIndex;
      }
      return section.startIndex + section.localIndex + 1;
    }

    if (direction === 'up') {
      if (row > 0) {
        return section.startIndex + Math.max(0, section.localIndex - columnCount);
      }

      const previousSection = getSiblingGridSection(section, -1);
      if (!previousSection) {
        return section.startIndex + section.localIndex;
      }

      const previousLastRow = Math.floor((previousSection.totalItems - 1) / columnCount);
      const previousLastColumn = (previousSection.totalItems - 1) % columnCount;
      const targetColumn = Math.min(column, previousLastColumn);
      return previousSection.startIndex + previousLastRow * columnCount + targetColumn;
    }

    const targetLocalIndex = section.localIndex + columnCount;
    if (targetLocalIndex < section.totalItems) {
      return section.startIndex + targetLocalIndex;
    }

    if (row < lastRow) {
      return section.startIndex + lastRow * columnCount + Math.min(column, lastColumnInLastRow);
    }

    const nextSection = getSiblingGridSection(section, 1);
    if (!nextSection) {
      return section.startIndex + section.localIndex;
    }

    return nextSection.startIndex + Math.min(column, nextSection.totalItems - 1);
  }

  function addGridSectionRangeSelection(
    next: SelectedFileMap,
    sectionStartIndex: number,
    sectionTotalItems: number,
    fromLocalIndex: number,
    toLocalIndex: number,
    gridColumnCount: number,
  ) {
    const columnCount = Math.max(gridColumnCount, 1);
    const clampedFromLocalIndex = Math.max(0, Math.min(fromLocalIndex, sectionTotalItems - 1));
    const clampedToLocalIndex = Math.max(0, Math.min(toLocalIndex, sectionTotalItems - 1));
    const minRow = Math.min(
      Math.floor(clampedFromLocalIndex / columnCount),
      Math.floor(clampedToLocalIndex / columnCount),
    );
    const maxRow = Math.max(
      Math.floor(clampedFromLocalIndex / columnCount),
      Math.floor(clampedToLocalIndex / columnCount),
    );
    const minColumn = Math.min(clampedFromLocalIndex % columnCount, clampedToLocalIndex % columnCount);
    const maxColumn = Math.max(clampedFromLocalIndex % columnCount, clampedToLocalIndex % columnCount);

    for (let row = minRow; row <= maxRow; row += 1) {
      for (let column = minColumn; column <= maxColumn; column += 1) {
        const localIndex = row * columnCount + column;
        if (localIndex >= sectionTotalItems) {
          continue;
        }
        const file = sortedRows[sectionStartIndex + localIndex];
        if (file) {
          next[getSelectionKey(file)] = file;
        }
      }
    }
  }

  function selectKeyboardRange(anchorIndex: number, targetIndex: number, mode: ViewMode, gridColumnCount: number) {
    const clampedAnchorIndex = clampRowIndex(anchorIndex);
    const clampedTargetIndex = clampRowIndex(targetIndex);
    const next: SelectedFileMap = {};

    if (mode === 'grid') {
      const anchorSection = getGridSectionInfo(clampedAnchorIndex);
      const targetSection = getGridSectionInfo(clampedTargetIndex);

      if (!anchorSection || !targetSection) {
        return;
      }

      if (anchorSection.startIndex === targetSection.startIndex) {
        addGridSectionRangeSelection(
          next,
          anchorSection.startIndex,
          anchorSection.totalItems,
          anchorSection.localIndex,
          targetSection.localIndex,
          gridColumnCount,
        );
      } else if (clampedAnchorIndex < clampedTargetIndex) {
        addGridSectionRangeSelection(
          next,
          anchorSection.startIndex,
          anchorSection.totalItems,
          anchorSection.localIndex,
          anchorSection.totalItems - 1,
          gridColumnCount,
        );
        addGridSectionRangeSelection(
          next,
          targetSection.startIndex,
          targetSection.totalItems,
          0,
          targetSection.localIndex,
          gridColumnCount,
        );
      } else {
        addGridSectionRangeSelection(
          next,
          anchorSection.startIndex,
          anchorSection.totalItems,
          anchorSection.localIndex,
          0,
          gridColumnCount,
        );
        addGridSectionRangeSelection(
          next,
          targetSection.startIndex,
          targetSection.totalItems,
          targetSection.totalItems - 1,
          targetSection.localIndex,
          gridColumnCount,
        );
      }
    } else {
      const startIndex = Math.min(clampedAnchorIndex, clampedTargetIndex);
      const endIndex = Math.max(clampedAnchorIndex, clampedTargetIndex);
      for (let index = startIndex; index <= endIndex; index += 1) {
        const file = sortedRows[index];
        if (file) {
          next[getSelectionKey(file)] = file;
        }
      }
    }

    lastSelectedIndexRef.current = clampedTargetIndex;
    setSelectedById(next);
  }

  function getSelectedGridColumnCount(file: FileItem) {
    const selectedElement = document.querySelector<HTMLElement>(
      `[data-file-selection-key="${getSelectionKey(file)}"][data-files-grid-card="true"]`,
    );
    const gridElement = selectedElement?.parentElement;
    if (!gridElement) {
      return 1;
    }

    const columns = window
      .getComputedStyle(gridElement)
      .gridTemplateColumns
      .split(' ')
      .filter((column) => column.trim() && column !== 'none');
    return Math.max(columns.length, 1);
  }

  function moveKeyboardSelection(delta: number) {
    const currentIndex = getSelectedKeyboardIndex();
    selectKeyboardRow(currentIndex < 0 ? 0 : currentIndex + delta);
  }

  function moveGridKeyboardSelection(direction: 'up' | 'down' | 'left' | 'right', gridColumnCount: number) {
    const currentIndex = getSelectedKeyboardIndex();
    if (currentIndex < 0) {
      selectKeyboardRow(0);
      return;
    }
    selectKeyboardRow(getGridNavigationTargetIndex(currentIndex, direction, gridColumnCount));
  }

  function extendKeyboardSelection(delta: number, mode: ViewMode, gridColumnCount: number) {
    const currentIndex = getSelectedKeyboardIndex();
    const anchorIndex = keyboardSelectionAnchorRef.current ?? (currentIndex < 0 ? 0 : currentIndex);
    const targetIndex = currentIndex < 0 ? 0 : currentIndex + delta;
    keyboardSelectionAnchorRef.current = clampRowIndex(anchorIndex);
    selectKeyboardRange(anchorIndex, targetIndex, mode, gridColumnCount);
  }

  function extendGridKeyboardSelection(direction: 'up' | 'down' | 'left' | 'right', gridColumnCount: number) {
    const currentIndex = getSelectedKeyboardIndex();
    const anchorIndex = keyboardSelectionAnchorRef.current ?? (currentIndex < 0 ? 0 : currentIndex);
    const targetIndex = currentIndex < 0 ? 0 : getGridNavigationTargetIndex(currentIndex, direction, gridColumnCount);
    keyboardSelectionAnchorRef.current = clampRowIndex(anchorIndex);
    selectKeyboardRange(anchorIndex, targetIndex, 'grid', gridColumnCount);
  }

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
      emitWorkspaceFolderTreeRefresh([currentPath]);
      refreshCurrentListing();
    },
  });

  const renameMutation = useMutation({
    mutationFn: ({ fileId, filename }: { fileId: number; filename: string; file: FileItem }) => renameFile(fileId, filename),
    onSuccess: (result, variables) => {
      const previousPath = getLogicalPath(variables.file);
      const nextPath = getLogicalPath(result);
      emitWorkspaceFolderTreeRefresh([
        getWorkspaceFolderParentPath(previousPath),
        getWorkspaceFolderParentPath(nextPath),
      ]);

      if (variables.file.directory) {
        const nextCurrentPath = replaceLogicalPathPrefix(currentPath, previousPath, nextPath);
        if (nextCurrentPath !== currentPath) {
          handlePathChange(nextCurrentPath, { replaceUrl: true });
          return;
        }
      }

      refreshCurrentListing();
    },
  });

  function applyAppearanceUpdate(updatedFile: FileItem) {
    setAllRows((prev) => prev.map((file) => (file.id === updatedFile.id ? updatedFile : file)));
    setSelectedById((prev) => {
      const key = getSelectionKey(updatedFile);
      if (!prev[key]) {
        return prev;
      }
      return {
        ...prev,
        [key]: updatedFile,
      };
    });
    if (detailFileId === updatedFile.id && detail) {
      setDetail({
        ...detail,
        ...updatedFile,
        path: getLogicalPath(updatedFile),
      });
    }
    if (updatedFile.directory) {
      emitWorkspaceFolderTreeRefresh([getWorkspaceFolderParentPath(getLogicalPath(updatedFile))]);
    }
    refreshCurrentListing();
  }

  function applyMoveResult(result: MoveResponse, sourceItems: FileItem[]) {
    const affectedPaths = new Set<string>();
    let nextCurrentPath: string | null = null;
    let hasActualChange = false;

    result.items.forEach((item) => {
      if (!item.toPath || item.skipped) {
        return;
      }
      const source = sourceItems.find((candidate) => candidate.id === item.fileId);
      if (!source) {
        return;
      }
      const previousPath = getLogicalPath(source);
      affectedPaths.add(getWorkspaceFolderParentPath(previousPath));
      affectedPaths.add(getWorkspaceFolderParentPath(item.toPath));
      if (previousPath !== item.toPath || item.renamed) {
        hasActualChange = true;
      }

      if (source.directory) {
        const candidatePath = replaceLogicalPathPrefix(currentPath, previousPath, item.toPath);
        if (candidatePath !== currentPath) {
          nextCurrentPath = candidatePath;
        }
      }
    });

    if (affectedPaths.size > 0) {
      emitWorkspaceFolderTreeRefresh(Array.from(affectedPaths));
    }

    if (nextCurrentPath && nextCurrentPath !== currentPath) {
      handlePathChange(nextCurrentPath, { replaceUrl: true });
      return;
    }

    if (!hasActualChange) {
      return;
    }

    refreshCurrentListing();
  }

  const moveMutation = useMutation({
    mutationFn: async ({ items, targetPath }: { items: FileItem[]; targetPath: string }) => {
      if (items.length === 1) {
        return moveFile(items[0].id, targetPath);
      }
      return batchMoveFiles(items.map((item) => item.id), targetPath);
    },
    onMutate: () => {
      return { toastId: showToast({ message: '正在移动...', severity: 'info', loading: true, duration: null }) };
    },
    onSuccess: (result, variables, context) => {
      if (result.status === 'CONFLICT') {
        if (context?.toastId) {
          removeToast(context.toastId);
        }
        setMoveDialogState({
          open: true,
          items: variables.items,
          targetPath: variables.targetPath,
          initialConflictResult: result,
        });
        return;
      }
      if (result.status === 'INVALID_TARGET') {
        if (context?.toastId) {
          updateToast(context.toastId, {
            message: result.message || '目标位置不可用',
            severity: 'error',
            loading: false,
            duration: 5000,
          });
        }
        return;
      }
      
      applyMoveResult(result, variables.items);
      
      const targetPath = variables.targetPath;
      if (context?.toastId) {
        updateToast(context.toastId, { 
          message: '任务成功', 
          severity: 'success',
          loading: false,
          duration: 6000,
          actions: [
            {
              label: '查看',
              icon: <Eye size={14} />,
              onClick: () => {
                if (targetPath !== currentPath) {
                  handlePathChange(targetPath);
                } else {
                  refreshCurrentListing();
                }
              },
            },
            {
              label: '恢复',
              icon: <RefreshCw size={14} />,
              onClick: async () => {
                const itemsToRestore = result.items.filter((item) => !item.skipped && item.fromPath);
                if (itemsToRestore.length === 0) {
                  return;
                }

                const restoreToastId = showToast({ message: '正在移动...', severity: 'info', loading: true, duration: null });
                try {
                  for (const item of itemsToRestore) {
                    const restoreResult = await moveFile(item.fileId, getWorkspaceFolderParentPath(item.fromPath!));
                    if (restoreResult.status !== 'SUCCESS') {
                      throw new Error(restoreResult.message || '恢复失败');
                    }
                  }

                  updateToast(restoreToastId, {
                    message: '任务成功',
                    severity: 'success',
                    loading: false,
                    duration: 5000,
                  });
                  refreshCurrentListing();
                } catch (error) {
                  updateToast(restoreToastId, {
                    message: isTimeoutError(error) ? '任务超时' : (error instanceof Error ? error.message : '恢复失败'),
                    severity: 'error',
                    loading: false,
                    duration: 5000,
                  });
                }
              },
            },
          ],
        });
      }
    },
    onError: (error, variables, context) => {
      if (context?.toastId) {
        updateToast(context.toastId, {
          message: isTimeoutError(error) ? '任务超时' : (error instanceof Error ? error.message : '移动失败'),
          severity: 'error',
          loading: false,
          duration: 5000,
        });
      }
    },
  });

  const { dragState, onMouseDown: startDragMove, registerDropTarget, activeDropTarget } = useWorkspaceDragMove(
    (items, targetPath) => {
      moveMutation.mutate({ items, targetPath });
    },
  );

  const copyMutation = useMutation({
    mutationFn: ({ fileId, path }: { fileId: number; path: string }) => copyFile(fileId, path),
    onSuccess: (result) => {
      emitWorkspaceFolderTreeRefresh([getWorkspaceFolderParentPath(getLogicalPath(result))]);
      refreshCurrentListing();
    },
  });

  const uploadMutation = useMutation({
    mutationFn: async ({ files, folderPath }: { files: File[]; folderPath?: string }) => {
      const targetPath = folderPath ? joinDirectoryPath(currentPath, folderPath) : currentPath;
      addUploadTasks(files, targetPath);
      setUploadPanelOpen(true);
      return files.length;
    },
    onSuccess: (count) => {
      setUploadStatus(`已加入队列 ${count} 个文件`);
    },
    onError: (error) => {
      showToast({ message: error instanceof Error ? error.message : '上传失败', severity: 'error' });
    },
  });

  const openWithPreferenceMutation = useMutation({
    mutationFn: ({ extension, viewerId }: { extension: string; viewerId: string }) =>
      updateUserSettings({
        defaultOpenWithByExt: setDefaultViewerPreference(userSettings?.defaultOpenWithByExt, extension, viewerId),
      }),
    onSuccess: (settings) => {
      queryClient.setQueryData(['userSettings'], settings);
    },
  });

  function handleOpenWithSelect(viewer: FileViewerDefinition, alwaysUse: boolean) {
    if (!openWithState) {
      return;
    }
    const { file, extension } = openWithState;
    openFileWithViewer(file, viewer);
    if (alwaysUse && extension) {
      openWithPreferenceMutation.mutate({ extension, viewerId: viewer.id });
    }
  }

  function shareFile(file: FileItem) {
    setShareFileItem(file);
    setShareDialogOpen(true);
    closeContextMenus();
  }
  const deleteMutation = useMutation({
    mutationFn: ({ fileIds, mode }: { fileIds: number[]; mode: FileDeleteMode }) => 
      batchDeleteFiles(fileIds, mode),
    onMutate: () => {
      return { toastId: showToast({ message: '正在删除...', severity: 'info', loading: true, duration: null }) };
    },
    onSuccess: (_, variables, context) => {
      if (context?.toastId) {
        updateToast(context.toastId, {
          message: '任务成功',
          severity: 'success',
          loading: false,
          duration: 5000,
        });
      }

      const { fileIds } = variables;
      emitWorkspaceFolderTreeRefresh(
        Array.from(
          new Set(
            selectedFiles
              .filter((file) => fileIds.includes(file.id))
              .map((file) => normalizeWorkspaceFolderPath(file.path)),
          ),
        ),
      );
      setSelectedById({});
      setContextMenu(null);
      setDetailFileId(null);
      setDetail(null);
      setDetailError(null);
      setDeleteDialogState({ open: false, files: [] });
      refreshCurrentListing();
      void refetchFavorites();
    },
    onError: (error, variables, context) => {
      if (context?.toastId) {
        updateToast(context.toastId, {
          message: isTimeoutError(error) ? '任务超时' : (error instanceof Error ? error.message : '删除失败'),
          severity: 'error',
          loading: false,
          duration: 5000,
        });
      }
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

  useEffect(() => {
    const handleUploadSuccess = (event: Event) => {
      const detail = (event as CustomEvent<{ path: string }>).detail;
      if (detail.path === currentPath) {
        refreshCurrentListing();
      }
    };

    window.addEventListener('upload-success', handleUploadSuccess);
    return () => {
      window.removeEventListener('upload-success', handleUploadSuccess);
    };
  }, [currentPath, refreshCurrentListing]);

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

  const folderDownloadMutation = useMutation({
    mutationFn: async ({ file, mode }: { file: FileItem; mode: FolderDownloadMode }) => {
      if (mode === 'server-archive') {
        return {
          mode,
          file,
          archiveBlob: await downloadFileBlob(file.id),
          downloadedFiles: [],
        };
      }

      if (mode === 'browser-archive') {
        return {
          mode,
          file,
          archiveBlob: await buildBrowserFolderArchive(file),
          downloadedFiles: [],
        };
      }

      const entries = await collectFolderDownloadEntries(file);
      const downloadedFiles = await Promise.all(
        entries.files.map(async (entry) => ({
          filename: entry.file.filename,
          blob: await downloadFileBlob(entry.file.id),
        })),
      );
      return {
        mode,
        file,
        archiveBlob: null,
        downloadedFiles,
      };
    },
    onSuccess: ({ file, archiveBlob, downloadedFiles }) => {
      if (archiveBlob) {
        triggerBlobDownload(archiveBlob, `${file.filename}.zip`);
        return;
      }

      if (downloadedFiles.length === 0) {
        showToast({ message: '该文件夹没有可下载的文件', severity: 'warning' });
        return;
      }

      downloadedFiles.forEach((downloadedFile, index) => {
        window.setTimeout(() => {
          triggerBlobDownload(downloadedFile.blob, downloadedFile.filename);
        }, index * 250);
      });
    },
    onError: (error, variables) => {
      const modeLabel = FOLDER_DOWNLOAD_MODE_LABELS[variables.mode];
      showToast({ message: error instanceof Error ? error.message : `${modeLabel}失败`, severity: 'error' });
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
    if (sortedRows.length === 0) {
      return;
    }

    const focusFile = sortedRows[clampRowIndex(lastSelectedIndexRef.current)];
    if (!focusFile) {
      return;
    }

    const selectedKey = getSelectionKey(focusFile);
    if (!selectedById[selectedKey]) {
      return;
    }

    const frameId = window.requestAnimationFrame(() => {
      document
        .querySelector<HTMLElement>(`[data-file-selection-key="${selectedKey}"]`)
        ?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    });

    return () => window.cancelAnimationFrame(frameId);
  }, [selectedById, sortedRows]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (isInputTarget(event.target)) {
        return;
      }

      if (event.key === 'Escape') {
        setSelectedById({});
        setContextMenu(null);
        keyboardSelectionAnchorRef.current = null;
        return;
      }

      if (!keyboardNavigationBlocked) {
        const isKeyboardMultiSelect = event.metaKey || event.ctrlKey;
        const isPrimaryShortcut = (event.metaKey || event.ctrlKey) && !event.altKey && !event.shiftKey;
        const shortcutKey = event.key.toLowerCase();

        if (isPrimaryShortcut && shortcutKey === 'a') {
          event.preventDefault();
          selectAllRows();
          return;
        }

        if (isPrimaryShortcut && shortcutKey === 'c' && selectedFiles.length > 0) {
          event.preventDefault();
          openMoveDialog(selectedFiles);
          return;
        }

        if (event.key === 'Enter') {
          const selectedIndex = getSelectedKeyboardIndex();
          const selectedFile = selectedIndex >= 0 ? sortedRows[selectedIndex] : null;
          if (selectedFile) {
            event.preventDefault();
            openFile(selectedFile);
          }
          return;
        }

        if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
          event.preventDefault();

          if (viewMode === 'grid') {
            const selectedIndex = getSelectedKeyboardIndex();
            if (selectedIndex < 0) {
              selectKeyboardRow(0);
              return;
            }

            const selectedFile = sortedRows[selectedIndex];
            const gridColumnCount = getSelectedGridColumnCount(selectedFile);
            const direction = event.key === 'ArrowUp' ? 'up' : 'down';
            if (isKeyboardMultiSelect) {
              extendGridKeyboardSelection(direction, gridColumnCount);
            } else {
              moveGridKeyboardSelection(direction, gridColumnCount);
            }
            return;
          }

          const delta = event.key === 'ArrowUp' ? -1 : 1;
          if (isKeyboardMultiSelect) {
            extendKeyboardSelection(delta, 'list', 1);
          } else {
            moveKeyboardSelection(delta);
          }
          return;
        }

        if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
          event.preventDefault();

          if (viewMode === 'grid') {
            const selectedIndex = getSelectedKeyboardIndex();
            if (selectedIndex < 0) {
              selectKeyboardRow(0);
              return;
            }

            const selectedFile = sortedRows[selectedIndex];
            const gridColumnCount = getSelectedGridColumnCount(selectedFile);
            const direction = event.key === 'ArrowLeft' ? 'left' : 'right';
            if (isKeyboardMultiSelect) {
              extendGridKeyboardSelection(direction, gridColumnCount);
            } else {
              moveGridKeyboardSelection(direction, gridColumnCount);
            }
            return;
          }

          if (isKeyboardMultiSelect) {
            extendKeyboardSelection(event.key === 'ArrowLeft' ? -1 : 1, 'list', 1);
            return;
          }

          if (event.key === 'ArrowLeft') {
            if (!isCategoryMode && currentPath !== '/') {
              handlePathChange(getParentDirectoryPath(currentPath));
            }
            return;
          }

          const selectedIndex = getSelectedKeyboardIndex();
          if (selectedIndex < 0) {
            selectKeyboardRow(0);
            return;
          }
          const selectedFile = selectedIndex >= 0 ? sortedRows[selectedIndex] : null;
          if (selectedFile?.directory) {
            openDirectory(selectedFile);
          }
          return;
        }

        if (
          event.key === 'Backspace' &&
          viewMode === 'grid' &&
          !event.metaKey &&
          !event.ctrlKey &&
          !event.altKey &&
          !isCategoryMode &&
          currentPath !== '/'
        ) {
          event.preventDefault();
          handlePathChange(getParentDirectoryPath(currentPath));
          return;
        }
      }

      const shouldDelete =
        (!isMacPlatform && event.key === 'Delete')
        || (isMacPlatform && event.key === 'Backspace' && event.metaKey && !event.ctrlKey && !event.altKey);

      if (shouldDelete && selectedFiles.length > 0) {
        event.preventDefault();
        deleteFiles(selectedFiles);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [
    currentPath,
    deleteFiles,
    isCategoryMode,
    isMacPlatform,
    keyboardNavigationBlocked,
    selectedFiles,
    sortedRows,
    viewMode,
  ]);

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

  function updatePathSearchParam(path: string, replace = false) {
    const normalizedPath = normalizeWorkspaceFolderPath(path);
    const nextSearchParams = new URLSearchParams(searchParams);

    if (normalizedPath === '/') {
      nextSearchParams.delete(FILES_PATH_SEARCH_PARAM);
    } else {
      nextSearchParams.set(FILES_PATH_SEARCH_PARAM, normalizedPath);
    }

    setSearchParams(nextSearchParams, { replace });
  }

  function resetBrowsingState() {
    setSelectedById({});
    setContextMenu(null);
    setPreviewFile(null);
    setActiveViewer(null);
    setOpenWithState(null);
    setDetailFileId(null);
    setDetail(null);
    setDetailError(null);
    lastSelectedIndexRef.current = 0;
    keyboardSelectionAnchorRef.current = null;
    setAllRows([]);
    setPage(1);
  }

  function handlePathChange(path: string, options?: { updateUrl?: boolean; replaceUrl?: boolean }) {
    const normalizedPath = normalizeWorkspaceFolderPath(path);
    if (normalizedPath !== currentPath) {
      resetBrowsingState();
      setCurrentPath(normalizedPath);
    }

    if (!isCategoryMode && options?.updateUrl !== false) {
      updatePathSearchParam(normalizedPath, options?.replaceUrl);
    }
  }

  function handleSearchChange(nextSearch: string) {
    resetBrowsingState();
    setSearch(nextSearch);
  }

  useEffect(() => {
    if (isCategoryMode) {
      return;
    }
    if (requestedPath !== currentPath) {
      handlePathChange(requestedPath, { updateUrl: false, replaceUrl: true });
    }
  }, [currentPath, isCategoryMode, requestedPath]);

  function openDirectory(file: FileItem) {
    if (!file.directory || isCategoryMode) {
      return;
    }
    handlePathChange(getLogicalPath(file));
  }

  function closeContextMenus() {
    closeSubmenus();
    contextMenuPopupState.close();
    setContextMenu(null);
  }

  function openFileWithViewer(file: FileItem, viewer: FileViewerDefinition) {
    closeContextMenus();
    setOpenWithState(null);
    setActiveViewer(viewer);
    setPreviewFile(file);
  }

  function openFile(file: FileItem, options?: { forcePicker?: boolean; viewerId?: string }) {
    closeContextMenus();
    if (file.directory) {
      openDirectory(file);
      return;
    }

    if (!viewerConfig) {
      showToast({ message: '打开方式配置仍在加载，请稍后再试', severity: 'warning' });
      return;
    }

    const extension = getFileExtension(file);
    const allViewers = getAllFileViewers(viewerConfig);
    const availableViewers = getAvailableViewersForFile(viewerConfig, file, extension);

    if (options?.viewerId) {
      const requestedViewer = availableViewers.find((viewer) => viewer.id === options.viewerId);
      if (requestedViewer) {
        openFileWithViewer(file, requestedViewer);
        return;
      }
    }

    if (!options?.forcePicker) {
      const preferredViewerId = extension ? userSettings?.defaultOpenWithByExt?.[extension] : null;
      const preferredViewer = preferredViewerId ? availableViewers.find((viewer) => viewer.id === preferredViewerId) : null;
      if (preferredViewer) {
        openFileWithViewer(file, preferredViewer);
        return;
      }
    }

    setOpenWithState({
      file,
      extension,
      recommendedViewers: getRecommendedViewersForFile(viewerConfig, file, extension),
      allViewers,
      availableViewerIds: availableViewers.map((viewer) => viewer.id),
    });
  }

  function selectFile(file: FileItem, index: number, event?: React.MouseEvent<HTMLElement>) {
    const isMultiSelectModifierPressed = Boolean(
      event && ((isMacPlatform && event.metaKey && !event.ctrlKey) || (!isMacPlatform && event.ctrlKey && !event.metaKey)),
    );
    if (isMultiSelectModifierPressed) {
      toggleFileSelection(file, index);
      return;
    }

    lastSelectedIndexRef.current = index;
    keyboardSelectionAnchorRef.current = index;
    setSelectedById({ [getSelectionKey(file)]: file });
  }

  function toggleFileSelection(file: FileItem, index: number) {
    lastSelectedIndexRef.current = index;
    keyboardSelectionAnchorRef.current = index;
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
      keyboardSelectionAnchorRef.current = index;
      setSelectedById({ [getSelectionKey(file)]: file });
    }
    closeSubmenus();
    setContextMenu({ file });
    contextMenuPopupState.open(event);
  }

  function openEmptyContextMenu(event: React.MouseEvent<HTMLElement>) {
    event.preventDefault();
    closeSubmenus();
    setContextMenu({});
    contextMenuPopupState.open(event);
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

  function deleteFiles(files: FileItem[]) {
    if (files.length === 0) {
      return;
    }
    closeContextMenus();
    setDeleteDialogState({ open: true, files });
  }

  function downloadFile(file: FileItem, mode: FolderDownloadMode = 'server-archive') {
    if (file.directory) {
      closeContextMenus();
      folderDownloadMutation.mutate({ file, mode });
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
      renameMutation.mutate({ fileId: file.id, filename: nextName.trim(), file });
    }
    closeContextMenus();
  }

  function resolveActionItems(file: FileItem) {
    if (selectedCount > 1 && selectedById[getSelectionKey(file)]) {
      return selectedFiles;
    }
    return [file];
  }

  function moveFileAction(file: FileItem) {
    openMoveDialog(resolveActionItems(file));
  }

  function openAppearanceDialog(file: FileItem) {
    setAppearanceFile(file);
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
      showToast({ message: '直链已复制到剪贴板', severity: 'success' });
    } catch (e) {
      showToast({ message: '获取直链失败', severity: 'error' });
    }
    closeContextMenus();
  }

  async function uploadFromClipboard() {
    try {
      const items = await navigator.clipboard.read();
      const files: File[] = [];
      const timestamp = getClipboardTimestamp();
      let index = 0;

      for (const item of items) {
        const preferredType = pickPreferredClipboardType(item.types);
        if (!preferredType) {
          continue;
        }

        const blob = await item.getType(preferredType);
        const resolvedType = blob.type || preferredType;
        const extension = inferClipboardExtension(resolvedType);
        const filename = await buildClipboardFilename(item, resolvedType, extension, timestamp, index);
        files.push(new File([blob], filename, { type: resolvedType }));
        index += 1;
      }

      if (files.length > 0) {
        uploadMutation.mutate({ files });
      } else {
        showToast({ message: '剪贴板中没有可上传的文件', severity: 'warning' });
      }
    } catch (e) {
      showToast({ message: '无法访问剪贴板或剪贴板为空', severity: 'error' });
    }
    closeContextMenus();
  }

  function getNewDocumentViewerId(type: 'md' | 'drawio' | 'txt' | 'excalidraw') {
    if (type === 'md') return 'markdown';
    if (type === 'drawio') return 'drawio';
    if (type === 'excalidraw') return 'excalidraw';
    return 'code-monaco';
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
    selectAllRows();
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

  function handleDragStart(file: FileItem, event: React.MouseEvent) {
    const target = event.target as HTMLElement;
    if (target.closest('button, input, a, [role="button"]')) {
      return;
    }
    event.preventDefault();
    startDragMove(resolveActionItems(file), event);
  }

  return (
    <DashboardLayout
      title={categoryMeta?.title ?? '文件 Files'}
      hideHeader={true}
      registerDropTarget={registerDropTarget}
      activeDropTarget={activeDropTarget}
    >
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
            rootLabel={categoryMeta?.rootLabel ?? '根目录'}
            pathNavigationEnabled={!isCategoryMode}
            registerDropTarget={registerDropTarget}
            activeDropTarget={activeDropTarget}
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
                focusedSelectionKey={sortedRows.length > 0 ? getSelectionKey(sortedRows[clampRowIndex(lastSelectedIndexRef.current)]) : undefined}
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
                onDragStart={handleDragStart}
                registerDropTarget={registerDropTarget}
                activeDropTarget={activeDropTarget}
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
          {...bindMenu(contextMenuPopupState)}
          keepMounted
          open={contextMenu != null && contextMenuPopupState.isOpen}
          onClose={closeContextMenus}
          disableAutoFocusItem
        >
          {menuMode === 'empty' && (
            <Box>
              <MenuItem onClick={() => { setContextMenu(null); fileInputRef.current?.click(); }}>
                <ListItemIcon><Upload size={16} /></ListItemIcon>
                <ListItemText>上传文件</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => { setContextMenu(null); folderInputRef.current?.click(); }}>
                <ListItemIcon><Archive size={16} /></ListItemIcon>
                <ListItemText>上传文件夹</ListItemText>
              </MenuItem>
              <MenuItem onClick={uploadFromClipboard}>
                <ListItemIcon><ClipboardPaste size={16} /></ListItemIcon>
                <ListItemText>从剪贴板上传</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => { setRemoteDownloadDialogOpen(true); setContextMenu(null); }}>
                <ListItemIcon><CloudDownload size={16} /></ListItemIcon>
                <ListItemText>离线下载</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem onClick={createFolder}>
                <ListItemIcon><FolderPlus size={16} /></ListItemIcon>
                <ListItemText>创建文件夹</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => createNewDocument('txt')}>
                <ListItemIcon><FileText size={16} /></ListItemIcon>
                <ListItemText>创建文件</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem onClick={() => createNewDocument('md')}>
                <ListItemIcon><FileText size={16} /></ListItemIcon>
                <ListItemText>Markdown (.md)</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => createNewDocument('drawio')}>
                <ListItemIcon><FileText size={16} /></ListItemIcon>
                <ListItemText>draw.io</ListItemText>
                <ChevronRight size={16} style={{ marginLeft: 'auto', opacity: 0.5 }} />
              </MenuItem>
              <MenuItem onClick={() => createNewDocument('txt')}>
                <ListItemIcon><FileText size={16} /></ListItemIcon>
                <ListItemText>Text (.txt)</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => createNewDocument('excalidraw')}>
                <ListItemIcon><FileText size={16} /></ListItemIcon>
                <ListItemText>Excalidraw (.excalidraw)</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem onClick={() => { setPage(1); void refetch(); setContextMenu(null); }}>
                <ListItemIcon><RefreshCw size={16} /></ListItemIcon>
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
                  openMoveDialog(selectedFiles);
                }}
              >
                <ListItemIcon><FolderInput size={16} /></ListItemIcon>
                <ListItemText>移动到</ListItemText>
              </MenuItem>
              <MenuItem
                onClick={() => {
                  setSelectedById({});
                  setContextMenu(null);
                }}
              >
                <ListItemIcon><X size={16} /></ListItemIcon>
                <ListItemText>取消选择</ListItemText>
              </MenuItem>
              <MenuItem sx={{ color: 'error.main' }} onClick={() => deleteFiles(selectedFiles)}>
                <ListItemIcon><Trash2 size={16} /></ListItemIcon>
                <ListItemText>批量删除</ListItemText>
              </MenuItem>
            </Box>
          )}

          {menuMode === 'folder' && activeMenuFile && (
            <CascadingContext.Provider
              value={{
                rootPopupState: contextMenuPopupState,
                parentPopupState: contextMenuPopupState,
              }}
            >
            <Box>
              <MenuItem onClick={() => openFile(activeMenuFile)}>
                <ListItemIcon><FolderOpen size={16} /></ListItemIcon>
                <ListItemText>进入</ListItemText>
              </MenuItem>
              <CascadingSubmenu title="下载" popupId="files-folder-download-submenu" icon={<Download size={16} />}>
                <CascadingMenuItem onClick={() => downloadFile(activeMenuFile, 'server-archive')}>
                  <ListItemIcon><Archive size={16} /></ListItemIcon>
                  <ListItemText>服务器端打包</ListItemText>
                </CascadingMenuItem>
                <CascadingMenuItem onClick={() => downloadFile(activeMenuFile, 'browser-archive')}>
                  <ListItemIcon><Archive size={16} /></ListItemIcon>
                  <ListItemText>浏览器打包</ListItemText>
                </CascadingMenuItem>
                <CascadingMenuItem onClick={() => downloadFile(activeMenuFile, 'individual-files')}>
                  <ListItemIcon><Download size={16} /></ListItemIcon>
                  <ListItemText>逐一文件下载</ListItemText>
                </CascadingMenuItem>
              </CascadingSubmenu>
              <Divider />
              <MenuItem onClick={() => shareFile(activeMenuFile)}>
                <ListItemIcon><Share2 size={16} /></ListItemIcon>
                <ListItemText>分享</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => renameFileAction(activeMenuFile)}>
                <ListItemIcon><Pencil size={16} /></ListItemIcon>
                <ListItemText>重命名</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => copyFileAction(activeMenuFile)}>
                <ListItemIcon><Copy size={16} /></ListItemIcon>
                <ListItemText>复制</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => getDirectLink(activeMenuFile)}>
                <ListItemIcon><Link2 size={16} /></ListItemIcon>
                <ListItemText>获取直链</ListItemText>
              </MenuItem>
              <Divider />
              <CascadingSubmenu title="标签" popupId="files-tag-submenu" icon={<Tags size={16} />}>
                <CascadingMenuItem
                  onClick={() => {
                    setTagFile(activeMenuFile || null);
                    closeContextMenus();
                    setTagManagerOpen(true);
                  }}
                >
                  <ListItemIcon><Tags size={16} /></ListItemIcon>
                  <ListItemText>管理标签</ListItemText>
                </CascadingMenuItem>
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
                        {assigned ? <Check size={14} /> : null}
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
              </CascadingSubmenu>
              <CascadingSubmenu title="整理" popupId="files-arrange-submenu" icon={<FolderInput size={16} />}>
                <CascadingMenuItem onClick={() => moveFileAction(activeMenuFile)}>
                  <ListItemIcon><FolderInput size={16} /></ListItemIcon>
                  <ListItemText>移动到</ListItemText>
                </CascadingMenuItem>
                <CascadingMenuItem onClick={() => openAppearanceDialog(activeMenuFile)}>
                  <ListItemIcon><Circle size={14} /></ListItemIcon>
                  <ListItemText>自定义图标</ListItemText>
                </CascadingMenuItem>
              </CascadingSubmenu>
              <Divider />
              <MenuItem onClick={() => openDetail(activeMenuFile)}>
                <ListItemIcon><Info size={16} /></ListItemIcon>
                <ListItemText>详情</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem sx={{ color: 'error.main' }} onClick={() => deleteFiles([activeMenuFile])}>
                <ListItemIcon><Trash2 size={16} /></ListItemIcon>
                <ListItemText>删除</ListItemText>
              </MenuItem>
            </Box>
            </CascadingContext.Provider>
          )}

          {menuMode === 'file' && activeMenuFile && (
            <CascadingContext.Provider
              value={{
                rootPopupState: contextMenuPopupState,
                parentPopupState: contextMenuPopupState,
              }}
            >
            <Box>
              <MenuItem onClick={() => openFile(activeMenuFile)}>
                <ListItemIcon><FolderOpen size={16} /></ListItemIcon>
                <ListItemText>打开</ListItemText>
              </MenuItem>
              {activeMenuFileAllViewers.length > 0 || activeMenuFileAvailableViewers.length > 0 ? (
                <CascadingSubmenu title="打开方式" popupId="files-open-with-submenu" icon={<Eye size={16} />}>
                  {activeMenuFileRecommendedViewers.map((viewer) => (
                    <CascadingMenuItem key={viewer.id} onClick={() => openFile(activeMenuFile, { viewerId: viewer.id })}>
                      <ListItemIcon><FileText size={16} /></ListItemIcon>
                      <ListItemText>{viewer.displayName}</ListItemText>
                    </CascadingMenuItem>
                  ))}
                  {activeMenuFileRecommendedViewers.length > 0 ? <Divider /> : null}
                  <CascadingMenuItem onClick={() => openFile(activeMenuFile, { forcePicker: true })}>
                    <ListItemIcon><PanelsTopLeft size={16} /></ListItemIcon>
                    <ListItemText>所有打开方式</ListItemText>
                  </CascadingMenuItem>
                </CascadingSubmenu>
              ) : (
                <MenuItem onClick={() => openFile(activeMenuFile, { forcePicker: true })}>
                  <ListItemIcon><Eye size={16} /></ListItemIcon>
                  <ListItemText>打开方式</ListItemText>
                </MenuItem>
              )}
              <MenuItem onClick={() => downloadFile(activeMenuFile)}>
                <ListItemIcon><Download size={16} /></ListItemIcon>
                <ListItemText>下载</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem onClick={() => shareFile(activeMenuFile)}>
                <ListItemIcon><Share2 size={16} /></ListItemIcon>
                <ListItemText>分享</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => renameFileAction(activeMenuFile)}>
                <ListItemIcon><Pencil size={16} /></ListItemIcon>
                <ListItemText>重命名</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => copyFileAction(activeMenuFile)}>
                <ListItemIcon><Copy size={16} /></ListItemIcon>
                <ListItemText>复制</ListItemText>
              </MenuItem>
              <MenuItem onClick={() => getDirectLink(activeMenuFile)}>
                <ListItemIcon><Link2 size={16} /></ListItemIcon>
                <ListItemText>获取直链</ListItemText>
              </MenuItem>
              <Divider />
              <CascadingSubmenu title="整理" popupId="files-arrange-submenu" icon={<FolderInput size={16} />}>
                <CascadingMenuItem onClick={() => moveFileAction(activeMenuFile)}>
                  <ListItemIcon><FolderInput size={16} /></ListItemIcon>
                  <ListItemText>移动到</ListItemText>
                </CascadingMenuItem>
                <CascadingMenuItem onClick={() => openAppearanceDialog(activeMenuFile)}>
                  <ListItemIcon><Circle size={14} /></ListItemIcon>
                  <ListItemText>自定义图标</ListItemText>
                </CascadingMenuItem>
              </CascadingSubmenu>
              <Divider />
              <MenuItem onClick={() => openDetail(activeMenuFile)}>
                <ListItemIcon><Info size={16} /></ListItemIcon>
                <ListItemText>详情</ListItemText>
              </MenuItem>
              <Divider />
              <MenuItem sx={{ color: 'error.main' }} onClick={() => deleteFiles([activeMenuFile])}>
                <ListItemIcon><Trash2 size={16} /></ListItemIcon>
                <ListItemText>删除</ListItemText>
              </MenuItem>
            </Box>
            </CascadingContext.Provider>
          )}
        </Menu>

        <OpenWithDialog
          open={openWithState != null}
          file={openWithState?.file ?? null}
          extension={openWithState?.extension ?? ''}
          recommendedViewers={openWithState?.recommendedViewers ?? []}
          allViewers={openWithState?.allViewers ?? []}
          availableViewerIds={openWithState?.availableViewerIds ?? []}
          onClose={() => setOpenWithState(null)}
          onSelect={handleOpenWithSelect}
        />
        <FileViewerHost
          file={previewFile}
          viewer={activeViewer}
          onClose={() => {
            setPreviewFile(null);
            setActiveViewer(null);
          }}
          onSaved={(updatedFile) => {
            setAllRows((prev) => prev.map((row) => (row.id === updatedFile.id ? updatedFile : row)));
            setSelectedById((prev) => {
              const key = getSelectionKey(updatedFile);
              if (!prev[key]) {
                return prev;
              }
              return { ...prev, [key]: updatedFile };
            });
            void refetch();
          }}
        />
        <FileTagsManagerDialog
          open={tagManagerOpen}
          onClose={() => {
            setTagManagerOpen(false);
            setTagFile(null);
          }}
          file={tagFile}
        />
        <CreateShareDialog
          open={shareDialogOpen}
          onClose={() => {
            setShareDialogOpen(false);
            setShareFileItem(null);
          }}
          file={shareFileItem}
        />
        <CreateRemoteDownloadDialog
          open={remoteDownloadDialogOpen}
          defaultPath={currentPath}
          onClose={() => {
            setRemoteDownloadDialogOpen(false);
          }}
          onCreated={(detail) => {
            setUploadStatus(`离线下载任务已创建 #${detail.backgroundTaskId ?? detail.id}`);
            void queryClient.invalidateQueries({ queryKey: ['tasks'] });
          }}
        />
        <MoveItemsDialog
          open={moveDialogState.open}
          onClose={() => setMoveDialogState((current) => ({
            ...current,
            open: false,
            initialConflictResult: null,
          }))}
          items={moveDialogState.items}
          currentPath={moveDialogState.targetPath}
          initialConflictResult={moveDialogState.initialConflictResult}
          onSuccess={(result) => {
            applyMoveResult(result, moveDialogState.items);
            if (result.items.some((item) => item.renamed || item.skipped)) {
              showToast({ message: summarizeMoveResult(result), severity: 'info' });
            }
            setMoveDialogState({
              open: false,
              items: [],
              targetPath: currentPath,
              initialConflictResult: null,
            });
          }}
        />
        {appearanceFile ? (
          <AppearanceDialog
            open={Boolean(appearanceFile)}
            file={appearanceFile}
            onClose={() => setAppearanceFile(null)}
            onSuccess={(updatedFile) => {
              applyAppearanceUpdate(updatedFile);
              setAppearanceFile(null);
            }}
          />
        ) : null}
        <FileDeleteDialog
          open={deleteDialogState.open}
          files={deleteDialogState.files}
          onClose={() => setDeleteDialogState({ open: false, files: [] })}
          onConfirm={(mode) => {
            deleteMutation.mutate({
              fileIds: deleteDialogState.files.map((f) => f.id),
              mode,
            });
          }}
          loading={deleteMutation.isPending}
        />
        <WorkspaceDragOverlay dragState={dragState} />
      </MuiThemeProvider>
    </DashboardLayout>
  );
};

export default Files;
