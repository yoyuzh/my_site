#!/usr/bin/env node

import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

import {
  buildObjectKey,
  createAwsV4Headers,
  encodeObjectKey,
  getCacheControl,
  loadRepoEnv,
  normalizeEndpoint,
  requestDogeCloudTemporaryS3Session,
} from './oss-deploy-lib.mjs';

const repoRoot = process.cwd();
const apkSourcePath = path.join(repoRoot, 'front', 'android', 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');

function parseArgs(argv) {
  return {
    dryRun: argv.includes('--dry-run'),
  };
}

function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }

  return value;
}

function getAndroidReleaseVersion() {
  const versionCode = requireEnv('YOYUZH_ANDROID_VERSION_CODE').trim();
  const versionName = requireEnv('YOYUZH_ANDROID_VERSION_NAME').trim();
  return {versionCode, versionName};
}

function getAndroidReleasePrefix() {
  return (process.env.YOYUZH_ANDROID_RELEASE_PREFIX || 'android/releases').trim().replace(/^\/+|\/+$/g, '');
}

function getAndroidReleaseScope() {
  return (process.env.YOYUZH_DOGECLOUD_ANDROID_SCOPE || process.env.YOYUZH_DOGECLOUD_STORAGE_SCOPE || '').trim();
}

function getAndroidReleaseApkObjectKey(versionName) {
  const safeVersionName = versionName.replace(/[^0-9A-Za-z._-]/g, '-');
  return buildObjectKey(getAndroidReleasePrefix(), `yoyuzh-portal-${safeVersionName}.apk`);
}

function getAndroidReleaseMetadataObjectKey() {
  return buildObjectKey(getAndroidReleasePrefix(), 'latest.json');
}

function buildAndroidReleaseMetadata() {
  const {versionCode, versionName} = getAndroidReleaseVersion();
  const fileName = path.posix.basename(getAndroidReleaseApkObjectKey(versionName));
  return {
    versionCode,
    versionName,
    fileName,
    objectKey: getAndroidReleaseApkObjectKey(versionName),
    publishedAt: new Date().toISOString(),
  };
}

async function uploadFile({
  bucket,
  endpoint,
  region,
  objectKey,
  filePath,
  contentType,
  accessKeyId,
  secretAccessKey,
  sessionToken,
}) {
  const body = await fs.readFile(filePath);
  const amzDate = new Date().toISOString().replace(/[:-]|\.\d{3}/g, '');
  const url = `https://${bucket}.${normalizeEndpoint(endpoint)}/${encodeObjectKey(objectKey)}`;
  const signatureHeaders = createAwsV4Headers({
    method: 'PUT',
    endpoint,
    region,
    bucket,
    objectKey,
    headers: {
      'Content-Type': contentType,
    },
    amzDate,
    accessKeyId,
    secretAccessKey,
    sessionToken,
  });

  const response = await fetch(url, {
    method: 'PUT',
    headers: {
      ...signatureHeaders,
      'Cache-Control': getCacheControl(objectKey),
      'Content-Length': String(body.byteLength),
    },
    body,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Upload failed for ${objectKey}: ${response.status} ${response.statusText}\n${text}`);
  }
}

async function main() {
  const {dryRun} = parseArgs(process.argv.slice(2));
  await loadRepoEnv({repoRoot});

  const androidScope = getAndroidReleaseScope();
  if (!androidScope) {
    throw new Error('Missing required environment variable: YOYUZH_DOGECLOUD_ANDROID_SCOPE or YOYUZH_DOGECLOUD_STORAGE_SCOPE');
  }

  await fs.access(apkSourcePath);

  const apiAccessKey = requireEnv('YOYUZH_DOGECLOUD_API_ACCESS_KEY');
  const apiSecretKey = requireEnv('YOYUZH_DOGECLOUD_API_SECRET_KEY');
  const apiBaseUrl = process.env.YOYUZH_DOGECLOUD_API_BASE_URL || 'https://api.dogecloud.com';
  const region = process.env.YOYUZH_DOGECLOUD_S3_REGION || 'automatic';
  const ttlSeconds = Number(process.env.YOYUZH_DOGECLOUD_ANDROID_TTL_SECONDS || process.env.YOYUZH_DOGECLOUD_STORAGE_TTL_SECONDS || '3600');

  const {
    accessKeyId,
    secretAccessKey,
    sessionToken,
    endpoint,
    bucket,
  } = await requestDogeCloudTemporaryS3Session({
    apiBaseUrl,
    accessKey: apiAccessKey,
    secretKey: apiSecretKey,
    scope: androidScope,
    ttlSeconds,
  });

  const metadata = buildAndroidReleaseMetadata();
  const tempMetadataPath = path.join(repoRoot, '.tmp-android-release.json');
  await fs.writeFile(tempMetadataPath, JSON.stringify(metadata, null, 2) + '\n', 'utf-8');

  try {
    const uploads = [
      {
        objectKey: metadata.objectKey,
        filePath: apkSourcePath,
        contentType: 'application/vnd.android.package-archive',
      },
      {
        objectKey: getAndroidReleaseMetadataObjectKey(),
        filePath: tempMetadataPath,
        contentType: 'application/json; charset=utf-8',
      },
    ];

    for (const upload of uploads) {
      if (dryRun) {
        console.log(`[dry-run] upload ${upload.filePath} -> ${upload.objectKey}`);
        continue;
      }

      await uploadFile({
        bucket,
        endpoint,
        region,
        objectKey: upload.objectKey,
        filePath: upload.filePath,
        contentType: upload.contentType,
        accessKeyId,
        secretAccessKey,
        sessionToken,
      });
      console.log(`uploaded ${upload.objectKey}`);
    }
  } finally {
    await fs.rm(tempMetadataPath, {force: true});
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});
