import React from 'react';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  FormControlLabel,
  MenuItem,
  Paper,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { Controller, useForm, useFormState } from 'react-hook-form';
import type { RegisterOptions } from 'react-hook-form';
import type { AdminConfigField, AdminConfigValidationRules } from './adminSchemaTypes';
import AdminStatusBadge from './AdminStatusBadge';
import { localizeAdminConfigSource, localizeAdminGroup } from './adminDisplayText';

export type AdminSchemaFormProps = {
  fields: AdminConfigField[];
  readOnly?: boolean;
  onSubmit?: (values: Record<string, unknown>) => void;
};

type FieldGroup = {
  name: string;
  items: Array<{
    label: string;
    fields: AdminConfigField[];
  }>;
};

function buildInitialValues(fields: AdminConfigField[]) {
  return fields.reduce<Record<string, unknown>>((accumulator, field) => {
    if (field.value !== undefined) {
      accumulator[field.key] = field.value;
      return accumulator;
    }

    if (field.defaultValue !== undefined) {
      accumulator[field.key] = field.defaultValue;
      return accumulator;
    }

    if (field.type === 'boolean') {
      accumulator[field.key] = false;
      return accumulator;
    }

    if (field.type === 'multi_select') {
      accumulator[field.key] = [];
      return accumulator;
    }

    accumulator[field.key] = '';
    return accumulator;
  }, {});
}

function normalizeValue(value: unknown): unknown {
  if (value instanceof Date) {
    return value.toISOString();
  }

  if (Array.isArray(value)) {
    return value.map(normalizeValue);
  }

  if (value && typeof value === 'object') {
    return Object.keys(value as Record<string, unknown>)
      .sort()
      .reduce<Record<string, unknown>>((accumulator, key) => {
        accumulator[key] = normalizeValue((value as Record<string, unknown>)[key]);
        return accumulator;
      }, {});
  }

  return value;
}

function createFieldSetSignature(fields: AdminConfigField[]) {
  return JSON.stringify(
    [...fields]
      .map((field) => ({
        key: field.key,
        type: field.type,
      }))
      .sort((left, right) => left.key.localeCompare(right.key)),
  );
}

function createInitialValuesSignature(initialValues: Record<string, unknown>) {
  return JSON.stringify(normalizeValue(initialValues));
}

function groupFields(fields: AdminConfigField[]): FieldGroup[] {
  const grouped: FieldGroup[] = [];

  for (const field of fields) {
    const groupName = localizeAdminGroup(field.group);
    const subgroupName = field.subgroup ?? '通用';

    let group = grouped.find((entry) => entry.name === groupName);
    if (!group) {
      group = {
        name: groupName,
        items: [],
      };
      grouped.push(group);
    }

    let subgroup = group.items.find((entry) => entry.label === subgroupName);
    if (!subgroup) {
      subgroup = {
        label: subgroupName,
        fields: [],
      };
      group.items.push(subgroup);
    }

    subgroup.fields.push(field);
  }

  return grouped;
}

function toValidationRules(field: AdminConfigField): RegisterOptions<Record<string, unknown>, string> {
  const rules: RegisterOptions<Record<string, unknown>, string> = {};
  const validationRules = field.validationRules ?? {};
  const supportedRules: AdminConfigValidationRules = {
    min: typeof validationRules.min === 'number' ? validationRules.min : undefined,
    max: typeof validationRules.max === 'number' ? validationRules.max : undefined,
    minLength: typeof validationRules.minLength === 'number' ? validationRules.minLength : undefined,
    maxLength: typeof validationRules.maxLength === 'number' ? validationRules.maxLength : undefined,
    pattern: typeof validationRules.pattern === 'string' ? validationRules.pattern : undefined,
  };

  if (field.required) {
    rules.required = `请填写${field.title}`;
  }

  if (typeof supportedRules.min === 'number') {
    rules.min = {
      value: supportedRules.min,
      message: `${field.title}不能小于 ${supportedRules.min}`,
    };
  }

  if (typeof supportedRules.max === 'number') {
    rules.max = {
      value: supportedRules.max,
      message: `${field.title}不能大于 ${supportedRules.max}`,
    };
  }

  if (typeof supportedRules.minLength === 'number') {
    rules.minLength = {
      value: supportedRules.minLength,
      message: `${field.title}至少需要 ${supportedRules.minLength} 个字符`,
    };
  }

  if (typeof supportedRules.maxLength === 'number') {
    rules.maxLength = {
      value: supportedRules.maxLength,
      message: `${field.title}最多允许 ${supportedRules.maxLength} 个字符`,
    };
  }

  if (typeof supportedRules.pattern === 'string' && supportedRules.pattern.length > 0) {
    rules.pattern = {
      value: new RegExp(supportedRules.pattern),
      message: `${field.title}格式不正确`,
    };
  }

  return rules;
}

function getFieldHelperText(field: AdminConfigField, readOnly: boolean) {
  const messages: string[] = [];

  if (field.description) {
    messages.push(field.description);
  }
  if (field.restartRequired) {
    messages.push('修改后需要重启生效。');
  }
  if (!field.editable || readOnly) {
    messages.push('当前为只读字段。');
  }
  messages.push(`来源：${localizeAdminConfigSource(field.source)}`);

  return messages.join(' ');
}

const supportedFieldTypes = new Set<AdminConfigField['type']>([
  'string',
  'number',
  'boolean',
  'select',
  'multi_select',
  'textarea',
]);

const AdminSchemaForm: React.FC<AdminSchemaFormProps> = ({ fields, readOnly = false, onSubmit }) => {
  const initialValues = React.useMemo(() => buildInitialValues(fields), [fields]);
  const fieldSetSignature = React.useMemo(() => createFieldSetSignature(fields), [fields]);
  const initialValuesSignature = React.useMemo(() => createInitialValuesSignature(initialValues), [initialValues]);
  const form = useForm<Record<string, unknown>>({
    defaultValues: initialValues,
  });
  const fieldNames = React.useMemo(() => fields.map((field) => field.key), [fields]);
  const { dirtyFields } = useFormState({
    control: form.control,
    name: fieldNames,
  });
  void dirtyFields;
  const baselineRef = React.useRef<{ fieldSetSignature: string; initialValuesSignature: string } | null>(null);

  React.useEffect(() => {
    const previousBaseline = baselineRef.current;
    const nextBaseline = {
      fieldSetSignature,
      initialValuesSignature,
    };

    if (!previousBaseline) {
      baselineRef.current = nextBaseline;
      return;
    }

    const fieldSetChanged = previousBaseline.fieldSetSignature !== fieldSetSignature;
    const initialValuesChanged = previousBaseline.initialValuesSignature !== initialValuesSignature;

    if (!fieldSetChanged && !initialValuesChanged) {
      return;
    }

    form.reset(initialValues, fieldSetChanged ? undefined : { keepDirtyValues: true });
    baselineRef.current = nextBaseline;
  }, [fieldSetSignature, form, initialValues, initialValuesSignature]);

  const groupedFields = groupFields(fields);
  const isFormReadOnly = readOnly || !onSubmit;

  return (
    <Box
      component="form"
      onSubmit={onSubmit ? form.handleSubmit((values) => onSubmit(values)) : undefined}
      sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
    >
      {groupedFields.map((group) => (
        <Paper
          key={group.name}
          elevation={0}
          sx={{
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 3,
            px: { xs: 2, md: 2.5 },
            py: 2,
          }}
        >
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6" sx={{ fontSize: '1rem', fontWeight: 700 }}>
                {group.name}
              </Typography>
            </Box>

            {group.items.map((subgroup) => (
              <Stack key={`${group.name}-${subgroup.label}`} spacing={2}>
                {subgroup.label !== '通用' ? (
                  <Typography variant="subtitle2" color="text.secondary" sx={{ fontWeight: 700 }}>
                    {subgroup.label}
                  </Typography>
                ) : null}

                {subgroup.fields.map((field) => {
                  const disabled = isFormReadOnly || !field.editable;
                  const rules = toValidationRules(field);
                  const helperText = getFieldHelperText(field, isFormReadOnly);
                  const isSupported = supportedFieldTypes.has(field.type);

                  if (!isSupported) {
                    return (
                      <Box
                        key={field.key}
                        sx={{
                          borderRadius: 2,
                          border: '1px dashed',
                          borderColor: 'warning.main',
                          px: 2,
                          py: 1.5,
                          bgcolor: 'action.hover',
                        }}
                      >
                        <Stack spacing={1}>
                          <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                              {field.title}
                            </Typography>
                            <AdminStatusBadge label={`暂不支持：${field.type}`} tone="warning" />
                            {field.restartRequired ? <AdminStatusBadge label="需要重启" tone="info" /> : null}
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            {field.description ?? '当前表单暂不支持编辑该字段类型。'}
                          </Typography>
                          <Alert severity="warning" sx={{ borderRadius: 2 }}>
                            当前以只读方式展示，因为暂不支持 `{field.type}` 类型。
                          </Alert>
                          <TextField
                            fullWidth
                            size="small"
                            value={String(field.value ?? field.defaultValue ?? '')}
                            label={field.title}
                            helperText={helperText}
                            InputProps={{ readOnly: true }}
                          />
                        </Stack>
                      </Box>
                    );
                  }

                  return (
                    <Stack key={field.key} spacing={1}>
                      <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                          {field.title}
                        </Typography>
                        {field.required ? <AdminStatusBadge label="必填" tone="info" /> : null}
                        {field.sensitive ? <AdminStatusBadge label="敏感" tone="warning" /> : null}
                        {field.restartRequired ? <AdminStatusBadge label="需要重启" tone="neutral" /> : null}
                        {!field.editable ? <AdminStatusBadge label="已锁定" tone="neutral" /> : null}
                      </Stack>

                      <Controller
                        name={field.key}
                        control={form.control}
                        rules={rules}
                        render={({ field: controllerField, fieldState }) => {
                          if (field.type === 'boolean') {
                            return (
                              <Box>
                                <FormControlLabel
                                  control={
                                    <Switch
                                      checked={Boolean(controllerField.value)}
                                      onChange={(_, checked) => controllerField.onChange(checked)}
                                      disabled={disabled}
                                    />
                                  }
                                  label={field.description ?? field.title}
                                />
                                <Typography
                                  variant="caption"
                                  color={fieldState.error ? 'error' : 'text.secondary'}
                                  sx={{ display: 'block', mt: 0.5 }}
                                >
                                  {fieldState.error?.message ?? helperText}
                                </Typography>
                              </Box>
                            );
                          }

                          if (field.type === 'select') {
                            return (
                              <TextField
                                select
                                fullWidth
                                size="small"
                                label={field.title}
                                value={String(controllerField.value ?? '')}
                                onChange={controllerField.onChange}
                                disabled={disabled}
                                error={fieldState.error != null}
                                helperText={fieldState.error?.message ?? helperText}
                              >
                                {(field.options ?? []).map((option) => (
                                  <MenuItem key={option.value} value={option.value}>
                                    {option.label}
                                  </MenuItem>
                                ))}
                              </TextField>
                            );
                          }

                          if (field.type === 'multi_select') {
                            const selectedValues = Array.isArray(controllerField.value)
                              ? controllerField.value.map((value) => String(value))
                              : [];

                            return (
                              <Autocomplete
                                multiple
                                options={field.options ?? []}
                                value={(field.options ?? []).filter((option) => selectedValues.includes(option.value))}
                                onChange={(_, nextOptions) => {
                                  controllerField.onChange(nextOptions.map((option) => option.value));
                                }}
                                disableCloseOnSelect
                                disabled={disabled}
                                getOptionLabel={(option) => option.label}
                                isOptionEqualToValue={(option, value) => option.value === value.value}
                                renderInput={(params) => (
                                  <TextField
                                    {...params}
                                    fullWidth
                                    size="small"
                                    label={field.title}
                                    error={fieldState.error != null}
                                    helperText={fieldState.error?.message ?? helperText}
                                  />
                                )}
                              />
                            );
                          }

                          if (field.type === 'textarea') {
                            return (
                              <TextField
                                fullWidth
                                multiline
                                minRows={4}
                                size="small"
                                label={field.title}
                                value={String(controllerField.value ?? '')}
                                onChange={controllerField.onChange}
                                disabled={disabled}
                                error={fieldState.error != null}
                                helperText={fieldState.error?.message ?? helperText}
                              />
                            );
                          }

                          if (field.type === 'number') {
                            return (
                              <TextField
                                fullWidth
                                size="small"
                                type="number"
                                label={field.title}
                                value={controllerField.value === '' ? '' : Number(controllerField.value ?? 0)}
                                onChange={(event) => {
                                  const nextValue = event.target.value;
                                  controllerField.onChange(nextValue === '' ? '' : Number(nextValue));
                                }}
                                disabled={disabled}
                                error={fieldState.error != null}
                                helperText={fieldState.error?.message ?? helperText}
                              />
                            );
                          }

                          return (
                            <TextField
                              fullWidth
                              size="small"
                              type={field.sensitive ? 'password' : 'text'}
                              label={field.title}
                              value={String(controllerField.value ?? '')}
                              onChange={controllerField.onChange}
                              disabled={disabled}
                              error={fieldState.error != null}
                              helperText={fieldState.error?.message ?? helperText}
                              autoComplete={field.sensitive ? 'new-password' : undefined}
                            />
                          );
                        }}
                      />
                    </Stack>
                  );
                })}
              </Stack>
            ))}
          </Stack>
        </Paper>
      ))}

      {onSubmit ? (
        <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button type="submit" variant="contained" disabled={isFormReadOnly}>
            保存更改
          </Button>
        </Box>
      ) : null}
    </Box>
  );
};

export default AdminSchemaForm;
