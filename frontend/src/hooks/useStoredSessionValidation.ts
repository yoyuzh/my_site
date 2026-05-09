import { useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { getProfile } from '../lib/auth';
import { clearSession, getSession, type PortalSession } from '../lib/session';

export type StoredSessionValidationStatus = 'anonymous' | 'checking' | 'authenticated';

type StoredSessionValidationState = {
  status: StoredSessionValidationStatus;
  session: PortalSession | null;
};

async function validateStoredSession() {
  const session = getSession();
  if (!session?.refreshToken) {
    if (session) {
      clearSession();
    }
    return null;
  }

  try {
    await getProfile();
    return getSession();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      clearSession();
      return null;
    }
    throw error;
  }
}

export function useStoredSessionValidation() {
  const [state, setState] = useState<StoredSessionValidationState>(() => {
    const session = getSession();
    return {
      status: session ? 'checking' : 'anonymous',
      session,
    };
  });

  useEffect(() => {
    let cancelled = false;

    const validate = () => {
      const session = getSession();
      if (!session) {
        setState({ status: 'anonymous', session: null });
        return;
      }

      setState({ status: 'checking', session });

      validateStoredSession()
        .then((validatedSession) => {
          if (cancelled) {
            return;
          }
          setState({
            status: validatedSession ? 'authenticated' : 'anonymous',
            session: validatedSession,
          });
        })
        .catch((error: unknown) => {
          console.error('Failed to validate stored session', error);
          if (cancelled) {
            return;
          }
          setState({
            status: 'anonymous',
            session: null,
          });
        });
    };

    const handleSessionChanged = (event: Event) => {
      const nextSession = (event as CustomEvent<PortalSession | null>).detail ?? getSession();
      setState({
        status: nextSession ? 'authenticated' : 'anonymous',
        session: nextSession,
      });
    };

    validate();
    window.addEventListener('portal-session-changed', handleSessionChanged as EventListener);

    return () => {
      cancelled = true;
      window.removeEventListener('portal-session-changed', handleSessionChanged as EventListener);
    };
  }, []);

  return state;
}
