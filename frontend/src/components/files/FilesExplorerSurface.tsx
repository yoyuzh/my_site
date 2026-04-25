import React, { useEffect, useRef } from 'react';
import {
  Box,
  Checkbox,
  IconButton,
  LinearProgress,
  Paper,
  Stack,
  Typography,
  alpha,
  useTheme,
  CircularProgress,
} from '@mui/material';
import { Folder, InsertDriveFile, MoreHoriz, RadioButtonUnchecked, CheckCircle } from '@mui/icons-material';
import type { FileItem, FileTag } from '../../api/types';
import { formatBytes, formatDateTime } from '../../lib/format';
import FileThumbnail from '../media/FileThumbnail';

function FileIcon({ file, selected }: { file: FileItem; selected: boolean }) {
  if (file.directory) {
    return <Folder sx={{ color: selected ? 'primary.main' : '#E9A23B' }} />;
  }
  return <InsertDriveFile sx={{ color: selected ? 'primary.main' : 'text.secondary' }} />;
}

function getTypeLabel(file: FileItem) {
  if (file.directory) {
    return '文件夹';
  }

  const ext = file.filename.includes('.') ? file.filename.split('.').pop() : '';
  return ext ? ext.toUpperCase() : '文件';
}

export interface FilesExplorerSurfaceProps {
  isLoading: boolean;
  isError: boolean;
  rows: FileItem[];
  viewMode: 'grid' | 'list';
  selectedById: Record<string, FileItem>;
  favoriteIds: Set<number>;
  allSelected: boolean;
  selectedCount: number;
  showSelectionControls: boolean;
  onSelectFile: (file: FileItem, index: number, event?: React.MouseEvent<HTMLElement>) => void;
  onToggleSelection: (file: FileItem, index: number) => void;
  onSelectAll: () => void;
  onOpenFile: (file: FileItem) => void;
  onContextMenu: (file: FileItem, index: number, event: React.MouseEvent<HTMLElement>) => void;
  onEmptyContextMenu: (event: React.MouseEvent<HTMLElement>) => void;
  onEmptyClick?: () => void;
  getLogicalPath: (file: Pick<FileItem, 'directory' | 'filename' | 'path'>) => string;
  getSelectionKey: (file: Pick<FileItem, 'id'>) => string;
  folderTagsMap?: Record<number, FileTag[]>;
  sortBy?: string;
  hasNextPage?: boolean;
  isFetchingNextPage?: boolean;
  onLoadMore?: () => void;
}

export const FilesExplorerSurface: React.FC<FilesExplorerSurfaceProps> = ({
  isLoading,
  isError,
  rows,
  viewMode,
  selectedById,
  favoriteIds,
  allSelected,
  selectedCount,
  showSelectionControls,
  onSelectFile,
  onToggleSelection,
  onSelectAll,
  onOpenFile,
  onContextMenu,
  onEmptyContextMenu,
  onEmptyClick,
  getLogicalPath,
  getSelectionKey,
  folderTagsMap = {},
  sortBy,
  hasNextPage,
  isFetchingNextPage,
  onLoadMore,
}) => {
  const theme = useTheme();
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const folderRows = rows.filter((file) => file.directory);
  const fileRows = rows.filter((file) => !file.directory);

  function renderFolderTags(file: FileItem, mode: 'grid' | 'list') {
    const tags = folderTagsMap[file.id] ?? [];
    if (!file.directory || tags.length === 0) {
      return null;
    }

    return (
      <Stack
        direction="row"
        spacing={0.75}
        useFlexGap
        flexWrap="wrap"
        justifyContent={mode === 'grid' ? 'flex-end' : 'flex-start'}
        sx={{
          minWidth: 0,
          maxWidth: mode === 'grid' ? '50%' : '100%',
        }}
      >
        {tags.map((tag) => (
          <Box
            key={tag.id}
            component="span"
            sx={{
              px: 1,
              py: 0.375,
              borderRadius: 999,
              bgcolor: tag.color,
              color: '#fff',
              fontSize: 11,
              fontWeight: 700,
              lineHeight: 1,
              whiteSpace: 'nowrap',
              boxShadow: `inset 0 0 0 1px ${alpha('#000000', 0.08)}`,
            }}
            title={tag.name}
          >
            {tag.name}
          </Box>
        ))}
      </Stack>
    );
  }

  useEffect(() => {
    if (!hasNextPage || isFetchingNextPage || !onLoadMore) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          onLoadMore();
        }
      },
      { threshold: 0.1 },
    );

    if (sentinelRef.current) {
      observer.observe(sentinelRef.current);
    }

    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, onLoadMore]);

  function renderFolderCard(file: FileItem, index: number) {
    const selected = Boolean(selectedById[getSelectionKey(file)]);
    return (
      <Paper
        key={`${file.id}-${index}`}
        elevation={0}
        onClick={(event) => {
          event.stopPropagation();
          onSelectFile(file, index, event);
        }}
        onDoubleClick={() => onOpenFile(file)}
        onContextMenu={(event) => {
          event.stopPropagation();
          onContextMenu(file, index, event);
        }}
        sx={{
          p: 1,
          cursor: 'default',
          border: '1px solid',
          borderColor: selected ? 'primary.main' : 'divider',
          bgcolor: selected ? alpha(theme.palette.primary.main, 0.08) : 'background.paper',
          boxShadow: selected ? `0 0 12px ${alpha(theme.palette.primary.main, 0.25)}` : 'none',
          transition: 'all 180ms ease',
          animation: 'fadeIn 300ms ease-out forwards',
          animationDelay: `${Math.min(index * 20, 300)}ms`,
          opacity: 0,
          '&:hover': {
            bgcolor: selected ? alpha(theme.palette.primary.main, 0.12) : 'action.hover',
            boxShadow: selected
              ? `0 8px 20px ${alpha(theme.palette.primary.main, 0.15)}, 0 0 12px ${alpha(theme.palette.primary.main, 0.25)}`
              : (theme.palette.mode === 'dark' ? 'none' : '0 8px 20px rgba(0,0,0,0.06)'),
            transform: 'translateY(-2px)',
            borderColor: selected ? 'primary.main' : alpha(theme.palette.text.primary, 0.15),
            '& .grid-icon': { display: 'none' },
            '& .grid-checkbox': { display: 'flex' },
          },
          '@keyframes fadeIn': {
            from: { opacity: 0, transform: 'translateY(4px)' },
            to: { opacity: 1, transform: 'translateY(0)' },
          },
        }}
      >
        <Stack direction="row" alignItems="center" spacing={1}>
          <Box sx={{ width: 24, height: 24, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            {selected ? (
              <Checkbox
                checked={true}
                size="small"
                onClick={(event) => {
                  event.stopPropagation();
                  onToggleSelection(file, index);
                }}
                sx={{ p: 0, color: 'primary.main', '&.Mui-checked': { color: 'primary.main' } }}
                icon={<RadioButtonUnchecked fontSize="small" />}
                checkedIcon={<CheckCircle fontSize="small" />}
              />
            ) : (
              <>
                <Box className="grid-icon" sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Folder sx={{ color: '#E9A23B', fontSize: 24 }} />
                </Box>
                <Box className="grid-checkbox" sx={{ display: 'none', alignItems: 'center', justifyContent: 'center' }}>
                  <Checkbox
                    checked={false}
                    size="small"
                    onClick={(event) => {
                      event.stopPropagation();
                      onToggleSelection(file, index);
                    }}
                    sx={{ p: 0 }}
                    icon={<RadioButtonUnchecked fontSize="small" />}
                    checkedIcon={<CheckCircle fontSize="small" />}
                  />
                </Box>
              </>
            )}
          </Box>
          <Typography noWrap fontWeight={600} sx={{ flex: 1, fontSize: '0.9rem' }} title={file.filename}>
            {favoriteIds.has(file.id) ? '★ ' : ''}
            {file.filename}
          </Typography>
          {renderFolderTags(file, 'grid')}
          <IconButton
            size="small"
            onClick={(event) => {
              event.stopPropagation();
              onContextMenu(file, index, event);
            }}
          >
            <MoreHoriz fontSize="small" />
          </IconButton>
        </Stack>
      </Paper>
    );
  }

  function renderFileCard(file: FileItem, index: number) {
    const selected = Boolean(selectedById[getSelectionKey(file)]);
    return (
      <Paper
        key={`${file.id}-${index}`}
        elevation={0}
        onClick={(event) => {
          event.stopPropagation();
          onSelectFile(file, index, event);
        }}
        onDoubleClick={() => onOpenFile(file)}
        onContextMenu={(event) => {
          event.stopPropagation();
          onContextMenu(file, index, event);
        }}
        sx={{
          p: 1,
          alignSelf: 'start',
          cursor: 'default',
          border: '1px solid',
          borderColor: selected ? 'primary.main' : 'divider',
          bgcolor: selected ? alpha(theme.palette.primary.main, 0.08) : 'background.paper',
          boxShadow: selected ? `0 0 12px ${alpha(theme.palette.primary.main, 0.25)}` : 'none',
          transition: 'all 180ms ease',
          animation: 'fadeIn 300ms ease-out forwards',
          animationDelay: `${Math.min(index * 20, 300)}ms`,
          opacity: 0,
          '&:hover': {
            bgcolor: selected ? alpha(theme.palette.primary.main, 0.12) : 'action.hover',
            boxShadow: selected
              ? `0 8px 20px ${alpha(theme.palette.primary.main, 0.15)}, 0 0 12px ${alpha(theme.palette.primary.main, 0.25)}`
              : (theme.palette.mode === 'dark' ? 'none' : '0 8px 20px rgba(0,0,0,0.06)'),
            transform: 'translateY(-2px)',
            borderColor: selected ? 'primary.main' : alpha(theme.palette.text.primary, 0.15),
            '& .grid-icon': { display: 'none' },
            '& .grid-checkbox': { display: 'flex' },
          },
          '@keyframes fadeIn': {
            from: { opacity: 0, transform: 'translateY(4px)' },
            to: { opacity: 1, transform: 'translateY(0)' },
          },
        }}
      >
        <Stack spacing={1}>
          <Stack direction="row" alignItems="center" spacing={1}>
            <Box sx={{ width: 24, height: 24, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              {selected ? (
                <Checkbox
                  checked={true}
                  size="small"
                  onClick={(event) => {
                    event.stopPropagation();
                    onToggleSelection(file, index);
                  }}
                  sx={{ p: 0, color: 'primary.main', '&.Mui-checked': { color: 'primary.main' } }}
                  icon={<RadioButtonUnchecked fontSize="small" />}
                  checkedIcon={<CheckCircle fontSize="small" />}
                />
              ) : (
                <>
                  <Box className="grid-icon" sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <InsertDriveFile sx={{ color: 'text.secondary', fontSize: 20 }} />
                  </Box>
                  <Box className="grid-checkbox" sx={{ display: 'none', alignItems: 'center', justifyContent: 'center' }}>
                    <Checkbox
                      checked={false}
                      size="small"
                      onClick={(event) => {
                        event.stopPropagation();
                        onToggleSelection(file, index);
                      }}
                      sx={{ p: 0 }}
                      icon={<RadioButtonUnchecked fontSize="small" />}
                      checkedIcon={<CheckCircle fontSize="small" />}
                    />
                  </Box>
                </>
              )}
            </Box>
            <Typography noWrap fontWeight={600} sx={{ flex: 1, fontSize: '0.9rem' }} title={file.filename}>
              {favoriteIds.has(file.id) ? '★ ' : ''}
              {file.filename}
            </Typography>
            <IconButton
              size="small"
              onClick={(event) => {
                event.stopPropagation();
                onContextMenu(file, index, event);
              }}
            >
              <MoreHoriz fontSize="small" />
            </IconButton>
          </Stack>
          <Box
            sx={{
              height: 112,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'action.hover',
              borderRadius: 1,
              overflow: 'hidden',
              position: 'relative',
              p: 1,
              border: '1px solid',
              borderColor: alpha(theme.palette.divider, 0.05),
            }}
          >
            <Box sx={{ transform: 'scale(3.2)', transformOrigin: 'center center' }}>
              <FileThumbnail file={file} />
            </Box>
          </Box>
        </Stack>
      </Paper>
    );
  }

  function renderFileRow(file: FileItem, index: number) {
    const selected = Boolean(selectedById[getSelectionKey(file)]);
    return (
      <Box
        key={`${file.id}-${index}`}
        role="row"
        onClick={(event) => {
          event.stopPropagation();
          onSelectFile(file, index, event);
        }}
        onDoubleClick={() => onOpenFile(file)}
        onContextMenu={(event) => {
          event.stopPropagation();
          onContextMenu(file, index, event);
        }}
        sx={{
          display: 'grid',
          gridTemplateColumns: showSelectionControls
            ? '44px minmax(220px,1fr) 100px 100px 170px 58px'
            : 'minmax(220px,1fr) 100px 100px 170px 58px',
          alignItems: 'center',
          minHeight: 54,
          px: 1,
          borderBottom: '1px solid',
          borderColor: 'divider',
          bgcolor: selected ? alpha(theme.palette.primary.main, 0.08) : 'transparent',
          transition: 'background-color 180ms ease',
          animation: 'fadeIn 300ms ease-out forwards',
          animationDelay: `${Math.min(index * 20, 300)}ms`,
          opacity: 0,
          cursor: 'pointer',
          '&:hover': {
            bgcolor: selected ? alpha(theme.palette.primary.main, 0.12) : 'action.hover',
          },
          '@keyframes fadeIn': {
            from: { opacity: 0, transform: 'translateY(4px)' },
            to: { opacity: 1, transform: 'translateY(0)' },
          },
        }}
      >
        {showSelectionControls && (
          <Checkbox
            checked={selected}
            size="small"
            onClick={(event) => {
              event.stopPropagation();
              onToggleSelection(file, index);
            }}
          />
        )}
        <Stack direction="row" alignItems="center" spacing={1.5} sx={{ minWidth: 0 }}>
          <FileIcon file={file} selected={selected} />
          <Box sx={{ minWidth: 0 }}>
            <Stack direction="row" alignItems="center" spacing={1} useFlexGap flexWrap="wrap" sx={{ minWidth: 0 }}>
              <Typography noWrap fontWeight={600} title={file.filename} sx={{ maxWidth: file.directory ? '100%' : undefined }}>
                {favoriteIds.has(file.id) ? '★ ' : ''}
                {file.filename}
              </Typography>
              {renderFolderTags(file, 'list')}
            </Stack>
          </Box>
        </Stack>
        <Box>
          <Box
            component="span"
            sx={{
              px: 0.75,
              py: 0.25,
              borderRadius: 0.5,
              bgcolor: selected ? alpha(theme.palette.primary.main, 0.12) : alpha(theme.palette.action.active, 0.08),
              color: 'text.secondary',
              fontSize: 10,
              fontWeight: 800,
              lineHeight: 1,
              textTransform: 'uppercase',
              letterSpacing: '0.02em',
            }}
          >
            {getTypeLabel(file)}
          </Box>
        </Box>
        <Typography variant="body2" color="text.secondary">
          {file.directory ? '-' : formatBytes(file.size)}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {sortBy === 'updatedAt' ? formatDateTime(file.updatedAt || file.createdAt) : formatDateTime(file.createdAt)}
        </Typography>
        <IconButton
          size="small"
          onClick={(event) => {
            event.stopPropagation();
            onContextMenu(file, index, event);
          }}
        >
          <MoreHoriz fontSize="small" />
        </IconButton>
      </Box>
    );
  }

  return (
    <Paper
      elevation={0}
      onContextMenu={onEmptyContextMenu}
      onClick={onEmptyClick}
      sx={{
        flex: 1,
        minHeight: 0,
        height: '100%',
        overflow: 'auto',
        border: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.paper',
        boxShadow: theme.palette.mode === 'dark' ? 'none' : '0 18px 40px rgba(15, 23, 42, 0.04)',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      {isLoading && rows.length === 0 ? (
        <LinearProgress />
      ) : isError ? (
        <Stack alignItems="center" justifyContent="center" sx={{ minHeight: 360 }}>
          <Typography color="error">文件列表加载失败</Typography>
        </Stack>
      ) : rows.length === 0 ? (
        <Stack alignItems="center" justifyContent="center" spacing={1} sx={{ minHeight: 360 }}>
          <Folder color="disabled" sx={{ fontSize: 56 }} />
          <Typography color="text.secondary">当前目录暂无文件</Typography>
        </Stack>
      ) : (
        <Box sx={{ flex: 1, minHeight: 0 }}>
          {viewMode === 'grid' ? (
            <Box sx={{ p: 2.5 }}>
              {folderRows.length > 0 && (
                <>
                  <Typography variant="h6" sx={{ mb: 1.5, fontWeight: 700, px: 0.5 }}>
                    文件夹
                  </Typography>
                  <Box
                    sx={{
                      display: 'grid',
                      gap: 1.5,
                      gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
                      mb: 4,
                    }}
                  >
                    {folderRows.map(renderFolderCard)}
                  </Box>
                </>
              )}
              {fileRows.length > 0 && (
                <>
                  <Typography variant="h6" sx={{ mb: 1.5, fontWeight: 700, px: 0.5 }}>
                    文件
                  </Typography>
                  <Box
                    sx={{
                      display: 'grid',
                      gap: 2,
                      gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
                    }}
                  >
                    {fileRows.map(renderFileCard)}
                  </Box>
                </>
              )}
            </Box>
          ) : (
            <Box sx={{ minWidth: 760, overflowX: 'auto' }}>
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: showSelectionControls
                    ? '44px minmax(220px,1fr) 100px 100px 170px 58px'
                    : 'minmax(220px,1fr) 100px 100px 170px 58px',
                  alignItems: 'center',
                  minHeight: 42,
                  px: 1,
                  borderBottom: '1px solid',
                  borderColor: 'divider',
                  bgcolor: 'action.hover',
                }}
              >
                {showSelectionControls && (
                  <Checkbox
                    size="small"
                    checked={allSelected}
                    indeterminate={selectedCount > 0 && !allSelected}
                    onChange={onSelectAll}
                  />
                )}
                <Typography variant="caption" fontWeight={700} color="text.secondary">
                  名称
                </Typography>
                <Typography variant="caption" fontWeight={700} color="text.secondary">
                  类型
                </Typography>
                <Typography variant="caption" fontWeight={700} color="text.secondary">
                  大小
                </Typography>
                <Typography variant="caption" fontWeight={700} color="text.secondary">
                  创建时间
                </Typography>
                <Typography variant="caption" fontWeight={700} color="text.secondary">
                  操作
                </Typography>
              </Box>
              {rows.map(renderFileRow)}
            </Box>
          )}

          <Box
            ref={sentinelRef}
            sx={{
              py: 4,
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center',
              visibility: hasNextPage ? 'visible' : 'hidden',
            }}
          >
            {isFetchingNextPage && <CircularProgress size={24} />}
          </Box>
        </Box>
      )}
    </Paper>
  );
};
