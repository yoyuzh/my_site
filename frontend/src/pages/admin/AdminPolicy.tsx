import React, { useState } from 'react';
import AdminLayout from '../../components/AdminLayout';
import { Database, Plus, Edit2, Trash2, ArrowRight, Power } from 'lucide-react';
import { useAdminPolicies } from '../../api/queries';
import {
  createAdminStoragePolicy,
  updateAdminStoragePolicy,
  updateAdminStoragePolicyStatus,
  type AdminStoragePolicyPayload,
} from '../../api/mutations';
import { formatBytes, formatDateTime } from '../../lib/format';
import type { AdminStoragePolicy, StoragePolicyCapabilities } from '../../api/types';

const DEFAULT_CAPABILITIES: StoragePolicyCapabilities = {
  directUpload: false,
  multipartUpload: false,
  signedDownloadUrl: false,
  serverProxyDownload: true,
  thumbnailNative: false,
  friendlyDownloadName: true,
  requiresCors: false,
  supportsInternalEndpoint: false,
  maxObjectSize: 1024 * 1024 * 1024,
};

function normalizeText(value: string | null | undefined) {
  const normalized = value?.trim();
  return normalized ? normalized : null;
}

function formatStoragePolicyType(type: string) {
  const labels: Record<string, string> = {
    LOCAL: '本地存储',
    S3_COMPATIBLE: 'S3 兼容存储',
    OSS_SDK: 'OSS SDK',
    WEBDAV: 'WebDAV',
  };
  return labels[type] ?? type;
}

function formatCredentialMode(mode: string) {
  const labels: Record<string, string> = {
    NONE: '无需凭证',
    DOGECLOUD_TEMP: '多吉云临时凭证',
    STATIC: '固定凭证',
  };
  return labels[mode] ?? mode;
}

function parseStoragePolicyTypeInput(value: string | null | undefined) {
  const normalized = value?.trim();
  if (!normalized) {
    return 'LOCAL';
  }
  if (normalized === '本地存储') {
    return 'LOCAL';
  }
  if (normalized === 'S3 兼容存储' || normalized === 'S3兼容存储') {
    return 'S3_COMPATIBLE';
  }
  return normalized.toUpperCase();
}

function buildPayload(policy: Partial<AdminStoragePolicy>): AdminStoragePolicyPayload {
  const maxSizeBytes = policy.maxSizeBytes && policy.maxSizeBytes > 0
    ? policy.maxSizeBytes
    : DEFAULT_CAPABILITIES.maxObjectSize;
  const capabilities = policy.capabilities ?? {
    ...DEFAULT_CAPABILITIES,
    maxObjectSize: maxSizeBytes,
  };

  return {
    name: policy.name?.trim() || '未命名策略',
    type: policy.type || 'LOCAL',
    bucketName: normalizeText(policy.bucketName),
    endpoint: normalizeText(policy.endpoint),
    region: normalizeText(policy.region),
    privateBucket: policy.privateBucket ?? true,
    prefix: normalizeText(policy.prefix),
    credentialMode: policy.credentialMode || 'NONE',
    maxSizeBytes,
    capabilities: {
      ...capabilities,
      maxObjectSize: capabilities.maxObjectSize > 0 ? capabilities.maxObjectSize : maxSizeBytes,
    },
    enabled: policy.enabled ?? true,
  };
}

const AdminPolicy: React.FC = () => {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const { data, isLoading, isError, refetch } = useAdminPolicies({ page, page_size: pageSize });

  async function handleCreate() {
    const name = window.prompt('策略名称');
    if (!name?.trim()) {
      return;
    }
    const type = parseStoragePolicyTypeInput(window.prompt('策略类型：本地存储 / S3 兼容存储', '本地存储'));
    if (type !== 'LOCAL' && type !== 'S3_COMPATIBLE') {
      setStatusMessage('策略类型只能是本地存储或 S3 兼容存储');
      return;
    }
    const maxSizeInput = window.prompt('最大对象大小（字节）', String(DEFAULT_CAPABILITIES.maxObjectSize));
    const maxSizeBytes = Number(maxSizeInput);
    if (!Number.isFinite(maxSizeBytes) || maxSizeBytes <= 0) {
      setStatusMessage('最大对象大小必须是正数');
      return;
    }

    const payload = buildPayload({
      name,
      type,
      maxSizeBytes,
      capabilities: {
        ...DEFAULT_CAPABILITIES,
        maxObjectSize: maxSizeBytes,
      },
      enabled: true,
    });

    try {
      await createAdminStoragePolicy(payload);
      setStatusMessage(`已创建存储策略：${name.trim()}`);
      await refetch();
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '创建存储策略失败');
    }
  }

  async function handleEdit(policy: AdminStoragePolicy) {
    const name = window.prompt('策略名称', policy.name);
    if (!name?.trim()) {
      return;
    }
    const bucketName = window.prompt('Bucket 名称（本地策略可留空）', policy.bucketName ?? '') ?? '';
    const endpoint = window.prompt('Endpoint（本地策略可留空）', policy.endpoint ?? '') ?? '';
    const region = window.prompt('Region（本地策略可留空）', policy.region ?? '') ?? '';
    const prefix = window.prompt('对象前缀（可留空）', policy.prefix ?? '') ?? '';
    const maxSizeInput = window.prompt('最大对象大小（字节）', String(policy.maxSizeBytes));
    const maxSizeBytes = Number(maxSizeInput);
    if (!Number.isFinite(maxSizeBytes) || maxSizeBytes <= 0) {
      setStatusMessage('最大对象大小必须是正数');
      return;
    }

    const payload = buildPayload({
      ...policy,
      name,
      bucketName,
      endpoint,
      region,
      prefix,
      maxSizeBytes,
      capabilities: {
        ...policy.capabilities,
        maxObjectSize: maxSizeBytes,
      },
    });

    try {
      await updateAdminStoragePolicy(policy.id, payload);
      setStatusMessage(`已更新存储策略：${name.trim()}`);
      await refetch();
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '更新存储策略失败');
    }
  }

  async function handleToggle(policy: AdminStoragePolicy) {
    try {
      await updateAdminStoragePolicyStatus(policy.id, !policy.enabled);
      setStatusMessage(`${policy.name} 已${policy.enabled ? '禁用' : '启用'}`);
      await refetch();
    } catch (error) {
      setStatusMessage(error instanceof Error ? error.message : '更新策略状态失败');
    }
  }

  return (
    <AdminLayout title="存储策略">
      <div className="flex justify-between items-center mb-6">
        <button className="btn-primary flex items-center gap-2 px-4 py-2 text-sm h-10" onClick={() => void handleCreate()}>
          <Plus size={16} /> 添加存储策略
        </button>
      </div>

      {statusMessage && (
        <div className="mb-4 rounded-lg border border-[#D9E3F2] dark:border-[#222233] bg-card-light dark:bg-[#111117] px-4 py-3 text-sm text-text-secondary-light dark:text-text-secondary-dark">
          {statusMessage}
        </div>
      )}

      {isLoading ? (
        <div className="p-8 text-center text-text-muted-light">加载中...</div>
      ) : isError ? (
        <div className="p-8 text-center text-red-500">加载失败</div>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 animate-fade-in-up">
            {(data?.items || []).map((policy: AdminStoragePolicy) => (
              <div key={policy.id} className="card-container flex flex-col h-full">
                <div className="p-6 flex-1">
                  <div className="flex justify-between items-start mb-4">
                    <div className="bg-brand-light/10 text-brand-light p-3 rounded-lg">
                      <Database size={24} />
                    </div>
                    <div className="flex gap-2">
                      <button
                        className="text-text-muted-light hover:text-brand-light transition-colors p-1"
                        title="编辑"
                        onClick={() => void handleEdit(policy)}
                      >
                        <Edit2 size={18} />
                      </button>
                      <button
                        className="text-text-muted-light hover:text-red-500 transition-colors p-1 disabled:opacity-50 disabled:cursor-not-allowed"
                        title="后端暂未提供删除存储策略接口"
                        disabled
                      >
                        <Trash2 size={18} />
                      </button>
                    </div>
                  </div>
                  <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2">{policy.name}</h3>
                  <div className="flex items-center gap-2 mb-4">
                    <span className="bg-black/5 dark:bg-white/5 px-2 py-1 rounded text-xs font-bold text-text-secondary-light dark:text-text-secondary-dark">{formatStoragePolicyType(policy.type)}</span>
                    <span className="text-xs text-text-muted-light dark:text-text-muted-dark">
                      {policy.defaultPolicy ? '默认策略' : policy.enabled ? '已启用' : '已禁用'}
                    </span>
                  </div>
                  <div className="space-y-2 text-sm text-text-secondary-light dark:text-text-secondary-dark font-geist">
                    <p className="break-all admin-muted-panel p-2 rounded">
                      {policy.endpoint || policy.bucketName || policy.prefix || '本地默认存储'}
                    </p>
                    <p>最大对象：{formatBytes(policy.maxSizeBytes)}</p>
                    <p>凭证模式：{formatCredentialMode(policy.credentialMode)}</p>
                    <p>更新时间：{formatDateTime(policy.updatedAt)}</p>
                  </div>
                </div>
                <div className="p-4 border-t border-[#D9E3F2] dark:border-[#222233] bg-card-light dark:bg-[#1A1A24]/50 flex justify-between items-center gap-2">
                   <button
                     className="text-sm font-semibold text-brand-light dark:text-brand-dark flex items-center gap-2 group"
                     onClick={() => void handleEdit(policy)}
                   >
                     修改策略向导
                     <ArrowRight size={16} className="transform group-hover:translate-x-1 transition-transform" />
                   </button>
                   <button
                     className={`text-xs font-semibold px-3 py-1 rounded-full transition-colors ${policy.enabled ? 'bg-red-500/10 text-red-500 hover:bg-red-500/20' : 'bg-emerald-500/10 text-emerald-600 hover:bg-emerald-500/20'}`}
                     onClick={() => void handleToggle(policy)}
                   >
                     <Power size={14} className="inline mr-1" />
                     {policy.enabled ? '禁用' : '启用'}
                   </button>
                </div>
              </div>
            ))}
          </div>

          {/* Pagination */}
          <div className="p-4 mt-6 border-t border-[#D9E3F2] dark:border-[#222233] flex flex-col sm:flex-row justify-between items-center text-sm text-text-secondary-light dark:text-text-secondary-dark gap-4">
            <div className="flex items-center gap-4">
              <span>共 {data?.pagination?.total_items || 0} 条记录</span>
              <select 
                className="bg-transparent border-none text-brand-light font-medium cursor-pointer outline-none hidden sm:block"
                value={pageSize}
                onChange={(e) => { setPageSize(Number(e.target.value)); setPage(1); }}
              >
                <option value={10}>10 条/页</option>
                <option value={20}>20 条/页</option>
                <option value={50}>50 条/页</option>
              </select>
            </div>
            <div className="flex gap-2">
              <button 
                className="px-3 py-1 border border-[#D9E3F2] dark:border-[#222233] rounded hover:bg-black/5 dark:hover:bg-white/5 transition-colors disabled:opacity-50 disabled:cursor-not-allowed" 
                disabled={page <= 1}
                onClick={() => setPage(page - 1)}
              >上一页</button>
              <button className="px-3 py-1 border border-[#D9E3F2] dark:border-[#222233] rounded hover:bg-black/5 dark:hover:bg-white/5 transition-colors bg-brand-light text-white border-brand-light">{page}</button>
              <button 
                className="px-3 py-1 border border-[#D9E3F2] dark:border-[#222233] rounded hover:bg-black/5 dark:hover:bg-white/5 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                disabled={!data?.pagination?.total_pages || page >= data.pagination.total_pages}
                onClick={() => setPage(page + 1)}
              >下一页</button>
            </div>
          </div>
        </>
      )}
    </AdminLayout>
  );
};

export default AdminPolicy;
