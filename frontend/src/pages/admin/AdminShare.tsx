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
import { Filter, Link as LinkIcon, Search, Share2, Trash2 } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';
import type { AdminColumn } from '../../components/admin/AdminDataTable';
import AdminConfirmDialog from '../../components/admin/AdminConfirmDialog';
import AdminDataTable from '../../components/admin/AdminDataTable';
import AdminFilterBar from '../../components/admin/AdminFilterBar';
import AdminPage from '../../components/admin/AdminPage';
import AdminStatusBadge from '../../components/admin/AdminStatusBadge';
import { useAdminShares } from '../../api/queries';
import { deleteAdminShare } from '../../api/mutations';
import { formatDateTime } from '../../lib/format';
import { buildFullShareUrl } from '../../lib/shares';
import type { AdminShare as AdminShareItem } from '../../api/types';

function formatShareLimit(value: number | null) {
  return value == null ? '不限' : String(value);
}

const AdminShare: React.FC = () => {
  const [showFilters, setShowFilters] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [searchDraft, setSearchDraft] = useState('');
  const [fileName, setFileName] = useState('');
  const [ownerDraft, setOwnerDraft] = useState('');
  const [userQuery, setUserQuery] = useState('');
  const [expiredDraft, setExpiredDraft] = useState('');
  const [expired, setExpired] = useState<boolean | undefined>(undefined);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [shareToDelete, setShareToDelete] = useState<AdminShareItem | null>(null);
  const [isDeleteSubmitting, setIsDeleteSubmitting] = useState(false);
  const { data, isLoading, isError, refetch } = useAdminShares({
    page,
    page_size: pageSize,
    fileName,
    userQuery,
    expired,
  });

  function applyFilters() {
    setFileName(searchDraft.trim());
    setUserQuery(ownerDraft.trim());
    setExpired(expiredDraft === '' ? undefined : expiredDraft === 'true');
    setPage(1);
  }

  function resetFilters() {
    setSearchDraft('');
    setFileName('');
    setOwnerDraft('');
    setUserQuery('');
    setExpiredDraft('');
    setExpired(undefined);
    setPage(1);
  }

  async function copyShareLink(share: AdminShareItem) {
    const url = buildFullShareUrl(share.token);
    try {
      await window.navigator.clipboard.writeText(url);
      setStatusMessage(`已复制分享链接：${url}`);
    } catch {
      window.prompt('复制分享链接', url);
    }
  }

  async function handleDelete() {
    if (!shareToDelete) {
      return;
    }

    const label = shareToDelete.shareName || shareToDelete.fileName || shareToDelete.token;
    setIsDeleteSubmitting(true);
    try {
      await deleteAdminShare(shareToDelete.id);
      setStatusMessage(`已取消分享：${label}`);
      await refetch();
      setShareToDelete(null);
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '取消分享失败');
    } finally {
      setIsDeleteSubmitting(false);
    }
  }

  const columns = useMemo<AdminColumn<AdminShareItem>[]>(
    () => [
      {
        id: 'select',
        header: '',
        accessor: () => <input type="checkbox" className="rounded border-gray-300 text-brand-light focus:ring-brand-light cursor-pointer" />,
      },
      {
        id: 'id',
        header: '#',
        accessor: (share) => <Typography variant="body2" color="text.secondary">#{share.id}</Typography>,
      },
      {
        id: 'share',
        header: '分享',
        accessor: (share) => (
          <Stack direction="row" spacing={1.5} alignItems="center">
            <Share2 size={16} />
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body2" sx={{ fontWeight: 700 }} noWrap>
                {share.shareName || share.fileName || share.token}
              </Typography>
              <Typography variant="caption" color="text.secondary" noWrap>
                {share.filePath || share.token}
              </Typography>
            </Box>
          </Stack>
        ),
      },
      {
        id: 'owner',
        header: '所属用户',
        accessor: (share) => (
          <Stack spacing={0.5}>
            <Typography variant="body2">{share.ownerUsername || 'Unknown'}</Typography>
            <Typography variant="caption" color="text.secondary">
              {share.ownerEmail || '无邮箱'}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'views',
        header: '浏览/下载',
        accessor: (share) => (
          <Stack spacing={0.5}>
            <Typography variant="body2">
              {share.viewCount} / {share.downloadCount}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              上限 {formatShareLimit(share.maxDownloads)}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'status',
        header: '状态',
        accessor: (share) => (
          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
            <AdminStatusBadge label={share.passwordProtected ? '受密码保护' : '公开访问'} tone={share.passwordProtected ? 'warning' : 'neutral'} />
            <AdminStatusBadge label={share.expired ? '已过期' : '有效'} tone={share.expired ? 'danger' : 'success'} />
            <AdminStatusBadge label={share.allowDownload ? '可下载' : '禁下载'} tone={share.allowDownload ? 'info' : 'neutral'} />
            <AdminStatusBadge label={share.allowImport ? '可导入' : '禁导入'} tone={share.allowImport ? 'info' : 'neutral'} />
          </Stack>
        ),
      },
      {
        id: 'expiresAt',
        header: '过期时间',
        accessor: (share) => <Typography variant="body2">{share.expiresAt ? formatDateTime(share.expiresAt) : '永久有效'}</Typography>,
      },
      {
        id: 'actions',
        header: '操作',
        accessor: (share) => (
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
            <Button size="small" color="inherit" onClick={() => void copyShareLink(share)}>
              <LinkIcon size={16} />
            </Button>
            <Button size="small" color="error" onClick={() => setShareToDelete(share)}>
              <Trash2 size={16} />
            </Button>
          </Box>
        ),
        className: 'text-right',
      },
    ],
    [],
  );

  return (
    <AdminLayout title="分享管理">
      <AdminPage
        title="分享管理"
        description="治理分享状态、访问权限、过期与取消操作。"
        isLoading={isLoading}
        isError={isError}
        errorText="分享列表加载失败。"
        toolbar={
          <Button variant="outlined" color="error" disabled title="后端暂未提供批量删除分享接口">
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
              placeholder="搜索分享文件名..."
              value={searchDraft}
              onChange={(event) => setSearchDraft(event.target.value)}
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
                placeholder="输入创建者用户名或邮箱"
                value={ownerDraft}
                onChange={(event) => setOwnerDraft(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    applyFilters();
                  }
                }}
                sx={{ minWidth: 220 }}
              />
              <FormControl size="small" sx={{ minWidth: 160 }}>
                <Select value={expiredDraft} onChange={(event) => setExpiredDraft(event.target.value)}>
                  <MenuItem value="">全部状态</MenuItem>
                  <MenuItem value="false">正常</MenuItem>
                  <MenuItem value="true">已过期</MenuItem>
                </Select>
              </FormControl>
            </AdminFilterBar>
          ) : null}

          {statusMessage ? <Alert severity="info">{statusMessage}</Alert> : null}

          <AdminDataTable
            rows={data?.items || []}
            columns={columns}
            getRowKey={(share) => share.id}
            emptyText="暂无分享记录"
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
        open={Boolean(shareToDelete)}
        title="取消分享"
        description={shareToDelete ? `确认取消分享「${shareToDelete.shareName || shareToDelete.fileName || shareToDelete.token}」？` : ''}
        confirmLabel="确认取消"
        danger
        isSubmitting={isDeleteSubmitting}
        onConfirm={() => void handleDelete()}
        onClose={() => setShareToDelete(null)}
      />
    </AdminLayout>
  );
};

export default AdminShare;
