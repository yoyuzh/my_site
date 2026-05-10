import React, { useMemo, useState } from 'react';
import {
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
import { Filter, Search, Shield } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';
import type { AdminColumn } from '../../components/admin/AdminDataTable';
import AdminDataTable from '../../components/admin/AdminDataTable';
import AdminFilterBar from '../../components/admin/AdminFilterBar';
import AdminPage from '../../components/admin/AdminPage';
import AdminStatusBadge from '../../components/admin/AdminStatusBadge';
import { localizeAuditAction, localizeAuditTarget } from '../../components/admin/adminDisplayText';
import { useAdminAudits } from '../../api/queries';
import type { AdminAuditLog } from '../../api/types';
import { formatDateTime } from '../../lib/format';

function auditTone(actionType: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  const normalized = actionType.toUpperCase();
  if (normalized.includes('DELETE') || normalized.includes('DISABLE') || normalized.includes('RESET')) {
    return 'danger';
  }
  if (normalized.includes('ROLLBACK') || normalized.includes('MIGRATION')) {
    return 'warning';
  }
  if (normalized.includes('CREATE') || normalized.includes('UPDATE') || normalized.includes('ROTATE')) {
    return 'info';
  }
  return 'neutral';
}

function formatTarget(audit: AdminAuditLog) {
  if (!audit.targetType) {
    return '-';
  }
  const targetType = localizeAuditTarget(audit.targetType);
  return audit.targetId == null ? targetType : `${targetType} #${audit.targetId}`;
}

function parseDetails(detailsJson: string) {
  if (!detailsJson) {
    return '无附加详情';
  }

  try {
    return JSON.stringify(JSON.parse(detailsJson), null, 2);
  } catch {
    return '（详情解析失败，原始数据格式异常）';
  }
}

const AdminAudit: React.FC = () => {
  const [showFilters, setShowFilters] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [actorDraft, setActorDraft] = useState('');
  const [actorQuery, setActorQuery] = useState('');
  const [actionDraft, setActionDraft] = useState('');
  const [actionType, setActionType] = useState('');
  const [targetDraft, setTargetDraft] = useState('');
  const [targetType, setTargetType] = useState('');
  const [targetIdDraft, setTargetIdDraft] = useState('');
  const [targetId, setTargetId] = useState<number | undefined>(undefined);
  const [selectedAuditId, setSelectedAuditId] = useState<number | null>(null);

  const { data, isLoading, isError } = useAdminAudits({
    page,
    page_size: pageSize,
    actorQuery,
    actionType,
    targetType,
    targetId,
  });
  const selectedAudit = useMemo(
    () => data?.items.find((audit) => audit.id === selectedAuditId) ?? null,
    [data, selectedAuditId],
  );

  function applyFilters() {
    const parsedTargetId = targetIdDraft.trim() === '' ? undefined : Number(targetIdDraft.trim());
    setActorQuery(actorDraft.trim());
    setActionType(actionDraft.trim());
    setTargetType(targetDraft.trim());
    setTargetId(Number.isFinite(parsedTargetId) ? parsedTargetId : undefined);
    setPage(1);
  }

  function resetFilters() {
    setActorDraft('');
    setActorQuery('');
    setActionDraft('');
    setActionType('');
    setTargetDraft('');
    setTargetType('');
    setTargetIdDraft('');
    setTargetId(undefined);
    setPage(1);
  }

  const columns = useMemo<AdminColumn<AdminAuditLog>[]>(
    () => [
      {
        id: 'id',
        header: '#',
        accessor: (audit) => <Typography variant="body2" color="text.secondary">#{audit.id}</Typography>,
      },
      {
        id: 'action',
        header: '动作',
        accessor: (audit) => (
          <Stack spacing={0.75}>
            <Stack direction="row" spacing={1} alignItems="center">
              <Shield size={16} />
              <AdminStatusBadge label={localizeAuditAction(audit.actionType)} tone={auditTone(audit.actionType)} />
            </Stack>
            <Typography variant="caption" color="text.secondary">
              {audit.summary || '无摘要'}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'actor',
        header: '操作者',
        accessor: (audit) => (
          <Stack spacing={0.5}>
            <Typography variant="body2">{audit.actorUsername || '系统'}</Typography>
            <Typography variant="caption" color="text.secondary">
              {audit.actorUserId == null ? '系统操作' : `用户 #${audit.actorUserId}`}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'target',
        header: '目标',
        accessor: (audit) => (
          <Stack spacing={0.5}>
            <Typography variant="body2">{formatTarget(audit)}</Typography>
            <Typography variant="caption" color="text.secondary">
              {audit.targetType ? localizeAuditTarget(audit.targetType) : '无目标类型'}
            </Typography>
          </Stack>
        ),
      },
      {
        id: 'createdAt',
        header: '记录时间',
        accessor: (audit) => <Typography variant="body2">{formatDateTime(audit.createdAt)}</Typography>,
      },
      {
        id: 'actions',
        header: '操作',
        accessor: (audit) => (
          <Button size="small" color="inherit" onClick={() => setSelectedAuditId(audit.id)}>
            详情
          </Button>
        ),
        className: 'text-right',
      },
    ],
    [],
  );

  return (
    <AdminLayout title="审计日志">
      <AdminPage
        title="审计日志"
        description="查看管理员治理操作、目标对象与结构化审计详情。"
        isLoading={isLoading}
        isError={isError}
        errorText="审计日志加载失败。"
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
              placeholder="搜索操作者..."
              value={actorDraft}
              onChange={(event) => setActorDraft(event.target.value)}
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
                placeholder="动作类型，例如：更新用户状态"
                value={actionDraft}
                onChange={(event) => setActionDraft(event.target.value)}
                sx={{ minWidth: { xs: '100%', md: 260 } }}
              />
              <TextField
                size="small"
                placeholder="目标类型，例如：存储策略"
                value={targetDraft}
                onChange={(event) => setTargetDraft(event.target.value)}
                sx={{ minWidth: { xs: '100%', md: 240 } }}
              />
              <TextField
                size="small"
                type="number"
                placeholder="目标编号"
                value={targetIdDraft}
                onChange={(event) => setTargetIdDraft(event.target.value)}
                sx={{ minWidth: { xs: '100%', md: 160 } }}
              />
            </AdminFilterBar>
          ) : null}

          <AdminDataTable
            rows={data?.items || []}
            columns={columns}
            getRowKey={(audit) => audit.id}
            emptyText="暂无审计日志"
          />

          {selectedAudit ? (
            <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 3, p: 3 }}>
              <Stack spacing={2}>
                <Stack direction={{ xs: 'column', lg: 'row' }} justifyContent="space-between" spacing={2}>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      审计详情 #{selectedAudit.id}
                    </Typography>
                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mt: 1 }}>
                      <AdminStatusBadge label={localizeAuditAction(selectedAudit.actionType)} tone={auditTone(selectedAudit.actionType)} />
                      <AdminStatusBadge label={formatTarget(selectedAudit)} tone="neutral" />
                    </Stack>
                  </Box>
                  <Stack spacing={0.5} alignItems={{ xs: 'flex-start', lg: 'flex-end' }}>
                    <Typography variant="body2" color="text.secondary">
                      {formatDateTime(selectedAudit.createdAt)}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {selectedAudit.actorUsername || '系统'}
                    </Typography>
                  </Stack>
                </Stack>

                <Typography variant="body2" color="text.secondary">
                  {selectedAudit.summary || '无摘要'}
                </Typography>

                <Paper
                  elevation={0}
                  component="pre"
                  sx={{
                    m: 0,
                    p: 2,
                    overflowX: 'auto',
                    border: '1px solid',
                    borderColor: 'divider',
                    borderRadius: 2,
                    bgcolor: 'action.hover',
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
                    fontSize: '0.8125rem',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                  }}
                >
                  {parseDetails(selectedAudit.detailsJson)}
                </Paper>
              </Stack>
            </Paper>
          ) : null}

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

export default AdminAudit;
