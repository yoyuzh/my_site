import React, { useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  FormControl,
  InputAdornment,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Edit2, Filter, Lock, Search, Trash2, UserPlus } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';
import type { AdminColumn } from '../../components/admin/AdminDataTable';
import AdminConfirmDialog from '../../components/admin/AdminConfirmDialog';
import AdminDataTable from '../../components/admin/AdminDataTable';
import AdminFilterBar from '../../components/admin/AdminFilterBar';
import AdminPage from '../../components/admin/AdminPage';
import AdminStatusBadge from '../../components/admin/AdminStatusBadge';
import { useAdminUsers } from '../../api/queries';
import type { AdminUser as AdminUserRecord } from '../../api/types';
import {
  resetAdminUserPassword,
  updateAdminUserBanned,
  updateAdminUserMaxUploadSize,
  updateAdminUserPassword,
  updateAdminUserRole,
  updateAdminUserStorageQuota,
} from '../../api/mutations';

const roleLabels: Record<AdminUserRecord['role'], string> = {
  ADMIN: '管理员',
  MODERATOR: '协管员',
  USER: '普通用户',
};

const roleTones: Record<AdminUserRecord['role'], 'danger' | 'warning' | 'neutral'> = {
  ADMIN: 'danger',
  MODERATOR: 'warning',
  USER: 'neutral',
};

const roleOptions: AdminUserRecord['role'][] = ['USER', 'MODERATOR', 'ADMIN'];
const roleInputLabels: Record<string, AdminUserRecord['role']> = {
  USER: 'USER',
  普通用户: 'USER',
  MODERATOR: 'MODERATOR',
  协管员: 'MODERATOR',
  ADMIN: 'ADMIN',
  管理员: 'ADMIN',
};

function toPositiveNumber(value: string | null) {
  if (value == null || value.trim() === '') {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

type UserConfirmState =
  | { type: 'ban'; user: AdminUserRecord }
  | { type: 'resetPassword'; user: AdminUserRecord }
  | null;

const AdminUser: React.FC = () => {
  const [showFilters, setShowFilters] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [searchDraft, setSearchDraft] = useState('');
  const [query, setQuery] = useState('');
  const [statusMessage, setStatusMessage] = useState('');
  const [confirmState, setConfirmState] = useState<UserConfirmState>(null);
  const [isConfirmSubmitting, setIsConfirmSubmitting] = useState(false);
  const { data, isLoading, isError, refetch } = useAdminUsers({ page, page_size: pageSize, query });

  async function runAction(action: () => Promise<unknown>, successMessage: string) {
    setStatusMessage('');
    try {
      await action();
      setStatusMessage(successMessage);
      await refetch();
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '操作失败');
    }
  }

  function applySearch() {
    setPage(1);
    setQuery(searchDraft.trim());
  }

  function resetSearch() {
    setSearchDraft('');
    setQuery('');
    setPage(1);
  }

  async function handleConfirm() {
    if (!confirmState) {
      return;
    }

    setIsConfirmSubmitting(true);
    try {
      if (confirmState.type === 'ban') {
        await runAction(
          () => updateAdminUserBanned(confirmState.user.id, !confirmState.user.banned),
          confirmState.user.banned ? '用户已解封' : '用户已封禁',
        );
      } else {
        await runAction(async () => {
          const result = await resetAdminUserPassword(confirmState.user.id);
          window.alert(`新密码：${result.newPassword}`);
        }, '密码已重置');
      }
      setConfirmState(null);
    } finally {
      setIsConfirmSubmitting(false);
    }
  }

  const columns = useMemo<AdminColumn<AdminUserRecord>[]>(
    () => [
      {
        id: 'select',
        header: '',
        accessor: () => <input type="checkbox" className="rounded border-gray-300 text-brand-light focus:ring-brand-light cursor-pointer" />,
      },
      {
        id: 'username',
        header: '用户名',
        accessor: (user) => (
          <Stack spacing={0.5}>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>
              {user.username}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              ID #{user.id}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'email',
        header: '邮箱',
        accessor: (user) => (
          <Stack spacing={0.5}>
            <Typography variant="body2">{user.email}</Typography>
            <Typography variant="caption" color="text.secondary">
              {user.phoneNumber || '无手机号'}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'role',
        header: '用户组',
        accessor: (user) => <AdminStatusBadge label={roleLabels[user.role]} tone={roleTones[user.role]} />,
      },
      {
        id: 'status',
        header: '状态',
        accessor: (user) => (
          <AdminStatusBadge label={user.banned ? '已封禁' : '正常'} tone={user.banned ? 'danger' : 'success'} />
        ),
      },
      {
        id: 'actions',
        header: '操作',
        accessor: (user) => (
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', flexWrap: 'wrap', gap: 0.5 }}>
            <Button
              size="small"
              color="inherit"
              onClick={() => {
                const nextRoleInput = window.prompt('输入新角色：普通用户 / 协管员 / 管理员', roleLabels[user.role])?.trim();
                if (!nextRoleInput) {
                  return;
                }
                const nextRole = roleInputLabels[nextRoleInput] ?? roleInputLabels[nextRoleInput.toUpperCase()];
                if (!nextRole || !roleOptions.includes(nextRole)) {
                  setStatusMessage('角色只能是普通用户、协管员或管理员');
                  return;
                }
                void runAction(() => updateAdminUserRole(user.id, nextRole), '角色已更新');
              }}
            >
              <Edit2 size={16} />
            </Button>
            <Button
              size="small"
              color="warning"
              onClick={() => {
                const newPassword = window.prompt(`输入 ${user.username} 的新密码`);
                if (!newPassword) {
                  return;
                }
                void runAction(() => updateAdminUserPassword(user.id, newPassword), '密码已更新');
              }}
            >
              <Lock size={16} />
            </Button>
            <Button size="small" color="inherit" onClick={() => setConfirmState({ type: 'ban', user })}>
              {user.banned ? '解封' : '封禁'}
            </Button>
            <Button size="small" color="inherit" onClick={() => setConfirmState({ type: 'resetPassword', user })}>
              重置
            </Button>
            <Button
              size="small"
              color="inherit"
              onClick={() => {
                const quota = toPositiveNumber(window.prompt('输入存储容量字节数', String(user.storageQuotaBytes)));
                if (quota == null) {
                  setStatusMessage('容量必须是正数');
                  return;
                }
                void runAction(() => updateAdminUserStorageQuota(user.id, quota), '存储容量已更新');
              }}
            >
              容量
            </Button>
            <Button
              size="small"
              color="inherit"
              onClick={() => {
                const maxUpload = toPositiveNumber(window.prompt('输入最大上传字节数', String(user.maxUploadSizeBytes)));
                if (maxUpload == null) {
                  setStatusMessage('最大上传大小必须是正数');
                  return;
                }
                void runAction(() => updateAdminUserMaxUploadSize(user.id, maxUpload), '最大上传大小已更新');
              }}
            >
              上传
            </Button>
            <Button size="small" color="error" disabled title="后端暂未提供删除用户接口">
              <Trash2 size={16} />
            </Button>
          </Box>
        ),
        className: 'text-right',
      },
    ],
    [],
  );

  const confirmTitle = confirmState?.type === 'ban'
    ? `${confirmState.user.banned ? '解封' : '封禁'}用户`
    : '重置密码';
  const confirmDescription = confirmState?.type === 'ban'
    ? `确认${confirmState.user.banned ? '解封' : '封禁'}用户 ${confirmState.user.username}？`
    : confirmState
      ? `确认重置 ${confirmState.user.username} 的密码？`
      : '';
  const confirmLabel = confirmState?.type === 'ban'
    ? (confirmState.user.banned ? '确认解封' : '确认封禁')
    : '确认重置';

  return (
    <AdminLayout title="用户管理">
      <AdminPage
        title="用户管理"
        description="治理用户角色、账号状态与单用户容量限制。"
        isLoading={isLoading}
        isError={isError}
        errorText="用户列表加载失败。"
        toolbar={
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
            <Button
              variant="contained"
              startIcon={<UserPlus size={16} />}
              disabled
              title="后端暂未提供创建用户接口"
            >
              添加用户
            </Button>
            <Button variant="outlined" color="error" disabled title="后端暂未提供批量删除用户接口">
              批量删除
            </Button>
          </Stack>
        }
      >
        <Stack spacing={2}>
          <AdminFilterBar
            actions={
              <Button
                variant={showFilters ? 'contained' : 'outlined'}
                startIcon={<Filter size={16} />}
                onClick={() => setShowFilters((value) => !value)}
              >
                高级筛选
              </Button>
            }
            summary={`共 ${data?.pagination?.total_items || 0} 条记录`}
          >
            <TextField
              size="small"
              placeholder="搜索用户名或邮箱..."
              value={searchDraft}
              onChange={(event) => setSearchDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  applySearch();
                }
              }}
              sx={{ minWidth: { xs: '100%', md: 280 } }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <Search size={16} />
                  </InputAdornment>
                ),
              }}
            />
            <Button variant="contained" onClick={applySearch}>
              搜索
            </Button>
          </AdminFilterBar>

          {showFilters ? (
            <AdminFilterBar
              actions={
                <Stack direction="row" spacing={1}>
                  <Button variant="outlined" onClick={resetSearch}>
                    重置
                  </Button>
                  <Button variant="contained" onClick={applySearch}>
                    应用筛选
                  </Button>
                </Stack>
              }
            >
              <FormControl size="small" sx={{ minWidth: 180 }}>
                <Select disabled displayEmpty value="" title="当前用户列表接口暂未提供用户组筛选">
                  <MenuItem value="">全部用户组</MenuItem>
                </Select>
              </FormControl>
              <FormControl size="small" sx={{ minWidth: 180 }}>
                <Select disabled displayEmpty value="" title="当前用户列表接口暂未提供账号状态筛选">
                  <MenuItem value="">全部状态</MenuItem>
                </Select>
              </FormControl>
            </AdminFilterBar>
          ) : null}

          {statusMessage ? <Alert severity="info">{statusMessage}</Alert> : null}

          <AdminDataTable
            rows={data?.items || []}
            columns={columns}
            getRowKey={(user) => user.id}
            emptyText="暂无用户数据"
          />

          <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 3, p: 2 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }}>
              <Stack direction="row" spacing={2} alignItems="center">
                <Typography variant="body2" color="text.secondary">
                  共 {data?.pagination?.total_items || 0} 条记录
                </Typography>
                <FormControl size="small" sx={{ minWidth: 120 }}>
                  <Select value={String(pageSize)} onChange={(event) => { setPageSize(Number(event.target.value)); setPage(1); }}>
                    <MenuItem value="10">10 条/页</MenuItem>
                    <MenuItem value="20">20 条/页</MenuItem>
                    <MenuItem value="50">50 条/页</MenuItem>
                  </Select>
                </FormControl>
              </Stack>
              <Stack direction="row" spacing={1}>
                <Button variant="outlined" disabled={page <= 1} onClick={() => setPage(page - 1)}>
                  上一页
                </Button>
                <Button variant="contained" disableElevation>
                  {page}
                </Button>
                <Button
                  variant="outlined"
                  disabled={!data?.pagination?.total_pages || page >= data.pagination.total_pages}
                  onClick={() => setPage(page + 1)}
                >
                  下一页
                </Button>
              </Stack>
            </Stack>
          </Paper>
        </Stack>
      </AdminPage>

      <AdminConfirmDialog
        open={Boolean(confirmState)}
        title={confirmTitle}
        description={confirmDescription}
        confirmLabel={confirmLabel}
        danger={confirmState?.type === 'ban' ? !confirmState.user.banned : false}
        isSubmitting={isConfirmSubmitting}
        onConfirm={() => void handleConfirm()}
        onClose={() => setConfirmState(null)}
      />
    </AdminLayout>
  );
};

export default AdminUser;
