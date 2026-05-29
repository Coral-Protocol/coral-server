export const theme = {
  // From @coral-os/component-library theme.css:
  // --brand-primary: oklch(0.6837 0.212 40.59)
  // --brand-secondary: oklch(0.7737 0.1734 65.01)
  brand: '#f97316',
  brandSoft: '#fbbf24',
  text: 'white',
  muted: 'gray',
  border: '#7c2d12',
  success: 'green',
  warning: 'yellow',
  danger: 'red',
  info: 'cyan'
} as const;

export type ThemeColor = (typeof theme)[keyof typeof theme];
