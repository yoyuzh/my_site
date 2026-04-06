#!/usr/bin/env node

import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import {spawnSync} from 'node:child_process';

import {
  buildObjectKey,
  createAwsV4Headers,
  encodeObjectKey,
  getFrontendSpaAliasContentType,
  getFrontendSpaAliasKeys,
  getCacheControl,
  getContentType,
  loadRepoEnv,
  listFiles,
  normalizeEndpoint,
  requestDogeCloudTemporaryS3Session,
} from './oss-deploy-lib.mjs';

const repoRoot = process.cwd();
const frontDir = path.join(repoRoot, 'front');
const distDir = path.join(frontDir, 'dist');

function parseArgs(argv) {
  return {
    dryRun: argv.includes('--dry-run'),
    skipBuild: argv.includes('--skip-build'),
  };
}

function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }

  return value;
}

function runBuild() {
  const result = spawnSync('npm', ['run', 'build'], {
    cwd: frontDir,
    stdio: 'inherit',
    shell: process.platform === 'win32',
  });

  if (result.status !== 0) {
    throw new Error('Frontend build failed');
  }
}

async function uploadFile({
  bucket,
  endpoint,
  region,
  objectKey,
  filePath,
  contentTypeOverride,
  accessKeyId,
  secretAccessKey,
  sessionToken,
}) {
  const body = await fs.readFile(filePath);
  const contentType = contentTypeOverride || getContentType(objectKey);
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

async function uploadSpaAliases({
  bucket,
  endpoint,
  region,
  distIndexPath,
  accessKeyId,
  secretAccessKey,
  sessionToken,
  remotePrefix,
  dryRun,
}) {
  const aliases = getFrontendSpaAliasKeys();
  const contentType = getFrontendSpaAliasContentType();

  for (const alias of aliases) {
    const objectKey = buildObjectKey(remotePrefix, alias);

    if (dryRun) {
      console.log(`[dry-run] upload alias ${alias} -> ${objectKey}`);
      continue;
    }

    await uploadFile({
      bucket,
      endpoint,
      region,
      objectKey,
      filePath: distIndexPath,
      contentTypeOverride: contentType,
      accessKeyId,
      secretAccessKey,
      sessionToken,
    });
    console.log(`uploaded alias ${objectKey}`);
  }
}

async function main() {
  const {dryRun, skipBuild} = parseArgs(process.argv.slice(2));

  await loadRepoEnv({repoRoot});

  const apiAccessKey = requireEnv('YOYUZH_DOGECLOUD_API_ACCESS_KEY');
  const apiSecretKey = requireEnv('YOYUZH_DOGECLOUD_API_SECRET_KEY');
  const scope = requireEnv('YOYUZH_DOGECLOUD_FRONT_SCOPE');
  const apiBaseUrl = process.env.YOYUZH_DOGECLOUD_API_BASE_URL || 'https://api.dogecloud.com';
  const region = process.env.YOYUZH_DOGECLOUD_S3_REGION || 'automatic';
  const remotePrefix = process.env.YOYUZH_DOGECLOUD_FRONT_PREFIX || '';
  const ttlSeconds = Number(process.env.YOYUZH_DOGECLOUD_FRONT_TTL_SECONDS || '3600');
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
    scope,
    ttlSeconds,
  });

  if (!skipBuild) {
    runBuild();
  }

  const files = await listFiles(distDir);
  if (files.length === 0) {
    throw new Error('No files found in front/dist. Run the frontend build first.');
  }

  for (const filePath of files) {
    const relativePath = path.relative(distDir, filePath).split(path.sep).join('/');
    const objectKey = buildObjectKey(remotePrefix, relativePath);

    if (dryRun) {
      console.log(`[dry-run] upload ${relativePath} -> ${objectKey}`);
      continue;
    }

    await uploadFile({
      bucket,
      endpoint,
      region,
      objectKey,
      filePath,
      accessKeyId,
      secretAccessKey,
      sessionToken,
    });
    console.log(`uploaded ${objectKey}`);
  }

  await uploadSpaAliases({
    bucket,
    endpoint,
    region,
    distIndexPath: path.join(distDir, 'index.html'),
    accessKeyId,
    secretAccessKey,
    sessionToken,
    remotePrefix,
    dryRun,
  });
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});
