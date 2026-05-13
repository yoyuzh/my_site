import React, { useState } from 'react';
import DashboardLayout from '../components/DashboardLayout';
import WebDavAccessPanel from '../components/webdav/WebDavAccessPanel';
import { getSession } from '../lib/session';

const Connections: React.FC = () => {
  const user = getSession()?.user;
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  if (!user) {
    return null;
  }

  return (
    <DashboardLayout title="连接与挂载">
      <div className="h-full overflow-y-auto pr-2">
        <div className="mx-auto flex max-w-4xl flex-col gap-4 pb-8">
          {message && (
            <div className={`rounded-2xl border px-4 py-3 text-sm ${
              message.type === 'success'
                ? 'border-emerald-500/20 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
                : 'border-red-500/20 bg-red-500/10 text-red-700 dark:text-red-300'
            }`}>
              {message.text}
            </div>
          )}
          <WebDavAccessPanel username={user.username} onMessage={setMessage} />
        </div>
      </div>
    </DashboardLayout>
  );
};

export default Connections;
