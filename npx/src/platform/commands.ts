import { constants } from './legacy.js';

export function cliBase(): string {
  return `npx ${constants.pkg.name}@${constants.pkg.version}`;
}

export function configureProfileCommand(profileName: string): string {
  return `${cliBase()} server configure ${profileName}`;
}

export function configureProfileNonInteractiveCommand(profileName: string): string {
  return `${configureProfileCommand(profileName)} --cloud.api-key=<key> --auth.key=<key> --yes`;
}

export function startServerCommand(profileName: string): string {
  return `${cliBase()} server start --config-profile=${profileName}`;
}

export function startServerWithAuthCommand(profileName: string, authKey: string): string {
  return `${startServerCommand(profileName)} -- --auth.keys=${authKey}`;
}
