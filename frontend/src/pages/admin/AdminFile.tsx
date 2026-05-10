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
import { FileKey, Filter, Import, Search, Trash2 } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';
import type { AdminColumn } from '../../components/admin/AdminDataTable';
import AdminConfirmDialog from '../../components/admin/AdminConfirmDialog';
import AdminDataTable from '../../components/admin/AdminDataTable';
import AdminFilterBar from '../../components/admin/AdminFilterBar';
import AdminPage from '../../components/admin/AdminPage';
import AdminStatusBadge from '../../components/admin/AdminStatusBadge';
import { useAdminFiles } from '../../api/queries';
import { deleteAdminFile } from '../../api/mutations';
import { formatBytes, formatDateTime } from '../../lib/format';
import type { AdminFile as AdminFileItem } from '../../api/types';

function renderFileKind(file: AdminFileItem) {
  if (file.directory) {
    return '目录';
  }
  if (file.contentType) {
    return file.contentType;
  }
  return '未知类型';
}

const AdminFile: React.FC = () => {
  const [showFilters, setShowFilters] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [searchDraft, setSearchDraft] = useState('');
  const [query, setQuery] = useState('');
  const [ownerDraft, setOwnerDraft] = useState('');
  const [ownerQuery, setOwnerQuery] = useState('');
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [fileToDelete, setFileToDelete] = useState<AdminFileItem | null>(null);
  const [isDeleteSubmitting, setIsDeleteSubmitting] = useState(false);
  const { data, isLoading, isError, refetch } = useAdminFiles({
    page,
    page_size: pageSize,
    query,
    ownerQuery,
  });

  function applyFilters() {
    setQuery(searchDraft.trim());
    setOwnerQuery(ownerDraft.trim());
    setPage(1);
  }

  function resetFilters() {
    setSearchDraft('');
    setQuery('');
    setOwnerDraft('');
    setOwnerQuery('');
    setPage(1);
  }

  async function handleDelete() {
    if (!fileToDelete) {
      return;
    }

    setIsDeleteSubmitting(true);
    try {
      await deleteAdminFile(fileToDelete.id);
      setStatusMessage(`已删除文件：${fileToDelete.filename}`);
      await refetch();
      setFileToDelete(null);
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '删除文件失败');
    } finally {
      setIsDeleteSubmitting(false);
    }
  }

  const columns = useMemo<AdminColumn<AdminFileItem>[]>(
    () => [
      {
        id: 'select',
        header: '',
        accessor: () => <input type="checkbox" className="rounded border-gray-300 text-brand-light focus:ring-brand-light cursor-pointer" />,
      },
      {
        id: 'id',
        header: '#',
        accessor: (file) => <Typography variant="body2" color="text.secondary">#{file.id}</Typography>,
      },
      {
        id: 'file',
        header: '文件',
        accessor: (file) => (
          <Stack direction="row" spacing={1.5} alignItems="center">
            <FileKey size={16} />
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body2" sx={{ fontWeight: 700 }} noWrap>
                {file.filename}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {file.directory ? '目录条目' : '文件条目'}
              </Typography>
            </Box>
          </Stack>
        ),
      },
      {
        id: 'path',
        header: '路径 / 类型',
        accessor: (file) => (
          <Stack spacing={0.5}>
            <Typography variant="body2" noWrap>
              {file.path}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {renderFileKind(file)}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'size',
        header: '大小',
        accessor: (file) => <Typography variant="body2">{file.directory ? '-' : formatBytes(file.size)}</Typography>,
      },
      {
        id: 'owner',
        header: '所属用户',
        accessor: (file) => (
          <Stack spacing={0.5}>
            <Typography variant="body2">{file.ownerUsername || '未知用户'}</Typography>
            <Typography variant="caption" color="text.secondary">
              {file.ownerEmail || '无邮箱'}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'status',
        header: '状态',
        accessor: (file) => (
          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
            <AdminStatusBadge label={file.favorite ? '已收藏' : '未收藏'} tone={file.favorite ? 'warning' : 'neutral'} />
            <AdminStatusBadge label={file.thumbnailAvailable ? '有缩略图' : '无缩略图'} tone={file.thumbnailAvailable ? 'success' : 'neutral'} />
          </Stack>
        ),
      },
      {
        id: 'createdAt',
        header: '创建时间',
        accessor: (file) => <Typography variant="body2">{formatDateTime(file.createdAt)}</Typography>,
      },
      {
        id: 'actions',
        header: '操作',
        accessor: (file) => (
          <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button size="small" color="error" onClick={() => setFileToDelete(file)}>
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
    <AdminLayout title="物理文件">
      <AdminPage
        title="物理文件"
        description="查看文件条目、所属用户、收藏与缩略图状态，并执行治理侧删除。"
        isLoading={isLoading}
        isError={isError}
        errorText="文件列表加载失败。"
        toolbar={
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
            <Button variant="outlined" startIcon={<Import size={16} />} disabled title="后端暂未提供导入外部目录接口">
              导入外部目录
            </Button>
            <Button variant="outlined" color="error" disabled title="后端暂未提供批量删除文件接口">
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
                筛选
              </Button>
            }
            summary={`共 ${data?.pagination?.total_items || 0} 条记录`}
          >
            <TextField
              size="small"
              placeholder="搜索文件名..."
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
              <FormControl size="small" sx={{ minWidth: 160 }}>
                <Select disabled displayEmpty value="" title="当前文件列表接口暂未提供存储策略筛选">
                  <MenuItem value="">全部策略</MenuItem>
                </Select>
              </FormControl>
              <TextField
                size="small"
                placeholder="输入用户名或邮箱"
                value={ownerDraft}
                onChange={(event) => setOwnerDraft(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    applyFilters();
                  }
                }}
                sx={{ minWidth: 200 }}
              />
              <FormControl size="small" sx={{ minWidth: 140 }}>
                <Select disabled displayEmpty value="" title="当前文件列表接口暂未提供直链筛选">
                  <MenuItem value="">包含直链</MenuItem>
                </Select>
              </FormControl>
              <FormControl size="small" sx={{ minWidth: 140 }}>
                <Select disabled displayEmpty value="" title="当前文件列表接口暂未提供分享状态筛选">
                  <MenuItem value="">已分享</MenuItem>
                </Select>
              </FormControl>
            </AdminFilterBar>
          ) : null}

          {statusMessage ? <Alert severity="info">{statusMessage}</Alert> : null}

          <AdminDataTable
            rows={data?.items || []}
            columns={columns}
            getRowKey={(file) => file.id}
            emptyText="暂无文件记录"
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
        open={Boolean(fileToDelete)}
        title="删除文件"
        description={fileToDelete ? `确认删除「${fileToDelete.filename}」？此操作会删除用户文件记录。` : ''}
        confirmLabel="确认删除"
        danger
        isSubmitting={isDeleteSubmitting}
        onConfirm={() => void handleDelete()}
        onClose={() => setFileToDelete(null)}
      />
    </AdminLayout>
  );
};

export default AdminFile;
