import React from 'react';
import { Alert, Box, CircularProgress, Paper, Stack, Typography } from '@mui/material';

type AdminPageProps = {
  title: string;
  description?: string;
  toolbar?: React.ReactNode;
  isLoading?: boolean;
  isError?: boolean;
  errorText?: string;
  children: React.ReactNode;
};

const AdminPage: React.FC<AdminPageProps> = ({
  title,
  description,
  toolbar,
  isLoading = false,
  isError = false,
  errorText,
  children,
}) => {
  let content: React.ReactNode = children;

  if (isLoading) {
    content = (
      <Paper
        elevation={0}
        sx={{
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 3,
          minHeight: 320,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: 'background.paper',
        }}
      >
        <Stack spacing={1.5} alignItems="center">
          <CircularProgress size={28} />
          <Typography variant="body2" color="text.secondary">
            Loading admin content...
          </Typography>
        </Stack>
      </Paper>
    );
  } else if (isError) {
    content = (
      <Alert severity="error" sx={{ borderRadius: 3 }}>
        {errorText ?? 'Unable to load this admin view.'}
      </Alert>
    );
  }

  return (
    <Stack spacing={2.5}>
      <Paper
        elevation={0}
        sx={{
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 3,
          px: { xs: 2, md: 2.5 },
          py: 2,
          bgcolor: 'background.paper',
        }}
      >
        <Stack
          direction={{ xs: 'column', lg: 'row' }}
          spacing={2}
          sx={{
            alignItems: { xs: 'flex-start', lg: 'center' },
            justifyContent: 'space-between',
          }}
        >
          <Box sx={{ minWidth: 0 }}>
            <Typography
              variant="overline"
              sx={{ display: 'block', color: 'text.secondary', fontWeight: 700, letterSpacing: '0.08em' }}
            >
              Admin Workspace
            </Typography>
            <Typography variant="h5" sx={{ fontWeight: 700 }}>
              {title}
            </Typography>
            {description ? (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75, maxWidth: 960 }}>
                {description}
              </Typography>
            ) : null}
          </Box>
          {toolbar ? (
            <Box
              sx={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: 1,
                alignItems: 'center',
                justifyContent: { xs: 'flex-start', lg: 'flex-end' },
                width: { xs: '100%', lg: 'auto' },
              }}
            >
              {toolbar}
            </Box>
          ) : null}
        </Stack>
      </Paper>

      {content}
    </Stack>
  );
};

export default AdminPage;
