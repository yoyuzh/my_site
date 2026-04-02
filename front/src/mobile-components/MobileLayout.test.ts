import assert from 'node:assert/strict';
import test from 'node:test';

import { getVisibleNavItems } from './MobileLayout';

test('mobile navigation hides the games entry', () => {
  const visiblePaths = getVisibleNavItems(false).map((item) => item.path as string);

  assert.equal(visiblePaths.includes('/games'), false);
  assert.deepEqual(visiblePaths, ['/overview', '/files', '/transfer']);
});
