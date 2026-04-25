import React, { useEffect, useState } from 'react';
import {
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  ListItemIcon,
  Checkbox,
  IconButton,
  TextField,
  Box,
  Typography,
  Stack,
  CircularProgress,
} from '@mui/material';
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  Edit as EditIcon,
  Check as CheckIcon,
  Close as CloseIcon,
  Circle as CircleIcon,
} from '@mui/icons-material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FileItem, FileTag } from '../../api/types';
import {
  listTags,
  createTag,
  updateTag,
  deleteTag,
  listFileTags,
  addFileTag,
  removeFileTag,
} from '../../lib/files';

interface FileTagsManagerDialogProps {
  open: boolean;
  onClose: () => void;
  file: FileItem | null;
}

const PRESET_COLORS = [
  '#4F7CFF', // Primary
  '#10B981', // Emerald
  '#F59E0B', // Amber
  '#EF4444', // Red
  '#8B5CF6', // Violet
  '#EC4899', // Pink
  '#06B6D4', // Cyan
  '#64748B', // Slate
];

export const FileTagsManagerDialog: React.FC<FileTagsManagerDialogProps> = ({
  open,
  onClose,
  file,
}) => {
  const queryClient = useQueryClient();
  const [editingTag, setEditingTag] = useState<FileTag | null>(null);
  const [newTagName, setNewTagName] = useState('');
  const [newTagColor, setNewTagColor] = useState(PRESET_COLORS[0]);
  const [isCreating, setIsCreating] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  // Queries
  const { data: allTags = [], isLoading: loadingAllTags } = useQuery({
    queryKey: ['tags'],
    queryFn: listTags,
    enabled: open,
  });

  const { data: fileTags = [], isLoading: loadingFileTags } = useQuery({
    queryKey: ['file-tags', file?.id],
    queryFn: () => (file ? listFileTags(file.id) : Promise.resolve([])),
    enabled: open && !!file,
  });

  // Mutations
  const createMutation = useMutation({
    mutationFn: async (vars: { name: string; color: string }) => {
      const createdTag = await createTag(vars.name, vars.color);
      if (file?.directory) {
        await addFileTag(file.id, createdTag.id);
      }
      return createdTag;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tags'] });
      queryClient.invalidateQueries({ queryKey: ['file-tags', file?.id] });
      queryClient.invalidateQueries({ queryKey: ['file-detail', file?.id] });
      setIsCreating(false);
      setNewTagName('');
      setNewTagColor(PRESET_COLORS[0]);
      setErrorMessage('');
    },
    onError: (error) => {
      setErrorMessage(error instanceof Error ? error.message : '创建标签失败');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (vars: { id: number; name: string; color: string }) =>
      updateTag(vars.id, vars.name, vars.color),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tags'] });
      setEditingTag(null);
      setErrorMessage('');
    },
    onError: (error) => {
      setErrorMessage(error instanceof Error ? error.message : '更新标签失败');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteTag,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tags'] });
      queryClient.invalidateQueries({ queryKey: ['file-tags'] });
      queryClient.invalidateQueries({ queryKey: ['file-detail', file?.id] });
      setErrorMessage('');
    },
    onError: (error) => {
      setErrorMessage(error instanceof Error ? error.message : '删除标签失败');
    },
  });

  const toggleTagMutation = useMutation({
    mutationFn: async ({ tagId, assigned }: { tagId: number; assigned: boolean }) => {
      if (!file) return;
      if (assigned) {
        await removeFileTag(file.id, tagId);
      } else {
        await addFileTag(file.id, tagId);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['file-tags', file?.id] });
      queryClient.invalidateQueries({ queryKey: ['file-detail', file?.id] });
      setErrorMessage('');
    },
    onError: (error) => {
      setErrorMessage(error instanceof Error ? error.message : '更新文件夹标签失败');
    },
  });

  const handleCreate = () => {
    if (newTagName.trim()) {
      createMutation.mutate({ name: newTagName.trim(), color: newTagColor });
    }
  };

  const handleUpdate = () => {
    if (editingTag && editingTag.name.trim()) {
      updateMutation.mutate({
        id: editingTag.id,
        name: editingTag.name.trim(),
        color: editingTag.color,
      });
    }
  };

  const isTagAssigned = (tagId: number) => {
    return fileTags.some((t) => t.id === tagId);
  };

  useEffect(() => {
    if (!open) {
      setEditingTag(null);
      setIsCreating(false);
      setNewTagName('');
      setNewTagColor(PRESET_COLORS[0]);
      setErrorMessage('');
    }
  }, [open]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        管理标签
        {file && (
          <Typography variant="caption" display="block" color="text.secondary" sx={{ mt: 0.5 }}>
            正在为 "{file.filename}" 配置
          </Typography>
        )}
      </DialogTitle>

      <DialogContent dividers sx={{ p: 0 }}>
        {errorMessage ? (
          <Alert severity="error" sx={{ mx: 2, mt: 2, mb: 0 }}>
            {errorMessage}
          </Alert>
        ) : null}

        {loadingAllTags || loadingFileTags ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
            <CircularProgress size={32} />
          </Box>
        ) : (
          <List sx={{ py: 0 }}>
            {allTags.map((tag) => {
              const assigned = isTagAssigned(tag.id);
              const isEditing = editingTag?.id === tag.id;

              return (
                <ListItem
                  key={tag.id}
                  secondaryAction={
                    <Stack direction="row" spacing={0.5}>
                      {!isEditing ? (
                        <>
                          <IconButton size="small" onClick={() => setEditingTag(tag)}>
                            <EditIcon fontSize="small" />
                          </IconButton>
                          <IconButton
                            size="small"
                            color="error"
                            onClick={() => {
                              if (window.confirm(`确定要删除标签 "${tag.name}" 吗？`)) {
                                deleteMutation.mutate(tag.id);
                              }
                            }}
                          >
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </>
                      ) : (
                        <>
                          <IconButton size="small" color="primary" onClick={handleUpdate}>
                            <CheckIcon fontSize="small" />
                          </IconButton>
                          <IconButton size="small" onClick={() => setEditingTag(null)}>
                            <CloseIcon fontSize="small" />
                          </IconButton>
                        </>
                      )}
                    </Stack>
                  }
                  sx={{
                    borderBottom: '1px solid',
                    borderColor: 'divider',
                    '&:last-child': { borderBottom: 0 },
                  }}
                >
                  <ListItemIcon sx={{ minWidth: 40 }}>
                    <Checkbox
                      edge="start"
                      checked={assigned}
                      onChange={() => toggleTagMutation.mutate({ tagId: tag.id, assigned })}
                      disableRipple
                    />
                  </ListItemIcon>

                  {!isEditing ? (
                    <Box sx={{ display: 'flex', alignItems: 'center', flex: 1, minWidth: 0 }}>
                      <CircleIcon sx={{ color: tag.color, fontSize: 12, mr: 1.5 }} />
                      <ListItemText
                        primary={tag.name}
                        primaryTypographyProps={{
                          variant: 'body2',
                          noWrap: true,
                        }}
                      />
                    </Box>
                  ) : (
                    <Stack spacing={1} sx={{ flex: 1, mr: 2 }}>
                      <TextField
                        size="small"
                        fullWidth
                        autoFocus
                        value={editingTag.name}
                        onChange={(e) => setEditingTag({ ...editingTag, name: e.target.value })}
                        variant="standard"
                      />
                      <Stack direction="row" spacing={0.5}>
                        {PRESET_COLORS.map((c) => (
                          <Box
                            key={c}
                            onClick={() => setEditingTag({ ...editingTag, color: c })}
                            sx={{
                              width: 18,
                              height: 18,
                              borderRadius: '50%',
                              bgcolor: c,
                              cursor: 'pointer',
                              border: editingTag.color === c ? '2px solid' : 'none',
                              borderColor: 'text.primary',
                              boxSizing: 'border-box',
                            }}
                          />
                        ))}
                      </Stack>
                    </Stack>
                  )}
                </ListItem>
              );
            })}

            {!isCreating ? (
              <ListItem disablePadding>
                <ListItemButton
                  onClick={() => {
                    setErrorMessage('');
                    setIsCreating(true);
                  }}
                  sx={{ color: 'primary.main', py: 1.5 }}
                >
                  <ListItemIcon sx={{ minWidth: 40 }}>
                    <AddIcon color="primary" fontSize="small" />
                  </ListItemIcon>
                  <ListItemText primary="创建新标签" primaryTypographyProps={{ variant: 'body2' }} />
                </ListItemButton>
              </ListItem>
            ) : (
              <Box sx={{ p: 2, bgcolor: 'action.hover' }}>
                <Stack spacing={2}>
                  <TextField
                    label="标签名称"
                    size="small"
                    fullWidth
                    autoFocus
                    value={newTagName}
                    onChange={(e) => setNewTagName(e.target.value)}
                  />
                  <Box>
                    <Typography variant="caption" color="text.secondary" gutterBottom>
                      选择颜色
                    </Typography>
                    <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
                      {PRESET_COLORS.map((c) => (
                        <Box
                          key={c}
                          onClick={() => setNewTagColor(c)}
                          sx={{
                            width: 24,
                            height: 24,
                            borderRadius: '50%',
                            bgcolor: c,
                            cursor: 'pointer',
                            border: newTagColor === c ? '2px solid' : 'none',
                            borderColor: 'text.primary',
                            boxSizing: 'border-box',
                          }}
                        />
                      ))}
                    </Stack>
                  </Box>
                  <Stack direction="row" spacing={1} justifyContent="flex-end">
                    <Button
                      size="small"
                      onClick={() => {
                        setErrorMessage('');
                        setIsCreating(false);
                      }}
                    >
                      取消
                    </Button>
                    <Button
                      size="small"
                      variant="contained"
                      onClick={handleCreate}
                      disabled={!newTagName.trim() || createMutation.isPending}
                    >
                      {createMutation.isPending ? '创建中...' : '创建'}
                    </Button>
                  </Stack>
                </Stack>
              </Box>
            )}

            {allTags.length === 0 && !isCreating && (
              <Box sx={{ py: 4, textAlign: 'center' }}>
                <Typography variant="body2" color="text.secondary">
                  暂无标签，点击下方按钮创建
                </Typography>
              </Box>
            )}
          </List>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose}>关闭</Button>
      </DialogActions>
    </Dialog>
  );
};
