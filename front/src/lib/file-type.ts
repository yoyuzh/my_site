export type FileTypeKind =
  | 'folder'
  | 'image'
  | 'pdf'
  | 'word'
  | 'spreadsheet'
  | 'presentation'
  | 'archive'
  | 'video'
  | 'audio'
  | 'design'
  | 'font'
  | 'application'
  | 'ebook'
  | 'code'
  | 'text'
  | 'data'
  | 'document';

export interface FileTypeDescriptor {
  extension: string;
  kind: FileTypeKind;
  label: string;
}

interface ResolveFileTypeOptions {
  fileName: string;
  contentType?: string | null;
}

interface ResolveStoredFileTypeOptions {
  filename: string;
  contentType?: string | null;
  directory: boolean;
}

const FILE_TYPE_LABELS: Record<FileTypeKind, string> = {
  folder: '文件夹',
  image: '图片',
  pdf: 'PDF',
  word: '文档',
  spreadsheet: '表格',
  presentation: '演示文稿',
  archive: '压缩包',
  video: '视频',
  audio: '音频',
  design: '设计稿',
  font: '字体',
  application: '应用包',
  ebook: '电子书',
  code: '代码',
  text: '文本',
  data: '数据',
  document: '文件',
};

const WORD_EXTENSIONS = new Set(['doc', 'docx', 'odt', 'pages', 'rtf']);
const SPREADSHEET_EXTENSIONS = new Set(['csv', 'numbers', 'ods', 'tsv', 'xls', 'xlsx']);
const PRESENTATION_EXTENSIONS = new Set(['key', 'odp', 'ppt', 'pptx']);
const IMAGE_EXTENSIONS = new Set(['avif', 'bmp', 'dng', 'gif', 'heic', 'heif', 'ico', 'jpeg', 'jpg', 'png', 'raw', 'svg', 'tif', 'tiff', 'webp']);
const VIDEO_EXTENSIONS = new Set(['avi', 'flv', 'm4v', 'mkv', 'mov', 'mp4', 'mpeg', 'mpg', 'rm', 'rmvb', 'ts', 'webm', 'wmv']);
const AUDIO_EXTENSIONS = new Set(['aac', 'aiff', 'amr', 'flac', 'm4a', 'mp3', 'ogg', 'opus', 'wav', 'wma']);
const ARCHIVE_EXTENSIONS = new Set(['7z', 'bz2', 'dmg', 'gz', 'iso', 'rar', 'tar', 'tgz', 'xz', 'zip', 'zst']);
const DESIGN_EXTENSIONS = new Set(['ai', 'eps', 'fig', 'indd', 'psd', 'sketch', 'xd']);
const FONT_EXTENSIONS = new Set(['eot', 'otf', 'ttf', 'woff', 'woff2']);
const APPLICATION_EXTENSIONS = new Set(['apk', 'appimage', 'crx', 'deb', 'exe', 'ipa', 'jar', 'msi', 'pkg', 'rpm', 'war']);
const EBOOK_EXTENSIONS = new Set(['azw', 'azw3', 'chm', 'epub', 'mobi']);
const DATA_EXTENSIONS = new Set(['arrow', 'db', 'db3', 'feather', 'parquet', 'sqlite', 'sqlite3']);
const TEXT_EXTENSIONS = new Set(['md', 'markdown', 'rst', 'txt']);
const CODE_EXTENSIONS = new Set([
  'bash',
  'bat',
  'c',
  'cc',
  'cmd',
  'conf',
  'cpp',
  'css',
  'cxx',
  'env',
  'fish',
  'gitignore',
  'go',
  'h',
  'hpp',
  'htm',
  'html',
  'ini',
  'java',
  'js',
  'json',
  'jsonc',
  'jsx',
  'kt',
  'kts',
  'less',
  'log',
  'lua',
  'm',
  'mm',
  'php',
  'ps1',
  'py',
  'rb',
  'rs',
  'sass',
  'scss',
  'sh',
  'sql',
  'svelte',
  'swift',
  'toml',
  'ts',
  'tsx',
  'vue',
  'xml',
  'yaml',
  'yml',
  'zsh',
]);

function getFileExtension(fileName: string) {
  const normalized = fileName.trim().split('/').at(-1) ?? '';
  if (!normalized) {
    return '';
  }

  const lastDotIndex = normalized.lastIndexOf('.');
  if (lastDotIndex > 0 && lastDotIndex < normalized.length - 1) {
    return normalized.slice(lastDotIndex + 1).toLowerCase();
  }

  if (lastDotIndex === 0 && normalized.length > 1) {
    return normalized.slice(1).toLowerCase();
  }

  return '';
}

function buildDescriptor(extension: string, kind: FileTypeKind): FileTypeDescriptor {
  return {
    extension,
    kind,
    label: FILE_TYPE_LABELS[kind],
  };
}

function resolveKindFromContentType(contentType: string | null | undefined, extension: string): FileTypeKind | null {
  if (!contentType) {
    return null;
  }

  const normalized = contentType.toLowerCase();

  if (normalized.includes('pdf')) {
    return 'pdf';
  }
  if (
    normalized.includes('photoshop')
    || normalized.includes('illustrator')
    || normalized.includes('figma')
    || normalized.includes('indesign')
    || normalized.includes('postscript')
  ) {
    return 'design';
  }
  if (
    normalized.includes('wordprocessingml')
    || normalized.includes('msword')
    || normalized.includes('opendocument.text')
    || normalized.includes('rtf')
  ) {
    return 'word';
  }
  if (
    normalized.includes('spreadsheetml')
    || normalized.includes('ms-excel')
    || normalized.includes('opendocument.spreadsheet')
    || normalized.includes('/csv')
    || normalized.includes('comma-separated-values')
  ) {
    return 'spreadsheet';
  }
  if (
    normalized.includes('presentationml')
    || normalized.includes('ms-powerpoint')
    || normalized.includes('opendocument.presentation')
    || normalized.includes('keynote')
  ) {
    return 'presentation';
  }
  if (normalized.includes('epub') || normalized.includes('mobipocket') || normalized.includes('kindle')) {
    return 'ebook';
  }
  if (normalized.startsWith('font/')) {
    return 'font';
  }
  if (
    normalized.includes('android.package-archive')
    || normalized.includes('portable-executable')
    || normalized.includes('java-archive')
    || normalized.includes('msi')
    || normalized.includes('appimage')
    || normalized.includes('x-debian-package')
    || normalized.includes('x-rpm')
  ) {
    return 'application';
  }
  if (
    normalized.includes('zip')
    || normalized.includes('compressed')
    || normalized.includes('archive')
    || normalized.includes('tar')
    || normalized.includes('rar')
    || normalized.includes('7z')
    || normalized.includes('gzip')
    || normalized.includes('bzip2')
    || normalized.includes('xz')
    || normalized.includes('diskimage')
    || normalized.includes('iso9660')
  ) {
    return 'archive';
  }
  if (normalized.startsWith('video/')) {
    return 'video';
  }
  if (normalized.startsWith('audio/')) {
    return 'audio';
  }
  if (normalized.startsWith('image/')) {
    return DESIGN_EXTENSIONS.has(extension) ? 'design' : 'image';
  }
  if (normalized.includes('sqlite') || normalized.includes('database') || normalized.includes('parquet') || normalized.includes('arrow')) {
    return 'data';
  }
  if (
    normalized.includes('json')
    || normalized.includes('javascript')
    || normalized.includes('typescript')
    || normalized.includes('xml')
    || normalized.includes('yaml')
    || normalized.includes('toml')
    || normalized.includes('x-sh')
    || normalized.includes('shellscript')
  ) {
    return 'code';
  }
  if (normalized.startsWith('text/')) {
    return CODE_EXTENSIONS.has(extension) ? 'code' : 'text';
  }

  return null;
}

function resolveKindFromExtension(extension: string): FileTypeKind {
  if (!extension) {
    return 'document';
  }
  if (IMAGE_EXTENSIONS.has(extension)) {
    return 'image';
  }
  if (extension === 'pdf') {
    return 'pdf';
  }
  if (WORD_EXTENSIONS.has(extension)) {
    return 'word';
  }
  if (SPREADSHEET_EXTENSIONS.has(extension)) {
    return 'spreadsheet';
  }
  if (PRESENTATION_EXTENSIONS.has(extension)) {
    return 'presentation';
  }
  if (ARCHIVE_EXTENSIONS.has(extension)) {
    return 'archive';
  }
  if (VIDEO_EXTENSIONS.has(extension)) {
    return 'video';
  }
  if (AUDIO_EXTENSIONS.has(extension)) {
    return 'audio';
  }
  if (DESIGN_EXTENSIONS.has(extension)) {
    return 'design';
  }
  if (FONT_EXTENSIONS.has(extension)) {
    return 'font';
  }
  if (APPLICATION_EXTENSIONS.has(extension)) {
    return 'application';
  }
  if (EBOOK_EXTENSIONS.has(extension)) {
    return 'ebook';
  }
  if (DATA_EXTENSIONS.has(extension)) {
    return 'data';
  }
  if (CODE_EXTENSIONS.has(extension)) {
    return 'code';
  }
  if (TEXT_EXTENSIONS.has(extension)) {
    return 'text';
  }

  return 'document';
}

export function resolveFileType({ fileName, contentType }: ResolveFileTypeOptions): FileTypeDescriptor {
  const extension = getFileExtension(fileName);
  const kind = resolveKindFromContentType(contentType, extension) ?? resolveKindFromExtension(extension);

  return buildDescriptor(extension, kind);
}

export function resolveStoredFileType({ filename, contentType, directory }: ResolveStoredFileTypeOptions): FileTypeDescriptor {
  if (directory) {
    return buildDescriptor('', 'folder');
  }

  return resolveFileType({
    fileName: filename,
    contentType,
  });
}
