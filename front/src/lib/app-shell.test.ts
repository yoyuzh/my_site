import assert from 'node:assert/strict';
import test from 'node:test';

import {
  MOBILE_APP_MAX_WIDTH,
  isNativeAppShellLocation,
  resolvePortalClientType,
  shouldUseMobileApp,
} from './app-shell';

test('shouldUseMobileApp enables the mobile shell below the width breakpoint', () => {
  assert.equal(shouldUseMobileApp(MOBILE_APP_MAX_WIDTH - 1), true);
  assert.equal(shouldUseMobileApp(MOBILE_APP_MAX_WIDTH), false);
  assert.equal(shouldUseMobileApp(1280), false);
});

test('isNativeAppShellLocation matches Capacitor localhost origins', () => {
  assert.equal(isNativeAppShellLocation(new URL('https://localhost')), true);
  assert.equal(isNativeAppShellLocation(new URL('http://127.0.0.1')), true);
  assert.equal(isNativeAppShellLocation(new URL('capacitor://localhost')), true);
  assert.equal(isNativeAppShellLocation(new URL('http://localhost:3000')), false);
  assert.equal(isNativeAppShellLocation(new URL('https://yoyuzh.xyz')), false);
});

test('resolvePortalClientType distinguishes desktop web from mobile shell or narrow screens', () => {
  assert.equal(resolvePortalClientType({ location: new URL('https://yoyuzh.xyz'), viewportWidth: 1280 }), 'desktop');
  assert.equal(resolvePortalClientType({ location: new URL('https://yoyuzh.xyz'), viewportWidth: 390 }), 'mobile');
  assert.equal(resolvePortalClientType({ location: new URL('https://localhost') }), 'mobile');
});
