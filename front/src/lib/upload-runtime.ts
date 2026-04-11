import { 
  createUploadSession, 
  getUploadSession, 
  prepareUpload, 
  prepareUploadPart, 
  recordUploadedPart, 
  completeUploadSession,
  type UploadSession 
} from './upload-session';
import { fetchApi } from './api';

export type UploadStatus = 'WAITING' | 'UPLOADING' | 'COMPLETING' | 'SUCCESS' | 'ERROR' | 'CANCELLED';

export interface UploadTask {
  id: string; // sessionId
  file: File;
  path: string;
  filename: string;
  size: number;
  progress: number;
  status: UploadStatus;
  error?: string;
}

class UploadRuntime {
  private tasks: Map<string, UploadTask> = new Map();
  private listeners: Set<() => void> = new Set();

  getTasks() {
    return Array.from(this.tasks.values());
  }

  subscribe(listener: () => void) {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify() {
    this.listeners.forEach((l) => l());
  }

  async uploadFile(file: File, path = '/') {
    const session = await createUploadSession({
      path,
      filename: file.name,
      contentType: file.type || 'application/octet-stream',
      size: file.size,
    });

    const task: UploadTask = {
      id: session.sessionId,
      file,
      path,
      filename: file.name,
      size: file.size,
      progress: 0,
      status: 'UPLOADING',
    };

    this.tasks.set(task.id, task);
    this.notify();

    try {
      await this.processUpload(session, file, (progress) => {
        task.progress = progress;
        this.notify();
      });
      task.status = 'SUCCESS';
      task.progress = 100;
    } catch (err) {
      task.status = 'ERROR';
      task.error = err instanceof Error ? err.message : '上传失败';
    } finally {
      this.notify();
    }
  }

  private async processUpload(session: UploadSession, file: File, onProgress: (p: number) => void) {
    if (session.uploadMode === 'PROXY') {
      const formData = new FormData();
      formData.append(session.strategy.proxyFieldName || 'file', file);
      // 注意：PROXY 模式目前难以获取原生 fetch 的上传进度，除非使用 XMLHttpRequest
      await fetchApi(session.strategy.proxyUploadUrl || `/v2/files/upload-sessions/${session.sessionId}/content`, {
        method: 'POST',
        body: formData,
      });
      onProgress(100);
      return;
    }

    if (session.uploadMode === 'DIRECT_SINGLE') {
      const prepared = await prepareUpload(session.sessionId);
      await fetch(prepared.uploadUrl, {
        method: prepared.method || 'PUT',
        headers: prepared.headers,
        body: file,
      });
      onProgress(100);
      await completeUploadSession(session.sessionId);
      return;
    }

    // 分片上传进度模拟
    const chunkSize = Math.max(session.chunkSize || 5 * 1024 * 1024, 5 * 1024 * 1024);
    for (let partIndex = 0; partIndex < session.chunkCount; partIndex += 1) {
      const start = partIndex * chunkSize;
      const end = Math.min(file.size, start + chunkSize);
      const chunk = file.slice(start, end);
      const prepared = await prepareUploadPart(session.sessionId, partIndex);
      
      const uploadResponse = await fetch(prepared.uploadUrl, {
        method: prepared.method || 'PUT',
        headers: prepared.headers,
        body: chunk,
      });

      if (!uploadResponse.ok) throw new Error(`分片 ${partIndex} 上传失败`);

      const etag = uploadResponse.headers.get('etag') ?? '';
      await recordUploadedPart(session.sessionId, partIndex, etag.replaceAll('"', ''), chunk.size);
      
      const currentProgress = Math.round(((partIndex + 1) / session.chunkCount) * 100);
      onProgress(currentProgress);
    }

    await completeUploadSession(session.sessionId);
  }

  clearFinished() {
    for (const [id, task] of this.tasks.entries()) {
      if (task.status === 'SUCCESS' || task.status === 'ERROR' || task.status === 'CANCELLED') {
        this.tasks.delete(id);
      }
    }
    this.notify();
  }
}

export const uploadRuntime = new UploadRuntime();
