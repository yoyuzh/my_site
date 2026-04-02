export const MOBILE_APP_MAX_WIDTH = 768;

export function shouldUseMobileApp(width: number) {
  return width < MOBILE_APP_MAX_WIDTH;
}
