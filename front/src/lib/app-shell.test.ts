import assert from 'node:assert/strict';
import test from 'node:test';

import { MOBILE_APP_MAX_WIDTH, shouldUseMobileApp } from './app-shell';

test('shouldUseMobileApp enables the mobile shell below the width breakpoint', () => {
  assert.equal(shouldUseMobileApp(MOBILE_APP_MAX_WIDTH - 1), true);
  assert.equal(shouldUseMobileApp(MOBILE_APP_MAX_WIDTH), false);
  assert.equal(shouldUseMobileApp(1280), false);
});
