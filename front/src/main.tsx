import {StrictMode, useEffect, useState} from 'react';
import {createRoot} from 'react-dom/client';
import App from './App.tsx';
import MobileApp from './MobileApp.tsx';
import {AuthProvider} from './auth/AuthProvider.tsx';
import {shouldUseMobileApp} from './lib/app-shell.ts';
import './index.css';

function ResponsiveApp() {
  const [isMobileApp, setIsMobileApp] = useState(() => shouldUseMobileApp(window.innerWidth));

  useEffect(() => {
    function syncAppShell() {
      setIsMobileApp(shouldUseMobileApp(window.innerWidth));
    }

    window.addEventListener('resize', syncAppShell);
    return () => window.removeEventListener('resize', syncAppShell);
  }, []);

  return isMobileApp ? <MobileApp /> : <App />;
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <ResponsiveApp />
    </AuthProvider>
  </StrictMode>,
);
