import { useState, useEffect } from 'react';

export function useIsMobile() {
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const checkIsMobile = () => {
      // Determine mobile based on aspect ratio (width / height < 1) or width < 768
      const isPortrait = window.innerWidth / window.innerHeight < 1;
      const isSmallScreen = window.innerWidth < 768;
      setIsMobile(isPortrait || isSmallScreen);
    };

    checkIsMobile();
    window.addEventListener('resize', checkIsMobile);
    return () => window.removeEventListener('resize', checkIsMobile);
  }, []);

  return isMobile;
}
