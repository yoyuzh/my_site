import React from 'react';
import { UploadCloud } from 'lucide-react';
import { useUploadQueue } from '../../hooks/useUploadQueue';
import clsx from 'clsx';

interface UploadTaskTriggerProps {
  onClick: () => void;
  active: boolean;
}

const UploadTaskTrigger: React.FC<UploadTaskTriggerProps> = ({ onClick, active }) => {
  const { tasks } = useUploadQueue();
  const uploadingCount = tasks.filter((t) => t.status === 'uploading' || t.status === 'preparing' || t.status === 'waiting').length;

  if (tasks.length === 0) return null;

  return (
    <button
      type="button"
      onClick={onClick}
      className={clsx(
        "relative flex h-10 w-10 items-center justify-center rounded-full transition-colors",
        active 
          ? "bg-blue-500 text-white" 
          : "text-text-secondary-light hover:bg-black/5 dark:text-text-secondary-dark dark:hover:bg-white/5"
      )}
      aria-label="Upload tasks"
    >
      <UploadCloud size={20} className={clsx(uploadingCount > 0 && "animate-bounce")} />
      {uploadingCount > 0 && (
        <span className="absolute -right-0.5 -top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-red-500 text-[10px] font-bold text-white shadow-sm">
          {uploadingCount}
        </span>
      )}
    </button>
  );
};

export default UploadTaskTrigger;
