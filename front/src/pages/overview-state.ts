import { isNativeAppShellLocation } from '@/src/lib/app-shell';

export const APK_DOWNLOAD_PATH = 'https://api.yoyuzh.xyz/api/app/android/download';
export const APK_DOWNLOAD_PUBLIC_URL = 'https://api.yoyuzh.xyz/api/app/android/download';

function normalizeVersionParts(value: string | null | undefined) {
  if (!value) {
    return [];
  }

  return value
    .split(/[^0-9A-Za-z]+/)
    .filter(Boolean)
    .map((part) => (/^\d+$/.test(part) ? Number(part) : part.toLowerCase()));
}

function compareVersionParts(left: Array<number | string>, right: Array<number | string>) {
  const length = Math.max(left.length, right.length);
  for (let index = 0; index < length; index += 1) {
    const leftPart = left[index] ?? 0;
    const rightPart = right[index] ?? 0;

    if (leftPart === rightPart) {
      continue;
    }

    if (typeof leftPart === 'number' && typeof rightPart === 'number') {
      return leftPart > rightPart ? 1 : -1;
    }

    return String(leftPart).localeCompare(String(rightPart), 'en');
  }

  return 0;
}

export function isAndroidReleaseNewer({
  currentVersionCode,
  currentVersionName,
  releaseVersionCode,
  releaseVersionName,
}: {
  currentVersionCode?: string | null;
  currentVersionName?: string | null;
  releaseVersionCode?: string | null;
  releaseVersionName?: string | null;
}) {
  if (currentVersionCode && releaseVersionCode && /^\d+$/.test(currentVersionCode) && /^\d+$/.test(releaseVersionCode)) {
    return BigInt(releaseVersionCode) > BigInt(currentVersionCode);
  }

  if (currentVersionName && releaseVersionName) {
    return compareVersionParts(normalizeVersionParts(currentVersionName), normalizeVersionParts(releaseVersionName)) < 0;
  }

  return true;
}

export function formatApkPublishedAtLabel(publishedAt: string | null) {
  if (!publishedAt) {
    return null;
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(publishedAt));
}

function formatOverviewStorageSize(size: number) {
  if (size <= 0) {
    return '0 B';
  }

  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const index = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1);
  const value = size / 1024 ** index;
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

export function getDesktopOverviewSectionColumns(showApkDownload: boolean) {
  return {
    main: showApkDownload
      ? ['recent-files', 'transfer-workbench', 'apk-download']
      : ['recent-files', 'transfer-workbench'],
    sidebar: ['quick-actions', 'storage', 'account'],
  };
}

export function getDesktopOverviewStretchSection(showApkDownload: boolean) {
  return showApkDownload ? 'apk-download' : 'transfer-workbench';
}

export function getOverviewStorageQuotaLabel(storageQuotaBytes: number) {
  return `已使用 / 共 ${formatOverviewStorageSize(storageQuotaBytes)}`;
}

export function getOverviewLoadErrorMessage(isPostLoginFailure: boolean) {
  if (isPostLoginFailure) {
    return '登录已成功，但总览数据加载失败，请稍后重试。';
  }

  return '总览数据加载失败，请稍后重试。';
}

function resolveOverviewLocation() {
  if (typeof globalThis.location !== 'undefined') {
    return globalThis.location;
  }

  if (typeof window !== 'undefined') {
    return window.location;
  }

  return null;
}

export function shouldShowOverviewApkDownload(location: Location | URL | null = resolveOverviewLocation()) {
  return !isNativeAppShellLocation(location);
}

export function getMobileOverviewApkEntryMode(location: Location | URL | null = resolveOverviewLocation()) {
  return isNativeAppShellLocation(location) ? 'update' : 'download';
}
