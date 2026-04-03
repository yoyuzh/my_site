export const RECYCLE_BIN_ROUTE = '/recycle-bin';
export const RECYCLE_BIN_RETENTION_DAYS = 10;

export interface FilesSidebarFooterEntry {
  label: string;
  path: string;
}

export function getFilesSidebarFooterEntries(): FilesSidebarFooterEntry[] {
  return [
    {
      label: '回收站',
      path: RECYCLE_BIN_ROUTE,
    },
  ];
}

export function formatRecycleBinExpiresLabel(expiresAt: string, now = new Date()) {
  const expiresAtDate = new Date(expiresAt);
  const diffMs = expiresAtDate.getTime() - now.getTime();
  if (Number.isNaN(expiresAtDate.getTime()) || diffMs <= 0) {
    return '今天清理';
  }

  const remainingDays = Math.max(1, Math.ceil(diffMs / (24 * 60 * 60 * 1000)));
  return `${remainingDays} 天后清理`;
}
