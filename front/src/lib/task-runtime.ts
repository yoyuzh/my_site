import { getTasks, type BackgroundTask } from './background-tasks';

class TaskRuntime {
  private activeTasks: BackgroundTask[] = [];
  private listeners: Set<() => void> = new Set();
  private pollInterval: number | null = null;

  getActiveTasks() {
    return this.activeTasks;
  }

  subscribe(listener: () => void) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify() {
    this.listeners.forEach((l) => l());
  }

  startPolling() {
    if (this.pollInterval) return;
    
    const poll = async () => {
      try {
        const result = await getTasks(0, 20);
        // 我们只关注活跃的任务（未完成的任务）
        const nextActive = result.items.filter(t => !t.finishedAt);
        
        // 如果任务状态发生变化，通知订阅者
        if (JSON.stringify(nextActive) !== JSON.stringify(this.activeTasks)) {
          this.activeTasks = nextActive;
          this.notify();
        }
      } catch (err) {
        console.error('任务轮询失败', err);
      }
    };

    void poll();
    this.pollInterval = window.setInterval(() => { void poll(); }, 5000);
  }

  stopPolling() {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
  }

  // 立即触发一次强制更新
  async refresh() {
    try {
      const result = await getTasks(0, 20);
      this.activeTasks = result.items.filter(t => !t.finishedAt);
      this.notify();
    } catch (err) {
      console.error('任务刷新失败', err);
    }
  }
}

export const taskRuntime = new TaskRuntime();
