import React from 'react';
import { Alert, Box, Button, Paper, Stack, Typography } from '@mui/material';
import { AlertTriangle, HardDrive, RefreshCw } from 'lucide-react';
import AdminLayout from '../../components/AdminLayout';
import AdminPage from '../../components/admin/AdminPage';
import AdminStatusBadge from '../../components/admin/AdminStatusBadge';
import { useAdminFilesystem } from '../../api/queries';
import { formatBytes } from '../../lib/format';

type ActionCardProps = {
  icon: React.ReactNode;
  title: string;
  description: string;
  buttonText: string;
  buttonColor?: 'primary' | 'warning' | 'error';
  buttonVariant?: 'contained' | 'outlined';
  buttonTitle: string;
};

const ActionCard: React.FC<ActionCardProps> = ({
  icon,
  title,
  description,
  buttonText,
  buttonColor = 'primary',
  buttonVariant = 'contained',
  buttonTitle,
}) => (
  <Paper
    elevation={0}
    sx={{
      border: '1px solid',
      borderColor: 'divider',
      borderRadius: 3,
      p: 2.5,
      bgcolor: 'background.paper',
    }}
  >
    <Stack spacing={2}>
      <Stack direction="row" spacing={1.5} alignItems="center">
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
          {icon}
        </Box>
        <Box>
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
            {title}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {description}
          </Typography>
        </Box>
      </Stack>
      <Button variant={buttonVariant} color={buttonColor} disabled title={buttonTitle}>
        {buttonText}
      </Button>
    </Stack>
  </Paper>
);

const AdminFileSystem: React.FC = () => {
  const { data, isLoading, isError } = useAdminFilesystem();
  const defaultPolicy = data?.defaultPolicy;

  return (
    <AdminLayout title="文件系统">
      <AdminPage
        title="系统状态"
        description="查看默认存储策略、上传能力、缓存与媒体处理状态。"
        isLoading={isLoading}
        isError={isError}
        errorText="文件系统信息加载失败。"
      >
        <Stack spacing={2.5}>
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', md: 'repeat(3, minmax(0, 1fr))' },
              gap: 2,
            }}
          >
            <ActionCard
              icon={<RefreshCw size={20} />}
              title="索引重建"
              description="重新扫描存储并同步数据库"
              buttonText="开始扫描"
              buttonTitle="后端暂未提供文件索引扫描接口"
            />
            <ActionCard
              icon={<AlertTriangle size={20} />}
              title="孤立文件清理"
              description="清理无引用的物理文件"
              buttonText="扫描孤立文件"
              buttonColor="error"
              buttonTitle="后端暂未提供孤立文件清理接口"
            />
            <ActionCard
              icon={<HardDrive size={20} />}
              title="容量校准"
              description="重新计算所有用户的使用量"
              buttonText="开始校准"
              buttonColor="warning"
              buttonVariant="outlined"
              buttonTitle="后端暂未提供容量校准接口"
            />
          </Box>

          <Paper
            elevation={0}
            sx={{
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 3,
              p: 3,
              bgcolor: 'background.paper',
            }}
          >
            <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
              文件系统快照
            </Typography>

            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', md: 'repeat(2, minmax(0, 1fr))' },
                gap: 2,
              }}
            >
              <Paper elevation={0} sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 3 }}>
                <Stack spacing={1}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                    存储概览
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    存储提供方：{data?.overview.storageProvider}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    文件：{data?.overview.totalFiles}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Blob：{data?.overview.totalBlobs}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    实体：{data?.overview.totalEntities}
                  </Typography>
                </Stack>
              </Paper>

              <Paper elevation={0} sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 3 }}>
                <Stack spacing={1}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                    默认策略上传能力
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {defaultPolicy?.name ?? '未配置默认策略'}
                  </Typography>
                  <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                    <AdminStatusBadge label="PROXY" tone={data?.upload.proxyUpload ? 'success' : 'neutral'} />
                    <AdminStatusBadge label="DIRECT_SINGLE" tone={data?.upload.directSingleUpload ? 'success' : 'neutral'} />
                    <AdminStatusBadge label="DIRECT_MULTIPART" tone={data?.upload.directMultipartUpload ? 'success' : 'neutral'} />
                  </Stack>
                  <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                    <AdminStatusBadge
                      label={defaultPolicy?.capabilities.requiresCors ? '需要 CORS' : '无需 CORS'}
                      tone={defaultPolicy?.capabilities.requiresCors ? 'warning' : 'success'}
                    />
                    <AdminStatusBadge
                      label={defaultPolicy?.capabilities.signedDownloadUrl ? '签名下载' : '代理下载'}
                      tone={defaultPolicy?.capabilities.signedDownloadUrl ? 'success' : 'neutral'}
                    />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    最大文件：{formatBytes(data?.upload.effectiveMaxFileSizeBytes ?? 0)}
                  </Typography>
                </Stack>
              </Paper>

              <Paper elevation={0} sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 3 }}>
                <Stack spacing={1}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                    媒体处理
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    元数据提取：{data?.mediaProcessing.metadataExtractionEnabled ? '开启' : '关闭'}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    原生缩略图：{data?.mediaProcessing.nativeThumbnailSupport ? '支持' : '不支持'}
                  </Typography>
                </Stack>
              </Paper>

              <Paper elevation={0} sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 3 }}>
                <Stack spacing={1}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                    缓存与 WebDAV
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    缓存后端：{data?.cache.backend}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    文件列表 TTL：{data?.cache.filesListTtlSeconds}s
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    目录版本 TTL：{data?.cache.directoryVersionTtlSeconds}s
                  </Typography>
                  <Alert severity={data?.webdav.enabled ? 'success' : 'info'}>
                    WebDAV：{data?.webdav.enabled ? '开启' : '关闭'}
                  </Alert>
                </Stack>
              </Paper>
            </Box>
          </Paper>
        </Stack>
      </AdminPage>
    </AdminLayout>
  );
};

export default AdminFileSystem;
