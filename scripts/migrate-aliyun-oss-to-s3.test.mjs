import assert from 'node:assert/strict';
import test from 'node:test';

import {parseArgs, pickTransferredHeaders} from './migrate-aliyun-oss-to-s3.mjs';

test('parseArgs keeps migration flags and bucket options', () => {
  const options = parseArgs([
    '--dry-run',
    '--overwrite',
    '--prefix=games/',
    '--source-bucket=aliyun-front',
    '--target-scope=yoyuzh-front',
    '--target-api-access-key=doge-ak',
  ]);

  assert.equal(options.dryRun, true);
  assert.equal(options.overwrite, true);
  assert.equal(options.prefix, 'games/');
  assert.equal(options.sourceBucket, 'aliyun-front');
  assert.equal(options.targetScope, 'yoyuzh-front');
  assert.equal(options.targetApiAccessKey, 'doge-ak');
});

test('pickTransferredHeaders preserves only safe object metadata headers', () => {
  const headers = pickTransferredHeaders({
    'cache-control': 'public,max-age=31536000,immutable',
    'content-type': 'text/javascript; charset=utf-8',
    'content-disposition': 'attachment; filename=test.js',
    etag: '"demo"',
    server: 'OSS',
  });

  assert.deepEqual(headers, {
    'cache-control': 'public,max-age=31536000,immutable',
    'Content-Type': 'text/javascript; charset=utf-8',
    'content-disposition': 'attachment; filename=test.js',
  });
});
