import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Box, Button, Divider, Menu, MenuItem, Paper, Stack, Typography } from '@mui/material';
import {
  CreateNewFolder,
  DeleteOutline,
  Download,
  InfoOutlined,
  OpenInFull,
  Share,
  Star,
  StarBorder,
  UploadFile,
  Visibility,
} from '@mui/icons-material';
import { ThemeProvider as MuiThemeProvider, createTheme } from '@mui/material/styles';
import { useMutation } from '@tanstack/react-query';
import DashboardLayout from '../components/DashboardLayout';
import FileDetailsRail from '../components/files/FileDetailsRail';
import { FilesExplorerSurface } from '../components/files/FilesExplorerSurface';
import { FilesPreviewDialog } from '../components/files/FilesPreviewDialog';
import { FilesTopBar } from '../components/files/FilesTopBar';
import { useFavoriteFiles, useFiles } from '../api/queries';
import type { FileDetail, FileItem } from '../api/types';
import {
  batchDeleteFiles,
  createDirectory,
  createLegacyShareLink,
  downloadFileBlob,
  getFileDetail,
  getFileDownloadUrl,
  setFileFavorite,
  uploadFile,
} from '../lib/files';
import { useTheme as useAppTheme } from '../hooks/useTheme';

type ViewMode = 'grid' | 'list';

type ContextMenuState = {
  mouseX: number;
  mouseY: number;
  file?: FileItem;
};

type SelectedFileMap = Record<string, FileItem>;

const FILES_PAGE_SIZE = 30;
const VIEW_MODE_STORAGE_KEY = 'cloudreve-files-view-mode';

function joinDirectoryPath(parentPath: string, filename: string) {
  return parentPath === '/' ? `/${filename}` : `${parentPath}/${filename}`;
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
  const { theme } = useAppTheme();
  const muiTheme = useMemo(
    () =>
      createTheme({
        palette: {
          mode: theme,
          primary: {
            main: '#4F7CFF',
          },
          background: {
            default: theme === 'dark' ? '#0F1117' : '#F6F8FC',
            paper: theme === 'dark' ? '#171923' : '#FFFFFF',
          },
        },
        shape: {
          borderRadius: 8,
        },
        typography: {
          fontFamily: 'Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        },
      }),
    [theme],
  );

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const lastSelectedIndexRef = useRef(0);
  const [search, setSearch] = useState('');
  const [currentPath, setCurrentPath] = useState('/');
  const [page, setPage] = useState(1);
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

  const { data, isLoading, isError, refetch } = useFiles(currentPath, page, FILES_PAGE_SIZE, search);
  const { data: favoriteFiles, refetch: refetchFavorites } = useFavoriteFiles();

  const rows = useMemo(() => data?.items ?? [], [data]);
  const favoriteIds = useMemo(
    () => new Set((favoriteFiles ?? []).filter((item) => item.favorite).map((item) => item.fileId)),
    [favoriteFiles],
  );
  const selectedFiles = useMemo(() => Object.values(selectedById), [selectedById]);
  const selectedCount = selectedFiles.length;
  const selectedFolderCount = useMemo(() => selectedFiles.filter((file) => file.directory).length, [selectedFiles]);
  const selectedFileCount = selectedCount - selectedFolderCount;
  const allSelected = rows.length > 0 && rows.every((file) => selectedById[getSelectionKey(file)]);

  const createDirectoryMutation = useMutation({
    mutationFn: createDirectory,
    onSuccess: () => {
      void refetch();
    },
  });

  const uploadMutation = useMutation({
    mutationFn: async (files: File[]) => {
      const uploaded = [];
      for (const file of files) {
        setUploadStatus(`正在上传：${file.name}`);
        uploaded.push(await uploadFile(currentPath, file));
      }
      return uploaded;
    },
    onSuccess: (result) => {
      setUploadStatus(`已上传 ${result.length} 个文件`);
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

  useEffect(() => {
    window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, viewMode);
  }, [viewMode]);

  useEffect(() => {
    setSelectedById((current) => {
      const next: SelectedFileMap = {};
      rows.forEach((file) => {
        const key = getSelectionKey(file);
        if (current[key]) {
          next[key] = file;
        }
      });
      return next;
    });
  }, [rows]);

  useEffect(() => {
    setSelectedById({});
    setContextMenu(null);
    setDetailFileId(null);
    setDetail(null);
    setDetailError(null);
  }, [currentPath, search]);

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
    setCurrentPath(path);
    setPage(1);
  }

  function openDirectory(file: FileItem) {
    if (!file.directory) {
      return;
    }
    handlePathChange(getLogicalPath(file));
  }

  function openFile(file: FileItem) {
    setContextMenu(null);
    if (file.directory) {
      openDirectory(file);
      return;
    }
    if (file.contentType?.startsWith('image/')) {
      setDetailFileId(file.id);
    } else {
      setPreviewFile(file);
    }
  }

  function selectFile(file: FileItem, index: number, event?: React.MouseEvent<HTMLElement>) {
    const ctrlOrMeta = Boolean(event?.ctrlKey || event?.metaKey);
    const shift = Boolean(event?.shiftKey);

    setDetailFileId(file.id);

    if (shift && !ctrlOrMeta && selectedCount > 0) {
      const begin = Math.min(lastSelectedIndexRef.current, index);
      const end = Math.max(lastSelectedIndexRef.current, index);
      const next: SelectedFileMap = {};
      rows.slice(begin, end + 1).forEach((item) => {
        next[getSelectionKey(item)] = item;
      });
      setSelectedById(next);
      return;
    }

    lastSelectedIndexRef.current = index;

    if (ctrlOrMeta) {
      setSelectedById((current) => {
        const next = { ...current };
        const key = getSelectionKey(file);
        if (next[key]) {
          delete next[key];
          // If we deselected the item that was in detail rail, close it or switch to another
          if (detailFileId === file.id) {
            const remainingKeys = Object.keys(next);
            setDetailFileId(remainingKeys.length > 0 ? next[remainingKeys[0]].id : null);
          }
        } else {
          next[key] = file;
        }
        return next;
      });
      return;
    }

    setSelectedById({ [getSelectionKey(file)]: file });
  }

  function openFileContextMenu(file: FileItem, index: number, event: React.MouseEvent<HTMLElement>) {
    event.preventDefault();
    event.stopPropagation();
    setDetailFileId(file.id);
    if (!selectedById[getSelectionKey(file)]) {
      lastSelectedIndexRef.current = index;
      setSelectedById({ [getSelectionKey(file)]: file });
    }
    setContextMenu({ mouseX: event.clientX + 2, mouseY: event.clientY - 6, file });
  }

  function openEmptyContextMenu(event: React.MouseEvent<HTMLElement>) {
    event.preventDefault();
    setContextMenu({ mouseX: event.clientX + 2, mouseY: event.clientY - 6 });
  }

  function openDetail(file: FileItem) {
    setDetailFileId(file.id);
    setContextMenu(null);
  }

  function toggleFavorite(file: FileItem) {
    favoriteMutation.mutate({
      fileId: file.id,
      favorite: !favoriteIds.has(file.id),
    });
    setContextMenu(null);
  }

  function shareFile(file: FileItem) {
    shareMutation.mutate(file.id);
    setContextMenu(null);
  }

  function deleteFiles(files: FileItem[]) {
    if (files.length === 0) {
      return;
    }
    setContextMenu(null);
    if (window.confirm(`确认删除 ${files.length} 个项目？`)) {
      deleteMutation.mutate(files.map((file) => file.id));
    }
  }

  function downloadFile(file: FileItem) {
    if (file.directory) {
      return;
    }
    setContextMenu(null);
    downloadMutation.mutate(file);
  }

  function createFolder() {
    const nextName = window.prompt('请输入新文件夹名称', '新建文件夹');
    if (nextName && nextName.trim()) {
      createDirectoryMutation.mutate(joinDirectoryPath(currentPath, nextName.trim()));
    }
    setContextMenu(null);
  }

  function handleSelectAll() {
    if (allSelected) {
      setSelectedById({});
      return;
    }
    const next: SelectedFileMap = {};
    rows.forEach((file) => {
      next[getSelectionKey(file)] = file;
    });
    setSelectedById(next);
  }

  const activeMenuFile = contextMenu?.file;

  return (
    <DashboardLayout title="文件 Files">
      <MuiThemeProvider theme={muiTheme}>
        <input
          ref={fileInputRef}
          type="file"
          multiple
          className="hidden"
          onChange={(event) => {
            const files = Array.from(event.target.files ?? []);
            if (files.length > 0) {
              uploadMutation.mutate(files);
            }
            event.target.value = '';
          }}
        />

        <Stack spacing={2}>
          <FilesTopBar
            currentPath={currentPath}
            onPathChange={handlePathChange}
            search={search}
            onSearchChange={(value) => {
              setSearch(value);
              setPage(1);
            }}
            onRefresh={() => void refetch()}
            onUploadClick={() => fileInputRef.current?.click()}
            onCreateFolderClick={createFolder}
            viewMode={viewMode}
            onViewModeChange={setViewMode}
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
          />

          <Box
            sx={{
              display: 'flex',
              alignItems: 'start',
              gap: detailFileId != null ? 2 : 0,
              transition: 'gap 300ms cubic-bezier(0.4, 0, 0.2, 1)',
              animation: 'filesWorkspaceEnter 240ms ease-out',
              '@keyframes filesWorkspaceEnter': {
                from: { opacity: 0, transform: 'translateY(6px)' },
                to: { opacity: 1, transform: 'translateY(0)' },
              },
            }}
          >
            <Stack spacing={2} sx={{ flex: 1, minWidth: 0 }}>
              <FilesExplorerSurface
                isLoading={isLoading}
                isError={isError}
                rows={rows}
                viewMode={viewMode}
                selectedById={selectedById}
                favoriteIds={favoriteIds}
                allSelected={allSelected}
                selectedCount={selectedCount}
                onSelectFile={selectFile}
                onSelectAll={handleSelectAll}
                onOpenFile={openFile}
                onContextMenu={openFileContextMenu}
                onEmptyContextMenu={openEmptyContextMenu}
                getLogicalPath={getLogicalPath}
                getSelectionKey={getSelectionKey}
              />

              <Paper elevation={0} sx={{ p: 1.5, border: '1px solid', borderColor: 'divider' }}>
                <Stack direction="row" alignItems="center" justifyContent="space-between">
                  <Stack spacing={0.25}>
                    <Typography variant="body2" color="text.secondary">
                      共 {data?.pagination.total_items ?? 0} 条记录
                    </Typography>
                    <Typography variant="caption" color="text.disabled">
                      当前路径 {currentPath}，每页 {FILES_PAGE_SIZE} 项
                    </Typography>
                  </Stack>
                  <Stack direction="row" spacing={1}>
                    <Button size="small" variant="outlined" disabled={page <= 1} onClick={() => setPage((value) => value - 1)}>
                      上一页
                    </Button>
                    <Button size="small" variant="contained" disableElevation>
                      {page}
                    </Button>
                    <Button
                      size="small"
                      variant="outlined"
                      disabled={page >= (data?.pagination.total_pages ?? 1)}
                      onClick={() => setPage((value) => value + 1)}
                    >
                      下一页
                    </Button>
                  </Stack>
                </Stack>
              </Paper>
            </Stack>

            <Box
              sx={{
                width: {
                  xs: detailFileId != null ? '100%' : 0,
                  md: detailFileId != null ? '320px' : 0,
                  xl: detailFileId != null ? '340px' : 0,
                },
                flexShrink: 0,
                transition: 'width 300ms cubic-bezier(0.4, 0, 0.2, 1)',
                overflow: 'hidden',
                position: { xs: 'fixed', md: 'sticky' },
                top: { xs: 'auto', md: 24 },
                bottom: { xs: 0, md: 'auto' },
                right: { xs: 0, md: 'auto' },
                zIndex: { xs: 1200, md: 'auto' },
                bgcolor: 'background.default',
                visibility: detailFileId != null ? 'visible' : 'hidden',
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
          onClose={() => setContextMenu(null)}
          anchorReference="anchorPosition"
          anchorPosition={contextMenu ? { top: contextMenu.mouseY, left: contextMenu.mouseX } : undefined}
        >
          {activeMenuFile ? (
            <Box>
              <Box sx={{ px: 2, pt: 1.25, pb: 0.75, maxWidth: 320 }}>
                <Typography variant="body2" fontWeight={700} noWrap title={activeMenuFile.filename}>
                  {activeMenuFile.filename}
                </Typography>
                <Typography variant="caption" color="text.secondary" noWrap title={getLogicalPath(activeMenuFile)}>
                  {activeMenuFile.directory ? '文件夹' : '文件'} · {getLogicalPath(activeMenuFile)}
                </Typography>
              </Box>
              <Divider sx={{ my: 0.5 }} />
              <MenuItem onClick={() => openFile(activeMenuFile)}>
                <OpenInFull fontSize="small" sx={{ mr: 1.5 }} />
                打开
              </MenuItem>
              {!activeMenuFile.directory ? (
                <MenuItem
                  onClick={() => {
                    setContextMenu(null);
                    setPreviewFile(activeMenuFile);
                  }}
                >
                  <Visibility fontSize="small" sx={{ mr: 1.5 }} />
                  预览
                </MenuItem>
              ) : null}
              {!activeMenuFile.directory ? (
                <MenuItem onClick={() => downloadFile(activeMenuFile)}>
                  <Download fontSize="small" sx={{ mr: 1.5 }} />
                  下载
                </MenuItem>
              ) : null}
              <Divider sx={{ my: 0.5 }} />
              {!activeMenuFile.directory ? (
                <MenuItem onClick={() => shareFile(activeMenuFile)}>
                  <Share fontSize="small" sx={{ mr: 1.5 }} />
                  分享
                </MenuItem>
              ) : null}
              <MenuItem onClick={() => toggleFavorite(activeMenuFile)}>
                {favoriteIds.has(activeMenuFile.id) ? (
                  <Star fontSize="small" sx={{ mr: 1.5 }} />
                ) : (
                  <StarBorder fontSize="small" sx={{ mr: 1.5 }} />
                )}
                {favoriteIds.has(activeMenuFile.id) ? '取消收藏' : '收藏'}
              </MenuItem>
              <MenuItem onClick={() => openDetail(activeMenuFile)}>
                <InfoOutlined fontSize="small" sx={{ mr: 1.5 }} />
                详情
              </MenuItem>
              <Divider sx={{ my: 0.5 }} />
              <MenuItem
                sx={{ color: 'error.main' }}
                onClick={() =>
                  deleteFiles(selectedById[getSelectionKey(activeMenuFile)] ? selectedFiles : [activeMenuFile])
                }
              >
                <DeleteOutline fontSize="small" sx={{ mr: 1.5 }} color="inherit" />
                删除
              </MenuItem>
            </Box>
          ) : (
            <Box>
              <Box sx={{ px: 2, pt: 1.25, pb: 0.75 }}>
                <Typography variant="body2" fontWeight={700}>
                  当前目录
                </Typography>
                <Typography variant="caption" color="text.secondary" noWrap title={currentPath}>
                  {currentPath}
                </Typography>
              </Box>
              <Divider />
              <MenuItem
                onClick={() => {
                  setContextMenu(null);
                  fileInputRef.current?.click();
                }}
              >
                <UploadFile fontSize="small" sx={{ mr: 1.5 }} />
                上传文件
              </MenuItem>
              <MenuItem onClick={createFolder}>
                <CreateNewFolder fontSize="small" sx={{ mr: 1.5 }} />
                新建文件夹
              </MenuItem>
            </Box>
          )}
        </Menu>

        <FilesPreviewDialog file={previewFile} onClose={() => setPreviewFile(null)} />
      </MuiThemeProvider>
    </DashboardLayout>
  );
};

export default Files;
