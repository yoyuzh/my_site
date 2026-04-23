import React, { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import Topbar from '../components/Topbar';
import BackgroundEffects from '../components/BackgroundEffects';
import BrandMark from '../components/BrandMark';
import { defaultSiteRuntimeConfig, loadSiteRuntimeConfig, type SiteRuntimeConfig } from '../lib/site-config';
import { login } from '../lib/auth';
import { getDefaultSignedInRoute, getSession } from '../lib/session';
import { ApiError } from '../api/client';

const Login: React.FC = () => {
  const [siteConfig, setSiteConfig] = useState<SiteRuntimeConfig>(defaultSiteRuntimeConfig);
  const [form, setForm] = useState({
    username: '',
    password: '',
  });
  const navigate = useNavigate();
  const session = getSession();

  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: (result) => {
      navigate(getDefaultSignedInRoute(result.user.role), { replace: true });
    },
  });

  if (session) {
    return <Navigate to={getDefaultSignedInRoute(session.user.role)} replace />;
  }

  useEffect(() => {
    const controller = new AbortController();

    loadSiteRuntimeConfig(controller.signal)
      .then((config) => {
        setSiteConfig(config);
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return;
        }
        console.error('Failed to load site runtime config', error);
      });

    return () => {
      controller.abort();
    };
  }, []);

  return (
    <div className="min-h-screen flex items-center justify-center pt-[72px] px-6">
      <Topbar meta={siteConfig.siteName} />
      <BackgroundEffects />
      
      <main className="w-full max-w-[460px] animate-fade-in-up">
        <div className="card-container p-11 relative">
          
          <div className="relative z-10">
            <header className="mb-9">
              <BrandMark
                title={siteConfig.siteName}
                subtitle="Personal Cloud"
                size={54}
                className="mb-6"
              />
              <h2 className="text-[34px] font-bold text-text-primary-light dark:text-white dark:mt-4 leading-tight">
                {siteConfig.passwordLoginEnabled ? '欢迎回来' : '登录暂未开放'}
              </h2>
              <p className="text-[15px] text-text-secondary-light dark:text-[#A1A1A1] mt-3 font-geist">
                {siteConfig.passwordLoginEnabled
                  ? siteConfig.siteDescription
                  : '当前站点暂未开放密码登录，请联系管理员获取访问方式。'}
              </p>
            </header>

            {siteConfig.passwordLoginEnabled ? (
              <form
                className="space-y-6"
                onSubmit={(e) => {
                  e.preventDefault();
                  loginMutation.mutate(form);
                }}
              >
                <div className="space-y-2">
                  <label className="text-[13px] font-medium text-[#31455F] dark:text-[#A1A1A1] font-geist ml-1">
                    用户名
                  </label>
                  <input 
                    type="text" 
                    placeholder="请输入用户名" 
                    className="input-field"
                    value={form.username}
                    onChange={(event) => setForm((current) => ({ ...current, username: event.target.value }))}
                  />
                </div>

                <div className="space-y-2">
                  <label className="text-[13px] font-medium text-[#31455F] dark:text-[#A1A1A1] font-geist ml-1">
                    密码
                  </label>
                  <input 
                    type="password" 
                    placeholder="请输入密码" 
                    className="input-field"
                    value={form.password}
                    onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
                  />
                </div>

                {loginMutation.isError ? (
                  <p className="text-[13px] text-red-500">
                    {(loginMutation.error as ApiError).message}
                  </p>
                ) : null}

                <button
                  className="btn-primary w-full mt-4 h-[50px] dark:h-[48px] dark:rounded-[4px] disabled:opacity-70"
                  disabled={loginMutation.isPending}
                >
                  {loginMutation.isPending ? '登录中...' : `登录到 ${siteConfig.siteName}`}
                </button>

                <p className="text-[12px] text-text-muted-light dark:text-text-muted-dark leading-relaxed">
                  {siteConfig.captchaEnabled ? '当前登录流程已启用验证码校验。' : '当前登录流程无需验证码。'}
                </p>
              </form>
            ) : (
              <div className="rounded-2xl border border-[#D6E1F0] dark:border-[#222233] bg-[#F8FBFF] dark:bg-black/40 px-5 py-4 text-[14px] text-text-secondary-light dark:text-text-secondary-dark">
                管理员已关闭密码登录入口。若你需要继续访问，请联系站点管理员确认当前可用的登录方式。
              </div>
            )}

            <footer className="mt-8 pt-6 border-t border-[#E2EAF5] dark:border-[#222233]">
              <div className="flex flex-col gap-3">
                {siteConfig.registrationEnabled ? (
                  <Link to="/register" className="text-[13px] text-[#4C607A] dark:text-brand-dark hover:text-brand-light dark:hover:text-brand-light transition-colors text-center">
                    没有账号？立即注册
                  </Link>
                ) : (
                  <p className="text-[13px] text-text-muted-light dark:text-text-muted-dark text-center">
                    当前站点暂未开放新用户注册
                  </p>
                )}
                <Link to="/dashboard/transfer-send" className="text-[13px] font-medium text-brand-light dark:text-[#A1A1A1] hover:underline text-center">
                  前往快传
                </Link>
              </div>
            </footer>
          </div>
        </div>
      </main>
    </div>
  );
};

export default Login;
