import React from 'react';
import { Link } from 'react-router-dom';
import { Moon, Sun } from 'lucide-react';
import { useTheme } from '../hooks/useTheme';
import BrandMark from './BrandMark';

interface TopbarProps {
  meta?: string;
}

const Topbar: React.FC<TopbarProps> = ({ meta }) => {
  const { theme, toggleTheme } = useTheme();

  return (
    <header className="fixed top-0 left-0 right-0 h-[72px] bg-white/80 dark:bg-[#111117]/80 backdrop-blur-md border-b border-[#D9E3F2] dark:border-[#222233] z-50 flex items-center justify-between px-9">
      <Link to="/" className="min-w-0">
        <BrandMark
          size={40}
          subtitle="Personal Cloud"
          textClassName="hidden sm:block"
        />
      </Link>

      <div className="flex items-center gap-6">
        {meta && (
          <span className="text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark font-funnel">
            {meta}
          </span>
        )}
        <button 
          type="button"
          onClick={toggleTheme}
          className="p-2 rounded-full hover:bg-black/5 dark:hover:bg-white/5 transition-colors text-text-secondary-light dark:text-text-secondary-dark"
        >
          {theme === 'light' ? <Moon size={20} /> : <Sun size={20} />}
        </button>
      </div>
    </header>
  );
};

export default Topbar;
