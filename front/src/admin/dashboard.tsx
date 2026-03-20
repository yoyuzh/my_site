import { useEffect, useState } from 'react';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import RefreshIcon from '@mui/icons-material/Refresh';
import { Alert, Button, Card, CardContent, Chip, CircularProgress, Grid, Stack, Typography } from '@mui/material';
import { useNavigate } from 'react-router-dom';

import { apiRequest } from '@/src/lib/api';
import { readStoredSession } from '@/src/lib/session';
import type { AdminSummary } from '@/src/lib/types';
import { getInviteCodePanelState } from './dashboard-state';

interface DashboardState {
  summary: AdminSummary | null;
}

const DASHBOARD_ITEMS = [
  {
    title: '文件资源',
    description: '已接入 /api/admin/files 与 /api/admin/files/{id} 删除接口，可查看全站文件元数据。',
    status: 'connected',
  },
  {
    title: '用户管理',
    description: '已接入 /api/admin/users，可查看账号、邮箱、手机号与权限状态。',
    status: 'connected',
  },
  {
    title: '门户运营',
    description: '当前后台专注于统一账号和文件资源，保持管理视图聚焦在核心门户能力上。',
    status: 'connected',
  },
];

export function PortalAdminDashboard() {
  const [state, setState] = useState<DashboardState>({
    summary: null,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [copyMessage, setCopyMessage] = useState('');
  const navigate = useNavigate();
  const session = readStoredSession();

  async function loadDashboardData() {
    setLoading(true);
    setError('');

    try {
      const summary = await apiRequest<AdminSummary>('/admin/summary');

      setState({
        summary,
      });
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '后台首页数据加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let active = true;

    void (async () => {
      setLoading(true);
      setError('');

      try {
        const summary = await apiRequest<AdminSummary>('/admin/summary');

        if (!active) {
          return;
        }

        setState({
          summary,
        });
      } catch (requestError) {
        if (!active) {
          return;
        }

        setError(requestError instanceof Error ? requestError.message : '后台首页数据加载失败');
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    })();

    return () => {
      active = false;
    };
  }, []);

  const inviteCodePanel = getInviteCodePanelState(state.summary);

  async function handleRefreshInviteCode() {
    setCopyMessage('');
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

  return (
    <Stack spacing={3} sx={{ p: 2 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }}>
        <Stack spacing={1}>
          <Typography variant="h4" fontWeight={700}>
            YOYUZH Admin
          </Typography>
          <Typography color="text.secondary">
            这是嵌入现有门户应用的 react-admin 管理入口，当前通过 `/api/admin/**` 提供后台数据。
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

      <Grid container spacing={2}>
        {DASHBOARD_ITEMS.map((item) => (
          <Grid key={item.title} size={{ xs: 12, md: 4 }}>
            <Card variant="outlined">
              <CardContent>
                <Stack spacing={1.5}>
                  <Chip label={item.status} size="small" color="primary" sx={{ width: 'fit-content' }} />
                  <Typography variant="h6" fontWeight={600}>
                    {item.title}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {item.description}
                  </Typography>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="h6" fontWeight={600}>
                  当前管理员
                </Typography>
                <Typography color="text.secondary">
                  用户名：{session?.user.username ?? '-'}
                </Typography>
                <Typography color="text.secondary">
                  邮箱：{session?.user.email ?? '-'}
                </Typography>
                <Typography color="text.secondary">
                  用户 ID：{session?.user.id ?? '-'}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="h6" fontWeight={600}>
                  后台汇总
                </Typography>
                <Typography color="text.secondary">
                  用户总数：{state.summary?.totalUsers ?? 0}
                </Typography>
                <Typography color="text.secondary">
                  文件总数：{state.summary?.totalFiles ?? 0}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6" fontWeight={600}>
                  当前邀请码
                </Typography>
                <Typography color="text.secondary">
                  注册成功一次后会自动刷新，后台展示的始终是下一次可用的邀请码。
                </Typography>
                <Typography
                  component="code"
                  sx={{
                    display: 'inline-block',
                    width: 'fit-content',
                    px: 1.5,
                    py: 1,
                    borderRadius: 1,
                    backgroundColor: 'action.hover',
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                    fontSize: '0.95rem',
                  }}
                >
                  {inviteCodePanel.inviteCode}
                </Typography>
                <Stack direction="row" spacing={1}>
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
                    disabled={loading}
                  >
                    刷新
                  </Button>
                </Stack>
                {copyMessage && <Alert severity="success">{copyMessage}</Alert>}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}
