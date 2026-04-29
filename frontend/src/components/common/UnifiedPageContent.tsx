import React, { useMemo } from 'react';
import {
  Box,
  Paper,
  Typography,
  alpha,
  LinearProgress,
  Stack,
  Pagination,
} from '@mui/material';
import { createTheme, ThemeProvider as MuiThemeProvider } from '@mui/material/styles';
import { useTheme as useAppTheme } from '../../hooks/useTheme';

interface UnifiedPageContentProps {
  title?: string;
  actions?: React.ReactNode;
  isLoading?: boolean;
  isError?: boolean;
  errorText?: string;
  isEmpty?: boolean;
  emptyText?: string;
  emptyIcon?: React.ReactNode;
  children: React.ReactNode;
  pagination?: {
    count: number;
    page: number;
    onChange: (page: number) => void;
  };
}

export const UnifiedPageContent: React.FC<UnifiedPageContentProps> = ({
  title,
  actions,
  isLoading,
  isError,
  errorText = '数据加载失败',
  isEmpty,
  emptyText = '暂无数据',
  emptyIcon,
  children,
  pagination,
}) => {
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
          text: {
            primary: theme === 'dark' ? '#F8FAFC' : '#0F172A',
            secondary: theme === 'dark' ? 'rgba(226, 232, 240, 0.72)' : 'rgba(15, 23, 42, 0.68)',
          },
          divider: theme === 'dark' ? 'rgba(255,255,255,0.08)' : 'rgba(15,23,42,0.08)',
        },
        shape: {
          borderRadius: 10,
        },
        typography: {
          fontFamily: 'Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        },
      }),
    [theme],
  );
  const isDark = theme === 'dark';

  return (
    <MuiThemeProvider theme={muiTheme}>
      <Stack spacing={2.5} sx={{ height: '100%', minHeight: 0 }}>
        {(title || actions) ? (
          <Box
            sx={{
              display: 'flex',
              justifyContent: title ? 'space-between' : 'flex-end',
              alignItems: 'center',
              px: 0.5,
            }}
          >
            {title ? (
              <Typography variant="h5" fontWeight={700} color="text.primary">
                {title}
              </Typography>
            ) : null}
            {actions}
          </Box>
        ) : null}

        <Paper
          elevation={0}
          sx={{
            flex: 1,
            minHeight: 0,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 2.25,
            bgcolor: 'background.paper',
            backgroundImage: 'none',
            color: 'text.primary',
            boxShadow: isDark ? '0 18px 40px rgba(2, 6, 23, 0.28)' : '0 12px 32px rgba(15, 23, 42, 0.04)',
          }}
        >
          {isLoading && <LinearProgress />}
          
          <Box sx={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
            {isError ? (
              <Stack alignItems="center" justifyContent="center" sx={{ height: '100%', py: 8 }}>
                <Typography color="error">{errorText}</Typography>
              </Stack>
            ) : isEmpty ? (
              <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ height: '100%', py: 8 }}>
                {emptyIcon}
                <Typography color="text.secondary">{emptyText}</Typography>
              </Stack>
            ) : (
              children
            )}
          </Box>

          {pagination && pagination.count > 1 && (
            <Box
              sx={{
                p: 2,
                borderTop: '1px solid',
                borderColor: 'divider',
                display: 'flex',
                justifyContent: 'center',
                bgcolor: isDark ? 'rgba(255,255,255,0.03)' : alpha(muiTheme.palette.action.hover, 0.04),
              }}
            >
              <Pagination
                count={pagination.count}
                page={pagination.page}
                onChange={(_, p) => pagination.onChange(p)}
                color="primary"
                size="small"
              />
            </Box>
          )}
        </Paper>
      </Stack>
    </MuiThemeProvider>
  );
};

export interface UnifiedListColumn {
  label: string;
  width?: string | number;
  flex?: number;
  align?: 'left' | 'right' | 'center';
}

interface UnifiedListProps {
  columns: UnifiedListColumn[];
  children: React.ReactNode;
  minWidth?: number | string;
}

export const UnifiedList: React.FC<UnifiedListProps> = ({ columns, children, minWidth = 800 }) => {
  const { theme } = useAppTheme();
  const isDark = theme === 'dark';
  const gridTemplateColumns = columns
    .map((col) => {
      if (col.width) return typeof col.width === 'number' ? `${col.width}px` : col.width;
      if (col.flex) return `${col.flex}fr`;
      return '1fr';
    })
    .join(' ');

  return (
    <Box sx={{ minWidth }}>
      {/* Header */}
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns,
          alignItems: 'center',
        minHeight: 42,
        px: 2,
        borderBottom: '1px solid',
        borderColor: 'divider',
        bgcolor: isDark ? 'rgba(255,255,255,0.035)' : 'action.hover',
        position: 'sticky',
        top: 0,
        zIndex: 1,
        }}
      >
        {columns.map((col, idx) => (
          <Typography
            key={idx}
            variant="caption"
            fontWeight={700}
            color="text.secondary"
            align={col.align}
            sx={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}
          >
            {col.label}
          </Typography>
        ))}
      </Box>
      {/* Body */}
      <Box>{children}</Box>
    </Box>
  );
};

interface UnifiedListRowProps {
  columns: UnifiedListColumn[];
  onClick?: () => void;
  children: React.ReactNode;
}

export const UnifiedListRow: React.FC<UnifiedListRowProps> = ({
  columns,
  onClick,
  children,
}) => {
  const { theme } = useAppTheme();
  const isDark = theme === 'dark';
  const gridTemplateColumns = columns
    .map((col) => {
      if (col.width) return typeof col.width === 'number' ? `${col.width}px` : col.width;
      if (col.flex) return `${col.flex}fr`;
      return '1fr';
    })
    .join(' ');

  return (
    <Box
      onClick={onClick}
      sx={{
        display: 'grid',
        gridTemplateColumns,
        alignItems: 'center',
        minHeight: 58,
        px: 2,
        borderBottom: '1px solid',
        borderColor: 'divider',
        transition: 'all 180ms ease',
        cursor: onClick ? 'pointer' : 'default',
        '&:hover': {
          bgcolor: isDark ? 'rgba(79,124,255,0.12)' : 'rgba(79,124,255,0.04)',
        },
        '&:last-child': {
          borderBottom: 'none',
        },
      }}
    >
      {children}
    </Box>
  );
};
