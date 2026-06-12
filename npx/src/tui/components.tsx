import React from 'react';
import { Box, Text } from 'ink';
import SelectInput from 'ink-select-input';
import { theme } from './theme.js';
import { ellipsizeMiddle, type LayoutMetrics } from './layout.js';

export type MenuItem<T extends string = string> = {
  label: string;
  value: T;
};

export function Divider({ title, width }: { title?: string; width: number }) {
  const label = title ? ` ${title} ` : '';
  const available = Math.max(width - label.length, 8);
  const left = '-'.repeat(title ? Math.floor(available * 0.32) : available);
  const right = title ? '-'.repeat(Math.max(available - left.length, 1)) : '';

  return (
    <Box>
      <Text color={theme.border}>{left}</Text>
      {title ? <Text color={theme.brand}>{label}</Text> : null}
      {right ? <Text color={theme.border}>{right}</Text> : null}
    </Box>
  );
}

export function CliEquivalent({ command }: { command: string }) {
  return (
    <Box flexDirection="column" marginTop={1}>
      <Text color={theme.muted}>CLI equivalent</Text>
      <Text color={theme.brandSoft}>{command}</Text>
    </Box>
  );
}

export function Menu<T extends string>({
  items,
  onSelect,
  limit
}: {
  items: Array<MenuItem<T>>;
  onSelect: (value: T) => void;
  limit?: number;
}) {
  const Item = ({ isSelected, label }: { isSelected?: boolean; label: string }) => {
    const marker = label.slice(0, 3);
    const rest = label.slice(3).trimStart();
    const markerColor =
      marker === '(!)' ? theme.warning :
      marker === '(*)' ? theme.success :
      marker === '(+)' ? theme.brand :
      marker === '(>)' ? theme.info :
      marker === '(<)' ? theme.muted :
      theme.brandSoft;

    return (
      <Box>
        <Text color={markerColor}>{marker}</Text>
        <Text color={isSelected ? theme.brandSoft : undefined}> {rest}</Text>
      </Box>
    );
  };

  const Indicator = ({ isSelected }: { isSelected?: boolean }) => (
    <Text color={isSelected ? theme.brand : theme.muted}>{isSelected ? '> ' : '  '}</Text>
  );

  return (
    <SelectInput
      items={items}
      limit={limit ?? 10}
      indicatorComponent={Indicator}
      itemComponent={Item}
      onSelect={item => onSelect(item.value)}
    />
  );
}

export function FieldRow({
  label,
  value,
  labelWidth = 16,
  muted = false
}: {
  label: string;
  value: string;
  labelWidth?: number;
  muted?: boolean;
}) {
  return (
    <Box flexDirection="row">
      <Box width={labelWidth}>
        <Text color={muted ? theme.muted : undefined}>{label}</Text>
      </Box>
      <Text color={value === 'Not set' ? theme.warning : theme.success}>{value}</Text>
    </Box>
  );
}

export function Panel({
  title,
  children,
  width
}: {
  title?: string;
  children: React.ReactNode;
  width?: number;
}) {
  return (
    <Box
      borderStyle="round"
      borderColor={theme.border}
      flexDirection="column"
      paddingX={1}
      paddingY={1}
      width={width}
    >
      {title ? <Text bold color={theme.brand}>{title}</Text> : null}
      {children}
    </Box>
  );
}

export function AppHeader({
  profileName,
  profilePath,
  metrics
}: {
  profileName: string;
  profilePath: string;
  metrics: LayoutMetrics;
}) {
  const maxPath = Math.max(metrics.width - 16, 24);

  return (
    <Box flexDirection="column">
      <Text bold color={theme.brand}>CoralOS Developer Console</Text>
      <Text color={theme.muted}>
        Profile <Text color={theme.brandSoft}>{profileName}</Text>
        {'  '}
        <Text color={theme.muted}>{ellipsizeMiddle(profilePath, maxPath)}</Text>
      </Text>
      <Divider title="profile" width={metrics.width - 2} />
    </Box>
  );
}

export function Footer({ metrics, text }: { metrics: LayoutMetrics; text: string }) {
  return (
    <Box flexDirection="column" marginTop={1}>
      <Divider width={metrics.width - 2} />
      <Text color={theme.muted}>{text}</Text>
    </Box>
  );
}
