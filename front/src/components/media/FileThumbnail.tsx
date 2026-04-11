import { File, FileAudio, FileImage, FileText, FileVideo, Folder } from 'lucide-react';
import { type FileItem } from '@/src/lib/files';
import { getApiBaseUrl } from '@/src/lib/api';
import { cn } from '@/src/lib/utils';

interface FileThumbnailProps {
  file: FileItem;
  className?: string;
}

export function FileThumbnail({ file, className }: FileThumbnailProps) {
  if (file.directory) {
    return <Folder className={cn("h-full w-full text-blue-500", className)} />;
  }

  // 如果有缩略图 Key，展示缩略图
  if (file.thumbnailKey) {
    const thumbUrl = `${getApiBaseUrl()}/v2/files/blobs/${file.thumbnailKey}/content`;
    return (
      <img
        src={thumbUrl}
        alt={file.filename}
        className={cn("h-full w-full object-cover rounded shadow-inner", className)}
        onError={(e) => {
          // 如果缩略图加载失败，回退到图标
          (e.target as HTMLImageElement).style.display = 'none';
        }}
      />
    );
  }

  // 根据 contentType 展示图标
  const type = file.contentType || '';
  if (type.startsWith('image/')) return <FileImage className={cn("h-full w-full text-green-500", className)} />;
  if (type.startsWith('video/')) return <FileVideo className={cn("h-full w-full text-purple-500", className)} />;
  if (type.startsWith('audio/')) return <FileAudio className={cn("h-full w-full text-amber-500", className)} />;
  if (type.includes('pdf') || type.includes('word') || type.includes('text')) {
    return <FileText className={cn("h-full w-full text-blue-400", className)} />;
  }

  return <File className={cn("h-full w-full text-gray-400", className)} />;
}
