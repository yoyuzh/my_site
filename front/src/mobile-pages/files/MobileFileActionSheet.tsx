import React from 'react';
import { Download, Share2, Copy, Folder, Edit2, Trash2 } from 'lucide-react';
import { Button } from '@/src/components/ui/button';
import { FileTypeIcon } from '@/src/components/ui/FileTypeIcon';
import { cn } from '@/src/lib/utils';
import type { UiFile } from '@/src/pages/files/file-types';
import { ResponsiveSheet } from '@/src/mobile-components/ResponsiveSheet';

export function ActionButton({ icon: Icon, label, color, onClick }: any) {
  return (
    <div className="flex flex-col items-center gap-2 p-2 hover:bg-white/5 rounded-xl transition-colors active:bg-white/10" onClick={onClick}>
       <div className={cn("p-3 rounded-full bg-black/20 border border-white/5 shadow-inner", color)}>
          <Icon className="w-5 h-5" />
       </div>
       <span className="text-xs text-slate-300">{label}</span>
    </div>
  );
}

export function MobileFileActionSheet({
  isOpen,
  selectedFile,
  shareStatus,
  onClose,
  onDownload,
  onShare,
  onMove,
  onCopy,
  onRename,
  onDelete,
}: {
  isOpen: boolean;
  selectedFile: UiFile | null;
  shareStatus: string;
  onClose: () => void;
  onDownload: () => void;
  onShare: (file: UiFile) => void;
  onMove: (file: UiFile) => void;
  onCopy: (file: UiFile) => void;
  onRename: (file: UiFile) => void;
  onDelete: (file: UiFile) => void;
}) {
  return (
    <ResponsiveSheet isOpen={isOpen && selectedFile !== null} onClose={onClose}>
      {selectedFile && (
        <>
          <div className="flex border-b border-white/10 pb-4 mb-4 gap-4 items-center px-2">
             <FileTypeIcon type={selectedFile.type} size="md" />
             <div className="min-w-0">
                <p className="text-sm font-semibold truncate text-white">{selectedFile.name}</p>
                <p className="text-xs text-slate-400 mt-1">{selectedFile.size} • {selectedFile.modified}</p>
             </div>
          </div>
          <div className="grid grid-cols-4 gap-2 mb-4 px-2">
             <ActionButton icon={Download} label="下载" onClick={() => { onDownload(); onClose(); }} color="text-amber-400" />
             {selectedFile.type !== 'folder' && <ActionButton icon={Share2} label="分享" onClick={() => onShare(selectedFile)} color="text-emerald-400" />}
             <ActionButton icon={Copy} label="复制" onClick={() => onCopy(selectedFile)} color="text-blue-400" />
             <ActionButton icon={Folder} label="移动" onClick={() => onMove(selectedFile)} color="text-indigo-400" />
             <ActionButton icon={Edit2} label="重命名" onClick={() => onRename(selectedFile)} color="text-slate-300" />
             <ActionButton icon={Trash2} label="删除" onClick={() => onDelete(selectedFile)} color="text-red-400" />
          </div>
          {shareStatus && <div className="mx-2 mt-2 p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-[10px] text-emerald-400 break-all">{shareStatus}</div>}
          <Button variant="ghost" onClick={onClose} className="mt-4 text-slate-400 py-6 text-sm">取消</Button>
        </>
      )}
    </ResponsiveSheet>
  );
}
