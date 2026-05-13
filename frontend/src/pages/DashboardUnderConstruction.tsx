import React from 'react';
import { useLocation } from 'react-router-dom';
import { CloudDownload, FileText, Image as ImageIcon, type LucideIcon, Music2, UsersRound, Video } from 'lucide-react';
import DashboardLayout from '../components/DashboardLayout';
import GlassPanel from '../components/ui/GlassPanel';

type PlaceholderMeta = {
  title: string;
  description: string;
  icon: LucideIcon;
};

const PLACEHOLDER_META: Record<string, PlaceholderMeta> = {
  images: {
    title: '图片',
    description: '图片视图、智能分类和批量浏览还在完善中。',
    icon: ImageIcon,
  },
  videos: {
    title: '视频',
    description: '视频聚合、封面和播放管理入口还未接完。',
    icon: Video,
  },
  music: {
    title: '音乐',
    description: '音乐分类、播放列表和媒体信息页还在开发中。',
    icon: Music2,
  },
  documents: {
    title: '文档',
    description: '文档聚合、最近编辑和预览工作流还未完成。',
    icon: FileText,
  },
  'shared-with-me': {
    title: '与我共享',
    description: '共享给我的文件列表和接收视图还在整理。',
    icon: UsersRound,
  },
  'offline-downloads': {
    title: '离线下载',
    description: '离线下载任务入口和目标管理正在接入。',
    icon: CloudDownload,
  },
};

const DashboardUnderConstruction: React.FC = () => {
  const location = useLocation();
  const segment = location.pathname.split('/').pop() || 'placeholder';
  const meta = PLACEHOLDER_META[segment] ?? {
    title: '功能建设中',
    description: '这个入口还在开发中，后续会接入完整功能。',
    icon: FileText,
  };
  const Icon = meta.icon;

  return (
    <DashboardLayout title={meta.title}>
      <GlassPanel className="flex min-h-[420px] flex-col items-center justify-center px-8 py-12 text-center">
        <div className="mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-brand-light/10 text-brand-light dark:bg-brand-dark/10 dark:text-brand-dark">
          <Icon size={30} />
        </div>
        <h3 className="text-2xl font-semibold text-text-primary-light dark:text-white">
          {meta.title} 还未完成
        </h3>
        <p className="mt-3 max-w-md text-sm leading-6 text-text-secondary-light dark:text-text-secondary-dark">
          {meta.description}
        </p>
      </GlassPanel>
    </DashboardLayout>
  );
};

export default DashboardUnderConstruction;
