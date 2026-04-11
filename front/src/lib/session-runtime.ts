import { getSession, setSession, clearSession, type PortalSession } from './session';

export type AuthFailureReason = 'EXPIRED' | 'REVOKED' | 'BANNED' | 'CONCURRENT_LOGIN' | 'UNKNOWN';

export interface SessionState {
  session: PortalSession | null;
  isValid: boolean;
  lastAuthFailure: AuthFailureReason | null;
}

type SessionChangeListener = (state: SessionState) => void;

class SessionRuntime {
  private listeners: Set<SessionChangeListener> = new Set();
  private state: SessionState;

  constructor() {
    this.state = {
      session: getSession(),
      isValid: !!getSession(),
      lastAuthFailure: null,
    };

    // 监听原生 storage 事件以支持多标签同步
    window.addEventListener('storage', (e) => {
      if (e.key === 'portal-session') {
        this.syncWithStorage();
      }
    });

    // 监听旧的自定义事件以保持兼容
    window.addEventListener('portal-session-changed', () => {
      this.syncWithStorage();
    });
  }

  private syncWithStorage() {
    const session = getSession();
    this.updateState({
      session,
      isValid: !!session,
    });
  }

  private updateState(patch: Partial<SessionState>) {
    this.state = { ...this.state, ...patch };
    this.notify();
  }

  getState(): SessionState {
    return this.state;
  }

  subscribe(listener: SessionChangeListener) {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify() {
    this.listeners.forEach((l) => l(this.state));
  }

  updateSession(session: PortalSession) {
    setSession(session);
    this.updateState({ session, isValid: true, lastAuthFailure: null });
  }

  handleAuthFailure(reason: AuthFailureReason) {
    clearSession();
    this.updateState({ 
      session: null, 
      isValid: false, 
      lastAuthFailure: reason 
    });
  }

  logout() {
    clearSession();
    this.updateState({ 
      session: null, 
      isValid: false, 
      lastAuthFailure: null 
    });
  }
}

export const sessionRuntime = new SessionRuntime();
