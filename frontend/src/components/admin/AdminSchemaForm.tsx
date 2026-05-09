import React from 'react';
import {
  Alert,
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
import { Controller, useForm } from 'react-hook-form';
import type { RegisterOptions } from 'react-hook-form';
import type { AdminConfigField } from './adminSchemaTypes';
import AdminStatusBadge from './AdminStatusBadge';

type AdminSchemaFormProps = {
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

    accumulator[field.key] = '';
    return accumulator;
  }, {});
}

function groupFields(fields: AdminConfigField[]): FieldGroup[] {
  const grouped: FieldGroup[] = [];

  for (const field of fields) {
    const groupName = field.group;
    const subgroupName = field.subgroup ?? 'General';

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

  if (field.required) {
    rules.required = `${field.title} is required.`;
  }

  if (typeof validationRules.min === 'number') {
    rules.min = {
      value: validationRules.min,
      message: `${field.title} must be at least ${validationRules.min}.`,
    };
  }

  if (typeof validationRules.max === 'number') {
    rules.max = {
      value: validationRules.max,
      message: `${field.title} must be at most ${validationRules.max}.`,
    };
  }

  if (typeof validationRules.minLength === 'number') {
    rules.minLength = {
      value: validationRules.minLength,
      message: `${field.title} must be at least ${validationRules.minLength} characters.`,
    };
  }

  if (typeof validationRules.maxLength === 'number') {
    rules.maxLength = {
      value: validationRules.maxLength,
      message: `${field.title} must be at most ${validationRules.maxLength} characters.`,
    };
  }

  if (typeof validationRules.pattern === 'string' && validationRules.pattern.length > 0) {
    rules.pattern = {
      value: new RegExp(validationRules.pattern),
      message: `${field.title} format is invalid.`,
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
    messages.push('Restart required after change.');
  }
  if (!field.editable || readOnly) {
    messages.push('Read-only field.');
  }
  if (field.permissionCode) {
    messages.push(`Permission: ${field.permissionCode}`);
  }
  messages.push(`Source: ${field.source}`);

  return messages.join(' ');
}

const supportedFieldTypes = new Set<AdminConfigField['type']>([
  'string',
  'number',
  'boolean',
  'select',
  'textarea',
]);

const AdminSchemaForm: React.FC<AdminSchemaFormProps> = ({ fields, readOnly = false, onSubmit }) => {
  const form = useForm<Record<string, unknown>>({
    defaultValues: buildInitialValues(fields),
  });

  React.useEffect(() => {
    form.reset(buildInitialValues(fields));
  }, [fields, form]);

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
                {subgroup.label !== 'General' ? (
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
                      <Paper
                        key={field.key}
                        variant="outlined"
                        sx={{
                          borderRadius: 2,
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
                            <AdminStatusBadge label={`Unsupported: ${field.type}`} tone="warning" />
                            {field.restartRequired ? <AdminStatusBadge label="Restart required" tone="info" /> : null}
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            {field.description ?? 'This field type is not editable in the current admin form.'}
                          </Typography>
                          <Alert severity="warning" sx={{ borderRadius: 2 }}>
                            This field is shown in read-only mode because `{field.type}` is not supported yet.
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
                      </Paper>
                    );
                  }

                  return (
                    <Stack key={field.key} spacing={1}>
                      <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                          {field.title}
                        </Typography>
                        {field.required ? <AdminStatusBadge label="Required" tone="info" /> : null}
                        {field.sensitive ? <AdminStatusBadge label="Sensitive" tone="warning" /> : null}
                        {field.restartRequired ? <AdminStatusBadge label="Restart required" tone="neutral" /> : null}
                        {!field.editable ? <AdminStatusBadge label="Locked" tone="neutral" /> : null}
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
            Save changes
          </Button>
        </Box>
      ) : null}
    </Box>
  );
};

export default AdminSchemaForm;
