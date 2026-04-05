import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

import { createUploadTask } from '@/src/pages/files-upload';
import {
  clearFilesUploads,
  replaceFilesUploads,
  resetFilesUploadStoreForTests,
  setFilesUploadPanelOpen,
} from '@/src/pages/files-upload-store';

import { UploadProgressPanel } from './UploadProgressPanel';

afterEach(() => {
  resetFilesUploadStoreForTests();
});

test('mobile upload progress panel renders as a top summary card instead of a bottom desktop panel', () => {
  replaceFilesUploads([
    createUploadTask(new File(['demo'], 'demo.txt', { type: 'text/plain' }), []),
  ]);
  setFilesUploadPanelOpen(false);

  const html = renderToStaticMarkup(
    React.createElement(UploadProgressPanel, {
      variant: 'mobile',
      className: 'top-offset-anchor',
    }),
  );

  clearFilesUploads();

  assert.match(html, /top-offset-anchor/);
  assert.match(html, /已在后台上传 1 项/);
  assert.doesNotMatch(html, /bottom-6/);
});
