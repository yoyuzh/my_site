interface RemoteIceCapableConnection {
  remoteDescription: RTCSessionDescription | null;
  addIceCandidate(candidate: RTCIceCandidateInit): Promise<void>;
}

export async function handleRemoteIceCandidate(
  connection: RemoteIceCapableConnection,
  pendingCandidates: RTCIceCandidateInit[],
  candidate: RTCIceCandidateInit,
) {
  if (!connection.remoteDescription) {
    return [...pendingCandidates, candidate];
  }

  await connection.addIceCandidate(candidate);
  return pendingCandidates;
}

export async function flushPendingRemoteIceCandidates(
  connection: RemoteIceCapableConnection,
  pendingCandidates: RTCIceCandidateInit[],
) {
  if (!connection.remoteDescription || pendingCandidates.length === 0) {
    return pendingCandidates;
  }

  for (const candidate of pendingCandidates) {
    await connection.addIceCandidate(candidate);
  }

  return [];
}
