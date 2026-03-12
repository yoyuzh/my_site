import { describe, expect, it } from 'vitest'

import { calculateRevealRadius, resolveInitialTheme, toggleTheme } from './theme'

describe('resolveInitialTheme', () => {
  it('uses stored theme when present', () => {
    expect(resolveInitialTheme('dark', false)).toBe('dark')
    expect(resolveInitialTheme('light', true)).toBe('light')
  })

  it('falls back to system preference when no stored theme', () => {
    expect(resolveInitialTheme(null, true)).toBe('dark')
    expect(resolveInitialTheme(null, false)).toBe('light')
  })
})

describe('toggleTheme', () => {
  it('toggles between light and dark', () => {
    expect(toggleTheme('light')).toBe('dark')
    expect(toggleTheme('dark')).toBe('light')
  })
})

describe('calculateRevealRadius', () => {
  it('returns max distance from origin to viewport corners', () => {
    const radius = calculateRevealRadius(100, 100, 1000, 600)
    expect(radius).toBeCloseTo(Math.hypot(900, 500), 4)
  })
})
