export interface RectLike {
  left: number
  top: number
  right: number
  bottom: number
}

export function isRectVisible(
  rect: RectLike,
  viewportWidth: number,
  viewportHeight: number,
  overscan = 96,
) {
  return !(
    rect.right < -overscan ||
    rect.bottom < -overscan ||
    rect.left > viewportWidth + overscan ||
    rect.top > viewportHeight + overscan
  )
}

export function isRectNearPointer(rect: RectLike, x: number, y: number, influence = 180) {
  return !(
    x < rect.left - influence ||
    x > rect.right + influence ||
    y < rect.top - influence ||
    y > rect.bottom + influence
  )
}

export function shouldRunLightingFrame(now: number, last: number, maxFps: number) {
  if (maxFps <= 0) return true
  const minDelta = 1000 / maxFps
  return now - last >= minDelta
}

function rectDistanceSquared(rect: RectLike, x: number, y: number) {
  const clampedX = Math.max(rect.left, Math.min(x, rect.right))
  const clampedY = Math.max(rect.top, Math.min(y, rect.bottom))
  const dx = x - clampedX
  const dy = y - clampedY
  return dx * dx + dy * dy
}

export function pickNearestRectIndices(
  rects: RectLike[],
  x: number,
  y: number,
  influence = 180,
  maxCount = 10,
) {
  if (!rects.length || maxCount <= 0) return []
  const influenceSquared = influence * influence
  const matches: Array<{ i: number; d: number }> = []

  for (let i = 0; i < rects.length; i += 1) {
    const rect = rects[i]
    if (!rect) continue
    const d = rectDistanceSquared(rect, x, y)
    if (d <= influenceSquared) {
      matches.push({ i, d })
    }
  }

  matches.sort((a, b) => a.d - b.d)
  return matches.slice(0, maxCount).map((item) => item.i)
}
