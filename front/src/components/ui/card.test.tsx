import assert from 'node:assert/strict';
import test from 'node:test';
import { renderToStaticMarkup } from 'react-dom/server';

import { Card } from './card';

test('Card applies the shared elevated shadow styling', () => {
  const html = renderToStaticMarkup(<Card>demo</Card>);

  assert.match(html, /shadow-\[0_12px_32px_rgba\(15,23,42,0\.18\)\]/);
});
