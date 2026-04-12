import { fetchApi } from './api';
import type { PageResponse } from './files';

export type AdminAuditLog = {
  id: number;
  actorUserId: number | null;
  actorUsername: string | null;
  actorAuthorities: string[] | string | null;
  actionType: string;
  targetType: string;
  targetId: string | null;
  summary: string;
  detailsJson: string | null;
  createdAt: string;
};

export type AdminAuditQuery = {
  actorQuery?: string;
  actionType?: string;
  targetType?: string;
  targetId?: string;
};

export async function getAdminAudits(page = 0, size = 100, query: AdminAuditQuery = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  if (query.actorQuery?.trim()) {
    params.set('actorQuery', query.actorQuery.trim());
  }

  if (query.actionType?.trim()) {
    params.set('actionType', query.actionType.trim());
  }

  if (query.targetType?.trim()) {
    params.set('targetType', query.targetType.trim());
  }

  if (query.targetId?.trim()) {
    params.set('targetId', query.targetId.trim());
  }

  return fetchApi<PageResponse<AdminAuditLog>>(`/admin/audits?${params.toString()}`);
}
