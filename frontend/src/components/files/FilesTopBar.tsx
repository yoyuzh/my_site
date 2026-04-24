import React, { useEffect, useRef } from 'react';
import {
  Box,
  Button,
  ButtonGroup,
  Chip,
  IconButton,
  InputAdornment,
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
  ChevronRight,
  Clear,
  CreateNewFolder,
  DeleteOutline,
  Download,
  GridView,
  InfoOutlined,
  OpenInFull,
  Refresh,
  Search,
  Share,
  UploadFile,
  ViewList,
} from '@mui/icons-material';
import type { FileItem } from '../../api/types';

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
  // Selection props
  selectedCount: number;
  selectedFiles: FileItem[];
  onClearSelection: () => void;
  onOpen: (file: FileItem) => void;
  onDetail: (file: FileItem) => void;
  onDownload: (file: FileItem) => void;
  onShare: (file: FileItem) => void;
  onDelete: (files: FileItem[]) => void;
}

export const FilesTopBar: React.FC<FilesTopBarProps> = ({
  currentPath,
  onPathChange,
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
}) => {
  const searchInputRef = useRef<HTMLInputElement | null>(null);
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
      {/* 第一层：工具栏和路径区 */}
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
                    {!singleFile.directory && (
                      <>
                        <Button
                          size="small"
                          startIcon={<Download />}
                          onClick={() => onDownload(singleFile)}
                          sx={{ textTransform: 'none' }}
                        >
                          下载
                        </Button>
                        <Button
                          size="small"
                          startIcon={<Share />}
                          onClick={() => onShare(singleFile)}
                          sx={{ textTransform: 'none' }}
                        >
                          分享
                        </Button>
                      </>
                    )}
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
                      disabled={currentPath === '/'}
                      onClick={() => onPathChange(parentDirectoryPath(currentPath))}
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

              {/* 路径导航 */}
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
                  borderColor: 'divider'
                }}
              >
                <Button
                  size="small"
                  variant="text"
                  sx={{ 
                    minWidth: 'auto', 
                    px: 0.5, 
                    color: currentPath === '/' ? 'primary.main' : 'text.secondary',
                    fontWeight: currentPath === '/' ? 600 : 400,
                    textTransform: 'none'
                  }}
                  onClick={() => onPathChange('/')}
                >
                  根目录
                </Button>
                {breadcrumbs.map((segment, index) => {
                  const targetPath = `/${breadcrumbs.slice(0, index + 1).join('/')}`;
                  return (
                    <React.Fragment key={targetPath}>
                      <ChevronRight sx={{ fontSize: 16, color: 'text.disabled', mx: -0.25 }} />
                      <Button
                        size="small"
                        variant="text"
                        sx={{ 
                          minWidth: 'auto', 
                          px: 0.5, 
                          color: targetPath === currentPath ? 'primary.main' : 'text.secondary',
                          fontWeight: targetPath === currentPath ? 600 : 400,
                          textTransform: 'none',
                          whiteSpace: 'nowrap'
                        }}
                        onClick={() => onPathChange(targetPath)}
                      >
                        {segment}
                      </Button>
                    </React.Fragment>
                  );
                })}
              </Stack>

              {/* 搜索框 */}
              <TextField
                inputRef={searchInputRef}
                value={search}
                onChange={(event) => onSearchChange(event.target.value)}
                placeholder="搜索... (按 /)"
                size="small"
                variant="outlined"
                sx={{ 
                  width: { xs: 120, sm: 180, md: 240 },
                  '& .MuiOutlinedInput-root': { height: 32, fontSize: '0.875rem' }
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

      {/* 第二层：状态栏 */}
      <Box sx={{ px: 1.5, py: 0.5, bgcolor: 'action.hover', display: 'flex', alignItems: 'center', justifyContent: 'space-between', minHeight: 32 }}>
        <Stack direction="row" spacing={2} alignItems="center">
          <Typography variant="caption" color="text.secondary" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <Box component="span" sx={{ fontWeight: 600, color: 'text.primary' }}>{totalItems}</Box> 个项目
          </Typography>
          
          <Box sx={{ width: '1px', height: 12, bgcolor: 'divider' }} />
          
          <Typography variant="caption" color="text.secondary">
            第 <Box component="span" sx={{ color: 'text.primary' }}>{page}</Box> / {Math.max(totalPages, 1)} 页
          </Typography>

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
