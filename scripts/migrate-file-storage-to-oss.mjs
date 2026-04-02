import fs from 'node:fs/promises';
import {constants as fsConstants} from 'node:fs';
import {spawn} from 'node:child_process';
import https from 'node:https';
import path from 'node:path';

import {
  createAwsV4Headers,
  normalizeEndpoint,
  parseSimpleEnv,
  encodeObjectKey,
  requestDogeCloudTemporaryS3Session,
} from './oss-deploy-lib.mjs';

const DEFAULTS = {
  appEnvPath: '/opt/yoyuzh/app.env',
  storageRoot: '/opt/yoyuzh/storage',
  database: 'yoyuzh_portal',
  bucket: 'yoyuzh-files',
  endpoint: 'https://cos.ap-chengdu.myqcloud.com',
  region: 'automatic',
};

function parseArgs(argv) {
  const options = {
    dryRun: false,
    cleanupLegacy: false,
    appEnvPath: DEFAULTS.appEnvPath,
    storageRoot: DEFAULTS.storageRoot,
    database: DEFAULTS.database,
    bucket: DEFAULTS.bucket,
  };

  for (const arg of argv) {
    if (arg === '--dry-run') {
      options.dryRun = true;
      continue;
    }

    if (arg === '--cleanup-legacy') {
      options.cleanupLegacy = true;
      continue;
    }

    if (arg.startsWith('--app-env=')) {
      options.appEnvPath = arg.slice('--app-env='.length);
      continue;
    }

    if (arg.startsWith('--storage-root=')) {
      options.storageRoot = arg.slice('--storage-root='.length);
      continue;
    }

    if (arg.startsWith('--database=')) {
      options.database = arg.slice('--database='.length);
      continue;
    }

    if (arg.startsWith('--bucket=')) {
      options.bucket = arg.slice('--bucket='.length);
      continue;
    }

    throw new Error(`Unknown argument: ${arg}`);
  }

  return options;
}

function preferredObjectKey(userId, filePath, storageName) {
  const cleanPath = filePath === '/' ? '' : filePath;
  return `users/${userId}${cleanPath}/${storageName}`;
}

function legacyObjectKey(userId, filePath, storageName) {
  const cleanPath = filePath === '/' ? '' : filePath;
  return `${userId}${cleanPath}/${storageName}`;
}

function localFilePath(storageRoot, userId, filePath, storageName) {
  const cleanPath = filePath === '/' ? '' : filePath.slice(1);
  return path.join(storageRoot, String(userId), cleanPath, storageName);
}

function archivedObjectPrefix(userId) {
  return `files/${userId}/`;
}

function archivedObjectSuffix(filename) {
  return `-${filename}`;
}

function runCommand(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {stdio: ['ignore', 'pipe', 'pipe']});
    let stdout = '';
    let stderr = '';

    child.stdout.on('data', (chunk) => {
      stdout += chunk.toString();
    });

    child.stderr.on('data', (chunk) => {
      stderr += chunk.toString();
    });

    child.on('error', reject);
    child.on('close', (code) => {
      if (code !== 0) {
        reject(new Error(stderr || `${command} exited with code ${code}`));
        return;
      }
      resolve(stdout);
    });
  });
}

async function readAppEnv(appEnvPath) {
  const raw = await fs.readFile(appEnvPath, 'utf8');
  return parseSimpleEnv(raw);
}

async function queryFiles(database) {
  const sql = [
    'SELECT user_id, path, storage_name, filename',
    'FROM portal_file',
    'WHERE is_directory = 0',
    'ORDER BY user_id, id',
  ].join(' ');

  const raw = await runCommand('sudo', [
    'mysql',
    '--batch',
    '--raw',
    '--skip-column-names',
    database,
    '-e',
    sql,
  ]);

  return raw
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [userId, filePath, storageName, filename] = line.split('\t');
      return {userId, filePath, storageName, filename};
    });
}

function s3Request({
  method,
  endpoint,
  region,
  bucket,
  objectKey,
  accessKeyId,
  secretAccessKey,
  sessionToken,
  headers = {},
  query = '',
  body,
}) {
  return new Promise((resolve, reject) => {
    const normalizedEndpoint = normalizeEndpoint(endpoint);
    const amzDate = new Date().toISOString().replace(/[:-]|\.\d{3}/g, '');
    const signatureHeaders = createAwsV4Headers({
      method,
      endpoint,
      region,
      bucket,
      objectKey,
      query,
      headers,
      amzDate,
      accessKeyId,
      secretAccessKey,
      sessionToken,
    });

    const request = https.request({
      hostname: `${bucket}.${normalizedEndpoint}`,
      path: `/${encodeObjectKey(objectKey)}${query ? `?${query}` : ''}`,
      method,
      headers: {
        ...signatureHeaders,
      },
    }, (response) => {
      let data = '';
      response.on('data', (chunk) => {
        data += chunk.toString();
      });
      response.on('end', () => {
        resolve({
          statusCode: response.statusCode ?? 500,
          headers: response.headers,
          body: data,
        });
      });
    });

    request.on('error', reject);

    if (body) {
      body.pipe(request);
      return;
    }

    request.end();
  });
}

async function objectExists(context, objectKey) {
  const response = await s3Request({
    ...context,
    method: 'HEAD',
    objectKey,
  });
  return response.statusCode >= 200 && response.statusCode < 300;
}

async function uploadLocalFile(context, objectKey, absolutePath, contentType = 'application/octet-stream') {
  const fileHandle = await fs.open(absolutePath, 'r');
  const stream = fileHandle.createReadStream();
  const stat = await fileHandle.stat();

  try {
    const response = await s3Request({
      ...context,
      method: 'PUT',
      objectKey,
      headers: {
        'Content-Type': contentType,
        'Content-Length': String(stat.size),
      },
      body: stream,
    });

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw new Error(`Upload failed for ${objectKey}: ${response.statusCode} ${response.body}`);
    }
  } finally {
    await fileHandle.close();
  }
}

async function copyObject(context, sourceKey, targetKey) {
  const response = await s3Request({
    ...context,
    method: 'PUT',
    objectKey: targetKey,
    headers: {
      'x-amz-copy-source': `/${context.bucket}/${encodeObjectKey(sourceKey)}`,
    },
  });

  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw new Error(`Copy failed ${sourceKey} -> ${targetKey}: ${response.statusCode} ${response.body}`);
  }
}

async function deleteObject(context, objectKey) {
  const response = await s3Request({
    ...context,
    method: 'DELETE',
    objectKey,
  });

  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw new Error(`Delete failed for ${objectKey}: ${response.statusCode} ${response.body}`);
  }
}

async function localFileExists(absolutePath) {
  try {
    await fs.access(absolutePath, fsConstants.R_OK);
    return true;
  } catch {
    return false;
  }
}

function extractXmlValues(xml, tagName) {
  const pattern = new RegExp(`<${tagName}>(.*?)</${tagName}>`, 'g');
  return [...xml.matchAll(pattern)].map((match) => match[1]);
}

async function listObjects(context, prefix) {
  const keys = [];
  let continuationToken = '';

  while (true) {
    const query = new URLSearchParams({
      'list-type': '2',
      'max-keys': '1000',
      prefix,
    });

    if (continuationToken) {
      query.set('continuation-token', continuationToken);
    }

    const response = await s3Request({
      ...context,
      method: 'GET',
      objectKey: '',
      query: query.toString(),
    });

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw new Error(`List failed for prefix ${prefix}: ${response.statusCode} ${response.body}`);
    }

    keys.push(...extractXmlValues(response.body, 'Key'));

    const truncated = extractXmlValues(response.body, 'IsTruncated')[0] === 'true';
    continuationToken = extractXmlValues(response.body, 'NextContinuationToken')[0] || '';
    if (!truncated || !continuationToken) {
      return keys;
    }
  }
}

async function buildArchivedObjectMap(context, files) {
  const userIds = [...new Set(files.map((file) => file.userId))];
  const archivedObjectsByKey = new Map();

  for (const userId of userIds) {
    const objects = await listObjects(context, archivedObjectPrefix(userId));
    for (const objectKey of objects) {
      const filename = objectKey.split('/').pop() ?? '';
      const match = filename.match(/^[0-9a-f-]{36}-(.+)$/i);
      if (!match) {
        continue;
      }

      const originalFilename = match[1];
      const recordKey = `${userId}\t${originalFilename}`;
      const matches = archivedObjectsByKey.get(recordKey) ?? [];
      matches.push(objectKey);
      archivedObjectsByKey.set(recordKey, matches);
    }
  }

  for (const matches of archivedObjectsByKey.values()) {
    matches.sort();
  }

  return archivedObjectsByKey;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const appEnv = await readAppEnv(options.appEnvPath);
  const apiAccessKey = appEnv.YOYUZH_DOGECLOUD_API_ACCESS_KEY;
  const apiSecretKey = appEnv.YOYUZH_DOGECLOUD_API_SECRET_KEY;
  const scope = appEnv.YOYUZH_DOGECLOUD_STORAGE_SCOPE || options.bucket;
  const apiBaseUrl = appEnv.YOYUZH_DOGECLOUD_API_BASE_URL || 'https://api.dogecloud.com';
  const region = appEnv.YOYUZH_DOGECLOUD_S3_REGION || DEFAULTS.region;

  if (!apiAccessKey || !apiSecretKey || !scope) {
    throw new Error('Missing DogeCloud storage configuration in app env');
  }

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
    ttlSeconds: Number(appEnv.YOYUZH_DOGECLOUD_STORAGE_TTL_SECONDS || '3600'),
  });

  const files = await queryFiles(options.database);
  const context = {
    endpoint,
    region,
    bucket,
    accessKeyId,
    secretAccessKey,
    sessionToken,
  };
  const archivedObjectsByKey = await buildArchivedObjectMap(context, files);

  const summary = {
    alreadyPreferred: 0,
    migratedFromLocal: 0,
    migratedFromLegacy: 0,
    migratedFromArchivedPrefix: 0,
    deletedLegacy: 0,
    missing: 0,
  };

  for (const file of files) {
    const preferredKey = preferredObjectKey(file.userId, file.filePath, file.storageName);
    const legacyKey = legacyObjectKey(file.userId, file.filePath, file.storageName);
    const absoluteLocalPath = localFilePath(options.storageRoot, file.userId, file.filePath, file.storageName);

    if (await objectExists(context, preferredKey)) {
      summary.alreadyPreferred += 1;
      console.log(`[skip] preferred exists: ${preferredKey}`);
      continue;
    }

    if (await localFileExists(absoluteLocalPath)) {
      summary.migratedFromLocal += 1;
      console.log(`${options.dryRun ? '[dry-run]' : '[upload]'} ${absoluteLocalPath} -> ${preferredKey}`);
      if (!options.dryRun) {
        await uploadLocalFile(context, preferredKey, absoluteLocalPath);
      }
      continue;
    }

    if (await objectExists(context, legacyKey)) {
      summary.migratedFromLegacy += 1;
      console.log(`${options.dryRun ? '[dry-run]' : '[copy]'} ${legacyKey} -> ${preferredKey}`);
      if (!options.dryRun) {
        await copyObject(context, legacyKey, preferredKey);
        if (options.cleanupLegacy) {
          await deleteObject(context, legacyKey);
          summary.deletedLegacy += 1;
        }
      }
      continue;
    }

    const archivedRecordKey = `${file.userId}\t${file.filename}`;
    const archivedMatches = archivedObjectsByKey.get(archivedRecordKey) ?? [];
    const archivedKey = archivedMatches.shift();
    if (archivedKey) {
      summary.migratedFromArchivedPrefix += 1;
      console.log(`${options.dryRun ? '[dry-run]' : '[copy]'} ${archivedKey} -> ${preferredKey}`);
      if (!options.dryRun) {
        await copyObject(context, archivedKey, preferredKey);
      }
      continue;
    }

    summary.missing += 1;
    console.warn(`[missing] user=${file.userId} path=${file.filePath} storage=${file.storageName} filename=${file.filename}`);
  }

  console.log('\nSummary');
  console.log(JSON.stringify(summary, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
