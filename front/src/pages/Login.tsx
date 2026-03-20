import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { motion, AnimatePresence } from 'motion/react';
import { LogIn, User, Lock, UserPlus, Mail, ArrowLeft, Phone } from 'lucide-react';

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/src/components/ui/card';
import { Button } from '@/src/components/ui/button';
import { Input } from '@/src/components/ui/input';
import { apiRequest, ApiError } from '@/src/lib/api';
import { getPostLoginRedirectPath } from '@/src/lib/file-share';
import { cn } from '@/src/lib/utils';
import { createSession, markPostLoginPending, saveStoredSession } from '@/src/lib/session';
import type { AuthResponse } from '@/src/lib/types';

const DEV_LOGIN_ENABLED = import.meta.env.DEV || import.meta.env.VITE_ENABLE_DEV_LOGIN === 'true';

export default function Login() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [isLogin, setIsLogin] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [registerUsername, setRegisterUsername] = useState('');
  const [registerEmail, setRegisterEmail] = useState('');
  const [registerPhoneNumber, setRegisterPhoneNumber] = useState('');
  const [registerPassword, setRegisterPassword] = useState('');

  function switchMode(nextIsLogin: boolean) {
    setIsLogin(nextIsLogin);
    setError('');
    setLoading(false);
  }

  async function handleLoginSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      let auth: AuthResponse;

      try {
        auth = await apiRequest<AuthResponse>('/auth/login', {
          method: 'POST',
          body: { username, password },
        });
      } catch (requestError) {
        if (
          DEV_LOGIN_ENABLED &&
          username.trim() &&
          requestError instanceof ApiError &&
          requestError.status === 401
        ) {
          auth = await apiRequest<AuthResponse>(
            `/auth/dev-login?username=${encodeURIComponent(username.trim())}`,
            { method: 'POST' }
          );
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
      setError(requestError instanceof Error ? requestError.message : '登录失败，请稍后重试');
    }
  }

  async function handleRegisterSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const auth = await apiRequest<AuthResponse>('/auth/register', {
        method: 'POST',
        body: {
          username: registerUsername.trim(),
          email: registerEmail.trim(),
          phoneNumber: registerPhoneNumber.trim(),
          password: registerPassword,
        },
      });

      saveStoredSession(createSession(auth));
      markPostLoginPending();
      setLoading(false);
      navigate(getPostLoginRedirectPath(searchParams.get('next')));
    } catch (requestError) {
      setLoading(false);
      setError(requestError instanceof Error ? requestError.message : '注册失败，请稍后重试');
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-[#07101D] relative overflow-hidden">
      <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-[#336EFF] rounded-full mix-blend-screen filter blur-[128px] opacity-20 animate-pulse" />
      <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-purple-600 rounded-full mix-blend-screen filter blur-[128px] opacity-20" />

      <div className="container mx-auto px-4 relative z-10 flex items-center w-full min-h-[600px]">
        <AnimatePresence>
          {isLogin && (
            <motion.div
              key="brand-info"
              initial={{ opacity: 0, x: -50 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -50 }}
              transition={{ duration: 0.5, ease: 'easeOut' }}
              className="absolute left-4 lg:left-8 xl:left-12 w-1/2 max-w-lg hidden lg:flex flex-col space-y-6"
            >
              <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full glass-panel border-white/10 w-fit">
                <span className="w-2 h-2 rounded-full bg-[#336EFF] animate-pulse" />
                <span className="text-sm text-slate-300 font-medium tracking-wide uppercase">Access Portal</span>
              </div>

              <div className="space-y-2">
                <h2 className="text-xl text-[#336EFF] font-bold tracking-widest uppercase">YOYUZH.XYZ</h2>
                <h1 className="text-5xl md:text-6xl font-bold text-white leading-tight">
                  个人网站
                  <br />
                  统一入口
                </h1>
              </div>

              <p className="text-lg text-slate-400 leading-relaxed">
                欢迎来到 YOYUZH 的个人门户。在这里，你可以集中管理个人网盘文件、使用跨设备快传能力，以及体验轻量级小游戏。
              </p>
            </motion.div>
          )}
        </AnimatePresence>

        <motion.div
          layout
          transition={{ type: 'spring', stiffness: 200, damping: 25 }}
          className={cn(
            'w-full max-w-md z-10',
            isLogin ? 'ml-auto lg:mr-8 xl:mr-12' : 'mx-auto'
          )}
        >
          <Card className="border-white/10 backdrop-blur-2xl bg-white/5 shadow-2xl overflow-hidden">
            <AnimatePresence mode="wait">
              {isLogin ? (
                <motion.div
                  key="login-form"
                  initial={{ opacity: 0, x: 20 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -20 }}
                  transition={{ duration: 0.3 }}
                >
                  <CardHeader className="space-y-1 pb-8">
                    <CardTitle className="text-2xl font-bold text-white flex items-center gap-2">
                      <LogIn className="w-6 h-6 text-[#336EFF]" />
                      登录
                    </CardTitle>
                    <CardDescription className="text-slate-400">
                      请输入您的账号和密码以继续
                    </CardDescription>
                  </CardHeader>
                  <CardContent>
                    <form onSubmit={handleLoginSubmit} className="space-y-6">
                      <div className="space-y-4">
                        <div className="space-y-2">
                          <label className="text-sm font-medium text-slate-300 ml-1">用户名</label>
                          <div className="relative">
                            <User className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                            <Input
                              type="text"
                              placeholder="账号 / 用户名 / 学号"
                              className="pl-10 bg-black/20 border-white/10 focus-visible:ring-[#336EFF]"
                              value={username}
                              onChange={(event) => setUsername(event.target.value)}
                              required
                            />
                          </div>
                        </div>
                        <div className="space-y-2">
                          <label className="text-sm font-medium text-slate-300 ml-1">密码</label>
                          <div className="relative">
                            <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                            <Input
                              type="password"
                              placeholder="••••••••"
                              className="pl-10 bg-black/20 border-white/10 focus-visible:ring-[#336EFF]"
                              value={password}
                              onChange={(event) => setPassword(event.target.value)}
                              required
                            />
                          </div>
                        </div>
                      </div>

                      {error && (
                        <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-sm">
                          {error}
                        </div>
                      )}

                      <div className="space-y-4">
                        <Button
                          type="submit"
                          className="w-full h-12 text-base font-semibold"
                          disabled={loading}
                        >
                          {loading ? (
                            <span className="flex items-center gap-2">
                              <span className="w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin" />
                              登录中...
                            </span>
                          ) : (
                            '进入系统'
                          )}
                        </Button>
                        <div className="text-center">
                          <button
                            type="button"
                            onClick={() => switchMode(false)}
                            className="text-sm text-slate-400 hover:text-[#336EFF] transition-colors"
                          >
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
                  initial={{ opacity: 0, x: 20 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -20 }}
                  transition={{ duration: 0.3 }}
                >
                  <CardHeader className="space-y-1 pb-8">
                    <div className="flex items-center justify-between">
                      <CardTitle className="text-2xl font-bold text-white flex items-center gap-2">
                        <UserPlus className="w-6 h-6 text-[#336EFF]" />
                        注册账号
                      </CardTitle>
                      <button
                        type="button"
                        onClick={() => switchMode(true)}
                        className="p-2 rounded-full hover:bg-white/5 text-slate-400 transition-colors"
                      >
                        <ArrowLeft className="w-5 h-5" />
                      </button>
                    </div>
                    <CardDescription className="text-slate-400">
                      创建一个新账号以开启您的门户体验
                    </CardDescription>
                  </CardHeader>
                  <CardContent>
                    <form onSubmit={handleRegisterSubmit} className="space-y-6">
                      <div className="space-y-4">
                        <div className="space-y-2">
                          <label className="text-sm font-medium text-slate-300 ml-1">用户名</label>
                          <div className="relative">
                            <User className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                            <Input
                              type="text"
                              placeholder="设置您的用户名"
                              className="pl-10 bg-black/20 border-white/10 focus-visible:ring-[#336EFF]"
                              value={registerUsername}
                              onChange={(event) => setRegisterUsername(event.target.value)}
                              required
                              minLength={3}
                              maxLength={64}
                            />
                          </div>
                        </div>
                        <div className="space-y-2">
                          <label className="text-sm font-medium text-slate-300 ml-1">邮箱</label>
                          <div className="relative">
                            <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                            <Input
                              type="email"
                              placeholder="your@email.com"
                              className="pl-10 bg-black/20 border-white/10 focus-visible:ring-[#336EFF]"
                              value={registerEmail}
                              onChange={(event) => setRegisterEmail(event.target.value)}
                              required
                            />
                          </div>
                        </div>
                        <div className="space-y-2">
                          <label className="text-sm font-medium text-slate-300 ml-1">手机号</label>
                          <div className="relative">
                            <Phone className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                            <Input
                              type="tel"
                              placeholder="请输入11位手机号"
                              className="pl-10 bg-black/20 border-white/10 focus-visible:ring-[#336EFF]"
                              value={registerPhoneNumber}
                              onChange={(event) => setRegisterPhoneNumber(event.target.value)}
                              required
                            />
                          </div>
                        </div>
                        <div className="space-y-2">
                          <label className="text-sm font-medium text-slate-300 ml-1">密码</label>
                          <div className="relative">
                            <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                            <Input
                              type="password"
                              placeholder="设置您的密码"
                              className="pl-10 bg-black/20 border-white/10 focus-visible:ring-[#336EFF]"
                              value={registerPassword}
                              onChange={(event) => setRegisterPassword(event.target.value)}
                              required
                              minLength={10}
                              maxLength={64}
                            />
                          </div>
                          <p className="text-xs text-slate-500 ml-1">
                            至少 10 位，并包含大写字母、小写字母、数字和特殊字符。
                          </p>
                        </div>
                      </div>

                      {error && (
                        <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-sm">
                          {error}
                        </div>
                      )}

                      <div className="space-y-4">
                        <Button
                          type="submit"
                          className="w-full h-12 text-base font-semibold"
                          disabled={loading}
                        >
                          {loading ? (
                            <span className="flex items-center gap-2">
                              <span className="w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin" />
                              注册中...
                            </span>
                          ) : (
                            '创建账号'
                          )}
                        </Button>
                        <div className="text-center">
                          <button
                            type="button"
                            onClick={() => switchMode(true)}
                            className="text-sm text-slate-400 hover:text-[#336EFF] transition-colors"
                          >
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
        </motion.div>
      </div>
    </div>
  );
}
