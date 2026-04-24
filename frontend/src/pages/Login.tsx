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
    <div className="min-h-screen px-4 pb-6 pt-[88px] lg:px-6">
      <Topbar meta={siteConfig.siteName} />
      <BackgroundEffects />

      <main className="mx-auto grid min-h-[calc(100vh-112px)] max-w-[1280px] items-center gap-6 lg:grid-cols-[1.15fr_0.85fr]">
        <section className="surface-shell hidden min-h-[620px] overflow-hidden lg:flex lg:flex-col lg:justify-between lg:p-10">
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.24em] text-slate-400">MY SITE CLOUD</p>
            <h1 className="mt-4 max-w-[10ch] text-5xl font-semibold leading-[1.04] text-slate-950 dark:text-white">
              更完整的文件空间，而不是只够用的页面。
            </h1>
            <p className="mt-5 max-w-xl text-base text-slate-500 dark:text-slate-400">
              {siteConfig.siteDescription}
            </p>
          </div>
          <div className="grid gap-3 text-sm text-slate-500 dark:text-slate-400">
            <div className="surface-muted rounded-3xl px-5 py-4">
              保留你自己的 API 和权限体系，只升级体验层。
            </div>
            <div className="surface-muted rounded-3xl px-5 py-4">
              登录、文件、分享、任务页面统一到一套更成熟的视觉语言。
            </div>
          </div>
        </section>

        <section className="surface-shell login-surface mx-auto w-full max-w-[480px] p-7 sm:p-9">
          <div className="relative z-10">
            <header className="mb-8">
              <BrandMark title={siteConfig.siteName} subtitle="Personal Cloud" size={52} className="mb-6" />
              <h2 className="text-[32px] font-semibold tracking-tight text-slate-950 dark:text-white">
                {siteConfig.passwordLoginEnabled ? '欢迎回来' : '登录暂未开放'}
              </h2>
              <p className="mt-3 text-sm leading-6 text-slate-500 dark:text-slate-400">
                {siteConfig.passwordLoginEnabled
                  ? '继续使用你现有的账号进入文件空间、分享页与快传页面。'
                  : '当前站点暂未开放密码登录，请联系管理员获取可用的登录方式。'}
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
                  <label htmlFor="login-username" className="ml-1 text-[13px] font-medium text-[#31455F] dark:text-[#A1A1A1] font-geist">
                    用户名
                  </label>
                  <input
                    id="login-username"
                    type="text"
                    placeholder="请输入用户名"
                    className="input-field"
                    value={form.username}
                    onChange={(event) => setForm((current) => ({ ...current, username: event.target.value }))}
                  />
                </div>

                <div className="space-y-2">
                  <label htmlFor="login-password" className="ml-1 text-[13px] font-medium text-[#31455F] dark:text-[#A1A1A1] font-geist">
                    密码
                  </label>
                  <input
                    id="login-password"
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
                  type="submit"
                  className="btn-primary mt-4 h-[50px] w-full disabled:opacity-70 dark:h-[48px] dark:rounded-[4px]"
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
        </section>
      </main>
    </div>
  );
};

export default Login;
