import assert from 'node:assert/strict';
import test from 'node:test';

import { getMobileTransferLayoutClassNames } from './MobileTransfer';

test('mobile transfer keeps its header sticky and avoids nested file-list scrolling', () => {
  const classNames = getMobileTransferLayoutClassNames();

  assert.match(classNames.root, /\bmin-h-full\b/);
  assert.match(classNames.root, /\bbg-transparent\b/);
  assert.doesNotMatch(classNames.root, /\boverflow-hidden\b/);
  assert.match(classNames.header, /\bsticky\b/);
  assert.match(classNames.header, /\btop-0\b/);
  assert.match(classNames.header, /\bpy-2\b/);
  assert.match(classNames.headerPanel, /\bglass-panel\b/);
  assert.match(classNames.titlePanel, /\brelative\b/);
  assert.match(classNames.content, /\bpb-6\b/);
  assert.doesNotMatch(classNames.sendFileList, /\boverflow-y-auto\b/);
});
