import assert from 'node:assert/strict';
import test from 'node:test';

import {
  getMobileViewportOffsetClassNames,
  getVisibleNavItems,
  isNativeMobileShellLocation,
} from './MobileLayout';

test('mobile navigation hides the games entry', () => {
  const visiblePaths = getVisibleNavItems(false).map((item) => item.path as string);

  assert.equal(visiblePaths.includes('/games'), false);
  assert.deepEqual(visiblePaths, ['/overview', '/files', '/transfer']);
});

test('mobile layout reserves top safe-area space for the fixed app bar', () => {
  const offsets = getMobileViewportOffsetClassNames();

  assert.match(offsets.header, /\bsafe-area-pt\b/);
  assert.match(offsets.main, /var\(--app-safe-area-top\)/);
});

test('mobile layout adds extra top spacing inside the native shell', () => {
  const offsets = getMobileViewportOffsetClassNames(true);

  assert.match(offsets.header, /\bpt-6\b/);
  assert.match(offsets.main, /1\.5rem/);
});

test('native mobile shell detection matches Capacitor localhost origins', () => {
  assert.equal(isNativeMobileShellLocation(new URL('https://localhost')), true);
  assert.equal(isNativeMobileShellLocation(new URL('http://127.0.0.1')), true);
  assert.equal(isNativeMobileShellLocation(new URL('https://yoyuzh.xyz')), false);
});
