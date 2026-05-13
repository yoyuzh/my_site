import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { CheckCircle2, Copy, Loader2, RefreshCw, Server } from 'lucide-react';
import { getWebDavCredential, getWebDavUrl, issueWebDavCredential } from '../../lib/webdav';

type WebDavAccessPanelProps = {
  username: string;
  onMessage?: (message: { type: 'success' | 'error'; text: string }) => void;
};

const WebDavAccessPanel: React.FC<WebDavAccessPanelProps> = ({ username, onMessage }) => {
  const queryClient = useQueryClient();
  const [issuedPassword, setIssuedPassword] = useState<string | null>(null);
  const [copiedField, setCopiedField] = useState<string | null>(null);

  const credentialQuery = useQuery({
    queryKey: ['webDavCredential'],
    queryFn: getWebDavCredential,
  });

  const credentialMutation = useMutation({
    mutationFn: issueWebDavCredential,
    onSuccess: (credential) => {
      const { plaintextPassword, ...safeCredential } = credential;
      queryClient.setQueryData(['webDavCredential'], safeCredential);
      setIssuedPassword(plaintextPassword ?? null);
      onMessage?.({ type: 'success', text: 'WebDAV 访问密码已生成' });
    },
    onError: (error) => {
      onMessage?.({
        type: 'error',
        text: error instanceof Error ? error.message : '生成 WebDAV 密码失败',
      });
    },
  });

  const credential = credentialQuery.data;
  const webDavUrl = getWebDavUrl(credential?.endpoint ?? '/dav');
  const displayUsername = credential?.username ?? username;

  async function copyValue(value: string, field: string) {
    try {
      await navigator.clipboard.writeText(value);
      setCopiedField(field);
      window.setTimeout(() => setCopiedField(null), 2000);
    } catch {
      onMessage?.({ type: 'error', text: '复制失败，请手动复制' });
    }
  }

  return (
    <section className="rounded-3xl border border-white/50 bg-white/80 p-8 shadow-sm dark:border-white/5 dark:bg-[#161922]/80">
      <div className="mb-6 flex items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-500/10 text-emerald-600">
            <Server size={20} />
          </div>
          <div>
            <h3 className="text-lg font-bold text-slate-900 dark:text-white">WebDAV</h3>
            <p className="text-sm text-slate-500 dark:text-slate-400">使用外部客户端访问你的网盘文件。</p>
          </div>
        </div>
        <span className={clsx(
          'rounded-full px-3 py-1 text-xs font-semibold',
          credential?.enabled
            ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
            : 'bg-slate-500/10 text-slate-500 dark:text-slate-400',
        )}>
          {credentialQuery.isLoading ? '加载中' : credential?.enabled ? '已启用' : '未生成'}
        </span>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-2">
          <label className="text-sm font-medium text-slate-700 dark:text-slate-300">服务器地址</label>
          <div className="flex rounded-2xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
            <input
              value={webDavUrl}
              readOnly
              className="min-w-0 flex-1 rounded-l-2xl bg-transparent px-4 py-3 text-sm text-slate-900 outline-none dark:text-white"
            />
            <button
              type="button"
              onClick={() => copyValue(webDavUrl, 'url')}
              className="flex items-center gap-2 rounded-r-2xl px-4 py-3 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800"
            >
              {copiedField === 'url' ? <CheckCircle2 size={16} /> : <Copy size={16} />}
              复制
            </button>
          </div>
        </div>

        <div className="space-y-2">
          <label className="text-sm font-medium text-slate-700 dark:text-slate-300">用户名</label>
          <div className="flex rounded-2xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
            <input
              value={displayUsername}
              readOnly
              className="min-w-0 flex-1 rounded-l-2xl bg-transparent px-4 py-3 text-sm text-slate-900 outline-none dark:text-white"
            />
            <button
              type="button"
              onClick={() => copyValue(displayUsername, 'username')}
              className="flex items-center gap-2 rounded-r-2xl px-4 py-3 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800"
            >
              {copiedField === 'username' ? <CheckCircle2 size={16} /> : <Copy size={16} />}
              复制
            </button>
          </div>
        </div>
      </div>

      {issuedPassword && (
        <div className="mt-5 rounded-2xl border border-amber-500/20 bg-amber-500/10 p-4">
          <div className="mb-2 flex items-center justify-between gap-3">
            <p className="text-sm font-semibold text-amber-700 dark:text-amber-300">新密码仅显示一次</p>
            <button
              type="button"
              onClick={() => copyValue(issuedPassword, 'password')}
              className="flex items-center gap-2 rounded-xl px-3 py-2 text-sm font-semibold text-amber-700 transition-colors hover:bg-amber-500/10 dark:text-amber-300"
            >
              {copiedField === 'password' ? <CheckCircle2 size={16} /> : <Copy size={16} />}
              复制密码
            </button>
          </div>
          <code className="block overflow-x-auto rounded-xl bg-white/70 px-3 py-2 text-sm text-slate-900 dark:bg-slate-950/60 dark:text-white">
            {issuedPassword}
          </code>
        </div>
      )}

      <div className="mt-6 flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <p className="text-sm text-slate-500 dark:text-slate-400">
          {credential?.updatedAt
            ? `最近更新：${new Date(credential.updatedAt).toLocaleString()}`
            : '生成后可在支持 WebDAV 的客户端中使用。'}
        </p>
        <button
          type="button"
          onClick={() => credentialMutation.mutate()}
          disabled={credentialMutation.isPending}
          className="flex items-center justify-center gap-2 rounded-2xl bg-emerald-600 px-6 py-3 text-sm font-semibold text-white transition-colors hover:bg-emerald-700 disabled:opacity-50"
        >
          {credentialMutation.isPending ? <Loader2 size={18} className="animate-spin" /> : <RefreshCw size={18} />}
          {credential?.enabled ? '重置密码' : '生成密码'}
        </button>
      </div>
    </section>
  );
};

export default WebDavAccessPanel;
