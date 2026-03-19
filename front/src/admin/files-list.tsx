import { Chip } from '@mui/material';
import {
  Datagrid,
  DateField,
  DeleteWithConfirmButton,
  FunctionField,
  List,
  RefreshButton,
  SearchInput,
  TextField,
  TopToolbar,
} from 'react-admin';

import type { AdminFile } from '@/src/lib/types';

function FilesListActions() {
  return (
    <TopToolbar>
      <RefreshButton />
    </TopToolbar>
  );
}

function formatFileSize(size: number) {
  if (size >= 1024 * 1024) {
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }
  if (size >= 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${size} B`;
}

export function PortalAdminFilesList() {
  return (
    <List
      actions={<FilesListActions />}
      filters={[
        <SearchInput key="query" source="query" alwaysOn placeholder="搜索文件名或路径" />,
        <SearchInput key="ownerQuery" source="ownerQuery" placeholder="搜索所属用户" />,
      ]}
      perPage={25}
      resource="files"
      title="文件管理"
      sort={{ field: 'createdAt', order: 'DESC' }}
    >
      <Datagrid bulkActionButtons={false} rowClick={false}>
        <TextField source="id" label="ID" />
        <TextField source="filename" label="文件名" />
        <TextField source="path" label="路径" />
        <TextField source="ownerUsername" label="所属用户" />
        <TextField source="ownerEmail" label="用户邮箱" />
        <FunctionField<AdminFile>
          label="类型"
          render={(record) =>
            record.directory ? <Chip label="目录" size="small" /> : <Chip label="文件" size="small" variant="outlined" />
          }
        />
        <FunctionField<AdminFile>
          label="大小"
          render={(record) => (record.directory ? '-' : formatFileSize(record.size))}
        />
        <TextField source="contentType" label="Content-Type" emptyText="-" />
        <DateField source="createdAt" label="创建时间" showTime />
        <DeleteWithConfirmButton mutationMode="pessimistic" label="删除" confirmTitle="删除文件" confirmContent="确认删除该文件吗？" />
      </Datagrid>
    </List>
  );
}
