import assert from 'node:assert/strict';
import test from 'node:test';

import { ellipsizeFileName } from './file-name';

test('ellipsizeFileName keeps short names unchanged', () => {
  assert.equal(ellipsizeFileName('report.pdf', 24), 'report.pdf');
});

test('ellipsizeFileName truncates long file names and preserves extension when possible', () => {
  assert.equal(
    ellipsizeFileName('2026-very-long-course-material-final-version.pdf', 24),
    '2026-very-long-co....pdf',
  );
});

test('ellipsizeFileName truncates long names without extension', () => {
  assert.equal(
    ellipsizeFileName('this-is-a-very-long-folder-name-without-extension', 20),
    'this-is-a-very-lo...',
  );
});
