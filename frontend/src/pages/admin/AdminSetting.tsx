import React, { useState } from 'react';
import AdminLayout from '../../components/AdminLayout';
import { Save, Globe, KeyRound, Bot, Image as ImageIcon, CreditCard, Mail, RefreshCw, Palette, Bell, Server } from 'lucide-react';

const tabs = [
  { id: 'siteInfo', label: '基础设置', icon: <Globe size={18} /> },
  { id: 'userSession', label: '用户与会话', icon: <KeyRound size={18} /> },
  { id: 'captcha', label: '验证码', icon: <Bot size={18} /> },
  { id: 'media', label: '媒体处理', icon: <ImageIcon size={18} /> },
  { id: 'vas', label: '增值服务', icon: <CreditCard size={18} /> },
  { id: 'email', label: '邮件', icon: <Mail size={18} /> },
  { id: 'queue', label: '队列', icon: <RefreshCw size={18} /> },
  { id: 'appearance', label: '外观', icon: <Palette size={18} /> },
  { id: 'events', label: '事件', icon: <Bell size={18} /> },
  { id: 'server', label: '服务', icon: <Server size={18} /> },
];

const AdminSetting: React.FC = () => {
  const [activeTab, setActiveTab] = useState('siteInfo');

  return (
    <AdminLayout title="参数设置">
      <div className="flex flex-col lg:flex-row gap-8">
        
        {/* Settings Navigation Tabs */}
        <aside className="w-full lg:w-64 flex-shrink-0">
          <div className="card-container p-2">
            <nav className="flex flex-row lg:flex-col gap-1 overflow-x-auto lg:overflow-x-visible pb-2 lg:pb-0 scrollbar-hide">
              {tabs.map((tab) => (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-all duration-200 whitespace-nowrap text-sm font-medium ${
                    activeTab === tab.id
                      ? 'bg-brand-light text-white dark:bg-brand-dark shadow-md'
                      : 'text-text-secondary-light dark:text-text-secondary-dark hover:bg-black/5 dark:hover:bg-white/5 hover:text-text-primary-light dark:hover:text-text-primary-dark'
                  }`}
                >
                  {tab.icon}
                  {tab.label}
                </button>
              ))}
            </nav>
          </div>
        </aside>

        {/* Settings Content Area */}
        <div className="flex-1 max-w-4xl">
          <div className="card-container p-8 animate-fade-in-up">
            
            {/* Site Information Tab */}
            {activeTab === 'siteInfo' && (
              <div>
                <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-6">站点信息</h3>
                <form className="space-y-6" onSubmit={(e) => e.preventDefault()}>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div className="md:col-span-1">
                      <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">站点名称</label>
                      <p className="text-[13px] text-text-muted-light dark:text-text-muted-dark leading-relaxed font-geist">用于页面标题、邮件及各种提示信息的站名</p>
                    </div>
                    <div className="md:col-span-2">
                      <input type="text" className="input-field" defaultValue="Cloudreve" />
                    </div>
                  </div>
                  
                  <hr className="border-[#D9E3F2] dark:border-[#222233]" />

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div className="md:col-span-1">
                      <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">站点描述</label>
                    </div>
                    <div className="md:col-span-2">
                      <input type="text" className="input-field" defaultValue="Cloudreve - A Next-Generation Cloud Storage Solution" />
                    </div>
                  </div>

                  <hr className="border-[#D9E3F2] dark:border-[#222233]" />

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div className="md:col-span-1">
                      <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">站点URL</label>
                      <p className="text-[13px] text-text-muted-light dark:text-text-muted-dark leading-relaxed font-geist">站点的完整 URL，包括协议和末尾的斜杠，这是很多功能正常工作的基础</p>
                    </div>
                    <div className="md:col-span-2">
                      <input type="url" className="input-field" defaultValue="http://localhost:5212/" />
                    </div>
                  </div>

                  <div className="pt-6 flex justify-end">
                    <button className="btn-primary flex items-center gap-2">
                      <Save size={18} /> 保存更改
                    </button>
                  </div>
                </form>
              </div>
            )}

            {/* User Session Tab */}
            {activeTab === 'userSession' && (
              <div>
                <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-6">用户与会话</h3>
                <form className="space-y-6" onSubmit={(e) => e.preventDefault()}>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div className="md:col-span-1">
                      <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">允许注册</label>
                    </div>
                    <div className="md:col-span-2 flex items-center">
                      <label className="relative inline-flex items-center cursor-pointer">
                        <input type="checkbox" className="sr-only peer" defaultChecked />
                        <div className="w-11 h-6 bg-[#D9E3F2] dark:bg-[#222233] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-brand-light dark:peer-checked:bg-brand-dark"></div>
                        <span className="ml-3 text-sm font-medium text-text-secondary-light dark:text-text-secondary-dark">开启</span>
                      </label>
                    </div>
                  </div>
                  
                  <hr className="border-[#D9E3F2] dark:border-[#222233]" />

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div className="md:col-span-1">
                      <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">邮箱激活</label>
                      <p className="text-[13px] text-text-muted-light dark:text-text-muted-dark leading-relaxed font-geist">新注册账号是否需要邮箱激活后才能使用</p>
                    </div>
                    <div className="md:col-span-2 flex items-center">
                      <label className="relative inline-flex items-center cursor-pointer">
                        <input type="checkbox" className="sr-only peer" />
                        <div className="w-11 h-6 bg-[#D9E3F2] dark:bg-[#222233] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-brand-light dark:peer-checked:bg-brand-dark"></div>
                        <span className="ml-3 text-sm font-medium text-text-secondary-light dark:text-text-secondary-dark">关闭</span>
                      </label>
                    </div>
                  </div>

                  <hr className="border-[#D9E3F2] dark:border-[#222233]" />

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div className="md:col-span-1">
                      <label className="block text-[14px] font-semibold text-text-primary-light dark:text-white mb-2">新用户默认用户组</label>
                    </div>
                    <div className="md:col-span-2">
                      <select className="input-field appearance-none bg-white dark:bg-[#0A0A0A]">
                        <option value="2">Registered Users</option>
                        <option value="3">Guests</option>
                      </select>
                    </div>
                  </div>

                  <div className="pt-6 flex justify-end">
                    <button className="btn-primary flex items-center gap-2">
                      <Save size={18} /> 保存更改
                    </button>
                  </div>
                </form>
              </div>
            )}

            {/* Placeholders for other tabs */}
            {activeTab !== 'siteInfo' && activeTab !== 'userSession' && (
              <div className="flex flex-col items-center justify-center py-20 text-text-muted-light dark:text-text-muted-dark">
                <Settings size={48} className="mb-4 opacity-50" />
                <p className="text-lg font-medium">{tabs.find(t => t.id === activeTab)?.label} 设置暂未加载</p>
                <p className="text-sm mt-2">相关参数表单将被注入在此处。</p>
              </div>
            )}
          </div>
        </div>
        
      </div>
    </AdminLayout>
  );
};

import { Settings } from 'lucide-react';
export default AdminSetting;
