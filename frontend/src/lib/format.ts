export function formatBytes(bytes: number | null | undefined) {
  if (bytes == null || Number.isNaN(bytes)) {
    return '-';
  }

  if (bytes === 0) {
    return '0 B';
  }

  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / 1024 ** index;
  const digits = value >= 10 || index === 0 ? 0 : 1;
  return `${value.toFixed(digits)} ${units[index]}`;
}

export function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '-';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function formatDate(value: string | null | undefined) {
  if (!value) {
    return '-';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date);
}

export function formatTimeUntil(value: string | null | undefined, now = new Date()) {
  if (!value) {
    return '-';
  }

  const target = new Date(value);
  if (Number.isNaN(target.getTime())) {
    return value;
  }

  const diffMs = target.getTime() - now.getTime();
  if (diffMs <= 0) {
    return '已到期';
  }

  const minuteMs = 60 * 1000;
  const hourMs = 60 * minuteMs;
  const dayMs = 24 * hourMs;

  if (diffMs < hourMs) {
    const minutes = Math.max(1, Math.ceil(diffMs / minuteMs));
    return `${minutes} 分钟后`;
  }

  if (diffMs < dayMs) {
    const hours = Math.ceil(diffMs / hourMs);
    return `${hours} 小时后`;
  }

  const days = Math.ceil(diffMs / dayMs);
  return `${days} 天后`;
}

export function formatPercent(value: number, total: number) {
  if (!Number.isFinite(value) || !Number.isFinite(total) || total <= 0) {
    return '0%';
  }
  return `${Math.min(100, Math.max(0, Math.round((value / total) * 100)))}%`;
}
