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
  't',
  'overview',
  'files',
  'recycle-bin',
  'transfer',
  'games',
  'login',
  'admin',
  'admin/users',
  'admin/files',
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

function toAmzDateParts(amzDate) {
  return {
    amzDate,
    dateStamp: amzDate.slice(0, 8),
  };
}

function sha256Hex(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}

function hmac(key, value, encoding) {
  const digest = crypto.createHmac('sha256', key).update(value).digest();
  return encoding ? digest.toString(encoding) : digest;
}

function hmacSha1Hex(key, value) {
  return crypto.createHmac('sha1', key).update(value).digest('hex');
}

function buildSigningKey(secretAccessKey, dateStamp, region, service) {
  const kDate = hmac(`AWS4${secretAccessKey}`, dateStamp);
  const kRegion = hmac(kDate, region);
  const kService = hmac(kRegion, service);
  return hmac(kService, 'aws4_request');
}

function encodeQueryComponent(value) {
  return encodeURIComponent(value).replace(/[!'()*]/g, (character) =>
    `%${character.charCodeAt(0).toString(16).toUpperCase()}`
  );
}

function toCanonicalQueryString(query) {
  const params = new URLSearchParams(query);
  return [...params.entries()]
    .sort(([leftKey, leftValue], [rightKey, rightValue]) =>
      leftKey === rightKey ? leftValue.localeCompare(rightValue) : leftKey.localeCompare(rightKey)
    )
    .map(([key, value]) => `${encodeQueryComponent(key)}=${encodeQueryComponent(value)}`)
    .join('&');
}

export function extractDogeCloudScopeBucketName(scope) {
  const separatorIndex = scope.indexOf(':');
  return separatorIndex >= 0 ? scope.slice(0, separatorIndex) : scope;
}

export function createDogeCloudApiAuthorization({apiPath, body, accessKey, secretKey}) {
  return `TOKEN ${accessKey}:${hmacSha1Hex(secretKey, `${apiPath}\n${body}`)}`;
}

export async function requestDogeCloudTemporaryS3Session({
  apiBaseUrl = 'https://api.dogecloud.com',
  accessKey,
  secretKey,
  scope,
  ttlSeconds = 3600,
  fetchImpl = fetch,
}) {
  const apiPath = '/auth/tmp_token.json';
  const body = JSON.stringify({
    channel: 'OSS_FULL',
    ttl: ttlSeconds,
    scopes: [scope],
  });
  const response = await fetchImpl(`${apiBaseUrl.replace(/\/+$/, '')}${apiPath}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: createDogeCloudApiAuthorization({
        apiPath,
        body,
        accessKey,
        secretKey,
      }),
    },
    body,
  });

  if (!response.ok) {
    throw new Error(`DogeCloud tmp_token request failed: HTTP ${response.status}`);
  }

  const payload = await response.json();
  if (payload.code !== 200) {
    throw new Error(`DogeCloud tmp_token request failed: ${payload.msg || 'unknown error'}`);
  }

  const bucketName = extractDogeCloudScopeBucketName(scope);
  const buckets = Array.isArray(payload.data?.Buckets) ? payload.data.Buckets : [];
  const bucket = buckets.find((entry) => entry.name === bucketName) ?? buckets[0];
  if (!bucket) {
    throw new Error(`DogeCloud tmp_token response did not include a bucket for scope: ${bucketName}`);
  }

  return {
    accessKeyId: payload.data.Credentials?.accessKeyId || '',
    secretAccessKey: payload.data.Credentials?.secretAccessKey || '',
    sessionToken: payload.data.Credentials?.sessionToken || '',
    bucket: bucket.s3Bucket,
    endpoint: bucket.s3Endpoint,
    bucketName: bucket.name,
    expiresAt: payload.data.ExpiredAt,
  };
}

export function createAwsV4Headers({
  method,
  endpoint,
  bucket,
  objectKey,
  query = '',
  headers: extraHeaders = {},
  amzDate = new Date().toISOString().replace(/[:-]|\.\d{3}/g, ''),
  accessKeyId,
  secretAccessKey,
  sessionToken,
  region = 'automatic',
}) {
  const {dateStamp} = toAmzDateParts(amzDate);
  const normalizedEndpoint = normalizeEndpoint(endpoint);
  const host = `${bucket}.${normalizedEndpoint}`;
  const canonicalUri = `/${encodeObjectKey(objectKey)}`;
  const canonicalQueryString = toCanonicalQueryString(query);
  const payloadHash = 'UNSIGNED-PAYLOAD';
  const service = 's3';
  const credentialScope = `${dateStamp}/${region}/${service}/aws4_request`;
  const signedHeaderEntries = [
    ['host', host],
    ['x-amz-content-sha256', payloadHash],
    ['x-amz-date', amzDate],
  ];

  for (const [key, value] of Object.entries(extraHeaders)) {
    signedHeaderEntries.push([key.toLowerCase(), String(value).trim()]);
  }

  if (sessionToken) {
    signedHeaderEntries.push(['x-amz-security-token', sessionToken]);
  }

  signedHeaderEntries.sort(([left], [right]) => left.localeCompare(right));
  const signedHeaders = signedHeaderEntries.map(([key]) => key).join(';');
  const canonicalHeadersText = signedHeaderEntries.map(([key, value]) => `${key}:${value}\n`).join('');
  const canonicalRequest = [
    method.toUpperCase(),
    canonicalUri,
    canonicalQueryString,
    canonicalHeadersText,
    signedHeaders,
    payloadHash,
  ].join('\n');
  const stringToSign = [
    'AWS4-HMAC-SHA256',
    amzDate,
    credentialScope,
    sha256Hex(canonicalRequest),
  ].join('\n');
  const signature = hmac(buildSigningKey(secretAccessKey, dateStamp, region, service), stringToSign, 'hex');

  const resultHeaders = {
    Authorization: `AWS4-HMAC-SHA256 Credential=${accessKeyId}/${credentialScope}, SignedHeaders=${signedHeaders}, Signature=${signature}`,
    'x-amz-content-sha256': payloadHash,
    'x-amz-date': amzDate,
    ...extraHeaders,
  };

  if (sessionToken) {
    resultHeaders['x-amz-security-token'] = sessionToken;
  }

  return resultHeaders;
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

export async function loadRepoEnv({
  repoRoot,
  candidateFileNames = ['.env.local', '.env', '.env.oss.local'],
}) {
  for (const fileName of candidateFileNames) {
    const filePath = path.join(repoRoot, fileName);

    try {
      const raw = await fs.readFile(filePath, 'utf-8');
      const values = parseSimpleEnv(raw);
      for (const [key, value] of Object.entries(values)) {
        if (!process.env[key]) {
          process.env[key] = value;
        }
      }
    } catch (error) {
      if (error && typeof error === 'object' && 'code' in error && error.code === 'ENOENT') {
        continue;
      }

      throw error;
    }
  }
}
