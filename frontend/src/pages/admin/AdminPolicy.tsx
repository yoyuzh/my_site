import React, { useState } from 'react';
import { Alert, Box, Button, Paper, Stack, Typography } from '@mui/material';
import { ArrowRight, Database, Edit2, Plus, Power, Trash2 } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';
import AdminPage from '../../components/admin/AdminPage';
import AdminStatusBadge from '../../components/admin/AdminStatusBadge';
import { useAdminPolicies } from '../../api/queries';
import {
  createAdminStoragePolicy,
  updateAdminStoragePolicy,
  updateAdminStoragePolicyStatus,
  type AdminStoragePolicyPayload,
} from '../../api/mutations';
import { formatBytes, formatDateTime } from '../../lib/format';
import type { AdminStoragePolicy, StoragePolicyCapabilities } from '../../api/types';

const DEFAULT_CAPABILITIES: StoragePolicyCapabilities = {
  directUpload: false,
  multipartUpload: false,
  signedDownloadUrl: false,
  serverProxyDownload: true,
  thumbnailNative: false,
  friendlyDownloadName: true,
  requiresCors: false,
  supportsInternalEndpoint: false,
  maxObjectSize: 1024 * 1024 * 1024,
};

function normalizeText(value: string | null | undefined) {
  const normalized = value?.trim();
  return normalized ? normalized : null;
}

function buildPayload(policy: Partial<AdminStoragePolicy>): AdminStoragePolicyPayload {
  const maxSizeBytes = policy.maxSizeBytes && policy.maxSizeBytes > 0
    ? policy.maxSizeBytes
    : DEFAULT_CAPABILITIES.maxObjectSize;
  const capabilities = policy.capabilities ?? {
    ...DEFAULT_CAPABILITIES,
    maxObjectSize: maxSizeBytes,
  };

  return {
    name: policy.name?.trim() || '未命名策略',
    type: policy.type || 'LOCAL',
    bucketName: normalizeText(policy.bucketName),
    endpoint: normalizeText(policy.endpoint),
    region: normalizeText(policy.region),
    privateBucket: policy.privateBucket ?? true,
    prefix: normalizeText(policy.prefix),
    credentialMode: policy.credentialMode || 'NONE',
    maxSizeBytes,
    capabilities: {
      ...capabilities,
      maxObjectSize: capabilities.maxObjectSize > 0 ? capabilities.maxObjectSize : maxSizeBytes,
    },
    enabled: policy.enabled ?? true,
  };
}

const AdminPolicy: React.FC = () => {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const { data, isLoading, isError, refetch } = useAdminPolicies({ page, page_size: pageSize });

  async function handleCreate() {
    const name = window.prompt('策略名称');
    if (!name?.trim()) {
      return;
    }
    const type = window.prompt('策略类型：LOCAL 或 S3_COMPATIBLE', 'LOCAL')?.trim() || 'LOCAL';
    if (type !== 'LOCAL' && type !== 'S3_COMPATIBLE') {
      setStatusMessage('策略类型只能是 LOCAL 或 S3_COMPATIBLE');
      return;
    }
    const maxSizeInput = window.prompt('最大对象大小（字节）', String(DEFAULT_CAPABILITIES.maxObjectSize));
    const maxSizeBytes = Number(maxSizeInput);
    if (!Number.isFinite(maxSizeBytes) || maxSizeBytes <= 0) {
      setStatusMessage('最大对象大小必须是正数');
      return;
    }

    const payload = buildPayload({
      name,
      type,
      maxSizeBytes,
      capabilities: {
        ...DEFAULT_CAPABILITIES,
        maxObjectSize: maxSizeBytes,
      },
      enabled: true,
    });

    try {
      await createAdminStoragePolicy(payload);
      setStatusMessage(`已创建存储策略：${name.trim()}`);
      await refetch();
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '创建存储策略失败');
    }
  }

  async function handleEdit(policy: AdminStoragePolicy) {
    const name = window.prompt('策略名称', policy.name);
    if (!name?.trim()) {
      return;
    }
    const bucketName = window.prompt('Bucket 名称（本地策略可留空）', policy.bucketName ?? '') ?? '';
    const endpoint = window.prompt('Endpoint（本地策略可留空）', policy.endpoint ?? '') ?? '';
    const region = window.prompt('Region（本地策略可留空）', policy.region ?? '') ?? '';
    const prefix = window.prompt('对象前缀（可留空）', policy.prefix ?? '') ?? '';
    const maxSizeInput = window.prompt('最大对象大小（字节）', String(policy.maxSizeBytes));
    const maxSizeBytes = Number(maxSizeInput);
    if (!Number.isFinite(maxSizeBytes) || maxSizeBytes <= 0) {
      setStatusMessage('最大对象大小必须是正数');
      return;
    }

    const payload = buildPayload({
      ...policy,
      name,
      bucketName,
      endpoint,
      region,
      prefix,
      maxSizeBytes,
      capabilities: {
        ...policy.capabilities,
        maxObjectSize: maxSizeBytes,
      },
    });

    try {
      await updateAdminStoragePolicy(policy.id, payload);
      setStatusMessage(`已更新存储策略：${name.trim()}`);
      await refetch();
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '更新存储策略失败');
    }
  }

  async function handleToggle(policy: AdminStoragePolicy) {
    try {
      await updateAdminStoragePolicyStatus(policy.id, !policy.enabled);
      setStatusMessage(`${policy.name} 已${policy.enabled ? '禁用' : '启用'}`);
      await refetch();
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '更新策略状态失败');
    }
  }

  return (
    <AdminLayout title="存储策略">
      <AdminPage
        title="存储策略"
        description="维护存储策略、对象大小上限与启停状态。"
        isLoading={isLoading}
        isError={isError}
        errorText="存储策略加载失败。"
        toolbar={
          <Button variant="contained" startIcon={<Plus size={16} />} onClick={() => void handleCreate()}>
            添加存储策略
          </Button>
        }
      >
        <Stack spacing={2}>
          {statusMessage ? <Alert severity="info">{statusMessage}</Alert> : null}

          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', md: 'repeat(2, minmax(0, 1fr))', xl: 'repeat(3, minmax(0, 1fr))' },
              gap: 2,
            }}
          >
            {(data?.items || []).map((policy: AdminStoragePolicy) => (
              <Paper
                key={policy.id}
                elevation={0}
                sx={{
                  border: '1px solid',
                  borderColor: 'divider',
                  borderRadius: 3,
                  bgcolor: 'background.paper',
                  display: 'flex',
                  flexDirection: 'column',
                  minHeight: '100%',
                }}
              >
                <Box sx={{ p: 2.5, flex: 1 }}>
                  <Stack direction="row" justifyContent="space-between" spacing={2} sx={{ mb: 2 }}>
                    <Box
                      sx={{
                        width: 44,
                        height: 44,
                        borderRadius: 2,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        bgcolor: 'action.hover',
                        color: 'primary.main',
                      }}
                    >
                      <Database size={22} />
                    </Box>
                    <Stack direction="row" spacing={0.5}>
                      <Button size="small" color="inherit" onClick={() => void handleEdit(policy)}>
                        <Edit2 size={16} />
                      </Button>
                      <Button size="small" color="error" disabled title="后端暂未提供删除存储策略接口">
                        <Trash2 size={16} />
                      </Button>
                    </Stack>
                  </Stack>

                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    {policy.name}
                  </Typography>
                  <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mt: 1.5, mb: 2 }}>
                    <AdminStatusBadge label={policy.type} tone="neutral" />
                    <AdminStatusBadge label={policy.defaultPolicy ? '默认策略' : policy.enabled ? '已启用' : '已禁用'} tone={policy.defaultPolicy ? 'info' : policy.enabled ? 'success' : 'warning'} />
                  </Stack>

                  <Stack spacing={1}>
                    <Typography variant="body2" color="text.secondary" sx={{ wordBreak: 'break-all' }}>
                      {policy.endpoint || policy.bucketName || policy.prefix || '本地默认存储'}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      最大对象：{formatBytes(policy.maxSizeBytes)}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      凭证模式：{policy.credentialMode}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      更新时间：{formatDateTime(policy.updatedAt)}
                    </Typography>
                  </Stack>
                </Box>

                <Stack
                  direction="row"
                  justifyContent="space-between"
                  alignItems="center"
                  spacing={1.5}
                  sx={{ px: 2.5, py: 2, borderTop: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}
                >
                  <Button size="small" color="inherit" endIcon={<ArrowRight size={16} />} onClick={() => void handleEdit(policy)}>
                    修改策略向导
                  </Button>
                  <Button
                    size="small"
                    variant={policy.enabled ? 'outlined' : 'contained'}
                    color={policy.enabled ? 'warning' : 'success'}
                    startIcon={<Power size={14} />}
                    onClick={() => void handleToggle(policy)}
                  >
                    {policy.enabled ? '禁用' : '启用'}
                  </Button>
                </Stack>
              </Paper>
            ))}
          </Box>

          <Paper elevation={0} sx={{ borderTop: '1px solid', borderColor: 'divider', p: 2 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }}>
              <Typography variant="body2" color="text.secondary">
                共 {data?.pagination?.total_items || 0} 条记录
              </Typography>
              <Stack direction="row" spacing={1}>
                <Button variant="outlined" onClick={() => { setPageSize(10); setPage(1); }} disabled={pageSize === 10}>
                  10 条/页
                </Button>
                <Button variant="outlined" onClick={() => { setPageSize(20); setPage(1); }} disabled={pageSize === 20}>
                  20 条/页
                </Button>
                <Button variant="outlined" onClick={() => { setPageSize(50); setPage(1); }} disabled={pageSize === 50}>
                  50 条/页
                </Button>
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
    </AdminLayout>
  );
};

export default AdminPolicy;
