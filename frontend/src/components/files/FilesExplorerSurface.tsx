import React from 'react';
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
} from '@mui/material';
import { Folder, InsertDriveFile, MoreHoriz } from '@mui/icons-material';
import type { FileItem } from '../../api/types';
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
  onSelectFile: (file: FileItem, index: number, event?: React.MouseEvent<HTMLElement>) => void;
  onSelectAll: () => void;
  onOpenFile: (file: FileItem) => void;
  onContextMenu: (file: FileItem, index: number, event: React.MouseEvent<HTMLElement>) => void;
  onEmptyContextMenu: (event: React.MouseEvent<HTMLElement>) => void;
  getLogicalPath: (file: Pick<FileItem, 'directory' | 'filename' | 'path'>) => string;
  getSelectionKey: (file: Pick<FileItem, 'id'>) => string;
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
  onSelectFile,
  onSelectAll,
  onOpenFile,
  onContextMenu,
  onEmptyContextMenu,
  getLogicalPath,
  getSelectionKey,
}) => {
  const theme = useTheme();

  function renderFileCard(file: FileItem, index: number) {
    const selected = Boolean(selectedById[getSelectionKey(file)]);
    return (
      <Paper
        key={file.id}
        elevation={0}
        onClick={(event) => onSelectFile(file, index, event)}
        onDoubleClick={() => onOpenFile(file)}
        onContextMenu={(event) => onContextMenu(file, index, event)}
        sx={{
          p: 1.5,
          minHeight: 240,
          cursor: 'default',
          border: '1px solid',
          borderColor: selected ? 'primary.main' : 'divider',
          bgcolor: selected ? alpha(theme.palette.primary.main, 0.08) : 'background.paper',
          transition: 'all 180ms ease',
          animation: 'fadeIn 300ms ease-out forwards',
          animationDelay: `${Math.min(index * 20, 300)}ms`,
          opacity: 0,
          '&:hover': {
            bgcolor: selected ? alpha(theme.palette.primary.main, 0.12) : 'action.hover',
            boxShadow: theme.palette.mode === 'dark' ? 'none' : '0 8px 20px rgba(0,0,0,0.06)',
            transform: 'translateY(-2px)',
            borderColor: selected ? 'primary.main' : alpha(theme.palette.text.primary, 0.15),
          },
          '@keyframes fadeIn': {
            from: { opacity: 0, transform: 'translateY(4px)' },
            to: { opacity: 1, transform: 'translateY(0)' },
          },
        }}
      >
        <Stack spacing={1.2}>
          <Stack direction="row" alignItems="center" justifyContent="space-between">
            <Checkbox
              checked={selected}
              size="small"
              onClick={(event) => {
                event.stopPropagation();
                onSelectFile(file, index, event);
              }}
            />
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
              height: 120,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'action.hover',
              borderRadius: 1,
              border: '1px solid',
              borderColor: alpha(theme.palette.divider, 0.05),
              overflow: 'hidden',
              position: 'relative',
            }}
          >
            {file.directory ? (
              <Folder sx={{ fontSize: 72, color: '#E9A23B' }} />
            ) : (
              <Box sx={{ transform: 'scale(1.45)' }}>
                <FileThumbnail file={file} />
              </Box>
            )}
          </Box>
          <Box>
            <Typography noWrap fontWeight={700} title={file.filename}>
              {favoriteIds.has(file.id) ? '★ ' : ''}
              {file.filename}
            </Typography>
            <Stack direction="row" spacing={0.75} alignItems="center" sx={{ mt: 0.75 }}>
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
              <Typography variant="caption" color="text.secondary" noWrap sx={{ fontSize: 11 }}>
                {formatDateTime(file.createdAt)}
              </Typography>
            </Stack>
            <Typography noWrap variant="caption" color="text.secondary" title={getLogicalPath(file)} sx={{ mt: 0.5, display: 'block', opacity: 0.8 }}>
              {file.directory ? '目录项目' : formatBytes(file.size)} · {getLogicalPath(file)}
            </Typography>
          </Box>
        </Stack>
      </Paper>
    );
  }

  function renderFileRow(file: FileItem, index: number) {
    const selected = Boolean(selectedById[getSelectionKey(file)]);
    return (
      <Box
        key={file.id}
        role="row"
        onClick={(event) => onSelectFile(file, index, event)}
        onDoubleClick={() => onOpenFile(file)}
        onContextMenu={(event) => onContextMenu(file, index, event)}
        sx={{
          display: 'grid',
          gridTemplateColumns: '44px minmax(220px,1fr) 100px 100px 170px 58px',
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
        <Checkbox
          checked={selected}
          size="small"
          onClick={(event) => {
            event.stopPropagation();
            onSelectFile(file, index, event);
          }}
        />
        <Stack direction="row" alignItems="center" spacing={1.5} sx={{ minWidth: 0 }}>
          <FileIcon file={file} selected={selected} />
          <Box sx={{ minWidth: 0 }}>
            <Typography noWrap fontWeight={600} title={file.filename}>
              {favoriteIds.has(file.id) ? '★ ' : ''}
              {file.filename}
            </Typography>
            <Typography noWrap variant="caption" color="text.secondary" title={getLogicalPath(file)}>
              {getLogicalPath(file)}
            </Typography>
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
          {formatDateTime(file.createdAt)}
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
      sx={{
        minHeight: 430,
        overflow: 'auto',
        border: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.paper',
        boxShadow: theme.palette.mode === 'dark' ? 'none' : '0 18px 40px rgba(15, 23, 42, 0.04)',
      }}
    >
      {isLoading ? (
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
      ) : viewMode === 'grid' ? (
        <Box
          sx={{
            p: 2,
            display: 'grid',
            gap: 2,
            gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
          }}
        >
          {rows.map(renderFileCard)}
        </Box>
      ) : (
        <Box sx={{ minWidth: 760, overflowX: 'auto' }}>
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: '44px minmax(220px,1fr) 100px 100px 170px 58px',
              alignItems: 'center',
              minHeight: 42,
              px: 1,
              borderBottom: '1px solid',
              borderColor: 'divider',
              bgcolor: 'action.hover',
            }}
          >
            <Checkbox
              size="small"
              checked={allSelected}
              indeterminate={selectedCount > 0 && !allSelected}
              onChange={onSelectAll}
            />
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
    </Paper>
  );
};
