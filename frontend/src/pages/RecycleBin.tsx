import React, { useState } from 'react';
import DashboardLayout from '../components/DashboardLayout';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRecycleBin } from '../api/queries';
import { formatBytes, formatDateTime, formatTimeUntil } from '../lib/format';
import { restoreRecycleBinItem, deleteRecycleBinItem } from '../lib/files';
import { 
  Folder, 
  InsertDriveFile, 
  RestoreFromTrash, 
  DeleteForever
} from '@mui/icons-material';
import { 
  IconButton, 
  Tooltip, 
  Dialog, 
  DialogTitle, 
  DialogContent, 
  DialogContentText, 
  DialogActions, 
  Button,
  CircularProgress
} from '@mui/material';

const RecycleBin: React.FC = () => {
  const [page, setPage] = useState(1);
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);
  const queryClient = useQueryClient();

  const { data, isLoading, isError } = useRecycleBin(page, 20);

  const restoreMutation = useMutation({
    mutationFn: restoreRecycleBinItem,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['recycleBin'] });
      void queryClient.invalidateQueries({ queryKey: ['files'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteRecycleBinItem,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['recycleBin'] });
      setDeleteConfirmId(null);
    },
  });

  const handleDeletePermanent = () => {
    if (deleteConfirmId !== null) {
      deleteMutation.mutate(deleteConfirmId);
    }
  };

  return (
    <DashboardLayout title="回收站 Recycle Bin">
      <div className="flex flex-col h-full">
        {isLoading ? (
          <div className="card-container flex-1 p-10 flex items-center justify-center">
            <CircularProgress size={32} />
            <span className="ml-3 text-text-secondary-light dark:text-text-secondary-dark font-geist">加载中...</span>
          </div>
        ) : isError ? (
          <div className="card-container flex-1 p-10 flex items-center justify-center text-red-500 font-geist">
            回收站加载失败
          </div>
        ) : data && data.items.length > 0 ? (
          <>
            <div className="flex-1 min-h-0 card-container overflow-hidden flex flex-col mb-4">
              <div className="flex-1 overflow-auto">
                <table className="w-full border-collapse text-left">
                  <thead className="sticky top-0 bg-[#F8FAFC] dark:bg-[#1A1A2E] z-10">
                    <tr className="border-b border-[#D9E3F2] dark:border-[#222233]">
                      <th className="px-6 py-4 text-xs font-bold text-text-secondary-light dark:text-text-secondary-dark uppercase tracking-wider">名称</th>
                      <th className="px-6 py-4 text-xs font-bold text-text-secondary-light dark:text-text-secondary-dark uppercase tracking-wider">大小</th>
                      <th className="px-6 py-4 text-xs font-bold text-text-secondary-light dark:text-text-secondary-dark uppercase tracking-wider">过期时间</th>
                      <th className="px-6 py-4 text-xs font-bold text-text-secondary-light dark:text-text-secondary-dark uppercase tracking-wider">原始位置</th>
                      <th className="px-6 py-4 text-xs font-bold text-text-secondary-light dark:text-text-secondary-dark uppercase tracking-wider text-right">操作</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#D9E3F2] dark:divide-[#222233]">
                    {data.items.map((item) => (
                      <tr key={item.id} className="hover:bg-[#F1F5F9] dark:hover:bg-[#222233] transition-colors group">
                        <td className="px-6 py-4">
                          <div className="flex items-center gap-3">
                            {item.directory ? (
                              <Folder className="text-amber-500" fontSize="small" />
                            ) : (
                              <InsertDriveFile className="text-blue-500" fontSize="small" />
                            )}
                            <span className="font-medium text-text-primary-light dark:text-white truncate max-w-[240px]" title={item.filename}>
                              {item.filename}
                            </span>
                          </div>
                        </td>
                        <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark whitespace-nowrap">
                          {item.directory ? '-' : formatBytes(item.size)}
                        </td>
                        <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark whitespace-nowrap">
                          <span title={formatDateTime(item.expiresAt)}>
                            {formatTimeUntil(item.expiresAt)}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-sm text-text-secondary-light dark:text-text-secondary-dark">
                          <div className="flex items-center gap-1 group/path">
                            <span className="truncate max-w-[200px]" title={item.path}>
                              {item.path}
                            </span>
                          </div>
                        </td>
                        <td className="px-6 py-4 text-right whitespace-nowrap">
                          <div className="flex justify-end gap-1">
                            <Tooltip title="恢复">
                              <IconButton 
                                size="small" 
                                color="primary" 
                                onClick={() => restoreMutation.mutate(item.id)}
                                disabled={restoreMutation.isPending && restoreMutation.variables === item.id}
                              >
                                {restoreMutation.isPending && restoreMutation.variables === item.id ? (
                                  <CircularProgress size={20} />
                                ) : (
                                  <RestoreFromTrash fontSize="small" />
                                )}
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="直接删除">
                              <IconButton 
                                size="small" 
                                color="error" 
                                onClick={() => setDeleteConfirmId(item.id)}
                              >
                                <DeleteForever fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
            
            <div className="flex-none card-container px-6 py-4 flex items-center justify-between text-sm text-text-secondary-light dark:text-text-secondary-dark">
              <span className="font-geist">共 {data.pagination.total_items} 项</span>
              <div className="flex gap-2">
                <button 
                  className="px-3 py-1 border border-[#D9E3F2] dark:border-[#222233] rounded disabled:opacity-50 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors" 
                  disabled={page <= 1} 
                  onClick={() => setPage((current) => current - 1)}
                >
                  上一页
                </button>
                <div className="px-3 py-1 bg-brand-light text-white rounded font-medium">
                  {page}
                </div>
                <button 
                  className="px-3 py-1 border border-[#D9E3F2] dark:border-[#222233] rounded disabled:opacity-50 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors" 
                  disabled={page >= data.pagination.total_pages} 
                  onClick={() => setPage((current) => current + 1)}
                >
                  下一页
                </button>
              </div>
            </div>
          </>
        ) : (
          <div className="card-container flex-1 p-10 text-center flex flex-col items-center justify-center">
            <div className="w-16 h-16 rounded-full bg-red-500/10 text-red-500 flex items-center justify-center mb-4">
              <DeleteForever sx={{ fontSize: 32 }} />
            </div>
            <h3 className="text-xl font-bold text-text-primary-light dark:text-white mb-2 font-geist">回收站为空</h3>
            <p className="text-text-secondary-light dark:text-text-secondary-dark font-geist max-w-md">
              被删除的文件会暂时保留在这里。你可以在它们被自动清除前恢复。
            </p>
          </div>
        )}
      </div>

      <Dialog
        open={deleteConfirmId !== null}
        onClose={() => setDeleteConfirmId(null)}
        aria-labelledby="delete-dialog-title"
      >
        <DialogTitle id="delete-dialog-title" className="font-bold">
          确认永久删除？
        </DialogTitle>
        <DialogContent>
          <DialogContentText className="font-geist">
            该操作将立即彻底删除该文件，且不可恢复。请确认是否继续？
          </DialogContentText>
        </DialogContent>
        <DialogActions className="px-6 pb-6">
          <Button onClick={() => setDeleteConfirmId(null)} color="inherit" variant="outlined" size="small">
            取消
          </Button>
          <Button 
            onClick={handleDeletePermanent} 
            color="error" 
            variant="contained" 
            autoFocus 
            size="small"
            disabled={deleteMutation.isPending}
            startIcon={deleteMutation.isPending ? <CircularProgress size={16} color="inherit" /> : null}
          >
            彻底删除
          </Button>
        </DialogActions>
      </Dialog>
    </DashboardLayout>
  );
};

export default RecycleBin;
