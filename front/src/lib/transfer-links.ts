export type TransferRouterMode = 'browser' | 'hash';

export const APP_TRANSFER_ROUTE = '/transfer';
export const PUBLIC_TRANSFER_ROUTE = '/transfer';
export const LEGACY_PUBLIC_TRANSFER_ROUTE = '/t';

export function getTransferRouterMode(mode: string | undefined = import.meta.env?.VITE_ROUTER_MODE): TransferRouterMode {
  return mode === 'hash' ? 'hash' : 'browser';
}

export function buildTransferShareUrl(
  origin: string,
  sessionId: string,
  routerMode: TransferRouterMode = 'browser',
) {
  const normalizedOrigin = origin.replace(/\/+$/, '');
  const encodedSessionId = encodeURIComponent(sessionId);

  if (routerMode === 'hash') {
    return `${normalizedOrigin}/#${PUBLIC_TRANSFER_ROUTE}?session=${encodedSessionId}`;
  }

  return `${normalizedOrigin}${PUBLIC_TRANSFER_ROUTE}?session=${encodedSessionId}`;
}
