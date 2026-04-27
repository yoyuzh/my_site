import React, { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import Topbar from '../components/Topbar';
import BackgroundEffects from '../components/BackgroundEffects';
import { formatBytes, formatDateTime } from '../lib/format';
import { buildShareDownloadUrl, getShareDetails, importShare, verifySharePassword, saveShare } from '../lib/shares';
import { ApiError } from '../api/client';
import { getSession } from '../lib/session';

const FileShare: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [password, setPassword] = useState('');
  const [verifiedShare, setVerifiedShare] = useState<Awaited<ReturnType<typeof getShareDetails>> | null>(null);
  const session = getSession();

  const shareQuery = useQuery({
    queryKey: ['publicShare', id],
    queryFn: () => getShareDetails(id ?? ''),
    enabled: !!id,
  });

  const verifyMutation = useMutation({
    mutationFn: (value: string) => verifySharePassword(id ?? '', { password: value }),
    onSuccess: (result) => {
      setVerifiedShare(result);
    },
  });

  const importMutation = useMutation({
    mutationFn: () => importShare(id ?? '', '/', password || undefined),
  });

  const saveMutation = useMutation({
    mutationFn: () => saveShare(id ?? '', password || undefined),
    onSuccess: () => {
      alert('已成功保存到与我共享');
    },
  });

  const share = verifiedShare ?? shareQuery.data;
  const canDownload = !!share && share.status === 'ACTIVE' && (!share.passwordRequired || share.passwordVerified) && share.allowDownload;
  const canSave = !!share && !!session && share.status === 'ACTIVE';

  const getStatusText = (status?: string) => {
    switch (status) {
      case 'EXPIRED': return ' (已过期)';
      case 'CONSUMED': return ' (已失效)';
      case 'REMOVED': return ' (已移除)';
      default: return '';
    }
  };

  return (
    <div className="min-h-screen pt-[72px] px-6 py-12">
      <Topbar meta="公开分享" />
      <BackgroundEffects />

      <main className="max-w-[1200px] mx-auto animate-fade-in-up">
        <header className="mb-12 ml-4">
          <h2 className="text-[34px] font-bold text-text-primary-light dark:text-white leading-tight">
            文件分享
          </h2>
          <p className="text-[15px] text-text-secondary-light dark:text-text-secondary-dark mt-3 font-geist">
            快速查看文件信息，并按权限完成验证、下载或导入。
          </p>
        </header>

        <div className="flex flex-col lg:flex-row gap-8 items-start">
          {/* Left Card: File Info */}
          <div className="card-container flex-1 p-10 min-h-[344px] flex flex-col justify-between">
            <div className="space-y-6">
              <div>
                <p className="text-[12px] font-medium text-text-muted-light dark:text-text-muted-dark font-geist">
                  分享者
                </p>
                <p className="text-[13px] font-semibold text-[#334861] dark:text-white mt-1 font-funnel">
                  {share?.ownerUsername ?? '加载中'}
                </p>
              </div>
              
              <h3 className="text-[28px] font-bold text-text-primary-light dark:text-white leading-snug">
                {share?.file?.filename ?? '正在加载分享文件'}{getStatusText(share?.status)}
              </h3>
              
              <p className="text-[14px] text-text-secondary-light dark:text-text-secondary-dark leading-relaxed font-geist">
                {share?.passwordRequired ? '该分享受密码保护，验证后即可下载或导入。' : '该分享可直接查看或导入。'}
              </p>
            </div>

            <div className="mt-8 pt-8 border-t border-[#E3EBF5] dark:border-[#222233] grid grid-cols-2 md:grid-cols-4 gap-6">
              <div>
                <p className="text-[12px] font-medium text-text-muted-light dark:text-text-muted-dark font-geist">文件大小</p>
                <p className="text-[15px] font-semibold text-text-primary-light dark:text-white mt-1 font-funnel">
                  {share?.file ? formatBytes(share.file.size) : '-'}
                </p>
              </div>
              <div>
                <p className="text-[12px] font-medium text-text-muted-light dark:text-text-muted-dark font-geist">创建时间</p>
                <p className="text-[15px] font-semibold text-text-primary-light dark:text-white mt-1 font-funnel">
                  {share ? formatDateTime(share.createdAt) : '-'}
                </p>
              </div>
              <div>
                <p className="text-[12px] font-medium text-text-muted-light dark:text-text-muted-dark font-geist">过期时间</p>
                <p className="text-[15px] font-semibold text-text-primary-light dark:text-white mt-1 font-funnel">
                  {share?.expiresAt ? formatDateTime(share.expiresAt) : '永久有效'}
                </p>
              </div>
              <div>
                <p className="text-[12px] font-medium text-text-muted-light dark:text-text-muted-dark font-geist">下载次数</p>
                <p className="text-[15px] font-semibold text-brand-light dark:text-brand-dark mt-1 font-funnel">
                  {share ? `${share.downloadCount} / ${share.maxDownloads ?? '不限'}` : '-'}
                </p>
              </div>
            </div>
          </div>

          {/* Right Card: Verification */}
          <div className="card-container w-full lg:w-[320px] p-8 flex flex-col min-h-[344px]">
            <h3 className="text-[22px] font-bold text-text-primary-light dark:text-white font-inter">
              访问验证
            </h3>
            <p className="text-[14px] text-text-secondary-light dark:text-text-secondary-dark mt-4 font-geist leading-relaxed">
              {share?.passwordRequired ? '此链接受密码保护。验证后可根据权限继续操作。' : '当前分享无需密码，可直接下载或导入。'}
            </p>

            <form
              className="mt-auto pt-8 space-y-4"
              onSubmit={(e) => {
                e.preventDefault();
                if (share?.passwordRequired) {
                  verifyMutation.mutate(password);
                }
              }}
            >
              <div className="space-y-2">
                <label className="text-[13px] font-medium text-[#334861] dark:text-[#A1A1A1] font-geist ml-1">
                  访问密码
                </label>
                <input
                  type="password"
                  placeholder="请输入访问密码"
                  className="input-field"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  disabled={!share?.passwordRequired}
                />
              </div>
              {verifyMutation.isError ? (
                <p className="text-[13px] text-red-500">{(verifyMutation.error as ApiError).message}</p>
              ) : null}
              <button className="btn-primary w-full h-[46px]">
                {share?.passwordRequired ? '验证' : '无需验证'}
              </button>
            </form>
          </div>
        </div>

        <div className="mt-12 flex flex-wrap gap-4 ml-4">
          <a
            href={canDownload && id ? buildShareDownloadUrl(id, password || undefined) : '#'}
            className={`btn-primary w-[212px] text-center ${canDownload ? '' : 'pointer-events-none opacity-50'}`}
          >
            下载文件
          </a>
          <button
            className="bg-white dark:bg-transparent border border-[#BFD2F7] dark:border-[#222233] text-brand-light dark:text-white font-semibold py-3 px-6 rounded-lg transition-all duration-300 hover:bg-brand-light/5 w-[212px] disabled:opacity-50"
            onClick={() => importMutation.mutate()}
            disabled={!share || share.status !== 'ACTIVE' || !share.allowImport || importMutation.isPending}
          >
            {importMutation.isPending ? '导入中...' : '导入到网盘'}
          </button>
          {session && (
            <button
              className="bg-white dark:bg-transparent border border-[#BFD2F7] dark:border-[#222233] text-brand-light dark:text-white font-semibold py-3 px-6 rounded-lg transition-all duration-300 hover:bg-brand-light/5 w-[212px] disabled:opacity-50"
              onClick={() => saveMutation.mutate()}
              disabled={!canSave || saveMutation.isPending || saveMutation.isSuccess}
            >
              {saveMutation.isPending ? '保存中...' : saveMutation.isSuccess ? '已保存' : '保存到与我共享'}
            </button>
          )}
        </div>

        <p className="mt-8 ml-4 text-[12px] text-text-muted-light dark:text-text-muted-dark font-geist">
          {shareQuery.isError ? '分享信息加载失败，请确认链接有效。' : '当前页面数据来自后端分享接口。'}
        </p>
      </main>
    </div>
  );
};

export default FileShare;
