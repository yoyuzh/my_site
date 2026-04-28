import type { FileItem, FileViewerConfig, FileViewerDefinition, FileViewerTemplate } from '../api/types';
import { getDefaultViewerId, normalizeExtension, type OpenWithPreferences } from './file-open-preferences';

export interface FileViewerMatch {
  extension: string;
  recommendedViewers: FileViewerDefinition[];
  availableViewers: FileViewerDefinition[];
  allViewers: FileViewerDefinition[];
  defaultViewer: FileViewerDefinition | null;
}

export function getFileExtension(file: Pick<FileItem, 'filename'> | string) {
  const filename = typeof file === 'string' ? file : file.filename;
  const name = filename.trim();
  const lastDot = name.lastIndexOf('.');
  if (lastDot <= 0 || lastDot === name.length - 1) {
    return '';
  }
  return normalizeExtension(name.slice(lastDot + 1));
}

export function normalizeViewerConfig(config: FileViewerConfig | null | undefined): FileViewerConfig {
  const fileViewers = (config?.fileViewers ?? []).map((viewer) => ({
    ...viewer,
    extensions: viewer.extensions.map(normalizeExtension).filter(Boolean),
  }));
  const defaultViewerMapping: Record<string, string> = {};
  Object.entries(config?.defaultViewerMapping ?? {}).forEach(([extension, viewerId]) => {
    const ext = normalizeExtension(extension);
    if (ext && viewerId.trim()) {
      defaultViewerMapping[ext] = viewerId.trim();
    }
  });
  return { fileViewers, defaultViewerMapping };
}

export function getViewersForExtension(config: FileViewerConfig, extension: string) {
  const ext = normalizeExtension(extension);
  if (!ext) {
    return [];
  }
  return normalizeViewerConfig(config).fileViewers.filter((viewer) => viewer.extensions.includes(ext));
}

export function getAllFileViewers(config: FileViewerConfig) {
  return normalizeViewerConfig(config).fileViewers;
}

export function isViewerAvailableForFile(
  viewer: FileViewerDefinition,
  file: Pick<FileItem, 'size'>,
  extension: string,
) {
  const normalizedExtension = normalizeExtension(extension);
  if (!viewer.extensions.includes(normalizedExtension)) {
    return false;
  }
  return viewer.maxSizeBytes == null || file.size <= viewer.maxSizeBytes;
}

export function getAvailableViewersForFile(
  config: FileViewerConfig,
  file: Pick<FileItem, 'filename' | 'size'>,
  extension = getFileExtension(file),
) {
  return getAllFileViewers(config).filter((viewer) => isViewerAvailableForFile(viewer, file, extension));
}

export function getRecommendedViewersForFile(
  config: FileViewerConfig,
  file: Pick<FileItem, 'filename' | 'size'>,
  extension = getFileExtension(file),
) {
  const availableViewers = getAvailableViewersForFile(config, file, extension);
  const recommendedViewers = getRecommendedViewers(config, extension);
  return recommendedViewers.filter((viewer) => availableViewers.some((candidate) => candidate.id === viewer.id));
}

export function getRecommendedViewers(config: FileViewerConfig, extension: string) {
  const viewers = getViewersForExtension(config, extension);
  const normalized = normalizeViewerConfig(config);
  const configuredDefaultId = normalized.defaultViewerMapping[normalizeExtension(extension)];
  const recommended = viewers.filter((viewer) => viewer.recommended);
  if (configuredDefaultId && !recommended.some((viewer) => viewer.id === configuredDefaultId)) {
    const configuredDefault = viewers.find((viewer) => viewer.id === configuredDefaultId);
    if (configuredDefault) {
      return [configuredDefault, ...recommended];
    }
  }
  return recommended.length > 0 ? recommended : viewers.slice(0, 3);
}

export function matchFileViewers(
  file: Pick<FileItem, 'filename' | 'size'>,
  config: FileViewerConfig,
  preferences?: OpenWithPreferences | null,
): FileViewerMatch {
  const extension = getFileExtension(file);
  const normalized = normalizeViewerConfig(config);
  const allViewers = getAllFileViewers(normalized);
  const availableViewers = getAvailableViewersForFile(normalized, file, extension);
  const preferredId = getDefaultViewerId({ defaultOpenWithByExt: preferences ?? {} }, extension);
  const defaultViewerId = preferredId ?? normalized.defaultViewerMapping[extension] ?? null;
  const defaultViewer = defaultViewerId ? availableViewers.find((viewer) => viewer.id === defaultViewerId) ?? null : null;
  const recommendedViewers = getRecommendedViewersForFile(normalized, file, extension);

  return {
    extension,
    recommendedViewers,
    availableViewers,
    allViewers,
    defaultViewer,
  };
}

export function getTemplateForViewer(config: FileViewerConfig, viewerId: string, extension: string): FileViewerTemplate | null {
  const ext = normalizeExtension(extension);
  const viewer = normalizeViewerConfig(config).fileViewers.find((candidate) => candidate.id === viewerId);
  return viewer?.templates.find((template) => normalizeExtension(template.extension) === ext) ?? null;
}
