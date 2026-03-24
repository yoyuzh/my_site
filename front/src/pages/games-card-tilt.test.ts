import assert from 'node:assert/strict';
import test from 'node:test';

import { calculateCardTilt } from './games-card-tilt';

const rect = {
  left: 100,
  top: 200,
  width: 320,
  height: 240,
};

test('calculateCardTilt keeps the card flat at the center', () => {
  assert.deepEqual(
    calculateCardTilt(
      {
        clientX: 260,
        clientY: 320,
      },
      rect,
    ),
    {
      rotateX: 0,
      rotateY: 0,
      glareX: 50,
      glareY: 50,
      scale: 1.02,
    },
  );
});

test('calculateCardTilt changes direction based on mouse position', () => {
  const topLeft = calculateCardTilt(
    {
      clientX: 100,
      clientY: 200,
    },
    rect,
  );
  const bottomRight = calculateCardTilt(
    {
      clientX: 420,
      clientY: 440,
    },
    rect,
  );

  assert.equal(topLeft.rotateX, 12);
  assert.equal(topLeft.rotateY, -12);
  assert.equal(topLeft.glareX, 0);
  assert.equal(topLeft.glareY, 0);

  assert.equal(bottomRight.rotateX, -12);
  assert.equal(bottomRight.rotateY, 12);
  assert.equal(bottomRight.glareX, 100);
  assert.equal(bottomRight.glareY, 100);
});

test('calculateCardTilt clamps mouse coordinates outside the card bounds', () => {
  assert.deepEqual(
    calculateCardTilt(
      {
        clientX: 600,
        clientY: 100,
      },
      rect,
    ),
    {
      rotateX: 12,
      rotateY: 12,
      glareX: 100,
      glareY: 0,
      scale: 1.02,
    },
  );
});
