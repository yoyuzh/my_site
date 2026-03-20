export const MAX_TRANSFER_BUFFERED_AMOUNT = 1024 * 1024;

export async function waitForTransferChannelDrain(
  channel: RTCDataChannel,
  maxBufferedAmount = MAX_TRANSFER_BUFFERED_AMOUNT,
) {
  if (channel.bufferedAmount <= maxBufferedAmount) {
    return;
  }

  await new Promise<void>((resolve) => {
    const timer = window.setInterval(() => {
      if (channel.readyState !== 'open' || channel.bufferedAmount <= maxBufferedAmount) {
        window.clearInterval(timer);
        resolve();
      }
    }, 40);
  });
}
