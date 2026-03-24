import { useState } from 'react';
import { Button, Chip, Stack } from '@mui/material';
import {
  Datagrid,
  DateField,
  FunctionField,
  List,
  SearchInput,
  TextField,
  TopToolbar,
  RefreshButton,
  useNotify,
  useRefresh,
} from 'react-admin';

import { apiRequest } from '@/src/lib/api';
import type { AdminPasswordResetResponse, AdminUser, AdminUserRole } from '@/src/lib/types';

const USER_ROLE_OPTIONS: AdminUserRole[] = ['USER', 'MODERATOR', 'ADMIN'];

function formatLimitSize(bytes: number) {
  if (bytes <= 0) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / 1024 ** index;
  return `${value >= 10 || index === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[index]}`;
}

function parseLimitInput(value: string): number | null {
  const normalized = value.trim().toLowerCase();
  const matched = normalized.match(/^(\d+(?:\.\d+)?)\s*(b|kb|mb|gb|tb)?$/);
  if (!matched) {
    return null;
  }
  const amount = Number.parseFloat(matched[1] ?? '0');
  if (!Number.isFinite(amount) || amount <= 0) {
    return null;
  }
  const unit = matched[2] ?? 'b';
  const multiplier = unit === 'tb'
    ? 1024 ** 4
    : unit === 'gb'
      ? 1024 ** 3
      : unit === 'mb'
        ? 1024 ** 2
        : unit === 'kb'
          ? 1024
          : 1;
  return Math.floor(amount * multiplier);
}

function UsersListActions() {
  return (
    <TopToolbar>
      <RefreshButton />
    </TopToolbar>
  );
}

function AdminUserActions({ record }: { record: AdminUser }) {
  const notify = useNotify();
  const refresh = useRefresh();
  const [busy, setBusy] = useState(false);

  async function handleRoleAssign() {
    const input = window.prompt('请输入角色：USER / MODERATOR / ADMIN', record.role);
    if (!input) {
      return;
    }
    const role = input.trim().toUpperCase() as AdminUserRole;
    if (!USER_ROLE_OPTIONS.includes(role)) {
      notify('角色必须是 USER、MODERATOR 或 ADMIN', { type: 'warning' });
      return;
    }

    setBusy(true);
    try {
      await apiRequest(`/admin/users/${record.id}/role`, {
        method: 'PATCH',
        body: { role },
      });
      notify(`已将 ${record.username} 设为 ${role}`, { type: 'success' });
      refresh();
    } catch (error) {
      notify(error instanceof Error ? error.message : '角色更新失败', { type: 'error' });
    } finally {
      setBusy(false);
    }
  }

  async function handleToggleBan() {
    const nextBanned = !record.banned;
    const confirmed = window.confirm(
      nextBanned ? `确认封禁用户 ${record.username} 吗？` : `确认解封用户 ${record.username} 吗？`,
    );
    if (!confirmed) {
      return;
    }

    setBusy(true);
    try {
      await apiRequest(`/admin/users/${record.id}/status`, {
        method: 'PATCH',
        body: { banned: nextBanned },
      });
      notify(nextBanned ? '用户已封禁' : '用户已解封', { type: 'success' });
      refresh();
    } catch (error) {
      notify(error instanceof Error ? error.message : '状态更新失败', { type: 'error' });
    } finally {
      setBusy(false);
    }
  }

  async function handleSetPassword() {
    const newPassword = window.prompt(
      '请输入新密码。密码至少10位，且必须包含大写字母、小写字母、数字和特殊字符。',
    );
    if (!newPassword) {
      return;
    }

    setBusy(true);
    try {
      await apiRequest(`/admin/users/${record.id}/password`, {
        method: 'PUT',
        body: { newPassword },
      });
      notify('密码已更新，旧 refresh token 已失效', { type: 'success' });
    } catch (error) {
      notify(error instanceof Error ? error.message : '密码更新失败', { type: 'error' });
    } finally {
      setBusy(false);
    }
  }

  async function handleSetStorageQuota() {
    const input = window.prompt(
      `请输入新的存储上限（支持 B/KB/MB/GB/TB，当前 ${formatLimitSize(record.storageQuotaBytes)}）`,
      `${Math.floor(record.storageQuotaBytes / 1024 / 1024 / 1024)}GB`,
    );
    if (!input) {
      return;
    }
    const storageQuotaBytes = parseLimitInput(input);
    if (!storageQuotaBytes) {
      notify('输入格式不正确，请输入例如 20GB 或 21474836480', { type: 'warning' });
      return;
    }

    setBusy(true);
    try {
      await apiRequest(`/admin/users/${record.id}/storage-quota`, {
        method: 'PATCH',
        body: { storageQuotaBytes },
      });
      notify(`存储上限已更新为 ${formatLimitSize(storageQuotaBytes)}`, { type: 'success' });
      refresh();
    } catch (error) {
      notify(error instanceof Error ? error.message : '存储上限更新失败', { type: 'error' });
    } finally {
      setBusy(false);
    }
  }

  async function handleSetMaxUploadSize() {
    const input = window.prompt(
      `请输入单文件最大上传大小（支持 B/KB/MB/GB/TB，当前 ${formatLimitSize(record.maxUploadSizeBytes)}）`,
      `${Math.max(1, Math.floor(record.maxUploadSizeBytes / 1024 / 1024))}MB`,
    );
    if (!input) {
      return;
    }
    const maxUploadSizeBytes = parseLimitInput(input);
    if (!maxUploadSizeBytes) {
      notify('输入格式不正确，请输入例如 500MB 或 524288000', { type: 'warning' });
      return;
    }

    setBusy(true);
    try {
      await apiRequest(`/admin/users/${record.id}/max-upload-size`, {
        method: 'PATCH',
        body: { maxUploadSizeBytes },
      });
      notify(`单文件上限已更新为 ${formatLimitSize(maxUploadSizeBytes)}`, { type: 'success' });
      refresh();
    } catch (error) {
      notify(error instanceof Error ? error.message : '单文件上限更新失败', { type: 'error' });
    } finally {
      setBusy(false);
    }
  }

  async function handleResetPassword() {
    const confirmed = window.confirm(`确认重置 ${record.username} 的密码吗？`);
    if (!confirmed) {
      return;
    }

    setBusy(true);
    try {
      const result = await apiRequest<AdminPasswordResetResponse>(`/admin/users/${record.id}/password/reset`, {
        method: 'POST',
      });
      notify('已生成临时密码，请立即复制并安全发送给用户', { type: 'success' });
      window.prompt(`用户 ${record.username} 的临时密码如下，请复制保存`, result.temporaryPassword);
    } catch (error) {
      notify(error instanceof Error ? error.message : '密码重置失败', { type: 'error' });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
      <Button size="small" variant="outlined" disabled={busy} onClick={() => void handleRoleAssign()}>
        角色分配
      </Button>
      <Button size="small" variant="outlined" disabled={busy} onClick={() => void handleSetPassword()}>
        修改密码
      </Button>
      <Button size="small" variant="outlined" disabled={busy} onClick={() => void handleResetPassword()}>
        重置密码
      </Button>
      <Button size="small" variant="outlined" disabled={busy} onClick={() => void handleSetStorageQuota()}>
        存储上限
      </Button>
      <Button size="small" variant="outlined" disabled={busy} onClick={() => void handleSetMaxUploadSize()}>
        单文件上限
      </Button>
      <Button
        size="small"
        variant={record.banned ? 'contained' : 'outlined'}
        color={record.banned ? 'success' : 'warning'}
        disabled={busy}
        onClick={() => void handleToggleBan()}
      >
        {record.banned ? '解封' : '封禁'}
      </Button>
    </Stack>
  );
}

export function PortalAdminUsersList() {
  return (
    <List
      actions={<UsersListActions />}
      filters={[<SearchInput key="query" source="query" alwaysOn placeholder="搜索用户名、邮箱或手机号" />]}
      perPage={25}
      resource="users"
      title="用户管理"
      sort={{ field: 'createdAt', order: 'DESC' }}
    >
      <Datagrid bulkActionButtons={false} rowClick={false}>
        <TextField source="id" label="ID" />
        <TextField source="username" label="用户名" />
        <TextField source="email" label="邮箱" />
        <TextField source="phoneNumber" label="手机号" emptyText="-" />
        <FunctionField<AdminUser>
          label="存储上限"
          render={(record) => formatLimitSize(record.storageQuotaBytes)}
        />
        <FunctionField<AdminUser>
          label="单文件上限"
          render={(record) => formatLimitSize(record.maxUploadSizeBytes)}
        />
        <FunctionField<AdminUser>
          label="角色"
          render={(record) => <Chip label={record.role} size="small" color={record.role === 'ADMIN' ? 'primary' : 'default'} />}
        />
        <FunctionField<AdminUser>
          label="状态"
          render={(record) => (
            <Chip
              label={record.banned ? '已封禁' : '正常'}
              size="small"
              color={record.banned ? 'warning' : 'success'}
              variant={record.banned ? 'filled' : 'outlined'}
            />
          )}
        />
        <DateField source="createdAt" label="创建时间" showTime />
        <FunctionField<AdminUser> label="操作" render={(record) => <AdminUserActions record={record} />} />
      </Datagrid>
    </List>
  );
}
