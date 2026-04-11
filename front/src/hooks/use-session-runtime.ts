import { useEffect, useState } from 'react';
import { sessionRuntime, type SessionState } from '../lib/session-runtime';

export function useSessionRuntime(): SessionState {
  const [state, setState] = useState<SessionState>(() => sessionRuntime.getState());

  useEffect(() => {
    const unsubscribe = sessionRuntime.subscribe((nextState) => {
      setState(nextState);
    });
    return unsubscribe;
  }, []);

  return state;
}
