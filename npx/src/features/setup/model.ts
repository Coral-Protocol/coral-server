export function maskValue(value: string): string {
  if (!value) return 'Not set';
  if (value.length <= 8) return '*'.repeat(value.length);
  return `${value.slice(0, 4)}${'*'.repeat(Math.min(12, value.length - 8))}${value.slice(-4)}`;
}

export type SetupState = {
  authKey: string;
  cloudApiKey: string;
};
