const DEFAULT_STUN_ICE_SERVERS: RTCIceServer[] = [
  { urls: 'stun:stun.cloudflare.com:3478' },
  { urls: 'stun:stun.l.google.com:19302' },
];

const RELAY_HINT =
  '当前环境只配置了 STUN，跨运营商或手机移动网络通常还需要 TURN 中继。';

type RawIceServer = {
  urls?: unknown;
  username?: unknown;
  credential?: unknown;
};

export const DEFAULT_TRANSFER_ICE_SERVERS = DEFAULT_STUN_ICE_SERVERS;

export function resolveTransferIceServers(rawConfig = import.meta.env?.VITE_TRANSFER_ICE_SERVERS_JSON) {
  if (typeof rawConfig !== 'string' || !rawConfig.trim()) {
    return DEFAULT_TRANSFER_ICE_SERVERS;
  }

  try {
    const parsed = JSON.parse(rawConfig) as unknown;
    if (!Array.isArray(parsed)) {
      return DEFAULT_TRANSFER_ICE_SERVERS;
    }

    const customServers = parsed
      .map(normalizeIceServer)
      .filter((server): server is RTCIceServer => server != null);

    if (customServers.length === 0) {
      return DEFAULT_TRANSFER_ICE_SERVERS;
    }

    return [...DEFAULT_TRANSFER_ICE_SERVERS, ...customServers];
  } catch {
    return DEFAULT_TRANSFER_ICE_SERVERS;
  }
}

export function hasRelayTransferIceServer(iceServers: RTCIceServer[]) {
  return iceServers.some((server) => toUrls(server.urls).some((url) => /^turns?:/i.test(url)));
}

export function appendTransferRelayHint(message: string, hasRelaySupport: boolean) {
  const normalizedMessage = message.trim();
  if (!normalizedMessage || hasRelaySupport || normalizedMessage.includes(RELAY_HINT)) {
    return normalizedMessage;
  }
  return `${normalizedMessage} ${RELAY_HINT}`;
}

function normalizeIceServer(rawServer: RawIceServer) {
  const urls = normalizeUrls(rawServer?.urls);
  if (urls == null) {
    return null;
  }

  const server: RTCIceServer = { urls };
  if (typeof rawServer.username === 'string' && rawServer.username.trim()) {
    server.username = rawServer.username.trim();
  }
  if (typeof rawServer.credential === 'string' && rawServer.credential.trim()) {
    server.credential = rawServer.credential.trim();
  }
  return server;
}

function normalizeUrls(rawUrls: unknown): string | string[] | null {
  if (typeof rawUrls === 'string' && rawUrls.trim()) {
    return rawUrls.trim();
  }

  if (!Array.isArray(rawUrls)) {
    return null;
  }

  const urls = rawUrls
    .filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
    .map((item) => item.trim());

  return urls.length > 0 ? urls : null;
}

function toUrls(urls: string | string[] | undefined) {
  if (!urls) {
    return [];
  }
  return Array.isArray(urls) ? urls : [urls];
}
