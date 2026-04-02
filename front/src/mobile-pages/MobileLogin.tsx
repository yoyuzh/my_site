import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { motion, AnimatePresence } from 'motion/react';
import { LogIn, User, Lock, UserPlus, Mail, ArrowLeft, Phone, Send } from 'lucide-react';

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/src/components/ui/card';
import { Button } from '@/src/components/ui/button';
import { Input } from '@/src/components/ui/input';
import { apiRequest, ApiError } from '@/src/lib/api';
import { getPostLoginRedirectPath } from '@/src/lib/file-share';
import { cn } from '@/src/lib/utils';
import { createSession, markPostLoginPending, saveStoredSession } from '@/src/lib/session';
import type { AuthResponse } from '@/src/lib/types';
import { buildRegisterPayload, validateRegisterForm } from '@/src/pages/login-state';

const DEV_LOGIN_ENABLED = import.meta.env.DEV || import.meta.env.VITE_ENABLE_DEV_LOGIN === 'true';

export default function MobileLogin() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [isLogin, setIsLogin] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Form states
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  
  const [registerUsername, setRegisterUsername] = useState('');
  const [registerEmail, setRegisterEmail] = useState('');
  const [registerPhoneNumber, setRegisterPhoneNumber] = useState('');
  const [registerPassword, setRegisterPassword] = useState('');
  const [registerConfirmPassword, setRegisterConfirmPassword] = useState('');
  const [registerInviteCode, setRegisterInviteCode] = useState('');

  function switchMode(nextIsLogin: boolean) {
    setIsLogin(nextIsLogin);
    setError('');
    setLoading(false);
  }

  async function handleLoginSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true); setError('');
    try {
      let auth: AuthResponse;
      try {
        auth = await apiRequest<AuthResponse>('/auth/login', { method: 'POST', body: { username, password } });
      } catch (requestError) {
        if (DEV_LOGIN_ENABLED && username.trim() && requestError instanceof ApiError && requestError.status === 401) {
          auth = await apiRequest<AuthResponse>(`/auth/dev-login?username=${encodeURIComponent(username.trim())}`, { method: 'POST' });
        } else {
          throw requestError;
        }
      }
      saveStoredSession(createSession(auth));
      markPostLoginPending();
      setLoading(false);
      navigate(getPostLoginRedirectPath(searchParams.get('next')));
    } catch (requestError) {
      setLoading(false);
      setError(requestError instanceof Error ? requestError.message : '登录失败，请重试');
    }
  }

  async function handleRegisterSubmit(e: React.FormEvent) {
    e.preventDefault();
    const validationMessage = validateRegisterForm({
      username: registerUsername, email: registerEmail, phoneNumber: registerPhoneNumber,
      password: registerPassword, confirmPassword: registerConfirmPassword, inviteCode: registerInviteCode,
    });
    if (validationMessage) { setError(validationMessage); return; }

    setLoading(true); setError('');
    try {
      const auth = await apiRequest<AuthResponse>('/auth/register', {
        method: 'POST',
        body: buildRegisterPayload({
          username: registerUsername, email: registerEmail, phoneNumber: registerPhoneNumber,
          password: registerPassword, confirmPassword: registerConfirmPassword, inviteCode: registerInviteCode,
        }),
      });
      saveStoredSession(createSession(auth));
      markPostLoginPending();
      setLoading(false);
      navigate(getPostLoginRedirectPath(searchParams.get('next')));
    } catch (requestError) {
      setLoading(false);
      setError(requestError instanceof Error ? requestError.message : '注册失败，请重试');
    }
  }

  return (
    <div className="min-h-[100dvh] flex flex-col bg-[#07101D] relative overflow-hidden">
      {/* Background Blobs - customized for mobile sizes */}
      <div className="absolute top-10 left-[-20%] w-[80%] h-[40%] bg-[#336EFF] rounded-full mix-blend-screen filter blur-[100px] opacity-25 animate-pulse" />
      <div className="absolute bottom-10 right-[-10%] w-[70%] h-[40%] bg-purple-600 rounded-full mix-blend-screen filter blur-[100px] opacity-20" />

      {/* Top Graphic Intro area */}
      <div className="pt-16 pb-6 px-6 relative z-10 flex flex-col items-center justify-center shrink-0">
        <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-[#336EFF] to-blue-400 flex items-center justify-center shadow-xl shadow-[#336EFF]/20 mb-4">
          <span className="text-white font-bold text-3xl leading-none">Y</span>
        </div>
        <h1 className="text-3xl font-bold tracking-tight text-white mb-2">优立云盘</h1>
        <p className="text-sm text-slate-400 text-center max-w-[280px]">
          集中管理网盘文件，跨设备快传，随时体验轻游戏。
        </p>
      </div>

      <div className="flex-1 w-full px-4 pb-8 relative z-10">
        <Card className="border-white/10 backdrop-blur-2xl bg-white/5 shadow-2xl overflow-hidden w-full max-w-sm mx-auto">
          <AnimatePresence mode="wait">
            {isLogin ? (
              <motion.div
                key="login-form"
                initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.3 }}
              >
                <CardHeader className="space-y-1 pb-6 pt-6 px-6">
                  <CardTitle className="text-xl font-bold text-white flex items-center gap-2">
                    <LogIn className="w-5 h-5 text-[#336EFF]" /> 登录
                  </CardTitle>
                  <CardDescription className="text-xs text-slate-400">登入您的账号以继续</CardDescription>
                </CardHeader>
                <CardContent className="px-6 pb-6">
                  <form onSubmit={handleLoginSubmit} className="space-y-5">
                    <div className="space-y-4">
                      <div className="relative">
                        <User className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                        <Input
                          type="text" placeholder="账号 / 用户名 / 学号" required value={username}
                          onChange={(e) => setUsername(e.target.value)}
                          className="pl-10 h-12 bg-black/20 border-white/10 focus-visible:ring-[#336EFF] text-base"
                        />
                      </div>
                      <div className="relative">
                        <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                        <Input
                          type="password" placeholder="••••••••" required value={password}
                          onChange={(e) => setPassword(e.target.value)}
                          className="pl-10 h-12 bg-black/20 border-white/10 focus-visible:ring-[#336EFF] text-base"
                        />
                      </div>
                    </div>

                    {error && (
                      <div className="p-3 rounded-lg bg-red-500/10 border border-red-500/20 text-red-400 text-xs">
                        {error}
                      </div>
                    )}

                    <div className="space-y-3 pt-2">
                      <Button type="submit" className="w-full h-12 text-base font-semibold rounded-xl" disabled={loading}>
                        {loading ? <span className="w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin" /> : '进入系统'}
                      </Button>
                      <Button type="button" variant="outline" onClick={() => navigate('/transfer')} className="w-full h-12 border-white/10 bg-white/5 text-slate-300 hover:bg-white/10 rounded-xl">
                        <Send className="mr-2 h-4 w-4" /> 直接进入快传
                      </Button>
                      <div className="text-center pt-2">
                        <button type="button" onClick={() => switchMode(false)} className="text-sm text-slate-400 hover:text-white transition-colors p-2">
                          还没有账号？立即注册
                        </button>
                      </div>
                    </div>
                  </form>
                </CardContent>
              </motion.div>
            ) : (
              <motion.div
                key="register-form"
                initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.3 }}
              >
                <CardHeader className="space-y-1 pb-4 pt-6 px-6">
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-xl font-bold text-white flex items-center gap-2">
                      <UserPlus className="w-5 h-5 text-[#336EFF]" /> 注册账号
                    </CardTitle>
                    <button type="button" onClick={() => switchMode(true)} className="p-1.5 rounded-full bg-white/5 text-slate-300">
                      <ArrowLeft className="w-4 h-4" />
                    </button>
                  </div>
                </CardHeader>
                <CardContent className="px-6 pb-6 h-[50vh] overflow-y-auto custom-scrollbar">
                  <form onSubmit={handleRegisterSubmit} className="space-y-4">
                    <div className="space-y-3">
                      <div className="relative">
                        <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                        <Input type="text" placeholder="设置您的用户名" required minLength={3} maxLength={64}
                          value={registerUsername} onChange={(e) => setRegisterUsername(e.target.value)}
                          className="pl-9 h-11 bg-black/20 border-white/10 text-sm" />
                      </div>
                      <div className="relative">
                        <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                        <Input type="email" placeholder="邮箱 (your@email.com)" required
                          value={registerEmail} onChange={(e) => setRegisterEmail(e.target.value)}
                          className="pl-9 h-11 bg-black/20 border-white/10 text-sm" />
                      </div>
                      <div className="relative">
                        <Phone className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                        <Input type="tel" placeholder="11位手机号" required
                          value={registerPhoneNumber} onChange={(e) => setRegisterPhoneNumber(e.target.value)}
                          className="pl-9 h-11 bg-black/20 border-white/10 text-sm" />
                      </div>
                      <div className="relative">
                        <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                        <Input type="password" placeholder="设置密码 (至少 8 位)" required minLength={8} maxLength={64}
                          value={registerPassword} onChange={(e) => setRegisterPassword(e.target.value)}
                          className="pl-9 h-11 bg-black/20 border-white/10 text-sm" />
                      </div>
                      <div className="relative">
                        <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                        <Input type="password" placeholder="验证密码" required minLength={8} maxLength={64}
                          value={registerConfirmPassword} onChange={(e) => setRegisterConfirmPassword(e.target.value)}
                          className="pl-9 h-11 bg-black/20 border-white/10 text-sm" />
                      </div>
                      <div className="relative">
                        <UserPlus className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                        <Input type="text" placeholder="邀请码" required
                          value={registerInviteCode} onChange={(e) => setRegisterInviteCode(e.target.value)}
                          className="pl-9 h-11 bg-black/20 border-white/10 text-sm" />
                      </div>
                    </div>

                    {error && (
                      <div className="p-3 rounded-lg bg-red-500/10 border border-red-500/20 text-red-400 text-xs">
                        {error}
                      </div>
                    )}

                    <div className="space-y-4 pt-2 pb-4">
                      <Button type="submit" className="w-full h-11 text-base rounded-xl" disabled={loading}>
                        {loading ? '注册中...' : '创建账号'}
                      </Button>
                      <div className="text-center">
                        <button type="button" onClick={() => switchMode(true)} className="text-xs text-slate-400 p-2">
                          已有账号？返回登录
                        </button>
                      </div>
                    </div>
                  </form>
                </CardContent>
              </motion.div>
            )}
          </AnimatePresence>
        </Card>
      </div>
    </div>
  );
}
