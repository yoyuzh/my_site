import assert from 'node:assert/strict';
import test from 'node:test';

import { buildFileSearchPath, searchFiles } from './file-search';

test('buildFileSearchPath includes search filters and encodes values', () => {
  assert.equal(
    buildFileSearchPath({
      name: '课件 2026',
      type: 'folder',
      sizeGte: 1024,
      sizeLte: 4096,
      createdGte: '2026-04-08T12:00:00',
      updatedLte: '2026-04-08T23:59:59',
      page: 2,
      size: 50,
    }),
    '/files/search?name=%E8%AF%BE%E4%BB%B6+2026&type=folder&sizeGte=1024&sizeLte=4096&createdGte=2026-04-08T12%3A00%3A00&updatedLte=2026-04-08T23%3A59%3A59&page=2&size=50',
  );
});

test('buildFileSearchPath skips empty filters', () => {
  assert.equal(
    buildFileSearchPath({
      name: '   ',
      type: 'all',
      createdGte: '',
    }),
    '/files/search?type=all',
  );
});

test('searchFiles uses the v2 search endpoint and unwraps the page payload', async () => {
  const originalFetch = globalThis.fetch;

  try {
    let requestUrl = '';
    globalThis.fetch = async (input) => {
      requestUrl = String(input);
      return new Response(
        JSON.stringify({
          code: 0,
          msg: 'success',
          data: {
            items: [
              {
                id: 1,
                filename: '说明.txt',
                path: '/',
                size: 12,
                contentType: 'text/plain',
                directory: false,
                createdAt: '2026-04-08T12:00:00',
              },
            ],
            total: 1,
            page: 0,
            size: 20,
          },
        }),
        {
          headers: {
            'Content-Type': 'application/json',
          },
        },
      );
    };

    const payload = await searchFiles({
      name: '说明',
      type: 'file',
      page: 0,
      size: 20,
    });

    assert.equal(requestUrl, '/api/v2/files/search?name=%E8%AF%B4%E6%98%8E&type=file&page=0&size=20');
    assert.deepEqual(payload, {
      items: [
        {
          id: 1,
          filename: '说明.txt',
          path: '/',
          size: 12,
          contentType: 'text/plain',
          directory: false,
          createdAt: '2026-04-08T12:00:00',
        },
      ],
      total: 1,
      page: 0,
      size: 20,
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});
