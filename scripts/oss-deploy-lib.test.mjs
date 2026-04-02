import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildObjectKey,
  createDogeCloudApiAuthorization,
  createAwsV4Headers,
  extractDogeCloudScopeBucketName,
  getFrontendSpaAliasContentType,
  getFrontendSpaAliasKeys,
  getCacheControl,
  getContentType,
  normalizeEndpoint,
  requestDogeCloudTemporaryS3Session,
} from './oss-deploy-lib.mjs';

test('normalizeEndpoint strips scheme and trailing slashes', () => {
  assert.equal(normalizeEndpoint('https://oss-ap-northeast-1.aliyuncs.com/'), 'oss-ap-northeast-1.aliyuncs.com');
});

test('buildObjectKey joins optional prefix with relative path', () => {
  assert.equal(buildObjectKey('', 'assets/index.js'), 'assets/index.js');
  assert.equal(buildObjectKey('portal', 'assets/index.js'), 'portal/assets/index.js');
});

test('getCacheControl keeps index uncached and assets immutable', () => {
  assert.equal(getCacheControl('index.html'), 'no-cache');
  assert.equal(getCacheControl('assets/index.js'), 'public,max-age=31536000,immutable');
  assert.equal(getCacheControl('race/index.html'), 'public,max-age=300');
});

test('getContentType resolves common frontend asset types', () => {
  assert.equal(getContentType('index.html'), 'text/html; charset=utf-8');
  assert.equal(getContentType('assets/app.css'), 'text/css; charset=utf-8');
  assert.equal(getContentType('assets/app.js'), 'text/javascript; charset=utf-8');
  assert.equal(getContentType('favicon.png'), 'image/png');
});

test('frontend spa aliases are uploaded as html entry points', () => {
  const aliases = getFrontendSpaAliasKeys();

  assert.ok(aliases.includes('t/index.html'));
  assert.ok(aliases.includes('overview'));
  assert.ok(aliases.includes('transfer/index.html'));
  assert.ok(aliases.includes('admin/users'));
  assert.equal(getFrontendSpaAliasContentType(), 'text/html; charset=utf-8');
});

test('createAwsV4Headers signs uploads with S3-compatible SigV4 headers', () => {
  const headers = createAwsV4Headers({
    method: 'PUT',
    endpoint: 'https://cos.ap-chengdu.myqcloud.com',
    bucket: 'demo-bucket',
    objectKey: 'assets/index.js',
    contentType: 'text/javascript; charset=utf-8',
    amzDate: '20260317T120000Z',
    accessKeyId: 'test-id',
    secretAccessKey: 'test-secret',
    region: 'automatic',
  });

  assert.equal(headers['x-amz-content-sha256'], 'UNSIGNED-PAYLOAD');
  assert.equal(headers['x-amz-date'], '20260317T120000Z');
  assert.ok(headers.Authorization.startsWith('AWS4-HMAC-SHA256 Credential=test-id/20260317/automatic/s3/aws4_request'));
});

test('extractDogeCloudScopeBucketName keeps only the logical bucket name', () => {
  assert.equal(extractDogeCloudScopeBucketName('yoyuzh-files:users/*'), 'yoyuzh-files');
  assert.equal(extractDogeCloudScopeBucketName('yoyuzh-front'), 'yoyuzh-front');
});

test('createDogeCloudApiAuthorization signs body with HMAC-SHA1 hex', () => {
  const authorization = createDogeCloudApiAuthorization({
    apiPath: '/auth/tmp_token.json',
    body: '{"channel":"OSS_FULL","ttl":1800,"scopes":["yoyuzh-files"]}',
    accessKey: 'doge-ak',
    secretKey: 'doge-sk',
  });

  assert.equal(
    authorization,
    'TOKEN doge-ak:2cf0cf7cf6ddaf673cfe47e55646779d44470929'
  );
});

test('requestDogeCloudTemporaryS3Session requests temp credentials and returns matching bucket', async () => {
  const requests = [];
  const session = await requestDogeCloudTemporaryS3Session({
    apiBaseUrl: 'https://api.dogecloud.com',
    accessKey: 'doge-ak',
    secretKey: 'doge-sk',
    scope: 'yoyuzh-front:assets/*',
    ttlSeconds: 1200,
    fetchImpl: async (url, options) => {
      requests.push({url, options});
      return {
        ok: true,
        status: 200,
        async json() {
          return {
            code: 200,
            msg: 'OK',
            data: {
              Credentials: {
                accessKeyId: 'tmp-ak',
                secretAccessKey: 'tmp-sk',
                sessionToken: 'tmp-token',
              },
              ExpiredAt: 1777777777,
              Buckets: [
                {
                  name: 'yoyuzh-files',
                  s3Bucket: 's-cd-14873-yoyuzh-files-1258813047',
                  s3Endpoint: 'https://cos.ap-chengdu.myqcloud.com',
                },
                {
                  name: 'yoyuzh-front',
                  s3Bucket: 's-cd-14873-yoyuzh-front-1258813047',
                  s3Endpoint: 'https://cos.ap-chengdu.myqcloud.com',
                },
              ],
            },
          };
        },
      };
    },
  });

  assert.equal(requests[0].url, 'https://api.dogecloud.com/auth/tmp_token.json');
  assert.equal(requests[0].options.method, 'POST');
  assert.equal(requests[0].options.headers['Content-Type'], 'application/json');
  assert.ok(requests[0].options.headers.Authorization.startsWith('TOKEN doge-ak:'));
  assert.equal(requests[0].options.body, '{"channel":"OSS_FULL","ttl":1200,"scopes":["yoyuzh-front:assets/*"]}');
  assert.deepEqual(session, {
    accessKeyId: 'tmp-ak',
    secretAccessKey: 'tmp-sk',
    sessionToken: 'tmp-token',
    bucket: 's-cd-14873-yoyuzh-front-1258813047',
    endpoint: 'https://cos.ap-chengdu.myqcloud.com',
    bucketName: 'yoyuzh-front',
    expiresAt: 1777777777,
  });
});
