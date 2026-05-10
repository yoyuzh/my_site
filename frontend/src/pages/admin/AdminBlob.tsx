import React, { useMemo, useState } from 'react';
import {
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
import { Filter, FolderKey, Search, Trash2 } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';
import type { AdminColumn } from '../../components/admin/AdminDataTable';
import AdminDataTable from '../../components/admin/AdminDataTable';
import AdminFilterBar from '../../components/admin/AdminFilterBar';
import AdminPage from '../../components/admin/AdminPage';
import AdminStatusBadge from '../../components/admin/AdminStatusBadge';
import { useAdminBlobs } from '../../api/queries';
import { formatBytes, formatDateTime } from '../../lib/format';
import type { AdminFileBlob } from '../../api/types';

const AdminBlob: React.FC = () => {
  const [showFilters, setShowFilters] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [objectKeyDraft, setObjectKeyDraft] = useState('');
  const [objectKey, setObjectKey] = useState('');
  const [userDraft, setUserDraft] = useState('');
  const [userQuery, setUserQuery] = useState('');
  const [policyDraft, setPolicyDraft] = useState('');
  const [storagePolicyId, setStoragePolicyId] = useState<number | undefined>(undefined);
  const { data, isLoading, isError } = useAdminBlobs({
    page,
    page_size: pageSize,
    objectKey,
    userQuery,
    storagePolicyId,
  });

  function applyFilters() {
    const parsedPolicy = Number(policyDraft);
    setObjectKey(objectKeyDraft.trim());
    setUserQuery(userDraft.trim());
    setStoragePolicyId(policyDraft.trim() && Number.isFinite(parsedPolicy) ? parsedPolicy : undefined);
    setPage(1);
  }

  function resetFilters() {
    setObjectKeyDraft('');
    setObjectKey('');
    setUserDraft('');
    setUserQuery('');
    setPolicyDraft('');
    setStoragePolicyId(undefined);
    setPage(1);
  }

  const columns = useMemo<AdminColumn<AdminFileBlob>[]>(
    () => [
      {
        id: 'select',
        header: '',
        accessor: () => <input type="checkbox" className="rounded border-gray-300 text-brand-light focus:ring-brand-light cursor-pointer" />,
      },
      {
        id: 'entityId',
        header: '#',
        accessor: (blob) => <Typography variant="body2" color="text.secondary">#{blob.entityId}</Typography>,
      },
      {
        id: 'storagePolicy',
        header: '存储策略',
        accessor: (blob) => (
          <AdminStatusBadge
            label={blob.storagePolicyId == null ? '未绑定策略' : `#${blob.storagePolicyId}`}
            tone={blob.storagePolicyId == null ? 'warning' : 'neutral'}
          />
        ),
      },
      {
        id: 'objectKey',
        header: '物理文件路径/哈希',
        accessor: (blob) => (
          <Stack direction="row" spacing={1.5} alignItems="center">
            <FolderKey size={16} />
            <Typography variant="body2" noWrap>
              {blob.objectKey || '无对象键'}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'size',
        header: '大小',
        accessor: (blob) => (
          <Stack spacing={0.5}>
            <Typography variant="body2">{blob.size == null ? '-' : formatBytes(blob.size)}</Typography>
            <Typography variant="caption" color="text.secondary">
              {blob.contentType || blob.entityType}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'references',
        header: '引用计数',
        accessor: (blob) => (
          <Stack spacing={0.5}>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>
              {blob.referenceCount ?? blob.linkedStoredFileCount}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {blob.createdAt ? formatDateTime(blob.createdAt) : '-'}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'flags',
        header: '状态',
        accessor: (blob) => (
          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
            <AdminStatusBadge label={blob.blobMissing ? '内容块缺失' : '内容块正常'} tone={blob.blobMissing ? 'danger' : 'success'} />
            <AdminStatusBadge label={blob.orphanRisk ? '孤儿风险' : '引用正常'} tone={blob.orphanRisk ? 'warning' : 'neutral'} />
            <AdminStatusBadge label={blob.referenceMismatch ? '引用不一致' : '引用一致'} tone={blob.referenceMismatch ? 'warning' : 'neutral'} />
          </Stack>
        ),
      },
      {
        id: 'actions',
        header: '操作',
        accessor: () => (
          <Button size="small" color="error" disabled title="后端暂未提供删除文件实体接口">
            <Trash2 size={16} />
          </Button>
        ),
        className: 'text-right',
      },
    ],
    [],
  );

  return (
    <AdminLayout title="文件实体记录">
      <AdminPage
        title="文件实体记录"
        description="查看文件实体与底层内容块关联、引用风险和策略归属。"
        isLoading={isLoading}
        isError={isError}
        errorText="文件实体列表加载失败。"
        toolbar={
          <Button variant="outlined" color="error" disabled title="后端暂未提供批量删除文件实体接口">
            批量删除
          </Button>
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
                筛选
              </Button>
            }
            summary={`共 ${data?.pagination?.total_items || 0} 条记录`}
          >
            <TextField
              size="small"
              placeholder="搜索对象键..."
              value={objectKeyDraft}
              onChange={(event) => setObjectKeyDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  applyFilters();
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
            <Button variant="contained" onClick={applyFilters}>
              搜索
            </Button>
          </AdminFilterBar>

          {showFilters ? (
            <AdminFilterBar
              actions={
                <Stack direction="row" spacing={1}>
                  <Button variant="outlined" onClick={resetFilters}>
                    重置
                  </Button>
                  <Button variant="contained" onClick={applyFilters}>
                    应用
                  </Button>
                </Stack>
              }
            >
              <TextField
                size="small"
                type="number"
                inputProps={{ min: 1 }}
                placeholder="输入策略编号"
                value={policyDraft}
                onChange={(event) => setPolicyDraft(event.target.value)}
                sx={{ minWidth: 180 }}
              />
              <TextField
                size="small"
                placeholder="输入用户名或邮箱"
                value={userDraft}
                onChange={(event) => setUserDraft(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    applyFilters();
                  }
                }}
                sx={{ minWidth: 220 }}
              />
            </AdminFilterBar>
          ) : null}

          <AdminDataTable
            rows={data?.items || []}
            columns={columns}
            getRowKey={(blob) => `${blob.entityType}-${blob.entityId}-${blob.blobId}`}
            emptyText="暂无文件实体记录"
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
    </AdminLayout>
  );
};

export default AdminBlob;
