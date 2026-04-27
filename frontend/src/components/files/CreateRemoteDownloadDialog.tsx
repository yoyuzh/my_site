import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Close } from '@mui/icons-material';
import { useMutation } from '@tanstack/react-query';
import type { RemoteDownloadDetail, RemoteDownloadSourceType } from '../../api/types';
import { createRemoteDownload } from '../../lib/remote-downloads';

interface CreateRemoteDownloadDialogProps {
  open: boolean;
  defaultPath: string;
  onClose: () => void;
  onCreated: (detail: RemoteDownloadDetail) => void;
}

const CreateRemoteDownloadDialog: React.FC<CreateRemoteDownloadDialogProps> = ({
  open,
  defaultPath,
  onClose,
  onCreated,
}) => {
  const [sourceType, setSourceType] = useState<RemoteDownloadSourceType>('HTTP');
  const [sourceValue, setSourceValue] = useState('');
  const [targetPath, setTargetPath] = useState(defaultPath);
  const [torrentFile, setTorrentFile] = useState<File | null>(null);

  useEffect(() => {
    if (open) {
      setTargetPath(defaultPath);
    }
  }, [defaultPath, open]);

  const createMutation = useMutation({
    mutationFn: createRemoteDownload,
    onSuccess: (detail) => {
      onCreated(detail);
      handleClose();
    },
  });

  const title = useMemo(() => {
    if (sourceType === 'MAGNET') {
      return '输入磁力链接';
    }
    if (sourceType === 'TORRENT_FILE') {
      return '上传种子文件';
    }
    return '输入下载链接';
  }, [sourceType]);

  const handleClose = () => {
    setSourceType('HTTP');
    setSourceValue('');
    setTargetPath(defaultPath);
    setTorrentFile(null);
    createMutation.reset();
    onClose();
  };

  const handleSubmit = () => {
    createMutation.mutate({
      sourceType,
      sourceValue: sourceType === 'TORRENT_FILE' ? undefined : sourceValue,
      torrentFile: sourceType === 'TORRENT_FILE' ? torrentFile : null,
      targetPath,
    });
  };

  const submitDisabled =
    createMutation.isPending ||
    !targetPath.trim() ||
    (sourceType === 'TORRENT_FILE' ? !torrentFile : !sourceValue.trim());

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ m: 0, p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        离线下载
        <IconButton onClick={handleClose} size="small">
          <Close />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2.5} sx={{ py: 1 }}>
          <Typography variant="body2" color="text.secondary">
            创建服务端后台下载任务。普通链接走 aria2，磁力链和种子文件走 qBittorrent。
          </Typography>

          <FormControl fullWidth>
            <InputLabel id="remote-download-source-type-label">来源类型</InputLabel>
            <Select
              labelId="remote-download-source-type-label"
              value={sourceType}
              label="来源类型"
              onChange={(event) => setSourceType(event.target.value as RemoteDownloadSourceType)}
            >
              <MenuItem value="HTTP">HTTP / HTTPS</MenuItem>
              <MenuItem value="MAGNET">磁力链接</MenuItem>
              <MenuItem value="TORRENT_FILE">种子文件</MenuItem>
            </Select>
          </FormControl>

          {sourceType === 'TORRENT_FILE' ? (
            <Button variant="outlined" component="label">
              {torrentFile ? `已选择：${torrentFile.name}` : '选择 .torrent 文件'}
              <input
                hidden
                type="file"
                accept=".torrent,application/x-bittorrent"
                onChange={(event) => setTorrentFile(event.target.files?.[0] ?? null)}
              />
            </Button>
          ) : (
            <TextField
              label={title}
              fullWidth
              multiline
              minRows={3}
              value={sourceValue}
              onChange={(event) => setSourceValue(event.target.value)}
              placeholder={sourceType === 'MAGNET' ? 'magnet:?xt=urn:btih:...' : 'https://example.com/demo.zip'}
            />
          )}

          <TextField
            label="导入到目录"
            fullWidth
            value={targetPath}
            onChange={(event) => setTargetPath(event.target.value)}
            placeholder="/downloads"
          />

          {createMutation.isError ? (
            <Alert severity="error">
              创建离线下载失败：{createMutation.error instanceof Error ? createMutation.error.message : '未知错误'}
            </Alert>
          ) : null}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <Button onClick={handleClose}>取消</Button>
        <Button onClick={handleSubmit} variant="contained" disabled={submitDisabled}>
          {createMutation.isPending ? '创建中...' : '创建任务'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default CreateRemoteDownloadDialog;
