import React from 'react';
import { Chip } from '@mui/material';

export type AdminStatusTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

export type AdminStatusBadgeProps = {
  label: React.ReactNode;
  tone?: AdminStatusTone;
};

const toneConfig: Record<AdminStatusTone, { color: 'success' | 'warning' | 'error' | 'info' | 'default'; variant: 'filled' | 'outlined' }> = {
  success: { color: 'success', variant: 'filled' },
  warning: { color: 'warning', variant: 'filled' },
  danger: { color: 'error', variant: 'filled' },
  info: { color: 'info', variant: 'filled' },
  neutral: { color: 'default', variant: 'outlined' },
};

const AdminStatusBadge: React.FC<AdminStatusBadgeProps> = ({ label, tone = 'neutral' }) => {
  const config = toneConfig[tone];

  return (
    <Chip
      label={label}
      size="small"
      color={config.color}
      variant={config.variant}
      sx={{
        height: 24,
        borderRadius: 1.5,
        fontSize: '0.75rem',
        fontWeight: 600,
        maxWidth: '100%',
        '.MuiChip-label': {
          display: 'block',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        },
      }}
    />
  );
};

export default AdminStatusBadge;
