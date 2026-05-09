import React, { useState, useEffect, useRef } from 'react';
import {
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  Breadcrumbs,
  Link,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Divider,
  CircularProgress,
  Stack,
  alpha,
  Radio,
  RadioGroup,
  FormControlLabel,
  FormControl,
} from '@mui/material';
import {
  Folder,
  InsertDriveFile,
  ChevronRight,
  Home,
  WarningAmber,
} from '@mui/icons-material';
import { BackgroundTask, FileItem, MoveConflictStrategy, MoveResponse } from '../../api/types';
import { listFiles, batchMoveFiles, moveFile } from '../../lib/files';
import { getWorkspaceItemLogicalPath, normalizeWorkspaceFolderPath } from '../../lib/workspace-folder-tree';

interface MoveItemsDialogProps {
  open: boolean;
  onClose: () => void;
  items: FileItem[];
  currentPath: string;
  initialConflictResult?: MoveResponse | null;
  onSuccess: (task: BackgroundTask) => void;
}

const MoveItemsDialog: React.FC<MoveItemsDialogProps> = ({
  open,
  onClose,
  items,
  currentPath: initialPath,
  initialConflictResult,
  onSuccess,
}) => {
  const [targetPath, setTargetPath] = useState(initialPath);
  const [targetContent, setTargetContent] = useState<FileItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [moving, setMoving] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [conflictResult, setConflictResult] = useState<MoveResponse | null>(null);
  const [conflictStrategy, setConflictStrategy] = useState<MoveConflictStrategy>('AUTO_RENAME');
  const loadRequestIdRef = useRef(0);

  useEffect(() => {
    if (open) {
      setTargetPath(initialPath);
      setConflictResult(initialConflictResult ?? null);
      setConflictStrategy('AUTO_RENAME');
      setLoadError(null);
    }
  }, [initialConflictResult, initialPath, open]);

  useEffect(() => {
    if (!open) {
      return;
    }

    const requestId = loadRequestIdRef.current + 1;
    loadRequestIdRef.current = requestId;
    setLoading(true);
    setLoadError(null);
    setTargetContent([]);

    const loadTargetContent = async () => {
      try {
        const result = await listFiles(targetPath, 0, 1000);
        if (loadRequestIdRef.current === requestId) {
          setTargetContent(result.items);
        }
      } catch (error) {
        console.error('Failed to load target content', error);
        if (loadRequestIdRef.current === requestId) {
          setTargetContent([]);
          setLoadError(error instanceof Error ? error.message : '目标目录加载失败');
        }
      } finally {
        if (loadRequestIdRef.current === requestId) {
          setLoading(false);
        }
      }
    };
    void loadTargetContent();

    return () => {
      if (loadRequestIdRef.current === requestId) {
        loadRequestIdRef.current += 1;
      }
    };
  }, [open, targetPath]);

  async function handleMove(strategy?: MoveConflictStrategy) {
    setMoving(true);
    try {
      let task: BackgroundTask;
      const fileIds = items.map((i) => i.id);
      const resolvedStrategy = strategy ?? 'AUTO_RENAME';
      
      if (fileIds.length === 1) {
        task = await moveFile(fileIds[0], targetPath, resolvedStrategy);
      } else {
        task = await batchMoveFiles(fileIds, targetPath, resolvedStrategy);
      }

      onSuccess(task);
      onClose();
    } catch (error) {
      console.error('Move failed', error);
      alert(error instanceof Error ? error.message : '移动失败');
    } finally {
      setMoving(false);
    }
  }

  const pathParts = targetPath.split('/').filter(Boolean);
  const breadcrumbs = [
    { name: '根目录', path: '/' },
    ...pathParts.map((part, index) => ({
      name: part,
      path: '/' + pathParts.slice(0, index + 1).join('/'),
    })),
  ];

  if (conflictResult) {
    return (
      <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <WarningAmber color="warning" />
          项目重名冲突
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            目标文件夹中已存在同名项目，请选择处理方式：
          </Typography>
          <FormControl>
            <RadioGroup
              value={conflictStrategy}
              onChange={(e) => setConflictStrategy(e.target.value as MoveConflictStrategy)}
            >
              <FormControlLabel
                value="AUTO_RENAME"
                control={<Radio />}
                label="自动重命名后移动"
              />
              <FormControlLabel
                value="SKIP"
                control={<Radio />}
                label="跳过重名项"
              />
            </RadioGroup>
          </FormControl>
          <Box sx={{ mt: 2, bgcolor: 'action.hover', p: 1.5, borderRadius: 1 }}>
            <Typography variant="caption" color="text.secondary" display="block">
              涉及冲突的项目：
            </Typography>
            {conflictResult.conflicts.map((c) => (
              <Typography key={c.fileId} variant="caption" sx={{ display: 'block' }}>
                • {c.filename}
              </Typography>
            ))}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConflictResult(null)}>上一步</Button>
          <Button
            variant="contained"
            onClick={() => handleMove(conflictStrategy)}
            disabled={moving}
          >
            {moving ? <CircularProgress size={20} /> : '确认'}
          </Button>
        </DialogActions>
      </Dialog>
    );
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>移动项目</DialogTitle>
      <DialogContent sx={{ p: 0, height: 400, display: 'flex', flexDirection: 'column' }}>
        <Box sx={{ px: 3, py: 1, bgcolor: 'action.hover' }}>
          <Breadcrumbs separator={<ChevronRight fontSize="small" />}>
            {breadcrumbs.map((b, i) => (
              <Link
                key={b.path}
                component="button"
                variant="body2"
                onClick={() => setTargetPath(b.path)}
                sx={{
                  color: i === breadcrumbs.length - 1 ? 'text.primary' : 'primary.main',
                  fontWeight: i === breadcrumbs.length - 1 ? 700 : 400,
                  textDecoration: 'none',
                  '&:hover': { textDecoration: 'underline' },
                  display: 'flex',
                  alignItems: 'center',
                }}
              >
                {i === 0 && <Home sx={{ fontSize: 16, mr: 0.5 }} />}
                {b.name}
              </Link>
            ))}
          </Breadcrumbs>
        </Box>
        <Divider />
        <Box sx={{ flex: 1, overflow: 'auto' }}>
          {loading ? (
            <Stack alignItems="center" justifyContent="center" sx={{ height: '100%' }}>
              <CircularProgress size={32} />
            </Stack>
          ) : loadError ? (
            <Stack alignItems="center" justifyContent="center" sx={{ height: '100%', p: 3 }}>
              <Alert severity="error" sx={{ width: '100%' }}>{loadError}</Alert>
            </Stack>
          ) : (
            <List sx={{ py: 0 }}>
              {targetContent
                .filter((item) => item.directory)
                .map((folder) => {
                  const isCurrent = items.some(i => i.id === folder.id);
                  return (
                    <ListItem key={folder.id} disablePadding>
                      <ListItemButton
                        onClick={() => setTargetPath(normalizeWorkspaceFolderPath(getWorkspaceItemLogicalPath(folder)))}
                        disabled={isCurrent}
                      >
                        <ListItemIcon>
                          {folder.customEmoji ? (
                            <Box sx={{ fontSize: 20, display: 'flex', alignItems: 'center', justifyContent: 'center', width: 24 }}>
                              {folder.customEmoji}
                            </Box>
                          ) : (
                            <Folder sx={{ color: folder.folderColor || '#E9A23B' }} />
                          )}
                        </ListItemIcon>
                        <ListItemText 
                           primary={folder.filename}
                           secondary={folder.customEmoji ? '已设置自定义图标' : undefined}
                        />
                        <ChevronRight fontSize="small" sx={{ opacity: 0.5 }} />
                      </ListItemButton>
                    </ListItem>
                  );
                })}
              {targetContent.filter(item => !item.directory).length > 0 && (
                <>
                  <Divider />
                  <Box sx={{ px: 2, py: 1, bgcolor: alpha('#000', 0.02) }}>
                    <Typography variant="caption" color="text.secondary">文件 (不可选为目标)</Typography>
                  </Box>
                  {targetContent.filter(item => !item.directory).map(file => (
                    <ListItem key={file.id} sx={{ opacity: 0.5 }}>
                       <ListItemIcon><InsertDriveFile fontSize="small" /></ListItemIcon>
                       <ListItemText primary={file.filename} primaryTypographyProps={{ variant: 'body2' }} />
                    </ListItem>
                  ))}
                </>
              )}
            </List>
          )}
        </Box>
      </DialogContent>
      <Divider />
      <Box sx={{ px: 3, py: 1.5, display: 'flex', alignItems: 'center', justifyContent: 'space-between', bgcolor: 'action.hover' }}>
         <Typography variant="caption" color="text.secondary">
            目标路径：{targetPath}
         </Typography>
         <Typography variant="caption" color="primary.main" fontWeight={700}>
            已选 {items.length} 个项目
         </Typography>
      </Box>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose}>取消</Button>
        <Button
          variant="contained"
          disabled={moving || loading || Boolean(loadError)}
          onClick={() => handleMove()}
        >
          {moving ? <CircularProgress size={20} sx={{ mr: 1 }} /> : null}
          立即移动
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default MoveItemsDialog;
