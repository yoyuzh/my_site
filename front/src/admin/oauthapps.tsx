import type { ReactNode } from 'react';
import { ArrowRight, Ban, CheckCircle2, KeyRound, ShieldAlert, Sparkles } from 'lucide-react';
import { motion } from 'motion/react';

const container = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: {
      staggerChildren: 0.06,
    },
  },
};

const itemVariants = {
  hidden: { y: 16, opacity: 0 },
  show: { y: 0, opacity: 1 },
};

function SectionTitle({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: string;
  description: string;
}) {
  return (
    <div className="mb-6">
      <h2 className="text-[10px] font-black uppercase tracking-[0.3em] opacity-30">{eyebrow}</h2>
      <h3 className="mt-3 text-2xl font-black tracking-tight text-gray-900 dark:text-white">{title}</h3>
      <p className="mt-3 max-w-3xl text-sm leading-7 text-gray-600 dark:text-gray-300">{description}</p>
    </div>
  );
}

function Badge({
  children,
  tone = 'neutral',
}: {
  children: string;
  tone?: 'neutral' | 'warning' | 'success' | 'info';
}) {
  const toneClasses = {
    neutral: 'border-white/10 bg-white/5 text-gray-600 dark:text-gray-300',
    warning: 'border-amber-500/20 bg-amber-500/10 text-amber-700 dark:text-amber-300',
    success: 'border-green-500/20 bg-green-500/10 text-green-700 dark:text-green-300',
    info: 'border-blue-500/20 bg-blue-500/10 text-blue-700 dark:text-blue-300',
  }[tone];

  return (
    <span
      className={`inline-flex items-center rounded-full border px-3 py-1 text-[9px] font-black uppercase tracking-[0.22em] ${toneClasses}`}
    >
      {children}
    </span>
  );
}

function InfoCard({
  title,
  description,
  icon,
  tone = 'blue',
}: {
  title: string;
  description: string;
  icon: ReactNode;
  tone?: 'blue' | 'amber' | 'green' | 'violet';
}) {
  const ringClasses = {
    blue: 'border-blue-500/20 bg-blue-500/10 text-blue-500',
    amber: 'border-amber-500/20 bg-amber-500/10 text-amber-500',
    green: 'border-green-500/20 bg-green-500/10 text-green-500',
    violet: 'border-violet-500/20 bg-violet-500/10 text-violet-500',
  }[tone];

  return (
    <motion.section
      variants={itemVariants}
      className="glass-panel-no-hover rounded-2xl border border-white/10 p-6 shadow-3xl"
    >
      <div className="flex items-start gap-4">
        <div className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-xl border ${ringClasses}`}>
          {icon}
        </div>
        <div>
          <h4 className="text-[13px] font-black uppercase tracking-[0.18em] text-gray-900 dark:text-white">{title}</h4>
          <p className="mt-2 text-sm leading-7 text-gray-600 dark:text-gray-300">{description}</p>
        </div>
      </div>
    </motion.section>
  );
}

export default function AdminOAuthApps() {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full flex-col overflow-y-auto p-8 text-gray-900 dark:text-gray-100"
    >
      <div className="mb-10 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="animate-text-reveal text-4xl font-black tracking-tight text-gray-900 dark:text-white">
            三方应用
          </h1>
          <p className="mt-3 text-[10px] font-black uppercase tracking-[0.2em] opacity-40">
            当前仅保留规划状态 / 后端支持未就绪 / 不提供可写配置
          </p>
        </div>
        <Badge tone="warning">规划中</Badge>
      </div>

      <motion.section
        variants={container}
        initial="hidden"
        animate="show"
        className="grid grid-cols-1 gap-6 xl:grid-cols-12"
      >
        <motion.div
          variants={itemVariants}
          className="xl:col-span-7 glass-panel-no-hover rounded-2xl border border-white/10 p-8 shadow-3xl"
        >
          <SectionTitle
            eyebrow="当前状态"
            title="后端尚未提供 OAuth 应用管理接口"
            description="页面现在只做状态说明，不接入任何写接口，也不展示虚假的配置表单。等 `/api/admin/oauth-apps` 及相关校验、审计、回调配置能力上线后，这里再开放真正的管理操作。"
          />

          <div className="grid gap-4 md:grid-cols-2">
            <div className="rounded-xl border border-white/10 bg-white/5 p-5">
              <div className="mb-3 flex items-center gap-2 text-[10px] font-black uppercase tracking-[0.22em] opacity-35">
                <ShieldAlert className="h-4 w-4 text-amber-500" />
                后端支持状态
              </div>
              <p className="text-sm leading-7 text-gray-700 dark:text-gray-200">
                目前仓库里没有面向管理员的 OAuth 应用管理 API，因此前端只能展示说明，不能创建、编辑或删除应用。
              </p>
            </div>

            <div className="rounded-xl border border-white/10 bg-white/5 p-5">
              <div className="mb-3 flex items-center gap-2 text-[10px] font-black uppercase tracking-[0.22em] opacity-35">
                <Ban className="h-4 w-4 text-red-500" />
                为什么没有可写控件
              </div>
              <p className="text-sm leading-7 text-gray-700 dark:text-gray-200">
                如果现在就提供按钮或输入框，会让人误以为配置已经生效。为了避免误导，这一页保持只读，直到后端能力真正落地。
              </p>
            </div>
          </div>
        </motion.div>

        <motion.aside
          variants={itemVariants}
          className="xl:col-span-5 glass-panel-no-hover rounded-2xl border border-white/10 p-8 shadow-3xl"
        >
          <div className="mb-6 flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-blue-500/20 bg-blue-500/10 text-blue-500">
              <KeyRound className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-[13px] font-black uppercase tracking-[0.18em] text-gray-900 dark:text-white">
                规划入口
              </h2>
              <p className="mt-1 text-[9px] font-black uppercase tracking-[0.24em] opacity-30">
                仅展示后续将开放的能力
              </p>
            </div>
          </div>

          <div className="space-y-4">
            <div className="rounded-xl border border-white/10 bg-black/20 p-5">
              <div className="mb-3 flex items-center gap-2 text-[10px] font-black uppercase tracking-[0.22em] opacity-35">
                <Sparkles className="h-4 w-4 text-blue-500" />
                未来会增加
              </div>
              <ul className="space-y-3 text-sm leading-7 text-gray-700 dark:text-gray-200">
                <li className="flex gap-2">
                  <CheckCircle2 className="mt-1 h-4 w-4 shrink-0 text-green-500" />
                  OAuth 应用的创建、编辑、停用与删除
                </li>
                <li className="flex gap-2">
                  <CheckCircle2 className="mt-1 h-4 w-4 shrink-0 text-green-500" />
                  Client ID / Client Secret 的安全展示与轮换
                </li>
                <li className="flex gap-2">
                  <CheckCircle2 className="mt-1 h-4 w-4 shrink-0 text-green-500" />
                  回调地址、授权范围和状态的可视化管理
                </li>
                <li className="flex gap-2">
                  <CheckCircle2 className="mt-1 h-4 w-4 shrink-0 text-green-500" />
                  审计记录与变更历史
                </li>
              </ul>
            </div>

            <button
              type="button"
              disabled
              aria-disabled="true"
              className="flex w-full items-center justify-between rounded-xl border border-dashed border-white/10 bg-white/5 px-5 py-4 text-left opacity-60"
            >
              <div>
                <div className="text-[11px] font-black uppercase tracking-[0.2em] text-gray-900 dark:text-white">
                  等待后端 API 到位
                </div>
                <div className="mt-2 text-[9px] font-black uppercase tracking-[0.22em] opacity-30">
                  目前不会提交任何写操作
                </div>
              </div>
              <ArrowRight className="h-4 w-4 shrink-0" />
            </button>
          </div>
        </motion.aside>

        <motion.section
          variants={itemVariants}
          className="xl:col-span-12 glass-panel-no-hover rounded-2xl border border-white/10 p-8 shadow-3xl"
        >
          <SectionTitle
            eyebrow="后续能力"
            title="后端上线后，这里会逐步开放的内容"
            description="这一页会先作为路线图，等管理员接口稳定后再切回真正的操作界面。前端会直接绑定真实接口，并补齐权限、校验和反馈。"
          />

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <InfoCard
              title="应用登记"
              description="支持录入应用名称、回调地址、主页地址、描述和启用状态，形成可追踪的应用清单。"
              icon={<KeyRound className="h-5 w-5" />}
            />
            <InfoCard
              title="凭据管理"
              description="支持生成、复制、轮换和失效 Client Secret，并对敏感凭据做受控展示。"
              icon={<ShieldAlert className="h-5 w-5" />}
              tone="amber"
            />
            <InfoCard
              title="授权范围"
              description="支持配置 OAuth scope、授权类型和回调白名单，避免把权限交给不该拿到的应用。"
              icon={<Sparkles className="h-5 w-5" />}
              tone="violet"
            />
            <InfoCard
              title="审计追踪"
              description="记录每一次增删改、密钥轮换和回调配置变更，方便排查和安全复核。"
              icon={<CheckCircle2 className="h-5 w-5" />}
              tone="green"
            />
          </div>
        </motion.section>
      </motion.section>
    </motion.div>
  );
}
