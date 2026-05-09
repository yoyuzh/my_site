import {
  Activity,
  Database,
  FileKey,
  FolderKey,
  ListChecks,
  Settings,
  Share2,
  Shield,
  Users,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

export type AdminPermissionCode =
  | 'admin.overview.read'
  | 'admin.users.read'
  | 'admin.users.write'
  | 'admin.settings.read'
  | 'admin.settings.write'
  | 'admin.storage.read'
  | 'admin.storage.write'
  | 'admin.files.read'
  | 'admin.files.write'
  | 'admin.shares.read'
  | 'admin.shares.write'
  | 'admin.tasks.read'
  | 'admin.audit.read'
  | 'admin.system.read';

export type AdminNavItem = {
  label: string;
  path: string;
  icon: LucideIcon;
  permission: AdminPermissionCode;
};

export type AdminNavGroup = {
  label: string;
  items: AdminNavItem[];
};

export const adminNavGroups: AdminNavGroup[] = [
  {
    label: '总览',
    items: [
      { label: '运行总览', path: '/admin/home', icon: Activity, permission: 'admin.overview.read' },
      { label: '系统状态', path: '/admin/system', icon: Shield, permission: 'admin.system.read' },
    ],
  },
  {
    label: '身份与权限',
    items: [{ label: '用户管理', path: '/admin/users', icon: Users, permission: 'admin.users.read' }],
  },
  {
    label: '配置与存储',
    items: [
      { label: '配置中心', path: '/admin/config', icon: Settings, permission: 'admin.settings.read' },
      { label: '存储策略', path: '/admin/storage-policies', icon: Database, permission: 'admin.storage.read' },
    ],
  },
  {
    label: '资源治理',
    items: [
      { label: '文件治理', path: '/admin/files', icon: FileKey, permission: 'admin.files.read' },
      { label: '内容实体', path: '/admin/file-blobs', icon: FolderKey, permission: 'admin.files.read' },
      { label: '分享治理', path: '/admin/shares', icon: Share2, permission: 'admin.shares.read' },
    ],
  },
  {
    label: '任务与审计',
    items: [
      { label: '任务中心', path: '/admin/tasks', icon: ListChecks, permission: 'admin.tasks.read' },
      { label: '审计日志', path: '/admin/audits', icon: Shield, permission: 'admin.audit.read' },
    ],
  },
];
