import React, { useEffect, useRef, useState } from 'react';
import { motion } from 'motion/react';
import {
  Folder,
  FileText,
  Image as ImageIcon,
  Download,
  Monitor,
  ChevronRight,
  Upload,
  Plus,
  LayoutGrid,
  List,
  MoreVertical,
} from 'lucide-react';

import { Button } from '@/src/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/src/components/ui/card';
import { apiDownload, apiRequest } from '@/src/lib/api';
import { readCachedValue, writeCachedValue } from '@/src/lib/cache';
import { getFilesLastPathCacheKey, getFilesListCacheKey } from '@/src/lib/page-cache';
import type { FileMetadata, PageResponse } from '@/src/lib/types';
import { cn } from '@/src/lib/utils';

const QUICK_ACCESS = [
  { name: '桌面', icon: Monitor, path: [] as string[] },
  { name: '下载', icon: Download, path: ['下载'] },
  { name: '文档', icon: FileText, path: ['文档'] },
  { name: '图片', icon: ImageIcon, path: ['图片'] },
];

const DIRECTORIES = [
  { name: '下载', icon: Folder },
  { name: '文档', icon: Folder },
  { name: '图片', icon: Folder },
];

function toBackendPath(pathParts: string[]) {
  return pathParts.length === 0 ? '/' : `/${pathParts.join('/')}`;
}

function formatFileSize(size: number) {
  if (size <= 0) {
    return '—';
  }

  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(size) / Math.log(1024)), units.length - 1);
  const value = size / 1024 ** index;
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

function toUiFile(file: FileMetadata) {
  const extension = file.filename.includes('.') ? file.filename.split('.').pop()?.toLowerCase() : '';
  let type = extension || 'document';

  if (file.directory) {
    type = 'folder';
  } else if (file.contentType?.startsWith('image/')) {
    type = 'image';
  } else if (file.contentType?.includes('pdf')) {
    type = 'pdf';
  }

  return {
    id: file.id,
    name: file.filename,
    type,
    size: file.directory ? '—' : formatFileSize(file.size),
    modified: formatDateTime(file.createdAt),
  };
}

export default function Files() {
  const initialPath = readCachedValue<string[]>(getFilesLastPathCacheKey()) ?? [];
  const initialCachedFiles = readCachedValue<FileMetadata[]>(getFilesListCacheKey(toBackendPath(initialPath))) ?? [];
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [currentPath, setCurrentPath] = useState<string[]>(initialPath);
  const [selectedFile, setSelectedFile] = useState<any | null>(null);
  const [currentFiles, setCurrentFiles] = useState<any[]>(initialCachedFiles.map(toUiFile));

  const loadCurrentPath = async (pathParts: string[]) => {
    const response = await apiRequest<PageResponse<FileMetadata>>(
      `/files/list?path=${encodeURIComponent(toBackendPath(pathParts))}&page=0&size=100`
    );
    writeCachedValue(getFilesListCacheKey(toBackendPath(pathParts)), response.items);
    writeCachedValue(getFilesLastPathCacheKey(), pathParts);
    setCurrentFiles(response.items.map(toUiFile));
  };

  useEffect(() => {
    const cachedFiles = readCachedValue<FileMetadata[]>(getFilesListCacheKey(toBackendPath(currentPath)));
    writeCachedValue(getFilesLastPathCacheKey(), currentPath);

    if (cachedFiles) {
      setCurrentFiles(cachedFiles.map(toUiFile));
    }

    loadCurrentPath(currentPath).catch(() => {
      if (!cachedFiles) {
        setCurrentFiles([]);
      }
    });
  }, [currentPath]);

  const handleSidebarClick = (pathParts: string[]) => {
    setCurrentPath(pathParts);
    setSelectedFile(null);
  };

  const handleFolderDoubleClick = (file: any) => {
    if (file.type === 'folder') {
      setCurrentPath([...currentPath, file.name]);
      setSelectedFile(null);
    }
  };

  const handleBreadcrumbClick = (index: number) => {
    setCurrentPath(currentPath.slice(0, index + 1));
    setSelectedFile(null);
  };

  const handleUploadClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    const formData = new FormData();
    formData.append('file', file);

    await apiRequest(`/files/upload?path=${encodeURIComponent(toBackendPath(currentPath))}`, {
      method: 'POST',
      body: formData,
    });

    await loadCurrentPath(currentPath);
    event.target.value = '';
  };

  const handleCreateFolder = async () => {
    const folderName = window.prompt('请输入新文件夹名称');
    if (!folderName?.trim()) {
      return;
    }

    const basePath = toBackendPath(currentPath).replace(/\/$/, '');
    const fullPath = `${basePath}/${folderName.trim()}` || '/';

    await apiRequest('/files/mkdir', {
      method: 'POST',
      body: new URLSearchParams({
        path: fullPath.startsWith('/') ? fullPath : `/${fullPath}`,
      }),
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
      },
    });

    await loadCurrentPath(currentPath);
  };

  const handleDownload = async () => {
    if (!selectedFile || selectedFile.type === 'folder') {
      return;
    }

    const response = await apiDownload(`/files/download/${selectedFile.id}`);
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = selectedFile.name;
    link.click();
    window.URL.revokeObjectURL(url);
  };

  return (
    <div className="flex flex-col lg:flex-row gap-6 h-[calc(100vh-8rem)]">
      {/* Left Sidebar */}
      <Card className="w-full lg:w-64 shrink-0 flex flex-col h-full overflow-y-auto">
        <CardContent className="p-4 space-y-6">
          <div className="space-y-1">
            <p className="px-3 text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">快速访问</p>
            {QUICK_ACCESS.map((item) => (
              <button
                key={item.name}
                onClick={() => handleSidebarClick(item.path)}
                className={cn(
                  'w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                  currentPath.join('/') === item.path.join('/')
                    ? 'bg-[#336EFF]/20 text-[#336EFF]'
                    : 'text-slate-300 hover:text-white hover:bg-white/5'
                )}
              >
                <item.icon className={cn('w-4 h-4', currentPath.join('/') === item.path.join('/') ? 'text-[#336EFF]' : 'text-slate-400')} />
                {item.name}
              </button>
            ))}
          </div>

          <div className="space-y-1">
            <p className="px-3 text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">网盘目录</p>
            {DIRECTORIES.map((item) => (
              <button
                key={item.name}
                onClick={() => handleSidebarClick([item.name])}
                className={cn(
                  'w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                  currentPath.length === 1 && currentPath[0] === item.name
                    ? 'bg-[#336EFF]/20 text-[#336EFF]'
                    : 'text-slate-300 hover:text-white hover:bg-white/5'
                )}
              >
                <item.icon className={cn('w-4 h-4', currentPath.length === 1 && currentPath[0] === item.name ? 'text-[#336EFF]' : 'text-slate-400')} />
                {item.name}
              </button>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Middle Content */}
      <Card className="flex-1 flex flex-col h-full overflow-hidden">
        {/* Header / Breadcrumbs */}
        <div className="p-4 border-b border-white/10 flex items-center justify-between shrink-0">
          <div className="flex items-center text-sm text-slate-400">
            <button className="hover:text-white transition-colors" onClick={() => handleSidebarClick([])}>
              网盘
            </button>
            {currentPath.map((pathItem, index) => (
              <React.Fragment key={index}>
                <ChevronRight className="w-4 h-4 mx-1" />
                <button
                  onClick={() => handleBreadcrumbClick(index)}
                  className={cn('transition-colors', index === currentPath.length - 1 ? 'text-white font-medium' : 'hover:text-white')}
                >
                  {pathItem}
                </button>
              </React.Fragment>
            ))}
          </div>
          <div className="flex items-center gap-2 bg-black/20 p-1 rounded-lg">
            <button className="p-1.5 rounded-md bg-white/10 text-white"><List className="w-4 h-4" /></button>
            <button className="p-1.5 rounded-md text-slate-400 hover:text-white"><LayoutGrid className="w-4 h-4" /></button>
          </div>
        </div>

        {/* File List */}
        <div className="flex-1 overflow-y-auto p-4">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="text-xs font-semibold text-slate-500 uppercase tracking-wider border-b border-white/5">
                <th className="pb-3 pl-4 font-medium">名称</th>
                <th className="pb-3 font-medium hidden md:table-cell">修改日期</th>
                <th className="pb-3 font-medium hidden lg:table-cell">类型</th>
                <th className="pb-3 font-medium">大小</th>
                <th className="pb-3"></th>
              </tr>
            </thead>
            <tbody>
              {currentFiles.length > 0 ? (
                currentFiles.map((file) => (
                  <tr
                    key={file.id}
                    onClick={() => setSelectedFile(file)}
                    onDoubleClick={() => handleFolderDoubleClick(file)}
                    className={cn(
                      'group cursor-pointer transition-colors border-b border-white/5 last:border-0',
                      selectedFile?.id === file.id ? 'bg-[#336EFF]/10' : 'hover:bg-white/[0.02]'
                    )}
                  >
                    <td className="py-3 pl-4">
                      <div className="flex items-center gap-3">
                        {file.type === 'folder' ? (
                          <Folder className="w-5 h-5 text-[#336EFF]" />
                        ) : file.type === 'image' ? (
                          <ImageIcon className="w-5 h-5 text-purple-400" />
                        ) : (
                          <FileText className="w-5 h-5 text-blue-400" />
                        )}
                        <span className={cn('text-sm font-medium', selectedFile?.id === file.id ? 'text-[#336EFF]' : 'text-slate-200')}>
                          {file.name}
                        </span>
                      </div>
                    </td>
                    <td className="py-3 text-sm text-slate-400 hidden md:table-cell">{file.modified}</td>
                    <td className="py-3 text-sm text-slate-400 hidden lg:table-cell uppercase">{file.type}</td>
                    <td className="py-3 text-sm text-slate-400 font-mono">{file.size}</td>
                    <td className="py-3 pr-4 text-right">
                      <button className="p-1.5 rounded-md text-slate-500 opacity-0 group-hover:opacity-100 hover:bg-white/10 hover:text-white transition-all">
                        <MoreVertical className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={5} className="py-12 text-center text-slate-500">
                    <div className="flex flex-col items-center justify-center space-y-3">
                      <Folder className="w-12 h-12 opacity-20" />
                      <p className="text-sm">此文件夹为空</p>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Bottom Actions */}
        <div className="p-4 border-t border-white/10 flex items-center gap-3 shrink-0 bg-white/[0.01]">
          <Button variant="default" className="gap-2" onClick={handleUploadClick}>
            <Upload className="w-4 h-4" /> 上传文件
          </Button>
          <Button variant="outline" className="gap-2" onClick={handleCreateFolder}>
            <Plus className="w-4 h-4" /> 新建文件夹
          </Button>
          <input ref={fileInputRef} type="file" className="hidden" onChange={handleFileChange} />
        </div>
      </Card>

      {/* Right Sidebar (Details) */}
      {selectedFile && (
        <motion.div
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          className="w-full lg:w-72 shrink-0"
        >
          <Card className="h-full">
            <CardHeader className="pb-4 border-b border-white/10">
              <CardTitle className="text-base">详细信息</CardTitle>
            </CardHeader>
            <CardContent className="p-6 space-y-6">
              <div className="flex flex-col items-center text-center space-y-3">
                <div className="w-16 h-16 rounded-2xl bg-[#336EFF]/10 flex items-center justify-center">
                  {selectedFile.type === 'folder' ? (
                    <Folder className="w-8 h-8 text-[#336EFF]" />
                  ) : selectedFile.type === 'image' ? (
                    <ImageIcon className="w-8 h-8 text-purple-400" />
                  ) : (
                    <FileText className="w-8 h-8 text-blue-400" />
                  )}
                </div>
                <h3 className="text-sm font-medium text-white break-all">{selectedFile.name}</h3>
              </div>

              <div className="space-y-4">
                <DetailItem label="位置" value={`网盘 > ${currentPath.length === 0 ? '根目录' : currentPath.join(' > ')}`} />
                <DetailItem label="大小" value={selectedFile.size} />
                <DetailItem label="修改时间" value={selectedFile.modified} />
                <DetailItem label="类型" value={selectedFile.type.toUpperCase()} />
              </div>

              {selectedFile.type !== 'folder' && (
                <Button variant="outline" className="w-full gap-2 mt-4" onClick={handleDownload}>
                  <Download className="w-4 h-4" /> 下载文件
                </Button>
              )}
              {selectedFile.type === 'folder' && (
                <Button variant="default" className="w-full gap-2 mt-4" onClick={() => handleFolderDoubleClick(selectedFile)}>
                  打开文件夹
                </Button>
              )}
            </CardContent>
          </Card>
        </motion.div>
      )}
    </div>
  );
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs font-medium text-slate-500 mb-1">{label}</p>
      <p className="text-sm text-slate-300">{value}</p>
    </div>
  );
}
