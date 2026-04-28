import React from 'react';
import { Box, Paper, Typography, Badge, alpha } from '@mui/material';
import { Folder, InsertDriveFile } from '@mui/icons-material';
import { DragState } from '../../hooks/useWorkspaceDragMove';

interface WorkspaceDragOverlayProps {
  dragState: DragState;
}

const WorkspaceDragOverlay: React.FC<WorkspaceDragOverlayProps> = ({ dragState }) => {
  if (!dragState.isDragging || dragState.sourceItems.length === 0) {
    return null;
  }

  const { sourceItems, currentMousePos, tiltAngle } = dragState;
  const mainItem = sourceItems[0];
  const count = sourceItems.length;

  return (
    <Box
      sx={{
        position: 'fixed',
        top: 0,
        left: 0,
        width: '100vw',
        height: '100vh',
        pointerEvents: 'none',
        zIndex: 9999,
      }}
    >
      <Box
        sx={{
          position: 'absolute',
          left: currentMousePos.x,
          top: currentMousePos.y,
          transform: `translate(12px, 12px) rotate(${tiltAngle}deg)`,
          transition: 'transform 0.05s linear',
        }}
      >
        <Paper
          elevation={8}
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1.5,
            px: 2,
            py: 1,
            borderRadius: 2,
            bgcolor: (theme) => alpha(theme.palette.background.paper, 0.9),
            backdropFilter: 'blur(8px)',
            border: '1px solid',
            borderColor: 'primary.main',
            boxShadow: (theme) => `0 8px 32px ${alpha(theme.palette.primary.main, 0.2)}`,
          }}
        >
          <Badge
            badgeContent={count > 1 ? count : 0}
            color="primary"
            anchorOrigin={{
              vertical: 'top',
              horizontal: 'right',
            }}
          >
            <Box sx={{ fontSize: '1.5rem', display: 'flex', alignItems: 'center' }}>
              {mainItem.customEmoji ? (
                <span>{mainItem.customEmoji}</span>
              ) : mainItem.directory ? (
                <Folder sx={{ color: mainItem.folderColor || '#E9A23B', fontSize: 28 }} />
              ) : (
                <InsertDriveFile sx={{ color: 'text.secondary', fontSize: 28 }} />
              )}
            </Box>
          </Badge>
          <Typography
            variant="body2"
            fontWeight={600}
            sx={{
              maxWidth: 200,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {mainItem.filename}
            {count > 1 && ` 等 ${count} 个项目`}
          </Typography>
        </Paper>
      </Box>
    </Box>
  );
};

export default WorkspaceDragOverlay;
