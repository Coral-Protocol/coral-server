import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);

export type PackageInfo = {
  name: string;
  version: string;
};

export type WizardConfigResult = {
  action: 'save' | 'cancel';
  authKey?: string | null;
  cloudApiKey?: string | null;
};

export type ConfigManager = {
  getConfigProfilePath(profileName: string): string;
  ensureConfigProfileDir(profileName: string): void;
  generateDefaultConfig(): string;
  buildConfigFromWizardResults(
    providers: Array<unknown>,
    authKey?: string | null,
    coralApiKey?: string | null
  ): string;
};

export const configManager = require('../../lib/config-manager') as ConfigManager;

export const constants = require('../../lib/constants') as {
  pkg: PackageInfo;
  IS_WINDOWS: boolean;
  CONFIG_PROFILES_DIR: string;
};
