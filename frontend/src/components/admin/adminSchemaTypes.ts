export type AdminConfigFieldType =
  | 'string'
  | 'number'
  | 'boolean'
  | 'select'
  | 'multi_select'
  | 'password'
  | 'textarea'
  | 'json'
  | 'url'
  | 'path'
  | 'size'
  | 'duration'
  | 'cron';

export type AdminConfigOption = {
  label: string;
  value: string;
};

export type AdminConfigField = {
  key: string;
  group: string;
  subgroup?: string | null;
  title: string;
  description?: string | null;
  type: AdminConfigFieldType;
  defaultValue?: unknown;
  value?: unknown;
  options?: AdminConfigOption[];
  required: boolean;
  editable: boolean;
  sensitive: boolean;
  restartRequired: boolean;
  validationRules?: Record<string, unknown>;
  permissionCode?: string | null;
  source: 'runtime' | 'environment' | 'database' | 'computed';
};
