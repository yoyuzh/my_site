import React from 'react';
import type { FileItem, FileViewerDefinition } from '../../api/types';
import { FilesPreviewDialog } from './FilesPreviewDialog';
import { ArchiveViewerDialog } from './ArchiveViewerDialog';
import { CustomViewerFrame } from './CustomViewerFrame';
import { EpubViewerDialog } from './EpubViewerDialog';
import { WopiViewerFrame } from './WopiViewerFrame';

export interface FileViewerHostProps {
  file: FileItem | null;
  viewer: FileViewerDefinition | null;
  onClose: () => void;
  onSaved?: (file: FileItem) => void;
}

export const FileViewerHost: React.FC<FileViewerHostProps> = ({ file, viewer, onClose, onSaved }) => {
  if (!file || !viewer) {
    return null;
  }
  const viewerInstanceKey = `${viewer.id}:${file.id}`;
  if (viewer.id === 'epub') {
    return <EpubViewerDialog key={viewerInstanceKey} file={file} onClose={onClose} />;
  }
  if (viewer.id === 'archive') {
    return <ArchiveViewerDialog key={viewerInstanceKey} file={file} onClose={onClose} />;
  }
  if (viewer.id === 'photopea' || viewer.type === 'custom') {
    return <CustomViewerFrame key={viewerInstanceKey} file={file} viewer={viewer} onClose={onClose} />;
  }
  if (viewer.type === 'wopi') {
    if (typeof viewer.props.urlTemplate === 'string') {
      return <CustomViewerFrame key={viewerInstanceKey} file={file} viewer={viewer} onClose={onClose} />;
    }
    return <WopiViewerFrame key={viewerInstanceKey} file={file} viewer={viewer} onClose={onClose} />;
  }
  return <FilesPreviewDialog key={viewerInstanceKey} file={file} onClose={onClose} onSaved={onSaved} />;
};
