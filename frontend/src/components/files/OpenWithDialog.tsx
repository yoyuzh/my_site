import React, { useMemo, useState } from 'react';
import {
  Box,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Typography,
  alpha,
  useTheme,
} from '@mui/material';
import {
  Archive,
  BookOpenText,
  ChevronDown,
  ChevronRight,
  File,
  FileCode2,
  FileImage,
  FileText,
  Music4,
  PlayCircle,
} from 'lucide-react';
import type { FileItem, FileViewerDefinition } from '../../api/types';
import { formatBytes } from '../../lib/format';
import drawioViewer from '../../assets/cloudreve-viewers/drawio.svg';
import excalidrawViewer from '../../assets/cloudreve-viewers/excalidraw.svg';
import googleViewer from '../../assets/cloudreve-viewers/gdrive.png';
import microsoftViewer from '../../assets/cloudreve-viewers/m365.svg';
import monacoViewer from '../../assets/cloudreve-viewers/monaco.svg';
import photopeaViewer from '../../assets/cloudreve-viewers/photopea.png';
import videoViewer from '../../assets/cloudreve-viewers/artplayer.png';

function ViewerIcon({ viewer, disabled }: { viewer: FileViewerDefinition; disabled?: boolean }) {
  const theme = useTheme();
  const brandedIcons: Record<string, string> = {
    'drawio': drawioViewer,
    'excalidraw': excalidrawViewer,
    'microsoft-office': microsoftViewer,
    'google-docs': googleViewer,
    'code-monaco': monacoViewer,
    'photopea': photopeaViewer,
    'video': videoViewer,
  };

  let icon: React.ReactNode = <File size={22} strokeWidth={2} />;
  if (viewer.id in brandedIcons) {
    icon = (
      <Box
        component="img"
        src={brandedIcons[viewer.id]}
        alt={viewer.displayName}
        sx={{
          width: 24,
          height: 24,
          objectFit: 'contain',
          filter: disabled ? 'grayscale(1)' : 'none',
        }}
      />
    );
  } else if (viewer.icon.includes('image')) icon = <FileImage size={22} strokeWidth={2} />;
  else if (viewer.icon.includes('archive')) icon = <Archive size={22} strokeWidth={2} />;
  else if (viewer.icon.includes('code')) icon = <FileCode2 size={22} strokeWidth={2} />;
  else if (viewer.icon.includes('markdown')) icon = <BookOpenText size={22} strokeWidth={2} />;
  else if (viewer.icon.includes('video')) icon = <PlayCircle size={22} strokeWidth={2} />;
  else if (viewer.icon.includes('music')) icon = <Music4 size={22} strokeWidth={2} />;
  else if (viewer.id.includes('pdf')) icon = <FileText size={22} strokeWidth={2} />;

  return (
    <Box
      sx={{
        width: 40,
        height: 40,
        borderRadius: 1.5,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: disabled ? alpha(theme.palette.action.disabled, 0.05) : alpha(theme.palette.primary.main, 0.08),
        color: disabled ? theme.palette.text.disabled : theme.palette.primary.main,
        transition: theme.transitions.create(['background-color', 'color']),
      }}
    >
      {icon}
    </Box>
  );
}

export interface OpenWithDialogProps {
  open: boolean;
  file: FileItem | null;
  extension: string;
  recommendedViewers: FileViewerDefinition[];
  allViewers: FileViewerDefinition[];
  availableViewerIds: string[];
  onClose: () => void;
  onSelect: (viewer: FileViewerDefinition, alwaysUse: boolean) => void;
}

export const OpenWithDialog: React.FC<OpenWithDialogProps> = ({
  open,
  file,
  extension,
  recommendedViewers,
  allViewers,
  availableViewerIds,
  onClose,
  onSelect,
}) => {
  const theme = useTheme();
  const [showAll, setShowAll] = useState(false);
  const [alwaysUse, setAlwaysUse] = useState(false);
  const availableViewerIdSet = useMemo(() => new Set(availableViewerIds), [availableViewerIds]);

  const recommendedList = useMemo(() => {
    if (recommendedViewers.length > 0) return recommendedViewers;
    return allViewers.filter(v => availableViewerIdSet.has(v.id)).slice(0, 2);
  }, [recommendedViewers, allViewers, availableViewerIdSet]);

  const otherList = useMemo(() => {
    const recommendedIds = new Set(recommendedList.map(v => v.id));
    return allViewers.filter(v => !recommendedIds.has(v.id));
  }, [allViewers, recommendedList]);

  function getViewerDescription(viewer: FileViewerDefinition) {
    const supportedByExtension = viewer.extensions.includes(extension);
    const withinSizeLimit = viewer.maxSizeBytes == null || (file != null && file.size <= viewer.maxSizeBytes);
    
    if (!supportedByExtension) {
      const supportedExtensions = viewer.extensions.slice(0, 3).join('、');
      return supportedExtensions ? `不支持当前格式 (支持 ${supportedExtensions} 等)` : '不支持当前格式';
    }
    if (!withinSizeLimit) {
      return `文件过大 (上限 ${formatBytes(viewer.maxSizeBytes!)})`;
    }
    
    if (viewer.type === 'custom') return '外部应用';
    if (viewer.type === 'wopi') return '在线 Office';
    return '内置阅读器';
  }

  React.useEffect(() => {
    if (open) {
      setShowAll(false);
      setAlwaysUse(false);
    }
  }, [open, file?.id]);

  const renderViewerItem = (viewer: FileViewerDefinition) => {
    const isAvailable = availableViewerIdSet.has(viewer.id);
    return (
      <ListItemButton
        key={viewer.id}
        onClick={() => isAvailable && onSelect(viewer, alwaysUse)}
        disabled={!isAvailable}
        sx={{
          py: 1.5,
          px: 2,
          borderRadius: 2,
          mb: 0.5,
          '&.Mui-disabled': {
            opacity: 0.6,
          },
        }}
      >
        <ListItemIcon sx={{ minWidth: 56 }}>
          <ViewerIcon viewer={viewer} disabled={!isAvailable} />
        </ListItemIcon>
        <ListItemText
          primary={
            <Typography variant="subtitle1" fontWeight={600} sx={{ lineHeight: 1.2 }}>
              {viewer.displayName}
            </Typography>
          }
          secondary={
            <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
              {getViewerDescription(viewer)}
            </Typography>
          }
        />
        {isAvailable && <ChevronRight size={18} color={theme.palette.action.disabled} />}
      </ListItemButton>
    );
  };

  return (
    <Dialog 
      open={open} 
      onClose={onClose} 
      fullWidth 
      maxWidth="xs"
      PaperProps={{
        sx: { borderRadius: 3, boxShadow: theme.shadows[10] }
      }}
    >
      <DialogTitle sx={{ pb: 2, pt: 3 }}>
        <Stack spacing={1}>
          <Typography variant="h6" fontWeight={800} sx={{ letterSpacing: -0.5 }}>
            选择打开方式
          </Typography>
          {file && (
            <Box 
              sx={{ 
                p: 1.5, 
                borderRadius: 2, 
                bgcolor: alpha(theme.palette.text.primary, 0.04),
                border: '1px solid',
                borderColor: alpha(theme.palette.divider, 0.1)
              }}
            >
              <Stack direction="row" spacing={1.5} alignItems="center">
                <File size={18} color={theme.palette.text.secondary} />
                <Box sx={{ minWidth: 0, flex: 1 }}>
                  <Typography variant="body2" fontWeight={600} noWrap sx={{ display: 'block' }}>
                    {file.filename}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {extension.toUpperCase() || '未知'} · {formatBytes(file.size)}
                  </Typography>
                </Box>
              </Stack>
            </Box>
          )}
        </Stack>
      </DialogTitle>

      <DialogContent sx={{ px: 2, py: 0 }}>
        {allViewers.length > 0 ? (
          <Box sx={{ pb: 2 }}>
            <Typography variant="overline" sx={{ px: 2, mb: 1, display: 'block', color: 'text.secondary', fontWeight: 700 }}>
              建议的方式
            </Typography>
            <List disablePadding>
              {recommendedList.map(renderViewerItem)}
            </List>

            {!showAll && otherList.length > 0 && (
              <Button
                fullWidth
                onClick={() => setShowAll(true)}
                endIcon={<ChevronDown size={16} />}
                sx={{ 
                  mt: 1, 
                  py: 1, 
                  borderRadius: 2,
                  color: 'text.secondary',
                  justifyContent: 'center',
                  '&:hover': { bgcolor: alpha(theme.palette.primary.main, 0.04) }
                }}
              >
                更多打开方式
              </Button>
            )}

            {showAll && otherList.length > 0 && (
              <>
                <Divider sx={{ my: 2, mx: 2, borderStyle: 'dashed' }} />
                <Typography variant="overline" sx={{ px: 2, mb: 1, display: 'block', color: 'text.secondary', fontWeight: 700 }}>
                  其他选项
                </Typography>
                <List disablePadding>
                  {otherList.map(renderViewerItem)}
                </List>
              </>
            )}
          </Box>
        ) : (
          <Box
            sx={{
              py: 6,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              textAlign: 'center'
            }}
          >
            <File size={44} color={theme.palette.action.disabled} style={{ marginBottom: 16 }} />
            <Typography color="text.secondary" variant="body2">
              未发现可用的打开方式
            </Typography>
          </Box>
        )}
      </DialogContent>

      <Divider />
      
      <DialogActions sx={{ p: 2, px: 3, justifyContent: 'space-between', bgcolor: alpha(theme.palette.text.primary, 0.01) }}>
        <Box 
          component="label" 
          sx={{ 
            display: 'flex', 
            alignItems: 'center', 
            cursor: 'pointer',
            '&:hover': { color: 'primary.main' },
            transition: 'color 0.2s'
          }}
        >
          <Checkbox 
            size="small" 
            checked={alwaysUse} 
            onChange={(e) => setAlwaysUse(e.target.checked)}
            sx={{ p: 0.5, mr: 0.5 }}
          />
          <Typography variant="body2" sx={{ userSelect: 'none' }}>始终使用此应用</Typography>
        </Box>
        <Button 
          variant="text" 
          onClick={onClose}
          sx={{ borderRadius: 2, px: 2 }}
        >
          取消
        </Button>
      </DialogActions>
    </Dialog>
  );
};
