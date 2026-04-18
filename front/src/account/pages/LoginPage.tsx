import { useState, type FormEvent } from 'react';
import { Moon, Sun } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import { useTheme } from '@/src/components/ThemeProvider';
import { devLogin, login, register } from '@/src/lib/auth';
import { getDefaultSignedInRoute } from '@/src/lib/session';
import { cn } from '@/src/lib/utils';

type LoginFormState = {
  username: string;
  password: string;
};

type RegisterFormState = {
  username: string;
  email: string;
  phoneNumber: string;
  password: string;
  confirmPassword: string;
  inviteCode: string;
};

const emptyRegisterForm: RegisterFormState = {
  username: '',
  email: '',
  phoneNumber: '',
  password: '',
  confirmPassword: '',
  inviteCode: '',
};

export default function Login() {
  const navigate = useNavigate();
  const [isLogin, setIsLogin] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [loginForm, setLoginForm] = useState<LoginFormState>({ username: '', password: '' });
  const [registerForm, setRegisterForm] = useState<RegisterFormState>(emptyRegisterForm);

  async function handleLoginSubmit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      const session = await login(loginForm);
      navigate(getDefaultSignedInRoute(session.user.role));
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败');
    } finally {
      setLoading(false);
    }
  }

  async function handleRegisterSubmit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      const session = await register(registerForm);
      navigate(getDefaultSignedInRoute(session.user.role));
    } catch (err) {
      setError(err instanceof Error ? err.message : '注册失败');
    } finally {
      setLoading(false);
    }
  }

  async function handleDevLogin(username: string) {
    setLoading(true);
    setError('');
    try {
      const session = await devLogin(username);
      navigate(getDefaultSignedInRoute(session.user.role));
    } catch (err) {
      setError(err instanceof Error ? err.message : '开发登录失败');
    } finally {
      setLoading(false);
    }
  }

  const { theme, setTheme } = useTheme();

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="min-h-screen bg-aurora text-gray-900 dark:text-gray-100 flex flex-col justify-center py-12 px-6 lg:px-8 relative overflow-hidden"
    >
      {/* Theme Toggle Top Right */}
      <motion.div 
        initial={{ y: -20, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ delay: 0.5 }}
        className="absolute top-6 right-6"
      >
        <button 
          onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          className="p-3 rounded-lg glass-panel-no-hover hover:bg-white/40 dark:hover:bg-black/40 transition-all shadow-lg"
        >
          {theme === 'dark' ? <Sun className="w-5 h-5 text-yellow-300" /> : <Moon className="w-5 h-5 text-gray-700" />}
        </button>
      </motion.div>

      <div className="sm:mx-auto sm:w-full sm:max-w-md relative z-10">
        <motion.div 
          layout
          initial={{ y: 20, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ duration: 0.6, ease: "easeOut" }}
          className="glass-panel-no-hover py-12 px-8 shadow-2xl rounded-lg sm:px-12 border-white/20 dark:border-white/10"
        >
          <div className="mb-10 text-center">
            <motion.h2 
              className="text-4xl font-black tracking-tight animate-text-reveal"
              style={{ background: 'linear-gradient(to right, currentColor, #3b82f6)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}
            >
              云盘门户
            </motion.h2>
            <motion.p 
              initial={{ opacity: 0 }}
              animate={{ opacity: 0.6 }}
              transition={{ delay: 0.4 }}
              className="mt-3 text-sm font-bold uppercase tracking-widest"
            >
              {isLogin ? '登录认证' : '创建账号'}
            </motion.p>
          </div>

          {error ? (
            <motion.div 
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              className="mb-6 rounded-lg bg-red-500/10 px-4 py-3 text-[13px] text-red-600 dark:text-red-400 font-bold border border-red-500/20 backdrop-blur-md overflow-hidden"
            >
              {error}
            </motion.div>
          ) : null}

          <motion.div layout>
            {isLogin ? (
              <form className="space-y-4" onSubmit={handleLoginSubmit}>
                <motion.div initial={{ x: -10, opacity: 0 }} animate={{ x: 0, opacity: 1 }} transition={{ delay: 0.1 }}>
                  <input
                    placeholder="用户名"
                    value={loginForm.username}
                    onChange={(event) => setLoginForm((current) => ({ ...current, username: event.target.value }))}
                    className="w-full px-5 py-4 bg-white/10 dark:bg-black/20 border border-white/10 dark:border-white/5 rounded-lg text-gray-900 dark:text-gray-100 placeholder:text-gray-500 dark:placeholder:text-gray-400 text-base focus:outline-none focus:ring-2 focus:ring-blue-500/50 transition-all font-bold tracking-wide"
                    required
                  />
                </motion.div>
                <motion.div initial={{ x: -10, opacity: 0 }} animate={{ x: 0, opacity: 1 }} transition={{ delay: 0.2 }}>
                  <input
                    type="password"
                    placeholder="密码"
                    value={loginForm.password}
                    onChange={(event) => setLoginForm((current) => ({ ...current, password: event.target.value }))}
                    className="w-full px-5 py-4 bg-white/10 dark:bg-black/20 border border-white/10 dark:border-white/5 rounded-lg text-gray-900 dark:text-gray-100 placeholder:text-gray-500 dark:placeholder:text-gray-400 text-base focus:outline-none focus:ring-2 focus:ring-blue-500/50 transition-all font-bold tracking-wide"
                    required
                  />
                </motion.div>
                <motion.button
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                  type="submit"
                  disabled={loading}
                  className="w-full flex justify-center mt-6 py-4 px-4 rounded-lg shadow-lg text-sm font-black uppercase tracking-widest text-white bg-blue-600 hover:bg-blue-500 transition-all disabled:opacity-50"
                >
                  {loading ? '处理中...' : '登录'}
                </motion.button>
              </form>
            ) : (
              <form className="space-y-4" onSubmit={handleRegisterSubmit}>
                {[
                  { name: 'username', placeholder: '用户名', type: 'text' },
                  { name: 'email', placeholder: '邮箱地址', type: 'email' },
                  { name: 'phoneNumber', placeholder: '手机号', type: 'text' },
                  { name: 'inviteCode', placeholder: '邀请码', type: 'text' },
                  { name: 'password', placeholder: '密码', type: 'password' },
                  { name: 'confirmPassword', placeholder: '确认密码', type: 'password' },
                ].map((field, idx) => (
                  <motion.div 
                    key={field.name}
                    initial={{ x: -10, opacity: 0 }} 
                    animate={{ x: 0, opacity: 1 }} 
                    transition={{ delay: idx * 0.05 }}
                  >
                    <input
                      type={field.type}
                      placeholder={field.placeholder}
                      value={registerForm[field.name as keyof RegisterFormState]}
                      onChange={(event) => setRegisterForm((current) => ({ ...current, [field.name]: event.target.value }))}
                      className="w-full px-5 py-3.5 bg-white/10 dark:bg-black/20 border border-white/10 dark:border-white/5 rounded-lg text-gray-900 dark:text-gray-100 placeholder:text-gray-500 dark:placeholder:text-gray-400 text-base focus:outline-none focus:ring-2 focus:ring-blue-500/50 transition-all font-bold tracking-wide"
                      required
                    />
                  </motion.div>
                ))}
                <motion.button
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                  type="submit"
                  disabled={loading}
                  className="w-full flex justify-center mt-6 py-4 px-4 rounded-lg shadow-lg text-sm font-black uppercase tracking-widest text-white bg-blue-600 hover:bg-blue-500 transition-all disabled:opacity-50"
                >
                  {loading ? '创建中...' : '注册账号'}
                </motion.button>
              </form>
            )}
          </motion.div>

          <div className="mt-8 space-y-4">
            <button
              type="button"
              onClick={() => setIsLogin((current) => !current)}
              className="w-full py-4 text-sm font-black uppercase tracking-widest opacity-70 hover:opacity-100 transition-opacity"
            >
              {isLogin ? '还没有账号？去注册' : '已有账号？去登录'}
            </button>

            <button
              type="button"
              onClick={() => navigate('/transfer')}
              className="w-full py-4 rounded-lg glass-panel border border-white/10 text-sm font-black uppercase tracking-widest text-blue-600 dark:text-blue-400 hover:bg-white/20 transition-all"
            >
              直接进入快传
            </button>
            
            <div className="flex justify-center gap-8 pt-4 border-t border-white/10">
              <button 
                onClick={() => handleDevLogin('demo')}
                disabled={loading}
                className="text-sm font-black uppercase tracking-widest text-blue-500 hover:text-blue-400 transition-colors disabled:opacity-50"
              >
                开发账号
              </button>
              <button 
                onClick={() => handleDevLogin('admin')}
                disabled={loading}
                className="text-sm font-black uppercase tracking-widest text-purple-500 hover:text-purple-400 transition-colors disabled:opacity-50"
              >
                管理员账号
              </button>
            </div>
          </div>
        </motion.div>
      </div>
    </motion.div>
  );
}
