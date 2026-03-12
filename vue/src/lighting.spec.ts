import { describe, expect, it } from 'vitest'

import { isRectNearPointer, isRectVisible, pickNearestRectIndices, shouldRunLightingFrame } from './lighting'

describe('isRectVisible', () => {
  it('returns true when rect intersects viewport', () => {
    expect(isRectVisible({ left: 10, top: 10, right: 110, bottom: 110 }, 400, 300)).toBe(true)
  })

  it('returns false when rect is far outside viewport', () => {
    expect(isRectVisible({ left: 900, top: 900, right: 980, bottom: 980 }, 400, 300)).toBe(false)
  })
})

describe('isRectNearPointer', () => {
  it('returns true when pointer is inside rect', () => {
    expect(isRectNearPointer({ left: 100, top: 100, right: 200, bottom: 200 }, 150, 150, 160)).toBe(true)
  })

  it('returns true when pointer is close to rect edge', () => {
    expect(isRectNearPointer({ left: 100, top: 100, right: 200, bottom: 200 }, 250, 150, 60)).toBe(true)
  })

  it('returns false when pointer is far from rect', () => {
    expect(isRectNearPointer({ left: 100, top: 100, right: 200, bottom: 200 }, 500, 500, 100)).toBe(false)
  })
})

describe('shouldRunLightingFrame', () => {
  it('throttles frames above max fps', () => {
    expect(shouldRunLightingFrame(20, 0, 30)).toBe(false)
    expect(shouldRunLightingFrame(40, 0, 30)).toBe(true)
  })
})

describe('pickNearestRectIndices', () => {
  it('returns nearest indices and honors max count', () => {
    const rects = [
      { left: 0, top: 0, right: 60, bottom: 60 },
      { left: 100, top: 0, right: 160, bottom: 60 },
      { left: 200, top: 0, right: 260, bottom: 60 },
      { left: 300, top: 0, right: 360, bottom: 60 },
    ]
    const indices = pickNearestRectIndices(rects, 120, 20, 240, 2)
    expect(indices).toEqual([1, 0])
  })
})
