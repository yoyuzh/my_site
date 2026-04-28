import type { ElementType } from 'react';
import type { SxProps, Theme } from '@mui/material/styles';
import type { SvgIconProps } from '@mui/material/SvgIcon';
import type { FileItem } from '../../api/types';
import Book from '../icons/cloudreve/Book';
import Document from '../icons/cloudreve/Document';
import DocumentFlowchart from '../icons/cloudreve/DocumentFlowchart';
import DocumentPDF from '../icons/cloudreve/DocumentPDF';
import FileExclBox from '../icons/cloudreve/FileExclBox';
import FilePowerPointBox from '../icons/cloudreve/FilePowerPointBox';
import FileWordBox from '../icons/cloudreve/FileWordBox';
import Folder from '../icons/cloudreve/Folder';
import FolderZip from '../icons/cloudreve/FolderZip';
import Image from '../icons/cloudreve/Image';
import Markdown from '../icons/cloudreve/Markdown';
import MusicNote1 from '../icons/cloudreve/MusicNote1';
import Notepad from '../icons/cloudreve/Notepad';
import Raw from '../icons/cloudreve/Raw';
import Video from '../icons/cloudreve/Video';
import Whiteboard from '../icons/cloudreve/Whiteboard';
import WindowApps from '../icons/cloudreve/WindowApps';

type FileLike = Pick<FileItem, 'filename' | 'directory' | 'contentType' | 'folderColor'>;

type IconComponent = ElementType<SvgIconProps>;

type IconDefinition = {
  component: IconComponent;
  color: string;
};

const WORD_EXTS = new Set(['doc', 'docx', 'odt', 'rtf']);
const EXCEL_EXTS = new Set(['xls', 'xlsx', 'csv', 'tsv', 'ods']);
const POWERPOINT_EXTS = new Set(['ppt', 'pptx', 'odp', 'key']);
const ARCHIVE_EXTS = new Set(['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'tgz']);
const MARKDOWN_EXTS = new Set(['md', 'markdown', 'mdx']);
const TEXT_EXTS = new Set([
  'txt',
  'text',
  'log',
  'json',
  'yaml',
  'yml',
  'toml',
  'xml',
  'ini',
  'conf',
  'config',
  'sh',
  'bash',
  'zsh',
  'js',
  'jsx',
  'ts',
  'tsx',
  'java',
  'kt',
  'go',
  'rs',
  'py',
  'php',
  'sql',
  'css',
  'scss',
  'less',
  'html',
  'vue',
  'c',
  'cc',
  'cpp',
  'h',
  'hpp',
  'cs',
]);
const IMAGE_EXTS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg', 'avif', 'heic', 'heif']);
const VIDEO_EXTS = new Set(['mp4', 'mov', 'm4v', 'avi', 'mkv', 'webm', 'flv', 'wmv', 'mpeg', 'mpg']);
const AUDIO_EXTS = new Set(['mp3', 'wav', 'aac', 'flac', 'ogg', 'm4a', 'opus', 'wma']);
const BOOK_EXTS = new Set(['epub', 'mobi', 'azw3']);
const RAW_EXTS = new Set(['raw', 'dng', 'cr2', 'cr3', 'nef', 'arw', 'rw2', 'orf', 'raf', 'tif', 'tiff', 'psd', 'psb', 'ai', 'sketch', 'xcf']);
const EXECUTABLE_EXTS = new Set(['exe', 'msi', 'apk', 'app', 'dmg', 'pkg', 'ipa']);
const FLOWCHART_EXTS = new Set(['drawio', 'dio']);
const WHITEBOARD_EXTS = new Set(['excalidraw']);

function getExtension(filename: string) {
  const index = filename.lastIndexOf('.');
  return index >= 0 ? filename.slice(index + 1).toLowerCase() : '';
}

function resolveDefinition(file: FileLike): IconDefinition {
  if (file.directory) {
    return { component: Folder, color: file.folderColor || '#e9a23b' };
  }

  const ext = getExtension(file.filename);
  const contentType = file.contentType || '';

  if (FLOWCHART_EXTS.has(ext)) {
    return { component: DocumentFlowchart, color: '#0f9d8a' };
  }
  if (WHITEBOARD_EXTS.has(ext)) {
    return { component: Whiteboard, color: '#7c4dff' };
  }
  if (MARKDOWN_EXTS.has(ext)) {
    return { component: Markdown, color: '#2563eb' };
  }
  if (ext === 'pdf' || contentType === 'application/pdf') {
    return { component: DocumentPDF, color: '#ef4444' };
  }
  if (WORD_EXTS.has(ext)) {
    return { component: FileWordBox, color: '#2563eb' };
  }
  if (EXCEL_EXTS.has(ext)) {
    return { component: FileExclBox, color: '#16a34a' };
  }
  if (POWERPOINT_EXTS.has(ext)) {
    return { component: FilePowerPointBox, color: '#f97316' };
  }
  if (ARCHIVE_EXTS.has(ext)) {
    return { component: FolderZip, color: '#f59e0b' };
  }
  if (BOOK_EXTS.has(ext)) {
    return { component: Book, color: '#059669' };
  }
  if (RAW_EXTS.has(ext)) {
    return { component: Raw, color: '#9333ea' };
  }
  if (EXECUTABLE_EXTS.has(ext)) {
    return { component: WindowApps, color: '#0f172a' };
  }
  if (contentType.startsWith('image/') || IMAGE_EXTS.has(ext)) {
    return { component: Image, color: '#3b82f6' };
  }
  if (contentType.startsWith('video/') || VIDEO_EXTS.has(ext)) {
    return { component: Video, color: '#ec4899' };
  }
  if (contentType.startsWith('audio/') || AUDIO_EXTS.has(ext)) {
    return { component: MusicNote1, color: '#a855f7' };
  }
  if (contentType.startsWith('text/') || TEXT_EXTS.has(ext)) {
    return { component: Notepad, color: '#64748b' };
  }

  return { component: Document, color: '#64748b' };
}

export interface CloudreveFileTypeIconProps {
  file: FileLike;
  size?: number | string;
  selected?: boolean;
  sx?: SxProps<Theme>;
}

export default function CloudreveFileTypeIcon({
  file,
  size = 24,
  selected = false,
  sx,
}: CloudreveFileTypeIconProps) {
  const definition = resolveDefinition(file);
  const Component = definition.component;
  const color = selected ? 'var(--mui-palette-primary-main)' : definition.color;

  return (
    <Component
      sx={{
        width: size,
        height: size,
        color,
        display: 'block',
        ...sx,
      }}
    />
  );
}
