import React, { useEffect, useState } from 'react';
import type { FileItem } from '../../api/types';
import CloudreveFileTypeIcon from '../files/CloudreveFileTypeIcon';
import { getThumbnail } from '../../lib/files';

type FileThumbnailProps = {
  file: FileItem;
  variant?: 'compact' | 'card';
};

function isPreviewable(file: FileItem) {
  const contentType = file.contentType ?? '';
  return !file.directory && (contentType.startsWith('image/') || contentType.startsWith('video/'));
}

function getFileExtension(file: FileItem) {
  const index = file.filename.lastIndexOf('.');
  return index >= 0 ? file.filename.slice(index + 1).toLowerCase() : '';
}

const FileThumbnail: React.FC<FileThumbnailProps> = ({ file, variant = 'compact' }) => {
  const [thumbnailUrl, setThumbnailUrl] = useState<string | null>(null);
  const extension = getFileExtension(file);

  useEffect(() => {
    if (!isPreviewable(file)) {
      setThumbnailUrl(null);
      return;
    }

    let disposed = false;
    void getThumbnail(file.id)
      .then((response) => {
        if (!disposed) {
          setThumbnailUrl(response.available ? response.url : null);
        }
      })
      .catch(() => {
        if (!disposed) {
          setThumbnailUrl(null);
        }
      });

    return () => {
      disposed = true;
    };
  }, [file]);

  if (variant === 'card') {
    return (
      <div className="w-full h-full overflow-hidden rounded-[22px] bg-slate-100 text-slate-500 dark:bg-slate-800/80 dark:text-slate-300">
        {thumbnailUrl ? (
          <img src={thumbnailUrl} alt={file.filename} className="w-full h-full object-cover" />
        ) : (
          <div className="flex h-full w-full items-center justify-center overflow-hidden bg-slate-100 dark:bg-slate-900">
            <div className="flex h-full w-full flex-col items-center justify-center gap-3 px-4">
              <CloudreveFileTypeIcon file={file} size={42} />
              <div className="rounded-full border border-slate-300 bg-white/92 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500 shadow-sm dark:border-slate-700 dark:bg-slate-950/72 dark:text-slate-300">
                {extension || 'FILE'}
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="w-10 h-10 rounded-lg overflow-hidden bg-brand-light/10 text-brand-light flex items-center justify-center flex-shrink-0">
      {thumbnailUrl ? (
        <img src={thumbnailUrl} alt={file.filename} className="w-full h-full object-cover" />
      ) : (
        <CloudreveFileTypeIcon file={file} size={18} />
      )}
    </div>
  );
};

export default FileThumbnail;
