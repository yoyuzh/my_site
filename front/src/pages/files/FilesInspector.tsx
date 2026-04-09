import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/src/components/ui/card';
import { FileTypeIcon } from '@/src/components/ui/FileTypeIcon';
import { Button } from '@/src/components/ui/button';
import { Share2, Edit2, Folder, Copy, RotateCcw, Trash2, Download } from 'lucide-react';
import { cn } from '@/src/lib/utils';
import type { UiFile } from './file-types';

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs font-medium text-slate-500 mb-1">{label}</p>
      <p className="text-sm text-slate-300">{value}</p>
    </div>
  );
}

export function FilesInspector({
  selectedFile,
  currentPath,
  shareStatus,
  backgroundTaskActionId,
  onShare,
  onRename,
  onMove,
  onCopy,
  onCreateMediaMetadataTask,
  onDelete,
  onFolderDoubleClick,
  onDownload,
}: {
  selectedFile: UiFile;
  currentPath: string[];
  shareStatus: string;
  backgroundTaskActionId: number | null;
  onShare: (file: UiFile) => void;
  onRename: (file: UiFile) => void;
  onMove: (file: UiFile) => void;
  onCopy: (file: UiFile) => void;
  onCreateMediaMetadataTask: () => void;
  onDelete: (file: UiFile) => void;
  onFolderDoubleClick: (file: UiFile) => void;
  onDownload: (file: UiFile) => void;
}) {
  return (
    <Card className="h-full">
      <CardHeader className="pb-4 border-b border-white/10">
        <CardTitle className="text-base">详细信息</CardTitle>
      </CardHeader>
      <CardContent className="p-6 space-y-6">
        <div className="flex w-full flex-col items-center text-center space-y-3">
          <FileTypeIcon type={selectedFile.type} size="lg" />
          <h3 className="w-full truncate text-sm font-medium text-white" title={selectedFile.name}>
            {selectedFile.name}
          </h3>
        </div>

        <div className="space-y-4">
          <DetailItem label="位置" value={`网盘 > ${currentPath.length === 0 ? '根目录' : currentPath.join(' > ')}`} />
          <DetailItem label="大小" value={selectedFile.size} />
          <DetailItem label="修改时间" value={selectedFile.modified} />
          <DetailItem label="类型" value={selectedFile.typeLabel} />
        </div>

        <div className="pt-4 space-y-3 border-t border-white/10">
          <div className="grid grid-cols-2 gap-3">
            {selectedFile.type !== 'folder' ? (
              <Button variant="outline" className="w-full gap-2 bg-white/5 border-white/10 hover:bg-white/10" onClick={() => onShare(selectedFile)}>
                <Share2 className="w-4 h-4" /> 分享链接
              </Button>
            ) : null}
            <Button variant="outline" className="w-full gap-2 bg-white/5 border-white/10 hover:bg-white/10" onClick={() => onRename(selectedFile)}>
              <Edit2 className="w-4 h-4" /> 重命名
            </Button>
            <Button variant="outline" className="w-full gap-2 bg-white/5 border-white/10 hover:bg-white/10" onClick={() => onMove(selectedFile)}>
              <Folder className="w-4 h-4" /> 移动
            </Button>
            <Button variant="outline" className="w-full gap-2 bg-white/5 border-white/10 hover:bg-white/10" onClick={() => onCopy(selectedFile)}>
              <Copy className="w-4 h-4" /> 复制到
            </Button>
            {selectedFile.type !== 'folder' ? (
              <Button
                variant="outline"
                className="col-span-2 w-full gap-2 border-white/10 bg-white/5 hover:bg-white/10"
                onClick={onCreateMediaMetadataTask}
                disabled={backgroundTaskActionId === selectedFile.id}
              >
                <RotateCcw className={cn('w-4 h-4', backgroundTaskActionId === selectedFile.id ? 'animate-spin' : '')} />
                {backgroundTaskActionId === selectedFile.id ? '创建中...' : '提取媒体信息'}
              </Button>
            ) : null}
            <Button
              variant="outline"
              className="w-full gap-2 border-red-500/20 bg-red-500/5 text-red-400 hover:bg-red-500/10 hover:text-red-300"
              onClick={() => onDelete(selectedFile)}
            >
              <Trash2 className="w-4 h-4" /> 删除
            </Button>
          </div>
          {selectedFile.type === 'folder' && (
            <div className="space-y-3">
              <Button variant="default" className="w-full gap-2" onClick={() => onFolderDoubleClick(selectedFile)}>
                打开文件夹
              </Button>
              <Button variant="default" className="w-full gap-2" onClick={() => onDownload(selectedFile)}>
                <Download className="w-4 h-4" /> 下载文件夹
              </Button>
            </div>
          )}
          {selectedFile.type !== 'folder' && (
            <Button variant="default" className="w-full gap-2" onClick={() => onDownload(selectedFile)}>
              <Download className="w-4 h-4" /> 下载文件
            </Button>
          )}
          {shareStatus && selectedFile.type !== 'folder' ? (
            <div className="rounded-xl border border-emerald-500/20 bg-emerald-500/10 px-3 py-2 text-xs text-emerald-200">
              {shareStatus}
            </div>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}
