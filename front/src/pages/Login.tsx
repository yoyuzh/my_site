import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import { LogIn, User, Lock } from 'lucide-react';

import { Button } from '@/src/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/src/components/ui/card';
import { Input } from '@/src/components/ui/input';
import { apiRequest, ApiError } from '@/src/lib/api';
import { saveStoredSession } from '@/src/lib/session';
import type { AuthResponse } from '@/src/lib/types';

const DEV_LOGIN_ENABLED = import.meta.env.DEV || import.meta.env.VITE_ENABLE_DEV_LOGIN === 'true';

export default function Login() {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleLogin = async (e: React.FormEvent) => {
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

      saveStoredSession({
        token: auth.token,
        user: auth.user,
      });
      setLoading(false);
      navigate('/overview');
    } catch (requestError) {
      setLoading(false);
      setError(requestError instanceof Error ? requestError.message : '登录失败，请稍后重试');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-[#07101D] relative overflow-hidden">
      {/* Background Glow */}
      <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-[#336EFF] rounded-full mix-blend-screen filter blur-[128px] opacity-20 animate-pulse" />
      <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-purple-600 rounded-full mix-blend-screen filter blur-[128px] opacity-20" />

      <div className="container mx-auto px-4 grid lg:grid-cols-2 gap-12 items-center relative z-10">
        {/* Left Side: Brand Info */}
        <motion.div
          initial={{ opacity: 0, x: -50 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.8, ease: 'easeOut' }}
          className="flex flex-col space-y-6 max-w-lg"
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
            欢迎来到 YOYUZH 的个人门户。在这里，你可以集中管理个人网盘文件、查询教务成绩课表，以及体验轻量级小游戏。
          </p>
        </motion.div>

        {/* Right Side: Login Form */}
        <motion.div
          initial={{ opacity: 0, y: 50 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.2, ease: 'easeOut' }}
          className="w-full max-w-md mx-auto lg:mx-0 lg:ml-auto"
        >
          <Card className="border-white/10 backdrop-blur-2xl bg-white/5 shadow-2xl">
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
              <form onSubmit={handleLogin} className="space-y-6">
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
              </form>
            </CardContent>
          </Card>
        </motion.div>
      </div>
    </div>
  );
}
