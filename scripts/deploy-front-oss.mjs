#!/usr/bin/env node

import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import {spawnSync} from 'node:child_process';

import {
  buildObjectKey,
  createAuthorizationHeader,
  encodeObjectKey,
  getCacheControl,
  getContentType,
  listFiles,
  normalizeEndpoint,
  parseSimpleEnv,
} from './oss-deploy-lib.mjs';

const repoRoot = process.cwd();
const frontDir = path.join(repoRoot, 'front');
const distDir = path.join(frontDir, 'dist');
const envFilePath = path.join(repoRoot, '.env.oss.local');

function parseArgs(argv) {
  return {
    dryRun: argv.includes('--dry-run'),
    skipBuild: argv.includes('--skip-build'),
  };
}

async function loadEnvFileIfPresent() {
  try {
    const raw = await fs.readFile(envFilePath, 'utf-8');
    const values = parseSimpleEnv(raw);
    for (const [key, value] of Object.entries(values)) {
      if (!process.env[key]) {
        process.env[key] = value;
      }
    }
  } catch (error) {
    if (error && typeof error === 'object' && 'code' in error && error.code === 'ENOENT') {
      return;
    }

    throw error;
  }
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
  objectKey,
  filePath,
  accessKeyId,
  accessKeySecret,
}) {
  const body = await fs.readFile(filePath);
  const contentType = getContentType(objectKey);
  const date = new Date().toUTCString();
  const url = `https://${bucket}.${normalizeEndpoint(endpoint)}/${encodeObjectKey(objectKey)}`;
  const authorization = createAuthorizationHeader({
    method: 'PUT',
    bucket,
    objectKey,
    contentType,
    date,
    accessKeyId,
    accessKeySecret,
  });

  const response = await fetch(url, {
    method: 'PUT',
    headers: {
      Authorization: authorization,
      'Cache-Control': getCacheControl(objectKey),
      'Content-Length': String(body.byteLength),
      'Content-Type': contentType,
      Date: date,
    },
    body,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Upload failed for ${objectKey}: ${response.status} ${response.statusText}\n${text}`);
  }
}

async function main() {
  const {dryRun, skipBuild} = parseArgs(process.argv.slice(2));

  await loadEnvFileIfPresent();

  const accessKeyId = requireEnv('YOYUZH_OSS_ACCESS_KEY_ID');
  const accessKeySecret = requireEnv('YOYUZH_OSS_ACCESS_KEY_SECRET');
  const endpoint = requireEnv('YOYUZH_OSS_ENDPOINT');
  const bucket = requireEnv('YOYUZH_OSS_BUCKET');
  const remotePrefix = process.env.YOYUZH_OSS_PREFIX || '';

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
      objectKey,
      filePath,
      accessKeyId,
      accessKeySecret,
    });
    console.log(`uploaded ${objectKey}`);
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});
