import type { AdminConfigField, AdminConfigOption } from './adminSchemaTypes';

const configTextByKey: Record<string, Pick<AdminConfigField, 'title' | 'description'>> = {
  'registration.inviteCodeRequired': {
    title: '邀请码注册',
    description: '新用户注册是否必须填写当前邀请码。',
  },
  'registration.currentInviteCode': {
    title: '当前邀请码',
    description: '当前注册流程使用的邀请码。',
  },
  'registration.managementRoles': {
    title: '注册管理角色',
    description: '允许管理注册控制项的后台角色。',
  },
  'transfer.offlineTransferStorageLimitBytes': {
    title: '离线传输容量限制',
    description: '离线传输可占用的最大存储容量。',
  },
  'media.metadataExtractionEnabled': {
    title: '元数据提取',
    description: '是否启用媒体元数据提取。',
  },
  'media.thumbnailGenerationEnabled': {
    title: '缩略图生成',
    description: '是否启用缩略图生成。',
  },
  'media.videoPosterEnabled': {
    title: '视频封面生成',
    description: '是否启用视频封面生成。',
  },
  'queue.backend': {
    title: '队列后端',
    description: '媒体元数据调度使用的队列后端。',
  },
  'queue.mediaMetadataFixedDelayMs': {
    title: '媒体任务固定间隔',
    description: '两次媒体队列运行之间的固定间隔，单位毫秒。',
  },
  'queue.mediaMetadataInitialDelayMs': {
    title: '媒体任务启动延迟',
    description: '媒体队列启动前的初始延迟，单位毫秒。',
  },
  'server.storageProvider': {
    title: '存储提供方',
    description: '服务端运行时当前使用的存储提供方。',
  },
  'server.redisEnabled': {
    title: 'Redis 状态',
    description: '服务端是否启用 Redis 支撑的能力。',
  },
};

const subgroupLabels: Record<string, string> = {
  General: '通用',
  offlineTransfer: '离线传输',
  processing: '处理配置',
  mediaMetadata: '媒体元数据',
};

const groupLabels: Record<string, string> = {
  registration: '注册',
  transfer: '离线传输',
  media: '媒体处理',
  queue: '队列',
  server: '服务',
};

const configSourceLabels: Record<AdminConfigField['source'], string> = {
  runtime: '运行时',
  environment: '环境变量',
  database: '数据库',
  computed: '计算值',
};

const exactValueLabels: Record<string, string> = {
  ADMIN: '管理员',
  MODERATOR: '协管员',
  USER: '普通用户',
  LOCAL: '本地存储',
  S3_COMPATIBLE: 'S3 兼容存储',
  NONE: '无需凭证',
  PROXY: '代理上传',
  DIRECT_SINGLE: '直传单文件',
  DIRECT_MULTIPART: '直传分片',
  STORAGE_POLICY: '存储策略',
  USER_ACCOUNT: '用户账号',
  FILE: '文件',
  SHARE: '分享',
};

const lowercaseValueLabels: Record<string, string> = {
  'in-memory': '内存队列',
  redis: 'Redis',
  local: '本地存储',
  s3: 'S3 兼容存储',
};

const taskStatusLabels: Record<string, string> = {
  SUCCESS: '成功',
  DONE: '完成',
  FINISHED: '已结束',
  COMPLETE: '已完成',
  FAILED: '失败',
  ERROR: '错误',
  RUNNING: '运行中',
  PROCESSING: '处理中',
  LEASED: '已领取',
  QUEUED: '排队中',
  PENDING: '等待中',
  WAITING: '等待中',
  CANCELED: '已取消',
  CANCELLED: '已取消',
};

const taskTypeLabels: Record<string, string> = {
  MEDIA_METADATA: '媒体元数据',
  SEARCH_INDEX_REBUILD: '搜索索引重建',
  OFFLINE_TRANSFER: '离线传输',
  ARCHIVE_EXTRACT: '压缩包解压',
  ARCHIVE_CREATE: '压缩包创建',
  FILE_IMPORT: '文件导入',
};

const auditActionLabels: Record<string, string> = {
  CONFIG_UPDATED: '配置更新',
  CONFIG_ROLLBACK: '配置回滚',
  INVITE_CODE_ROTATED: '邀请码重新生成',
  INVITE_CODE_UPDATED: '邀请码更新',
  STORAGE_POLICY_CREATED: '创建存储策略',
  STORAGE_POLICY_UPDATED: '更新存储策略',
  STORAGE_POLICY_STATUS_UPDATED: '更新存储策略状态',
  USER_ROLE_UPDATED: '更新用户角色',
  USER_STATUS_UPDATED: '更新用户状态',
  USER_PASSWORD_UPDATED: '更新用户密码',
  USER_PASSWORD_RESET: '重置用户密码',
  USER_QUOTA_UPDATED: '更新用户容量',
  FILE_DELETED: '删除文件',
  SHARE_DELETED: '取消分享',
};

export function localizeAdminConfigField(field: AdminConfigField): AdminConfigField {
  const text = configTextByKey[field.key];
  return {
    ...field,
    title: text?.title ?? field.title,
    description: text?.description ?? field.description,
    subgroup: field.subgroup ? localizeAdminSubgroup(field.subgroup) : field.subgroup,
    options: field.options?.map(localizeAdminConfigOption),
  };
}

export function localizeAdminConfigOption(option: AdminConfigOption): AdminConfigOption {
  return {
    ...option,
    label: localizeAdminValue(option.value, option.label),
  };
}

export function localizeAdminSubgroup(value: string) {
  return subgroupLabels[value] ?? value;
}

export function localizeAdminGroup(value: string) {
  return groupLabels[value] ?? value;
}

export function localizeAdminConfigSource(value: AdminConfigField['source']) {
  return configSourceLabels[value] ?? value;
}

export function localizeStoragePolicyType(value: string) {
  return localizeAdminValue(value);
}

export function localizeUploadMode(value: string) {
  return exactValueLabels[value] ?? value;
}

export function localizeTaskType(value: string) {
  return taskTypeLabels[value] ?? localizeAdminValue(value);
}

export function localizeTaskStatus(value: string) {
  return taskStatusLabels[value] ?? localizeAdminValue(value);
}

export function localizeAuditAction(value: string) {
  return auditActionLabels[value] ?? localizeAdminValue(value);
}

export function localizeAuditTarget(value: string) {
  return exactValueLabels[value] ?? localizeAdminValue(value);
}

export function localizeAdminValue(value: string, fallback = value) {
  return exactValueLabels[value] ?? lowercaseValueLabels[value.toLowerCase()] ?? fallback;
}
