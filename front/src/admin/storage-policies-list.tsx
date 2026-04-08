import { Chip, Stack } from '@mui/material';
import {
  BooleanField,
  Datagrid,
  DateField,
  FunctionField,
  List,
  RefreshButton,
  TextField,
  TopToolbar,
} from 'react-admin';

import type { AdminStoragePolicy, StoragePolicyCapabilities } from '@/src/lib/types';

const CAPABILITY_LABELS: Array<{ key: keyof StoragePolicyCapabilities; label: string }> = [
  { key: 'directUpload', label: '直传' },
  { key: 'multipartUpload', label: '分片' },
  { key: 'signedDownloadUrl', label: '签名下载' },
  { key: 'serverProxyDownload', label: '服务端下载' },
  { key: 'thumbnailNative', label: '原生缩略图' },
  { key: 'friendlyDownloadName', label: '友好文件名' },
  { key: 'requiresCors', label: 'CORS' },
  { key: 'supportsInternalEndpoint', label: '内网 endpoint' },
];

function StoragePoliciesListActions() {
  return (
    <TopToolbar>
      <RefreshButton />
    </TopToolbar>
  );
}

function formatFileSize(size: number) {
  if (size >= 1024 * 1024 * 1024) {
    return `${(size / (1024 * 1024 * 1024)).toFixed(1)} GB`;
  }
  if (size >= 1024 * 1024) {
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }
  if (size >= 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${size} B`;
}

function renderCapabilities(capabilities: StoragePolicyCapabilities) {
  return (
    <Stack direction="row" flexWrap="wrap" gap={0.5}>
      {CAPABILITY_LABELS.map(({ key, label }) => {
        const enabled = capabilities[key] === true;
        return (
          <Chip
            key={key}
            color={enabled ? 'success' : 'default'}
            label={`${label}${enabled ? '开' : '关'}`}
            size="small"
            variant={enabled ? 'filled' : 'outlined'}
          />
        );
      })}
    </Stack>
  );
}

export function PortalAdminStoragePoliciesList() {
  return (
    <List
      actions={<StoragePoliciesListActions />}
      perPage={25}
      resource="storagePolicies"
      title="存储策略"
      sort={{ field: 'id', order: 'ASC' }}
    >
      <Datagrid bulkActionButtons={false} rowClick={false}>
        <TextField source="id" label="ID" />
        <TextField source="name" label="名称" />
        <TextField source="type" label="类型" />
        <TextField source="bucketName" label="Bucket" emptyText="-" />
        <TextField source="endpoint" label="Endpoint" emptyText="-" />
        <TextField source="region" label="Region" emptyText="-" />
        <TextField source="prefix" label="Prefix" emptyText="-" />
        <TextField source="credentialMode" label="凭证模式" />
        <BooleanField source="enabled" label="启用" />
        <BooleanField source="defaultPolicy" label="默认" />
        <FunctionField<AdminStoragePolicy>
          label="容量上限"
          render={(record) => formatFileSize(record.maxSizeBytes)}
        />
        <FunctionField<AdminStoragePolicy>
          label="能力"
          render={(record) => renderCapabilities(record.capabilities)}
        />
        <DateField source="updatedAt" label="更新时间" showTime />
      </Datagrid>
    </List>
  );
}
