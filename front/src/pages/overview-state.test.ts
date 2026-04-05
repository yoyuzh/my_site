import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  APK_DOWNLOAD_PATH,
  APK_DOWNLOAD_PUBLIC_URL,
  formatApkPublishedAtLabel,
  getDesktopOverviewSectionColumns,
  getDesktopOverviewStretchSection,
  getMobileOverviewApkEntryMode,
  getOverviewLoadErrorMessage,
  getOverviewStorageQuotaLabel,
  isAndroidReleaseNewer,
  shouldShowOverviewApkDownload,
} from './overview-state';

test('post-login failures are presented as overview initialization issues', () => {
  assert.equal(
    getOverviewLoadErrorMessage(true),
    '登录已成功，但总览数据加载失败，请稍后重试。'
  );
});

test('generic overview failures stay generic when not coming right after login', () => {
  assert.equal(
    getOverviewLoadErrorMessage(false),
    '总览数据加载失败，请稍后重试。'
  );
});

test('overview exposes a backend download endpoint for apk delivery', () => {
  assert.equal(APK_DOWNLOAD_PATH, 'https://api.yoyuzh.xyz/api/app/android/download');
  assert.equal(APK_DOWNLOAD_PUBLIC_URL, 'https://api.yoyuzh.xyz/api/app/android/download');
});

test('overview hides the apk download entry inside the native app shell', () => {
  assert.equal(shouldShowOverviewApkDownload(new URL('https://yoyuzh.xyz')), true);
  assert.equal(shouldShowOverviewApkDownload(new URL('https://localhost')), false);
});

test('mobile overview switches from download mode to update mode inside the native shell', () => {
  assert.equal(getMobileOverviewApkEntryMode(new URL('https://yoyuzh.xyz')), 'download');
  assert.equal(getMobileOverviewApkEntryMode(new URL('https://localhost')), 'update');
});

test('desktop overview places the apk card in the main column to avoid empty left-side space', () => {
  assert.deepEqual(getDesktopOverviewSectionColumns(true), {
    main: ['recent-files', 'transfer-workbench', 'apk-download'],
    sidebar: ['quick-actions', 'storage', 'account'],
  });
});

test('desktop overview omits the apk card entirely when the download entry is hidden', () => {
  assert.deepEqual(getDesktopOverviewSectionColumns(false), {
    main: ['recent-files', 'transfer-workbench'],
    sidebar: ['quick-actions', 'storage', 'account'],
  });
});

test('desktop overview stretches the last visible main card to keep column bottoms aligned', () => {
  assert.equal(getDesktopOverviewStretchSection(true), 'apk-download');
  assert.equal(getDesktopOverviewStretchSection(false), 'transfer-workbench');
});

test('overview storage quota label uses the real quota instead of a fixed 50 GB copy', () => {
  assert.equal(getOverviewStorageQuotaLabel(50 * 1024 * 1024 * 1024), '已使用 / 共 50 GB');
  assert.equal(getOverviewStorageQuotaLabel(100 * 1024 * 1024 * 1024), '已使用 / 共 100 GB');
});

test('apk published time is formatted into a readable update label', () => {
  assert.match(formatApkPublishedAtLabel('2026-04-03T08:33:54Z') ?? '', /04[/-]03 16:33/);
  assert.equal(formatApkPublishedAtLabel(null), null);
});

test('android update check compares numeric versionCode first', () => {
  assert.equal(isAndroidReleaseNewer({
    currentVersionCode: '260931807',
    releaseVersionCode: '260931807',
  }), false);
  assert.equal(isAndroidReleaseNewer({
    currentVersionCode: '260931807',
    releaseVersionCode: '260931808',
  }), true);
});

test('android update check falls back to versionName comparison', () => {
  assert.equal(isAndroidReleaseNewer({
    currentVersionName: '2026.04.03.1807',
    releaseVersionName: '2026.04.03.1807',
  }), false);
  assert.equal(isAndroidReleaseNewer({
    currentVersionName: '2026.04.03.1807',
    releaseVersionName: '2026.04.03.1810',
  }), true);
});
