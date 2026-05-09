import React from 'react';
import { Alert, Box, Paper, Stack, Typography } from '@mui/material';
import { Activity, AlertTriangle, FileText, HardDrive, Share2, Users } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';
import AdminPage from '../../components/admin/AdminPage';
import AdminStatusBadge from '../../components/admin/AdminStatusBadge';
import { useAdminSummary } from '../../api/queries';
import { formatBytes, formatPercent } from '../../lib/format';

type SummaryCardProps = {
  icon: React.ReactNode;
  label: string;
  value: React.ReactNode;
  tone?: 'info' | 'warning' | 'success' | 'danger' | 'neutral';
};

const SummaryCard: React.FC<SummaryCardProps> = ({ icon, label, value, tone = 'neutral' }) => {
  const tones = {
    info: { background: '#EFF6FF', color: '#2563EB' },
    warning: { background: '#FEF3C7', color: '#D97706' },
    success: { background: '#DCFCE7', color: '#16A34A' },
    danger: { background: '#FEE2E2', color: '#DC2626' },
    neutral: { background: '#F3F4F6', color: '#4B5563' },
  } as const;

  return (
    <Paper
      elevation={0}
      sx={{
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 3,
        p: 2,
        bgcolor: 'background.paper',
      }}
    >
      <Stack direction="row" spacing={1.5} alignItems="center">
        <Box
          sx={{
            width: 40,
            height: 40,
            borderRadius: 2,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: tones[tone].background,
            color: tones[tone].color,
            flexShrink: 0,
          }}
        >
          {icon}
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
            {label}
          </Typography>
          <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
            {value}
          </Typography>
        </Box>
      </Stack>
    </Paper>
  );
};

const AdminHome: React.FC = () => {
  const { data, isLoading, isError } = useAdminSummary();

  return (
    <AdminLayout title="管理面板">
      <AdminPage
        title="管理面板"
        description="查看治理侧汇总指标、邀请码状态与当前离线任务占用情况。"
        isLoading={isLoading}
        isError={isError}
        errorText="管理概览加载失败。"
      >
        <Stack spacing={2.5}>
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: {
                xs: '1fr',
                md: 'repeat(2, minmax(0, 1fr))',
                xl: 'repeat(5, minmax(0, 1fr))',
              },
              gap: 2,
            }}
          >
            <SummaryCard icon={<Users size={18} />} label="总用户数" value={data?.totalUsers ?? 0} tone="info" />
            <SummaryCard icon={<FileText size={18} />} label="文件总数" value={data?.totalFiles ?? 0} tone="warning" />
            <SummaryCard icon={<Share2 size={18} />} label="分享下载总量" value={data?.shareDownloadCount ?? 0} tone="success" />
            <SummaryCard icon={<FileText size={18} />} label="收藏文件" value={data?.favoriteFileCount ?? 0} tone="neutral" />
            <SummaryCard icon={<Activity size={18} />} label="活跃任务" value={data?.activeTaskCount ?? 0} tone="danger" />
          </Box>

          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', xl: 'minmax(0, 2fr) minmax(360px, 1fr)' },
              gap: 2,
            }}
          >
            <Paper
              elevation={0}
              sx={{
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 3,
                p: 3,
                bgcolor: 'background.paper',
              }}
            >
              <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2} sx={{ mb: 2 }}>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  趋势概览
                </Typography>
                <AdminStatusBadge label="生成于 10 分钟前" tone="neutral" />
              </Stack>
              <Box
                sx={{
                  minHeight: 320,
                  border: '1px dashed',
                  borderColor: 'divider',
                  borderRadius: 3,
                  bgcolor: 'action.hover',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 1,
                  textAlign: 'center',
                  px: 3,
                }}
              >
                <Activity size={44} />
                <Typography variant="body1" sx={{ fontWeight: 600 }}>
                  图表数据加载中...
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  此处将渲染按日期统计的用户、文件和分享趋势图。
                </Typography>
              </Box>
            </Paper>

            <Stack spacing={2}>
              <Paper
                elevation={0}
                sx={{
                  border: '1px solid',
                  borderColor: 'divider',
                  borderRadius: 3,
                  p: 2.5,
                  bgcolor: 'background.paper',
                }}
              >
                <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 2 }}>
                  <HardDrive size={18} />
                  <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                    离线快传占用
                  </Typography>
                </Stack>
                <Stack spacing={1}>
                  <Stack direction="row" justifyContent="space-between" spacing={2}>
                    <Typography variant="body2" color="text.secondary">
                      占比
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                      {data ? formatPercent(data.offlineTransferStorageBytes, data.offlineTransferStorageLimitBytes) : '0%'}
                    </Typography>
                  </Stack>
                  <Box sx={{ width: '100%', height: 8, borderRadius: 999, bgcolor: 'action.disabledBackground', overflow: 'hidden' }}>
                    <Box
                      sx={{
                        width: data ? formatPercent(data.offlineTransferStorageBytes, data.offlineTransferStorageLimitBytes) : '0%',
                        height: '100%',
                        bgcolor: 'secondary.main',
                      }}
                    />
                  </Box>
                  <Typography variant="body2" color="text.secondary">
                    当前累计存储：{data ? formatBytes(data.totalStorageBytes) : '-'}
                  </Typography>
                </Stack>
              </Paper>

              <Alert severity="warning" icon={<AlertTriangle size={18} />}>
                <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
                  当前邀请码
                </Typography>
                <Typography variant="body2">
                  {data?.inviteCode ? `注册邀请码：${data.inviteCode}` : '后端未返回邀请码。'}
                </Typography>
              </Alert>
            </Stack>
          </Box>
        </Stack>
      </AdminPage>
    </AdminLayout>
  );
};

export default AdminHome;
