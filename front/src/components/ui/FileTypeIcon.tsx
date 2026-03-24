import type { LucideIcon } from 'lucide-react';
import {
  AppWindow,
  BookOpenText,
  Database,
  FileArchive,
  FileAudio2,
  FileBadge2,
  FileCode2,
  FileImage,
  FileSpreadsheet,
  FileText,
  FileVideoCamera,
  Folder,
  Presentation,
  SwatchBook,
  Type,
} from 'lucide-react';

import type { FileTypeKind } from '@/src/lib/file-type';
import { cn } from '@/src/lib/utils';

type FileTypeIconSize = 'sm' | 'md' | 'lg';

interface FileTypeTheme {
  badgeClassName: string;
  icon: LucideIcon;
  iconClassName: string;
  surfaceClassName: string;
}

const FILE_TYPE_THEMES: Record<FileTypeKind, FileTypeTheme> = {
  folder: {
    icon: Folder,
    iconClassName: 'text-[#78A1FF]',
    surfaceClassName: 'border border-[#336EFF]/25 bg-[linear-gradient(135deg,rgba(51,110,255,0.24),rgba(15,23,42,0.2))] shadow-[0_16px_30px_-22px_rgba(51,110,255,0.95)]',
    badgeClassName: 'border border-[#336EFF]/20 bg-[#336EFF]/10 text-[#93B4FF]',
  },
  image: {
    icon: FileImage,
    iconClassName: 'text-cyan-300',
    surfaceClassName: 'border border-cyan-400/20 bg-[linear-gradient(135deg,rgba(34,211,238,0.18),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(34,211,238,0.8)]',
    badgeClassName: 'border border-cyan-400/15 bg-cyan-400/10 text-cyan-200',
  },
  pdf: {
    icon: FileBadge2,
    iconClassName: 'text-rose-300',
    surfaceClassName: 'border border-rose-400/20 bg-[linear-gradient(135deg,rgba(251,113,133,0.18),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(251,113,133,0.78)]',
    badgeClassName: 'border border-rose-400/15 bg-rose-400/10 text-rose-200',
  },
  word: {
    icon: FileText,
    iconClassName: 'text-sky-300',
    surfaceClassName: 'border border-sky-400/20 bg-[linear-gradient(135deg,rgba(56,189,248,0.18),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(56,189,248,0.8)]',
    badgeClassName: 'border border-sky-400/15 bg-sky-400/10 text-sky-200',
  },
  spreadsheet: {
    icon: FileSpreadsheet,
    iconClassName: 'text-emerald-300',
    surfaceClassName: 'border border-emerald-400/20 bg-[linear-gradient(135deg,rgba(52,211,153,0.18),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(52,211,153,0.82)]',
    badgeClassName: 'border border-emerald-400/15 bg-emerald-400/10 text-emerald-200',
  },
  presentation: {
    icon: Presentation,
    iconClassName: 'text-amber-300',
    surfaceClassName: 'border border-amber-400/20 bg-[linear-gradient(135deg,rgba(251,191,36,0.18),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(251,191,36,0.82)]',
    badgeClassName: 'border border-amber-400/15 bg-amber-400/10 text-amber-100',
  },
  archive: {
    icon: FileArchive,
    iconClassName: 'text-orange-300',
    surfaceClassName: 'border border-orange-400/20 bg-[linear-gradient(135deg,rgba(251,146,60,0.18),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(251,146,60,0.8)]',
    badgeClassName: 'border border-orange-400/15 bg-orange-400/10 text-orange-100',
  },
  video: {
    icon: FileVideoCamera,
    iconClassName: 'text-fuchsia-300',
    surfaceClassName: 'border border-fuchsia-400/20 bg-[linear-gradient(135deg,rgba(232,121,249,0.18),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(232,121,249,0.78)]',
    badgeClassName: 'border border-fuchsia-400/15 bg-fuchsia-400/10 text-fuchsia-100',
  },
  audio: {
    icon: FileAudio2,
    iconClassName: 'text-teal-300',
    surfaceClassName: 'border border-teal-400/20 bg-[linear-gradient(135deg,rgba(45,212,191,0.18),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(45,212,191,0.8)]',
    badgeClassName: 'border border-teal-400/15 bg-teal-400/10 text-teal-100',
  },
  design: {
    icon: SwatchBook,
    iconClassName: 'text-pink-300',
    surfaceClassName: 'border border-pink-400/20 bg-[linear-gradient(135deg,rgba(244,114,182,0.18),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(244,114,182,0.8)]',
    badgeClassName: 'border border-pink-400/15 bg-pink-400/10 text-pink-100',
  },
  font: {
    icon: Type,
    iconClassName: 'text-lime-300',
    surfaceClassName: 'border border-lime-400/20 bg-[linear-gradient(135deg,rgba(163,230,53,0.18),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(163,230,53,0.8)]',
    badgeClassName: 'border border-lime-400/15 bg-lime-400/10 text-lime-100',
  },
  application: {
    icon: AppWindow,
    iconClassName: 'text-violet-300',
    surfaceClassName: 'border border-violet-400/20 bg-[linear-gradient(135deg,rgba(167,139,250,0.18),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(167,139,250,0.82)]',
    badgeClassName: 'border border-violet-400/15 bg-violet-400/10 text-violet-100',
  },
  ebook: {
    icon: BookOpenText,
    iconClassName: 'text-yellow-200',
    surfaceClassName: 'border border-yellow-300/20 bg-[linear-gradient(135deg,rgba(253,224,71,0.16),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(253,224,71,0.7)]',
    badgeClassName: 'border border-yellow-300/15 bg-yellow-300/10 text-yellow-100',
  },
  code: {
    icon: FileCode2,
    iconClassName: 'text-cyan-200',
    surfaceClassName: 'border border-cyan-300/20 bg-[linear-gradient(135deg,rgba(103,232,249,0.16),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(103,232,249,0.72)]',
    badgeClassName: 'border border-cyan-300/15 bg-cyan-300/10 text-cyan-100',
  },
  text: {
    icon: FileText,
    iconClassName: 'text-slate-200',
    surfaceClassName: 'border border-slate-400/20 bg-[linear-gradient(135deg,rgba(148,163,184,0.18),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(148,163,184,0.55)]',
    badgeClassName: 'border border-slate-400/15 bg-slate-400/10 text-slate-200',
  },
  data: {
    icon: Database,
    iconClassName: 'text-indigo-300',
    surfaceClassName: 'border border-indigo-400/20 bg-[linear-gradient(135deg,rgba(129,140,248,0.18),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(129,140,248,0.8)]',
    badgeClassName: 'border border-indigo-400/15 bg-indigo-400/10 text-indigo-100',
  },
  document: {
    icon: FileText,
    iconClassName: 'text-slate-100',
    surfaceClassName: 'border border-white/10 bg-[linear-gradient(135deg,rgba(148,163,184,0.14),rgba(15,23,42,0.18))] shadow-[0_16px_30px_-22px_rgba(15,23,42,0.9)]',
    badgeClassName: 'border border-white/10 bg-white/10 text-slate-200',
  },
};

const CONTAINER_SIZES: Record<FileTypeIconSize, string> = {
  sm: 'h-10 w-10 rounded-xl',
  md: 'h-12 w-12 rounded-2xl',
  lg: 'h-16 w-16 rounded-[1.35rem]',
};

const ICON_SIZES: Record<FileTypeIconSize, string> = {
  sm: 'h-[18px] w-[18px]',
  md: 'h-[22px] w-[22px]',
  lg: 'h-8 w-8',
};

export function getFileTypeTheme(type: FileTypeKind): FileTypeTheme {
  return FILE_TYPE_THEMES[type] ?? FILE_TYPE_THEMES.document;
}

export function FileTypeIcon({
  type,
  size = 'md',
  className,
}: {
  type: FileTypeKind;
  size?: FileTypeIconSize;
  className?: string;
}) {
  const theme = getFileTypeTheme(type);
  const Icon = theme.icon;

  return (
    <div
      className={cn(
        'flex shrink-0 items-center justify-center backdrop-blur-sm',
        CONTAINER_SIZES[size],
        theme.surfaceClassName,
        className,
      )}
    >
      <Icon className={cn(ICON_SIZES[size], theme.iconClassName)} />
    </div>
  );
}
