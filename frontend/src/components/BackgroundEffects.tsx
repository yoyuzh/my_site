import React from 'react';

const BackgroundEffects: React.FC = () => {
  return (
    <div className="fixed inset-0 overflow-hidden pointer-events-none -z-10">
      {/* Halos */}
      <div 
        className="absolute w-[340px] h-[340px] rounded-full bg-[#D6E7FF] dark:bg-[#0066FF] opacity-70 dark:opacity-20 blur-[90px]"
        style={{ top: '-80px', right: '120px' }}
      />
      <div 
        className="absolute w-[260px] h-[260px] rounded-full bg-[#E7F0FF] dark:bg-[#0B1F5E] opacity-90 dark:opacity-30 blur-[80px]"
        style={{ bottom: '100px', left: '120px' }}
      />

      {/* Neon Streaks - extracted from .pen */}
      <div 
        className="absolute w-[9000px] h-[7px] bg-[#0066FF] opacity-45 blur-[40px] rotate-[6deg]"
        style={{ top: '1520px', left: '120px' }}
      />
      <div 
        className="absolute w-[7600px] h-[4px] bg-[#0B1F5E] opacity-42 blur-[36px] -rotate-[8deg]"
        style={{ top: '2360px', left: '780px' }}
      />
      
      {/* Decorative streak for top section */}
      <div 
        className="absolute w-full h-[1px] bg-gradient-to-r from-transparent via-brand-light to-transparent opacity-20 top-20 shadow-[0_0_15px_rgba(15,107,255,0.5)]"
      />
    </div>
  );
};

export default BackgroundEffects;
