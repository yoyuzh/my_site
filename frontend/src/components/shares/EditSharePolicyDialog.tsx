import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  FormControlLabel,
  Switch,
  Stack,
  Box,
  IconButton,
  Alert,
  Typography,
} from '@mui/material';
import {
  Close,
  Lock,
  Timer,
  AutoDelete,
} from '@mui/icons-material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateSharePolicy } from '../../lib/shares';
import type { ShareItem } from '../../api/types';

interface EditSharePolicyDialogProps {
  open: boolean;
  onClose: () => void;
  share: ShareItem | null;
}

const EditSharePolicyDialog: React.FC<EditSharePolicyDialogProps> = ({ open, onClose, share }) => {
  const queryClient = useQueryClient();
  const [password, setPassword] = useState('');
  const [useExpiry, setUseExpiry] = useState(false);
  const [expiresAt, setExpiresAt] = useState('');
  const [maxDownloads, setMaxDownloads] = useState<string>('');
  const [expireAfterConsume, setExpireAfterConsume] = useState(false);

  useEffect(() => {
    if (share && open) {
      setPassword(share.password || '');
      setUseExpiry(!!share.expiresAt);
      if (share.expiresAt) {
        const date = new Date(share.expiresAt);
        const tzOffset = date.getTimezoneOffset() * 60000;
        const localISOTime = new Date(date.getTime() - tzOffset).toISOString().slice(0, 16);
        setExpiresAt(localISOTime);
      } else {
        setExpiresAt('');
      }
      setMaxDownloads(share.maxDownloads?.toString() || '');
      setExpireAfterConsume(share.expireAfterConsume);
    }
  }, [share, open]);

  const updateMutation = useMutation({
    mutationFn: (payload: any) => updateSharePolicy(share!.id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['myShares'] });
      onClose();
    },
  });

  const handleUpdate = () => {
    if (!share) return;
    updateMutation.mutate({
      password: password || '', // Empty string clears password in backend if not provided as null
      expiresAt: useExpiry && expiresAt ? new Date(expiresAt).toISOString() : undefined,
      maxDownloads: maxDownloads ? parseInt(maxDownloads, 10) : null,
      expireAfterConsume,
    });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ m: 0, p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        修改分享策略
        <IconButton onClick={onClose} size="small">
          <Close />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2.5} sx={{ py: 1 }}>
          <Box>
            <Typography variant="subtitle2" gutterBottom sx={{ display: 'flex', alignItems: 'center' }}>
              <Lock sx={{ fontSize: 18, mr: 1, opacity: 0.7 }} />
              访问密码
            </Typography>
            <TextField
              fullWidth
              size="small"
              placeholder="留空表示不设密码"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </Box>

          <Box>
            <FormControlLabel
              control={<Switch checked={useExpiry} onChange={(e) => setUseExpiry(e.target.checked)} />}
              label={
                <Box sx={{ display: 'flex', alignItems: 'center' }}>
                  <Timer sx={{ fontSize: 18, mr: 1, opacity: 0.7 }} />
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
                  <AutoDelete sx={{ fontSize: 18, mr: 1, opacity: 0.7 }} />
                  成功消费后失效 (下载或导入后)
                </Box>
              }
            />
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

          {updateMutation.isError && (
            <Alert severity="error">
              更新失败: {updateMutation.error instanceof Error ? updateMutation.error.message : '未知错误'}
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <Button onClick={onClose}>取消</Button>
        <Button
          onClick={handleUpdate}
          variant="contained"
          disabled={updateMutation.isPending || (useExpiry && !expiresAt)}
        >
          {updateMutation.isPending ? '更新中...' : '保存修改'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default EditSharePolicyDialog;
