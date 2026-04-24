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
    <header className="fixed inset-x-0 top-0 z-50 border-b border-white/40 bg-white/70 backdrop-blur-xl dark:border-white/10 dark:bg-[#0B0D12]/68">
      <div className="mx-auto flex h-[68px] max-w-[1600px] items-center justify-between px-4 lg:px-6">
        <Link to="/" className="min-w-0">
          <BrandMark
            size={38}
            subtitle="Personal Cloud"
            textClassName="hidden sm:block"
          />
        </Link>

        <div className="flex items-center gap-4">
          {meta && (
            <span className="text-xs font-semibold text-text-secondary-light dark:text-text-secondary-dark font-funnel">
              {meta}
            </span>
          )}
          <button
            type="button"
            aria-label="Toggle color theme"
            onClick={toggleTheme}
            className="flex h-10 w-10 items-center justify-center rounded-full text-text-secondary-light transition-colors hover:bg-black/5 dark:text-text-secondary-dark dark:hover:bg-white/5"
          >
            {theme === 'light' ? <Moon size={20} /> : <Sun size={20} />}
          </button>
        </div>
      </div>
    </header>
  );
};

export default Topbar;
