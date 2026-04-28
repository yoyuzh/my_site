import React, { useEffect, useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  TextField,
  Stack,
  IconButton,
  Tooltip,
} from '@mui/material';
import { DeleteOutline, EmojiEmotions } from '@mui/icons-material';
import { FileItem } from '../../api/types';
import { updateAppearance } from '../../lib/files';

interface AppearanceDialogProps {
  open: boolean;
  onClose: () => void;
  file: FileItem;
  onSuccess: (updatedFile: FileItem) => void;
}

const PRESET_COLORS = [
  '#E9A23B', // Default orange
  '#EF4444', // Red
  '#F59E0B', // Amber
  '#10B981', // Emerald
  '#3B82F6', // Blue
  '#6366F1', // Indigo
  '#8B5CF6', // Violet
  '#EC4899', // Pink
  '#64748B', // Slate
];

const AppearanceDialog: React.FC<AppearanceDialogProps> = ({
  open,
  onClose,
  file,
  onSuccess,
}) => {
  const [emoji, setEmoji] = useState(file.customEmoji || '');
  const [color, setColor] = useState(file.folderColor || '');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setEmoji(file.customEmoji || '');
    setColor(file.folderColor || '');
  }, [file]);

  async function handleSave() {
    setSaving(true);
    try {
      const updated = await updateAppearance(file.id, {
        customEmoji: emoji.trim() || null,
        folderColor: file.directory ? (color || null) : null,
      });
      onSuccess(updated);
      onClose();
    } catch (error) {
      console.error('Update appearance failed', error);
      alert('保存失败');
    } finally {
      setSaving(false);
    }
  }

  function handleClear() {
    setEmoji('');
    setColor('');
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>自定义外观</DialogTitle>
      <DialogContent>
        <Stack spacing={3} sx={{ mt: 1 }}>
          <Box>
            <Typography variant="subtitle2" gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <EmojiEmotions fontSize="small" />
              自定义 Emoji
            </Typography>
            <TextField
              fullWidth
              placeholder="输入一个 Emoji (如 📂, 🚀, 💡)"
              value={emoji}
              onChange={(e) => setEmoji(e.target.value)}
              helperText="将替换原有的文件/文件夹图标"
              size="small"
            />
          </Box>

          {file.directory && (
            <Box>
              <Typography variant="subtitle2" gutterBottom>
                文件夹颜色
              </Typography>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                {PRESET_COLORS.map((c) => (
                  <Tooltip key={c} title={c}>
                    <IconButton
                      onClick={() => setColor(c)}
                      sx={{
                        width: 32,
                        height: 32,
                        bgcolor: c,
                        border: '2px solid',
                        borderColor: color === c ? 'primary.main' : 'transparent',
                        '&:hover': { bgcolor: c, opacity: 0.8 },
                      }}
                    />
                  </Tooltip>
                ))}
              </Box>
            </Box>
          )}

          <Box sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 2, display: 'flex', alignItems: 'center', gap: 2 }}>
            <Box sx={{ fontSize: '2rem' }}>
              {emoji.trim() ? emoji.trim() : (file.directory ? <Box sx={{ color: color || PRESET_COLORS[0], display: 'flex' }}>📂</Box> : '📄')}
            </Box>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body2" fontWeight={700} noWrap>预览效果</Typography>
              <Typography variant="caption" color="text.secondary" noWrap>{file.filename}</Typography>
            </Box>
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button startIcon={<DeleteOutline />} color="inherit" onClick={handleClear} sx={{ mr: 'auto' }}>
          重置
        </Button>
        <Button onClick={onClose}>取消</Button>
        <Button variant="contained" onClick={handleSave} disabled={saving}>
          保存更改
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default AppearanceDialog;
