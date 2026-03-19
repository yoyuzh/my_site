import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';

const CONTENT_TYPES = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.html', 'text/html; charset=utf-8'],
  ['.ico', 'image/x-icon'],
  ['.jpeg', 'image/jpeg'],
  ['.jpg', 'image/jpeg'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.map', 'application/json; charset=utf-8'],
  ['.png', 'image/png'],
  ['.svg', 'image/svg+xml'],
  ['.txt', 'text/plain; charset=utf-8'],
  ['.webmanifest', 'application/manifest+json; charset=utf-8'],
]);

const FRONTEND_SPA_ALIASES = [
  'overview',
  'files',
  'school',
  'games',
  'login',
  'admin',
  'admin/users',
  'admin/files',
  'admin/schoolSnapshots',
];

export function normalizeEndpoint(endpoint) {
  return endpoint.replace(/^https?:\/\//, '').replace(/\/+$/, '');
}

export function buildObjectKey(prefix, relativePath) {
  const cleanPrefix = prefix.replace(/^\/+|\/+$/g, '');
  const cleanRelativePath = relativePath.replace(/^\/+/, '');
  return cleanPrefix ? `${cleanPrefix}/${cleanRelativePath}` : cleanRelativePath;
}

export function getCacheControl(relativePath) {
  if (relativePath === 'index.html') {
    return 'no-cache';
  }

  if (relativePath.startsWith('assets/')) {
    return 'public,max-age=31536000,immutable';
  }

  return 'public,max-age=300';
}

export function getContentType(relativePath) {
  const ext = path.extname(relativePath).toLowerCase();
  return CONTENT_TYPES.get(ext) || 'application/octet-stream';
}

export function getFrontendSpaAliasKeys() {
  return FRONTEND_SPA_ALIASES.flatMap((alias) => [
    alias,
    `${alias}/`,
    `${alias}/index.html`,
  ]);
}

export function getFrontendSpaAliasContentType() {
  return 'text/html; charset=utf-8';
}

export function createAuthorizationHeader({
  method,
  bucket,
  objectKey,
  contentType,
  date,
  accessKeyId,
  accessKeySecret,
}) {
  const stringToSign = [
    method.toUpperCase(),
    '',
    contentType,
    date,
    `/${bucket}/${objectKey}`,
  ].join('\n');

  const signature = crypto
    .createHmac('sha1', accessKeySecret)
    .update(stringToSign)
    .digest('base64');

  return `OSS ${accessKeyId}:${signature}`;
}

export function encodeObjectKey(objectKey) {
  return objectKey
    .split('/')
    .map((part) => encodeURIComponent(part))
    .join('/');
}

export async function listFiles(rootDir) {
  const entries = await fs.readdir(rootDir, {withFileTypes: true});
  const files = [];

  for (const entry of entries) {
    const absolutePath = path.join(rootDir, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await listFiles(absolutePath)));
      continue;
    }

    if (entry.isFile()) {
      files.push(absolutePath);
    }
  }

  return files.sort();
}

export function parseSimpleEnv(rawText) {
  const parsed = {};

  for (const line of rawText.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      continue;
    }

    const separatorIndex = trimmed.indexOf('=');
    if (separatorIndex === -1) {
      continue;
    }

    const key = trimmed.slice(0, separatorIndex).trim();
    let value = trimmed.slice(separatorIndex + 1).trim();

    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }

    parsed[key] = value;
  }

  return parsed;
}
