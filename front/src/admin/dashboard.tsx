import { useEffect, useState } from 'react';
import ArchiveRoundedIcon from '@mui/icons-material/ArchiveRounded';
import BoltRoundedIcon from '@mui/icons-material/BoltRounded';
import CloudDownloadRoundedIcon from '@mui/icons-material/CloudDownloadRounded';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import EditRoundedIcon from '@mui/icons-material/EditRounded';
import FolderRoundedIcon from '@mui/icons-material/FolderRounded';
import HubRoundedIcon from '@mui/icons-material/HubRounded';
import RefreshIcon from '@mui/icons-material/Refresh';
import StorageRoundedIcon from '@mui/icons-material/StorageRounded';
import { Alert, Box, Button, Card, CardContent, CircularProgress, Grid, Stack, Typography } from '@mui/material';
import { useNavigate } from 'react-router-dom';

import { apiRequest } from '@/src/lib/api';
import { readStoredSession } from '@/src/lib/session';
import type { AdminOfflineTransferStorageLimitResponse, AdminSummary } from '@/src/lib/types';
import {
  buildRequestLineChartModel,
  buildRequestLineChartXAxisPoints,
  formatMetricValue,
  getInviteCodePanelState,
  parseStorageLimitInput,
} from './dashboard-state';

interface DashboardState {
  summary: AdminSummary | null;
}

interface MetricCardDefinition {
  key: string;
  title: string;
  scope: string;
  accent: string;
  icon: React.ReactNode;
  value: string;
  helper: string;
}

const DASHBOARD_CARD_BG = '#111827';
const DASHBOARD_CARD_BORDER = 'rgba(148, 163, 184, 0.22)';
const DASHBOARD_CARD_TEXT = '#f8fafc';
const DASHBOARD_CARD_MUTED_TEXT = 'rgba(226, 232, 240, 0.72)';

function DashboardMetricCard({ metric }: { metric: MetricCardDefinition }) {
  return (
    <Card
      variant="outlined"
      sx={(theme) => ({
        borderColor: theme.palette.mode === 'dark' ? DASHBOARD_CARD_BORDER : 'divider',
        backgroundColor: theme.palette.mode === 'dark' ? DASHBOARD_CARD_BG : '#fff',
        color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : theme.palette.text.primary,
        boxShadow: theme.palette.mode === 'dark' ? '0 20px 45px rgba(15, 23, 42, 0.28)' : 'none',
        height: '100%',
        position: 'relative',
        overflow: 'hidden',
        '&::before': {
          content: '""',
          position: 'absolute',
          inset: '0 auto 0 0',
          width: 4,
          backgroundColor: metric.accent,
        },
      })}
    >
      <CardContent sx={{ height: '100%', pl: 2.5 }}>
        <Stack spacing={1.25} sx={{ height: '100%' }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Box
              sx={{
                width: 42,
                height: 42,
                borderRadius: 2,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: metric.accent,
                backgroundColor: `${metric.accent}14`,
              }}
            >
              {metric.icon}
            </Box>
            <Typography
              variant="caption"
              sx={{
                px: 1,
                py: 0.4,
                borderRadius: 99,
                color: metric.accent,
                backgroundColor: `${metric.accent}12`,
                fontWeight: 700,
              }}
            >
              {metric.scope}
            </Typography>
          </Stack>
          <Stack spacing={0.75}>
            <Typography
              variant="subtitle2"
              sx={(theme) => ({
                color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
                fontWeight: 700,
              })}
            >
              {metric.title}
            </Typography>
            <Typography
              variant="h3"
              sx={(theme) => ({
                fontWeight: 800,
                lineHeight: 1.05,
                letterSpacing: '-0.02em',
                color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : 'text.primary',
              })}
            >
              {metric.value}
            </Typography>
          </Stack>
          <Typography
            variant="body2"
            sx={(theme) => ({
              mt: 'auto',
              color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
            })}
          >
            {metric.helper}
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  );
}

function RequestTrendChart({ summary }: { summary: AdminSummary }) {
  const chart = buildRequestLineChartModel(summary.requestTimeline);
  const currentHour = new Date().getHours();
  const currentPoint = chart.points.find((point) => point.hour === currentHour) ?? chart.points.at(-1) ?? null;
  const xAxisPoints = buildRequestLineChartXAxisPoints(chart.points);
  const hasRequests = chart.maxValue > 0;
  const scaleMax = chart.maxValue > 0 ? chart.maxValue : 4;

  return (
    <Card
      variant="outlined"
      sx={(theme) => ({
        borderColor: theme.palette.mode === 'dark' ? DASHBOARD_CARD_BORDER : 'divider',
        backgroundColor: theme.palette.mode === 'dark' ? DASHBOARD_CARD_BG : '#fff',
        color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : theme.palette.text.primary,
        boxShadow: theme.palette.mode === 'dark' ? '0 20px 45px rgba(15, 23, 42, 0.28)' : 'none',
      })}
    >
      <CardContent>
        <Stack spacing={2.5}>
          <Stack
            direction={{ xs: 'column', lg: 'row' }}
            spacing={2}
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', lg: 'center' }}
          >
            <Stack spacing={0.75}>
              <Typography variant="h6" fontWeight={700}>
                今日请求折线图
              </Typography>
              <Typography
                variant="body2"
                sx={(theme) => ({
                  color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
                })}
              >
                按小时统计今天已发生的 `/api/**` 请求；曲线会随当天已过时间自然拉长，不再预留未来小时。
              </Typography>
            </Stack>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.25}>
              <Stack
                spacing={0.35}
                sx={{
                  minWidth: 132,
                  px: 1.5,
                  py: 1.25,
                  borderRadius: 2,
                  backgroundColor: (theme) => theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.06)' : 'action.hover',
                  border: (theme) => theme.palette.mode === 'dark' ? '1px solid rgba(148, 163, 184, 0.16)' : '1px solid transparent',
                }}
              >
                <Typography
                  variant="caption"
                  sx={(theme) => ({
                    color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
                  })}
                  fontWeight={700}
                >
                  当前小时
                </Typography>
                <Typography
                  variant="h6"
                  fontWeight={800}
                  sx={(theme) => ({
                    color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : 'text.primary',
                  })}
                >
                  {formatMetricValue(currentPoint?.requestCount ?? 0, 'count')}
                </Typography>
                <Typography
                  variant="caption"
                  sx={(theme) => ({
                    color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
                  })}
                >
                  {currentPoint?.label ?? '--'}
                </Typography>
              </Stack>
              <Stack
                spacing={0.35}
                sx={{
                  minWidth: 132,
                  px: 1.5,
                  py: 1.25,
                  borderRadius: 2,
                  backgroundColor: (theme) => theme.palette.mode === 'dark' ? 'rgba(37, 99, 235, 0.14)' : '#eff6ff',
                  border: (theme) => theme.palette.mode === 'dark' ? '1px solid rgba(96, 165, 250, 0.2)' : '1px solid transparent',
                }}
              >
                <Typography
                  variant="caption"
                  sx={(theme) => ({
                    color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
                  })}
                  fontWeight={700}
                >
                  今日峰值
                </Typography>
                <Typography
                  variant="h6"
                  fontWeight={800}
                  sx={(theme) => ({
                    color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : 'text.primary',
                  })}
                >
                  {formatMetricValue(chart.peakPoint?.requestCount ?? 0, 'count')}
                </Typography>
                <Typography
                  variant="caption"
                  sx={(theme) => ({
                    color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
                  })}
                >
                  {chart.peakPoint?.label ?? '--'}
                </Typography>
              </Stack>
            </Stack>
          </Stack>

          <Box
            sx={{
              p: { xs: 1.5, md: 2 },
              borderRadius: 3,
              border: (theme) => theme.palette.mode === 'dark' ? '1px solid rgba(148, 163, 184, 0.16)' : '1px solid rgba(148, 163, 184, 0.24)',
              background: (theme) =>
                theme.palette.mode === 'dark'
                  ? 'linear-gradient(180deg, rgba(15, 23, 42, 0.72) 0%, rgba(17, 24, 39, 0.94) 100%)'
                  : 'linear-gradient(180deg, #f8fbff 0%, #eef5ff 100%)',
            }}
          >
            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', md: '56px minmax(0, 1fr)' },
                gap: 1.5,
                alignItems: 'stretch',
              }}
            >
              <Stack
                spacing={0}
                justifyContent="space-between"
                sx={{ py: 1.25, display: { xs: 'none', md: 'flex' } }}
              >
                {chart.yAxisTicks.slice().reverse().map((tick) => (
                  <Typography
                    key={tick}
                    variant="caption"
                    sx={(theme) => ({
                      color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
                    })}
                  >
                    {formatMetricValue(tick, 'count')}
                  </Typography>
                ))}
              </Stack>

              <Stack spacing={1.25}>
                <Box
                  sx={{
                    position: 'relative',
                    height: { xs: 220, md: 280 },
                    borderRadius: 2.5,
                    overflow: 'hidden',
                    backgroundColor: (theme) => theme.palette.mode === 'dark' ? 'rgba(15, 23, 42, 0.58)' : 'rgba(255, 255, 255, 0.72)',
                  }}
                >
                  <Box component="svg" viewBox="0 0 100 100" preserveAspectRatio="none" sx={{ display: 'block', width: '100%', height: '100%' }}>
                    <defs>
                      <linearGradient id="request-trend-area" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#2563eb" stopOpacity="0.28" />
                        <stop offset="100%" stopColor="#2563eb" stopOpacity="0.02" />
                      </linearGradient>
                    </defs>

                    {chart.yAxisTicks.map((tick) => {
                      const y = 100 - (tick / scaleMax) * 100;
                      return (
                        <line
                          key={tick}
                          x1="0"
                          x2="100"
                          y1={y}
                          y2={y}
                          stroke="rgba(148, 163, 184, 0.28)"
                          strokeDasharray="3 4"
                          vectorEffect="non-scaling-stroke"
                        />
                      );
                    })}

                    {currentPoint && (
                      <line
                        x1={currentPoint.x}
                        x2={currentPoint.x}
                        y1="0"
                        y2="100"
                        stroke="rgba(15, 23, 42, 0.18)"
                        strokeDasharray="2 4"
                        vectorEffect="non-scaling-stroke"
                      />
                    )}

                    {chart.areaPath && <path d={chart.areaPath} fill="url(#request-trend-area)" />}
                    {chart.linePath && (
                      <path
                        d={chart.linePath}
                        fill="none"
                        stroke="#2563eb"
                        strokeWidth="2.6"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        vectorEffect="non-scaling-stroke"
                      />
                    )}
                  </Box>

                  {chart.points.map((point) => (
                    <Box
                      key={point.label}
                      sx={{
                        position: 'absolute',
                        left: `${point.x}%`,
                        top: `${point.y}%`,
                        width: point.hour === currentPoint?.hour ? 8 : 6,
                        height: point.hour === currentPoint?.hour ? 8 : 6,
                        borderRadius: '50%',
                        backgroundColor: point.hour === currentPoint?.hour ? '#0f172a' : '#2563eb',
                        transform: 'translate(-50%, -50%)',
                        pointerEvents: 'none',
                        zIndex: 1,
                      }}
                    />
                  ))}

                  {!hasRequests && (
                    <Stack
                      spacing={0.4}
                      alignItems="center"
                      justifyContent="center"
                      sx={{
                        position: 'absolute',
                        inset: 0,
                        color: (theme) => theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
                        backgroundColor: (theme) => theme.palette.mode === 'dark' ? 'rgba(15, 23, 42, 0.82)' : 'rgba(248, 250, 252, 0.68)',
                      }}
                    >
                      <Typography
                        variant="subtitle2"
                        fontWeight={700}
                        sx={(theme) => ({
                          color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : 'text.primary',
                        })}
                      >
                        今日还没有请求数据
                      </Typography>
                      <Typography variant="body2">
                        新请求进入后，这里会自动形成实时折线。
                      </Typography>
                    </Stack>
                  )}
                </Box>

                <Stack direction="row" justifyContent="space-between" sx={{ px: 0.5 }}>
                  {xAxisPoints.map((point) => (
                    <Typography
                      key={point.label}
                      variant="caption"
                      sx={(theme) => ({
                        color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
                      })}
                    >
                      {point.label}
                    </Typography>
                  ))}
                </Stack>
              </Stack>
            </Box>
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
}

function DailyActiveUsersCard({ summary }: { summary: AdminSummary }) {
  const latestDay = summary.dailyActiveUsers.at(-1) ?? null;

  return (
    <Card
      variant="outlined"
      sx={(theme) => ({
        borderColor: theme.palette.mode === 'dark' ? DASHBOARD_CARD_BORDER : 'divider',
        backgroundColor: theme.palette.mode === 'dark' ? DASHBOARD_CARD_BG : '#fff',
        color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : theme.palette.text.primary,
        boxShadow: theme.palette.mode === 'dark' ? '0 20px 45px rgba(15, 23, 42, 0.28)' : 'none',
      })}
    >
      <CardContent>
        <Stack spacing={2}>
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            spacing={1.5}
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', md: 'center' }}
          >
            <Stack spacing={0.75}>
              <Typography variant="h6" fontWeight={700}>
                最近 7 天上线记录
              </Typography>
              <Typography
                variant="body2"
                sx={(theme) => ({
                  color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
                })}
              >
                JWT 鉴权成功后会记录当天首次上线用户，只保留最近 7 天，便于回看每天有多少人上线以及具体是谁。
              </Typography>
            </Stack>

            <Stack
              spacing={0.35}
              sx={{
                minWidth: 156,
                px: 1.5,
                py: 1.25,
                borderRadius: 2,
                backgroundColor: (theme) => theme.palette.mode === 'dark' ? 'rgba(16, 185, 129, 0.12)' : '#ecfdf5',
                border: (theme) => theme.palette.mode === 'dark' ? '1px solid rgba(52, 211, 153, 0.18)' : '1px solid transparent',
              }}
            >
              <Typography
                variant="caption"
                sx={(theme) => ({
                  color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
                })}
                fontWeight={700}
              >
                今日上线人数
              </Typography>
              <Typography
                variant="h6"
                fontWeight={800}
                sx={(theme) => ({
                  color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : 'text.primary',
                })}
              >
                {formatMetricValue(latestDay?.userCount ?? 0, 'count')}
              </Typography>
              <Typography
                variant="caption"
                sx={(theme) => ({
                  color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
                })}
              >
                {latestDay?.label ?? '--'}
              </Typography>
            </Stack>
          </Stack>

          <Stack spacing={1.2}>
            {summary.dailyActiveUsers.slice().reverse().map((day) => (
              <Box
                key={day.metricDate}
                sx={(theme) => ({
                  px: 1.5,
                  py: 1.25,
                  borderRadius: 2,
                  border: theme.palette.mode === 'dark' ? '1px solid rgba(148, 163, 184, 0.16)' : '1px solid rgba(148, 163, 184, 0.24)',
                  backgroundColor: theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.03)' : '#f8fafc',
                })}
              >
                <Stack
                  direction={{ xs: 'column', md: 'row' }}
                  spacing={1.25}
                  justifyContent="space-between"
                  alignItems={{ xs: 'flex-start', md: 'center' }}
                >
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                    <Typography fontWeight={700}>{day.label}</Typography>
                    <Typography
                      variant="caption"
                      sx={(theme) => ({
                        px: 0.9,
                        py: 0.3,
                        borderRadius: 99,
                        color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : 'text.primary',
                        backgroundColor: theme.palette.mode === 'dark' ? 'rgba(59, 130, 246, 0.18)' : '#dbeafe',
                      })}
                    >
                      {formatMetricValue(day.userCount, 'count')} 人
                    </Typography>
                    <Typography
                      variant="caption"
                      sx={(theme) => ({
                        color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
                      })}
                    >
                      {day.metricDate}
                    </Typography>
                  </Stack>

                  <Typography
                    variant="body2"
                    sx={(theme) => ({
                      color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary',
                    })}
                  >
                    {day.usernames.length > 0 ? day.usernames.join('、') : '当天无人上线'}
                  </Typography>
                </Stack>
              </Box>
            ))}
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}

export function PortalAdminDashboard() {
  const [state, setState] = useState<DashboardState>({
    summary: null,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [copyMessage, setCopyMessage] = useState('');
  const [updatingLimit, setUpdatingLimit] = useState(false);
  const navigate = useNavigate();
  const session = readStoredSession();

  async function loadDashboardData() {
    setLoading(true);
    setError('');

    try {
      const summary = await apiRequest<AdminSummary>('/admin/summary');
      setState({ summary });
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '后台首页数据加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadDashboardData();
  }, []);

  const inviteCodePanel = getInviteCodePanelState(state.summary);
  const summary = state.summary;

  const metrics: MetricCardDefinition[] = summary ? [
    {
      key: 'total-storage',
      title: '总存储量',
      scope: '累计',
      value: formatMetricValue(summary.totalStorageBytes, 'bytes'),
      helper: `全站普通文件 ${formatMetricValue(summary.totalFiles, 'count')} 个。`,
      accent: '#0f766e',
      icon: <StorageRoundedIcon />,
    },
    {
      key: 'download-traffic',
      title: '下载流量',
      scope: '累计',
      value: formatMetricValue(summary.downloadTrafficBytes, 'bytes'),
      helper: '文件下载和离线快传下载都会计入这里。',
      accent: '#2563eb',
      icon: <CloudDownloadRoundedIcon />,
    },
    {
      key: 'request-count',
      title: '今日请求次数',
      scope: '今日',
      value: formatMetricValue(summary.requestCount, 'count'),
      helper: '只统计今天的 `/api/**` 请求，不再显示累计值。',
      accent: '#d97706',
      icon: <HubRoundedIcon />,
    },
    {
      key: 'transfer-usage',
      title: '快传使用量',
      scope: '累计',
      value: formatMetricValue(summary.transferUsageBytes, 'bytes'),
      helper: '按快传会话申报的文件体积累计统计。',
      accent: '#7c3aed',
      icon: <BoltRoundedIcon />,
    },
    {
      key: 'offline-transfer-storage',
      title: '快传离线存储量',
      scope: '当前',
      value: formatMetricValue(summary.offlineTransferStorageBytes, 'bytes'),
      helper: `当前上限 ${formatMetricValue(summary.offlineTransferStorageLimitBytes, 'bytes')}。`,
      accent: '#be123c',
      icon: (
        <Stack direction="row" spacing={0.5} alignItems="center">
          <ArchiveRoundedIcon fontSize="small" />
          <BoltRoundedIcon fontSize="small" />
        </Stack>
      ),
    },
  ] : [];

  async function handleRefreshInviteCode() {
    setCopyMessage('');
    setSuccessMessage('');
    await loadDashboardData();
  }

  async function handleCopyInviteCode() {
    if (!inviteCodePanel.canCopy) {
      return;
    }

    if (!navigator.clipboard?.writeText) {
      setError('当前浏览器不支持复制邀请码');
      return;
    }

    try {
      await navigator.clipboard.writeText(inviteCodePanel.inviteCode);
      setCopyMessage('邀请码已复制到剪贴板');
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '复制邀请码失败');
    }
  }

  async function handleUpdateOfflineTransferLimit() {
    if (!summary) {
      return;
    }

    const input = window.prompt(
      `请输入新的离线快传存储上限（支持 B/KB/MB/GB/TB，当前 ${formatMetricValue(summary.offlineTransferStorageLimitBytes, 'bytes')}）`,
      `${Math.max(1, Math.floor(summary.offlineTransferStorageLimitBytes / 1024 / 1024 / 1024))}GB`,
    );
    if (!input) {
      return;
    }

    const offlineTransferStorageLimitBytes = parseStorageLimitInput(input);
    if (!offlineTransferStorageLimitBytes) {
      setError('输入格式不正确，请输入例如 20GB 或 21474836480');
      return;
    }

    setUpdatingLimit(true);
    setError('');
    setSuccessMessage('');
    try {
      const result = await apiRequest<AdminOfflineTransferStorageLimitResponse>('/admin/settings/offline-transfer-storage-limit', {
        method: 'PATCH',
        body: { offlineTransferStorageLimitBytes },
      });
      setSuccessMessage(`离线快传存储上限已更新为 ${formatMetricValue(result.offlineTransferStorageLimitBytes, 'bytes')}`);
      await loadDashboardData();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '离线快传存储上限更新失败');
    } finally {
      setUpdatingLimit(false);
    }
  }

  return (
    <Stack spacing={3} sx={{ p: 2 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }}>
        <Stack spacing={1}>
          <Typography variant="h4" fontWeight={700}>
            YOYUZH Admin
          </Typography>
          <Typography color="text.secondary">
            管理面板展示站点核心指标，并按小时画出今天的请求走势。
          </Typography>
        </Stack>
        <Button variant="outlined" onClick={() => navigate('/overview')}>
          返回总览
        </Button>
      </Stack>

      {loading && (
        <Stack direction="row" spacing={1} alignItems="center">
          <CircularProgress size={20} />
          <Typography color="text.secondary">正在加载后台数据...</Typography>
        </Stack>
      )}

      {error && <Alert severity="error">{error}</Alert>}
      {successMessage && <Alert severity="success">{successMessage}</Alert>}
      {copyMessage && <Alert severity="success">{copyMessage}</Alert>}

      <Grid container spacing={2}>
        {metrics.map((metric) => (
          <Grid key={metric.key} size={{ xs: 12, sm: 6, xl: 2.4 }}>
            <DashboardMetricCard metric={metric} />
          </Grid>
        ))}
      </Grid>

      {summary && (
        <Stack spacing={2}>
          <RequestTrendChart summary={summary} />
          <DailyActiveUsersCard summary={summary} />
        </Stack>
      )}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card
            variant="outlined"
            sx={(theme) => ({
              borderColor: theme.palette.mode === 'dark' ? DASHBOARD_CARD_BORDER : 'divider',
              backgroundColor: theme.palette.mode === 'dark' ? DASHBOARD_CARD_BG : '#fff',
              color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : theme.palette.text.primary,
              boxShadow: theme.palette.mode === 'dark' ? '0 20px 45px rgba(15, 23, 42, 0.28)' : 'none',
              height: '100%',
            })}
          >
            <CardContent sx={{ height: '100%' }}>
              <Stack spacing={1.25} sx={{ height: '100%' }}>
                <Typography
                  variant="h6"
                  fontWeight={600}
                  sx={(theme) => ({
                    color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : 'text.primary',
                  })}
                >
                  当前管理员
                </Typography>
                <Typography sx={(theme) => ({ color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary' })}>
                  用户名：{session?.user.username ?? '-'}
                </Typography>
                <Typography sx={(theme) => ({ color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary' })}>
                  邮箱：{session?.user.email ?? '-'}
                </Typography>
                <Typography sx={(theme) => ({ color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary' })}>
                  用户 ID：{session?.user.id ?? '-'}
                </Typography>
                <Typography sx={(theme) => ({ mt: 'auto', color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary' })}>
                  管理用户 {formatMetricValue(summary?.totalUsers ?? 0, 'count')}，文件总量 {formatMetricValue(summary?.totalFiles ?? 0, 'count')}。
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          <Card
            variant="outlined"
            sx={(theme) => ({
              borderColor: theme.palette.mode === 'dark' ? DASHBOARD_CARD_BORDER : 'divider',
              backgroundColor: theme.palette.mode === 'dark' ? DASHBOARD_CARD_BG : '#fff',
              color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : theme.palette.text.primary,
              boxShadow: theme.palette.mode === 'dark' ? '0 20px 45px rgba(15, 23, 42, 0.28)' : 'none',
              height: '100%',
            })}
          >
            <CardContent sx={{ height: '100%' }}>
              <Stack spacing={1.5} sx={{ height: '100%' }}>
                <Typography
                  variant="h6"
                  fontWeight={600}
                  sx={(theme) => ({
                    color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : 'text.primary',
                  })}
                >
                  离线快传配置
                </Typography>
                <Typography sx={(theme) => ({ color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary' })}>
                  当前离线占用：{formatMetricValue(summary?.offlineTransferStorageBytes ?? 0, 'bytes')}
                </Typography>
                <Typography sx={(theme) => ({ color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary' })}>
                  当前上限：{formatMetricValue(summary?.offlineTransferStorageLimitBytes ?? 0, 'bytes')}
                </Typography>
                <Typography variant="body2" sx={(theme) => ({ mt: 'auto', color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary' })}>
                  调整后会立即影响新的离线快传上传校验，避免离线存储无限增长。
                </Typography>
                <Button
                  variant="contained"
                  size="small"
                  startIcon={<EditRoundedIcon />}
                  disabled={updatingLimit || !summary}
                  onClick={() => void handleUpdateOfflineTransferLimit()}
                >
                  调整离线上限
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          <Card
            variant="outlined"
            sx={(theme) => ({
              borderColor: theme.palette.mode === 'dark' ? DASHBOARD_CARD_BORDER : 'divider',
              backgroundColor: theme.palette.mode === 'dark' ? DASHBOARD_CARD_BG : '#fff',
              color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : theme.palette.text.primary,
              boxShadow: theme.palette.mode === 'dark' ? '0 20px 45px rgba(15, 23, 42, 0.28)' : 'none',
              height: '100%',
            })}
          >
            <CardContent sx={{ height: '100%' }}>
              <Stack spacing={1.5} sx={{ height: '100%' }}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <FolderRoundedIcon color="primary" />
                  <Typography
                    variant="h6"
                    fontWeight={600}
                    sx={(theme) => ({
                      color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : 'text.primary',
                    })}
                  >
                    当前邀请码
                  </Typography>
                </Stack>
                <Typography sx={(theme) => ({ color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_MUTED_TEXT : 'text.secondary' })}>
                  注册成功一次后会自动刷新，后台展示的始终是下一次可用的邀请码。
                </Typography>
                <Typography
                  component="code"
                  sx={(theme) => ({
                    display: 'inline-block',
                    width: 'fit-content',
                    px: 1.5,
                    py: 1,
                    borderRadius: 1,
                    color: theme.palette.mode === 'dark' ? DASHBOARD_CARD_TEXT : 'text.primary',
                    backgroundColor: theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.08)' : 'action.hover',
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                    fontSize: '0.95rem',
                  })}
                >
                  {inviteCodePanel.inviteCode}
                </Typography>
                <Stack direction="row" spacing={1} sx={{ mt: 'auto' }}>
                  <Button
                    variant="contained"
                    size="small"
                    startIcon={<ContentCopyIcon />}
                    onClick={() => void handleCopyInviteCode()}
                    disabled={!inviteCodePanel.canCopy}
                  >
                    复制
                  </Button>
                  <Button
                    variant="outlined"
                    size="small"
                    startIcon={<RefreshIcon />}
                    onClick={() => void handleRefreshInviteCode()}
                  >
                    刷新
                  </Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}
