import type { UserSettings } from '../api/types';

export type OpenWithPreferences = Record<string, string>;

export function normalizeExtension(extension: string | null | undefined) {
  if (!extension) {
    return '';
  }
  return extension.trim().toLowerCase().replace(/^\.+/, '');
}

export function normalizeOpenWithPreferences(preferences: OpenWithPreferences | null | undefined) {
  const normalized: OpenWithPreferences = {};
  Object.entries(preferences ?? {}).forEach(([extension, viewerId]) => {
    const ext = normalizeExtension(extension);
    const viewer = viewerId.trim();
    if (ext && viewer) {
      normalized[ext] = viewer;
    }
  });
  return normalized;
}

export function getDefaultViewerId(settings: Pick<UserSettings, 'defaultOpenWithByExt'> | null | undefined, extension: string) {
  const ext = normalizeExtension(extension);
  if (!ext) {
    return null;
  }
  return settings?.defaultOpenWithByExt?.[ext] ?? null;
}

export function setDefaultViewerPreference(
  preferences: OpenWithPreferences | null | undefined,
  extension: string,
  viewerId: string,
) {
  const ext = normalizeExtension(extension);
  if (!ext || !viewerId.trim()) {
    return normalizeOpenWithPreferences(preferences);
  }
  return {
    ...normalizeOpenWithPreferences(preferences),
    [ext]: viewerId.trim(),
  };
}

export function clearDefaultViewerPreference(preferences: OpenWithPreferences | null | undefined, extension: string) {
  const ext = normalizeExtension(extension);
  const next = normalizeOpenWithPreferences(preferences);
  if (ext) {
    delete next[ext];
  }
  return next;
}
