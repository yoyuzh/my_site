import crypto from 'node:crypto';
import https from 'node:https';
import {pathToFileURL} from 'node:url';

import {
  createAwsV4Headers,
  encodeObjectKey,
  normalizeEndpoint,
  requestDogeCloudTemporaryS3Session,
} from './oss-deploy-lib.mjs';

const DEFAULTS = {
  sourceEndpoint: 'https://oss-ap-northeast-1.aliyuncs.com',
  targetEndpoint: 'https://cos.ap-chengdu.myqcloud.com',
  targetRegion: 'automatic',
  prefix: '',
  dryRun: false,
  overwrite: false,
};

export function parseArgs(argv) {
  const options = {...DEFAULTS};

  for (const arg of argv) {
    if (arg === '--dry-run') {
      options.dryRun = true;
      continue;
    }

    if (arg === '--overwrite') {
      options.overwrite = true;
      continue;
    }

    if (arg.startsWith('--prefix=')) {
      options.prefix = arg.slice('--prefix='.length);
      continue;
    }

    if (arg.startsWith('--source-endpoint=')) {
      options.sourceEndpoint = arg.slice('--source-endpoint='.length);
      continue;
    }

    if (arg.startsWith('--source-bucket=')) {
      options.sourceBucket = arg.slice('--source-bucket='.length);
      continue;
    }

    if (arg.startsWith('--source-access-key-id=')) {
      options.sourceAccessKeyId = arg.slice('--source-access-key-id='.length);
      continue;
    }

    if (arg.startsWith('--source-access-key-secret=')) {
      options.sourceAccessKeySecret = arg.slice('--source-access-key-secret='.length);
      continue;
    }

    if (arg.startsWith('--target-api-base-url=')) {
      options.targetApiBaseUrl = arg.slice('--target-api-base-url='.length);
      continue;
    }

    if (arg.startsWith('--target-region=')) {
      options.targetRegion = arg.slice('--target-region='.length);
      continue;
    }

    if (arg.startsWith('--target-scope=')) {
      options.targetScope = arg.slice('--target-scope='.length);
      continue;
    }

    if (arg.startsWith('--target-api-access-key=')) {
      options.targetApiAccessKey = arg.slice('--target-api-access-key='.length);
      continue;
    }

    if (arg.startsWith('--target-api-secret-key=')) {
      options.targetApiSecretKey = arg.slice('--target-api-secret-key='.length);
      continue;
    }

    if (arg.startsWith('--target-ttl-seconds=')) {
      options.targetTtlSeconds = Number(arg.slice('--target-ttl-seconds='.length));
      continue;
    }

    throw new Error(`Unknown argument: ${arg}`);
  }

  return options;
}

export function pickTransferredHeaders(sourceHeaders) {
  const forwardedHeaders = {};
  const supportedHeaders = [
    'cache-control',
    'content-disposition',
    'content-encoding',
    'content-type',
  ];

  for (const headerName of supportedHeaders) {
    const value = sourceHeaders[headerName];
    if (typeof value === 'string' && value) {
      forwardedHeaders[headerName === 'content-type' ? 'Content-Type' : headerName] = value;
    }
  }

  return forwardedHeaders;
}

function requireOption(options, key) {
  const value = options[key];
  if (!value) {
    throw new Error(`Missing required option: ${key}`);
  }
  return value;
}

function createOssAuthorizationHeader({
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

function sourceRequest({method, endpoint, bucket, objectKey, accessKeyId, accessKeySecret, query = ''}) {
  return new Promise((resolve, reject) => {
    const normalizedEndpoint = normalizeEndpoint(endpoint);
    const date = new Date().toUTCString();
    const authorization = createOssAuthorizationHeader({
      method,
      bucket,
      objectKey,
      contentType: '',
      date,
      accessKeyId,
      accessKeySecret,
    });

    const request = https.request({
      hostname: `${bucket}.${normalizedEndpoint}`,
      path: `/${encodeObjectKey(objectKey)}${query ? `?${query}` : ''}`,
      method,
      headers: {
        Authorization: authorization,
        Date: date,
      },
    }, (response) => {
      const chunks = [];
      response.on('data', (chunk) => {
        chunks.push(chunk);
      });
      response.on('end', () => {
        resolve({
          statusCode: response.statusCode ?? 500,
          headers: response.headers,
          body: Buffer.concat(chunks),
        });
      });
    });

    request.on('error', reject);
    request.end();
  });
}

function targetRequest({
  method,
  endpoint,
  region,
  bucket,
  objectKey,
  accessKeyId,
  secretAccessKey,
  sessionToken,
  query = '',
  headers = {},
  body,
}) {
  return new Promise((resolve, reject) => {
    const normalizedEndpoint = normalizeEndpoint(endpoint);
    const amzDate = new Date().toISOString().replace(/[:-]|\.\d{3}/g, '');
    const signedHeaders = createAwsV4Headers({
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
      headers: signedHeaders,
    }, (response) => {
      const chunks = [];
      response.on('data', (chunk) => {
        chunks.push(chunk);
      });
      response.on('end', () => {
        resolve({
          statusCode: response.statusCode ?? 500,
          headers: response.headers,
          body: Buffer.concat(chunks),
        });
      });
    });

    request.on('error', reject);
    if (body) {
      request.end(body);
      return;
    }
    request.end();
  });
}

function extractXmlValues(xmlBuffer, tagName) {
  const xml = xmlBuffer.toString('utf8');
  const pattern = new RegExp(`<${tagName}>(.*?)</${tagName}>`, 'g');
  return [...xml.matchAll(pattern)].map((match) => match[1]);
}

async function listSourceObjects(context, prefix) {
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

    const response = await sourceRequest({
      ...context,
      method: 'GET',
      objectKey: '',
      query: query.toString(),
    });
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw new Error(`List failed for prefix "${prefix}": ${response.statusCode} ${response.body.toString('utf8')}`);
    }

    keys.push(...extractXmlValues(response.body, 'Key'));
    const truncated = extractXmlValues(response.body, 'IsTruncated')[0] === 'true';
    continuationToken = extractXmlValues(response.body, 'NextContinuationToken')[0] || '';
    if (!truncated || !continuationToken) {
      return keys;
    }
  }
}

async function targetObjectExists(context, objectKey) {
  const response = await targetRequest({
    ...context,
    method: 'HEAD',
    objectKey,
  });
  return response.statusCode >= 200 && response.statusCode < 300;
}

async function copyObject(context, objectKey) {
  const sourceResponse = await sourceRequest({
    endpoint: context.sourceEndpoint,
    bucket: context.sourceBucket,
    accessKeyId: context.sourceAccessKeyId,
    accessKeySecret: context.sourceAccessKeySecret,
    method: 'GET',
    objectKey,
  });
  if (sourceResponse.statusCode < 200 || sourceResponse.statusCode >= 300) {
    throw new Error(`Download failed for ${objectKey}: ${sourceResponse.statusCode} ${sourceResponse.body.toString('utf8')}`);
  }

  const forwardedHeaders = pickTransferredHeaders(sourceResponse.headers);
  const response = await targetRequest({
    endpoint: context.targetEndpoint,
    region: context.targetRegion,
    bucket: context.targetBucket,
    accessKeyId: context.targetAccessKeyId,
    secretAccessKey: context.targetSecretAccessKey,
    sessionToken: context.targetSessionToken,
    method: 'PUT',
    objectKey,
    headers: {
      ...forwardedHeaders,
      'Content-Length': String(sourceResponse.body.byteLength),
    },
    body: sourceResponse.body,
  });
  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw new Error(`Upload failed for ${objectKey}: ${response.statusCode} ${response.body.toString('utf8')}`);
  }
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const targetSession = await requestDogeCloudTemporaryS3Session({
    apiBaseUrl: options.targetApiBaseUrl || 'https://api.dogecloud.com',
    accessKey: requireOption(options, 'targetApiAccessKey'),
    secretKey: requireOption(options, 'targetApiSecretKey'),
    scope: requireOption(options, 'targetScope'),
    ttlSeconds: options.targetTtlSeconds || 3600,
  });
  const context = {
    sourceEndpoint: options.sourceEndpoint,
    sourceBucket: requireOption(options, 'sourceBucket'),
    sourceAccessKeyId: requireOption(options, 'sourceAccessKeyId'),
    sourceAccessKeySecret: requireOption(options, 'sourceAccessKeySecret'),
    targetEndpoint: targetSession.endpoint,
    targetRegion: options.targetRegion,
    targetBucket: targetSession.bucket,
    targetAccessKeyId: targetSession.accessKeyId,
    targetSecretAccessKey: targetSession.secretAccessKey,
    targetSessionToken: targetSession.sessionToken,
  };

  const keys = await listSourceObjects({
    endpoint: context.sourceEndpoint,
    bucket: context.sourceBucket,
    accessKeyId: context.sourceAccessKeyId,
    accessKeySecret: context.sourceAccessKeySecret,
  }, options.prefix);

  const summary = {
    listed: keys.length,
    copied: 0,
    skippedExisting: 0,
    failed: 0,
  };

  for (const objectKey of keys) {
    if (!options.overwrite && await targetObjectExists({
      endpoint: context.targetEndpoint,
      region: context.targetRegion,
      bucket: context.targetBucket,
      accessKeyId: context.targetAccessKeyId,
      secretAccessKey: context.targetSecretAccessKey,
      sessionToken: context.targetSessionToken,
    }, objectKey)) {
      summary.skippedExisting += 1;
      console.log(`[skip] ${objectKey}`);
      continue;
    }

    console.log(`${options.dryRun ? '[dry-run]' : '[copy]'} ${objectKey}`);
    if (options.dryRun) {
      summary.copied += 1;
      continue;
    }

    try {
      await copyObject(context, objectKey);
      summary.copied += 1;
    } catch (error) {
      summary.failed += 1;
      console.error(`[failed] ${objectKey}: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  console.log('\nSummary');
  console.log(JSON.stringify(summary, null, 2));
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
