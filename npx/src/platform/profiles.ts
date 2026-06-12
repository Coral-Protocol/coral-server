import fs from 'node:fs';
import path from 'node:path';
import { configManager, constants } from './legacy.js';

export function listConfigProfiles(): string[] {
  if (!fs.existsSync(constants.CONFIG_PROFILES_DIR)) {
    return [];
  }

  return fs.readdirSync(constants.CONFIG_PROFILES_DIR, { withFileTypes: true })
    .filter(entry => entry.isDirectory())
    .map(entry => entry.name)
    .filter(name => fs.existsSync(configManager.getConfigProfilePath(name)))
    .sort((a, b) => a.localeCompare(b));
}

export function normalizeProfileName(value: string): string {
  return value.trim().replace(/[^a-zA-Z0-9._-]/g, '-').replace(/-+/g, '-');
}

export function ensureProfileConfig(profileName: string): string {
  configManager.ensureConfigProfileDir(profileName);
  const profilePath = configManager.getConfigProfilePath(profileName);

  if (!fs.existsSync(profilePath)) {
    fs.writeFileSync(profilePath, configManager.generateDefaultConfig());
  }

  return profilePath;
}

export function profileDir(profileName: string): string {
  return path.join(constants.CONFIG_PROFILES_DIR, profileName);
}
