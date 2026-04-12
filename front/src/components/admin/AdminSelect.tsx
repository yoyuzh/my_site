import * as Select from '@radix-ui/react-select';
import { Check, ChevronDown, ChevronUp } from 'lucide-react';
import { Children, isValidElement, type ReactNode } from 'react';
import { cn } from '@/src/lib/utils';

type AdminSelectChangeEvent = {
  target: {
    value: string;
  };
};

type ParsedOption = {
  value: string;
  label: string;
  disabled: boolean;
};

type WidthPreset = 'field' | 'filter' | 'compact' | 'fit';

type AdminSelectProps = {
  value: string | number;
  onChange: (event: AdminSelectChangeEvent) => void;
  children: ReactNode;
  className?: string;
  disabled?: boolean;
  width?: WidthPreset;
};

const EMPTY_VALUE_SENTINEL = '__ADMIN_SELECT_EMPTY__';

function stringifyNode(node: ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') {
    return String(node);
  }

  if (Array.isArray(node)) {
    return node.map((item) => stringifyNode(item)).join('').trim();
  }

  if (isValidElement<{ children?: ReactNode }>(node)) {
    return stringifyNode(node.props.children);
  }

  return '';
}

function parseOptions(children: ReactNode): { options: ParsedOption[]; hasEmptyOption: boolean } {
  const options: ParsedOption[] = [];
  let hasEmptyOption = false;

  Children.forEach(children, (child) => {
    if (!isValidElement<{ value?: string | number; disabled?: boolean; children?: ReactNode }>(child)) {
      return;
    }

    const rawValue = child.props.value;
    const value = rawValue == null ? '' : String(rawValue);
    if (value === '') {
      hasEmptyOption = true;
    }

    options.push({
      value,
      label: stringifyNode(child.props.children),
      disabled: Boolean(child.props.disabled),
    });
  });

  return { options, hasEmptyOption };
}

export function AdminSelect({
  value,
  onChange,
  children,
  className,
  disabled = false,
  width = 'field',
}: AdminSelectProps) {
  const { options, hasEmptyOption } = parseOptions(children);
  const normalizedValue = String(value ?? '');
  const resolvedValue = normalizedValue === '' && hasEmptyOption ? EMPTY_VALUE_SENTINEL : normalizedValue;
  const selectedOption = options.find((option) => option.value === normalizedValue);
  const placeholder = options.find((option) => option.value === '')?.label ?? '请选择';

  return (
    <Select.Root
      value={resolvedValue}
      disabled={disabled}
      onValueChange={(nextValue) => {
        const resolvedNextValue = nextValue === EMPTY_VALUE_SENTINEL ? '' : nextValue;
        onChange({
          target: {
            value: resolvedNextValue,
          },
        });
      }}
    >
      <Select.Trigger
        className={cn(
          'inline-flex w-full items-center justify-between gap-3 rounded-lg border border-white/10 bg-white/10 px-4 py-4 text-left text-sm font-bold text-gray-900 outline-none transition-all data-[placeholder]:text-gray-500 dark:text-gray-100 dark:data-[placeholder]:text-gray-400',
          'focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10 disabled:cursor-not-allowed disabled:opacity-60',
          width === 'filter' && 'min-h-[3.25rem] text-[11px] font-black uppercase tracking-widest',
          width === 'compact' && 'min-h-0 rounded-none border-0 bg-transparent p-0 pr-8 shadow-none focus:ring-0 focus:border-transparent text-[11px] font-black uppercase tracking-widest',
          width === 'fit' && 'w-auto',
          className,
        )}
      >
        <Select.Value placeholder={placeholder}>
          {selectedOption?.label}
        </Select.Value>
        <Select.Icon asChild>
          <ChevronDown className="h-4 w-4 shrink-0 opacity-50" />
        </Select.Icon>
      </Select.Trigger>

      <Select.Portal>
        <Select.Content
          position="popper"
          sideOffset={8}
          className="z-[120] overflow-hidden rounded-2xl border border-white/10 bg-gray-950/95 text-gray-100 shadow-[0_24px_80px_rgba(0,0,0,0.45)] backdrop-blur-2xl"
        >
          <Select.ScrollUpButton className="flex h-8 items-center justify-center text-gray-400">
            <ChevronUp className="h-4 w-4" />
          </Select.ScrollUpButton>
          <Select.Viewport className="min-w-[var(--radix-select-trigger-width)] p-2">
            {options.map((option) => {
              const optionValue = option.value === '' ? EMPTY_VALUE_SENTINEL : option.value;

              return (
                <Select.Item
                  key={`${optionValue}-${option.label}`}
                  value={optionValue}
                  disabled={option.disabled}
                  className={cn(
                    'relative flex cursor-default select-none items-center rounded-xl py-2.5 pl-3 pr-9 text-sm font-bold text-gray-100 outline-none transition-colors',
                    'data-[highlighted]:bg-blue-500/15 data-[highlighted]:text-blue-300 data-[disabled]:pointer-events-none data-[disabled]:opacity-35',
                  )}
                >
                  <Select.ItemText>{option.label}</Select.ItemText>
                  <Select.ItemIndicator className="absolute right-3 inline-flex items-center justify-center text-blue-300">
                    <Check className="h-4 w-4" />
                  </Select.ItemIndicator>
                </Select.Item>
              );
            })}
          </Select.Viewport>
          <Select.ScrollDownButton className="flex h-8 items-center justify-center text-gray-400">
            <ChevronDown className="h-4 w-4" />
          </Select.ScrollDownButton>
        </Select.Content>
      </Select.Portal>
    </Select.Root>
  );
}
