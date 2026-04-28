import { Excalidraw } from '@excalidraw/excalidraw';
import '@excalidraw/excalidraw/index.css';
import { Box } from '@mui/material';
import { useMemo } from 'react';

export interface ExcalidrawDocumentEditorProps {
  value: string;
  readOnly?: boolean;
  darkMode?: boolean;
  onChange: (value: string) => void;
  onSaveShortcut?: () => void;
}

function parseInitialData(value: string) {
  try {
    return JSON.parse(value);
  } catch {
    return {
      type: 'excalidraw',
      version: 2,
      source: window.location.origin,
      elements: [],
      appState: { viewBackgroundColor: '#ffffff' },
      files: {},
    };
  }
}

function serializeExcalidraw(elements: readonly unknown[], appState: Record<string, unknown>, files: unknown) {
  return JSON.stringify({
    type: 'excalidraw',
    version: 2,
    source: window.location.origin,
    elements,
    appState: {
      ...appState,
      collaborators: [],
    },
    files,
  });
}

export default function ExcalidrawDocumentEditor({
  value,
  readOnly = false,
  darkMode = false,
  onChange,
  onSaveShortcut,
}: ExcalidrawDocumentEditorProps) {
  const initialData = useMemo(() => parseInitialData(value), [value]);

  return (
    <Box
      sx={{ width: '100%', height: '68vh', minHeight: 520 }}
      onKeyDown={(event) => {
        if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
          event.preventDefault();
          onSaveShortcut?.();
        }
      }}
    >
      <Excalidraw
        initialData={initialData}
        viewModeEnabled={readOnly}
        isCollaborating={false}
        theme={darkMode ? 'dark' : 'light'}
        onChange={(elements, appState, files) => {
          onChange(serializeExcalidraw(elements, appState as unknown as Record<string, unknown>, files));
        }}
      />
    </Box>
  );
}
