import React, { useEffect, useRef } from 'react';
import {
  Box,
  Button,
  ButtonGroup,
  Chip,
  IconButton,
  InputAdornment,
  Menu,
  MenuItem,
  Paper,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  ArrowBack,
  ArrowDownward,
  ArrowUpward,
  CalendarToday,
  ChevronRight,
  Clear,
  CreateNewFolder,
  DeleteOutline,
  Download,
  GridView,
  InfoOutlined,
  Label,
  OpenInFull,
  Refresh,
  Search,
  Share,
  Sort,
  SortByAlpha,
  Update,
  UploadFile,
  ViewList,
} from '@mui/icons-material';
import type { FileItem } from '../../api/types';
import type { FolderDownloadMode } from '../../lib/folder-downloads';
import type { SortBy, SortOrder } from '../../pages/Files';

function parentDirectoryPath(path: string) {
  if (path === '/') {
    return '/';
  }
  const segments = path.split('/').filter(Boolean);
  segments.pop();
  return segments.length === 0 ? '/' : `/${segments.join('/')}`;
}

function pathSegments(path: string) {
  return path.split('/').filter(Boolean);
}

function isInputTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  const tagName = target.tagName;
  return target.isContentEditable || tagName === 'INPUT' || tagName === 'TEXTAREA' || tagName === 'SELECT';
}

export interface FilesTopBarProps {
  currentPath: string;
  onPathChange: (path: string) => void;
  rootLabel?: string;
  pathNavigationEnabled?: boolean;
  registerDropTarget?: (el: HTMLElement | null, path: string) => void;
  activeDropTarget?: string | null;
  search: string;
  onSearchChange: (search: string) => void;
  onRefresh: () => void;
  onUploadClick: () => void;
  onCreateFolderClick: () => void;
  viewMode: 'grid' | 'list';
  onViewModeChange: (mode: 'grid' | 'list') => void;
  page: number;
  totalPages: number;
  totalItems: number;
  selectedCount: number;
  selectedFiles: FileItem[];
  onClearSelection: () => void;
  onOpen: (file: FileItem) => void;
  onDetail: (file: FileItem) => void;
  onDownload: (file: FileItem, mode?: FolderDownloadMode) => void;
  onShare: (file: FileItem) => void;
  onDelete: (files: FileItem[]) => void;
  onUploadFolderClick?: () => void;
  onCreateFileClick?: () => void;
  onRename?: (file: FileItem) => void;
  onMove?: (file: FileItem) => void;
  onCopy?: (file: FileItem) => void;
  sortBy: SortBy;
  sortOrder: SortOrder;
  onSortChange: (sortBy: SortBy, sortOrder: SortOrder) => void;
}

export const FilesTopBar: React.FC<FilesTopBarProps> = ({
  currentPath,
  onPathChange,
  rootLabel = '根目录',
  pathNavigationEnabled = true,
  registerDropTarget,
  activeDropTarget,
  search,
  onSearchChange,
  onRefresh,
  onUploadClick,
  onCreateFolderClick,
  viewMode,
  onViewModeChange,
  page,
  totalPages,
  totalItems,
  selectedCount,
  selectedFiles,
  onClearSelection,
  onOpen,
  onDetail,
  onDownload,
  onShare,
  onDelete,
  sortBy,
  sortOrder,
  onSortChange,
}) => {
  const searchInputRef = useRef<HTMLInputElement | null>(null);
  const [sortAnchorEl, setSortAnchorEl] = React.useState<null | HTMLElement>(null);
  const [folderDownloadAnchorEl, setFolderDownloadAnchorEl] = React.useState<null | HTMLElement>(null);
  const openSortMenu = Boolean(sortAnchorEl);
  const openFolderDownloadMenu = Boolean(folderDownloadAnchorEl);
  const breadcrumbs = pathSegments(currentPath);
  const isSelected = selectedCount > 0;
  const singleFile = selectedCount === 1 ? selectedFiles[0] : null;

  useEffect(() => {
    function focusSearch(event: KeyboardEvent) {
      if (event.key !== '/' || event.ctrlKey || event.metaKey || event.altKey || isInputTarget(event.target)) {
        return;
      }
      event.preventDefault();
      searchInputRef.current?.focus();
    }

    window.addEventListener('keydown', focusSearch);
    return () => window.removeEventListener('keydown', focusSearch);
  }, []);

  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', overflow: 'hidden', borderRadius: 1 }}>
      <Box sx={{ px: 1.5, py: 1, borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'background.paper' }}>
        <Stack direction="row" spacing={1.5} alignItems="center">
          {isSelected ? (
            <Stack direction="row" spacing={1} alignItems="center" sx={{ flexGrow: 1 }}>
              <Tooltip title="清除选择">
                <IconButton size="small" onClick={onClearSelection} sx={{ mr: 0.5 }}>
                  <Clear fontSize="small" />
                </IconButton>
              </Tooltip>
              <Typography variant="body2" fontWeight={700} sx={{ color: 'primary.main', display: { xs: 'none', sm: 'block' } }}>
                已选中 {selectedCount} 个项目
              </Typography>
              <Box sx={{ flexGrow: 1 }} />
              <Stack direction="row" spacing={1}>
                {singleFile ? (
                  <>
                    <Button
                      size="small"
                      startIcon={<OpenInFull />}
                      onClick={() => onOpen(singleFile)}
                      sx={{ textTransform: 'none' }}
                    >
                      打开
                    </Button>
                    <Button
                      size="small"
                      startIcon={<InfoOutlined />}
                      onClick={() => onDetail(singleFile)}
                      sx={{ textTransform: 'none' }}
                    >
                      详情
                    </Button>
                    {singleFile.directory ? (
                      <>
                        <Button
                          size="small"
                          startIcon={<Download />}
                          onClick={(event) => setFolderDownloadAnchorEl(event.currentTarget)}
                          sx={{ textTransform: 'none' }}
                        >
                          下载
                        </Button>
                        <Menu
                          anchorEl={folderDownloadAnchorEl}
                          open={openFolderDownloadMenu}
                          onClose={() => setFolderDownloadAnchorEl(null)}
                        >
                          <MenuItem onClick={() => { setFolderDownloadAnchorEl(null); onDownload(singleFile, 'server-archive'); }}>
                            服务器端打包
                          </MenuItem>
                          <MenuItem onClick={() => { setFolderDownloadAnchorEl(null); onDownload(singleFile, 'browser-archive'); }}>
                            浏览器打包
                          </MenuItem>
                          <MenuItem onClick={() => { setFolderDownloadAnchorEl(null); onDownload(singleFile, 'individual-files'); }}>
                            逐一文件下载
                          </MenuItem>
                        </Menu>
                      </>
                    ) : (
                      <Button
                        size="small"
                        startIcon={<Download />}
                        onClick={() => onDownload(singleFile)}
                        sx={{ textTransform: 'none' }}
                      >
                        下载
                      </Button>
                    )}
                    <Button
                      size="small"
                      startIcon={<Share />}
                      onClick={() => onShare(singleFile)}
                      sx={{ textTransform: 'none' }}
                    >
                      分享
                    </Button>
                  </>
                ) : (
                  <Button
                    size="small"
                    color="error"
                    startIcon={<DeleteOutline />}
                    onClick={() => onDelete(selectedFiles)}
                    sx={{ textTransform: 'none' }}
                  >
                    批量删除
                  </Button>
                )}
                {selectedCount > 1 && (
                  <Button
                    size="small"
                    onClick={onClearSelection}
                    sx={{ textTransform: 'none' }}
                  >
                    取消
                  </Button>
                )}
              </Stack>
            </Stack>
          ) : (
            <>
              <ButtonGroup size="small" variant="outlined" sx={{ flexShrink: 0 }}>
                <Tooltip title="返回上级">
                  <span>
                    <Button
                      disabled={!pathNavigationEnabled || currentPath === '/'}
                      onClick={() => pathNavigationEnabled && onPathChange(parentDirectoryPath(currentPath))}
                      sx={{ px: 1 }}
                    >
                      <ArrowBack fontSize="small" />
                    </Button>
                  </span>
                </Tooltip>
                <Tooltip title="刷新">
                  <Button onClick={onRefresh} sx={{ px: 1 }}>
                    <Refresh fontSize="small" />
                  </Button>
                </Tooltip>
              </ButtonGroup>

              <Box sx={{ width: '1px', height: 20, bgcolor: 'divider', flexShrink: 0 }} />

              <Stack
                direction="row"
                alignItems="center"
                sx={{
                  flexGrow: 1,
                  overflow: 'hidden',
                  bgcolor: 'action.hover',
                  borderRadius: 0.5,
                  px: 1,
                  height: 32,
                  border: '1px solid',
                  borderColor: 'divider',
                }}
              >
                <Button
                  ref={(el: HTMLButtonElement | null) => registerDropTarget?.(el, '/')}
                  size="small"
                  variant="text"
                  sx={{
                    minWidth: 'auto',
                    px: 0.5,
                    borderRadius: 0.5,
                    color: activeDropTarget === '/'
                      ? 'primary.contrastText'
                      : (currentPath === '/' || !pathNavigationEnabled ? 'primary.main' : 'text.secondary'),
                    fontWeight: currentPath === '/' || activeDropTarget === '/' || !pathNavigationEnabled ? 600 : 400,
                    textTransform: 'none',
                    bgcolor: activeDropTarget === '/' ? 'primary.main' : undefined,
                    boxShadow: activeDropTarget === '/' ? 2 : undefined,
                    '&:hover': activeDropTarget === '/'
                      ? { bgcolor: 'primary.dark' }
                      : undefined,
                  }}
                  onClick={() => pathNavigationEnabled && onPathChange('/')}
                  disabled={!pathNavigationEnabled}
                >
                  {rootLabel}
                </Button>
                {pathNavigationEnabled && breadcrumbs.map((segment, index) => {
                  const targetPath = `/${breadcrumbs.slice(0, index + 1).join('/')}`;
                  return (
                    <React.Fragment key={targetPath}>
                      <ChevronRight sx={{ fontSize: 16, color: 'text.disabled', mx: -0.25 }} />
                      <Button
                        ref={(el: HTMLButtonElement | null) => registerDropTarget?.(el, targetPath)}
                        size="small"
                        variant="text"
                        sx={{
                          minWidth: 'auto',
                          px: 0.5,
                          borderRadius: 0.5,
                          color: activeDropTarget === targetPath
                            ? 'primary.contrastText'
                            : (targetPath === currentPath ? 'primary.main' : 'text.secondary'),
                          fontWeight: targetPath === currentPath || activeDropTarget === targetPath ? 600 : 400,
                          textTransform: 'none',
                          whiteSpace: 'nowrap',
                          bgcolor: activeDropTarget === targetPath ? 'primary.main' : undefined,
                          boxShadow: activeDropTarget === targetPath ? 2 : undefined,
                          '&:hover': activeDropTarget === targetPath
                            ? { bgcolor: 'primary.dark' }
                            : undefined,
                        }}
                        onClick={() => onPathChange(targetPath)}
                      >
                        {segment}
                      </Button>
                    </React.Fragment>
                  );
                })}
              </Stack>

              <TextField
                inputRef={searchInputRef}
                value={search}
                onChange={(event) => onSearchChange(event.target.value)}
                placeholder="搜索... (按 /)"
                size="small"
                variant="outlined"
                sx={{
                  width: { xs: 120, sm: 180, md: 240 },
                  '& .MuiOutlinedInput-root': { height: 32, fontSize: '0.875rem' },
                }}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Search fontSize="small" />
                    </InputAdornment>
                  ),
                }}
              />

              <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
                <Button
                  variant="contained"
                  size="small"
                  startIcon={<UploadFile />}
                  onClick={onUploadClick}
                  disableElevation
                  sx={{ height: 32 }}
                >
                  上传
                </Button>
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<CreateNewFolder />}
                  onClick={onCreateFolderClick}
                  sx={{ height: 32 }}
                >
                  新建
                </Button>
              </Stack>
            </>
          )}
        </Stack>
      </Box>

      <Box sx={{ px: 1.5, py: 0.5, bgcolor: 'action.hover', display: 'flex', alignItems: 'center', justifyContent: 'space-between', minHeight: 32 }}>
        <Stack direction="row" spacing={2} alignItems="center">
          <Typography variant="caption" color="text.secondary" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <Box component="span" sx={{ fontWeight: 600, color: 'text.primary' }}>{totalItems}</Box> 个项目
          </Typography>

          <Box sx={{ width: '1px', height: 12, bgcolor: 'divider' }} />

          <Typography variant="caption" color="text.secondary">
            第 <Box component="span" sx={{ color: 'text.primary' }}>{page}</Box> / {Math.max(totalPages, 1)} 页
          </Typography>

          <Box sx={{ width: '1px', height: 12, bgcolor: 'divider' }} />

          <Stack direction="row" spacing={0.5} alignItems="center">
            <Button
              size="small"
              variant="text"
              startIcon={<Sort sx={{ fontSize: 16 }} />}
              onClick={(e) => setSortAnchorEl(e.currentTarget)}
              sx={{
                height: 24,
                fontSize: '0.75rem',
                textTransform: 'none',
                color: 'text.secondary',
                px: 1,
                '&:hover': { bgcolor: 'background.paper' },
              }}
            >
              {sortBy === 'name' && '名称'}
              {sortBy === 'tags' && '标签'}
              {sortBy === 'createdAt' && '创建时间'}
              {sortBy === 'updatedAt' && '修改时间'}
            </Button>

            <Tooltip title={sortOrder === 'asc' ? '升序' : '降序'}>
              <IconButton
                size="small"
                onClick={() => onSortChange(sortBy, sortOrder === 'asc' ? 'desc' : 'asc')}
                sx={{ width: 24, height: 24, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}
              >
                {sortOrder === 'asc' ? <ArrowUpward sx={{ fontSize: 14 }} /> : <ArrowDownward sx={{ fontSize: 14 }} />}
              </IconButton>
            </Tooltip>

            <Menu
              anchorEl={sortAnchorEl}
              open={openSortMenu}
              onClose={() => setSortAnchorEl(null)}
              PaperProps={{
                sx: {
                  mt: 0.5,
                  minWidth: 160,
                  boxShadow: '0 4px 20px rgba(0,0,0,0.1)',
                  border: '1px solid',
                  borderColor: 'divider',
                },
              }}
            >
              <MenuItem
                selected={sortBy === 'name'}
                onClick={() => {
                  onSortChange('name', sortOrder);
                  setSortAnchorEl(null);
                }}
                sx={{ fontSize: '0.875rem' }}
              >
                <SortByAlpha fontSize="small" sx={{ mr: 1, opacity: 0.7 }} />
                名称
              </MenuItem>
              <MenuItem
                selected={sortBy === 'tags'}
                onClick={() => {
                  onSortChange('tags', sortOrder);
                  setSortAnchorEl(null);
                }}
                sx={{ fontSize: '0.875rem' }}
              >
                <Label fontSize="small" sx={{ mr: 1, opacity: 0.7 }} />
                标签
              </MenuItem>
              <MenuItem
                selected={sortBy === 'createdAt'}
                onClick={() => {
                  onSortChange('createdAt', sortOrder);
                  setSortAnchorEl(null);
                }}
                sx={{ fontSize: '0.875rem' }}
              >
                <CalendarToday fontSize="small" sx={{ mr: 1, opacity: 0.7 }} />
                创建时间
              </MenuItem>
              <MenuItem
                selected={sortBy === 'updatedAt'}
                onClick={() => {
                  onSortChange('updatedAt', sortOrder);
                  setSortAnchorEl(null);
                }}
                sx={{ fontSize: '0.875rem' }}
              >
                <Update fontSize="small" sx={{ mr: 1, opacity: 0.7 }} />
                修改时间
              </MenuItem>
            </Menu>
          </Stack>

          {search && (
            <>
              <Box sx={{ width: '1px', height: 12, bgcolor: 'divider' }} />
              <Chip
                size="small"
                variant="filled"
                label={`搜索: ${search}`}
                onDelete={() => onSearchChange('')}
                sx={{ height: 20, fontSize: '0.75rem', bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}
              />
            </>
          )}
        </Stack>

        <ToggleButtonGroup
          exclusive
          size="small"
          value={viewMode}
          onChange={(_, nextMode) => nextMode && onViewModeChange(nextMode as 'grid' | 'list')}
          sx={{ height: 24, '& .MuiToggleButton-root': { px: 1, border: 'none', '&.Mui-selected': { bgcolor: 'background.paper', boxShadow: 1 } } }}
        >
          <ToggleButton value="grid" title="网格视图">
            <GridView sx={{ fontSize: 16 }} />
          </ToggleButton>
          <ToggleButton value="list" title="列表视图">
            <ViewList sx={{ fontSize: 16 }} />
          </ToggleButton>
        </ToggleButtonGroup>
      </Box>
    </Paper>
  );
};
