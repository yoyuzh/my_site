import React, { useEffect, useState } from 'react';
import { FileImage, FileText, Film, Folder } from 'lucide-react';
import type { FileItem } from '../../api/types';
import { getThumbnail } from '../../lib/files';

type FileThumbnailProps = {
  file: FileItem;
};

function isPreviewable(file: FileItem) {
  return !file.directory && (file.contentType.startsWith('image/') || file.contentType.startsWith('video/'));
}

function FallbackIcon({ file }: FileThumbnailProps) {
  if (file.directory) {
    return <Folder size={18} />;
  }
  if (file.contentType.startsWith('image/')) {
    return <FileImage size={18} />;
  }
  if (file.contentType.startsWith('video/')) {
    return <Film size={18} />;
  }
  return <FileText size={18} />;
}

const FileThumbnail: React.FC<FileThumbnailProps> = ({ file }) => {
  const [thumbnailUrl, setThumbnailUrl] = useState<string | null>(null);

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

  return (
    <div className="w-10 h-10 rounded-lg overflow-hidden bg-brand-light/10 text-brand-light flex items-center justify-center flex-shrink-0">
      {thumbnailUrl ? (
        <img src={thumbnailUrl} alt={file.filename} className="w-full h-full object-cover" />
      ) : (
        <FallbackIcon file={file} />
      )}
    </div>
  );
};

export default FileThumbnail;
