import assert from 'node:assert/strict';
import test from 'node:test';

import { getMobileFilesLayoutClassNames } from './MobileFiles';

test('mobile files uses a single page scroller and keeps the toolbar sticky', () => {
  const classNames = getMobileFilesLayoutClassNames();

  assert.match(classNames.root, /\bmin-h-full\b/);
  assert.match(classNames.root, /\bbg-transparent\b/);
  assert.doesNotMatch(classNames.root, /\boverflow-hidden\b/);
  assert.match(classNames.toolbar, /\bsticky\b/);
  assert.match(classNames.toolbar, /\btop-0\b/);
  assert.match(classNames.toolbar, /\bpy-2\b/);
  assert.match(classNames.toolbarInner, /\bglass-panel\b/);
  assert.match(classNames.list, /\bpt-2\b/);
  assert.match(classNames.list, /\bpb-4\b/);
  assert.doesNotMatch(classNames.list, /\boverflow-y-auto\b/);
});
