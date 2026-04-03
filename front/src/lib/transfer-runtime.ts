export const SAFE_TRANSFER_CHUNK_SIZE = 64 * 1024;
export const MAX_TRANSFER_CHUNK_SIZE = 64 * 1024;
export const TRANSFER_PROGRESS_UPDATE_INTERVAL_MS = 120;

export function resolveTransferChunkSize(maxMessageSize?: number | null) {
  if (!Number.isFinite(maxMessageSize) || !maxMessageSize || maxMessageSize <= 0) {
    return SAFE_TRANSFER_CHUNK_SIZE;
  }

  return Math.max(1024, Math.min(maxMessageSize, MAX_TRANSFER_CHUNK_SIZE));
}

export function shouldPublishTransferProgress(params: {
  nextProgress: number;
  previousProgress: number;
  now: number;
  lastPublishedAt: number;
}) {
  const { nextProgress, previousProgress, now, lastPublishedAt } = params;

  if (nextProgress === previousProgress) {
    return false;
  }

  if (nextProgress >= 100 || nextProgress <= 0) {
    return true;
  }

  return now - lastPublishedAt >= TRANSFER_PROGRESS_UPDATE_INTERVAL_MS;
}
