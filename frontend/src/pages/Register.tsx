import React, { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import Topbar from '../components/Topbar';
import BackgroundEffects from '../components/BackgroundEffects';
import BrandMark from '../components/BrandMark';
import { register } from '../lib/auth';
import { ApiError } from '../api/client';
import { useStoredSessionValidation } from '../hooks/useStoredSessionValidation';
import { getDefaultSignedInRoute } from '../lib/session';

const Register: React.FC = () => {
  const [form, setForm] = useState({
    username: '',
    email: '',
    phoneNumber: '',
    inviteCode: '',
    password: '',
    confirmPassword: '',
  });
  const navigate = useNavigate();
  const { status: sessionStatus, session } = useStoredSessionValidation();
  const registerMutation = useMutation({
    mutationFn: register,
    onSuccess: (result) => {
      navigate(getDefaultSignedInRoute(result.user.role), { replace: true });
    },
  });

  if (sessionStatus === 'authenticated' && session) {
    return <Navigate to={getDefaultSignedInRoute(session.user.role)} replace />;
  }

  return (
    <div className="min-h-screen flex items-center justify-center pt-[72px] px-6 py-12">
      <Topbar meta="注册入口" />
      <BackgroundEffects />
      
      <main className="w-full max-w-[512px] animate-fade-in-up">
        <div className="card-container p-11 relative">
          <header className="mb-9">
            <BrandMark
              title="YOYUZH.XYZ"
              subtitle="Create Workspace"
              size={54}
              className="mb-6"
            />
            <h2 className="text-[32px] font-bold text-text-primary-light dark:text-white mt-4 leading-tight">
              注册账号
            </h2>
            <p className="text-[15px] text-text-secondary-light dark:text-text-secondary-dark mt-3 font-geist">
              填写基础资料后即可开始上传、快传与分享。
            </p>
          </header>

          <form
            className="space-y-5"
            onSubmit={(e) => {
              e.preventDefault();
              registerMutation.mutate(form);
            }}
          >
            <div className="space-y-2">
              <label className="text-[13px] font-medium text-[#31455F] dark:text-[#A1A1A1] font-geist ml-1">
                用户名
              </label>
              <input
                type="text"
                placeholder="请输入用户名"
                className="input-field h-[44px]"
                value={form.username}
                onChange={(event) => setForm((current) => ({ ...current, username: event.target.value }))}
              />
            </div>

            <div className="space-y-2">
              <label className="text-[13px] font-medium text-[#31455F] dark:text-[#A1A1A1] font-geist ml-1">
                邮箱
              </label>
              <input
                type="email"
                placeholder="请输入邮箱地址"
                className="input-field h-[44px]"
                value={form.email}
                onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
              />
            </div>

            <div className="space-y-2">
              <label className="text-[13px] font-medium text-[#31455F] dark:text-[#A1A1A1] font-geist ml-1">
                手机号
              </label>
              <input
                type="tel"
                placeholder="请输入手机号"
                className="input-field h-[44px]"
                value={form.phoneNumber}
                onChange={(event) => setForm((current) => ({ ...current, phoneNumber: event.target.value }))}
              />
            </div>

            <div className="space-y-2">
              <label className="text-[13px] font-medium text-[#31455F] dark:text-[#A1A1A1] font-geist ml-1">
                邀请码
              </label>
              <input
                type="text"
                placeholder="请输入邀请码"
                className="input-field h-[44px]"
                value={form.inviteCode}
                onChange={(event) => setForm((current) => ({ ...current, inviteCode: event.target.value }))}
              />
            </div>

            <div className="space-y-2">
              <label className="text-[13px] font-medium text-[#31455F] dark:text-[#A1A1A1] font-geist ml-1">
                密码
              </label>
              <input
                type="password"
                placeholder="请输入密码"
                className="input-field h-[44px]"
                value={form.password}
                onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
              />
            </div>

            <div className="space-y-2">
              <label className="text-[13px] font-medium text-[#31455F] dark:text-[#A1A1A1] font-geist ml-1">
                确认密码
              </label>
              <input
                type="password"
                placeholder="请再次输入密码"
                className="input-field h-[44px]"
                value={form.confirmPassword}
                onChange={(event) => setForm((current) => ({ ...current, confirmPassword: event.target.value }))}
              />
            </div>

            {registerMutation.isError ? (
              <p className="text-[13px] text-red-500">
                {(registerMutation.error as ApiError).message}
              </p>
            ) : null}

            <button className="btn-primary w-full mt-4 h-[50px]" disabled={registerMutation.isPending || sessionStatus === 'checking'}>
              {sessionStatus === 'checking' ? '正在检查登录状态...' : registerMutation.isPending ? '注册中...' : '注册'}
            </button>
          </form>

          <footer className="mt-8 space-y-4">
            <Link to="/login" className="block text-[13px] text-[#4C607A] dark:text-text-secondary-dark hover:text-brand-light transition-colors">
              已有账号？返回登录
            </Link>
            <p className="text-[12px] text-text-muted-light dark:text-text-muted-dark leading-relaxed">
              邀请码用于控制注册节奏，不影响后续正常使用。
            </p>
          </footer>
        </div>
      </main>
    </div>
  );
};

export default Register;
