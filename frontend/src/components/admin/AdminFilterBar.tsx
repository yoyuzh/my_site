import React from 'react';
import { Box, Paper, Stack } from '@mui/material';

export type AdminFilterBarProps = {
  children: React.ReactNode;
  actions?: React.ReactNode;
  summary?: React.ReactNode;
};

const AdminFilterBar: React.FC<AdminFilterBarProps> = ({ children, actions, summary }) => {
  return (
    <Paper
      elevation={0}
      sx={{
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 3,
        px: 2,
        py: 1.5,
        bgcolor: 'background.paper',
      }}
    >
      <Stack spacing={1.5}>
        <Stack
          direction={{ xs: 'column', lg: 'row' }}
          spacing={1.5}
          useFlexGap
          sx={{
            alignItems: { xs: 'stretch', lg: 'center' },
            justifyContent: 'space-between',
          }}
        >
          <Box
            sx={{
              display: 'flex',
              flex: 1,
              flexWrap: 'wrap',
              gap: 1,
              alignItems: 'center',
            }}
          >
            {children}
          </Box>
          {actions ? (
            <Box
              sx={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: 1,
                alignItems: 'center',
                justifyContent: { xs: 'flex-start', lg: 'flex-end' },
              }}
            >
              {actions}
            </Box>
          ) : null}
        </Stack>
        {summary ? <Box sx={{ color: 'text.secondary', fontSize: '0.8125rem' }}>{summary}</Box> : null}
      </Stack>
    </Paper>
  );
};

export default AdminFilterBar;
