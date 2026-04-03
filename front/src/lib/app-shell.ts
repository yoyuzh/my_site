export const MOBILE_APP_MAX_WIDTH = 768;
export const PORTAL_CLIENT_HEADER = 'X-Yoyuzh-Client';

export type PortalClientType = 'desktop' | 'mobile';

export function shouldUseMobileApp(width: number) {
  return width < MOBILE_APP_MAX_WIDTH;
}

export function isNativeAppShellLocation(location: Location | URL | null) {
  if (!location) {
    return false;
  }

  const hostname = location.hostname || '';
  const protocol = location.protocol || '';
  const port = location.port || '';

  if (protocol === 'capacitor:') {
    return true;
  }

  const isLocalhostHost = hostname === 'localhost' || hostname === '127.0.0.1';
  const isCapacitorLocalScheme = protocol === 'http:' || protocol === 'https:';

  return isLocalhostHost && isCapacitorLocalScheme && port === '';
}

function resolveRuntimeViewportWidth() {
  if (typeof globalThis.innerWidth === 'number' && Number.isFinite(globalThis.innerWidth)) {
    return globalThis.innerWidth;
  }

  if (typeof window !== 'undefined' && typeof window.innerWidth === 'number') {
    return window.innerWidth;
  }

  return null;
}

function resolveRuntimeLocation() {
  if (typeof globalThis.location !== 'undefined') {
    return globalThis.location;
  }

  if (typeof window !== 'undefined') {
    return window.location;
  }

  return null;
}

export function resolvePortalClientType({
  location = resolveRuntimeLocation(),
  viewportWidth = resolveRuntimeViewportWidth(),
}: {
  location?: Location | URL | null;
  viewportWidth?: number | null;
} = {}): PortalClientType {
  if (isNativeAppShellLocation(location)) {
    return 'mobile';
  }

  if (typeof viewportWidth === 'number' && shouldUseMobileApp(viewportWidth)) {
    return 'mobile';
  }

  return 'desktop';
}
