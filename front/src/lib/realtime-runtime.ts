import { getApiBaseUrl } from './api';
import { getSession } from './session';
import { filesCache } from './files-cache';
import { taskRuntime } from './task-runtime';

export interface RealtimeEvent {
  type: string;
  payload: any;
  timestamp: string;
}

class RealtimeRuntime {
  private eventSource: EventSource | null = null;
  private listeners: Set<(event: RealtimeEvent) => void> = new Set();
  private reconnectTimer: number | null = null;

  start() {
    if (this.eventSource) return;

    const session = getSession();
    if (!session) return;

    // SSE 不支持直接传递 Bearer 头，通常使用查询参数或 Cookie
    // 这里假设后端支持 token 查询参数，或者后端已通过 Cookie 鉴权
    const url = new URL(`${getApiBaseUrl()}/v2/files/events`, window.location.origin);
    url.searchParams.set('token', session.accessToken);

    this.eventSource = new EventSource(url.toString());

    this.eventSource.onmessage = (e) => {
      try {
        const event = JSON.parse(e.data) as RealtimeEvent;
        this.handleEvent(event);
      } catch (err) {
        console.error('实时事件解析失败', err);
      }
    };

    this.eventSource.onerror = () => {
      console.warn('实时连接异常，尝试重连...');
      this.stop();
      this.reconnectTimer = window.setTimeout(() => this.start(), 3000);
    };
  }

  stop() {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private handleEvent(event: RealtimeEvent) {
    // 基础事件分发
    switch (event.type) {
      case 'FILE_CREATED':
      case 'FILE_UPDATED':
      case 'FILE_DELETED':
      case 'FILE_MOVED':
        // 简单暴力失效：前缀匹配或全量标记
        // 实际上可以根据 payload 中的路径精准失效
        if (event.payload?.path) {
           filesCache.invalidate(event.payload.path);
        } else {
           filesCache.invalidate('/');
        }
        break;
      case 'TASK_UPDATED':
      case 'TASK_FINISHED':
        // 触发任务运行时立即刷新
        void taskRuntime.refresh();
        break;
    }

    this.listeners.forEach(l => l(event));
  }

  subscribe(listener: (event: RealtimeEvent) => void) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }
}

export const realtimeRuntime = new RealtimeRuntime();
