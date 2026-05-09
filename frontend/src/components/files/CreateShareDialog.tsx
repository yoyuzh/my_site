import React, { useState } from 'react';import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  FormControlLabel,
  Switch,
  Stack,
  Typography,
  Box,
  IconButton,
  InputAdornment,
  Tooltip,
  Alert,
} from '@mui/material';
import {
  Close,
  ContentCopy,
  CheckCircleOutline,
  Lock,
  Timer,
  Download,
  CloudUpload,
  AutoDelete,
} from '@mui/icons-material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createShare, buildFullShareUrl } from '../../lib/shares';
import { formatDateTime } from '../../lib/format';
import type { FileItem, ShareItem } from '../../api/types';

interface CreateShareDialogProps {
  open: boolean;
  onClose: () => void;
  file: FileItem | null;
}

const CreateShareDialog: React.FC<CreateShareDialogProps> = ({ open, onClose, file }) => {
  const queryClient = useQueryClient();
  const [shareName, setShareName] = useState('');
  const [usePassword, setUsePassword] = useState(false);
  const [password, setPassword] = useState('');
  const [useExpiry, setUseExpiry] = useState(false);
  const [expiresAt, setExpiresAt] = useState('');
  const [maxDownloads, setMaxDownloads] = useState<string>('');
  const [allowImport, setAllowImport] = useState(true);
  const [allowDownload, setAllowDownload] = useState(true);
  const [expireAfterConsume, setExpireAfterConsume] = useState(false);
  const [result, setResult] = useState<ShareItem | null>(null);
  const [validationError, setValidationError] = useState('');

  const createMutation = useMutation({
    mutationFn: createShare,
    onSuccess: (data) => {
      setResult(data);
      void queryClient.invalidateQueries({ queryKey: ['myShares'] });
    },
  });

  const handleCreate = () => {
    if (!file) return;
    const trimmedPassword = password.trim();
    const trimmedMaxDownloads = maxDownloads.trim();
    const parsedMaxDownloads = trimmedMaxDownloads ? Number.parseInt(trimmedMaxDownloads, 10) : undefined;
    if (usePassword && trimmedPassword.length < 4) {
      setValidationError('提取密码至少需要 4 位');
      return;
    }
    if (trimmedMaxDownloads && !/^[1-9]\d*$/.test(trimmedMaxDownloads)) {
      setValidationError('最大下载/导入次数必须是大于 0 的整数');
      return;
    }
    setValidationError('');
    createMutation.mutate({
      fileId: file.id,
      shareName: shareName.trim() || undefined,
      password: usePassword ? trimmedPassword : undefined,
      expiresAt: useExpiry ? expiresAt : undefined,
      maxDownloads: parsedMaxDownloads,
      allowImport,
      allowDownload,
      expireAfterConsume,
    });
  };

  const handleClose = () => {
    setResult(null);
    setShareName('');
    setUsePassword(false);
    setPassword('');
    setUseExpiry(false);
    setExpiresAt('');
    setMaxDownloads('');
    setAllowImport(true);
    setAllowDownload(true);
    setExpireAfterConsume(false);
    setValidationError('');
    createMutation.reset();
    onClose();
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  if (result) {
    const fullUrl = buildFullShareUrl(result.token);
    return (
      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ m: 0, p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          分享成功
          <IconButton onClick={handleClose} size="small">
            <Close />
          </IconButton>
        </DialogTitle>
        <DialogContent>
          <Stack spacing={3} sx={{ py: 1 }}>
            <Box sx={{ textAlign: 'center', py: 2 }}>
              <CheckCircleOutline sx={{ fontSize: 64, color: 'success.main', mb: 1 }} />
              <Typography variant="h6">分享链接已生成</Typography>
            </Box>

            <TextField
              label="分享链接"
              fullWidth
              value={fullUrl}
              InputProps={{
                readOnly: true,
                endAdornment: (
                  <InputAdornment position="end">
                    <Tooltip title="复制链接">
                      <IconButton onClick={() => copyToClipboard(fullUrl)}>
                        <ContentCopy />
                      </IconButton>
                    </Tooltip>
                  </InputAdornment>
                ),
              }}
            />

            {result.passwordRequired && result.password && (
              <TextField
                label="提取密码"
                fullWidth
                value={result.password}
                InputProps={{
                  readOnly: true,
                  endAdornment: (
                    <InputAdornment position="end">
                      <Tooltip title="复制密码">
                        <IconButton onClick={() => copyToClipboard(result.password!)}>
                          <ContentCopy />
                        </IconButton>
                      </Tooltip>
                    </InputAdornment>
                  ),
                }}
              />
            )}

            <Box sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 1 }}>
              <Typography variant="subtitle2" gutterBottom>分享设置摘要：</Typography>
              <Typography variant="body2" color="text.secondary">
                • 有效期：{result.expiresAt ? formatDateTime(result.expiresAt) : '永久有效'}
              </Typography>
              {result.maxDownloads && (
                <Typography variant="body2" color="text.secondary">
                  • 下载次数限制：{result.maxDownloads} 次
                </Typography>
              )}
              {result.expireAfterConsume && (
                <Typography variant="body2" color="text.secondary">
                  • 阅后即焚: 链接将在首次下载或导入后失效
                </Typography>
              )}
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} variant="contained" fullWidth>
            完成
          </Button>
        </DialogActions>
      </Dialog>
    );
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ m: 0, p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        创建分享链接
        <IconButton onClick={handleClose} size="small">
          <Close />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2.5} sx={{ py: 1 }}>
          <TextField
            label="分享名称 (可选)"
            fullWidth
            placeholder={file?.filename}
            value={shareName}
            onChange={(e) => setShareName(e.target.value)}
          />

          <Box>
            <FormControlLabel
              control={<Switch checked={usePassword} onChange={(e) => setUsePassword(e.target.checked)} />}
              label={
                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                  <Lock sx={{ fontSize: 20, mr: 1, opacity: 0.7 }} />
                  密码保护
                </Box>
              }
            />
            {usePassword && (
              <TextField
                fullWidth
                size="small"
                placeholder="输入 4 位以上密码"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                sx={{ mt: 1 }}
              />
            )}
          </Box>

          <Box>
            <FormControlLabel
              control={<Switch checked={useExpiry} onChange={(e) => setUseExpiry(e.target.checked)} />}
              label={
                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                  <Timer sx={{ fontSize: 20, mr: 1, opacity: 0.7 }} />
                  设置有效期
                </Box>
              }
            />
            {useExpiry && (
              <TextField
                fullWidth
                type="datetime-local"
                size="small"
                value={expiresAt}
                onChange={(e) => setExpiresAt(e.target.value)}
                sx={{ mt: 1 }}
                InputLabelProps={{ shrink: true }}
              />
            )}
          </Box>

          <Box>
            <FormControlLabel
              control={<Switch checked={expireAfterConsume} onChange={(e) => setExpireAfterConsume(e.target.checked)} />}
              label={
                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                  <AutoDelete sx={{ fontSize: 20, mr: 1, opacity: 0.7 }} />
                  阅后即焚 (下载或导入后链接失效)
                </Box>
              }
            />
            <Typography variant="caption" color="text.secondary" sx={{ ml: 4.5, display: 'block' }}>
              注意: 链接在被成功下载或导入一次后将立即失效，仅访问页面不会触发。
            </Typography>
          </Box>

          <TextField
            label="最大下载/导入次数"
            fullWidth
            type="number"
            placeholder="留空表示不限制"
            value={maxDownloads}
            onChange={(e) => setMaxDownloads(e.target.value)}
            helperText="累计达到此次数后分享将失效"
          />

          <Stack direction="row" spacing={3}>
            <FormControlLabel
              control={<Switch checked={allowDownload} onChange={(e) => setAllowDownload(e.target.checked)} />}
              label={
                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                  <Download sx={{ fontSize: 20, mr: 1, opacity: 0.7 }} />
                  允许下载
                </Box>
              }
            />
            <FormControlLabel
              control={<Switch checked={allowImport} onChange={(e) => setAllowImport(e.target.checked)} />}
              label={
                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                  <CloudUpload sx={{ fontSize: 20, mr: 1, opacity: 0.7 }} />
                  允许导入
                </Box>
              }
            />
          </Stack>

            {createMutation.isError && (
              <Alert severity="error">
                创建分享失败: {createMutation.error instanceof Error ? createMutation.error.message : '未知错误'}
              </Alert>
            )}
            {validationError ? (
              <Alert severity="error">
                {validationError}
              </Alert>
            ) : null}
          </Stack>
        </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <Button onClick={handleClose}>取消</Button>
        <Button
          onClick={handleCreate}
          variant="contained"
          disabled={createMutation.isPending || (usePassword && !password.trim()) || (useExpiry && !expiresAt)}
        >
          {createMutation.isPending ? '创建中...' : '生成链接'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default CreateShareDialog;
