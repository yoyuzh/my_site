import React from 'react';
import { ShieldAlert } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/src/components/ui/button';

export default function MobileAdminUnavailable() {
  const navigate = useNavigate();

  return (
    <div className="flex flex-col h-full bg-[#07101D] text-slate-300 min-h-[100dvh] pb-24 items-center justify-center p-6">
      <div className="w-20 h-20 rounded-full bg-blue-500/10 flex items-center justify-center mb-6 shadow-inner border border-blue-500/20">
        <ShieldAlert className="w-10 h-10 text-blue-400" />
      </div>
      <h1 className="text-xl font-bold text-white mb-3 text-center">移动端暂不支持管理台</h1>
      <p className="text-sm text-slate-400 text-center max-w-sm mb-8 leading-relaxed">
        由于管理后台包含复杂的表单和表格操作，为了保证体验，该功能仅支持桌面端使用。请在电脑上访问以继续管理操作。
      </p>
      <Button 
        className="bg-[#336EFF] hover:bg-blue-600 text-white rounded-xl shadow-lg border-none px-8 py-6"
        onClick={() => navigate('/overview')}
      >
        返回主页
      </Button>
    </div>
  );
}
