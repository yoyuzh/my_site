import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildBackgroundTasksPath,
  cancelBackgroundTask,
  createMediaMetadataTask,
  getBackgroundTask,
  listBackgroundTasks,
  parseBackgroundTaskState,
} from './background-tasks';

test('buildBackgroundTasksPath defaults to the first ten tasks', () => {
  assert.equal(buildBackgroundTasksPath(), '/tasks?page=0&size=10');
});

test('listBackgroundTasks requests the v2 task list and unwraps the page payload', async () => {
  const originalFetch = globalThis.fetch;

  try {
    let requestUrl = '';
    let requestMethod = '';
    globalThis.fetch = async (input, init) => {
      requestUrl = String(input);
      requestMethod = init?.method || 'GET';
      return new Response(
        JSON.stringify({
          code: 0,
          msg: 'success',
          data: {
            items: [
              {
                id: 1,
                type: 'MEDIA_META',
                status: 'QUEUED',
                userId: 7,
                publicStateJson: '{"fileId":1}',
                correlationId: 'corr-1',
                errorMessage: null,
                createdAt: '2026-04-09T10:00:00',
                updatedAt: '2026-04-09T10:00:00',
                finishedAt: null,
              },
            ],
            total: 1,
            page: 0,
            size: 10,
          },
        }),
        {
          headers: {
            'Content-Type': 'application/json',
          },
        },
      );
    };

    const payload = await listBackgroundTasks();

    assert.equal(requestUrl, '/api/v2/tasks?page=0&size=10');
    assert.equal(requestMethod, 'GET');
    assert.deepEqual(payload, {
      items: [
        {
          id: 1,
          type: 'MEDIA_META',
          status: 'QUEUED',
          userId: 7,
          publicStateJson: '{"fileId":1}',
          correlationId: 'corr-1',
          errorMessage: null,
          createdAt: '2026-04-09T10:00:00',
          updatedAt: '2026-04-09T10:00:00',
          finishedAt: null,
        },
      ],
      total: 1,
      page: 0,
      size: 10,
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('getBackgroundTask and cancelBackgroundTask hit the task detail endpoints', async () => {
  const originalFetch = globalThis.fetch;

  try {
    const calls: Array<{url: string; method: string}> = [];
    globalThis.fetch = async (input, init) => {
      calls.push({
        url: String(input),
        method: init?.method || 'GET',
      });
      return new Response(
        JSON.stringify({
          code: 0,
          msg: 'success',
          data: {
            id: 123,
            type: 'ARCHIVE',
            status: 'COMPLETED',
            userId: 7,
            publicStateJson: '{"worker":"noop"}',
            correlationId: null,
            errorMessage: null,
            createdAt: '2026-04-09T10:00:00',
            updatedAt: '2026-04-09T10:00:00',
            finishedAt: '2026-04-09T10:01:00',
          },
        }),
        {
          headers: {
            'Content-Type': 'application/json',
          },
        },
      );
    };

    await getBackgroundTask(123);
    await cancelBackgroundTask(123);

    assert.deepEqual(calls, [
      { url: '/api/v2/tasks/123', method: 'GET' },
      { url: '/api/v2/tasks/123', method: 'DELETE' },
    ]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('createMediaMetadataTask sends the queued file task payload', async () => {
  const originalFetch = globalThis.fetch;

  try {
    let requestUrl = '';
    let requestMethod = '';
    let requestBody = '';
    globalThis.fetch = async (input, init) => {
      requestUrl = String(input);
      requestMethod = init?.method || 'GET';
      requestBody = String(init?.body || '');
      return new Response(
        JSON.stringify({
          code: 0,
          msg: 'success',
          data: {
            id: 123,
            type: 'MEDIA_META',
            status: 'QUEUED',
            userId: 7,
            publicStateJson: '{"fileId":9}',
            correlationId: 'media-9',
            errorMessage: null,
            createdAt: '2026-04-09T10:00:00',
            updatedAt: '2026-04-09T10:00:00',
            finishedAt: null,
          },
        }),
        {
          headers: {
            'Content-Type': 'application/json',
          },
        },
      );
    };

    const payload = await createMediaMetadataTask({
      fileId: 9,
      path: '/docs/photo.png',
      correlationId: 'media-9',
    });

    assert.equal(requestUrl, '/api/v2/tasks/media-metadata');
    assert.equal(requestMethod, 'POST');
    assert.deepEqual(JSON.parse(requestBody), {
      fileId: 9,
      path: '/docs/photo.png',
      correlationId: 'media-9',
    });
    assert.equal(payload.status, 'QUEUED');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('parseBackgroundTaskState handles valid and invalid JSON defensively', () => {
  assert.deepEqual(parseBackgroundTaskState(null), {});
  assert.deepEqual(parseBackgroundTaskState(''), {});
  assert.deepEqual(parseBackgroundTaskState('not-json'), {});
  assert.deepEqual(parseBackgroundTaskState('[]'), {});
  assert.deepEqual(parseBackgroundTaskState('{"worker":"media-metadata","fileId":9}'), {
    worker: 'media-metadata',
    fileId: 9,
  });
});
