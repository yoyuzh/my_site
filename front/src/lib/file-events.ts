export function subscribeToFileEvents(onEvent: (event: any) => void) {
  const eventSource = new EventSource('/api/v2/files/events');
  
  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      onEvent(data);
    } catch (error) {
      console.error('Failed to parse file event:', error);
    }
  };

  return () => {
    eventSource.close();
  };
}
