import assert from 'node:assert/strict';
import test from 'node:test';

import { buildFileEventsPath, createSseEventParser, getFileEventsReconnectDelayMs } from './file-events';

test('buildFileEventsPath encodes the watched path', () => {
  assert.equal(
    buildFileEventsPath('/课程资料/2026 春'),
    '/files/events?path=%2F%E8%AF%BE%E7%A8%8B%E8%B5%84%E6%96%99%2F2026+%E6%98%A5',
  );
});

test('createSseEventParser ignores READY and returns file events', () => {
  const parser = createSseEventParser();

  const events = parser.push([
    'event: READY',
    'data: {"eventType":"READY","path":"/"}',
    '',
    'event: CREATED',
    'data: {"eventType":"CREATED","fileId":42,"fromPath":null,"toPath":"/notes.txt","clientId":"other","createdAt":"2026-04-08T12:00:00","payload":"{}"}',
    '',
    '',
  ].join('\n'));

  assert.deepEqual(events, [
    {
      eventType: 'CREATED',
      fileId: 42,
      fromPath: null,
      toPath: '/notes.txt',
      clientId: 'other',
      createdAt: '2026-04-08T12:00:00',
      payload: '{}',
    },
  ]);
});

test('createSseEventParser keeps partial chunks until the event is complete', () => {
  const parser = createSseEventParser();

  assert.deepEqual(parser.push('event: RENAMED\ndata: {"eventType":"REN'), []);

  assert.deepEqual(parser.push('AMED","fileId":7,"fromPath":"/old.txt","toPath":"/new.txt"}\n\n'), [
    {
      eventType: 'RENAMED',
      fileId: 7,
      fromPath: '/old.txt',
      toPath: '/new.txt',
    },
  ]);
});

test('getFileEventsReconnectDelayMs uses capped backoff for stream reconnects', () => {
  assert.equal(getFileEventsReconnectDelayMs(0), 1000);
  assert.equal(getFileEventsReconnectDelayMs(1), 1500);
  assert.equal(getFileEventsReconnectDelayMs(2), 2250);
  assert.equal(getFileEventsReconnectDelayMs(10), 5000);
});
