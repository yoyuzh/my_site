import React, { useState } from 'react';
import { motion } from 'motion/react';
import { Card, CardContent, CardHeader, CardTitle } from '@/src/components/ui/card';
import { Button } from '@/src/components/ui/button';
import { 
  Folder, FileText, Image as ImageIcon, Download, Monitor, 
  Star, ChevronRight, Upload, Plus, LayoutGrid, List, File,
  MoreVertical
} from 'lucide-react';
import { cn } from '@/src/lib/utils';

const QUICK_ACCESS = [
  { name: '桌面', icon: Monitor },
  { name: '下载', icon: Download },
  { name: '文档', icon: FileText },
  { name: '图片', icon: ImageIcon },
];

const DIRECTORIES = [
  { name: '我的文件', icon: Folder },
  { name: '课程资料', icon: Folder },
  { name: '项目归档', icon: Folder },
  { name: '收藏夹', icon: Star },
];

const MOCK_FILES_DB: Record<string, any[]> = {
  '我的文件': [
    { id: 1, name: '软件工程期末复习资料.pdf', type: 'pdf', size: '2.4 MB', modified: '2025-01-15 14:30' },
    { id: 2, name: '2025春季学期课表.xlsx', type: 'excel', size: '156 KB', modified: '2025-02-28 09:15' },
    { id: 3, name: '项目架构设计图.png', type: 'image', size: '4.1 MB', modified: '2025-03-01 16:45' },
    { id: 4, name: '实验报告模板.docx', type: 'word', size: '45 KB', modified: '2025-03-05 10:20' },
    { id: 5, name: '前端学习笔记', type: 'folder', size: '—', modified: '2025-03-10 11:00' },
  ],
  '课程资料': [
    { id: 6, name: '高等数学', type: 'folder', size: '—', modified: '2025-02-20 10:00' },
    { id: 7, name: '大学物理', type: 'folder', size: '—', modified: '2025-02-21 11:00' },
    { id: 8, name: '软件工程', type: 'folder', size: '—', modified: '2025-02-22 14:00' },
  ],
  '项目归档': [
    { id: 9, name: '2024秋季学期项目', type: 'folder', size: '—', modified: '2024-12-20 15:30' },
    { id: 10, name: '个人博客源码.zip', type: 'archive', size: '15.2 MB', modified: '2025-01-05 09:45' },
  ],
  '收藏夹': [
    { id: 11, name: '常用工具网站.txt', type: 'document', size: '2 KB', modified: '2025-03-01 10:00' },
  ],
  '我的文件/前端学习笔记': [
    { id: 12, name: 'React Hooks 详解.md', type: 'document', size: '12 KB', modified: '2025-03-08 09:00' },
    { id: 13, name: 'Tailwind 技巧.md', type: 'document', size: '8 KB', modified: '2025-03-09 14:20' },
    { id: 14, name: '示例代码', type: 'folder', size: '—', modified: '2025-03-10 10:00' },
  ],
  '课程资料/软件工程': [
    { id: 15, name: '需求规格说明书.pdf', type: 'pdf', size: '1.2 MB', modified: '2025-03-05 16:00' },
    { id: 16, name: '系统设计文档.docx', type: 'word', size: '850 KB', modified: '2025-03-06 11:30' },
  ]
};

export default function Files() {
  const [currentPath, setCurrentPath] = useState<string[]>(['我的文件']);
  const [selectedFile, setSelectedFile] = useState<any | null>(null);

  const activeDir = currentPath[currentPath.length - 1];
  const pathKey = currentPath.join('/');
  const currentFiles = MOCK_FILES_DB[pathKey] || [];

  const handleSidebarClick = (name: string) => {
    setCurrentPath([name]);
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
                className="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium text-slate-300 hover:text-white hover:bg-white/5 transition-colors"
              >
                <item.icon className="w-4 h-4 text-slate-400" />
                {item.name}
              </button>
            ))}
          </div>

          <div className="space-y-1">
            <p className="px-3 text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">网盘目录</p>
            {DIRECTORIES.map((item) => (
              <button
                key={item.name}
                onClick={() => handleSidebarClick(item.name)}
                className={cn(
                  "w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors",
                  currentPath.length === 1 && currentPath[0] === item.name 
                    ? "bg-[#336EFF]/20 text-[#336EFF]" 
                    : "text-slate-300 hover:text-white hover:bg-white/5"
                )}
              >
                <item.icon className={cn("w-4 h-4", currentPath.length === 1 && currentPath[0] === item.name ? "text-[#336EFF]" : "text-slate-400")} />
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
            <button className="hover:text-white transition-colors">网盘</button>
            {currentPath.map((pathItem, index) => (
              <React.Fragment key={index}>
                <ChevronRight className="w-4 h-4 mx-1" />
                <button 
                  onClick={() => handleBreadcrumbClick(index)}
                  className={cn("transition-colors", index === currentPath.length - 1 ? "text-white font-medium" : "hover:text-white")}
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
                      "group cursor-pointer transition-colors border-b border-white/5 last:border-0",
                      selectedFile?.id === file.id ? "bg-[#336EFF]/10" : "hover:bg-white/[0.02]"
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
                        <span className={cn("text-sm font-medium", selectedFile?.id === file.id ? "text-[#336EFF]" : "text-slate-200")}>
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
          <Button variant="default" className="gap-2">
            <Upload className="w-4 h-4" /> 上传文件
          </Button>
          <Button variant="outline" className="gap-2">
            <Plus className="w-4 h-4" /> 新建文件夹
          </Button>
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
                <DetailItem label="位置" value={`网盘 > ${currentPath.join(' > ')}`} />
                <DetailItem label="大小" value={selectedFile.size} />
                <DetailItem label="修改时间" value={selectedFile.modified} />
                <DetailItem label="类型" value={selectedFile.type.toUpperCase()} />
              </div>

              {selectedFile.type !== 'folder' && (
                <Button variant="outline" className="w-full gap-2 mt-4">
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

function DetailItem({ label, value }: { label: string, value: string }) {
  return (
    <div>
      <p className="text-xs font-medium text-slate-500 mb-1">{label}</p>
      <p className="text-sm text-slate-300">{value}</p>
    </div>
  );
}
