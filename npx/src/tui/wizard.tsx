import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import React from 'react';
import { render } from 'ink';
import { WizardApp, FirstRunApp, ProfilePickerApp, type WizardResult } from './app.js';
import { configManager, constants } from '../platform/legacy.js';
import { ensureProfileConfig, listConfigProfiles } from '../platform/profiles.js';

function runInkApp<T>(elementFactory: (finish: (value: T) => void) => React.ReactElement): Promise<T | null> {
  if (!process.stdin.isTTY || !process.stdout.isTTY) {
    return Promise.reject(new Error('The setup wizard requires an interactive terminal.'));
  }

  return new Promise((resolve, reject) => {
    let settled = false;
    const finish = (value: T) => {
      settled = true;
      resolve(value);
    };

    const instance = render(elementFactory(finish), {
      exitOnCtrlC: true
    });

    instance.waitUntilExit().then(() => {
      if (!settled) resolve(null);
    }).catch(reject);
  });
}

export async function runSetupWizard(profileName: string, options: {
  hasAuthKeysArg?: boolean;
  isStartCommand?: boolean;
} = {}): Promise<boolean> {
  const { hasAuthKeysArg = false, isStartCommand = false } = options;
  const profilePath = configManager.getConfigProfilePath(profileName);
  const version = constants.pkg.version;

  console.log(`\nRun this wizard any time with:`);
  console.log(`  npx ${constants.pkg.name}@${version} server configure ${profileName}\n`);

  const result = await runInkApp<WizardResult>(finish => (
    <WizardApp
      profileName={profileName}
      hasAuthKeysArg={hasAuthKeysArg}
      isStartCommand={isStartCommand}
      finish={finish}
    />
  ));

  if (!result || result.action !== 'save') {
    console.log('Configuration unchanged.');
    return false;
  }

  const config = configManager.buildConfigFromWizardResults([], null, result.cloudApiKey);
  fs.writeFileSync(profilePath, config);
  console.log(`Configuration saved to ${profilePath}`);
  return true;
}

export async function runHomeTui(): Promise<boolean> {
  const profileName = await runInkApp<string | null>(finish => (
    <ProfilePickerApp profiles={listConfigProfiles()} finish={finish} />
  ));

  if (!profileName) {
    return false;
  }

  ensureProfileConfig(profileName);
  return runSetupWizard(profileName, { hasAuthKeysArg: false, isStartCommand: false });
}

export async function handleFirstRun(profileName: string, options: {
  hasAuthKeysArg?: boolean;
  isStartCommand?: boolean;
} = {}): Promise<boolean> {
  const profilePath = configManager.getConfigProfilePath(profileName);

  configManager.ensureConfigProfileDir(profileName);
  fs.writeFileSync(profilePath, configManager.generateDefaultConfig());
  console.log(`File created at ${profilePath}\n`);

  const choice = await runInkApp<'wizard' | 'editor' | 'continue' | 'exit'>(finish => (
    <FirstRunApp profileName={profileName} finish={finish} />
  ));

  switch (choice) {
    case 'wizard':
      await runSetupWizard(profileName, options);
      return true;
    case 'editor': {
      const editor = process.env.EDITOR || process.env.VISUAL || (constants.IS_WINDOWS ? 'notepad' : 'vi');
      const result = spawnSync(editor, [profilePath], { stdio: 'inherit' });
      if (result.status !== 0) {
        console.error('Editor exited with non-zero status.');
      }
      return true;
    }
    case 'continue':
      return true;
    case 'exit':
    default:
      process.exit(0);
      return false;
  }
}
