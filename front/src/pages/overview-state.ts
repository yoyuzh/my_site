import { isNativeAppShellLocation } from '@/src/lib/app-shell';

export const APK_DOWNLOAD_PATH = '/downloads/yoyuzh-portal.apk';
export const APK_DOWNLOAD_PUBLIC_URL = 'https://yoyuzh.xyz/downloads/yoyuzh-portal.apk';

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
