import assert from 'node:assert/strict';
import test from 'node:test';

import type { AdminFile, PageResponse } from '@/src/lib/types';

import {
  buildAdminListPath,
  buildFilesListPath,
  mapFilesListResponse,
} from './data-provider';

test('buildFilesListPath maps react-admin pagination to the backend files list query', () => {
  assert.equal(
    buildFilesListPath({
      pagination: {
        page: 3,
        perPage: 25,
      },
      filter: {},
    }),
    '/admin/files?page=2&size=25',
  );
});

test('buildFilesListPath includes file and owner search filters when present', () => {
  assert.equal(
    buildFilesListPath({
      pagination: {
        page: 1,
        perPage: 25,
      },
      filter: {
        query: 'report',
        ownerQuery: 'alice',
      },
    }),
    '/admin/files?page=0&size=25&query=report&ownerQuery=alice',
  );
});

test('mapFilesListResponse preserves list items and total count', () => {
  const payload: PageResponse<AdminFile> = {
    items: [
      {
        id: 1,
        filename: 'hello.txt',
        path: '/',
        size: 12,
        contentType: 'text/plain',
        directory: false,
        createdAt: '2026-03-19T15:00:00',
        ownerId: 7,
        ownerUsername: 'alice',
        ownerEmail: 'alice@example.com',
      },
    ],
    total: 1,
    page: 0,
    size: 25,
  };

  assert.deepEqual(mapFilesListResponse(payload), {
    data: payload.items,
    total: 1,
  });
});

test('buildAdminListPath maps generic admin resources to backend paging queries', () => {
  assert.equal(
    buildAdminListPath('users', {
      pagination: {
        page: 2,
        perPage: 20,
      },
      filter: {},
    }),
    '/admin/users?page=1&size=20',
  );
});

test('buildAdminListPath includes the user search query when present', () => {
  assert.equal(
    buildAdminListPath('users', {
      pagination: {
        page: 1,
        perPage: 25,
      },
      filter: {
        query: 'alice',
      },
    }),
    '/admin/users?page=0&size=25&query=alice',
  );
});

test('buildAdminListPath rejects the removed school snapshots resource', () => {
  assert.throws(
    () =>
      buildAdminListPath('schoolSnapshots', {
        pagination: {
          page: 1,
          perPage: 50,
        },
        filter: {},
      }),
    /schoolSnapshots/,
  );
});
