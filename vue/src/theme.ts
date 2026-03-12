export type Theme = 'light' | 'dark'

export function resolveInitialTheme(storedTheme: Theme | null, prefersDark: boolean): Theme {
  if (storedTheme === 'light' || storedTheme === 'dark') {
    return storedTheme
  }
  return prefersDark ? 'dark' : 'light'
}

export function toggleTheme(currentTheme: Theme): Theme {
  return currentTheme === 'dark' ? 'light' : 'dark'
}

export function calculateRevealRadius(
  originX: number,
  originY: number,
  viewportWidth: number,
  viewportHeight: number,
) {
  const distances = [
    Math.hypot(originX, originY),
    Math.hypot(viewportWidth - originX, originY),
    Math.hypot(originX, viewportHeight - originY),
    Math.hypot(viewportWidth - originX, viewportHeight - originY),
  ]
  return Math.max(...distances)
}
